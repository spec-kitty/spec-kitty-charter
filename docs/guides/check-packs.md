---
title: Check packs
description: "Download and verify the pack-check jar, check a set of packs on your machine with no service, read its report and exit codes, and know what only the service checks."
doc_status: draft
updated: 2026-11-19T00:00:00Z
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

`pack-check` checks a set of packs on your machine, with no service to build or start
([ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/) decision 9). It
runs the checks a registration runs, in the same codes and words, plus the placement checks, the composition of the
set (`CLOSURE`) and, when you ask, the round trip through the charter's model. It writes one report, in the contract's
problem JSON.

It is one file, `pack-check-<version>.jar`, and it needs **Java 25**. It has no native build.

## Download and verify

The jar is an asset of every service release, `service-v<version>`, from `service-v0.1.0-rc.2` on
([ADR-0046](/adr/0046-service-release-line-and-native-packaging/), amendment of 2026-11-19). Download it with
`SHA256SUMS` into an empty directory, then check its digest and its build provenance:

```bash
tag=service-v0.1.0-rc.2
version="${tag#service-v}"
gh release download "$tag" --repo spec-kitty/spec-kitty-charter \
  --pattern "pack-check-$version.jar" --pattern SHA256SUMS
sha256sum --check --ignore-missing SHA256SUMS
gh attestation verify "pack-check-$version.jar" --repo spec-kitty/spec-kitty-charter \
  --signer-workflow spec-kitty/spec-kitty-charter/.github/workflows/service-release.yml \
  --source-ref "refs/tags/$tag"
```

Keep both flags of `gh attestation verify`: without them, any workflow of the repository would pass. In CI, pin the
tag and the jar's SHA-256, as you pin any other tool.

## Run it

Give it the packs in one of two forms:

- **A config root:** `--config-root <dir>` checks every pack under `<dir>/packs/<name>/`, each at the level its
  `pack.yaml` declares. This is the layout the service reads. A hidden entry (`.git`) is not a pack.
- **Repeated `--pack`:** `--pack <dir>[:<level>]` checks the pack in `<dir>`, at `<level>` (`built-in`, `org` or
  `project`) when you give one. Repeat it to check a set of packs together.

Add `--round-trip` to round-trip every pack through the charter's model as well: each file is read, written back from
the model and compared by meaning ([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)).

```bash
# A config root: packs/built-in, packs/org-base and packs/project under ./governance
java -jar pack-check-0.1.0-rc.2.jar --config-root governance --round-trip

# The same packs, given one by one with their levels
java -jar pack-check-0.1.0-rc.2.jar \
  --pack governance/packs/built-in:built-in \
  --pack governance/packs/org-base:org \
  --pack governance/packs/project:project \
  --round-trip
```

**Name each pack's directory by its pack id.** A pack is checked under the name of its directory, as the service
reads it, so the name must be a pack id: lowercase letters and digits in hyphen-separated runs, at most 63 characters.
A directory named otherwise, such as `My_Pack`, is one `pack_id_invalid` violation at `pack`, and the pack is not read
further.

`--config-root` and `--pack` cannot be mixed. `java -jar pack-check-<version>.jar --help` prints the usage. A
directory whose name starts with `--` goes after `--`, which ends the options.

**Check a pack with the packs it depends on.** `CLOSURE` judges the set as given and never fetches a pack. A pack
whose `depends_on` names a pack that is not in the set gets `pack_dependency_unknown`, even when the pack is fine.
Give its dependencies too, or use a config root that holds them.

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Every pack is valid. Warnings are allowed; they are on standard error. |
| `1` | At least one problem. The report on standard output lists them. |
| `2` | The command line is wrong (an unknown option, a missing directory, two packs in one directory), the output cannot be written, or `pack-check` failed internally. Nothing is on standard output; the cause is on standard error. |

A pack it cannot read is a problem (`1`), not a failure (`2`).

## The report

The report goes to standard output, as one JSON object. Its schema is the contract's `PackCheckReport`
(`contracts/charter/schemas/PackCheckReport.yaml`):

- **`checks`:** the checks that ran over the set, in order: `PACK` always, `CLOSURE` when every pack's composition
  could be read, and `ROUND_TRIP` with `--round-trip`.
- **`packs`:** each pack checked, with its directory name, its level and its path as you gave it.
- **`skipped`:** present only when a check was asked for and did not run. Today that is `CLOSURE`, when a pack could
  not be read; its `reason` names the pack.
- **`problems`:** each a `Problem` of the contract, with the code and the shape a registration answers for the same
  fault. A pack's violations are one `validation_failed` problem, with each violation's code, file and detail. Its
  `instance` is the `file:` URI of the pack's directory, so you can tell the packs apart. A composition fault has its
  own code (`pack_dependency_unknown`, `pack_duplicate_id` and the others of
  [Compose packs](/guides/compose-packs/)) and names its packs. The round trip's faults are one more
  `validation_failed` problem per pack, with `round_trip_red` or `round_trip_written_unpaired` for each file. The list
  is empty when the set is valid.

**Warnings** go to standard error, one line each, and never change the exit code:

```text
warning: <pack>: <code> <file>: <detail>
```

For example, a file the charter does not model is `file_not_held`, a warning. Read them, but a CI gate on the exit code
passes with them.

## Links

A symbolic link inside a pack is refused with `pack_entry_linked`, one violation per link, and nothing is read through
it: a link to a file, to a directory or to nothing. A registration and the service's load of a configured pack refuse
it the same way. Replace the link with the file it points to.

- **The pack root itself may be a link.** `--pack ~/packs/current` works when `current` links to the pack's directory,
  and so does a link in a config root's `packs/` that resolves to a directory.
- **Hard links and Windows junctions are not judged.** A hard link is an ordinary file to the checker, and a junction
  is not seen as a link. Do not rely on either: a pack holds its own files.

## What only the service checks

`pack-check` runs no closure walk and reaches no service state. These stay in the service (ADR-0075, decision 6), so a
pack that passes here can still be refused when you register it:

- the closure walk over the composed graph;
- the impact of a registration on the packs already registered;
- whether a pack may be changed (its mutability);
- the fallback profile a pack declares
  ([ADR-0068](/adr/0068-a-pack-declares-its-fallback-profile/));
- the required floor of a governing preset.
