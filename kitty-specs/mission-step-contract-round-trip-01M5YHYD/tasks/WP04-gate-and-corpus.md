---
work_package_id: WP04
title: Gate and corpus
dependencies:
- WP03
requirement_refs:
- FR-005
- FR-006
- NFR-002
- NFR-003
planning_base_branch: feat/mission-step-contract-round-trip
merge_target_branch: feat/mission-step-contract-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/mission-step-contract-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/mission-step-contract-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-mission-step-contract-round-trip-01M5YHYD
base_commit: df887d765e2f8deb33f1edcab593c02c8d6ba015
created_at: '2026-10-27T12:01:11.559167+00:00'
subtasks:
- T012
- T014
- T016
phase: Phase 3 - User Stories 1 and 3
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractCorpusTest.java
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractCorpusTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP04 — Gate and corpus

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Turn `mission-step-contract` green in the #187 whole-pack gate, and prove that all 28 pinned step contracts
round-trip, including the 7 in `qa`, which no pack-level gate loads (spec FR-005, FR-006, NFR-002; SC-001,
SC-002).

The gate adapter learns its artifact kinds from the registry, so each sibling Wave 1 kind turns green by binding
itself, without editing the adapter (research D6).

## Branch Strategy

Planning base and merge target: `feat/mission-step-contract-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP04 --agent claude --mission mission-step-contract-round-trip-01M5YHYD`.

## Context

`DomainRoundTrip.java` (test scope) hard-codes three things:

- `ARTEFACT_KINDS` (gate kind names, which are **schema** names);
- `writeAll`, one `pack.<kind>().forEach(to::save)` per kind;
- `writtenByKindAndId`, which matches `"." + kind + ".yaml"`. That uses the schema name as a file token, which is
  wrong for this kind.

The gate runs over the pinned built-in pack (`WholePackRoundTripTest`, the `wholePackRoundTrip` Gradle task) and
over every open-packs pack with a `pack.yaml` (`WholePackOpenPacksRoundTripTest`). See how both locate the
open-packs checkout (`OpenPacksCorpus.java`), and reuse that.

## Subtask T012 — Confirm the adapter models the kind

WP01 made `DomainRoundTrip` take its kinds from the registry; with WP03's binding it now models
`mission-step-contract`. Confirm with a targeted `DomainRoundTripTest` case: a fixture pack holding one
`*.step-contract.yaml` in a nested directory yields a `Written` outcome for that path (no edit to the adapter).

## (Moved to WP03) T013 — Expected red

Remove the `mission-step-contract` entry from `service/packs/src/test/resources/whole-pack/expected-red.yaml`. Leave
every other entry byte for byte (collision rule). The ratchet fails with `BASELINED_MODELLED_KIND` until this is
done, which is the proof the kind is modelled.

## Subtask T014 — Corpus test with a positive control

`MissionStepContractCorpusTest`:

1. Find every `*.step-contract.yaml` under the pinned open-packs `packs/` directory, the same way
   `WholePackOpenPacksRoundTripTest` locates the checkout. If the checkout is absent, skip with an assumption; CI
   always has it.
2. Assert the count is **28**. A pin move that changes the count must be looked at, not silently accepted.
3. For each file:
   - `strategy.read(MissionStepContractBinding.INSTANCE, text, path)` must be `FileRead.Read`;
   - map to the entity (`toEntity(new PackIdentifier("probe"), dso)`) and back (`toDso`);
   - `strategy.write`;
   - compare the source text and the written text with `MeaningComparator`, which must report no difference;
   - read the written text again, which must be `FileRead.Read`.

   Use a `@TestFactory`/`DynamicTest` or `@ParameterizedTest` per file so a failure names the file.
4. **Positive control.** A lossy format double (delegates to the YAML strategy, drops `fail_open` and `guidance` from
   the written text) must yield differences at `gates[0].fail_open` for `review` and at `steps[i].guidance` for every
   step carrying guidance. Mirror `aLossyWriterTurnsTheRealCorpusRed`.
5. **Coverage assertions** over the corpus: exactly 28 files (21 + 7); at least 1 `gates`; at least 10 `guidance`;
   at least 101 `delegates_to`. A pin move that changes them is looked at, not absorbed.
6. Tag the test like the other open-packs tests (`@Tag("open-packs")`, see `build.gradle.kts` `openPacksRoundTrip`,
   which `check` depends on) so a missing checkout **fails** in that task instead of skipping.

## (Moved to WP03) T015 — Generated pack tree and exactness tests

`GeneratedPackTree` plants a `DECOY_STEP_CONTRACT` file, which is now a loaded kind. Known touch points (post-plan
squad): `boundKinds()` feeds `Kind.valueOf(omitted.name())`, which throws for `MISSION_STEP_CONTRACT`; `Kind.token` is
used both as the kind name and the file token (`loadedPath`); `typed`, `idOf` and `contentOf` are exhaustive switches.
So: give `Kind` a separate file token (default the token), add `MISSION_STEP_CONTRACT` with file token
`step-contract` and a minimal schema-valid `contentOf`, add its `typed` entry, delete the decoy row. Keep these as
append-only rows so #190 adds one more. Update:

- the decoy's classification;
- the expected loaded/digested lists in `PackFileSetExactnessTest`.

The step-contract file is now loaded and digested, after the five older kinds. Every other digest assertion stays.
Read the failure messages first; change only what the new binding explains. Note: the decoy content may need to be
a schema-valid step contract, or the store records a violation. Keep the test's intent.

## Subtask T016 — Run

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew :packs:test
./gradlew :packs:wholePackRoundTrip   # NOT part of check/verifyAll: run it explicitly; its report is PR evidence (SC-002)
./gradlew verifyAll
```

Record the gate report's `mission-step-contract` row (expected 21 green, 0 expected red, 0 red) for WP05's
changelog. If any pinned file is red, read the first difference:

- A service defect is fixed here.
- A pack inconsistency is **not** worked around. It is filed in `spec-kitty-open-packs` per ADR-0036 decision 5, and
  the file is listed in `expected-red.yaml` with that issue.

## Definition of Done

- The gate is green with this kind modelled; the corpus test passes 28 of 28; the positive control fails as
  intended; `verifyAll` is green on Java 25.
- One commit: `test(packs): the whole-pack gate models the bound kinds from the registry; step contracts green (#191)`.

## Reviewer guidance

- The adapter change must not alter the outcome for the five older kinds. Compare the gate report before and after:
  same counts.
- The positive control must fail for the reason it claims.
