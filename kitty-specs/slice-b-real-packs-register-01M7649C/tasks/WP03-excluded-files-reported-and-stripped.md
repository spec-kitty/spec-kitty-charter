---
work_package_id: WP03
title: Excluded files are reported, stripped and judged
dependencies:
- WP02
requirement_refs:
- FR-004
- FR-005
- FR-021
- SC-001
- SC-002
planning_base_branch: feat/466-slice-b-real-packs-register
merge_target_branch: feat/466-slice-b-real-packs-register
branch_strategy: Planning artifacts for this mission were generated on feat/466-slice-b-real-packs-register. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/466-slice-b-real-packs-register unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-b-real-packs-register-01M7649C
base_commit: bcb3a1f8fe04a7eca0d3095ed4b432b0e3a1edd4
created_at: '2026-11-11T21:55:56.367816+00:00'
subtasks:
- T010
- T011
- T012
- T013
- T014
- T015
- T016
phase: Phase 2 - Registration accepts real packs
history:
- at: '2026-11-11T21:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/testFixtures/resources/example-packs/built-in/missions/software-dev/templates/README.md
- contracts/charter/changes/2026-11-11-excluded-files-are-not-held.md
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ExcludedFilesAgreementTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Companions.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
- contracts/charter/schemas/DiagnosticCode.yaml
- contracts/charter/schemas/PackRegistration.yaml
- contracts/charter/scenarios/registration/whole-pack-registration.feature
- contracts/charter/changes/2026-11-11-excluded-files-are-not-held.md
- service/packs/src/testFixtures/resources/example-packs/org-base/docs/**
- service/packs/src/testFixtures/resources/example-packs/built-in/missions/software-dev/templates/README.md
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackCensus.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionFilesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExamplesRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ExcludedFilesAgreementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ExamplePacksTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP03 - Excluded files are reported, stripped and judged

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` (or read `.../agent_profiles/java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/a79c36318b7a506ca2ab26d74705c5e3e406004f/packs/built-in/`) and adopt it. Read
`.kittify/charter/charter.md`.

## Objective

An excluded file (a table `exclusions` rule, or the new `ignored` fallback for unmodelled Markdown) is not pack content:
the registration does not refuse it, does not hold it, and says so. Concretely: classification gains a last-resort
`ignored` section; the load lists excluded files apart from unmodelled ones; registration strips them from the staged
tree before the revision and the commit, and answers a `file_not_held` warning diagnostic per file with the rule's
reason; the whole-pack gate gives them a checked EXCLUDED status. Requirements: FR-004, FR-005, FR-021.

## Context

Read `research.md` D1, D2, §A; `research/squad-plan-point.md` A1, A2, A9, R1, R2, R4, R5, R8, R9; `data-model.md`
(classification table, which this WP makes true). Key code: `S/PackFileKind.java:306-355`,
`S/Companions.java:176-197`, `S/WholePackStore.java:296-332` (`loadStaged`), :786-810 (`unmodelled`),
`M/application/PackRegistrationService.java` (:410 `applyVersionRule`, :604-606 commit),
`S/PackCommitStore.java:507,545,789-806` (`copyTree`), `T/WholePackCensus.java:125-135`,
`T/WholePackExpectation.java:286-341`, `contracts/charter/schemas/PackRegistration.yaml:5,75-89` (`diagnostics`),
`contracts/charter/schemas/DiagnosticCode.yaml`.

## Subtasks

### T010 - The `ignored` section

- `contracts/packs/file-kinds.yaml` (owned by WP02; recorded out-of-map edit): new top-level `ignored:` section and its
  header doc. Rules: `match`, `reason`, both required; any other key fails the table. One rule:
  `match: "**/*.md"`, reason "Author aid: Markdown that no files rule models (#451). Not pack content: not held, not
  served, not refused." Consulted last, after `payloads` and after companion claims; extension matched
  case-insensitively (squad R3).
- `S/PackFileKind.java` (owned by WP02; out-of-map): parse the section; `classify` returns `Excluded` with the rule's
  reason when nothing else matched and an `ignored` rule does; keep `ignored` rules out of `matchingRules` and the
  overlap check (squad R8). WP02's case-variant refusal is consulted before the fallback.
- `S/Companions.java:176-197`: a companion claim wins over the fallback: a path whose only match is an `ignored` rule is
  claimable (skill bodies such as `skills/foo.skill.md`, `skills/README.md`, `NOTES.MD`). A table exclusion still
  refuses a companion, as today.
- Tests (`PackFileKindTest`, owned by WP02; out-of-map): `x/docs/notes.md`, `x/docs/pack.md`, `x/NOTES.MD`,
  `x/.notes.md` → Excluded with the fallback reason; `x/assets/a/guide.md`, `x/templates/writing/t.md`,
  `x/missions/m/templates/spec.md` → payloads; `x/mission-steps/a/b.md` → Retired; no `.md` sample of another section
  classifies as fallback-excluded (invariant test).

### T011 - Load and registration: listed, not refused, stripped

- `S/WholePackStore.java`: `unmodelled` (:790-806) drops files classified Excluded; a new `excluded()` listing on the
  loaded result carries `(file, reason)`. `loadStaged` (:300-332) no longer refuses excluded files.
- Strip: before `revisionOf` and before the commit, remove excluded files from the staged directory (inside the staging
  area only, never from a configured source). Where: `PackRegistrationService` around :604-606, or a `WholePackStore`
  method it calls. The live directory after `POST`/`PUT` holds no excluded file (store test).
- A `PUT` that differs only in an excluded file is UNCHANGED (same revision) and still reports the diagnostic.
- Update the `RetiredFilesTest` README overlap comment (:79-84, "never read either way": it is now excluded and not
  refused; owned by WP02, out-of-map) (squad R9).

### T012 - `file_not_held` diagnostic

- `contracts/charter/schemas/DiagnosticCode.yaml`: add `file_not_held` (severity warning) with a description; find its
  Java twin (grep `DiagnosticCode`) and add it there; check any vocabulary-equality test.
- The registration answer's `diagnostics` (`PackRegistration.yaml:75-89`) carries one entry per excluded file: pointer
  = the file, detail = `<file>: not held (<reason>)`.
- `PackRegistration.yaml:5` and `contracts/charter/paths/packs_packId.yaml:33` (the latter owned by WP05;
  out-of-map): "holds exactly the staged tree" becomes "holds the staged tree less the files it reports as not held";
  "an unmodelled file" in the 422 list stays (an unclassified file is still refused).
- Fragment `contracts/charter/changes/2026-11-11-excluded-files-are-not-held.md`, one `### Changed` block: excluded
  files (table exclusions and unmodelled Markdown) are no longer refused; they are not held and are reported as
  `file_not_held` warnings; core's seam: a registration that used to answer 422 for such files now answers 2xx with
  warnings.

### T013 - The gate judges excluded files

- `T/WholePackCensus.java:125-135`: classify through `PackFileKind`; an Excluded file gets a new census kind
  `excluded:<rule>` (not `other:<segment>`) and status EXCLUDED.
- `T/WholePackExpectation.java:286-341`: EXCLUDED is checked: the file is absent from the tree the store wrote. It is
  never red and never needs an `expected-red.yaml` or gate `exclusions.yaml` entry (NFR-004).
- `WholePackExpectationTest`: an ignored note → EXCLUDED; a `notes.txt` control → red `other:notes.txt` (unexpected).

### T014 - Validate mode and the agreement test

- `S/FileSystemPackInspector.java:345-383`: Markdown excluded by the fallback is listed like excluded YAML (with its
  `exclusionClass`/reason), never read, never a finding.
- New `T/ExcludedFilesAgreementTest.java`: one pack holding a skill body, a toolguide guide, `docs/notes.md`,
  `missions/x/templates/README.md` and `missions/x/templates/plan-field-declaration.yaml`; assert classification,
  validate mode, load, digest (`PackFileSet.digested`), gate census and `loadStaged` agree: companions held and
  digested; excluded files not held, not digested, not refused, reported (squad A9).

### T015 - Registration scenarios

`contracts/charter/scenarios/registration/whole-pack-registration.feature` (look at :345-381 for the `notes.txt`
scenario and reuse its steps; add any new step in the deployable bdd steps and `scenarios/README.md`):

- A pack with `docs/notes.md`, `missions/x/README.md` and `missions/x/templates/README.md` registers; the answer lists
  three `file_not_held` diagnostics; its revision equals the same pack without them.
- A pack with root `pack.md` and `PROVENANCE.md` → `422 validation_failed` with exactly two violations of code
  `retired`, none `unmodelled`.
- A pack with `notes.txt` stays `422` with code `unmodelled` (control).

### T016 - Examples and flipped tests

- Add `service/packs/src/testFixtures/resources/example-packs/org-base/docs/authoring-notes.md` (an author note) and
  `built-in/missions/software-dev/templates/README.md` (a table-excluded README), so the gate and the examples
  round trip prove the rules on canonical packs.
- `ExamplePacksTest:80` (`unmodelled()` empty): add a positive assertion that the note is excluded and not held.
- `WholePackExamplesRoundTripTest:176-179`: drop the README-name filter that becomes dead.
- `WholePackStoreTest:320-330, 335-351, 543-551`: root `README.md` is the document; use `notes.txt` for unmodelled
  samples; assert excluded files are listed as excluded.
- `CompanionFilesTest:170-185`: rename the occupied file to `notes.md` for the "taken" case; add a separate test that a
  companion resolving to the root `README.md` is refused as the pack document.
- `DomainRoundTrip.java`, `WholePackRoundTripTest.java`: only if the census change needs it.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :packs:test
./gradlew --max-workers=1 :packs:wholePackRoundTrip
./gradlew --max-workers=1 :contracts:check
./gradlew --max-workers=1 :deployable:test --tests '*CharterContractSuite*'
```

## Branch Strategy

Planning base and merge target: `feat/466-slice-b-real-packs-register`; worktrees per lane. Implement with
`spec-kitty agent action implement WP03 --agent claude --mission slice-b-real-packs-register-01M7649C`. Commits signed,
no model trailers, refs `(#451)`.

## Definition of Done

FR-004, FR-005, FR-021 hold on the HTTP path (scenarios) and in the store; gate green with the example note; no entry
added to `expected-red.yaml` or the gate's `exclusions.yaml`.

## Risks and reviewer guidance

The fallback must never capture a file another rule or a companion claim takes. Stripping touches only staging. Check
every flipped test changed meaning deliberately.
