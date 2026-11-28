---
title: Check packs
description: "Get the pack-doctor jar, check packs with no service, repair a pack's manifest, form and quoted numbers, read its report and exit codes, and know what only the service checks."
doc_status: draft
updated: 2026-11-28T00:00:00Z
type: Guide
kind: How-To
related:
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
  - adr/0046-service-release-line-and-native-packaging
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - adr/0068-a-pack-declares-its-fallback-profile
  - adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor
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
`validate` command runs the checks a registration runs on each pack, in the same codes and words, plus the placement
checks and the composition of the set (`CLOSURE`, with its presets). When you ask, it also runs the reference and
relation checks registration runs on each pack's closure (`--full`) and the round trip through the charter's model
(`--round-trip`). It writes one report, in the contract's problem JSON
([ADR-0083](/adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor/)).

It is one file, `pack-doctor-<version>.jar`, and it needs **Java 25**. It has no native build. Run it under a UTF-8
locale, such as `LC_ALL=C.UTF-8`: under the POSIX locale (`LANG` unset, or `C`, as in many CI containers) Java reads
file names as ASCII, and a pack holding a file whose name is not ASCII, such as `café.tactic.yaml`, stops the run with
`pack-doctor: internal error: java.nio.file.InvalidPathException` (exit `2`).

## Download and verify

The jar is an asset of every service release, `service-v<version>`, from the first one that carries pack-doctor on
([ADR-0046](/adr/0046-service-release-line-and-native-packaging/), amendment of 2026-11-19). No release is cut before
the work that brings pack-doctor lands
([ADR-0083](/adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor/), ruling R-w), so
take the tag of that release or a later one. Download the jar with `SHA256SUMS` into an empty directory, then check
its digest and its build provenance:

```bash
tag=service-v<version>   # a release that carries pack-doctor
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
- **`remediate`** repairs what can be repaired mechanically: a pack's manifest, the form of its files, and a quoted
  number in a number field. See [Repair a pack](#repair-a-pack).

A command line with no command, such as the options alone, is a usage fault (`2`), except `--help`, which prints the
usage and exits `0`.

Give `validate` the packs in one of two forms:

- **A config root:** `--config-root <dir>` checks every pack under `<dir>/packs/<name>/`, each at the level its
  `pack.yaml` declares and under its directory's name, as the service reads it. A hidden entry (`.git`) is not a pack.
- **Repeated `--pack`:** `--pack <dir>[:<level>][=<id>]` checks the pack in `<dir>`, at `<level>` (`built-in`, `org`
  or `project`) when you give one, and under the pack id `<id>` when you give one. Repeat it to check a set of packs
  together.

Add `--round-trip` to round-trip every pack through the charter's model as well: each file is read, written back from
the model and compared by meaning ([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)). Add `--full`
to run the reference and relation checks as well; see [Check what registration checks](#check-what-registration-checks).

```bash
# A config root: packs/built-in, packs/org-base and packs/project under ./governance
java -jar pack-doctor-<version>.jar validate --config-root governance --round-trip

# The same, with the reference and relation checks, as an open-packs CI gate runs it
java -jar pack-doctor-<version>.jar validate --config-root governance --round-trip --full

# The same packs, given one by one with their levels
java -jar pack-doctor-<version>.jar validate \
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
java -jar pack-doctor-<version>.jar validate --pack build/staging:org=org-base --pack vendor/built-in
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

`CLOSURE` runs, in order: the composition of the set (`pack_dependency_unknown`, `pack_duplicate_id` and the others
of [Compose packs](/guides/compose-packs/)), the preset contests, the preset items, then the case-only answer.

**A preset item is judged against its pack's own closure:** the pack, and every pack it reaches over `depends_on` and
`parent_pack`, never a pack that depends on it. `CLOSURE` judges each item of each preset once, as a registration does:

| The item | The problem |
| --- | --- |
| is `must_have`, and no pack of the closure holds it as a file | `preset_item_not_held` |
| no pack of the closure declares it as its kind, by a file or a graph node | `preset_item_undeclared` |

A preset item problem has one violation, at `presets/<id>.yaml#/activated_<kinds>/<index>`. An anti-pattern is held
only by its file, `anti_patterns/<id>.anti-pattern.yaml`. An item that names a held artifact only in other letter case
gets neither: it gets the case-only answer below.

**The case-only answer is judged as a registration judges it.** A preset item, or a lock in the pack's policy file (the
file `mutability_policy` names), that names an artifact only in other letter case, `directive:directive_001` where a
pack holds `directive:DIRECTIVE_001`, is `pack_reference_artifact_missing`, naming the mentioning pack then the pack
that holds the artifact, with no violation of its own. Its scope is wider than a preset item's: for each pack of the
set, `CLOSURE` takes the closure that pack roots, as a registration of that pack composes it, and judges every
member's items and locks against every member's artifacts. So a dependency's item or lock that only a pack depending
on it holds in other case is a problem when you check the two together, and is not when you check the dependency
alone. A problem two packs' closures find is reported once. A pack's items come before its locks, and its locks are in
URN order. An item or a lock naming an id that no pack of the closure holds, in any case, is not a problem.

**A manifest's hashes are checked.** A `content_hash` or `manifest_hash` of `pack-manifest.yaml` that does not match the
pack's bytes is `manifest_stale`, one violation per field, naming it. `remediate` restamps it; see
[Repair a pack](#repair-a-pack).

## Check what registration checks

`--full` adds two checks, `REFERENCES` and then `RELATIONS`. For each pack of the set taken as a root, over the
closure it composes (the pack and every pack it reaches over `depends_on` and `parent_pack`), `REFERENCES` runs three
checks a registration runs on every pack of that closure, in the registration's order and with its codes, as
registering that pack would. So a dependency's bare reference that only its dependent holds is reported when you give
the dependent too. A problem several roots find is reported once; a qualified reference into a pack outside its
writer's `depends_on`, but inside a root's closure, is reported once per root, each in that root's words, as
registering each root answers it. Packs that only an activation lists together are judged by a registration alone:
pack-doctor does not see activations. A pack named in a reference, a delegation candidate or an enhancement target must
be in the pack's `depends_on` closure: the pack and every pack it reaches over `depends_on`. Its `parent_pack` does not
count, as at registration.

1. **The substep delegations.** A candidate of a substep's `delegates_to` that names a pack of the closure that does not
   hold it is `validation_failed`, with the violation `substep_delegation_unresolved` at the mission type file.
2. **The references.** These are the references a pack writes:
   - a graph edge's endpoints;
   - a guard's `implemented_by`;
   - a glossary term's `see_also`;
   - an agent profile's `directive-references` codes, its `tactic-references`, `toolguide-references` and
     `styleguide-references` ids, its `collaboration.operating-procedures`, and each `excluding` value
     under one of those lists' keys.

   A profile's entry names an artifact of its list's kind by the artifact's id: bare when the artifact is in the
   profile's own pack, `<pack>::<id>` when it is in another pack, and the kind may be written out
   (`<pack>::directive:DIRECTIVE_001`). A directive code is the directive's id, never a part of it, so the schema
   refuses `001`.

   Each reference gets one of three codes:
   - `pack_reference_unqualified` when it is bare and only another pack of the closure holds it;
   - `pack_reference_pack_missing` when it names a pack outside the `depends_on` closure;
   - `pack_reference_artifact_missing` when it names a pack of that closure that does not hold it.

   A bare reference that no pack holds is not a problem. A profile's references mint no edge: they say which doctrine
   the profile is handed (ADR-0064 decision 11).
3. **The step enhancements' targets.** A step enhancement whose target step does not exist is
   `pack_reference_pack_missing` or `pack_reference_artifact_missing`.

Each check reports every fault it finds, not only the first. A later check runs only when the earlier ones find nothing
in the root's closure, since it would report the same cause again. `REFERENCES` runs when `CLOSURE` found no composition
problem: a pack whose closure does not compose (an unknown dependency, a cycle, a duplicate id and the others of
[Compose packs](/guides/compose-packs/)) has no closure to check, so fix those first. A preset contest, a preset item or
a case-only problem does not stop it: those are reported beside `REFERENCES`' own. When `CLOSURE` is skipped,
`REFERENCES` is skipped too.

`RELATIONS` then runs the relation rules a registration runs on each closure, over the same closure of each root, with
the registration's codes, in this order:

1. **A self-override.** An `overrides` edge whose target is its own source is `self_override`.
2. **A conflicting relation.** An artifact that both enhances and overrides one target is `conflicting_relations`.
3. **An override that is not permitted.** An override of an artifact that is not `open`, or an enhancement of a step
   whose mission type is locked, is `override_not_permitted`.
4. **An override cycle.** `overrides` edges that form a cycle are `override_cycle`.
5. **An enhances cycle.** `enhances` edges that form a cycle, or an enhancer whose chain reaches no artifact that
   enhances nothing, are `enhances_cycle`.
6. **A specialization cycle.** `specializes_from` edges among agent profiles that form a cycle are
   `specialization_cycle`, naming the profiles in order. A cycle a dependent closes across its dependency is found
   from the dependent's root, as registering the dependent refuses it.

A rule runs only when the closure writes an `enhances` or `overrides` edge; the specialization cycle is always judged.
Each rule reports every fault it finds. A problem several roots find is reported once. `RELATIONS` runs after
`REFERENCES` and is skipped when it is. A root whose closure has a `REFERENCES` problem is not judged, as registration's
phases 2 to 4 (the delegations, the references and the targets) gate its relation checks: a broken reference would be
reported again as a relation. `RELATIONS` is listed in `checks` only when at least one root was judged. A preset
contest, a preset-item problem or a case-only problem does not stop it, though registration would stop at those first:
they stand beside `RELATIONS`' problems, as beside `REFERENCES`'.

Within one root's closure, its `REFERENCES` problems come before its `RELATIONS` problems, so that closure's first
problem is the one registering the root answers. Across roots the report lists every `REFERENCES` problem, then every
`RELATIONS` problem, where a registration judges one closure after another.

`--full` does not yet judge everything a registration does. A pack it passes can still be refused when you register it
for an edge rule that needs the layer merge, or the closure walk of its own profiles; see
[What only the service checks](#what-only-the-service-checks).

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Every pack is valid. Warnings are allowed; they are on standard error. |
| `1` | At least one problem. The report on standard output lists them. |
| `2` | The command line is wrong (no command, an unknown option, a missing directory, an `=<id>` that is not a pack id, two packs in one directory or under one id, a `--report` file inside a pack it checks, a pack to `remediate` that is or holds the working directory), a directory given on the command line cannot be listed, the output cannot be written, the report file cannot be written, a pack cannot be rewritten by `remediate`, or `pack-doctor` has a defect (`pack-doctor: internal error:`). The cause is on standard error. Nothing is on standard output, except that `remediate` keeps the outcome lines it printed for the packs before the failure, and that a report file whose last rename fails leaves the report on standard output. |

What a pack holds is never a failure (`2`). A pack it cannot read, a malformed `pack.yaml` field (a `pack_version`
that is not a version, a `depends_on` range or `requires_core` that is not a range, a `pack_level` the schema does not
know) or a file it refuses is a problem (`1`), at the file that holds it. So are:

- a YAML file, or a Markdown file's front matter, of more than 1 048 576 code points, or one whose strings, a value
  shared through an alias counted each time it is used, add up to more than that (a number's digits count too), or that
  holds a number of more than 1 000 digits, or a float whose plain form would be (such as `1e-99999`), or past the
  parser's alias or nesting budget: `unreadable` at the file (`pack_unreadable` at `pack.yaml`), with the detail `the
  document is too large`, or the parser's reason. The round trip reads every file through the same guards;
- with `--round-trip`, and for `remediate`, a YAML file (an artifact, a preset, a graph document, the policy, the
  manifest) whose canonical form would hold more than 1 048 576 code points (a tab in an unquoted value is written
  `\t`, so a file just inside the bound can grow past it): `round_trip_red` at
  the file, `not written: its canonical form holds <n> code points, past the cap of 1048576: the document is too
  large`. Split the file's content or shorten it;
- a special file (see [Links and special files](#links-and-special-files));
- a file name or a value holding a character XML cannot carry: the XML report file escapes it (see
  [The report file](#the-report-file)).

An `internal error` is a defect of `pack-doctor` itself, whatever the packs hold; report it.

## Repair a pack

`remediate` takes one or more pack directories, each written as a `--pack` value is (`<dir>[:<level>][=<id>]`), and
no option:

```bash
java -jar pack-doctor-<version>.jar remediate packs/built-in:built-in packs/org-base:org
```

For each pack, in the order given, it:

1. refuses the pack when the pack has a fault it does not repair: any violation of the storage verdict except the
   manifest's (`manifest_membership`, `manifest_stale`) and a quoted number in a number field (step 2). It names the
   faults and writes nothing. A directory with no `pack.yaml` is refused this way too, as `validate` reports it; a path
   that is not a directory is a usage fault (`2`).
2. repairs a quoted number in a field the schema types `number` or `integer`: `input_per_1m_usd: "15"` becomes
   `input_per_1m_usd: 15`. This is the one fault of a file's content it repairs, the one whose detail ends `write 15
   without quotes, or run pack-doctor remediate`. It writes the value in place of the whole value as written: its
   quotes, an escape (`"1\x35"`), a `!!str` tag, or a block scalar (`|-`); step 3 puts it in canonical form (`"1e-3"`
   ends as `0.001`). Only a value that, written plain, is a finite number of the field's type is repaired; an
   `integer` field takes integers only, so `"1.5"` there is not. It leaves a value with an anchor (`&price "15"`), and
   every value in a file that uses an alias or holds more than one document. It then checks the pack again, and
   refuses it, writing nothing, when anything is still wrong: a quoted value that is no such number (`"fifteen"`), a
   value it left, any other fault beside the quoted numbers, or a number the field's range refuses once it is plain
   (`"-15"` for a price). In that refusal a quoted number's detail ends `write 15 without quotes; remediate does once
   the pack's other faults are fixed`, or, for a value it left, `; remediate cannot:` and why. A quoted string in a
   field typed `string` is never touched, even one that looks like a number. A quoted number in `policy.yaml` is not
   repaired: its fault is joined into the file's one detail, which has no pointer, and says `write 1 without quotes`
   alone.
3. rewrites each file the charter models in the round trip's canonical form. The meaning stays the same; comments,
   quoting, key order and line folding may change. A file the round trip refuses, such as one whose canonical form is
   past the size bound, cannot be rewritten: the pack is refused, each such file named on an indented
   `the round trip refused <file>: <reason>` line, and nothing is written.
4. derives `pack-manifest.yaml` from the files and stamps its hashes, as every write of the service does. A pack with
   no manifest gets one.

Files the charter does not hold, such as your own notes, are left as they are. `remediate` takes no lock: do not run
it on a directory a running service reads packs from.

How it writes:

- **In a copy.** It does its work in a copy under the Java temporary directory (`java.io.tmpdir`), each file of it
  writable by you, so a read-only file is repaired and rewritten too and keeps its mode. A pack the copy
  equals byte for byte is `unchanged`, and nothing is written in or beside it, so a canonical pack needs no write
  access. A pack it rewrites is written to a hidden sibling, `.<pack>.remediate-<uuid>`, keeping the files' modes
  (a file the pack never had, such as a manifest it lacked, takes the mode of the directory it is in, without search
  bits), which then takes the pack's place: the pack is moved aside to `.<pack>.aside-<uuid>` and deleted once the sibling is
  in, its read-only directories made writable first. So rewriting needs write access to the directory that holds the
  pack. If the old copy still cannot be deleted, the pack is rewritten all the same and the exit code is unchanged:
  a `pack-doctor: warning:` line on standard error names the copy, `.<pack>.aside-<uuid>`; delete it.
- **A pack edited meanwhile** is not overwritten. The copy is the one thing `remediate` reads, and just before the pack
  is moved aside it is compared with the copy it was made from: a pack that changed meanwhile is `refused`, with
  `the pack changed during remediation` on its line, nothing is written, and the edit stays. A file deleted before
  its quoted numbers are repaired is refused the same way. Run `remediate` again.
- **A pack root that is a link** is followed: the directory it resolves to is rewritten and swapped, and the link is
  kept.
- **Not from inside the pack.** A pack that is, or holds, the working directory is a usage fault (`2`): run
  `remediate` from outside it.
- **When it fails.** A pack that fails midway is left as it was, when the process lives through the failure, and
  standard error says `it is unchanged`. If the pack was moved aside and could not be put back, standard error says
  where the pack as it was is, `.<pack>.aside-<uuid>` beside it, and prints each further failure on a
  `pack-doctor:   and:` line; move that directory back by hand. Stopped with `Ctrl-C` or `SIGTERM`, it deletes its copy
  and its sibling, and waits for a swap under way. Killed outright (`SIGKILL`, a power loss), it can leave a hidden
  sibling: delete `.<pack>.remediate-<uuid>`, a copy; `.<pack>.aside-<uuid>` holds the pack as it was, to move back
  when the pack itself is gone. A `pack-remediation-*` directory can be left in the temporary directory; delete it.

Run it, then `validate`, to clear the problems it repairs:

```bash
java -jar pack-doctor-<version>.jar validate --pack packs/org-base:org --pack packs/built-in:built-in
# exit 1: the report lists a manifest_stale violation at pack-manifest.yaml
java -jar pack-doctor-<version>.jar remediate packs/built-in:built-in packs/org-base:org
```

It prints one line per pack, `<dir>: rewritten`, `<dir>: unchanged` or `<dir>: refused` followed by the faults or the
files the round trip refused. A pack whose quoted numbers it repaired is `<dir>: rewritten, <n> quoted numbers
repaired` (`1 quoted number` for one), followed by one line for each, in the order the storage verdict finds them. The line
ends `is now a number` in a `number` field and `is now an integer` in an `integer` field:

```text
packs/org-base: rewritten, 2 quoted numbers repaired
  repaired at routing_catalogs/cost-led.routing-catalog.yaml#/models/0/cost/input_per_1m_usd: "15" is now a number
  repaired at agent_profiles/human-operator.agent.yaml#/routing-priority: "40" is now an integer
```

The report that follows the lines lists the same repairs in `repairs` (operator ruling Q14 of 2026-11-25, #651): each
with its `pack`, `file`, `pointer`, `value` and `type` (`number` or `integer`), per pack in the order given, and empty
when nothing was repaired. `validate`'s report has no `repairs`.

Then it checks the same set with `validate --round-trip` and writes that report after the lines. It exits `0` when no
pack was refused and the check finds no problem, `1` when a pack was refused or the check finds a problem, and `2` on a
usage fault or a failure. Give the packs a pack depends on too, as for `validate`. A usage fault names `remediate`, as
in `remediate . holds the working directory`.

## The report

The report goes to standard output, as one JSON object. Its schema is the contract's `PackDoctorReport`
(`contracts/charter/schemas/PackDoctorReport.yaml`):

- **`tool`:** `pack-doctor` and its version.
- **`inputs`:** what you gave, as you wrote it: the `config_root`, or the `packs` (each `--pack` value), and the
  `options` that choose the checks: `round_trip` and `full`, each `true` or `false`.
- **`checks`:** the checks that ran over the set, in order: `PACK` always, `CLOSURE` when every pack's composition
  could be read and its `pack.yaml` is well formed, `REFERENCES` with `--full` when `CLOSURE` found no composition
  problem, `RELATIONS` after it when at least one root's closure passed `REFERENCES`, and `ROUND_TRIP` with
  `--round-trip`.
- **`packs`:** each pack checked, with its pack id as `name` (its `=<id>`, or its directory's name), its level and
  its path as you gave it. Every problem, warning and `skipped` reason names a pack by that id.
- **`skipped`:** present only when a check was asked for and could not run. That is `CLOSURE`, and with `--full`
  `REFERENCES` and `RELATIONS`, when a pack could not be read, its `pack.yaml` is malformed, or its level is unknown;
  its `reason` names the pack. `REFERENCES` and `RELATIONS` left out because `CLOSURE` found a composition problem are
  not listed here, nor is `RELATIONS` left out because no root's closure passed `REFERENCES`. The pack's own fault is
  its one problem, under `PACK`. One such pack skips `CLOSURE` for the whole set, on purpose: judging the others
  without it would report false `pack_dependency_unknown` faults for the packs that depend on it. A closure fault among
  the other packs shows once that pack is fixed; the exit is `1` either way.
- **`problems`:** each a `Problem` of the contract, with the code and the shape a registration answers for the same
  fault. A pack's violations are one `validation_failed` problem, with each violation's code, file and detail. Its
  `instance` is the `file:` URI of the pack's directory, so you can tell the packs apart. A composition fault has its
  own code (`pack_dependency_unknown`, `pack_duplicate_id` and the others of
  [Compose packs](/guides/compose-packs/), `preset_item_not_held`, `preset_item_undeclared`, and
  `pack_reference_artifact_missing` for a case-only answer) and names each of its packs once; so does a `REFERENCES`
  or `RELATIONS` fault. The round trip's faults are one more `validation_failed` problem per pack, with
  `round_trip_red` or `round_trip_written_unpaired` for each file. The list is empty when the set is valid.
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

The file must lie outside every pack the run checks, by its real path, links followed: a report file inside a pack is
a usage fault (`2`), so the report never becomes part of a pack. It is first written to a hidden file beside it,
`.<name>.<uuid>.tmp`, before anything reaches standard output, and renamed into place once standard output took the
report, so a reader never sees half a report. If standard output cannot be written, the file is left as it was. A
`--report` that is a link is followed, and the file it names is replaced; a link to a file not written yet is judged
where it leads.

```bash
java -jar pack-doctor-<version>.jar validate --config-root ./charter --report pack-doctor.yaml
java -jar pack-doctor-<version>.jar validate --config-root ./charter --report pack-doctor.xml --format xml
```

The XML is the same tree in elements of its own, not JUnit's: an element per key, an element per list item named for
the list (`problems/problem`, `packs/pack`), and a `type` attribute on a list, an integer, a boolean or an empty
object. A string holding a character XML 1.0 cannot carry, such as `U+0001` in a file name, is an element with
`type="escaped"`: each such character is written `\uXXXX`, the upper-case hex of its UTF-16 code unit, and each
backslash `\\`. Undo that, and you have the string; a string with no such character is never escaped. The schema's
description states the mapping. `--format` without `--report`, or a format other than `yaml` or `xml`, is a usage
fault, and a report file that cannot be written exits `2` with nothing on standard output.

## Links and special files

A symbolic link inside a pack is refused with `pack_entry_linked`, one violation per link, and nothing is read through
it: a link to a file, to a directory or to nothing. A registration and the service's load of a configured pack refuse
it the same way. Replace the link with the file it points to.

A special file inside a pack, an entry that is neither a regular file nor a directory nor a link (a named pipe made by
`mkfifo`, a socket, a device), is refused with `unreadable` at its path, `<file>: is not a regular file or a directory;
a pack holds only regular files and directories`, one violation per file, and is never opened. That is its one fault:
a mission type that names it as a step's prompt or guidelines is not refused beside it. `validate`,
`remediate` and a registration refuse it alike, and so does the service's load of a configured pack. Remove it.

- **The pack root itself may be a link.** `--pack ~/packs/current` works when `current` links to the pack's directory,
  and so does a link in a config root's `packs/` that resolves to a directory.
- **Hard links and Windows junctions are not judged.** A hard link is an ordinary file to the checker, and a junction
  is not seen as a link. Do not rely on either: a pack holds its own files.

## What only the service checks

`pack-doctor` reaches no service state, and even with `--full` it does not yet run every closure check. These stay in
the service (ADR-0075, decision 6; ADR-0083), so a pack that passes here can still be refused when you register it:

- the edge rules that judge the layer merge: an override the level of the overridden artifact forbids, a `self_override`
  that only the layer merge creates (a re-pointed override; the declared one is `RELATIONS` rule 1), an enhancement of a
  step whose mission type only the merge locks (a lock the closure declares is `RELATIONS` rule 3), and another pack's step link that names no step of the mission type in force;
- the closure walk of a pack's own profiles: the fallback profile it declares
  ([ADR-0068](/adr/0068-a-pack-declares-its-fallback-profile/)), its role pins and its profile enhancements;
- the impact of a registration on the packs already registered, and the closures stored activations compose: a
  case-only mention between two packs that only an activation lists together is refused at registration and passes
  here;
- whether a pack may be changed (its mutability).

The first two move into the checks `pack-doctor validate --full` shares with registration when epic
[#636](https://github.com/spec-kitty/spec-kitty-charter/issues/636) slice 7 gives pack-doctor the layer merge and a
profile reader.
