# Tasks: Slice F, the kind taxonomy and the mission-type schema

**Mission**: `slice-f-kind-taxonomy-01M7B19K`
**Branch**: `kitty/mission-slice-f-kind-taxonomy-01M7B19K` (mission), landing on `slice-f-537-kind-taxonomy`, then `main` by PR #567
**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md) · **Data model**: [data-model.md](data-model.md) · **Squad**: [research/squad-post-plan.md](research/squad-post-plan.md)

**One build at a time.** Gradle runs in the foreground with `--max-workers=1`, so work packages run in sequence even
where their files would allow parallel lanes.

**Each work package lands green.** Its commits pass the module tests it touches, plus `:deployable:test`. The plan's
order is: content first (WP01, WP02), then the model and its refusals (WP03 to WP05), then the mission-type rules
(WP06), then substeps and the kind's retirement (WP07, WP08), then the R18 read (WP09), then the records (WP10,
WP11).

**Shared registries.** Each registry file below is owned by one work package. Another work package that adds its own
rows to it records that as a small, out-of-map edit with a one-line rationale in its commit message ("rows for the
codes or operations this WP adds").

| Registry | Owner |
|---|---|
| `contracts/charter/schemas/CharterRefusalCode.yaml` | WP05 |
| `contracts/charter/scenarios/switch-gate.yaml` and `every-operation.feature` | WP09 |
| `contracts/lint/ruleset.yaml` | WP09 |
| `docs/development/hypermedia-hateoas.md` | WP10 |
| `contracts/packs/file-kinds.yaml` | WP08 |
| `PackFindingCode.java` and `PackPolicy.java` | WP05 |

**The change fragment.** WP10 writes the single `### Breaking` and `### Added` fragment. The other work packages
record their breaking items in their own WP review notes, and WP10 collects them.

**Commits.** Every commit is signed, has no `Co-Authored-By` trailer and names no model. The CLI's own commits come
out unsigned: re-sign any unpushed one with `git commit --amend --no-edit -S`. Never force-push a pushed branch.

## Subtask Index

| ID | Description | WP | Parallel |
|---|---|---|---|
| T001 | Example packs: drop the `impacts` edges, and make `suggests styleguide→anti_pattern` a `rejects` edge | WP01 | |
| T002 | Example packs: add the 8 missing action nodes and `mission_type -requires-> action` edges | WP01 | |
| T003 | Example pack: the `spec-kitty-pre-review` guard and its `work-package` lifecycle entry | WP01 | |
| T004 | Example pack: repoint unresolved delegation candidates, and add the `scope` edges from each action | WP01 | |
| T005 | Regenerate the manifest hashes, and keep the example-pack tests and the round trip green | WP01 | |
| T006 | Census script over scenarios and test fixtures (the endpoint table, plus the step-edge rule) | WP02 | |
| T007 | Rewrite the about 34 disallowed inline edges in `.feature` files to allowed forms, keeping each scenario's intent | WP02 | |
| T008 | Give every inline mission type in the scenarios its `requires` edges | WP02 | |
| T009 | Migrate the Java test fixtures (load fixtures, planted packs, test packs) in the same way | WP02 | |
| T010 | Re-run the census: 0 valid-path edges refused, 0 unlinked steps | WP02 | |
| T011 | Sealed kind markers, one per `NodeKind`, and kind groups (`Guidance`, `Activatable`, the per-rectangle groups) | WP03 | |
| T012 | `KindRef<K>`, with a private constructor and per-kind leaf factories | WP03 | |
| T013 | The endpoint table as data on `RelationType` (research R6) | WP03 | |
| T014 | Per-rectangle typed factories on `Relationship`, plus `EndpointRule.bind` and `bindTension` | WP03 | |
| T015 | The reflection agreement test (factories against the table), with a skewed positive control, and the kernel unit tests | WP03 | |
| T016 | Retire `IMPACTS`, `REPLACES` and `DELEGATES_TO`. Delete `Relation.java`, and repoint `Relation.yaml` (adding `implemented_by`) | WP04 | |
| T017 | Remove the `impacts` strength: `DrgEdge`, the graph schemas, the `PackPolicy` strength rule and `RelationshipMerge` | WP04 | |
| T018 | Delete the impacts fixtures and update their test classes. Add one retired-relation refusal fixture and scenario | WP04 | |
| T019 | Parity tests (`RelationSetParityTest`, `WireTokenParityTest`, `VocabularyEqualityTest`) follow the enum | WP04 | |
| T020 | Route the 7 construction sites through `EndpointRule` or the factories. `Relationship`'s constructor becomes package-private, guarded by an ArchUnit rule | WP05 | |
| T021 | Read-side refusal: `PackPolicy.endpoints`, the load path's `typedEdge`, and the `PackFindingCode` with its `violationKind` | WP05 | |
| T022 | Write-side refusal at `/type` in `RelationshipRegistrationService`, with the fault order fixed (`StepEnhancementRules`, then `OverrideSourceRule`, then the table) | WP05 | |
| T023 | Register the code in `CharterRefusalCode.yaml`, and extend `ExampleCoverageTest` to cover violation codes | WP05 | |
| T024 | Invalid packs per relation family, #350, and `overrides` across kinds. Scenarios. Rewrite `override-source-kind.feature` and the prompt-fragment fixtures | WP05 | |
| T025 | Bind tension through `bindTension` | WP05 | |
| T026 | `mission_step_unlinked` per pack, over plain-data `PackContents.MissionTypeFacts` | WP06 | |
| T027 | Compose-time check on the winning type: no edge to a missing step | WP06 | |
| T028 | Invalid packs for an authored `action_sequence` and `template_set`, plus a scenario | WP06 | |
| T029 | Drop `template_set` from `charter.schema.json` and its fixtures, and clean up the `ENHANCES` javadoc | WP06 | |
| T030 | Schema: `Step.substeps` in `mission-type.schema.json` | WP07 | |
| T031 | Domain: `Substep`, `SubstepInput`, `SubstepDelegation`, and `MissionStep.substeps`. Codec and round trip | WP07 | |
| T032 | Load rules: `substep_delegation_unresolved` and `substep_delegation_unscoped` (in pack), and `DUPLICATE_STEP_ID` retargeted to substeps | WP07 | |
| T033 | Compose and registration check for qualified candidates | WP07 | |
| T034 | Contract: `MissionStep.substeps`, the renamed delegation components, and the reads in both trees | WP07 | |
| T035 | Example pack: fold the 5 step contracts into substeps, and satisfy `ExamplePackCoverageTest` | WP07 | |
| T036 | Delete the packs-side step-contract types, bindings and codecs | WP08 | |
| T037 | Kernel enums, `RefusalCode`, `RefusalHandler`, `file-kinds.yaml` (marked retired) and the pack schemas | WP08 | |
| T038 | missions-edge and activations step-contract code, including slice C's winner and placement code | WP08 | |
| T039 | Contract removals (the full list) and the switch-gate key | WP08 | |
| T040 | Fixtures and scenarios, plus `removed-routes.feature`, which pins each retired route's answer | WP08 | |
| T041 | Source scans over the main sources and `contracts/` | WP08 | |
| T042 | Contract: `getKindTaxonomy`, its schemas, the example, the headers and the fingerprint lint exceptions | WP09 | |
| T043 | Service: the taxonomy builder and its controller, with the body-hash `ETag` and `304` | WP09 | |
| T044 | Links: `kinds` on the root and on `Offering`, plus templated `artifacts` links | WP09 | |
| T045 | Switch gate, `every-operation` rows, and the `offering.feature` 200 and 304 scenarios | WP09 | |
| T046 | A parity case with a positive control | WP09 | |
| T047 | The new ADR, and the amendment notes on the touched ADRs | WP10 | |
| T048 | `service-contract.md`, `resolution-model.md`, `hypermedia-hateoas.md`, the glossary, diagrams and pages | WP10 | |
| T049 | The single `### Breaking` and `### Added` change fragment | WP10 | |
| T050 | The open-packs drafts (5), each with an `open-packs#TBD-<slug>` placeholder | WP11 | |
| T051 | Census evidence against open-packs with the drafts applied, and the closeout issue list | WP11 | |

## WP01: Example packs, content first

- **Prompt**: [tasks/WP01-example-packs-content-first.md](tasks/WP01-example-packs-content-first.md)
- **Goal**: Charter's example packs already satisfy every new rule while today's rules still hold.
- **Priority**: P1, the foundation.
- **Independent test**: the example-pack tests and `:packs:wholePackRoundTrip` stay green.
- **Subtasks**:

T001 Drop the impacts edges, and change `suggests styleguide→anti_pattern` to a `rejects` edge (WP01)
T002 Add 8 action nodes and their `requires` edges (WP01)
T003 Add the pre-review guard and its lifecycle entry (WP01)
T004 Repoint delegation candidates, and add `scope` edges (WP01)
T005 Regenerate the manifest hashes; tests green (WP01)

- **Dependencies**: none.
- **Risk**: manifest hash drift. Regenerate the hashes with the repository's own tooling, never by hand.

## WP02: Scenarios and fixtures, content first

- **Prompt**: [tasks/WP02-scenarios-and-fixtures-content-first.md](tasks/WP02-scenarios-and-fixtures-content-first.md)
- **Goal**: Every valid-path inline pack and fixture already satisfies the endpoint table and the step-edge rule.
- **Priority**: P1.
- **Subtasks**:

T006 Census script (WP02)
T007 Rewrite the disallowed inline edges (WP02)
T008 Add the `requires` edges to the inline mission types (WP02)
T009 Migrate the Java fixtures (WP02)
T010 Re-run the census and record the evidence (WP02)

- **Dependencies**: none.
- **Risk**: a rewrite that changes what a scenario tests. Keep the scenario's subject, and change only the kinds.

## WP03: Kernel kind typing and the endpoint table

- **Prompt**: [tasks/WP03-kernel-kind-typing.md](tasks/WP03-kernel-kind-typing.md)
- **Goal**: The table is data on `RelationType`, and the typed factories cannot build a disallowed edge. Nothing
  uses the factories yet, so the change is additive.
- **Priority**: P1.
- **Subtasks**:

T011 Kind markers and groups (WP03)
T012 `KindRef<K>` (WP03)
T013 The endpoint table on `RelationType` (WP03)
T014 The typed factories and `EndpointRule` (WP03)
T015 The agreement test and the kernel tests (WP03)

- **Dependencies**: none.

## WP04: Retire impacts, replaces and delegates_to

- **Prompt**: [tasks/WP04-retire-relations.md](tasks/WP04-retire-relations.md)
- **Subtasks**:

T016 Retire the enum values and delete `Relation.java` (WP04)
T017 Remove the `impacts` strength plumbing (WP04)
T018 Fixtures and the retired-relation refusal (WP04)
T019 Parity tests (WP04)

- **Dependencies**: WP01, WP02, WP03.

## WP05: Endpoint refusal at read and write

- **Prompt**: [tasks/WP05-endpoint-refusal.md](tasks/WP05-endpoint-refusal.md)
- **Subtasks**:

T020 Construction through `EndpointRule`, with the ArchUnit guard (WP05)
T021 Read-side refusal (WP05)
T022 Write-side refusal and the fault order (WP05)
T023 Code registration and example coverage (WP05)
T024 Invalid packs, scenarios, #350 and C's scenario (WP05)
T025 Tension binding (WP05)

- **Dependencies**: WP04.

## WP06: Mission-type rules and template_set

- **Prompt**: [tasks/WP06-mission-type-rules.md](tasks/WP06-mission-type-rules.md)
- **Subtasks**:

T026 `mission_step_unlinked` per pack (WP06)
T027 Compose-time check on the winning type (WP06)
T028 `action_sequence` and `template_set` refusals (WP06)
T029 Drop `template_set` from the charter schema, and fix the javadoc (WP06)

- **Dependencies**: WP05.

## WP07: Substeps on the mission step

- **Prompt**: [tasks/WP07-substeps-on-the-step.md](tasks/WP07-substeps-on-the-step.md)
- **Subtasks**:

T030 Schema (WP07)
T031 Domain and codec (WP07)
T032 In-pack load rules (WP07)
T033 Compose check for qualified candidates (WP07)
T034 Contract and reads (WP07)
T035 Example-pack substeps (WP07)

- **Dependencies**: WP06.

## WP08: Retire mission_step_contract

- **Prompt**: [tasks/WP08-retire-mission-step-contract.md](tasks/WP08-retire-mission-step-contract.md)
- **Subtasks**:

T036 Packs types (WP08)
T037 Kernel, file kinds and pack schemas (WP08)
T038 missions-edge and activations (WP08)
T039 Contract removals and the switch-gate key (WP08)
T040 Fixtures, scenarios and the removed routes (WP08)
T041 Source scans (WP08)

- **Dependencies**: WP07.

## WP09: R18, the kind taxonomy read

- **Prompt**: [tasks/WP09-kind-taxonomy-read.md](tasks/WP09-kind-taxonomy-read.md)
- **Subtasks**:

T042 Contract (WP09)
T043 Service (WP09)
T044 Links (WP09)
T045 Gates and scenarios (WP09)
T046 Parity (WP09)

- **Dependencies**: WP08.

## WP10: ADR, docs and changelog

- **Prompt**: [tasks/WP10-adr-docs-changelog.md](tasks/WP10-adr-docs-changelog.md)
- **Subtasks**:

T047 The new ADR and the amendment notes (WP10)
T048 Docs (WP10)
T049 The change fragment (WP10)

- **Dependencies**: WP09.

## WP11: Open-packs drafts and closeout evidence

- **Prompt**: [tasks/WP11-open-packs-drafts.md](tasks/WP11-open-packs-drafts.md)
- **Subtasks**:

T050 The open-packs drafts (WP11)
T051 Census evidence and the closeout list (WP11)

- **Dependencies**: WP10.
