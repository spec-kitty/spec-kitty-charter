---
work_package_id: WP02
title: Service and contract without edge-minting fields
dependencies: []
requirement_refs:
- FR-003
- FR-004
- FR-005
- FR-006
- FR-007
- FR-008
- FR-009
- FR-010
- FR-011
- FR-014
- NFR-001
- NFR-003
- SC-001
- SC-002
- SC-003
- C-001
- C-002
- C-004
planning_base_branch: claude/240-drop-edge-fields
merge_target_branch: claude/240-drop-edge-fields
branch_strategy: Planning artifacts for this mission were generated on claude/240-drop-edge-fields. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/240-drop-edge-fields unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-graph-documents-sole-edge-source-01M6P9D0
base_commit: afb597e34cbb33eb6e6860007684ea8cf5c7cdb6
created_at: '2026-11-05T17:06:20.221549+00:00'
subtasks:
- T005
- T006
- T007
- T008
- T009
- T010
- T011
- T012
- T013
phase: Phase 2 - Service
history:
- at: '2026-11-05T17:40:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/
create_intent:
- contracts/charter/changes/2026-11-05-edges-only-in-graph-documents.md
execution_mode: code_change
model: ''
owned_files:
- service/**
- contracts/**
- gradle.properties
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – Service and contract without edge-minting fields

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## ⚠️ IMPORTANT: Review Feedback

**Read this first if you are implementing this task!**

- **Has review feedback?**: Check the `review_ref` field in the event log (via `spec-kitty agent tasks status`).
- **You must address all feedback** before your work is complete. Feedback items are your implementation TODO list.
- **Report progress**: As you address each feedback item, record what you changed with `spec-kitty agent tasks add-history <WPID> --note "..."` (stored in the status event log).

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Markdown Formatting

Wrap HTML/XML tags in backticks: `` `<div>` ``, `` `<script>` ``
Use language identifiers in code blocks: ````java`, ````bash`

---

## Objectives & Success Criteria

- **No artifact field mints an authored edge.** A structural test builds every `CharterArtifact`
  kind from a populated fixture and asserts that `derivedRelationships()` holds only
  `RelationType.derived()` types. Restoring any override turns it red.
- **The fields are gone from the pack schemas, domain, storage and contract**, as listed in
  data-model.md. A directive's `procedures` stays as prose and mints nothing.
- **The directive and paradigm registration bodies carry no `relationships`.** `putPackRelationship`
  is the only edge write. The dead write path is deleted.
- **The read shapes carry no `relationships`.** `listArtifactReferences` answers graph edges with
  their relation.
- **Unknown members are refused.** Every registration body (directive, paradigm, styleguide,
  toolguide, relationship) refuses one with 422 `validation_failed`, naming the member, and nothing
  is written. Each pack schema refuses each removed field.
- **The gates compare edges from graph documents only.** `./gradlew verifyAll` on Java 25 passes
  against the open-packs part-A commit. It passes first against the local draft, then against the
  merged pin.
- **The contract carries one `### Breaking` fragment.** There is no version bump and no tag.

## Context & Constraints

- **Read first**:
  - [plan.md](../plan.md), design decisions P1 to P6 (these are binding);
  - [data-model.md](../data-model.md);
  - [research.md](../research.md): R1 has the field table with file:line, R2 to R5 the consumers
    and gates, R7 the migration evidence and R8 the squad findings.
- **ArchUnit**: `application` depends on `domain` only (`HexagonalArchitectureTest`, rule
  `ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY`). The edge port lives in `domain`, and its adapter lives
  in `infra.storage` (`DomainRepositoryService`).
- **Contract rules**:
  - A fragment goes in `contracts/charter/changes/`, in the format given in `changes/README.md`. Do
    not edit `CHANGELOG.md` directly.
  - Every property keeps `x-source` or `x-derived` (lint rule `resource-property-cited`).
  - Orphaned component files must be deleted by hand, together with their `_index` entries.
- **Style gates**: palantir-java-format (Spotless), Checkstyle, Error Prone, NullAway and doclint.
  Run `./gradlew spotlessApply` before the check.
- **Local open-packs**:
  - The migrated checkout is `/tmp/claude-0/-home-user-spec-kitty-charter/eaaf1313-9aef-56a1-9b9a-09e87ee4ac55/scratchpad/op-mig`
    (a local commit). Run with `-PopenPacksDir=<that> -PopenPacksRef=$(git -C <that> rev-parse HEAD)`.
  - WP04 owns its patch. If WP04 regenerates the draft, re-run against the new commit.
- **Designed as if core did not exist**: no shim that keeps reading the old fields.

## Branch Strategy

- Planning base and merge target: `claude/240-drop-edge-fields`. Lanes come from `lanes.json`.
- Run: `spec-kitty agent action implement WP02 --agent <name> --mission graph-documents-sole-edge-source-01M6P9D0`.

## Subtasks & Detailed Guidance

Do the subtasks in order. Each one should leave the tree compiling, except T005 and T006, which
compile together with T007.

### Subtask T005 – Pack schemas

- **Files**: `contracts/packs/schemas/`:
  - `directive.schema.json`: remove `references` (:145) and the `DirectiveReference` `$def` (:25-66).
    Keep `procedures` (:116), but rewrite its description as prose steps that mint no edge.
  - `paradigm.schema.json`: remove `directive_refs` (:67), `references` (:75) and the
    `ParadigmReference` `$def`. Fix the top-level description (:47).
  - `tactic.schema.json`: remove `references` (:148), the step `references` (:82) and the
    `TacticReference` `$def`.
  - `procedure.schema.json`: remove `references` (:166) and its `$def` if orphaned.
  - `mission-type.schema.json`: remove `references` (:106) and the step `references` (:296).
  - `styleguide.schema.json`: remove `references` (:163).
  - `toolguide.schema.json`: remove `references` (:60).
  - `org-fragment.schema.json`: add `when` to `_OrgDRGEdge` with the same shape as `DRGEdge.when` in
    `graph-shard.schema.json`.
- Check whether `contracts/packs/README.md` or `file-kinds.yaml` need a line (probably not).
- **Tests (write first)**:
  - In `PackSchemasTest`, add a parameterized case for each (kind, removed field) pair, asserting the
    file is refused as an unknown key.
  - Add one case asserting that a fragment edge with `when` is accepted.
  - `VocabularyEqualityTest` must stay green.

### Subtask T006 – Domain

- **Kernel**: in `CharterArtifact`, rename `relationships()` to `derivedRelationships()`, with an
  empty default. The javadoc says these relations are derived from runtime data and never authored.
  Rewrite the "interim mapping" javadoc (:55-62). `writtenReferences()` stays, built from the derived
  hook.
- **packs domain**:
  - Remove the fields, the builder methods and the getters from `Directive` (`references`; keep
    `procedures`), `Paradigm` (`directiveRefs`, `references`), `Tactic` (`references`, plus its
    `writtenReferences` override), `TacticStep` (`references`), `Procedure` (`references`, plus its
    override), `Styleguide` and `Toolguide` (`references`).
  - Delete `ArtifactEdges`, `DirectiveReference`, `ParadigmReference`, `TacticReference` and
    `ProcedureReference`.
  - `Guard` overrides `derivedRelationships()` and keeps its existing derivation.
  - Delete `PackPolicy.referenceProblem` and its call site (`DomainRepositoryService:535`).
- **missions**: remove `references` and the step references from `MissionType`, delete
  `MissionReference`, and remove the `writtenReferences` override at :155. Make the same change in
  missions storage.
- **Tests**:
  - Add the structural test `NoArtifactMintsAnEdgeTest` (name it freely) in packs domain tests. It
    covers every kind in `ArtifactKind` that has an entity, each built with every field populated,
    and asserts that every derived relation is a `derived()` type.
  - Retire the pinned relation tests by rewriting them as negative assertions:
    `CharterArtifactFamilyTest.relationshipsFollowTheContract`,
    `DirectiveEntityTest.relationshipsFromProceduresAndReferences`, `ParadigmEntityTest`,
    `ArtifactRelationshipsTest` and `AggregateRetrofitTest` (:130, :158).
  - Delete `blankDirectiveRefRejected`.
  - `blankProcedureRejected` stays, because a blank prose step is still invalid.
  - `GuardAndLifecycleLoadTest.implementedByIsDerivedIntoTheGraph` uses the new name.
  - `AgentProfileEntityTest:93` uses the new name.

### Subtask T007 – Storage and the edge port

- Remove the fields from `DirectiveDso`, `DirectiveYaml`, `DirectiveBinding`, `ParadigmDso`,
  `ParadigmYaml`, `ParadigmBinding`, `TacticDso`, `TacticYaml`, `TacticBinding`, and from the
  procedure, styleguide, toolguide and mission-type bindings. Line numbers are in research R1.
- `GraphDocument` and its YAML must carry `when` on a fragment edge, not only on a shard edge. Check
  `GraphDocumentYaml` and `GraphShardWriter`.
- **Port**: add `PackArtifactRepository.edges(PackIdentifier)` in domain. It returns
  `List<PackEdge(source, target, relation)>` (choose the name) in document order: root shards in file
  order, then `drg/fragment.yaml`.
  - Implement it in `DomainRepositoryService`.
  - It refuses with `pack_unreadable` when a graph document cannot be read, as `compositionFacts`
    does.
- **Fixtures**: `roundtrip-fixtures/{procedure,procedure/edges-pack,mission-type,pack-a}`,
  `schema-fixtures/procedure` (9 files) and any other test resource carrying the fields. Find them
  with `grep -rln "references:\|directive_refs" service/*/src/test/resources`. Move a reference a
  test still needs into a fixture graph document.
- **Tests**: a storage test for `edges(pack)` covering order, a fragment `when`, and an unreadable
  document.

### Subtask T008 – Contract

- **Registration shapes**:
  - `schemas/DirectiveRegistration.yaml`: remove `references` and `relationships`. Rewrite the
    description (:3-6).
  - `ParadigmRegistration.yaml`: remove `directiveRefs`, `references` and `relationships`.
  - `StyleguideRegistration.yaml`: remove `references`.
  - `ToolguideRegistration.yaml`: remove `references`.
- **Read shapes**:
  - `Directive.yaml`, `Paradigm.yaml` and `Styleguide.yaml`: remove `relationships` and the reference
    fields, including their `required` entries.
  - `Tactic.yaml`, `TacticStep.yaml` and `Toolguide.yaml`: remove `references`.
- **Orphans**: delete `DirectiveReference.yaml`, `ParadigmReference.yaml` and `TacticReference.yaml`,
  and remove their index entries.
- **Wording**:
  - `RelationshipRegistration.yaml:5-7` and `RelationshipRegistered.yaml:5-6`: the shape is the
    body of `putPackRelationship`, the only edge write.
  - `ReferencedArtifact.yaml`: the description and the `reference` `x-source` say the row is an
    outgoing graph edge.
- **Paths**: the descriptions of `packs_packId_artifacts_{directive,paradigm}[_artifactId].yaml` (:8-9)
  and `packs_packId_artifacts_kind_artifactId_references.yaml` (:5-9).
- **Examples**: `Artifact.{directive,paradigm,styleguide,tactic,toolguide}.yaml` and
  `ArtifactReferenceList.cross-pack.yaml`. The second example now shows graph edges.
- **Fragment**: `changes/2026-11-05-edges-only-in-graph-documents.md`, with one `### Breaking` block.
  It lists the removed members per shape and the unknown-member refusal, says the change is breaking
  on the unreleased 0.x line as ADR-0022 allows, and gives core's seam as "none until incorporation".
  Also add a note to `changes/2026-11-03-pack-composition-refusals-and-dependency-facts.md:7`, which
  names `procedures` and `directive_refs`.
- **Validation**: `./gradlew :contracts:check`, which runs the lint, bundle, closed-response,
  compatibility and fragment checks.

### Subtask T009 – API and application

- **Controllers**:
  - `DirectiveController` and `ParadigmController`: stop reading `references`, `directiveRefs` and
    `relationships`. Remove the `relationships` argument from `registerDirective` and
    `registerParadigm`.
  - Styleguide and toolguide controllers: stop reading `references`.
- **`ArtifactDtos`**: no `relationships` (:173, :208, :250) and no reference fields.
- **Dead path**: delete `RelationshipMerge.ownBodyRelationships` and `mergeAll` (keep `upsert`),
  `RelationshipWire.fromWire(List)` (keep `fromWire` for one relationship, if the relationship
  controller uses it, and `toWire`), and the 3-argument `PackWriteStore.Change.artifact`.
- **`ArtifactReadService.resolved`** (:379-396): build the rows from `repository.edges(pack)`.
  - Keep each edge whose source is the artifact's URN, written bare or self-qualified (use the
    `PackPolicy.joinsItself` test).
  - The `Reference` takes the edge's target as a `PackQualifiedReference`, its relation, and
    `composed.holder(…)`.
  - Then append the derived relations.
  - Keep the consistent-read fingerprinting as it is.
- **Tests**: update `DirectiveControllerTest`, `ParadigmControllerTest`, `RegistrationListsTest`,
  `HypermediaTest`, the registration service tests and the read service tests listed in research R1
  §8. Add a read-service test proving that an edge registered through `putPackRelationship` appears
  in the references read with its relation.

### Subtask T010 – Unknown-member refusal

- **`RegistrationLists`**: add `members(Class<?> dto)`, which returns every `@JsonProperty` name of the
  DTO, and a top-level scan that returns the first member not in that set.
- **`RegistrationBodyAdvice`** and **`ToolguideRegistrationBodyAdvice`**: refuse an unknown member
  with `CharterRefusal(VALIDATION_FAILED, "<kind> registration: unknown member '<name>'")`, mapped
  to 422.
  - Run the check before the null-list check.
  - Every guarded registration gets the check, the relationship registration included.
  - Declare the paradigm guard with no lists, as the relationship guard is
    (`new Guarded("paradigm", List.of())`), so the context starts. Better still, make
    `Guarded(kind, dto)` tolerate a DTO without lists, and keep the `IllegalStateException` only where
    a list is expected.
- **Javadoc**: update the `RegistrationLists` javadoc line about Jackson features.
- **Tests**:
  - A parameterized advice test, with one case per (body, removed member) pair: directive
    `references`/`relationships`; paradigm `directiveRefs`/`references`/`relationships`; styleguide
    `references`; toolguide `references`.
  - Plus one case of an arbitrary unknown member on the relationship body.
  - Each case asserts 422, the member named, and no write (assert on the write store or the commit
    count).

### Subtask T011 – Scenarios and BDD

- **Rewrite**:
  - directives.feature: :3, :23-33, :50, :69-72, :82-85, :156-170.
  - paradigms.feature: :3, :8-9, :25-28, :39-47, :65, :84-87, :97-100, :130-144.
  - styleguides.feature: :8, :24, :35, :51, :116-117.
  - toolguides.feature: :26.
  - artifacts.feature: :84, :103, :122, :141, :160.
  - journeys/journey-06-publish-a-pack.feature: :21-24, :32-35. The journey now registers the
    relationship through `putPackRelationship`.
  - composition.feature: :162, :1141, :1198, :1265, :1297, :1348. Move each reference from artifact
    YAML into `drg/fragment.yaml` edges in the scenario's fixture.
- **Delete**: scenarios that exist only to test `relationships` in a body ("unknown relationship
  endpoint kind", "may assert only its own edges"). Their rule lives on in relationships.feature;
  check that it covers both.
- **Add**: a Scenario Outline in directives.feature or a new `registration-bodies.feature`. For each
  body and removed member it expects 422 `validation_failed`, the member named, and the pack revision
  unchanged.
- **BDD steps**: update `RegistrationSteps.java:96-120` ("has <n> relationships", "points to <urn>"),
  then `DoctrineWiringRoundTripTest.java:133` and the `ProfileArtifact` fixture at :44. Update
  `ContractConformanceTest:94-96`, whose fixture carries tactic `references`.
- **README**: `scenarios/README.md:113` (the step list).

### Subtask T012 – Corpus tests on the migrated checkout

- Run `:packs:openPacksRoundTrip` and `:packs:wholePackRoundTrip` against the draft checkout.
- Retarget `OpenPacksComposeTest.aBareTypedReferenceIsRefused`. It now edits the fragment edge
  `directive:EVD_006 → built-in::tactic:anti-corruption-layer` in `event-driven/drg/fragment.yaml`,
  and the refusal is still `PACK_REFERENCE_UNQUALIFIED`. Check every other test there that edits a
  field.
- Re-pin each count that moves, such as the manifest-constituent count or the procedure counts, and
  write a one-line justification for each in the commit message.
- Delete `DirectiveWalkSliceTest` and `ParadigmWalkSliceTest`. Keep `GraphWalkTest` if it builds its
  own components, and adjust it otherwise. `GraphWalk` uses `derivedRelationships()`.
- `WholePackOpenPacksRoundTripTest.meaning()` compares the DSOs (which no longer hold edges) and
  `graphs()`. Confirm with a short comment there.

### Subtask T013 – Pin move and verifyAll

- **Blocked** until the parent session names the merged open-packs commit for part A.
- Then set `openPacksRef` in `gradle.properties`, update
  `service/packs/src/test/resources/whole-pack/open-packs.lock.yaml` if it records the ref, and run
  `syncPacks` if `packs/internal` and `packs/qa` are synced from the pin.
- Run `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon -PopenPacksDir=../spec-kitty-open-packs verifyAll`
  with the sibling checkout at the new pin. The run must be green.

## Test Strategy

Write the tests first. The non-fakeable pins are the structural no-minting test (T006), the
parameterized pack-schema refusals (T005), the parameterized body refusals (T010 and T011) and the
references-read-from-graph test (T009). The BDD `ContractConformance` validates every response
against the closed schemas, so a leftover `relationships` fails the envelope scenarios.

## Risks & Mitigations

- **A large diff**: commit per subtask with a clear message, so the reviewer can follow it surface by
  surface.
- **The paradigm guard startup failure**: T010 handles it explicitly, and `DeployableContextTest` (or
  any `@SpringBootTest`) proves the context starts.
- **Counts that move**: each one is justified (T012).
- **Parallel sessions**: A (ci.yml), C (WorktreeGuard and deployable config) and D (the client
  module). Do not touch `.github/workflows`, `WorktreeGuard` or `deployable/src/main`. Rebase on
  `main` before review.

## Review Guidance

- `grep -rn "directiveRefs\|directive_refs\|ArtifactEdges\|ownBodyRelationships" service contracts`
  finds nothing.
- `derivedRelationships()` is overridden only by `Guard`.
- The references read uses the port. `application` does not import `infra`.
- The contract fragment's format is valid, and no version changes.

## Activity Log

- 2026-11-05T17:40:00Z – system – Prompt created.
