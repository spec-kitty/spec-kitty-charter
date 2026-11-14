---
work_package_id: WP07
title: 'The activation''s vocabulary: contract and endpoints'
dependencies:
- WP06
requirement_refs:
- FR-013
- FR-014
- FR-016
- FR-017
- FR-019
- NFR-003
- SC-004
planning_base_branch: feat/106-glossary-contexts
merge_target_branch: feat/106-glossary-contexts
branch_strategy: Planning artifacts for this mission were generated on feat/106-glossary-contexts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/106-glossary-contexts unless the human explicitly redirects the landing branch.
subtasks:
- T035
- T036
- T037
- T038
- T039
history:
- at: '2026-11-14T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/
execution_mode: code_change
owned_files:
- contracts/charter/paths/activations_activationId_vocabulary_terms.yaml
- contracts/charter/paths/activations_activationId_vocabulary_contextive.yaml
- contracts/charter/paths/activations_activationId_vocabulary_taboos.yaml
- contracts/charter/schemas/ActiveTermList.yaml
- contracts/charter/schemas/ActiveTerm.yaml
- contracts/charter/schemas/TabooList.yaml
- contracts/charter/schemas/TabooEntry.yaml
- contracts/charter/examples/ActiveTermList*.yaml
- contracts/charter/examples/TabooList*.yaml
- contracts/charter/examples/Contextive*.yaml
- contracts/charter/scenarios/vocabulary.feature
- contracts/charter/changes/2026-11-14-activation-vocabulary.md
- service/artifacts-edge/build.gradle.kts
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/VocabularyController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/VocabularyBodies.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/ContextiveYaml.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/VocabularyControllerTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/ContextiveYamlTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/VocabularySteps.java
- docs/development/rest-resource-conventions.md
create_intent:
- contracts/charter/paths/activations_activationId_vocabulary_terms.yaml
- contracts/charter/paths/activations_activationId_vocabulary_contextive.yaml
- contracts/charter/paths/activations_activationId_vocabulary_taboos.yaml
- contracts/charter/schemas/ActiveTermList.yaml
- contracts/charter/schemas/ActiveTerm.yaml
- contracts/charter/schemas/TabooList.yaml
- contracts/charter/schemas/TabooEntry.yaml
- contracts/charter/scenarios/vocabulary.feature
- contracts/charter/changes/2026-11-14-activation-vocabulary.md
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/VocabularyController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/VocabularyBodies.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/ContextiveYaml.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/VocabularyControllerTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/ContextiveYamlTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/VocabularySteps.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP07 - The activation's vocabulary: contract and endpoints

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/glossary-contexts-and-terms-01M7B1PP/`:

- `spec.md`: story 4
- `plan.md`: IC-06
- `research.md`: R6

Then read [ADR-0076](../../../docs/adr/0076-the-activation-vocabulary-reads-and-projections.md), decisions 4 to 9.

Read the YAML precedent:

- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/InfoController.java:28,106`: a `String` body
  answered as `application/yaml`;
- `contracts/charter/paths/openapi.yaml:38`: `type: string`;
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/CharterSteps.java`: `everySuccessAnswerIsLinked`
  skips non-JSON bodies, and the step "the response body is the text:" exists;
- `LinkChecks.java:16`, which judges JSON bodies only.

## Objective

Expose WP06's reads as three contract operations (tag `Vocabulary`):

| Request | Answer |
| --- | --- |
| `GET /activations/{a}/vocabulary/terms` (`getActivationVocabularyTerms`) | `200 ActiveTermList` (JSON): `activationId`, `revision`, `governanceFingerprint`, `terms: [ActiveTerm]` and `_links{self, activation}`. Each `ActiveTerm` carries `_links{effective-term, effective-glossary}` |
| `GET /activations/{a}/vocabulary/contextive` (`getActivationVocabularyContextive`) | `200 application/yaml`: a Contextive v1 document (ADR-0076 d6) |
| `GET /activations/{a}/vocabulary/taboos` (`getActivationVocabularyTaboos`) | `200 TabooList` (JSON): `activationId`, `revision`, `governanceFingerprint`, `entries: [{word, term, name}]` and `_links{self, activation}` |
| `Accept` excluding the operation's media type | `406`, as the REST conventions say |
| unknown activation | `404 activation_not_found` |
| matching `If-None-Match` | `304` |

Each 200 carries `ETag` and `Charter-Source-Fingerprint`. Refusals are judged before the 304. The activation gains
the links `vocabulary-terms`, `vocabulary-contextive` and `vocabulary-taboos`.

## Subtasks

### T035: Contract

- Write the three path files and the tag `Vocabulary`.
- Write the schemas `ActiveTermList`, `ActiveTerm`, `TabooList` and `TabooEntry`, closed, with `_links`.
- The Contextive response is `content: application/yaml: schema: {type: string}`, with an example that is a real
  Contextive v1 document of two contexts. The description cites ADR-0076 decision 6 and lists the field mapping.
- Write examples for every operation.
- **Out-of-map rows**, in WP04's registries:
  - `openapi.yaml` and `switch-gate.yaml`;
  - `every-operation.feature`, `read-refusals.feature` and `unexpected-failure.feature`;
  - the relation table and the lint enum (`vocabulary-*`, `effective-term`, `effective-glossary`);
  - `CharterRefusalCode.yaml`'s raised map.

  Also `Activation.yaml`, owned by WP05: its three links. Record each in the commit message.

### T036: The controller

- `VocabularyController implements VocabularyApi` delegates to WP06's `VocabularyReads` and projections.
- `VocabularyBodies` maps to the DTOs.
- `ContextiveYaml` renders WP06's Contextive tree with `snakeyaml-engine`:
  - **dependency**: add `catalog.lib("snakeyaml-engine")` to `service/artifacts-edge/build.gradle.kts`. It is
    already in `gradle/libs.versions.toml` and the verification metadata, so check
    `gradle/verification-metadata.xml` and confirm no new artifact is fetched (DIRECTIVE_051);
  - **output**: block style, keys in the ADR's order, UTF-8, and a stable rendering: same input, same bytes.
    `ContextiveYamlTest` pins one document byte for byte, and parses it back to check the structure.
- The 406 rule follows how Spring and the existing controllers answer an unacceptable `Accept`. Read
  `RefusalHandler`, and do not invent a refusal code.
- `ETag`, `If-None-Match` and the fingerprint header follow the other effective controllers exactly
  (`EffectiveSkillsController`).

### T037: Links

- `ActivationMapper` (WP05's file, an out-of-map edit) gains `vocabulary-terms`, `vocabulary-contextive` and
  `vocabulary-taboos`.
- Each `ActiveTerm` links its effective term read and effective context read.
- Every new relation goes in the relation table and the lint enum. `LinkChecks` passes.

### T038: `vocabulary.feature` (`@contract`)

Packs are inline, in the canonical shape. Include:

1. **Whole override**: a project copy of `spec-kitty-core` without `lane`, over the built-in copy with it. `terms`
   lacks `term:spec-kitty-core/lane`.
2. **Positive control**: the same activation without the project pack lists `lane`.
3. **Shared name**: `Mission` in two contexts gives two terms.
4. **Status**: a deprecated term is listed, with `status: deprecated`.
5. **Contextive**: `Content-Type` is `application/yaml`. The body parses as YAML with the Contextive v1 field set:
   each context has `name`, `domainVisionStatement` and `paths` when present; each term has `name`, `definition`,
   `examples`, `aliases` and `meta.urn`/`meta.status`. Add the step to a new `VocabularySteps` class; do not grow
   `CharterSteps`.
6. **Taboos**: a word avoided by two terms appears twice, in order.
7. **Cross-read (SC-004)**: over the same activation, Contextive's `meta.urn` set equals `terms`' URN set, and the
   taboo pairs equal the flattened `avoid` entries.
8. **Empty**: no glossary in force gives `terms: []`, `entries: []` and `contexts: []`.
9. **304** on a matching `If-None-Match`, for each read.
10. **406** for `Accept: application/json` on Contextive.
11. **`activation_not_found`**.
12. The activation's three links resolve.

Write the change fragment `2026-11-14-activation-vocabulary.md` (`### Added`).

### T039: REST conventions

`docs/development/rest-resource-conventions.md`, under "Media types": the success rule gains its exceptions,
`/openapi.yaml` and `…/vocabulary/contextive` (`application/yaml`, ADR-0076 d6).

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :contracts:check :artifacts-edge:test
./gradlew --max-workers=1 :deployable:test
./gradlew --max-workers=1 verifyAll
python3 scripts/docs/check_docs.py all
```

## Definition of Done

- All three operations are `ready`, and `vocabulary.feature` passes.
- The Contextive bytes are stable and pinned.
- No new artifact enters the build.
- The links pass `LinkChecks`.
- The complexity gate and the mutation floor (85) are met.

## Risks and reviewer guidance

- **Reviewer**: check `verification-metadata.xml` is unchanged, or only gains the module's existing coordinates.
- **Reviewer**: check that the 406 behaviour is Spring's standard path.
- **Reviewer**: check the scenario's positive controls.

## Branch Strategy

- **Planning base and merge target**: `feat/106-glossary-contexts`.
- **Mission branch**: `kitty/mission-glossary-contexts-and-terms-01M7B1PP`.
- **Worktrees**: allocated per computed lane (`lanes.json`).
- **Implement with**:
  `spec-kitty agent action implement WP07 --agent claude --mission glossary-contexts-and-terms-01M7B1PP`.
- **Commits**: signed, with no model name and no `Co-Authored-By` trailer. Reference `(#106)`.
- **Integrating `main`**: rebase only. Force-push only your own rebased branch, with a lease.
