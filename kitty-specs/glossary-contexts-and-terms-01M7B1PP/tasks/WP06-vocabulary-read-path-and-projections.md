---
work_package_id: WP06
title: 'The activation''s vocabulary: read path and projections'
dependencies:
- WP05
requirement_refs:
- FR-014
- FR-015
- FR-016
- FR-017
- NFR-005
- SC-004
planning_base_branch: feat/106-glossary-contexts
merge_target_branch: feat/106-glossary-contexts
branch_strategy: Planning artifacts for this mission were generated on feat/106-glossary-contexts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/106-glossary-contexts unless the human explicitly redirects the landing branch.
subtasks:
- T030
- T031
- T032
- T033
- T034
history:
- at: '2026-11-14T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/
execution_mode: code_change
owned_files:
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/VocabularyReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/ActiveTerm.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/ContextsInForce.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/ContextiveProjection.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/TabooProjection.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksVocabulary.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/application/VocabularyReadsTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/application/ContextiveProjectionTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/application/TabooProjectionTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/packs/PacksVocabularyTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceContextsInForceTest.java
create_intent:
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/VocabularyReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/ActiveTerm.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/ContextsInForce.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/ContextiveProjection.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/TabooProjection.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksVocabulary.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/application/VocabularyReadsTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/application/ContextiveProjectionTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/application/TabooProjectionTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/packs/PacksVocabularyTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceContextsInForceTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP06 - The activation's vocabulary: read path and projections

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/glossary-contexts-and-terms-01M7B1PP/`:

- `spec.md`: story 4, FR-014 to FR-017, NFR-005 and SC-004
- `plan.md`: IC-06
- `research.md`: R5 and R6
- `data-model.md`

Then read [ADR-0076](../../../docs/adr/0076-the-activation-vocabulary-reads-and-projections.md) in full. It is the
source of record for this WP and WP07.

## Objective

Build the service side of ADR-0076, without HTTP:

- the glossary contexts in force under an activation;
- the active terms;
- the Contextive document and the taboo list, as pure functions of the active terms.

Each read opens one activation session and reads each winning pack at most once, against the closure fingerprint
(NFR-005). WP07 adds the contract and the controller.

## Rules (ADR-0076 decisions 1 to 3, 5 to 7)

- **In force**: the winning copy of each `glossary` context whose effective state is in force. A deselected, denied
  or unselected context contributes nothing.
- **Override**: a context overrides across levels **whole**. A built-in term that the project copy lacks is not in
  force, and nothing is merged per term.
- **Status**: every term of a context in force is listed, whatever its status, and carries the status.
- **Order**:
  - terms by context id, then term id;
  - Contextive contexts by id, and their terms by term id;
  - taboo entries by word, then term URN.
- **Taboos**: one entry per avoided word per term, so a word avoided by two terms appears twice.
- **Contextive mapping**:
  - context `name` → `name`;
  - `description` → `domainVisionStatement`;
  - `applies_to.paths` → `paths` (when present);
  - per term: `name`, `definition`, `examples` and `aliases` when present, and `meta: {urn, status}`.

## Subtasks

### T030: The contexts in force

- The `ActivationArtifacts` port (owned by WP05) gains `contextsInForce(activationId, inm)`. That is an out-of-map
  edit; record it. It returns a `ContextsInForce` value: the activation id, revision and governance fingerprint, and
  per context in force its id, winning pack and level.
- `ActivationsArtifacts` implements it through a new `EffectiveReadService` method that lists the `GLOSSARY` winners
  with a state in force. That is an out-of-map edit of WP05's files; record it.
  - Reuse the effective list query filtered to `glossary` and state in force. Do not walk the closure a second time.
- `EffectiveReadServiceContextsInForceTest` covers:
  - selected, deselected and denied;
  - the project override;
  - an empty activation;
  - `activation_not_found`.

### T031: `PacksVocabulary`

- An adapter in `infra.packs` reads the contexts of each winning pack **once**, checked against the closure
  fingerprint as `Fingerprints.require` does for skill. A mismatch is `stale_input`, as A2's payload read does.
- It returns the `GlossaryContext` domain objects, keyed by id.
- `PacksVocabularyTest` counts pack reads with a counting fake of the packs port.
  - Two contexts in one pack: 1 read.
  - Two packs: 2 reads.

  This is NFR-005's proof on the production path.

### T032: `VocabularyReads`

- `VocabularyReads.terms(activationId, inm)` returns `ActiveTermList`: the activation fields, and the list of
  `ActiveTerm` (`urn`, `contextId`, `termId`, the term's fields, `packId`, `level`), ordered.
- It composes T030 and T031: one session, then one read per winning pack.
- `VocabularyReadsTest`:
  - **Whole override**: the project copy without `lane`, over the built-in copy with `lane`: `lane` is absent.
  - **Positive control**: without the project pack, `lane` is present.
  - **Shared name**: `Mission` in two contexts gives two terms with different URNs.
  - **Status**: deprecated and draft terms are listed, with their status.
  - **Empty**: no context in force gives an empty list.

### T033: The projections

- `ContextiveProjection.of(List<ActiveTerm>, Map<contextId, GlossaryContext>)` returns an ordered tree of plain
  values. These are records, not YAML; WP07 renders them.
- `TabooProjection.of(List<ActiveTerm>)` returns the ordered entries.
- Both are pure functions with no I/O.
- Tests:
  - **Contextive**: the field mapping, including `paths`. Optional members are omitted when empty.
  - **Taboos**: a word shared by two terms appears twice; the order; a term with no `avoid` gives no entry.
  - **Cross-check (SC-004)**:
    - over one fixture, the set of `meta.urn` in the Contextive tree equals the set of active term URNs;
    - the multiset of taboo `(word, term)` pairs equals the flattened `avoid` entries of the active terms.

### T034: Tags

- `EffectiveTag.Read` gains `VOCABULARY_TERMS`, `VOCABULARY_CONTEXTIVE` and `VOCABULARY_TABOOS`, with the guards.
  That is an out-of-map edit of WP05's `EffectiveTag`; record it.
- Each read has its own ETag, derived from the activation revision and governance fingerprint, as the other effective
  reads are.
- `EffectiveTagTest` rows: the three tags differ from each other and from the glossary item tags.
- Refusals are judged before the 304 (ADR-0076 d8): an unknown activation, and the closure's `500` refusals.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :activations:test :artifacts-edge:test
./gradlew --max-workers=1 verifyAll
```

## Definition of Done

- The four tests of T030 to T033 pass, including the override's positive control and the SC-004 cross-check.
- The pack-read count is pinned.
- The tags are distinct.
- The complexity gate and the mutation floors (85) are met. The projections are small pure functions, which helps
  both.

## Risks and reviewer guidance

- **Reviewer**: check that no per-term merge crept in.
- **Reviewer**: check that the read count is asserted on the production path, not on a test double of the service
  itself.
- **Reviewer**: check that the projections take no I/O.

## Branch Strategy

- **Planning base and merge target**: `feat/106-glossary-contexts`.
- **Mission branch**: `kitty/mission-glossary-contexts-and-terms-01M7B1PP`.
- **Worktrees**: allocated per computed lane (`lanes.json`).
- **Implement with**:
  `spec-kitty agent action implement WP06 --agent claude --mission glossary-contexts-and-terms-01M7B1PP`.
- **Commits**: signed, with no model name and no `Co-Authored-By` trailer. Reference `(#106)`.
- **Integrating `main`**: rebase only. Force-push only your own rebased branch, with a lease.
