# Open-packs dry run against slice B (WP09, T041)

A one-off dry run, recorded as research and not kept as a test (ADR-0070: charter tests never read open-packs content).
It answers SC-005's question for the service's registration load: once open-packs applies `open-packs#TBD-readme-doc`
and `open-packs#TBD-org-charter-preset`, does every pack pass it?

**Answer: yes.** At open-packs `main` `a6cfeab`, all 19 packs fail the registration load (`WholePackStore.loadStaged`,
IC-04 step 2). With both drafts applied to a copy of the packs, all 19 pass it with no refusal: no retired,
case-variant, unclassified or unmodelled file, and every `README.md` read by the codec. Neither draft alone is enough.
This is not a full registration: no `POST` ran, so the steps after the load (the composition and closure checks, the
commit) were not exercised. A full registration run belongs to the mission's gates. No open-packs content refusal is
left over. The leftovers in (c) are charter-side and core-side observations for the orchestrator. None is filed here.

## Inputs

| Input | Value |
| --- | --- |
| Open-packs | `spec-kitty/spec-kitty-open-packs` `origin/main` at `a6cfeabf0326d0d5d6785777f7d3b342b67952b5` (2026-11-12, "test(landing): retire duplicate and self-comparing pack digest tests"), fetched and checked out detached in the read-only clone. Nothing there was edited. |
| Charter service | lane-e worktree `kitty/mission-slice-b-real-packs-register-01M7649C-lane-e` at `2f9b10b5` (WP02 to WP06): `README.md` as the pack document; `pack.md`, `PROVENANCE.md` and `org-charter.yaml` retired; `active_charter` removed from the closed descriptor; the `ignored` fallback for unmodelled Markdown. The compiled classes under `service/*/build/classes/java/main` were at least as new as every main source file. Gradle was not run. |
| Packs | 19 directories under `packs/`: architecture, built-in, cave-kitty, drupal, event-driven, foundation, go, internal, java, javascript, pack-authoring, planning-analysis, python, qa, refactoring, rust, spdd, testing, writing. This is the same set and the same `pack.md`, `README.md`, `PROVENANCE.md` and `org-charter.yaml` files as `faa4770`, the state the drafts were first written against. Between the two commits only `packs/*.source.yaml` and `packs/README.md` changed. |

## How it was run

A throwaway driver, `ai.speckitty.charter.packs.infra.storage.DryRun`, was compiled into the same package as the
store so it could reach the package-private seams. It was compiled and run with Temurin 25 on a plain classpath. The
classpath held lane-e's `service/*/build/classes/java/main` and `service/*/build/resources/main`, plus these jars from
the Gradle cache: snakeyaml-engine 3.2, json-schema-validator 3.0.8, itu 1.14.0, `jackson-databind` and `jackson-core`
3.1.5 (`tools.jackson`), jackson-annotations 2.21, re2j 1.8, slf4j-api 2.0.18 and jspecify 1.0.0.

```sh
javac -proc:none -cp "$CP" -d out DryRun.java
java -cp "out:$CP" ai.speckitty.charter.packs.infra.storage.DryRun <copy-of-open-packs>/packs
```

For each pack directory `dir`, named `name`, the driver makes these calls. They are the seams the service itself uses:

```java
// 1. Classification of every regular file (hidden files included), as PackFileSet and WholePackStore call it
PackFileKind.Classification c = PackFileKind.bundled().classify(name + "/" + file);
//    -> Mapped | Excluded | Retired (a case variant is a Retired naming the rule's file) | Unclassified

// 2. The README codec: strict UTF-8 decode, then the read the store and validate mode share
FileRead<MarkdownDocument> doc = PackDocumentMarkdown.read(text);  // MarkdownCodec.parse + pack-document schema

// 3. The registration load (IC-04 step 2): CharterRefusal with every violation, or the pack's contents
new WholePackStore(new YamlSerializationStrategy()).loadStaged(new StagedPack(dir));
//    when it passes, also: load(dir).excluded(), .unmodelled(), .warnings(), .pack().document()
```

The driver always ran on a copy of `packs/`, never on the clone. A second throwaway script, `apply_drafts.py`, applied
each draft to a fresh copy. It does exactly this:

- **readme-doc.**
  - **built-in:** rename `pack.md` to `README.md`.
  - **Every other pack:**
    - Keep the README's H1 and preamble. Delete the Level, `pack_id` and Depends-on bullets, both the three-bullet form
      and the one-line form of foundation and writing.
    - Remove the inline pointers "Carved from `packs/built-in/` by #73; see [PROVENANCE.md](PROVENANCE.md)." (foundation,
      writing) and "Where the content came from: [PROVENANCE.md](PROVENANCE.md)." (go, rust). Rewrite qa's "; see
      [PROVENANCE.md](PROVENANCE.md)." as "; see Provenance below.".
    - Drop the README's `## Provenance` pointer section.
    - Drop `pack.md`'s preamble. For go and rust, keep its first paragraph as the README's opening paragraph.
    - Drop `pack.md`'s "Contents" section and add its other H2 sections. The script stops if a section title is in
      both files; none is.
    - Add `## Provenance` only where `PROVENANCE.md` exists. It holds the `PROVENANCE.md` body with the H1 dropped and
      every heading outside a fence demoted one level.
    - Delete `pack.md` and `PROVENANCE.md`.
- **org-charter-preset:** delete `org-charter.yaml` and the `active_charter:` line. For `internal`, rewrite
  `presets/base.yaml` with the 18 must-have items listed in that draft.

The script does not make the drafts' editorial changes that cannot affect the load, such as comments, guides, the
org-charter wording in READMEs and preset descriptions, or CI scripts. The run was done in four states: as found,
readme-doc only, org-charter-preset only, and both. Both scripts stay in the session scratchpad and are not committed.

## Results

### Counts per category

| State | Files | Mapped | Excluded | Retired | Case variant | Unclassified | README codec: read / refused / absent | Packs that pass the registration load | Load violations |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| As found (`a6cfeab`) | 780 | 709 | 24 | **47** | 0 | 0 | 18 / 0 / 1 | **0 of 19** | 25 (13 `retired`, 12 `pack_unreadable`) |
| readme-doc only | 746 | 710 | 24 | 12 | 0 | 0 | 19 / 0 / 0 | 7 of 19 | 12 (`pack_unreadable`) |
| org-charter-preset only | 768 | 709 | 24 | 35 | 0 | 0 | 18 / 0 / 1 | 0 of 19 | 35 (`retired`) |
| **Both drafts** | 734 | 710 | 24 | **0** | 0 | 0 | **19 / 0 / 0** | **19 of 19** | **0** |

After both drafts, `load` reports no unmodelled file and no warning for any pack, and every pack holds its document.
These counts are from the run with the final `apply_drafts.py`, which follows the readme-doc draft's preamble rule.

### Refused at the registration load, as found

**Retired (47 files, refused by name).** All 47 are fixed by the drafts.

| File | Packs | Count | Fixed by |
| --- | --- | --- | --- |
| `pack.md` | all 19 | 19 | readme-doc |
| `PROVENANCE.md` | architecture, cave-kitty, drupal, foundation, go, internal, java, javascript, pack-authoring, planning-analysis, python, qa, refactoring, rust, testing, writing | 16 | readme-doc |
| `org-charter.yaml` | cave-kitty, drupal, event-driven, go, internal, java, javascript, pack-authoring, python, qa, rust, spdd | 12 | org-charter-preset |

**`pack.yaml` unreadable (12 packs).** The same 12 packs declare `active_charter: org-charter.yaml`, which the closed
descriptor no longer admits. The load answers one `pack_unreadable` violation, "pack.yaml of `<pack>` cannot be read",
and stops there, so the pack's retired files are not listed. A probe confirmed the cause. With only `org-charter.yaml`
deleted, `java` is still `pack_unreadable`. With only the `active_charter` line deleted, `drupal` reports its
`org-charter.yaml` as `retired`. The org-charter-preset draft fixes this.

**Case variant: none.** No file differs only in letter case from `README.md` or a retired root file.

**Unclassified or unmodelled: none.** Every file is mapped, excluded or retired.

The load refuses, in short:

- architecture, foundation, planning-analysis, refactoring, testing and writing: `pack.md` and `PROVENANCE.md`.
- built-in: `pack.md` only.
- The other 12: `pack_unreadable`. Their 35 retired files appear once `active_charter` is gone.

### Excluded: not refused, not held

Classification excludes 24 files. They are listed here for completeness. None of them is a refusal.

| Pack | File | Rule |
| --- | --- | --- |
| foundation | `missions/README.md` | `*/missions/README.md` |
| foundation | `missions/plan/README.md`, `missions/research/README.md`, `missions/software-dev/README.md` | `*/missions/*/README.md` |
| foundation | `missions/plan/templates/README.md`, `missions/research/templates/README.md`, `missions/research/templates/research/README.md`, `missions/software-dev/templates/README.md` | `*/missions/*/templates/**/README.md` |
| foundation | `missions/plan/templates/.gitkeep` | `*/missions/*/templates/**/.*` |
| foundation | `agent_profiles/README.md` | `**/*.md` (the `ignored` fallback) |
| internal | `skills/{issue-triage,land-pr,mission-from-issue,report-debrief,semi-auto}.skill.md` | `**/*.md` (fallback) when classified alone. At load they are claimed as their skills' `text` companions and held, so `load().excluded()` does not list them. |
| qa | `missions/qa/templates/README.md` | `*/missions/*/templates/**/README.md` |
| qa | `missions/qa/templates/plan-field-declaration.yaml` | `**/templates/plan-field-declaration.yaml` |
| writing | `assets/README.md`, `assets/audiences/README.md` | `*/assets/**/README.md` |
| writing | `missions/documentation/README.md` | `*/missions/*/README.md` |
| writing | `missions/documentation/templates/README.md`, `.../divio/README.md`, `.../generators/README.md` | `*/missions/*/templates/**/README.md` |
| writing | `styleguides/writing/README.md` | `**/*.md` (fallback) |

At load, after the companion claims, 19 files are excluded. The two drafts change none of them.

### README codec

All 18 existing `README.md` files are read by `PackDocumentMarkdown.read`, with no parse failure and no schema
violation. `built-in` has no README. Squad A11b's concern about cave-kitty's `#` lines does not apply: those lines
(`# Voice only, ...`) sit inside a fenced `bash` block, which the codec reads as content. The same holds for the code
fences in event-driven, go, internal, qa and rust.

The risk sits in the merge, not in today's files. A probe on a merged `java/README.md` showed three things:

- A copied `# Provenance` H1 is refused ("line 39: a second H1 (the first is at line 1)").
- A `setext` heading is refused: `a setext heading underline is ambiguous; use an ATX heading or a blank line`.
- An H4 heading and a `#` line inside a fence are read.

The codec's full line rules are in the class comment of `MarkdownScanner`. The readme-doc draft lists them, including
the closing `#` sequence and an unclosed fence. With the draft's rules, the merged README of every pack is read, as the
"Both drafts" row shows.

## Split

### (a) Fixed by `open-packs#TBD-readme-doc`

- 19 `pack.md` and 16 `PROVENANCE.md` files, refused as `retired`.
- `built-in`'s missing pack document: its `pack.md` becomes its `README.md`.
- The merge rules that keep all 19 merged READMEs readable by the codec: drop `pack.md`'s preamble (keep the first
  paragraph only for go and rust), one H1, `PROVENANCE.md`'s H1 dropped and its headings demoted, ATX headings only.
- Open-packs tooling that must move with the files:
  - `scripts/ci/charter_service_check.py`: `NOT_REGISTERED` leaves `README.md` out of the uploaded archive, so the pack
    document would never reach the service;
  - `scripts/ci/validate_pack_schemas.py`;
  - `scripts/ci/check_vocabulary.py`: a row reads `packs/qa/PROVENANCE.md`;
  - `scripts/docs/gen_pack_reference.py`: it reads `pack.md`, and the generated `docs/packs/*.md` must be regenerated
    for the CI `--check`;
  - the recorded tree digests in `packs/<pack>.source.yaml`.

### (b) Fixed by `open-packs#TBD-org-charter-preset`

- 12 `org-charter.yaml` files, refused as `retired`.
- 12 `pack.yaml` files declaring `active_charter`, refused as `pack_unreadable`.
- `internal`'s required floor, carried by `presets/base.yaml` with 18 must-have items. The registration load accepts
  the rewritten preset, and all 18 ids are held in `internal` itself.
- Open-packs tooling, fixtures and text:
  - `NOT_REGISTERED`'s `org-charter.yaml` entry;
  - `tests/fixtures/valid/example-pack/`, which ships `org-charter.yaml` and `active_charter`;
  - the generated `docs/packs/go.md` and `rust.md`;
  - every README, preset description, guide and glossary entry that presents the org charter as live.

### (c) Left over

No open-packs content refusal at the registration load is left once both drafts are applied. The four observations
below are outside open-packs content and are handed to the orchestrator. None is filed.

1. **Charter: the `active_charter` refusal does not name the key.** `FilesystemPackStore.descriptorOf` turns any
   unreadable `pack.yaml` into `pack_unreadable`, "pack.yaml of `<pack>` cannot be read". The schema violations are
   dropped, and the pack's other faults are hidden until the descriptor reads. Spec US3 scenario 2 and FR-010 say the
   key "is refused as an unknown key of the closed descriptor". The load gives an author no pointer to
   `active_charter`. Validate mode was not exercised here.
2. **Charter: an unreadable `README.md` is reported twice by the registration load.** The probe got one `unreadable`
   violation and one `unmodelled` violation for the same file. `WholePackStore.unreportedFiles` drops a retired file
   that is already reported but not a pack document that is already reported. This breaks its own "one fault, one
   violation" rule. No open-packs file triggers it today. It was seen only with a deliberately broken README.
3. **Core divergence (accepted by the spec).** Core reads `pack.md` and `org-charter.yaml` today. Once open-packs applies
   the drafts, core loses `internal`'s `required_*` lists and `skill_namespace: kitty` (the `kitty-` prefix on the
   pack's skills) until core aligns through the generated client. Projects that use `internal` through core should
   expect that gap.
4. **Full registration and the usability run (SC-005's wording).** The dry run covers the registration load only: no
   `POST`, closure or commit. It stands in for neither the full registration nor
   `scripts/usability/service-usability-run.sh`. The script's org-charter lines (research.md §B, `:419-432`) are
   charter work in this mission. Both are for the mission's gates.
