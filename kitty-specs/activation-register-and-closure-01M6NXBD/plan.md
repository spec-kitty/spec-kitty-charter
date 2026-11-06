# Implementation Plan: Activation register and activated-pack closure

**Branch**: `issue-360-activated-closure` (on `main`, which now carries the merged PR #368 and PR #376) | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/activation-register-and-closure-01M6NXBD/spec.md`
**Revision**: post-plan squad (architect-alphonso, reviewer-renata, researcher-robbie) folded 2026-11-05; see
research.md "Squad dispositions". The PR A squad (architect-alphonso, reviewer-renata, on lane-i) added IC-11 and
IC-12 (WP16, WP17); see tasks.md "PR A squad dispositions".

## Summary

The charter service gains an **activations** slice. It answers "what should be resolved for this project?", while
**packs** stays the authority on "what is available". An activation is a stored document that references packs by
id. Every question about pack content goes to `packs` through one port, answered from **one verified closure
snapshot per request**:

- composition and closure;
- the ids the closure holds;
- the required floor and the locks;
- presets;
- reachability over the cascade relations.

Nothing about pack content is implemented twice.

The mission lands as two stacked PRs:

- **PR A** builds:
  - shared transport and reserved-operation plumbing;
  - the rooted compose and the closure read (#359, #360);
  - the per-pack mutability policy file (ADR-0063);
  - the required floor;
  - the slice, its storage, and create, read, replace and delete;
  - contract A.
- **PR B** builds:
  - typed graph edges and `packs`' reach operation;
  - select, unselect, deactivate, reactivate and apply-preset, with the cascade (ADR-0059 D2 as revised
    2026-11-05);
  - contract B.

## Technical Context

**Language/Version**: Java 25 (Temurin 25.0.5), Gradle 9.8.1 wrapper, Kotlin DSL build-logic conventions
**Primary Dependencies**: Spring Boot web (existing stack), generated `charter-api` interfaces/DTOs, Jackson (existing
`ActivationJson` NON_NULL mix-ins), networknt JSON Schema for pack files; no new external library (JCS canonicaliser
in-house, research R3)
**Storage**: files under `<config-root>/activations/<encoded id>/activation.json` (a reversible, case-safe directory
name) (temp file + fsync + atomic move;
per-id in-process lock; one replica, ADR-0061 of PR #392); in-memory adapter for unit tests and the scenario suite
**Testing**: Cucumber `@contract` scenarios in a new `contracts/charter/scenarios/activation-register.feature` (red
first), JUnit 5 unit tests, a property test (≥1,000 documents), an 8-writer concurrency test and a context-restart +
injected-failure durability test on the file adapter, ArchUnit, `ReservedOperationCoverageTest`,
`ReservedRefusalCodesTest`; per-PR gate `./gradlew verifyAll`
**Target Platform**: the charter service (JVM jar and GraalVM native image, loopback daemon per worktree)
**Project Type**: single multi-module Gradle service (`service/*`)
**Performance Goals**: cascading select over a 500-artifact fixture < 200 ms p95 on CI, report-only (ADR-0057)
**Constraints**: the Checkstyle limits PR #368 defined, still off on `main` and held by construction (CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1); no new
suppressions; no disabled tests; native-image friendly
**Scale/Scope**: 10 operations served, 4 kept reserved with contract changes; two new modules (`activations`, `web-transport`); changes in `packs`,
`kernel`, `deployable`, contracts and docs

## Charter Check

| Charter rule | How the plan meets it |
|---|---|
| Missions, and squads on contract, architecture and domain changes | Squads run after each phase: plan done, then tasks, each PR's WPs, and pre-PR. |
| BDD first | Every served operation's scenario is written and failing before its code (IC-06, IC-09). |
| DDD and hexagonal slices | The domain receives snapshot values and never a port (ONION rule). Only `activations.infra.packs` may name `packs`, and only `packs.application`. Both are gated in ArchUnit. |
| No compatibility with core | Absent means none now; the divergences are registered (C-006). |
| DIRECTIVE_051 | No new dependency. |
| NO_FULL_HEAVY_SUITES_IN_MISSION | WPs run module tasks; `verifyAll` runs once per PR. |

No violations.

## Project Structure

```
service/
├── activations/                                  # NEW slice: depends on :kernel, :packs, :charter-api
│   └── src/main/java/ai/speckitty/charter/activations/
│       ├── domain/       ActivationDocument, ActivationId, Revision, Jcs, DocumentRules, CascadeRules,
│       │                 ClosureSnapshot (value), Reach (value: reached nodes with relation), ActivationChange,
│       │                 SkippedReport, ActivationRepository (port, domain-owned like the packs repositories)
│       ├── application/  ActivationService, ActivationSelectionService, ActivationExclusionService,
│       │                 ActivationPresetService, AvailablePacks (port), config/
│       └── infra/
│           ├── api/      ActivationController (moved), ActivationJson (moved), mappers, config/
│           ├── packs/    PacksAvailability (adapter: one verified packs read → ClosureSnapshot; reach calls)
│           └── storage/  InMemoryActivationRepository, FileActivationRepository, config/
├── packs/   domain:      PackCompositionResolver.resolveRooted + public compositionClosure (tolerant of unreadable packs
│                         outside the closure); PackCompositionFacts.edges (PR B); PolicyFile; PerPackPolicy
│            application: OfferingReadService.composed(roots) reading facts only for closure packs;
│                         ClosureQueries (one verified read → closure facts, held URNs, floor, levels, presets);
│                         Reachability.reach(...) on GraphWalk over an edge-backed resolver (PR B)
│            infra:       PolicyYaml, descriptor `mutability_policy`, DomainRepositoryService edges (PR B)
├── kernel/  ArtifactKind.InForceWhenAbsent → NONE; PreconditionException + If-Match parsing (moved from packs);
│            ReservedOperation annotation (method- and type-level, moved from packs)
├── web-transport/  NEW shared adapter ring, package ai.speckitty.charter.web (WP16): RefusalHandler, ProblemHandler,
│                   Problems, JsonBodyGuard, the Problem JSON and hints; used by packs.infra.api and
│                   activations.infra.api, names no slice
└── deployable/ ReservedOperationInterceptor (moved, method-aware); activation advices moved out to
             activations.infra.api (WP16); DeployableConfiguration scans; activation storage root from ConfigRoot.activations();
             HexagonalArchitectureTest generalised; ActivationSteps glue
contracts/charter/  openapi.yaml, paths/activations*.yaml, schemas (ActivationPackList, Activation,
                    ActivationConfig, SelectedArtifacts, effective reads +304), CharterRefusalCode raised flags,
                    scenarios/activation-register.feature (new), activations.feature, composition.feature, changes/
contracts/packs/schemas/policy.schema.json (new), pack-descriptor.schema.json (+ mutability_policy)
docs/adr/0063-*.md, docs/conformance/divergence-register.md (DIV-203+), docs/architecture/*, docs/changelog/
```

**Structure Decision:** `activations` is a top-level slice over `packs`, following the 2026-11-05 decision.
`ActivationRepository` is a domain-owned port, the same pattern as `packs.domain.*Repository`. `AvailablePacks` is an
application port. The application service calls it once per request and passes the resulting `ClosureSnapshot`
value into `DocumentRules` and `CascadeRules`, so the domain never reaches outward. Traversal lives in `packs`
(`reach`); activations supplies only the policy: which relations count, the kind scope, stop-at-deny, lineage and
the deny-clearing set.

## Complexity Tracking

| Departure | Why needed | Simpler alternative rejected because |
|---|---|---|
| New Gradle module | ADR-0020 (its own slice); operator, 2026-11-05 | Inside `packs` mixes "available" with "in force" |
| Generalising ArchUnit (#140) and moving reserved-operation and precondition plumbing to shared homes | A second HTTP slice | Leaving them in `packs` forces cross-slice reaches or duplicates them |

## Implementation Concern Map

The concerns are listed in dependency order. A concern belongs to PR **A** or PR **B**.

### IC-01 (A) — Shared transport plumbing

- **Reserved-operation marker.**
  - `@ReservedOperation` becomes method-and-type level and moves to `kernel` (a plain annotation).
  - The interceptor moves to `deployable` and checks the handler method, so one controller can serve some
    operations and reserve others (`skipDefaultInterface=true`).
  - `RESERVED_CONTROLLER_READS_NOTHING` becomes method-scoped.
- **Preconditions.** `PreconditionException` and strong `If-Match` parsing (`PackRevision.matches`) move to `kernel`.
  `RefusalHandler`'s 412/428 mapping is reused.
- **Body limits.** The 1 MiB / null-list guard (`RegistrationBodyAdvice`, `RegistrationLists.BODY_LIMIT`) is shared
  and applied to activation bodies (#323).
- **Requirements:** C-002, FR-006 (body part).
- **Collisions:** PR #393 touches `ProblemHandler`. Rebase onto it if it merges first.
- **Later (IC-11, WP16):** the Spring transport pieces (`RefusalHandler`, `ProblemHandler`, `Problems`,
  `JsonBodyGuard`) move from `packs.infra.api` to `service/web-transport`.

### IC-02 (A) — packs: rooted compose, tolerant of the outside

- `PackCompositionResolver.resolveRooted(available, roots)` and a public `compositionClosure` (`depends_on` + `parent_pack`; the existing private `closureOf` stays `depends_on`-only as `dependsOnClosure`).
- `OfferingReadService.composed(roots)` reads descriptors and facts **only for packs in the closure**. An unreadable,
  duplicate-id or duplicate-name pack outside the closure is never judged.
- `/offering` calls it with every configured pack, so its behaviour is unchanged.
- **Requirements:** FR-009, FR-020, US2 AS2 (#360). The @WIP composition scenario is rewritten against the closure
  read and its stale comment goes.

### IC-03 (A) — packs: per-pack policy file, the floor, and one closure snapshot

- **Policy file.**
  - The descriptor gains `mutability_policy`, and the closed `policy.schema.json` is added.
  - `PolicyYaml` reads the file per pack.
  - A `PerPackPolicy` registry, keyed by pack, replaces the single global map. `levelIn(closure, urn)` answers the
    highest level along the closure and the pack that set it.
  - `PackMutabilityPolicy` (the registration write gate) and the activation lock query share that one function.
    No pack ships a file yet, so registrations do not change.
- **ADR-0063** records the format and cites ADR-0012 D2, D4 and D5 and ADR-0030.
- **The required floor.** `requiredIn(closure)` answers the must-have items of every governing preset in the closure,
  with the requiring pack.
- **The snapshot.** `ClosureQueries` answers, in one `ConsistentRead`:
  - the closure facts in the offering's pack shape (`OfferingPack`, `ResolvedDependency`);
  - the held URNs per kind;
  - the floor, the lock levels and the presets.
- **Requirements:** FR-007, FR-022, FR-023, NFR-007, US6.
- **Collisions:** PR #377 touches `DomainRepositoryService`, `PackCommitStore` and `PackMutabilityPolicyTest`, and
  PR #390 touches `FilesystemPackStore`. Rebase in order.

### IC-04 (A) — the activations slice skeleton and architecture

- **The new module.** `service/activations`, registered in `settings.gradle.kts`, `charter-reports`, the
  `service.yml` and `sonar.yml` module lists, and `DeployableConfiguration` scans. PR #391 touches `settings.gradle.kts`
  and the conventions.
- **The controller move.** `ActivationController` and `ActivationJson` move to `activations.infra.api`.
- **ArchUnit generalisation (#140).**
  - `SLICES` gains `activations`, and the per-slice rules replace the `PACKS_*` constants.
  - Any slice's `infra.api` may name the generated types.
  - Only `activations.infra.packs` may name `packs..`, and only `packs.application`.
  - Bad-fixtures are added for the new slice.
- **Docs.** `docs/architecture/hexagonal-service.md` documents the cross-slice adapter rule. PR #390 touches that file.
- **Storage root wiring.** The activation storage root comes from `ConfigRoot.activations()`. With no `ConfigRoot`
  bound, the service uses the in-memory repository.
- **Requirements:** C-002, NFR-001.

### IC-05 (A) — the activation document domain

- **Document and revision.** `ActivationDocument` (three-state lists, canonical form), the `Jcs` canonicaliser and
  the `sha256:` revision.
- **`DocumentRules`** checks a document against a `ClosureSnapshot`. It refuses:
  - an unknown id, kind, pack or preset;
  - a duplicate pack, or a pin that differs from the held version;
  - an empty deny list;
  - a deny entry on a required or locked artifact.
- **Kernel.** `InForceWhenAbsent` becomes "none"; skill loses its "required" default (divergence entry).
- **Requirements:** FR-005 to FR-008, FR-010 (body), FR-017 (write half), FR-018.

### IC-06 (A) — storage, CRUD and the closure read (scenarios first)

- **`ActivationRepository`.** An in-memory adapter, and a file adapter with a temp file, fsync, atomic move, a
  per-id lock and a `Rename` failure-injection seam mirroring `FilesystemPackStore`.
- **`ActivationService`.**
  - Create: `POST`, and `PUT` with `If-None-Match: *`.
  - Get: `ETag` and `304`.
  - Replace: `If-Match` is optional; without it the last writer wins. A stale `If-Match` answers `412`.
  - Delete.
  - Both `If-Match` and `If-None-Match` on one request answer `400`.
- **The #125 build notes.** Unknown JSON members answer `422`, never dropped. Bean-validation failures answer
  `422 activation_invalid`, and a converter maps snake_case path kinds.
- **The closure read.** `GET /activations/{id}/packs`.
- **Contract A.**
  - `listActivationPacks` with `ActivationPackList` (items are `OfferingPack`).
  - `summary` moves to `EffectiveArtifactList`. `governanceFingerprint` leaves `Activation`; the effective reads
    already carry it.
  - `If-None-Match`/`304` on `getActivation` and the four effective reads.
  - The absent-means-none texts in `ActivationConfig`, `SelectedArtifacts`, `activatedKinds` and the
    select/unselect/deactivate paths.
  - `x-reserved` is removed from the four CRUD operations. `CharterRefusalCode` raises `activation_not_found`, `activation_invalid`, `artifact_required` and `artifact_locked`,
    and `ReservedRefusalCodesTest` follows.
  - A changelog fragment.
- **Scenarios.** `activation-register.feature` carries the US1, US2 and US6 scenarios.
- **The reserved pins.** `ReservedOperationCoverageTest` counts only `@reserved` blocks and goes from 13 to 9. The
  "501 before binding" pin moves to `selectActivationArtifact`.
- **Durability.** The restart and failure-injection integration test (NFR-005), the concurrency test (NFR-004) and
  the round-trip property test (NFR-003).
- **Requirements:** FR-001 to FR-004, FR-009, FR-019, FR-021, NFR-002 to NFR-005, SC-001 to SC-004 (A part).

### IC-07 (B) — packs: typed edges and reach

- **Typed edges.** `PackCompositionFacts.edges` carries `(source, relation, target)` as written, for every
  graph-authored kind. Component relationships and registered shard edges join the same set.
- **One traversal.** `Reachability.reach(snapshot, starts, relations, stop, lineage)` is built on `GraphWalk` over an
  edge-backed `ComponentResolver`. It returns the reached nodes with the relation and the predecessor, and the
  nodes it stopped at.
- **Enhancer lookup.** `enhancersOf(snapshot, urns)` serves the deny-clearing and shared checks. *Removed 2026-11-06
  (ADR-0059 amendment A4): `enhances` is walked as a cascade relation, so neither check needs a lookup of its own.*
- **No second walker anywhere.**
- **Requirements:** enables FR-012 to FR-015. PR #377 touches `DomainRepositoryService`.

### IC-08 (B) — cascade rules (activations domain)

`CascadeRules` turns a `reach` result into the selection delta and the skipped report. It handles:

- the kind scope and the `["all"]`-mixed refusal;
- stop-at-deny with `viaRequires` and `requiredBy`;
- `unreachedBehindDeactivated`;
- the lineage pass-through policy (`requires` and `scope` only);
- the `includeDeactivated` clearing set (`enhances`, ADR-0059 D2.2);
- deactivate's `skippedShared`, whose active set is `selected` plus the floor, counting active enhancers' references.

It never adds an enhancer to `selected`.

**Requirements:** FR-012 to FR-015.

### IC-09 (B) — per-artifact and preset writes (scenarios first)

- **Operations.** Select and unselect (`mission_type` maps to `missionTypes`), deactivate and reactivate, and
  apply-preset (replace each listed key; `force` clears).
- **Answers.** An `ActivationChange` with `applied` and `cleared` (`ArtifactReference`) and `skipped`. A no-op
  answers `200` with the same revision. A missing `If-Match` answers `428`. An unknown path id answers `404`.
- **Contract B.** `x-reserved` is removed from the five writes, `ReservedRefusalCodesTest` follows, and a changelog
  fragment is added.
- **The reserved pins.** `ReservedOperationCoverageTest` goes from 9 to 4. The binding pin is retired: no reserved
  operation takes a body.
- **Scenarios.** US3 to US5.
- **Requirements:** FR-011 to FR-016.

### IC-10 (A and B) — documentation and divergences

- **ADR amendment notes.**
  - ADR-0020 D6: "an empty document means every kind's default".
  - ADR-0013 D2: "absent means all", and the `force` interplay.
  - ADR-0012 D2: the policy-file pointer to ADR-0063.
- **Architecture docs.** `offering-and-activation.md` ("answers 501"), `logical-model.md` and `resolution-model.md`.
- **Divergence register.** DIV-203 onwards (C-006).
- **Changelogs.** A docs changelog fragment per PR.
- **Issue #360.** An acceptance re-point comment when PR A opens.
- **Open review items** (research.md).

### IC-11 (A) — shared web transport (WP16, post-PR-A squad)

- **The new module.** `service/web-transport` (`ai.speckitty.charter.web`) holds the service-wide HTTP transport:
  - `RefusalHandler`, `ProblemHandler` and `Problems`;
  - `JsonBodyGuard`;
  - the `Problem` JSON and its hints.

  `packs.infra.api` and `activations.infra.api` both depend on it.
- **The activation advices** (`ActivationBodyAdvice`, `ActivationProblems`) move from `deployable` into
  `activations.infra.api`.
- **ArchUnit:**
  - `TRANSPORT_STAYS_IN_ADAPTER` loses its `DEPLOYABLE` exemption;
  - the web ring is an adapter ring that no domain or application ring may depend on, and it names no slice;
  - no `..domain..` class depends on `kernel.transport..`.

  The decision is recorded in `hexagonal-service.md`.
- **Behaviour-preserving.**
- **Requirements:** C-002, C-005, FR-006, NFR-001.

### IC-12 (A) — activation register correctness fixes (WP17, post-PR-A squad)

- **Preconditions before content:** 412 before 422 or 409, with no snapshot taken.
- **Storage:** a reversible, case-safe directory name, and striped (bounded) locks.
- **Packs:**
  - a typed `UnknownRootException`;
  - one de-duplicated preset list for the presets map and the floor;
  - the registration gate composes and reads its policy in one `composedWithFacts`.
- **Tests:** service-level races, and a non-fakeable atomicity test; 401/403 scenarios.
- **Refusal bodies:** `RequiringPack.via` from the `parent_pack` chain; complete 422 `violations`.
- **Requirements:** FR-001, FR-003, FR-006, FR-007, FR-019, FR-022, NFR-001, NFR-004, NFR-005, NFR-007, C-001, C-002.

## Rebase order

`main` (with the merged PR #368 and PR #376, ADR-0059) → PR #377 / #379 / #384 / #390 / #391 / #392 / #393 / #394 as they merge → PR A → PR B.

Each WP rebases onto the current lane base before review.
