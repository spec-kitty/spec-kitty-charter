---
work_package_id: WP04
title: Glossary reads in a pack
dependencies:
- WP02
requirement_refs:
- FR-007
- FR-008
- FR-009
- FR-010
- FR-012
- FR-019
- NFR-003
- SC-001
- SC-002
planning_base_branch: feat/106-glossary-contexts
merge_target_branch: feat/106-glossary-contexts
branch_strategy: Planning artifacts for this mission were generated on feat/106-glossary-contexts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/106-glossary-contexts unless the human explicitly redirects the landing branch.
subtasks:
- T019
- T020
- T021
- T022
- T023
- T024
history:
- at: '2026-11-14T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/
execution_mode: code_change
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/paths/packs_packId_artifacts_glossary.yaml
- contracts/charter/paths/packs_packId_artifacts_glossary_contextId.yaml
- contracts/charter/paths/packs_packId_artifacts_glossary_contextId_terms_termId.yaml
- contracts/charter/parameters/ContextId.yaml
- contracts/charter/parameters/TermId.yaml
- contracts/charter/parameters/ArtifactKind.yaml
- contracts/charter/parameters/_index.yaml
- contracts/charter/schemas/GlossaryContextList.yaml
- contracts/charter/schemas/GlossaryContextSummary.yaml
- contracts/charter/schemas/GlossaryContext.yaml
- contracts/charter/schemas/GlossaryTerm.yaml
- contracts/charter/schemas/ArtifactKindToken.yaml
- contracts/charter/schemas/Artifact.yaml
- contracts/charter/schemas/_index.yaml
- contracts/charter/examples/GlossaryContextList*.yaml
- contracts/charter/examples/Artifact.glossary*.yaml
- contracts/charter/examples/GlossaryTerm*.yaml
- contracts/charter/examples/_index.yaml
- contracts/charter/scenarios/glossary.feature
- contracts/charter/scenarios/artifacts.feature
- contracts/charter/scenarios/switch-gate.yaml
- contracts/charter/scenarios/every-operation.feature
- contracts/charter/scenarios/read-refusals.feature
- contracts/charter/scenarios/unexpected-failure.feature
- contracts/charter/changes/2026-11-14-glossary-reads-in-a-pack.md
- contracts/lint/ruleset.yaml
- docs/development/hypermedia-hateoas.md
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ForeignArtifactRead.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ForeignArtifactReadTest.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/GlossaryReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/GlossaryView.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksGlossaries.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/GlossaryBodies.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/GlossariesController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/config/ArtifactsApplicationConfiguration.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/config/ArtifactsApiConfiguration.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/config/ArtifactsPacksConfiguration.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/GlossariesControllerTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/GlossaryBodiesTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/packs/PacksGlossariesTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/packs/ExampleVocabularyReadTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/PackArtifactRoutingTest.java
create_intent:
- contracts/charter/paths/packs_packId_artifacts_glossary.yaml
- contracts/charter/paths/packs_packId_artifacts_glossary_contextId.yaml
- contracts/charter/paths/packs_packId_artifacts_glossary_contextId_terms_termId.yaml
- contracts/charter/parameters/ContextId.yaml
- contracts/charter/parameters/TermId.yaml
- contracts/charter/schemas/GlossaryContextList.yaml
- contracts/charter/schemas/GlossaryContextSummary.yaml
- contracts/charter/schemas/GlossaryContext.yaml
- contracts/charter/schemas/GlossaryTerm.yaml
- contracts/charter/scenarios/glossary.feature
- contracts/charter/changes/2026-11-14-glossary-reads-in-a-pack.md
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/GlossaryReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/GlossaryView.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksGlossaries.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/GlossaryBodies.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/GlossariesController.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/GlossariesControllerTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/GlossaryBodiesTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/packs/PacksGlossariesTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/packs/ExampleVocabularyReadTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP04 - Glossary reads in a pack

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/glossary-contexts-and-terms-01M7B1PP/`:

- `spec.md`: story 2, FR-007 to FR-012
- `plan.md`: IC-04
- `research.md`: R3, R7 and R8
- `data-model.md`: the contract read models

Then read [ADR-0074](../../../docs/adr/0074-the-remaining-cutover-reads-and-their-paths.md), decisions 9 to 14.

Then study slice A2's skill read: `kitty-specs/slice-a2-cutover-reads-01M78RJD/tasks/WP05-skill-reads-in-a-pack.md`,
`SkillsController`, `SkillReads`, `PacksSkills` and `SkillBodies`, and `contracts/charter/scenarios/skills.feature`.

## Objective

Serve a pack's glossary contexts and terms (#106, R12; ADR-0074 decisions 9 to 14).

| Request | Answer |
| --- | --- |
| `GET /packs/{p}/artifacts/glossary` (`listPackGlossaryContexts`, tag `Glossaries`) | `200 GlossaryContextList`: `{packId, items: [GlossaryContextSummary{id, name, description, tags?, termCount, _links.self}], _links}`, in id order, unpaged |
| `GET /packs/{p}/artifacts?kind=glossary` | the pack's artifact list, with the contexts |
| `GET /packs/{p}/artifacts/glossary/{contextId}` (`getPackGlossaryContext`) | `200 Artifact` with member `glossary`: the whole context in camel case, every term, `_links` `self`, `collection` and a templated `term` |
| `GET /packs/{p}/artifacts/glossary/{contextId}/terms/{termId}` (`getPackGlossaryTerm`) | `200 GlossaryTerm{id, name, definition, status, examples?, aliases?, avoid?, seeAlso?, urn, contextId, _links{self, up}}` |
| unknown pack | `404 pack_not_found` |
| context not held | `404 artifact_not_found`, detail ``glossary context `<id>` is not held`` |
| term not held | `404 artifact_not_found`, detail ``term `<termId>` is not held in glossary context `<contextId>` `` |
| id breaks the grammar | `400` from the parameter pattern (as `AssetOrSkillId` does) |

The ETag of each read is the pack revision, as for skill. **This WP owns the shared contract registries.** WP05 and
WP07 add rows to them as recorded out-of-map edits.

## Subtasks

### T019: Contract

- Write three path files, in the `skill` path files' style.
- Write `parameters/ContextId.yaml` and `TermId.yaml`, with the letter-first slug pattern and `maxLength` 64, as
  `AssetOrSkillId.yaml`.
- Write the schemas `GlossaryContextList`, `GlossaryContextSummary`, `GlossaryContext` (the `Artifact` member) and
  `GlossaryTerm`. Each JSON success answer carries `_links` (lint rules `success-schemas-carry-links`,
  `success-responses-have-link-examples`).
- `ArtifactKindToken.yaml` gains `glossary`. `Artifact.yaml` gains the `glossary` member and its kind prose.
  `parameters/ArtifactKind.yaml` lists the literal kinds.
- Write the examples: a list, a context item and a term.
- Add the tag `Glossaries` to `openapi.yaml`, and register the paths in path order. The literal paths come before the
  templated `/{kind}/{artifactId}`.
- Update the `_index.yaml` files.

### T020: Packs side

- `ArtifactReadService`:
  - `GLOSSARY` joins `SERVED` and `LISTED`, and `LITERAL_ITEM_KINDS`;
  - `ABSENT` maps it to `ARTIFACT_NOT_FOUND`;
  - it does **not** join `ArtifactRemovalService.REMOVABLE`;
  - the derived `ITEM_KINDS` must not contain it, or the templated item read answers 500;
  - update the pinned-set tests.
- `ForeignArtifactRead`: `read(pack, GLOSSARY, id, …)` and `held(pack, GLOSSARY)` work through the generic path. Add
  test rows for them.

### T021: The `:artifacts-edge` read

- `GlossaryReads` (port and service, as `SkillReads`), with `list(pack)`, `context(pack, contextId)` and
  `term(pack, contextId, termId)`.
- `GlossaryView`: the edge's record.
- `PacksGlossaries` (adapter, as `PacksSkills`): casts to `GlossaryContext`.
- `GlossaryBodies`: maps to the DTOs, field by field. Optional members are omitted when empty, and `termCount` is
  computed.
- `GlossariesController implements GlossariesApi`.
- Wire them in the three configuration classes.

### T022: Refusals

- The order is pack, then context, then term (research R3). The detail texts are exactly those in the Objective
  table. `artifact_not_found` stays the code (ruling `DM-01M7B1Q4BPQB95EVQBRWN4Q4F8`).
- Check how `RefusalCode.ARTIFACT_NOT_FOUND` carries a detail today, and use that mechanism; do not invent a new one.
- `CharterRefusalCode.yaml`'s raised map gains the three operations. That is an out-of-map edit; record it.

### T023: Scenarios and registries

- Write `contracts/charter/scenarios/glossary.feature` (`@contract @artifacts`). Its background pack is inline, in the
  canonical shape:
  - context `spec-kitty-core` (3 terms, one with `avoid`, one `deprecated`);
  - context `qa` (2 terms).
- Scenarios:
  - the list, with term counts;
  - `?kind=glossary`;
  - the context item;
  - one term, with every optional member;
  - a term with no optional members;
  - the three 404s, with their details;
  - a `400` for `Core`.
- The registries this WP owns get rows for the three operations:
  - `switch-gate.yaml` (`ready`);
  - `every-operation.feature`, `read-refusals.feature` and `unexpected-failure.feature`;
  - the `term` relation in the hypermedia relation table (`docs/development/hypermedia-hateoas.md`);
  - `link-relations-are-known` in `contracts/lint/ruleset.yaml`.
- `artifacts.feature`: `?kind=glossary` in a pack.
- `PackArtifactRoutingTest`: the literal glossary paths route to their own controller.
- Write the change fragment `2026-11-14-glossary-reads-in-a-pack.md` (`### Added`).

### T024: Every example term is readable (SC-001)

`ExampleVocabularyReadTest` loads the example pack `built-in` and reads every term of `spec-kitty-core` (139) through
the production `GlossaryReads.term` path. It asserts each answer's id, name and URN. The positive control is the term
count. It fails if any term is missing or unreadable.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :contracts:check :packs:test :artifacts-edge:test
./gradlew --max-workers=1 :deployable:test
./gradlew --max-workers=1 verifyAll
```

Run Gradle in the foreground, one run at a time.

## Definition of Done

- All three operations are `ready` in the switch gate.
- `glossary.feature` passes.
- The pinned sets are updated.
- The templated item read still answers `kind_not_found` for kinds it never served, and it answers neither 500 nor a
  glossary for `glossary`. Check `ITEM_KINDS`.
- SC-001's test passes.
- The complexity gate and the artifacts-edge mutation floor (85) are met.

## Risks and reviewer guidance

- **Reviewer**: check that the literal paths come before the templated ones in `openapi.yaml`.
- **Reviewer**: check the details' exact text.
- **Reviewer**: check that `ArtifactKindToken` equals `LISTED` (the parity test).

## Branch Strategy

- **Planning base and merge target**: `feat/106-glossary-contexts`.
- **Mission branch**: `kitty/mission-glossary-contexts-and-terms-01M7B1PP`.
- **Worktrees**: allocated per computed lane (`lanes.json`).
- **Implement with**:
  `spec-kitty agent action implement WP04 --agent claude --mission glossary-contexts-and-terms-01M7B1PP`.
- **Commits**: signed, with no model name and no `Co-Authored-By` trailer. Reference `(#106)`.
- **Integrating `main`**: rebase only. Force-push only your own rebased branch, with a lease.
