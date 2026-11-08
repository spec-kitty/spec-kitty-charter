# Implementation Plan: Graph documents are the sole source of edges

**Branch**: `claude/240-drop-edge-fields` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/graph-documents-sole-edge-source-01M6P9D0/spec.md`

Design input: the grounding squad (architect, service-code, contract and pack-content lenses), in
[research.md](research.md). The three operator questions are deferred Decision Moments (spec D1 to
D3). The plan builds on the recorded recommendations and keeps each one reversible.

- **D1** reverses by restoring the `procedures` refusal.
- **D2** reverses by restoring a field's schema entry and binding.
- **D3** reverses by restoring the read-shape array.

## Summary

An edge has one home, the pack's graph documents, and one write operation, `putPackRelationship`.
This plan does five things:

- It removes every artifact-to-artifact reference field from the domain records, the YAML bindings
  and DSOs, the pack schemas and the contract shapes.
- It removes the `relationships` arrays from the directive and paradigm registration bodies and from
  the read shapes.
- It retires the interim mapping (`ArtifactEdges`), so that no artifact derives an authored edge from
  a field.
- It makes the round-trip gates compare edges through the graph documents only.
- It keeps a directive's `procedures` as prose that mints nothing.

The artifact-references read now reports an artifact's outgoing edges from the pack's graph documents,
each with its relation. A request body that carries an unknown member, a removed field among them, is
refused, so a client that still sends `relationships` cannot get a silent success. The open-packs migration (drafted here) lands first and this change moves `openPacksRef` to it (P5); an org
fragment's edge gains `when`, so a fragment edge can hold what the dropped fields held.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain; `-Pcharter.javaVersion=21` only for a local run without a 25 JDK)
**Primary Dependencies**: none added; Spring Boot / Jackson 3 (existing), JUnit 5, AssertJ, Cucumber (existing)
**Storage**: pack files on disk (YAML) and the commit store; graph documents `*.graph.yaml` and `drg/fragment.yaml`
**Testing**: JUnit 5 per module; the contract BDD scenarios (`contracts/charter/scenarios/`) through `deployable`; the tagged corpus gates `:packs:wholePackRoundTrip` and the open-packs round trip at `openPacksRef`
**Target Platform**: the charter service (Linux container) and its OpenAPI contract
**Project Type**: single (Gradle multi-module service: `kernel`, `packs`, `missions`, `profiles`, `charter-api`, `deployable`; plus `contracts/`)
**Performance Goals**: no measurable change; the references read walks a pack's graph edges once per call
**Constraints**: C-001 to C-005 of the spec; Error Prone, NullAway, Checkstyle and palantir-java-format pass; contract lint passes
**Scale/Scope**: about 8 domain records, 8 DSO/Yaml/Binding sets, 8 pack schemas, ~20 contract files, ~6 feature files, ~30 test classes, 10 ADRs touched

Supply chain (DIRECTIVE_051): no dependency is added, upgraded or removed, so no control applies, and
the verification metadata is unchanged.

## Charter Check

| Gate | Result |
|------|--------|
| Non-trivial work runs as a mission and lands by PR | Pass: this mission, draft PR to `main` |
| Adversarial squad at the plan point and before merge (contract, architecture, domain change) | Planned: a grounding squad ran (research.md); a plan-point squad reviews this plan; a pre-PR squad is planned |
| `./gradlew verifyAll` on Java 25 with strict dependency verification | Planned: no dependency change |
| Java style check | Planned: part of `verifyAll` |
| Docs checks (`check_docs.py`, markdownlint, cspell, frontmatter ≤180) | Planned |
| Contract versioning (ADR-0022) | Pass: `### Breaking` fragment on unreleased 0.2.0-SNAPSHOT, no bump, no tag |
| Designed as if core did not exist | Pass: no compatibility shim; core's seam is noted in the fragment as "none until incorporation" |
| A call made without review is logged | D1 to D3 are recorded as deferred decisions and asked on #240; FR-013 (Q4) is an assumption asked on #240 |

## Design decisions

The plan-point squad (architect, reviewer, reducer) changed P1 and P3 to P6. Its dispositions are in
[research.md](research.md), section R8.

- **P1. The spine keeps a derived-relations hook only, under a name that says so.**
  `CharterArtifact.relationships()` becomes `derivedRelationships()`, with an empty default. Its
  javadoc reads "relations derived from runtime data, never authored". A guard's `IMPLEMENTED_BY` to
  an in-pack asset is the only override. It is derived (`RelationType.derived()`), refused on write,
  and never compared by the round trip. The directive, paradigm and tactic overrides, `ArtifactEdges`
  and the domain reference types (`DirectiveReference`, `ParadigmReference`, `TacticReference`,
  `ProcedureReference`, `MissionReference`) are deleted. A structural test covers every
  `CharterArtifact` kind, each built from a populated fixture, and asserts that its derived relations
  are all `derived()` types. This keeps SC-002 from being met merely by deleting tests.
- **P2. `writtenReferences()` reduces to the derived hook, and `PackPolicy.referenceProblem` is
  retired.** The tactic, procedure and mission-type overrides go with their fields. A guard's asset
  reference is never malformed or a self-reference, so `referenceProblem` always returns empty and is
  deleted together with its call site. `compositionFacts` already takes both endpoints of every
  graph-document edge, and `OfferingReadService.composed()` checks exactly that list, so the
  cross-pack checks keep their coverage. The composition scenarios that wrote references in artifact
  YAML move those references into `drg/fragment.yaml`.
- **P3. The references read is built from graph edges through a domain port.** A new port,
  `PackArtifactRepository.edges(pack)`, returns the pack's graph-document edges
  `(source, target, relation)` in document order: root shards (the registered shard among them) in
  file order, then `drg/fragment.yaml`. The adapter lives in `DomainRepositoryService`, which keeps
  `application` free of `infra` (`ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY`).
  - `listArtifactReferences` answers the edges whose source is the artifact. A self-qualified source
    (`<thispack>::kind:id`) counts, by the `PackPolicy.joinsItself` test. The edge's relation and its
    composed holder are reported, followed by the derived relations.
  - Edges that another pack authors from this artifact are not listed. The read answers what this
    pack authors, as it does today.
  - `ReferencedArtifact` keeps its shape. Its `reference` `x-source` and its description change.
- **P4. A registration body is refused for an unknown member, with 422 `validation_failed`, naming
  the member.** The check sits in the existing body guards (`RegistrationBodyAdvice`,
  `ToolguideRegistrationBodyAdvice`, `RegistrationLists`). Those guards already read every
  registration body before conversion, so one more check compares the top-level members against the
  DTO's `@JsonProperty` names. Every body that writes is covered: directive, paradigm, styleguide,
  toolguide and relationship. There is no global Jackson change and no `deployable` config change, so
  nothing collides with the parallel deployable-config session. The paradigm guard is declared with
  no lists, as the relationship guard is, so the context still starts.
  - Pins: a parameterized scenario sends each removed member to each registration body and expects
    422 with nothing written.
  - The same applies to a standalone advice test.
- **P5. The open-packs migration lands first, and this PR moves `openPacksRef`.** A known-failure list
  cannot carry this change. Every pinned pack carries a removed field, a pack with any violation skips
  its round trip (so the gate goes vacuous and red), and several `verifyAll` corpus tests never
  consult the list (`PackDoctrineCorpusRoundTripTest`, `ProcedureAndGuideCorpusTest`,
  `MissionTypeCorpusTest`, `OpenPacksComposeTest`, `FileSystemPackInspectorTest`,
  `PackManifestParityTest`).
  - The migration only removes optional fields and adds graph edges, so it is forward-compatible with
    today's schemas. The evidence is in research.md R7.
  - The parent session merges it into open-packs. This PR then moves the pin together with the schema
    change.
  - Until the parent session names the new commit, the branch builds against a local checkout of the
    draft. CI stays red only on the pin, and the PR stays in draft. No `#TBD-` key is added to the
    gate.
- **P6. GraphWalk is deferred, not rewritten.** Only tests construct `GraphWalk`. Once the mapping is
  retired it would walk guard edges only. Retiring it, along with `ComposeResolver`, the kernel's
  `GraphWalker` and `Descent`, is an amendment to ADR-0025's walk decision and belongs in its own
  change. The tests that pin the retired mapping (`DirectiveWalkSliceTest`, `ParadigmWalkSliceTest`)
  are deleted. `GraphWalkTest` is kept wherever it builds its own components. A follow-up issue is
  drafted for the parent session to file.

## Project Structure

### Documentation (this mission)

```
kitty-specs/graph-documents-sole-edge-source-01M6P9D0/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── research/open-packs-migration.md   # the draft open-packs change (FR-012)
├── research/open-packs-issue-draft.md # the issue the parent files
└── checklists/requirements.md
```

### Source Code (repository root)

```
docs/adr/00NN-*.md (new) + amended 0024 0025 0026 0027 0036 0037 0043 0044 0050 0052
contracts/charter/{schemas,paths,examples,scenarios,changes}/
contracts/packs/schemas/{directive,paradigm,tactic,procedure,mission-type,styleguide,toolguide}.schema.json
service/kernel/.../CharterArtifact.java
service/packs/.../domain/{Directive,Paradigm,Tactic,TacticStep,Procedure,Styleguide,Toolguide,ArtifactEdges,PackPolicy}.java
service/packs/.../infra/storage/*{Dso,Yaml,Binding}.java, DomainRepositoryService.java
service/packs/.../infra/api/{Directive,Paradigm,Styleguide,Toolguide}Controller.java, ArtifactDtos.java, RelationshipWire.java
service/packs/.../application/{ArtifactReadService,DirectiveRegistrationService,ParadigmRegistrationService,RelationshipMerge}.java
service/missions/.../MissionType.java (+ storage)
service/deployable/src/main/resources/application.yaml, ProblemHandler/RefusalHandler
gradle.properties (openPacksRef), service/packs/src/test/resources/whole-pack/open-packs.lock.yaml
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Unknown-member refusal on every registration body (P4) | Without it a client sending the removed `relationships` gets a silent success and no edge, the exact two-paths failure the ruling removes | Spring/Jackson-wide fail-on-unknown touches deployable config owned by a parallel session and unrelated bodies; the existing body advice already reads every registration body |
| A new domain port for graph edges (P3) | The application layer may not read infra graph documents (ArchUnit onion rule), and no port exposes edges with relation and direction | `registeredRelationships` covers the registered shard only; `compositionFacts` has no relation or direction |

## Implementation Concern Map

The concerns are split by surface, so no two of them edit the same file.

### IC-01 — Decision record

- **Purpose**: one new ADR (numbered at writing; 0060 while PR #376 holds 0059) records the
  per-field decision, the single write path and the classification of the guard's derived relation.
  - It amends ADR-0024, 0025 (`relationships()` becomes `derivedRelationships()`), 0026 (body
    relationships), 0027, 0036 decision 4 and 0053 decision 22 (step enhancement `references`).
  - It repoints the #189 and #167 edge-authority citations in ADR-0024, 0025, 0027, 0036, 0037, 0043,
    0044, 0050 and 0052 decision 9.
  - It cross-references ADR-0059 D2 and open question 4 (PR #376): the cascade's reach now follows
    graph edges only.
  - It says why agent-profile references are selection data and not edges (FR-013).
- **Relevant requirements**: FR-001, FR-002, FR-013
- **Affected surfaces**: `docs/adr/`
- **Sequencing/depends-on**: none
- **Risks**: an ADR-number collision with open PRs. Re-check `main` and the open PRs at writing time.

### IC-02 — Domain, storage and pack schemas

- **Purpose**: remove the reference fields from the domain records, bindings, DSOs and pack schemas.
  - Keep `procedures` as prose.
  - Retire `ArtifactEdges`, the reference types and `PackPolicy.referenceProblem`.
  - Introduce `derivedRelationships()` and the `edges(pack)` port with its adapter.
  - Add the structural no-minting test.
  - Update the fixtures that carry the fields.
- **Relevant requirements**: FR-003 to FR-006, FR-009, FR-010 (pack files), FR-011, FR-013
- **Affected surfaces**:
  - `service/kernel` (`CharterArtifact`)
  - `service/packs/.../domain`
  - `service/packs/.../infra/storage` (`*Dso`, `*Yaml`, `*Binding`, `DomainRepositoryService`)
  - `service/missions` (`MissionType`, `MissionReference`, storage)
  - `contracts/packs/schemas`, including their `$defs`
  - test fixtures (`roundtrip-fixtures/{procedure,procedure/edges-pack,mission-type,pack-a}`,
    `schema-fixtures/procedure`, and the `ContractConformanceTest` tactic fixture)
  - `PackSchemasTest`, with one refusal case per removed field
  - the corpus tests named in P5
- **Sequencing/depends-on**: none
- **Risks**:
  - Pinned counts in the corpus tests, such as the manifest-constituent counts, may move with the
    migrated content. Each moved count is re-pinned against the migrated checkout and justified.

### IC-03 — Contract, wire and reads

- **Purpose**: remove the fields and `relationships` arrays from the registration and read shapes,
  examples and path descriptions, and delete the orphaned item schemas (and their index entries).
  - Rebuild the references read on the edge port.
  - Refuse unknown registration members in the body advice.
  - Delete the dead write path: `RelationshipMerge.ownBodyRelationships` and `mergeAll`,
    `RelationshipWire.fromWire(List)`, and the 3-argument `PackWriteStore.Change.artifact`.
  - Write the `### Breaking` fragment, and add a note to the unreleased 2026-11-03 fragment.
  - Update the scenarios and BDD steps.
- **Relevant requirements**: FR-007, FR-008, FR-010 (bodies), FR-014, NFR-003
- **Affected surfaces**:
  - `contracts/charter/`
  - `service/packs/.../infra/api`: the controllers, `ArtifactDtos`, `RegistrationBodyAdvice`,
    `ToolguideRegistrationBodyAdvice` and `RegistrationLists`
  - `service/packs/.../application`: `ArtifactReadService` and the registration services
  - `service/deployable/src/test` (BDD steps)
- **Sequencing/depends-on**: IC-02
- **Risks**:
  - The standalone MockMvc tests use their own converter, so the refusal is pinned in the advice
    itself and in the `@SpringBootTest` BDD run.

### IC-04 — open-packs migration and the pin

- **Purpose**: write the exact open-packs change as a patch and a description: the field removals,
  the graph edges to add, the `when` merges, the open-packs validator, its tests and docs, and the
  listed citations that are dropped.
  - Prove it is forward-compatible against `main`.
  - Draft the open-packs issue and the GraphWalk follow-up issue.
  - Move `openPacksRef` once the parent session names the merged commit.
- **Relevant requirements**: FR-011, FR-012
- **Affected surfaces**: `kitty-specs/<mission>/research/`, `gradle.properties`, `service/packs/src/test/resources/whole-pack/open-packs.lock.yaml`
- **Sequencing/depends-on**: IC-02, for the schema edits that the patch must satisfy
- **Risks**:
  - The open-packs validator pins the charter schemas at a charter commit. The draft therefore
    removes its readers of the dropped fields, and the validator's pin moves after this PR merges.

### IC-05 — Living documentation

- **Purpose**: write the docs changelog entry and update the pages that describe the removed fields
  or the body `relationships` arrays:
  - `docs/guides/compose-packs.md`
  - `docs/architecture/logical-model.md`
  - `docs/architecture/descriptors.md`
  - `docs/context/conceptual-model.md`
  - `docs/context/user-journeys/06-publish-a-pack.md` and `09-upgrade-packs.md`
  - `contracts/charter/scenarios/README.md`

  Historical plans (`docs/development/epic-186-*`) stay as they are.
- **Relevant requirements**: NFR-002
- **Affected surfaces**: `docs/`
- **Sequencing/depends-on**: IC-01 to IC-03
- **Risks**: the generated indexes are never edited by hand (ADR-0047).
