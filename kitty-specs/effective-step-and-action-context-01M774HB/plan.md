# Implementation Plan: Slice C, the effective step and action context

**Branch**: `kitty/mission-effective-step-and-action-context-01M774HB`, merging into `slice-c/effective-step-context`
(PR to `main`) | **Date**: 2026-11-12 | **Spec**: [spec.md](spec.md)
**Input**: `kitty-specs/effective-step-and-action-context-01M774HB/spec.md`; [research.md](research.md) R1 to R10.

## Summary

Three deliveries, one contract change set:

1. **The effective action context applies the ownership rule** (#450, operator ruling R1). The effective walk runs
   three passes (action, mission type, project-wide), and each item says through which with `appliesThrough`. ADR-0018
   D5 and rule E16 are amended, and the 16 directives of the evidence are explained.
2. **ADR-0066 phase 2** (#438). Three compose checks for step enhancements (`prompt_slot_missing`, target held,
   inherited lock) on reads and at registration, and the effective-prompt read
   `GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/prompt` with
   `409 mission_type_not_in_force` (OD-5) and a `prompt` link on the context.
3. **R3 (#97) is decided and closed** (R6): docs only.

## Technical Context

**Language/Version**: Java 25 (Temurin), Gradle multi-project build (`./gradlew`, run with `--max-workers=1`, never
two runs at once)
**Primary Dependencies**: Spring Boot (web transport), the OpenAPI generator over `contracts/charter` (`charter-api`,
`skipDefaultInterface`), Jackson; no dependency is added (DIRECTIVE_051: no supply-chain decision in this mission)
**Storage**: Charter Packs on the file system, read through the `packs` storage ring; activations stored as documents
**Testing**: JUnit Jupiter, AssertJ, ArchUnit (`HexagonalArchitectureTest`), Cucumber-JVM contract scenarios under
`contracts/charter/scenarios/` (BDD first, C-004), the contract lint and its tests, the whole-pack round trip over the
example packs (ADR-0070), PIT on the slices it already covers
**Target Platform**: Linux server and native image (the `service-v*` line), Java 25
**Project Type**: single hexagonal service; slices `kernel`, `packs`, `activations`, `missions`, `missions-edge`,
`web-transport`, `deployable`
**Performance Goals**: the prompt read and the context stay linear in the enhancements and in the artifacts in force
(NFR-002, NFR-003: operation-count ratio at most 2.2 between 1,000 and 2,000)
**Constraints**: determinism (NFR-001, NFR-004: byte-identical across 100 reads and every permutation of three packs);
canonical shape only in tests (C-005); stay out of slice B's files (C-006)
**Scale/Scope**: about 12 contract files, 2 ADR amendments, 1 divergence entry, rules-page and glossary edits; code
in `packs` (application, domain, a new storage adapter), `activations` (application, domain, infra), `web-transport`
(refusal mapping)

## Charter Check

| Gate (charter.md) | How this plan meets it |
|---|---|
| Divergence from the charter's own model | The scoping rule is the operator's ruling on the charter's model (ADR-0018 D5); core's behaviour is cited as evidence and the one deliberate difference (mission-type scope honoured) is registered. |
| OpenAPI contract and its versioning | Every change has a dated fragment: `### Added` (read, schemas, `prompt` link, `appliesThrough`), `### Changed` (`prompt_slot_missing` raised, `override_not_permitted` broadened), `### Breaking` (new enum value `mission_type_not_in_force`; the context lists more; compose and registration refusals). 0.x line, ADR-0022. |
| Round trip by meaning | No holding or write-back changes (C-002). `:packs:wholePackRoundTrip` stays required and green. |
| Canonical model, no legacy readers | No old shape is read; example packs gain only canonical content. |
| Tests carry only canonical shape | Fixtures added in the canonical shape; invalid shapes only in refusal tests (twins per SC-002). |
| Adversarial squad at plan point and before merge | Plan-point squad on this plan; pre-merge squad after the rebase onto #501 (C-006). |
| Gates before merge | `verifyAll` runs in CI on Java 25; locally per-module tasks only, foreground, one at a time. Docs checks: `check_docs.py`, markdownlint, cspell. |
| Issues stay small | Squad minors folded or listed under "Not done"; #438, #450, #97 closed with evidence; #467 ticked. |

No violation to justify.

## Project Structure

### Documentation (this mission)

```
kitty-specs/effective-step-and-action-context-01M774HB/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/            # the contract delta of this mission (planning copy)
└── tasks/                # /spec-kitty.tasks
```

### Source Code (repository root)

```
contracts/charter/        paths/, schemas/, examples/, responses/, scenarios/, changes/, openapi.yaml
contracts/lint/           ruleset.yaml (the link-relation enum gains prompt)
service/packs/            application/ (ActionContextWalk, ClosureView, EffectivePromptQuery, ImpactRelations,
                          RegistrationImpact), domain/ (PromptSlotFacts port, EnhancementCompose rules),
                          infra/storage/ (a new StoredPromptSlotFacts adapter), testFixtures/ example packs
service/activations/      application/ (EffectiveReadService, PacksSession), domain/ (EffectiveContext, EffectiveTag,
                          EffectivePrompt), infra/packs/ (ClosureViewSession, ActivationImpactAdapter),
                          infra/api/ (ActivationController, EffectiveReadMapper, ActivationMapper)
service/kernel/           RefusalCode (MISSION_TYPE_NOT_IN_FORCE)
service/web-transport/    RefusalHandler (statuses and per-operation 422s)
docs/                     adr/0018, adr/0066, architecture/composition-and-activation-rules.md,
                          conformance/divergence-register.md, api/service-contract.md, glossary, changelog,
                          development/hypermedia-hateoas.md
```

**Structure Decision**: The existing hexagonal layout. The walk and the prompt query stay in `packs.application`,
served through `activations` as the context read is (`ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER`). Markdown rendering
stays in `packs.infra.storage` behind a domain port (R9). No slice-B file is edited (C-006): not
`DomainRepositoryService`, `PackCommitStore`, `PackLocator`, `CommitStorePackWriteStore`, `FileSystemPackSources`,
`Loader`/`LoadedPack`, `CharterWiring` or `file-kinds.yaml`. The shared `CharterRefusalCode.yaml` gains one enum value
and edits to existing entries; the overlap is announced to the orchestrator.

## Design

### The ownership rule in the walk (R1 to R4)

```mermaid
flowchart LR
  A["pass 1: action:mt/a<br/>scope, requires, suggests,<br/>requires-of-suggested, vocabulary"] --> B["pass 2: roots = in-force D/T/P<br/>scoped by mission_type:mt"]
  B --> C["pass 3: roots = in-force D/T/P<br/>scoped by no action and no mission type"]
  A -.->|appliesThrough: action| L[(one listing,<br/>first pass wins)]
  B -.->|appliesThrough: mission_type| L
  C -.->|appliesThrough: project| L
```

- `ActionContextWalk` gains an effective form (the offering read keeps the five-step walk as it is, ADR-0018 D1). The
  same `Engine` runs the passes over one listing, so deduplication, `via` and `hops` keep their meaning inside a pass.
  A pass-2 root is listed with `via = mission_type:mt`; a pass-3 root with `via` = the action node and `hops` 1, an
  internal value the effective context does not serialize.
- Ownership is read from held `scope` edges of the merged closure whose source is a held step or a mission type the
  closure holds, whatever is in force (R2). It is computed once per merged index (memoized like `heldSteps`), so the
  rule is linear (NFR-003).
- `EffectiveContext.of` carries `appliesThrough` per item; the suggestion takes its suggester's.

### Compose checks (FR-001, FR-002; R7, R9)

| Check | Pure rule | Reads (500) | Registration and impact-checked writes (422) | Activation writes (422) |
|---|---|---|---|---|
| target held | an enhancement's `enhances` target `action:M/s` is held by a pack of its `depends_on` closure (`StepHolding.heldByPack`) | `pack_reference_pack_missing` / `pack_reference_artifact_missing`, from `OfferingReadService.composed` | same, through `RegistrationImpact.judge` | composes as reads |
| inherited lock | `mission_type:M` is `LOCKED` in the closure without the enhancing pack (`PerPackPolicy.levelIn`) | `pack_unreadable` | `override_not_permitted`, a new rule in `ImpactRelations` beside `notPermitted` | composes as reads |
| slot declared | every slot an **in-force** enhancement names is declared by the prompt in force of its target (R8) | `prompt_slot_missing`, after `InForceResolution` in `ClosureView.inForce` | per stored activation the change joins, through the impact port (R7) | before the document is stored |

- The pack-qualified target of an `enhances` edge whose kind is `action` is today never `artifact_missing`
  (`NodeKind.ACTION.artifactKind()` is empty). The target-held rule closes that for step enhancements only.

### The effective-prompt read (FR-003 to FR-007; R5, R8, R10)

- `packs.application.EffectivePromptQuery`: for a merged closure, in-force states, `M` and `s`: the prompt in force
  (R8), its slots in declaration order, and per slot the fragments of the in-force enhancements whose sole target is
  `action:M/s`, ordered by level, pack descriptor name, enhancement id (code points). Legacy slot text yields a
  `prompt_slot_not_empty` diagnostic (OD-7, OD-8).
- `activations`: `EffectiveReadService.prompt` judges in the order of R10, the mission-type check reading the effective
  set; `EffectiveTag.Read.PROMPT`; `ActivationController.getEffectiveStepPrompt`; `EffectiveReadMapper.toPrompt`.
- The context's `_links.prompt` is set when the mission type is in force (the read would answer `409` otherwise).

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — Contract delta and scenarios first

- **Purpose**: Write the contract changes and the BDD scenarios before code, so the generated interface and the
  scenarios drive the build (C-004).
- **Relevant requirements**: FR-001 to FR-009, C-001, C-004.
- **Affected surfaces**: `contracts/charter/paths/activations_activationId_missionTypes_missionType_actions_action_prompt.yaml`
  (new), `schemas/EffectiveStepPrompt.yaml`, `EffectivePromptSlot.yaml`, `EffectivePromptFragment.yaml`, `SlotId.yaml`,
  `EffectiveContextArtifact.yaml` (new), `EffectiveActionContext.yaml`, `CharterRefusalCode.yaml`, `openapi.yaml`,
  `schemas/_index.yaml`, examples, `contracts/lint/ruleset.yaml` (`prompt` relation), `changes/2026-11-12-*.md`,
  scenarios `effective-step-prompt.feature`, `step-enhancement-compose.feature`, `effective-context-scoping.feature`,
  `errors.feature` raised-code rows.
- **Sequencing/depends-on**: none. The generated interface lands with its controller (IC-05), so the path file can
  merge only with IC-05.
- **Risks**: `CharterRefusalCode.yaml` is shared with slice B: keep the edit to one enum value and the existing entries.

### IC-02 — The ownership rule in the effective walk

- **Purpose**: List every in-force directive, tactic and paradigm the ruling says applies, with `appliesThrough`.
- **Relevant requirements**: FR-008, FR-009, NFR-003, NFR-004, SC-004.
- **Affected surfaces**: `packs/application/ActionContextWalk`, `EdgeIndex` (scope owners), `ClosureView.actionContext`;
  `activations/application/PacksSession`, `activations/infra/packs/ClosureViewSession`,
  `activations/domain/{ActionContext, EffectiveContext}`, `activations/infra/api/EffectiveReadMapper`.
- **Sequencing/depends-on**: IC-01 (the schema with `appliesThrough`).
- **Risks**: existing walk tests pin the action-only answer; they must stay green for the offering read and for
  closures with no root to add. A fixture shaped like the evidence proves the 15/1 split.

### IC-03 — Slot facts port and its storage adapter

- **Purpose**: Give the compose check and the read one source of declared slots, fragment slots and fragment text.
- **Relevant requirements**: FR-001, FR-003, FR-005; R8, R9.
- **Affected surfaces**: `packs/domain/PromptSlotFacts` (port), `packs/infra/storage/StoredPromptSlotFacts` (new),
  wiring in `packs` configuration (not `CharterWiring`).
- **Sequencing/depends-on**: none.
- **Risks**: the canonical render must match the load rule's (A5); a test parses both and compares.

### IC-04 — Compose checks

- **Purpose**: Refuse a compose that would misplace or drop a fragment.
- **Relevant requirements**: FR-001, FR-002, FR-006, SC-002, SC-003.
- **Affected surfaces**: `packs/application/{OfferingReadService, ClosureView, ImpactRelations, RegistrationImpact}`,
  `packs/domain/EnhancementCompose` (pure rules), `activations/infra/packs/ActivationImpactAdapter` (report, not
  swallow, `prompt_slot_missing`), `activations/application/ActivationService` (activation writes),
  `web-transport/RefusalHandler` (422 per operation), `ReservedRefusalCodesTest`.
- **Sequencing/depends-on**: IC-01, IC-03.
- **Risks**: registration path overlaps slice B's `StagedPacks`/`WholePackStore` by call only; the hook is
  `RegistrationImpact.judge`. Each refusal gets a failing fixture and a passing twin.

### IC-05 — The effective-prompt read

- **Purpose**: Serve the prompt in force and its ordered fragments, with `409 mission_type_not_in_force` and the
  context's `prompt` link.
- **Relevant requirements**: FR-003, FR-004, FR-005, FR-007, NFR-001, NFR-002, SC-001.
- **Affected surfaces**: `packs/application/{EffectivePromptQuery, ClosureView}`, `activations/application/{EffectiveReadService,
  PacksSession}`, `activations/domain/{EffectivePrompt, EffectiveTag}`, `activations/infra/{packs/ClosureViewSession,
  api/ActivationController, api/EffectiveReadMapper, api/ActivationMapper}`, `kernel/RefusalCode`,
  `web-transport/RefusalHandler`.
- **Sequencing/depends-on**: IC-01, IC-03; IC-04 for the slot-missing refusal on this read.
- **Risks**: NFR-001/002 gates; the A7 mismatch with `getEffectiveMissionStep` is recorded, not fixed (R8).

### IC-06 — Records and docs

- **Purpose**: Make the docs and records say what the service does.
- **Relevant requirements**: FR-010, FR-011, FR-012, SC-005.
- **Affected surfaces**: ADR-0018 (amendment of D5), ADR-0066 (annotations on d7, d8, d9), rules page E16 and new
  compose rules, divergence register (mission-type scope; context scoping), `docs/api/service-contract.md` (R3 row,
  operation lists), `docs/development/{hypermedia-hateoas, http-status-codes, rest-resource-conventions}.md`,
  conceptual model integrity note, glossary terms, changelog page, the 16-directive table.
- **Sequencing/depends-on**: IC-02, IC-04, IC-05 (records follow behaviour).
- **Risks**: `RelationTableParityTest` and the docs checks pin the text.
