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
subtasks:
- T012
- T013
- T014
- T015
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
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetExactnessTest.java
- service/packs/src/test/resources/whole-pack/expected-red.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
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

## Subtask T012 — The adapter takes its kinds from the registry

1. In `DomainRepositoryService`, change `private void saveArtifact(CharterArtifact)` to package-private, and add a
   one-line javadoc: "saves one artifact of any bound kind; the gate adapter uses it".
2. In `DomainRoundTrip`:
   - `ARTEFACT_KINDS` becomes `KindBindings.all()` mapped by `KindBinding::schema`, collected to an unmodifiable set.
     Keep the name and its use in `MODELLED`.
   - `writeAll`: after the descriptor, `pack.artifacts().forEach(to::saveArtifact)`. This replaces the five lines,
     keeps the kind order of the aggregate, and writes everything a pack holds.
   - `writtenByKindAndId`: for each binding, match `fileName.endsWith("." + binding.fileToken() + ".yaml")`, and key
     the entry `binding.schema() + "/" + id`. The lookup in `artefact(...)` stays `kind + "/" + elementId`, since
     `kind` is the schema name.
   - Update the class javadoc: "the bound kinds" instead of "the five modelled artefact kinds"; written as
     `<id>.<fileToken>.yaml`.
3. `DomainRoundTripTest`: extend the expected modelled-kind list with `mission-step-contract`. Better: assert it
   equals the registry's schemas plus the descriptor and the shard, so the next kind needs no edit. Keep every other
   assertion. If a lossy-writer double names a kind, leave it alone.

## Subtask T013 — Expected red

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
4. **Positive control.** A `SerializationStrategy` double that delegates to the YAML strategy but drops `guidance`
   (or `fail_open`) from the written text must make at least one corpus comparison fail. Mirror the gate's
   "lossy-writer" control (`aLossyWriterTurnsTheRealCorpusRed`) so the test cannot pass vacuously.

## Subtask T015 — Generated pack tree and exactness tests

`GeneratedPackTree` plants a `DECOY_STEP_CONTRACT` file, which is now a loaded kind. Update:

- the decoy's classification;
- the expected loaded/digested lists in `PackFileSetExactnessTest`.

The step-contract file is now loaded and digested, after the five older kinds. Every other digest assertion stays.
Read the failure messages first; change only what the new binding explains. Note: the decoy content may need to be
a schema-valid step contract, or the store records a violation. Keep the test's intent.

## Subtask T016 — Run

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew :packs:test
./gradlew :packs:wholePackRoundTrip   # or the task name in service/packs/build.gradle.kts
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
