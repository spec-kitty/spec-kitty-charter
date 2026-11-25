# Implementation Plan: ADR-0082 slices 2 to 4: one graph walk, rules from the factory

**Branch**: `kitty/mission-adr-0082-graph-walk-slices-01M8A867` (planning and merge target `feat/adr-0082-graph-walk-slices`; each slice lands on `main` by its own pull request) | **Date**: 2026-11-25 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/adr-0082-graph-walk-slices-01M8A867/spec.md`; research in [research.md](research.md) and [research/pre-spec-squad.md](research/pre-spec-squad.md).

## Summary

The packs domain takes over every walk of the pack graph and every pack rule (ADR-0082). Four slices:

- **Slice 2 (#637).** `PackGraph` holds the descents, the cycle and group searches, and the `enhances` grounding. The composition resolver's five walks move into it too. An ArchUnit guard keeps walks out of the application layer.
- **Slice 3 (#638).** Builds the full type design: the kernel visitor contract becomes generic (`GraphWalker<N, E>`), and `PackGraph` drives one visitor over sealed `PackGraphNode`s within a `Scope`. A `ValidationWalker` runs the rules a predicate selects, with one bucket per rule, joined in rule order, behind a phase gate on `VIOLATION`. The own-closure rules and `REFERENCES` run on it.
- **Slice 4 (#639).** Moves the relation rules, the specialization cycle and the edge-index refusals into the factory's relations phase, and unifies the read-time and registration codes of the edge-index refusals. That last change is a wire change.
- **WP05 (#632 item 3).** `pack-doctor` picks up the specialization cycle and the relations.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain, `./gradlew verifyAll`)
**Primary Dependencies**: Spring Boot (application wiring only), JSpecify/NullAway, ArchUnit 1.5.1, JUnit 5, AssertJ; no new dependency
**Storage**: N/A (pack files are read through existing ports; nothing new is stored)
**Testing**: red-first unit tests per moved walk and per rule; ArchUnit guards with known-bad fixtures; the contract scenarios (`contracts/charter/scenarios`); `FullPassParityTest` rows for pack-doctor; `PackRegistrationScaleTest`, which is report-only
**Target Platform**: the charter service (Linux JVM), the `pack-doctor` CLI
**Project Type**: single Gradle multi-module service (`service/kernel`, `service/packs`, `service/pack-validation`, `service/pack-doctor`, `service/web-transport`, `contracts`)
**Performance Goals**: no more than 10% slower per registration in `PackRegistrationScaleTest` (NFR-003); the graph is built once per judge or census, and facts are read lazily
**Constraints**: refusal codes, details, origins and order unchanged except FR-011 (NFR-001); iterative walks only (NFR-002); replace outright (C-001); one PR per slice (C-002); no PIT (C-004); stay out of the other runners' files (C-005)
**Scale/Scope**: about 15 production classes in `packs`, the kernel `GraphWalker`, `RefusalHandler`, the contract refusal-code schema, 4 scenario files, about 20 test classes

## Charter Check

Gate before Phase 0, and again after design:

| Charter rule | How the plan meets it | Status |
|---|---|---|
| The charter's model is canonical; replace outright | Each slice deletes what it replaces: `ClosureWalk`/`OwnClosureWalk` (slice 3), `relationFaults` and the application gates (slice 4), and the resolver's private walks (slice 2) | Pass |
| Tests carry the canonical shape only | New fixtures use canonical pack shapes; the bad fixtures exist only to fail an ArchUnit rule | Pass |
| Contract changes follow ADR-0022 | FR-011 is recorded as `### Breaking` in a `contracts/charter/changes` fragment; whether the version moves follows ADR-0022 D2 (0.x minor) | Pass, with Q-A and Q-C open |
| Adversarial squad at plan and before merge | Pre-spec squad done; a review squad runs before each PR is marked ready, posted as "Independent adversarial review (agent)" | Pass |
| No PIT in mission work | No `pitest` or `checkMutationFloors`; floors move from the weekly report | Pass |
| Nits folded, issues small | Every squad finding is folded or recorded; one defect issue filed (research R-5) | Pass |
| Gates before merge | `verifyAll` on Java 25, `check_docs.py all`, markdownlint, cspell per PR | Pass |

## Open decisions (operator)

- **Q-A, status model for the unified codes.** Recommended: the ADR-0056 model. A read answers 500, because the configured packs are at fault, and the 13 impact-checked operations answer 422 through `x-refusal` operation overrides. [NEEDS CLARIFICATION: status model for self_override and override_not_permitted once reads answer them] <!-- decision_id: 01M8A9W2C4ZEYT5Q946PBW8JTB -->
- **Q-B, C21's layer-merge refusal.** Recommended: out of scope, recorded on epic #636. [NEEDS CLARIFICATION: whether C21 joins the D2 unification] <!-- decision_id: 01M8A9W4XH5QFBWQBXNFJ5TFC8 -->
- **Q-C, the stray step link's code.** Recommended: `pack_reference_artifact_missing`, with the violation `mission_step_unlinked`. [NEEDS CLARIFICATION: the refusal code of a stray step link] <!-- decision_id: 01M8A9W7G09TCRETWG48M9VRCG -->

Only slice 4 (IC-05) depends on Q-A to Q-C. Slices 2 and 3 proceed.

## Project Structure

### Documentation (this mission)

```text
kitty-specs/adr-0082-graph-walk-slices-01M8A867/
├── spec.md
├── plan.md              # this file
├── research.md          # D1 and D2 research, decisions
├── research/pre-spec-squad.md
├── data-model.md        # the graph, scope, walker and rule types
├── quickstart.md        # how to add a check
├── checklists/requirements.md
└── tasks.md             # /spec-kitty.tasks
```

### Source Code (repository root)

```text
service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
  GraphWalker.java                 # generic GraphWalker<N, E> (slice 3)
service/packs/src/main/java/ai/speckitty/charter/packs/
  domain/PackGraph.java            # walks (slice 2), graph of nodes and driver (slice 3)
  domain/PackGraphNode.java, PackNode.java, ArtifactNode.java, EdgeNode.java, EdgeKind.java, Scope.java
  domain/ValidationWalker.java, GraphCheck.java
  domain/PackCompositionResolver.java   # its five walks move out (slice 2)
  domain/rules/PackRulesets.java   # ownClosure(), references() on the walker, relations()
  domain/rules/FallbackProfileRule.java, RolePinRule.java, ProfileEnhancementRule.java, ProfileReader.java
  domain/rules/RelationRules.java  # slice 4: the ImpactRelations rules and the specialization cycle
  application/ClosureImpactCheck.java, ImpactRelations.java (deleted in slice 4), SpecializationCycleRule.java (deleted),
  application/EdgeIndex.java, ClosureView.java, GraphWalk.java, Reachability.java
service/web-transport/.../RefusalHandler.java          # Q-A
service/pack-validation/... and service/pack-doctor/... # WP05: relations over each root's composed closure
contracts/charter/schemas/CharterRefusalCode.yaml, responses/OfferingUnreadable.yaml, scenarios/*.feature, changes/
docs/architecture/architectural-patterns-applied.md   # determinism guard, built notes (Proposed)
```

**Structure Decision**: no new module. The types go where ADR-0082 puts them: the visitor contract in the kernel, and the driver, nodes, scope and walker in `packs.domain`. `ValidationWalker` stays in `packs.domain` until slice 7 settles the ledger on `Rationale`, because its rules return `ClosureFault`s, which carry the packs and violations the edge maps.

## Complexity Tracking

| Deviation from the architecture page | Why needed | Simpler alternative rejected because |
|---|---|---|
| Rules return `List<ClosureFault>`, not kernel `Rule<T>` reasons | `ClosureFault` carries packs, violations and `requiredBy` that `PackRefusals` maps; `Reason` alone loses them | Converging `ClosureFault` with `Reason` is slice 7's job (ruling R-x) |
| The determinism guard is amended to phase, rule, then input (merge) order, not pack id in code-point order | Today's refusal codes depend on rule-first, input-order findings (operator ruling D1) | Code-point order changes the first code on multi-fault closures |
| `GraphWalk` (the artifact cascade) stays in the application layer | `EdgeKind` cannot name the DRG relation types, and the walk is recursive | Folding it needs an `EdgeKind` for arbitrary relations; it is on epic #636's checklist |

## Implementation Concern Map

### IC-01 --- The application layer's walks and their guard

- **Purpose**: every walk the application layer wrote by hand lives in `PackGraph`, and an ArchUnit rule keeps it so.
- **Relevant requirements**: FR-001, FR-003, FR-013
- **Affected surfaces**: `PackGraph`, `ClosureRoots`, `RemovalDependents`, `PackMutabilityPolicy` (not its test-only constructors), `OfferingReadService`, `InboundReferences`, `ClosureRoleFacts`, `ImpactRelations`, `SpecializationCycleRule`, `PackGraphWalkBoundaryTest`
- **Sequencing/depends-on**: none (in PR #665)
- **Risks**: the order of each moved walk; review finding 1 (the read order in `RemovalDependents`) is folded

### IC-02 --- The resolver's walks

- **Purpose**: the dependency order (Kahn's pass), the dependency-cycle search, the shortest-cycle search, the `depends_on`-only closure and the `parent_pack` line move into `PackGraph`, each pinned by a test.
- **Relevant requirements**: FR-002, NFR-001, NFR-002
- **Affected surfaces**: `PackCompositionResolver`, `PackGraph`, `PackGraphTest`, `PackCompositionResolverTest`
- **Sequencing/depends-on**: IC-01 (same PR, #665)
- **Risks**: `dependsOnClosure` is a LIFO descent and Kahn's pass takes packs in a priority order; both orders reach refusal details, so each is moved as written and pinned

### IC-03 --- Graph nodes, scope and the walker

- **Purpose**: the kernel visitor contract becomes generic. `PackGraph` is built once per judge from packs in merge order and walks a `Scope` in one of the domain's walk modes. `ValidationWalker` keeps one bucket per rule and gates phases on `VIOLATION`.
- **Relevant requirements**: FR-004, FR-005, NFR-002, NFR-003
- **Affected surfaces**: kernel `GraphWalker`, `Descent` (Javadoc), `GraphWalk`, `Reachability`, `GraphWalkTest` (generics only); new domain types; architecture page (Proposed amendment)
- **Sequencing/depends-on**: IC-02
- **Risks**: a god walker (the walk keeps three parameters); facts read eagerly (they are read lazily and memoized)

### IC-04 --- The own-closure rules and REFERENCES on the walker

- **Purpose**: the three own-closure rules move into `packs.domain.rules`, and `PackRulesets.ownClosure(..)` hands out one phase over the root `PackNode`: fallback, own pins, pin conflicts, enhancements, in that order. `references()` becomes three one-check phases. `ClosureWalk` and `OwnClosureWalk` are deleted.
- **Relevant requirements**: FR-006, FR-007
- **Affected surfaces**: `PackRulesets`, the three rules, `ProfileReader`, `ClosureImpactCheck` (the field and constructors go), tests that construct the check, `OwnClosureRulesTest`, `FullPassParityTest`
- **Sequencing/depends-on**: IC-03
- **Risks**: write-path order is closure-major, and preset items stay ungated beside the breaches (squad S8); the declaring pack comes from the root node, not from `ClosureFacts` (spec Key Entities amended)

### IC-05 --- Relations and edge-index rules, one code per fault

- **Purpose**: the relation rules, the specialization cycle and the edge-index refusals become relations-phase rules. The relations gate stays ahead of the edge-index rules. A read and a registration answer one code per fault (D2).
- **Relevant requirements**: FR-008, FR-009, FR-010, FR-011
- **Affected surfaces**: `ImpactRelations` and `SpecializationCycleRule` (deleted), `EdgeIndex`, `ClosureView`, `ClosureImpactCheck`, `RefusalHandler`, `CharterRefusalCode.yaml`, `OfferingUnreadable.yaml`, `step-enhancement-compose.feature`, `mission-types.feature`, registration scenarios, the contract change fragment, `http-status-codes.md`, `composition-and-activation-rules.md`, `ruleset-scenario-trace.md`
- **Sequencing/depends-on**: IC-04; Q-A to Q-C
- **Risks**: registration starts refusing a re-pointed self-override and a stray step link, which it accepts today. That flips `ClosureImpactCheckTest`'s case for a layer-above override, and it closes the read leak in research R-5.

### IC-06 --- pack-doctor picks up the cycle and the relations

- **Purpose**: `pack-doctor validate --full` runs the factory's relations phase over each root's composed closure, reporting a fault that several roots find once, with registration's codes.
- **Relevant requirements**: FR-012
- **Affected surfaces**: `pack-validation` (`ReferencesPhase` or a sibling phase, `PackSetCheck`), `pack-doctor`, `FullPassParityTest`, `docs/guides/check-packs.md`, ADR-0083 decision 9 (a Proposed amendment note)
- **Sequencing/depends-on**: IC-05
- **Risks**: pack-doctor has no profile reader, so the own-closure rules stay with slice 7
