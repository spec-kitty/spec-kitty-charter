# Implementation Plan: Agent profile reads

**Branch**: `kitty/mission-agent-profile-reads-01M70ZDM` (PR branch `issue-407-agent-profile-reads` → `main`) | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/agent-profile-reads-01M70ZDM/spec.md`

## Summary

The service already stores and parses agent profiles. Every pack-stored kind has a domain type and a storage
binding (verified 2026-11-09: `KindBindings.ALL`; templates go through `TemplateBinding`; anti-patterns live
inside directives; step enhancements come with #437). So this mission is contract and REST-adapter work, plus one
walk and one module.

1. **Pin bump.** Move the open-packs pin to `a2a9379` and re-vendor `packs/internal`, `packs/qa` and the lock.
   Move the corpus counts and baselines with it.
2. **New module `:profiles-edge`.** It holds the profiles slice's `application`, `infra.api`, `infra.packs` and
   `infra.activations` rings, so `:profiles` stays the pure domain module that `:packs` binds. An ADR-0006/0037
   amendment and new ArchUnit rules record and gate it.
3. **Governance walk in `packs.application`.** Generalise the action-context walk's engine so it can start from any
   node. A profile's walk replaces `scope` with a `lineage` step (`specializes_from`, transitive). It runs over a
   pack's composed closure (pack-scoped), or over the merged closure with the in-force predicate (activation-scoped).
4. **Pack-scoped profile read and list.**
   - `GET /packs/{packId}/artifacts/agent_profile/{artifactId}` is served by `profiles.infra.api`. It answers the
     `Artifact` envelope with an `agentProfile` member and a `governance` list.
   - `agent_profile` joins `ArtifactReadService.SERVED`, so the list, the `?kind=` filter, the references read
     and DELETE all open to it.
   - The generic packs controller never renders a profile body.
5. **Activation-scoped read.**
   - `GET /activations/{activationId}/artifacts/{kind}/{artifactId}` is served by `activations.infra.api` for
     every served kind. It answers the winner (kind, id, `packId`, `level`, state, required, policy, reason), with
     `artifact` (pack-scoped body) and `self` links.
   - The literal `agent_profile` route is served by `profiles.infra.api` over the same activations application
     service. It answers the same winner fields, plus the embedded profile body and the governance with states.
6. **Links.**
   - Effective-list items, graph nodes and action-context items of a served kind carry `effective-artifact` (the
     activation-scoped read) and `artifact` (the winner's pack-scoped read).
   - The root links `activations`.
7. **Usability script.** Commit `scripts/usability/service-usability-run.sh`. It is a link-following client, and
   it is the acceptance proof. It is run red on `main` (baseline) and green at the end.

## Technical Context

**Language/Version**: Java 25 (Temurin 25, `/usr/lib/jvm/temurin-25`), Gradle multi-project (Kotlin DSL), Bash + `curl` + `jq` for the usability script
**Primary Dependencies**: Spring Boot 4 / Spring MVC; OpenAPI Generator (`charter.contract-api` plugin) for `:charter-api`; ArchUnit; JUnit Jupiter; Cucumber-JVM for the `@contract` scenarios. No new dependency (DIRECTIVE_051: nothing to vet; verification metadata unchanged)
**Storage**: Packs on the filesystem pack store (existing bindings); activations in the file activation repository. No storage change
**Testing**: Test first (DIRECTIVE_034). `@contract` scenarios in `contracts/charter/scenarios/` first, then unit tests per ring, then ArchUnit rules with bite fixtures. The open-packs corpus test covers every profile (NFR-001), and `./gradlew verifyAll` includes `:packs:wholePackRoundTrip`
**Target Platform**: Linux server (the charter service JVM)
**Project Type**: Single service, multi-module (`service/*`, `contracts/`)
**Performance Goals**: A profile read answers in under 500 ms at p95 on a warm service (NFR-003). The walk is linear in the edges of the closure
**Constraints**:
- No ArchUnit rule is weakened (C-001).
- At most one change fragment per contract change (ADR-0047).
- 0.x versioning (ADR-0022).
- Minimal edits to #437's hot files (C-005).
- Gradle runs in the foreground, `--max-workers=1`.
**Scale/Scope**:
- About 30 agent profiles in `built-in` at the pin, 169 `requires`, 35 `suggests` and 5 `specializes_from` edges.
- One new module, three new contract paths, two new schemas, one widened enum, one new relation.

## Charter Check

| Gate (charter) | How this plan meets it |
|---|---|
| Never diverge from the charter's own model | Bodies come from the stored `AgentProfile` and round-trip (NFR-001). Lineage comes from graph edges only, as the model says. |
| Never break the OpenAPI contract | Additive on 0.x. `ArtifactKindToken` widens (an enum widening, recorded under `### Added` with a note to clients that switch on it). Every change has a fragment, scenarios and examples. |
| Adversarial review squad at the plan point and before merge | Plan squad (architecture + contract + scope lenses) before tasks; pre-PR squad after consolidation. |
| Gates before merge | `verifyAll` on Java 25, the style checks, the whole-pack round trip, the docs checks (`check_docs.py`, markdownlint-cli2, cspell). |
| BDD / SbE, test first | `@contract` scenarios red first per surface (`agent-profiles.feature`, `effective-artifact.feature`). |
| DDD in core's vocabulary | The glossary terms of the spec's Domain Language; "governance", "lineage" and "walker rules" are added to `docs/context/` if absent. |
| Living documentation | `docs/api/service-contract.md` rows for R2 (`agent_profile`) and R8; `hexagonal-service.md` module table; `hypermedia-hateoas.md` relation table; the ADR amendment; the changelog. |
| Supply chain | No new dependency. |

No violation needs justification.

## Project Structure

### Documentation (this mission)

```text
kitty-specs/agent-profile-reads-01M70ZDM/
├── spec.md
├── plan.md              # this file
├── research.md          # decisions, alternatives, squad dispositions
├── data-model.md        # governance entry, effective artifact, walk
├── contracts/           # contract sketch for the three paths and two schemas
├── quickstart.md        # how to run the usability script
├── research/squad-findings.md
└── tasks/
```

### Source Code (repository root)

```text
gradle.properties                                   # openPacksRef
packs/internal, packs/qa, packs/open-packs.lock.yaml  # re-vendored
settings.gradle.kts                                 # includes service/profiles-edge (if not discovered)
service/
├── profiles/                                       # unchanged: domain only
├── profiles-edge/                                  # NEW module
│   └── src/main/java/ai/speckitty/charter/profiles/
│       ├── application/                            # ProfileReadService, ports ProfileSource, EffectiveProfiles
│       └── infra/
│           ├── api/                                # AgentProfilesController implements AgentProfilesApi; ProfileDtos
│           ├── packs/                              # ProfileSource over packs.application
│           └── activations/                        # EffectiveProfiles over activations.application
├── packs/src/main/java/.../packs/application/
│   ├── ActionContextWalk.java                      # engine generalised (start node, first step)
│   ├── GovernanceWalk.java (or a method on it)     # LINEAGE first step
│   ├── ArtifactReadService.java                    # SERVED += AGENT_PROFILE; Item.Foreign; withGovernance
│   └── ClosureView.java                            # governance(urn, depth, states)
├── packs/src/main/java/.../packs/infra/api/        # ArtifactDtos: list item for a foreign kind; root link
├── activations/src/main/java/.../activations/
│   ├── application/EffectiveReadService.java       # artifact(id, kind, artifactId, depth)
│   ├── application/PacksSession.java               # governance(...), winner(...)
│   ├── infra/packs/ClosureViewSession.java
│   └── infra/api/                                  # GET /activations/{id}/artifacts/{kind}/{artifactId}; item links
├── deployable/                                     # imports profiles-edge config; ArchUnit rules + fixtures
contracts/charter/
├── paths/packs_packId_artifacts_agent_profile_artifactId.yaml          # NEW (GET; tag AgentProfiles)
├── paths/activations_activationId_artifacts_kind_artifactId.yaml       # NEW (GET; tag Activations)
├── paths/activations_activationId_artifacts_agent_profile_artifactId.yaml  # NEW (GET; tag AgentProfiles)
├── schemas/AgentProfile.yaml, ProfileGovernanceEntry.yaml, EffectiveArtifactRead.yaml  # NEW
├── schemas/Artifact.yaml, ArtifactKindToken.yaml, EffectiveArtifact*.yaml, Links rel table  # widened
├── examples/…, scenarios/agent-profiles.feature, scenarios/effective-artifact.feature
└── changes/2026-11-09-agent-profile-reads.md
scripts/usability/service-usability-run.sh          # NEW (FR-011)
docs/adr/0037-…md (amendment), docs/architecture/hexagonal-service.md, docs/development/hypermedia-hateoas.md,
docs/api/service-contract.md, docs/changelog/CHANGELOG.md
```

**Structure Decision**:
- A new Gradle module, `:profiles-edge`, in `service/profiles-edge` (operator, decision `01M710E3M0B37N99XEK4A7JEY0`).
- Its packages keep the slice name (`ai.speckitty.charter.profiles.*`), so the ArchUnit slice rules see one
  `profiles` slice spread over two modules.
- Module dependency direction: `:profiles` ← `:packs` ← `:activations` ← `:profiles-edge` ← `:deployable`.
- There is no cycle. `:packs` and `:activations` never depend on `:profiles-edge`.

## Complexity Tracking

| Addition | Why needed | Simpler alternative rejected because |
|---|---|---|
| A new module `:profiles-edge` | Rings in `:profiles` would create a cycle with `:packs` | Splitting `:profiles` into a domain module touches every storage import. Putting the rings in `:packs` grows another slice's rings inside `packs` (operator rejected both). |
| Two controllers on one activation path family | One application service is wired to several endpoints (operator ruling). Only the profiles slice renders a profile body. | One activations controller embedding every body needs a cross-slice rendering seam at the REST layer, which the operator ruled out. |

## Implementation Concern Map

> Implementation concerns are not work packages. `/spec-kitty.tasks` slices them.

### IC-01 — The open-packs pin bump

- **Purpose**: Put the corpus at `a2a9379`, so that `java-jenny → java-conventions` (#405), the edge curation (#135)
  and the `spdd` pack are what the service and the tests read (#435).
- **Relevant requirements**: FR-001, SC-003.
- **Affected surfaces**:
  - `gradle.properties` (`openPacksRef`) and the pin comment;
  - `./gradlew syncPacks -PupdatePacksLock=true`, which re-vendors `packs/internal`, `packs/qa` and
    `packs/open-packs.lock.yaml`;
  - `WholePackOpenPacksRoundTripTest` (constituent count, `Counts`), `whole-pack/expected-red.yaml`, the corpus
    tests that pin counts or digests (`MissionTypeCorpusTest` and others), and the CI workflow if it pins the ref.
- **Sequencing/depends-on**: none. It goes first, and every later WP builds on it.
- **Risks**:
  - It collides with #437 WP01's files. Whoever merges second rebases; announce it through the orchestrator.
  - The deleted paradigms (execution-lanes, git-flow, shared-branch-ci, extract-then-publish) may be referenced
    by this repository's project pack or `charter.yaml`. Check, and record any divergence.
  - Every change of count is traced to an upstream commit in the WP's notes, never adjusted blindly
    (no fake green).

### IC-02 — The ADR amendment, the module and the ArchUnit rules

- **Purpose**: Record and gate where the profiles slice's outer rings live.
- **Relevant requirements**: C-001.
- **Affected surfaces**:
  - `docs/adr/0037-…` (amendment "the slice edge module"), `docs/architecture/hexagonal-service.md`;
  - `service/profiles-edge/build.gradle.kts`, `settings.gradle.kts`, `service/deployable/build.gradle.kts` and its
    configuration import;
  - in `HexagonalArchitectureTest`, new rules with bite fixtures:
    - `profiles.application` names only itself, `profiles.domain`, kernel, java, JSpecify and Spring;
    - `profiles.infra.packs` names only `packs.application` and `packs.domain` of packs;
    - `profiles.infra.activations` names only `activations.application` and `activations.domain`;
    - `profiles.infra.api` names no other slice;
    - no `packs` or `activations` class names `profiles.application` or `profiles.infra`.
  - The `SLICES` list and the existing slice rules accept the module split.
- **Sequencing/depends-on**: IC-01 (the base only).
- **Risks**: the existing rules' package globs must cover classes in the new module (they read the classpath of
  `:deployable`). Add a bite test per new rule.

### IC-03 — The governance walk (packs.application)

- **Purpose**: Walk a profile's governance under the walker rules (decision `01M710DXJR9AK5WBGVCDJNANA8`).
- **Relevant requirements**: FR-003, FR-004, NFR-004.
- **Affected surfaces**:
  - `ActionContextWalk`: extract the run engine, so the first step and the start node are parameters. The
    action walk's answers are unchanged, and the existing tests pin them.
  - New `Step.LINEAGE(SPECIALIZES_FROM)`. It is listed before `REQUIRES`, and the profile and its lineage seed the
    later steps.
  - `ClosureView.governance(Urn, depth, InForceStates)` over the merged closure.
  - A pack-scoped entry point over the pack's composed closure (`ClosureQueries.snapshot(Optional.of(List.of(pack)))`
    edges, every node in force). It reports `held` per entry from `EdgeIndex.placesOf`.
- **Sequencing/depends-on**: IC-01.
- **Risks**:
  - The `Step` enum may be mapped to the wire (`EffectiveActionContext`). Adding a constant must not leak a new
    token into the action context. Map exhaustively.
  - Cycle fixture for `specializes_from` (NFR-004).

### IC-04 — The contract: profile read, activation-scoped read, links, root

- **Purpose**: Shape every wire change test-first (ADR-0022, ADR-0047). This answers #96 for `agent_profile` and
  #407's shape.
- **Relevant requirements**: FR-002, FR-004 to FR-010, NFR-002, C-004, C-005.
- **Affected surfaces** (see `contracts/` of this mission):
  - the new paths and schemas;
  - `Artifact.yaml` gains the `agentProfile` member and an optional `governance`;
  - `ArtifactKindToken` and `ActivatableKind` are aligned;
  - the effective item schemas gain `_links`;
  - the root gains `activations`;
  - the relation table gains `effective-artifact` (and `body` if used);
  - examples, `@contract` scenarios (red first), the change fragment;
  - `ContractDescriptionsTest` / `WireTokenParityTest` updates.
- **Refusals**: no new code. An unknown profile is `artifact_not_found`; check `ABSENT`'s mapping, and add an
  `AGENT_PROFILE → ARTIFACT_NOT_FOUND` entry rather than a new code (C-005).
- **Sequencing/depends-on**: IC-02 for the tag and generated interface names. It can be drafted in parallel.
- **Risks**:
  - Spring's literal-over-pattern routing must match the contract's overlapping paths. A test pins that GET and
    DELETE route to the right controller.
  - The client-java generation gets new types: `client-java` tests.

### IC-05 — Pack-scoped profile read (profiles-edge + packs.application)

- **Purpose**: Serve the profile body and governance from its pack, and open the list, the references and DELETE.
- **Relevant requirements**: FR-002 to FR-006, FR-010, NFR-001, SC-003, SC-004.
- **Affected surfaces**:
  - In `ArtifactReadService`:
    - `SERVED += AGENT_PROFILE`, placed in list order;
    - a kind-neutral `Item.Foreign(CharterArtifact, PackRevision)`;
    - a verified `withGovernance(pack, kind, id, depth)` that reads the item and the walk in one consistent read,
      with one fingerprint.
  - `ArtifactRemovalService`, `ArtifactPosition`.
  - `ArtifactDtos`: the list item title comes from `label()`. The generic GET is never reached for a foreign kind;
    if it is, it answers `kind_not_found` with a test.
  - `profiles.application.ProfileReadService` and the `ProfileSource` port; `profiles.infra.packs`;
    `profiles.infra.api.AgentProfilesController` and `ProfileDtos` (every schema field mapped).
  - A corpus test: every built-in profile reads, and its body round-trips.
- **Sequencing/depends-on**: IC-02, IC-03, IC-04.
- **Risks**: the mapping must be exhaustive. A test fails on any schema property that is not mapped, with the
  generated DTO as the oracle.

### IC-06 — Activation-scoped read and effective links (activations + profiles-edge)

- **Purpose**: Read any served artifact under an activation, read a profile's body and governance there, and link
  the effective items.
- **Relevant requirements**: FR-007, FR-008, FR-009, SC-001.
- **Affected surfaces**:
  - `EffectiveReadService.artifact(activationId, kind, artifactId, depth)` and `PacksSession.governance(…)`.
    `ClosureViewSession` returns the winner plus governance entries with states and suggestion candidates.
  - `ActivationController` and its mapper. `EffectiveReadMapper` adds item links to the list, the graph and the
    action context.
  - `profiles.infra.activations` (`EffectiveProfiles`), and `AgentProfilesController`'s activation route. It
    embeds the body read from the winner's pack through `ProfileSource`.
  - The root controller gets the `activations` link.
- **Sequencing/depends-on**: IC-03, IC-04, IC-05.
- **Risks**:
  - Entity tags and the governance fingerprint follow the effective reads (closure fingerprint).
  - Pagination of the effective list is unchanged.

### IC-07 — Usability script, docs and changelog

- **Purpose**: Prove the acceptance, and keep the docs living.
- **Relevant requirements**: FR-011, SC-001, SC-002, NFR-003.
- **Affected surfaces**:
  - `scripts/usability/service-usability-run.sh`: it builds and starts the service, registers the packs,
    activates `charter.yaml` plus `java-jenny`, follows links only, times 20 reads and exits non-zero on any
    unresolved hop.
  - The red-baseline record in `research/`.
  - `docs/api/service-contract.md`, `docs/development/hypermedia-hateoas.md`, the guides, and
    `docs/changelog/CHANGELOG.md`.
  - The usability note's follow-up (one line).
- **Sequencing/depends-on**: the script is written first, and run red on the IC-01 base. It is run green after
  IC-06.
- **Risks**:
  - Registering real packs is refused over README/PROVENANCE files (#451). The script copies `built-in` into
    `<config-root>/packs/`, as the run did, and registers the others with documented workarounds. The workarounds
    are listed, not hidden.

## Fold of the plan-point squad (2026-11-09)

Three lenses reviewed the plan: architecture (architect-alphonso), contract (reviewer-renata), and walk, corpus
and testing (java-jenny). The squad raised four blockers, all folded. The operator ruled on one of them (decision
`01M7115WE6WQ7Q41HSS9ZHD1F3`). Each finding's disposition is in `research.md` § Squad dispositions. These rules
supersede the IC text above where they differ.

### Walk (IC-03)

- **W1 — lineage under an activation (operator, as confirmed).**
  - `LINEAGE` follows `specializes_from` through the in-force predicate, as every other step does. Under an
    activation, an ancestor that is not in force is not listed and seeds nothing.
  - Ancestors in force seed `REQUIRES` and `SUGGESTS`.
  - This diverges from the cascade's pass-through rule (`CascadeRules`, `ReachRequest.Lineage`). The divergence is
    recorded in `research.md` D4, in the contract description and in `docs/api/service-contract.md`.
- **W2 — `held=false`.**
  - `EdgeIndex` gains `writtenLinks()`. These are the written edges of the closure's packs, dangling targets
    included.
  - Only the pack-scoped walk uses them. It lists an unheld target with `held: false` and never expands it.
  - Red test: a fixture profile whose `specializes_from` parent no pack of the closure holds.
- **W3 — the profile's own vocabulary.** `VOCABULARY` is seeded from `{start} ∪ listed` when the start is a profile.
  The action walk is unchanged.
- **W4 — the start node.**
  - The start is exempt from the predicate, as the action's start is. A profile read under an activation answers
    its governance whatever its own state, and the state is reported on the read.
  - Entries carry no `enhancedBy` or `enhances`. Profiles take no step enhancements, and this is stated in the
    schema description.
- **W5 — one read, one closure.**
  - Pack-scoped: the body and the walk are computed in one `ConsistentRead.read` supplier, with no inner retry.
  - The closure is the pack's composed `depends_on` closure, and its merged view is walked.
  - A closure whose merge or compose is refused refuses the profile read with the same code the references read
    gives. A test pins it.
- **W6 — no wire leak.** `Step` reaches no existing wire: `ClosureViewSession.contextOf` drops step, via and hops.
  The governance wire has its own step enum.

### Contract (IC-04)

- **C1 — two entry schemas, no overloaded field.**
  - **`PackGovernanceEntry`** (pack-scoped) has:
    - `kind` (`NodeKind`), `artifactId`, `urn`;
    - `step`, `relation`, `via`, `hops`;
    - `suggestion{when, reason}` (only on a `suggests` entry);
    - `packId` (nullable), `held`, `_links`.
  - **`EffectiveGovernanceEntry`** (activation-scoped) is `allOf` `EffectiveArtifact` plus `step`, `relation`, `via`
    and `hops`. A suggestion sits inline (`suggestion`) as in the action context. There is no `suggestions[]`.
- **C2 — `excluding` on the wire.**
  - It is a closed object, `{ wholeFields: string[] | null, fieldValues: [{ field, values: string[] }] | null }`.
    At most one of the two is set, and an absent `excluding` is `null`.
  - It keeps the authored order, empty value lists included.
  - A round-trip test covers both forms.
- **C3 — kinds and tokens.**
  - Entry kinds use `NodeKind`, not `ArtifactKindToken`.
  - `ArtifactKindToken` gains `agent_profile`, appended last, so it stays equal to `SERVED`.
  - New `WireTokenParityTest` pairs cover the entry `kind`, `relation` and `step` enums. The line about "aligning"
    `ActivatableKind` is struck.
- **C4 — headers on the activation routes.** Both activation routes declare:
  - `Charter-Source-Fingerprint` (required);
  - `ETag`, a `304` on `If-None-Match`, and a `400` for `depth`, as the other effective reads do.
  - New `EffectiveTag.Read.ARTIFACT` and `PROFILE` constants, whose query covers kind, id and depth.
- **C5 — fragments and ETag.**
  - Two fragments: `…-agent-profile-reads.md` (`### Added`) and `…-agent-profile-reads-changes.md`
    (`### Changed`). The second covers:
    - the served-kinds wording;
    - `artifact_not_found` on the pack path;
    - the profile's `ETag`.
  - The pack-scoped profile read carries `ETag` = the pack revision, as a directive does, because DELETE needs
    `If-Match`. NFR-002 reads "one fragment per `###` block".
- **C6 — overlapping operations.**
  - Separate response schemas: `EffectiveAgentProfileRead` is `allOf` `EffectiveArtifactRead` plus `agentProfile`
    and `governance`. The generic read never carries a body.
  - Tags: the generic activation route is tagged `Activation`. A new top-level tag, `AgentProfiles`, holds
    `getPackAgentProfile` and `getEffectiveAgentProfile`.
  - The generic descriptions say that `agent_profile` is answered by the literal operation.
  - Operation coverage gets a 2xx scenario for each new operation, including the generic effective read on a
    directive.
- **C7 — refusals.**
  - Rule: kinds served from now on use `artifact_not_found`, so `ABSENT` gains `AGENT_PROFILE →
    ARTIFACT_NOT_FOUND`.
  - The only `CharterRefusalCode.yaml` edits are two description sentences: the served list of `kind_not_found`,
    and the scope of `artifact_not_found`.
  - The per-kind lists in the path descriptions are updated.
  - `errors.feature`, `ContractDescriptionsTest` and `ReservedRefusalCodesTest` are not touched.
  - The orchestrator is told about the two `CharterRefusalCode.yaml` sentences, which is #437's hot file.
- **C8 — relations.**
  - `effective-artifact` gets a table row and a ruleset enum entry in the same commit (`RelationTableParityTest`).
    The `On` column of `artifact` is widened.
  - The root's `activations` link carries `method: POST`, and its table row becomes "In the contract".
  - US3 and the script read "POST to `activations`, then follow `self`".
  - `root.feature` is updated.
- **C9 — citations.**
  - `AgentProfile` properties cite `service/profiles/.../AgentProfile.java` accessors (`x-source`).
  - Governance properties use `x-derived`.
  - The descriptions that list five served kinds are reworded: `ArtifactKindToken`, `ArtifactKind`, `Artifact`
    (both places), `ArtifactSummary` and `Depth`.
- **C10 — the mapping oracle.** The oracle is `contracts/packs/schemas/agent-profile.schema.json`, in camelCase. An
  exhaustive test walks the schema's properties.
- **C11 — scenarios.**
  - The new file is `scenarios/agent-profile-reads.feature`. `profiles.feature` (#380, `@WIP`) is left alone.
  - The activation read answers the stored winner and never resolves `enhances`, so it is not #380's effective
    profile.
  - Steps live in `service/deployable/src/test/java/.../deployable/bdd/` (a new `ProfileSteps`), with the
    vocabulary in `scenarios/README.md`.

### Architecture and build (IC-02, IC-05, IC-06)

- **A1 — registration lists for `:profiles-edge`.**
  - `settings.gradle.kts`.
  - `DeployableConfiguration`'s `@ComponentScan`: the `profiles.application.config` package and the
    `profiles.infra.{api,packs,activations}.config` packages.
  - `charter-reports` `serviceModules`: module → a list of packages, so `profiles-edge` owns
    `profiles.application` and `profiles.infra`.
  - `sonar.yml` and `scripts/ci/mutation_summary.py`.
  - Plugins: `charter.spring-conventions`, `charter.hand-written`, `charter.mutation-conventions`, plus
    `mutationFloors`.
  - Explicit `implementation(project(":packs"))`, and `:deployable` → `implementation(project(":profiles-edge"))`.
- **A2 — ArchUnit, each rule with a bite fixture.** `applicationIsEdge("profiles")` already holds the application
  ring. New rules:
  - (a) `profiles..` outside `profiles.infra.packs` names no `packs..`.
  - (b) `profiles.infra.packs` names only `packs.application` and `packs.domain`.
  - (c) `profiles..` outside `profiles.infra.activations` names no `activations..`.
  - (d) `profiles.infra.activations` names only `activations.application` and `activations.domain`.
  - (e) No `packs..` or `activations..` class names `profiles.application..` or `profiles.infra..`.
  - Every new package has a `@NullMarked` `package-info`.
  - The profile controller builds its own links and its fingerprint header, as `ActivationController` does. It
    never names `packs.infra.api` (no `Hypermedia` and no `ArtifactDtos`, per the operator's steer to the
    mission-types session).
  - The `Charter-Source-Fingerprint` header name moves to `kernel.transport`, and the existing users switch to it.
- **A3 — one session for the activation profile read.**
  - `EffectiveReadService.artifact(activationId, kind, artifactId, depth, ifNoneMatch)` opens one `PacksSession`.
    From it, it returns the winner's `CharterArtifact`, the winner's pack and level, the state, and (for a
    profile) the governance.
  - This is backed by `ClosureView.effectiveArtifact(kind, id, depth, states)`.
  - The body is never read a second time through `ProfileSource`.
  - New `activations.domain` value types carry the governance entries, with their own step enum.
- **A4 — widening `SERVED`.**
  - The `ABSENT` entry is added, so DELETE cannot answer 500.
  - `agent_profile` goes last in `SERVED`. `ArtifactPosition` follows, and `ArtifactReadServiceTest` and
    `WireTokenParityTest` are updated.
  - The generic `ArtifactDtos` path throws `IllegalStateException` for `Item.Foreign`. It cannot be reached, and a
    full-context routing test proves that.
- **A5 — routing.**
  - Same base-path prefix.
  - A `:deployable` full-context test pins that:
    - a GET on the literal paths reaches the profile controller;
    - a DELETE and `/references` reach the generic handler;
    - a PUT on `agent_profile` answers `405`.
- **A6 — smaller items.**
  - A problem-mapping parity test for the profile's activation route against `ActivationProblems`.
  - `NullOmissionTest` covers the new schemas.
  - A parity check for `effective-artifact` built outside packs.
  - The amendment covers ADR-0006 ("one module per slice") as well as ADR-0037.
  - Runtime hints for the new DTOs, as `ActivationRuntimeHints` provides for its own.

### Pin bump (IC-01) and corpus

- **P1 — the tests to move.**
  - `WholePackOpenPacksRoundTripTest`: the counts, the 315 constituents, and "26 built-in + 5 qa".
  - `PackManifestParityTest` (315/317), `MissionTypeCorpusTest`, `MissionStepContractCorpusTest` (28),
    `CompanionDigestTest` (10).
  - `whole-pack/expected-red.yaml`.
  - Every count change is traced to an upstream commit in the WP's notes.
- **P2 — divergences.** `.kittify/missions/software-dev/actions/implement/index.yaml:33-35` and
  `.kittify/missions/built_in_step_contracts/implement.step-contract.yaml:15-17` name paradigms the bump deletes.
  These are recorded as divergences in the WP. They are core's project files, and `charter.yaml` selects none of
  them.
- **P3 — `verifyAll` does not run the whole-pack round trip.** The pin-bump WP's definition of done runs
  `./gradlew :packs:wholePackRoundTrip -PopenPacksDir=…` explicitly, and CI runs it through `CI gate / gate`.
  `syncPacks` re-vendors `internal` and `qa` only. `spdd` is not vendored, which corrects the purpose line.
- **P4 — the corpus test covers every profile.** That is 31 (26 built-in plus 5 qa), not only built-in. The spec
  states that body references (`directive-references` and others) are body fields, not walked edges.
- **P5 — data-model types.**
  - `writingStyle` and `complexityPreference` are arrays.
  - A null and an empty `selfReviewProtocol` and `specializationContext` are kept apart where the domain keeps them
    apart. Where it collapses them, that is recorded and asserted.

### Usability script (IC-07)

- **U1 — the note at a commit.** The engineering note is cited at `c1c2ab56` (branch
  `docs/service-usability-run-2026-11-09`).
- **U2 — packs as pinned.**
  - The script registers packs as pinned, unmodified.
  - It checks the copied `built-in` tree against its git tree digest.
  - When a registration is refused (#451, #452), it prints the refusal and the workaround it applies by name. The
    summary lists every workaround, so the proof is never silent about them.
  - The profile proof needs only `built-in` (and `qa`) plus the project pack.
- **U3 — the red baseline.** The SC-002 baseline is recorded with the `main` commit and the pin it ran at.
