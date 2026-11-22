---
title: Check packs
description: "Get the pack-doctor jar, check a set of packs with no service, repair a pack's manifest and form, read its report and exit codes, and know what only the service checks."
doc_status: draft
updated: 2026-11-22T00:00:00Z
type: Guide
kind: How-To
related:
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
  - adr/0046-service-release-line-and-native-packaging
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - adr/0068-a-pack-declares-its-fallback-profile
  - guides/compose-packs
  - guides/service-build
audience:
  - profile: pack-author
    guidance_text: Follow this to check your packs on your machine or in CI before you register them or open a pull request.
  - profile: governance-lead
    guidance_text: Use the exit codes and the report to gate a pack repository's CI on the same checks a registration runs.
---

# Check packs

`pack-doctor` checks a set of packs on your machine, with no service to build or start
([ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/) decision 9). Its
`validate` command runs the checks a registration runs, in the same codes and words, plus the placement checks, the composition of the
set (`CLOSURE`) and, when you ask, the round trip through the charter's model. It writes one report, in the contract's
problem JSON.

It is one file, `pack-doctor-<version>.jar`, and it needs **Java 25**. It has no native build.

## Download and verify

The jar is an asset of every service release, `service-v<version>`, from `service-v0.1.0-rc.3` on
([ADR-0046](/adr/0046-service-release-line-and-native-packaging/), amendment of 2026-11-19). Download it with
`SHA256SUMS` into an empty directory, then check its digest and its build provenance:

```bash
tag=service-v0.1.0-rc.3
version="${tag#service-v}"
gh release download "$tag" --repo spec-kitty/spec-kitty-charter \
  --pattern "pack-doctor-$version.jar" --pattern SHA256SUMS
sha256sum --check --ignore-missing SHA256SUMS
gh attestation verify "pack-doctor-$version.jar" --repo spec-kitty/spec-kitty-charter \
  --signer-workflow spec-kitty/spec-kitty-charter/.github/workflows/service-release.yml \
  --source-ref "refs/tags/$tag"
```

Keep both flags of `gh attestation verify`: without them, any workflow of the repository would pass. In CI, pin the
tag and the jar's SHA-256, as you pin any other tool.

## Run it

`pack-doctor` takes a command first:

- **`validate`** checks a set of packs and writes the report. Everything below is about it, except
  [Repair a pack](#repair-a-pack).
- **`remediate`** repairs what can be repaired mechanically: a pack's manifest and the form of its files. See
  [Repair a pack](#repair-a-pack).

A command line with no command, such as the options alone, is a usage fault (`2`).

Give `validate` the packs in one of two forms:

- **A config root:** `--config-root <dir>` checks every pack under `<dir>/packs/<name>/`, each at the level its
  `pack.yaml` declares and under its directory's name, as the service reads it. A hidden entry (`.git`) is not a pack.
- **Repeated `--pack`:** `--pack <dir>[:<level>][=<id>]` checks the pack in `<dir>`, at `<level>` (`built-in`, `org`
  or `project`) when you give one, and under the pack id `<id>` when you give one. Repeat it to check a set of packs
  together.

Add `--round-trip` to round-trip every pack through the charter's model as well: each file is read, written back from
the model and compared by meaning ([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)).

```bash
# A config root: packs/built-in, packs/org-base and packs/project under ./governance
java -jar pack-doctor-0.1.0-rc.3.jar validate --config-root governance --round-trip

# The same packs, given one by one with their levels
java -jar pack-doctor-0.1.0-rc.3.jar validate \
  --pack governance/packs/built-in:built-in \
  --pack governance/packs/org-base:org \
  --pack governance/packs/project:project \
  --round-trip
```

**A pack is checked under its pack id.** The id is the name the pack is registered under: lowercase letters and
digits in hyphen-separated runs, at most 63 characters. With no `=<id>`, it is the name of the pack's directory, as
the service reads it, and a directory named otherwise, such as `My_Pack`, is one `pack_id_invalid` violation at `pack`,
and the pack is not read further. `--config-root` always uses directory names.

Give `=<id>` when the directory is not named by the pack's id: a checkout, a build directory or a copy. The pack is
then read, composed and round-tripped under that id, the report and its problems name it by that id, and the
directory's name plays no part:

```bash
java -jar pack-doctor-0.1.0-rc.3.jar validate --pack build/staging:org=org-base --pack vendor/built-in
```

How a `--pack` value is read:

- The id is the text after the last `=`, when it is a pack id. The level is then the text after the last `:` of
  what remains, when it is `built-in`, `org` or `project`. So `=<id>` comes last: `<dir>:org=org-base`.
- A directory whose name holds `=` or `:` still works. When the value read without an id names a directory and the
  value read with one does not, the value is that directory, under its own name. When both name a directory (both
  `x=y` and `x` exist, or both `x:org` and `x`), the value is ambiguous and refused, naming both readings. Rename
  one, or spell the reading you mean: `x/=y` is the directory `x` under the id `y`, and `x=y=<id>` is the directory
  `x=y` under `<id>`; `x:org:<level>` is the directory `x:org`.
- An empty directory part, as in `=<id>` or `:org`, is refused. It never checks the working directory.
- Text after the last `=` that is not a pack id, such as `dir=My_Id` or `dir=`, is refused unless the whole value
  names a directory. It is never read as part of a path that does not exist.
- Two packs under one id, given or taken from the directory's name, are refused.

`--config-root` and `--pack` cannot be mixed. `java -jar pack-doctor-<version>.jar --help` prints the usage. A
directory whose name starts with `--` goes after `--`, which ends the options.

**Check a pack with the packs it depends on.** `CLOSURE` judges the set as given and never fetches a pack. A pack
whose `depends_on` names a pack that is not in the set gets `pack_dependency_unknown`, even when the pack is fine.
Give its dependencies too, or use a config root that holds them.

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Every pack is valid. Warnings are allowed; they are on standard error. |
| `1` | At least one problem. The report on standard output lists them. |
| `2` | The command line is wrong (no command, an unknown option, a missing directory, an `=<id>` that is not a pack id, two packs in one directory or under one id), a directory given on the command line cannot be listed, the output cannot be written, the report file cannot be written, or `pack-doctor` has a defect (`pack-doctor: internal error:`). Nothing is on standard output; the cause is on standard error. |

What a pack holds is never a failure (`2`). A pack it cannot read, a malformed `pack.yaml` field (a `pack_version`
that is not a version, a `depends_on` range or `requires_core` that is not a range, a `pack_level` the schema does not
know) or a file it refuses is a problem (`1`), at the file that holds it. An `internal error` is a defect of
`pack-doctor` itself, whatever the packs hold; report it.

## Repair a pack

`remediate` takes one or more pack directories, each written as a `--pack` value is (`<dir>[:<level>][=<id>]`), and
no option:

```bash
java -jar pack-doctor-<version>.jar remediate packs/built-in:built-in packs/org-base:org
```

For each pack, in the order given, it:

1. refuses the pack when the pack has a fault it does not repair: any violation of the storage verdict except the
   manifest's (`manifest_membership`, `manifest_stale`). It names the faults and writes nothing.
2. rewrites each file the charter models in the round trip's canonical form. The meaning stays the same; comments,
   quoting, key order and line folding may change.
3. derives `pack-manifest.yaml` from the files and stamps its hashes, as every write of the service does. A pack with
   no manifest gets one.

Files the charter does not hold, such as your own notes, are left as they are. The pack is written in a copy beside
it, which then takes its place, so a pack that fails midway is left as it was.

It prints one line per pack, `<dir>: rewritten`, `<dir>: unchanged` or `<dir>: refused` followed by the faults, then
checks the same set with `validate --round-trip` and writes that report after the lines. It exits `0` when no pack was
refused and the check finds no problem, `1` when a pack was refused or the check finds a problem, and `2` on a usage
fault or a failure. Give the packs a pack depends on too, as for `validate`.

## The report

The report goes to standard output, as one JSON object. Its schema is the contract's `PackDoctorReport`
(`contracts/charter/schemas/PackDoctorReport.yaml`):

- **`tool`:** `pack-doctor` and its version.
- **`inputs`:** what you gave, as you wrote it: the `config_root`, or the `packs` (each `--pack` value), and the
  `options` that choose the checks (`round_trip`, and `full`).
- **`checks`:** the checks that ran over the set, in order: `PACK` always, `CLOSURE` when every pack's composition
  could be read and its `pack.yaml` is well formed, and `ROUND_TRIP` with `--round-trip`.
- **`packs`:** each pack checked, with its pack id as `name` (its `=<id>`, or its directory's name), its level and
  its path as you gave it.
- **`skipped`:** present only when a check was asked for and did not run. Today that is `CLOSURE`, when a pack could
  not be read, its `pack.yaml` is malformed, or its level is unknown; its `reason` names the pack. The pack's own
  fault is its one problem, under `PACK`. One such pack skips `CLOSURE` for the whole set, on purpose: judging the
  others without it would report false `pack_dependency_unknown` faults for the packs that depend on it. A closure
  fault among the other packs shows once that pack is fixed; the exit is `1` either way.
- **`problems`:** each a `Problem` of the contract, with the code and the shape a registration answers for the same
  fault. A pack's violations are one `validation_failed` problem, with each violation's code, file and detail. Its
  `instance` is the `file:` URI of the pack's directory, so you can tell the packs apart. A composition fault has its
  own code (`pack_dependency_unknown`, `pack_duplicate_id` and the others of
  [Compose packs](/guides/compose-packs/)) and names its packs. The round trip's faults are one more
  `validation_failed` problem per pack, with `round_trip_red` or `round_trip_written_unpaired` for each file. The list
  is empty when the set is valid.
- **`summary`:** the counts: the `packs` checked, the `violations` the problems state (all of them, also those a
  problem's list leaves out), the `warnings` and `notices`, and the `exit_code`.

**Warnings** go to standard error, one line each, and never change the exit code:

```text
warning: <pack>: <code> <file>: <detail>
```

For example, a file the charter does not model is `file_not_held`, a warning. Read them, but a CI gate on the exit code
passes with them. This holds with `--round-trip` too: the round trip leaves out a file the charter does not hold, such
as a stray `notes.txt`, and never fails it.

### The report file

`--report <file>` writes the same report to a file too, for a CI system to keep or parse. It is YAML by default;
`--format xml` makes it XML. Standard output and the exit code are the same with it as without.

```bash
java -jar pack-doctor-<version>.jar validate --config-root ./charter --report pack-doctor.yaml
java -jar pack-doctor-<version>.jar validate --config-root ./charter --report pack-doctor.xml --format xml
```

The XML is the same tree in elements of its own, not JUnit's: an element per key, an element per list item named for
the list (`problems/problem`, `packs/pack`), and a `type` attribute on a list, an integer or a boolean. The schema's
description states the mapping. `--format` without `--report`, or a format other than `yaml` or `xml`, is a usage
fault, and a report file that cannot be written exits `2` with nothing on standard output.

## Links

A symbolic link inside a pack is refused with `pack_entry_linked`, one violation per link, and nothing is read through
it: a link to a file, to a directory or to nothing. A registration and the service's load of a configured pack refuse
it the same way. Replace the link with the file it points to.

- **The pack root itself may be a link.** `--pack ~/packs/current` works when `current` links to the pack's directory,
  and so does a link in a config root's `packs/` that resolves to a directory.
- **Hard links and Windows junctions are not judged.** A hard link is an ordinary file to the checker, and a junction
  is not seen as a link. Do not rely on either: a pack holds its own files.

## What only the service checks

`pack-doctor` runs no closure walk and reaches no service state. These stay in the service (ADR-0075, decision 6), so a
pack that passes here can still be refused when you register it:

- the closure walk over the composed graph;
- the impact of a registration on the packs already registered;
- whether a pack may be changed (its mutability);
- the fallback profile a pack declares
  ([ADR-0068](/adr/0068-a-pack-declares-its-fallback-profile/));
- the required floor of a governing preset.
