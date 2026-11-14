# Implementation Plan: R12: glossary contexts and terms

**Branch**: `kitty/mission-glossary-contexts-and-terms-01M7B1PP` → `feat/106-glossary-contexts` → `main` (PR) | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/glossary-contexts-and-terms-01M7B1PP/spec.md`

## Summary

This Mission replaces the kind `glossary_pack` outright with ADR-0049's glossary contexts and terms, and serves them:

- **The model**:
  - the kind `glossary`, with node kinds `glossary_context` and `term`;
  - the closed context and term shapes and the layout `glossaries/<id>.glossary.yaml`;
  - letter-first slug ids.

  It reaches both contracts, the kernel, the packs domain and storage, the activation document, presets, example packs
  and fixtures. No reader of the old shape remains.
- **The ADR-0074 reads**, in a pack and as activation twins: the context list (and `?kind=glossary`), the context item
  and the term.
- **The activation's vocabulary**, shaped by a new ADR-0076: the terms in force, the Contextive projection
  (`application/yaml`) and the taboo list.

The reads are built as slice A2 built `skill`: a contract path per operation, the `:artifacts-edge` controllers, the
packs-side `ForeignArtifactRead`, and `EffectiveReadService` for the activation tree. Each operation passes the
switch-gate register with `@contract` scenarios.

Implementation was gated on slice C (#467), which merged as PR #566. The operator said GO on 2026-11-13. The mission
branch has `main` `d71e79bb` merged in.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain); OpenAPI 3.1 contract in `contracts/charter/`; JSON Schema 2020-12
pack schemas in `contracts/packs/`.
**Primary Dependencies**: these are the existing ones: Spring Boot web, the generated `ai.speckitty.charter.api`
interfaces (`useTags`), and `snakeyaml-engine`. `snakeyaml-engine` is already in the version catalog and the
verification metadata, and `:artifacts-edge` now declares it to render the Contextive YAML. Cucumber runs the contract
scenarios. No new artifact enters the build, so DIRECTIVE_051's controls hold on what is already verified.
**Storage**: pack files on disk through the existing kind bindings, and activation documents in the existing activation
store. Nothing new.
**Testing**:
- unit tests per module (`:kernel`, `:packs`, `:activations`, `:artifacts-edge`);
- contract scenarios run by `:deployable:test` (`CharterContractSuite`, `SwitchGate`, `LinkChecks`);
- the contract checks;
- the whole-pack round trip over charter's example packs;
- `./gradlew verifyAll --max-workers=1`, in the foreground, one run at a time.
**Target Platform**: the charter service (a Linux container), and contract consumers through the generated client.
**Project Type**: a multi-module service (`service/*`) plus the contract trees.
**Performance Goals**: each read verifies one `ConsistentRead` or `PacksSession` per request. A vocabulary read opens
one activation session and reads each winning pack at most once, against the closure fingerprint (NFR-005, research
R6). The 139-term example vocabulary is the reference load.
**Constraints**:
- the charter's canonical-model rule (C-001);
- ADR-0074 decisions 9 to 14;
- ADR-0049 as amended by ADR-0053;
- 0.x breaking changes with `### Breaking` entries (C-003);
- signed commits; no force-push (C-006).
**Scale/Scope**:
- 9 new operations (3 pack, 3 twin, 3 vocabulary);
- about 10 new contract schemas;
- 1 renamed pack schema;
- 7 contract enums and objects renamed;
- about 60 files touched by the rename.

## Charter Check

| Gate | How this plan meets it |
| --- | --- |
| Divergence from the charter's own model | The shape is ADR-0049's, the charter's own model. No core shape is kept. |
| Breaking the contract | The breaks are deliberate, on the 0.x line: the kind token, the node kinds, the selection members, the preset key and the pack schema. Each is listed under `### Breaking` in the charter CHANGELOG, and in the packs CHANGELOG once #559 adds it. The new reads are `### Added`. |
| Canonical model, no legacy | No reader, alias or upgrade of `glossary_pack`, in a pack, a preset or a stored activation (research R2). |
| Tests use only canonical shapes | Old-shape content appears only in refusal tests: one planted pack, one stored document and one write body. |
| Round trip on charter's example packs | The moved fixtures stay in the whole-pack round trip, and the proof checks that the new file kind is modelled, not skipped (#547). |
| Adversarial squad at plan and before merge | The plan-point squad runs on this plan and ADR-0076 (`research/squad-post-plan.md`). The pre-merge squad runs on the aggregate diff. |
| ADR discipline | ADR-0076 is written now as Proposed, and the operator accepts it before tasks. ADR-0049 and ADR-0074 get dated annotations, not rewrites. |
| Issue rules | `Closes #106`, `Closes #536`, and `Refs` #94 and #142. The open-packs content is a draft issue in research. No squad minor is filed. |
| Gates before merge | `verifyAll` on Java 25, the docs checks, and the switch-gate register. |

Result: pass, with no violations to justify.

## Project Structure

### Documentation (this mission)

```
kitty-specs/glossary-contexts-and-terms-01M7B1PP/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md, occurrence_map.yaml
├── contracts/glossary-reads.openapi.yaml   # sketch; contracts/charter is the source of record
├── decisions/                              # Decision Moments of specify and plan
└── research/                               # squad reports, term-id map, open-packs draft issue
docs/adr/0076-the-activation-vocabulary-reads-and-projections.md   # Proposed with this plan
```

### Source Code (repository root)

```
contracts/packs/
├── schemas/glossary.schema.json (replaces glossary-pack.schema.json); graph-shard, org-fragment, pack-manifest,
│   preset, charter, org-charter, policy, mission-step-contract schemas (token and plural)
└── file-kinds.yaml (node, artifact and fragment kinds; **/*.glossary.yaml rule), README.md
contracts/charter/
├── openapi.yaml (9 paths; tags Glossaries, EffectiveGlossaries, Vocabulary)
├── paths/packs_packId_artifacts_glossary{,_contextId,_contextId_terms_termId}.yaml
│   activations_activationId_artifacts_glossary{,_contextId,_contextId_terms_termId}.yaml
│   activations_activationId_vocabulary_{terms,contextive,avoid}.yaml
├── parameters/ContextId.yaml, TermId.yaml, ArtifactKind.yaml (prose)
├── schemas/NodeKind, ActivatableKind, CascadeKind, SelectedArtifacts, DeactivatedArtifacts, Preset, ArtifactKindToken,
│   Artifact; new GlossaryContext*, GlossaryTerm, EffectiveGlossary*Read, ActiveTerm*, TabooList, Activation (links)
├── examples/, scenarios/glossary.feature, effective-glossary.feature, vocabulary.feature, every-operation.feature,
│   read-refusals.feature, activation-links.feature, effective-reads.feature (400 detail), switch-gate.yaml
└── changes/2026-11-1x-glossary-contexts-and-terms.md
contracts/offering/schemas/ActivationConfig.schema.json + corpus valid/06, valid/07 (+ an invalid case)
service/kernel/…/domain/ArtifactKind.java, NodeKind.java, Relation.java (+ tests)
service/packs/…/domain/GlossaryContext, GlossaryTerm, GlossaryContextIdentifier, TermIdentifier, TermStatus, AppliesTo
    (replace GlossaryPack*); infra/storage/GlossaryBinding, GlossaryDso, GlossaryYaml, KindBindings, YamlFormats;
    infra/api/PresetMapper; application/ArtifactReadService (SERVED, LITERAL_ITEM_KINDS, ABSENT), ForeignArtifactRead
service/activations/…/application/EffectiveReadService (route, held winners), domain/EffectiveTag (Read values, guards),
    infra/api/ActivationMapper (links)
service/artifacts-edge/…/ Glossary*, EffectiveGlossary*, Vocabulary* controllers, reads, bodies, adapters
service/packs/src/testFixtures/resources/example-packs/built-in/…, src/test/resources/{digest-baseline,pack-level,planted}
docs/api/service-contract.md, docs/conformance/divergence-register.md + divergences.yaml, docs/plans/*, docs/adr/0049, 0074
```

**Structure Decision**:

- The domain and storage stay in `:packs`.
- Every new read lives in `:artifacts-edge`, beside the skill and asset reads (research R8).
- The projections are pure functions in `artifacts-edge.application` over the list of active terms.

No new module, and no new ArchUnit rule.

## Complexity Tracking

None. No charter gate is violated.

## Implementation Concern Map

> These concerns are not work packages. `/spec-kitty.tasks` turns them into work packages. The plan-point squad
> (`research/squad-post-plan.md`) reshaped this map. The kind rename is now one concern, because its parts do not
> compile apart.

### IC-01: ADR-0076 and the record annotations

- **Purpose**: decide the vocabulary reads before they are built, and annotate:
  - ADR-0049 decisions 7, 10, 13 (the warning retired) and 15;
  - ADR-0074 decision 3.
- **Relevant requirements**: FR-018, FR-015, FR-021.
- **Affected surfaces**: `docs/adr/0076-…md` (Proposed, then Accepted by the operator), `docs/adr/0049-…md`,
  `docs/adr/0074-…md`.
- **Sequencing/depends-on**: none. It is accepted before tasks.
- **Risks**: an ADR number collision with slice F (announced through the orchestrator).

### IC-02: The kind rename, atomic

- **Purpose**: replace `glossary_pack` in one change that compiles and passes its parity tests.
- **What changes**:
  - **Kernel** (research R1): `ArtifactKind.GLOSSARY`; the explicit `NodeKind` table with token, artifact kind,
    plural, node-declarable and edge-target flags; `GLOSSARY_CONTEXT` and `TERM`; the `Relation.VOCABULARY` targets.
  - **Packs contract**: `glossary.schema.json` replaces `glossary-pack.schema.json`, with the id patterns pinned.
    Every packs schema and `file-kinds.yaml` follows (`glossaries: glossary_context`, `**/*.glossary.yaml`).
  - **Domain and storage**: `GlossaryContext`, `GlossaryTerm`, `GlossaryContextIdentifier`, `TermIdentifier`,
    `TermStatus`, `AppliesTo`; the binding, DSO and YAML codec; presets (`activated_glossaries`). The `GlossaryPack*`
    classes are deleted, and old-shape content is refused at load.
  - **Charter and offering contracts**: `NodeKind`, `ActivatableKind`, `CascadeKind`, `SelectedArtifacts`,
    `DeactivatedArtifacts`, `Preset`, `ArtifactKindToken`, the `Artifact` `oneOf`, `ActivationConfig` and its corpus,
    the `activations_activationId_presets_presetId.yaml` prose, and the 400 detail in `effective-reads.feature`.
  - **URN-token mappings** (research R1): `EffectiveProfileDtos`, `ProfileDtos`, `EffectiveBodies` and the profile
    `linksArtifact` paths map a URN's kind through `NodeKind.artifactKind()`.
  - **This repository's charter file** (R14): `ProjectCharterYamlSchemaTest` stops validating
    `.kittify/charter/charter.yaml`, and its `charter.schema.json` coverage moves to a canonical example.
  - **`scripts/usability/charter-to-activation.py`**: the key mapping follows.
- **Relevant requirements**: FR-001 to FR-006, FR-008 (token), NFR-002.
- **Affected surfaces**:
  - `service/kernel/**`, `contracts/packs/**`, `contracts/charter/schemas/**`, `contracts/offering/**`;
  - `service/packs/…/domain`, `…/infra/storage`, `…/infra/api/PresetMapper`;
  - `service/profiles-edge/…/infra/api`, `service/artifacts-edge/…/EffectiveBodies`.
- **Tests that move**:
  - `IdentifierBoundsTest` (two rows), `WireTokenParityTest` (its "unknown kind" example moves off `glossary`),
    `ActivationWireTokenParityTest`, `VocabularyEqualityTest`, `PackFileKindTest`, the schema tests;
  - `EffectiveProfileReadTest`, `PacksProfileSourceTest`, `ActivationPresetServiceTest`, `PresetRulesTest`;
  - the `ActivationFileCodec` refusal test (the codec already refuses an unknown token; only the test is new).
- **Sequencing/depends-on**: IC-01 (accepted).
- **Risks**:
  - It is a large work package, but its parts do not compile apart (squad, Java lens H4).
  - It shares ground with slice F's taxonomy (#537) and with #559's `file-kinds.yaml` (PR #568). Rebase on whichever
    lands first.

### IC-03: Fixtures, example packs and scenario packs

- **Purpose**: move every glossary fixture to the new shape (research R4 and R10), and build the scenario packs the
  stories need. Re-record the pinned digests and file sets, each justified in the commit.
- **What is built**:
  - **Moved**: `built-in`, `digest-baseline`, `pack-level/presets-pack`.
  - **`research/term-id-map.md`**: 139 ids.
  - **Planted cases**:
    - an old-shape pack;
    - a bad context id, and a bad term id (`Core`, `a--b`, `4-eyes`);
    - a duplicate term id;
    - a `see_also` that is not a URN.
  - **Scenario packs** for `glossary.feature`, `effective-glossary.feature` and `vocabulary.feature` (squad, analyst
    lens H3), all in the canonical shape:
    - a pack with contexts `spec-kitty-core` and `qa`;
    - a project pack whose `spec-kitty-core` copy drops `lane`;
    - a second context that shares a term name;
    - terms with `avoid` entries, one word shared by two terms.
- **Relevant requirements**: FR-020, FR-003, FR-005, SC-001.
- **Affected surfaces**: `service/packs/src/testFixtures/resources/example-packs/**`, `src/test/resources/**`, the
  contract scenario fixtures under `service/deployable/src/test/resources/**`.
- **Sequencing/depends-on**: IC-02.
- **Risks**: a digest re-record can hide a real change, so each one is named in its commit. The whole-pack round trip
  must model `glossaries/*.glossary.yaml`, not skip it (#547).

### IC-04: Glossary reads in a pack

- **Purpose**: `listPackGlossaryContexts`, `getPackGlossaryContext` and `getPackGlossaryTerm`, and `?kind=glossary` in
  the pack list.
  - Refusals come in the order pack, then context, then term, with segment-naming details (research R3).
  - The context item links `term` as a templated relation.
- **Relevant requirements**: FR-007, FR-008, FR-009, FR-010, FR-012, FR-019, SC-001.
- **Affected surfaces**:
  - the contract paths, `parameters/ContextId.yaml` and `TermId.yaml`, the schemas, the examples, `glossary.feature`,
    `read-refusals.feature`, `every-operation.feature` and `switch-gate.yaml`;
  - `ArtifactReadService` (`SERVED`, `LITERAL_ITEM_KINDS`, `ABSENT`) and `ForeignArtifactRead`;
  - the `:artifacts-edge` `GlossariesController`, reads and bodies;
  - `PackArtifactRoutingTest`.
- **Sequencing/depends-on**: IC-02, IC-03.
- **Risks**:
  - The literal item path must come ahead of the templated one, as for skill.
  - A wrong set membership answers 500, and the pinned-set tests catch it.
  - The term read iterates all 139 ids (SC-001).

### IC-05: Activation twins

- **Purpose**: `listEffectiveGlossaryContexts`, `getEffectiveGlossaryContext` and `getEffectiveGlossaryTerm`.
  - They take the skill route, answering whatever the state. The term read carries its context's state (research R7).
  - They add `EffectiveTag.Read` values, with the `winner`/`winners` guards and a term query that normalizes both ids.
  - The activation gains its `glossaries` link.
- **Relevant requirements**: FR-011, FR-012, FR-013, FR-019.
- **Affected surfaces**:
  - `EffectiveReadService` (route, held winners), `EffectiveTag`, `ActivationMapper`;
  - the `:artifacts-edge` effective controller and reads;
  - `effective-glossary.feature`, `activation-links.feature`, `ActivationRoutingTest`;
  - the relation table in `docs/development/hypermedia-hateoas.md`.
- **Sequencing/depends-on**: IC-04.
- **Risks**: slice C reshaped `EffectiveReadService` and `EffectiveTag`, so build on `main` after PR #566.

### IC-06: The activation's vocabulary and projections

- **Purpose**: `getActivationVocabularyTerms`, `getActivationVocabularyContextive` (`application/yaml`) and
  `getActivationVocabularyTaboos`, per ADR-0076 decisions 4 to 9 and research R6.
- **Read path**: the `ActivationArtifacts.contextsInForce` port method, its `ActivationsArtifacts` and
  `EffectiveReadService` implementation, and a `PacksVocabulary` adapter.
- **Pure functions**: the projections, in `artifacts-edge.application`.
- **Proof**:
  - `vocabulary.feature`:
    - the whole-override case and its positive control (FR-015);
    - the shared-name case;
    - empty;
    - 304;
    - 406;
    - `activation_not_found`;
    - the Contextive field-set step;
    - the cross-read consistency of SC-004;
    - the three activation links.
  - A unit test counts the pack reads (NFR-005).
- **Relevant requirements**: FR-013, FR-014, FR-015, FR-016, FR-017, FR-019, NFR-005, SC-004.
- **Affected surfaces**:
  - the contract (tag `Vocabulary`, three paths, `ActiveTermList`, `ActiveTerm`, `TabooList`, a Contextive example);
  - `EffectiveTag`, `ActivationMapper`;
  - `:artifacts-edge` (with `snakeyaml-engine`);
  - the relation table, and the media-type exception line in `docs/development/rest-resource-conventions.md`.
- **Sequencing/depends-on**: IC-05, IC-01.
- **Risks**: the YAML body follows the `/openapi.yaml` precedent (research R6). The only new test step is the
  structural YAML check.

### IC-07: Contract bookkeeping, docs and closeout

- **Purpose**: the records say what the service does, and the issues close with evidence.
- **Relevant requirements**: FR-021, FR-022, NFR-001, NFR-002, SC-005.
- **Records and docs**:
  - the change fragment, `### Breaking` (each renamed token and what a caller changes) and `### Added`;
  - the packs CHANGELOG entry, if #559 has landed;
  - `docs/api/service-contract.md`, #106's criterion: the decision, the shape, the scenarios and the corpus cases;
  - the divergence register (DIV-012, DIV-106, DIV-211 and the lenient-conversions entry, page and YAML);
  - the pack-ecosystem work breakdown item 4, and the next-slices and roadmap rows;
  - the NFR-002 `git grep` check.
- **Closeout checklist** (issue-tracker Rules 2 and 4):
  - `Closes #106` and `Closes #536`, with #106 ticked in #536.
  - R12 ticked in #94.
  - `Refs` #142, #361, #164 and #537.
  - A comment on #518 pointing it at `avoid`.
  - The open-packs draft issue (`research/open-packs-draft-glossary-contexts.md`, `open-packs#TBD-glossary-contexts`,
    with the term-id map). The orchestrator files it and replaces the placeholder before merge.
  - The PR body notes the 2026-11-14 rebase that re-signed the CLI commits.
- **Affected surfaces**: `contracts/charter/CHANGELOG.md`, `changes/`, `docs/**`, `research/**`.
- **Sequencing/depends-on**: IC-04, IC-05, IC-06.
- **Risks**: none beyond the docs checks.

## Risks

1. **Shared kind surfaces with slice F (#537)**:
   - `NodeKind`, `ArtifactKind` and `file-kinds.yaml`;
   - coordinated through the orchestrator;
   - whichever lands second rebases.
2. **This repository's governance packs**:
   - `packs/internal` and `packs/qa` stay in the old shape until open-packs migrates, so the drift watch is red until
     then;
   - accepted (research R11).
3. **New code meets slice D's complexity gate and the mutation floors from the start** (research R15).
4. **The kind rename is a large atomic change (IC-02)**: it is reviewed against the occurrence map, path by path.
