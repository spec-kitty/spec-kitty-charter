---
work_package_id: WP05
title: Glossary reads under an activation
dependencies:
- WP04
requirement_refs:
- FR-011
- FR-012
- FR-013
- FR-019
- NFR-003
- SC-002
planning_base_branch: feat/106-glossary-contexts
merge_target_branch: feat/106-glossary-contexts
branch_strategy: Planning artifacts for this mission were generated on feat/106-glossary-contexts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/106-glossary-contexts unless the human explicitly redirects the landing branch.
subtasks:
- T025
- T026
- T027
- T028
- T029
history:
- at: '2026-11-14T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/
execution_mode: code_change
owned_files:
- contracts/charter/paths/activations_activationId_artifacts_glossary.yaml
- contracts/charter/paths/activations_activationId_artifacts_glossary_contextId.yaml
- contracts/charter/paths/activations_activationId_artifacts_glossary_contextId_terms_termId.yaml
- contracts/charter/schemas/EffectiveGlossaryContextRead.yaml
- contracts/charter/schemas/EffectiveGlossaryTermRead.yaml
- contracts/charter/schemas/Activation.yaml
- contracts/charter/examples/EffectiveGlossary*.yaml
- contracts/charter/scenarios/effective-glossary.feature
- contracts/charter/scenarios/activation-links.feature
- contracts/charter/changes/2026-11-14-glossary-activation-twins.md
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceGlossaryTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectiveTagTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationMapperTest.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/ActivationArtifacts.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/EffectiveGlossaryReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/activations/ActivationsArtifacts.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveGlossariesController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveGlossaryBodies.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/application/EffectiveGlossaryReadsTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/EffectiveGlossariesControllerTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationRoutingTest.java
create_intent:
- contracts/charter/paths/activations_activationId_artifacts_glossary.yaml
- contracts/charter/paths/activations_activationId_artifacts_glossary_contextId.yaml
- contracts/charter/paths/activations_activationId_artifacts_glossary_contextId_terms_termId.yaml
- contracts/charter/schemas/EffectiveGlossaryContextRead.yaml
- contracts/charter/schemas/EffectiveGlossaryTermRead.yaml
- contracts/charter/scenarios/effective-glossary.feature
- contracts/charter/changes/2026-11-14-glossary-activation-twins.md
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceGlossaryTest.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/EffectiveGlossaryReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveGlossariesController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveGlossaryBodies.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/application/EffectiveGlossaryReadsTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/EffectiveGlossariesControllerTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP05 - Glossary reads under an activation

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/glossary-contexts-and-terms-01M7B1PP/`:

- `spec.md`: story 3, FR-011 to FR-013
- `plan.md`: IC-05
- `research.md`: R3, R5 and R7
- `data-model.md`: the state and precedence section

Then study slice A2's effective skill read:

- `EffectiveSkillReads`, `EffectiveSkillsController`, `ActivationArtifacts.skill` and `ActivationsArtifacts.skill`;
- `EffectiveReadService.artifact(…)`, its route switch (~:620) and `heldWinners` (~:642);
- `EffectiveTag.Read` and its `winner`/`winners` guards (~:320, ~:338);
- `ActivationMapper` links (~:155);
- `contracts/charter/scenarios/effective-assets-and-skills.feature`.

Slice C (PR #566) reshaped these classes, so read them on the rebased branch.

## Objective

Each WP04 read gets its activation twin at the same suffix (ADR-0074 decision 12; research R7):

| Request | Answer |
| --- | --- |
| `GET /activations/{a}/artifacts/glossary` (`listEffectiveGlossaryContexts`, tag `EffectiveGlossaries`) | the closure's glossary winners, with state and reason, as `listEffectiveSkills` does |
| `GET /activations/{a}/artifacts/glossary/{contextId}` (`getEffectiveGlossaryContext`) | `EffectiveGlossaryContextRead`: `activationId`, `revision`, `governanceFingerprint`, `kind` (`glossary`), `artifactId`, `packId`, `level`, `policy`, `state`, `reason`, `glossary` (the context) and `_links` |
| `GET /activations/{a}/artifacts/glossary/{contextId}/terms/{termId}` (`getEffectiveGlossaryTerm`) | `EffectiveGlossaryTermRead`: the same envelope, with `term` and the **context's** state and reason; a term has none of its own |
| unknown activation | `404 activation_not_found` |
| context or term not held by any pack of the closure | `404 artifact_not_found`, with WP04's detail texts |
| `If-None-Match` matches | `304` |

The answer covers whatever the state: selected, deselected, denied or not selected (research R7, as skill). The
activation's `_links` gains `glossaries`.

## Subtasks

### T025: Contract

- Write the three path files, the tag `EffectiveGlossaries`, `EffectiveGlossaryContextRead`,
  `EffectiveGlossaryTermRead` and the examples.
- `Activation.yaml` gets its `glossaries` link.
- **Out-of-map rows**, in WP04's registries: `openapi.yaml` (tag and paths, in order), `switch-gate.yaml`,
  `every-operation.feature`, `read-refusals.feature`, `unexpected-failure.feature`, the relation table (`glossaries`)
  and the lint enum. Record them in the commit message as "out-of-map: registry rows for <operationIds>".

### T026: The activations service

- `EffectiveReadService`:
  - the route switch gains `case GLOSSARY` → a new `EffectiveTag.Read.GLOSSARY`;
  - `heldWinners` serves `GLOSSARY` → `GLOSSARIES`;
  - a term read uses `EffectiveTag.Read.GLOSSARY_TERM`. The read resolves the context winner, as for the item, then
    finds the term in it.
- `EffectiveTag`: the new `Read` values `GLOSSARIES`, `GLOSSARY` and `GLOSSARY_TERM`, with the `winner`/`winners`
  guards taking them. The term's `ArtifactQuery` normalizes both ids. No two URLs share a tag.
- `EffectiveReadServiceGlossaryTest` covers:
  - a winner across levels;
  - each state;
  - the term found;
  - the term missing (`artifact_not_found` naming the term);
  - the context missing;
  - 304.

### T027: The `:artifacts-edge` read

- `ActivationArtifacts` gains `glossary(activationId, contextId, inm)`, and `ActivationsArtifacts` implements it
  through `EffectiveReadService.artifact(…, GLOSSARY, …)`.
- `EffectiveGlossaryReads` reads each winner's pack against the fingerprint (`WinnerReads`, as skill), using
  `PacksGlossaries` from WP04 for the context content.
- `EffectiveGlossaryBodies` and `EffectiveGlossariesController`.
- Wiring in the configuration classes is an out-of-map edit of WP04's configuration files; record it.

### T028: Links

- `ActivationMapper`: the activation gains `glossaries`.
- The effective context item links `self`, `collection`, the templated `term`, and `pack-artifact` (the winner's pack
  read). Follow the skill's relation names exactly where they exist.
- Add each new relation to the relation table and the lint enum (out-of-map rows).
- `activation-links.feature` follows.

### T029: Scenarios

Write `contracts/charter/scenarios/effective-glossary.feature` (`@contract`). Packs are inline, in the canonical shape:

- **Whole override**: a project pack whose `spec-kitty-core` copy holds `mission` but not `lane`, over a built-in
  copy holding both.
  - The effective context is the project copy.
  - `…/terms/lane` is `404 artifact_not_found`, naming the term.
  - Positive control: with no project pack, `…/terms/lane` answers.
- **States**: a deselected context answers with state `deselected` and its reason.
- **Siblings**: two packs at one level holding one context id are refused `500 pack_sibling_conflict` (C14).
- **Refusals**: `activation_not_found`, then `artifact_not_found` for the context and for the term.
- **Caching**: `304` on a matching `If-None-Match`.
- **Kind filter**: `?kind=glossary` on the effective list (`listEffectiveArtifacts`).

`ActivationRoutingTest`: the literal paths route to their own controller. Write the change fragment
(`### Added`).

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :activations:test :artifacts-edge:test :contracts:check
./gradlew --max-workers=1 :deployable:test
./gradlew --max-workers=1 verifyAll
```

## Definition of Done

- All three operations are `ready`.
- The whole-override scenario and its positive control pass.
- Each state is answered.
- The links pass `LinkChecks`.
- The complexity gate and the activations and artifacts-edge mutation floors (85) are met.

## Risks and reviewer guidance

- **Reviewer**: check that the term carries the *context's* state and invents none.
- **Reviewer**: check that no tag is shared between the three URLs.
- **Reviewer**: check that the routes follow slice C's shape on `main`.

## Branch Strategy

- **Planning base and merge target**: `feat/106-glossary-contexts`.
- **Mission branch**: `kitty/mission-glossary-contexts-and-terms-01M7B1PP`.
- **Worktrees**: allocated per computed lane (`lanes.json`).
- **Implement with**:
  `spec-kitty agent action implement WP05 --agent claude --mission glossary-contexts-and-terms-01M7B1PP`.
- **Commits**: signed, with no model name and no `Co-Authored-By` trailer. Reference `(#106)`.
- **Integrating `main`**: rebase only. Force-push only your own rebased branch, with a lease.
