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
  (`application/yaml`) and the avoid list.

The reads are built as slice A2 built `skill`: a contract path per operation, the `:artifacts-edge` controllers, the
packs-side `ForeignArtifactRead`, and `EffectiveReadService` for the activation tree. Each operation passes the
switch-gate register with `@contract` scenarios.

Implementation was gated on slice C (#467), which merged as PR #566. The operator said GO on 2026-11-13. The mission
branch has `main` `d71e79bb` merged in.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain); OpenAPI 3.1 contract in `contracts/charter/`; JSON Schema 2020-12
pack schemas in `contracts/packs/`.
**Primary Dependencies**: these are the existing ones: Spring Boot web, the generated `ai.speckitty.charter.api`
interfaces (`useTags`), and SnakeYAML/Jackson YAML as the packs storage already uses them. Cucumber runs the contract
scenarios. No new dependency is added, so no supply-chain review applies (DIRECTIVE_051).
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
**Performance Goals**: each read verifies one `ConsistentRead` or `PacksSession` per request. A vocabulary read
resolves the closure once and walks each winning context once (NFR-005). The 139-term example vocabulary is the
reference load.
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
│   Artifact; new GlossaryContext*, GlossaryTerm, EffectiveGlossary*Read, ActiveTerm*, AvoidList, Activation (links)
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

> These concerns are not work packages. `/spec-kitty.tasks` turns them into work packages.

### IC-01: ADR-0076 and the record annotations

- **Purpose**: decide the vocabulary reads before they are built (FR-018), and annotate ADR-0049 decisions 7 and 13 and
  ADR-0074 decision 3 with what this Mission builds.
- **Relevant requirements**: FR-018, FR-015, FR-021.
- **Affected surfaces**: `docs/adr/0076-…md` (Proposed, then Accepted by the operator), `docs/adr/0049-…md`,
  `docs/adr/0074-…md`, `docs/adr/README` index.
- **Sequencing/depends-on**: none. It is accepted before tasks.
- **Risks**: an ADR number collision with slice F (announced through the orchestrator).

### IC-02: The kernel's kinds

- **Purpose**: `ArtifactKind.GLOSSARY`, `NodeKind.GLOSSARY_CONTEXT` and `TERM`, with explicit node-kind tokens and
  artifact kinds (research R1), and `Relation.VOCABULARY`'s targets.
- **Relevant requirements**: FR-004, FR-005, FR-006.
- **Affected surfaces**: `ArtifactKind`, `NodeKind`, `Urn` (parse of `term:<ctx>/<term>`), and their tests.
- **Sequencing/depends-on**: IC-01 (accepted).
- **Risks**: shared ground with slice F's taxonomy. Every kind switch in the service must compile, which is the point:
  the compiler finds each use.

### IC-03: The packs contract and the file family

- **Purpose**: `glossary.schema.json` replaces `glossary-pack.schema.json`. The token and plural follow in every
  packs schema and in `file-kinds.yaml`, and the id patterns are pinned (FR-001 to FR-005).
- **Relevant requirements**: FR-001, FR-002, FR-003, FR-004, FR-005.
- **Affected surfaces**: `contracts/packs/**`, `VocabularyEqualityTest`, `PackFileKindTest`, the schema tests.
- **Sequencing/depends-on**: IC-02.
- **Risks**: PR #568 (#559) touches `file-kinds.yaml`, so rebase on whichever lands first.

### IC-04: The domain, storage and round trip

- **Purpose**: `GlossaryContext`, `GlossaryTerm`, the identifiers, `TermStatus` and `AppliesTo`. The binding (layout,
  file stem, schema), the YAML codec and presets (`activated_glossaries`). The old classes are deleted. Old-shape
  content is refused at load.
- **Relevant requirements**: FR-001 to FR-005, FR-020.
- **Affected surfaces**: `service/packs/…/domain`, `…/infra/storage`, `PresetYaml`, `PresetMapper`,
  `IdentifierBoundsTest`, about 30 test classes.
- **Sequencing/depends-on**: IC-02, IC-03.
- **Risks**:
  - The `:packs` mutation floor; new invariants need killing tests.
  - The whole-pack round trip must model the new file (#547).

### IC-05: Fixtures and example packs

- **Purpose**: move every glossary fixture to the new shape, with the term-id map (research R4 and R10). Re-record the
  pinned digests and file sets. Add one planted old-shape refusal.
- **Relevant requirements**: FR-020, FR-005, NFR-002.
- **Affected surfaces**: `example-packs/built-in/**`, `digest-baseline/**`, `pack-level/presets-pack/**`,
  `planted/**`, `research/term-id-map.md`.
- **Sequencing/depends-on**: IC-04.
- **Risks**: a digest re-record hides a real change, so each one is justified in the commit.

### IC-06: The charter contract's kind surfaces and the activation document

- **Purpose**: `NodeKind`, `ActivatableKind`, `CascadeKind`, `SelectedArtifacts`, `DeactivatedArtifacts`,
  `ActivationConfig` (and its corpus), `Preset`, `ArtifactKindToken`, the `Artifact` `oneOf` member `glossary`, and the
  400 detail in `effective-reads.feature`. Stored and written old members are refused (research R2).
- **Relevant requirements**: FR-005, FR-008.
- **Affected surfaces**: `contracts/charter/schemas/**`, `contracts/offering/**`, `WireTokenParityTest` (its "unknown
  kind" example moves off `glossary`), `ActivationWireTokenParityTest`, `ActivationFileCodec` tests.
- **Sequencing/depends-on**: IC-02.
- **Risks**: the generated client changes, so the `### Breaking` entry must say what a caller changes.

### IC-07: Glossary reads in a pack

- **Purpose**: `listPackGlossaryContexts`, `getPackGlossaryContext` and `getPackGlossaryTerm`, with refusals in the
  order pack, context, term (research R3), and `?kind=glossary` in the pack list.
- **Relevant requirements**: FR-007, FR-008, FR-009, FR-010, FR-012, FR-019.
- **Affected surfaces**: contract paths, schemas, examples and `glossary.feature`; `ArtifactReadService` sets;
  `ForeignArtifactRead`; the `:artifacts-edge` controller, reads and bodies; routing tests; switch-gate rows.
- **Sequencing/depends-on**: IC-04, IC-06.
- **Risks**: a literal item path ahead of the templated one, as for skill. A wrong set membership answers 500, and the
  pinned-set tests catch it.

### IC-08: Activation twins

- **Purpose**: `listEffectiveGlossaryContexts`, `getEffectiveGlossaryContext` and `getEffectiveGlossaryTerm`. The skill
  route answers whatever the state, and the term read carries its context's state (research R7). It adds
  `EffectiveTag.Read` values and the activation links.
- **Relevant requirements**: FR-011, FR-012, FR-013, FR-019.
- **Affected surfaces**: `EffectiveReadService`, `EffectiveTag`, `ActivationMapper`, the `:artifacts-edge` effective
  controller and reads, `effective-glossary.feature`, `activation-links.feature`.
- **Sequencing/depends-on**: IC-07.
- **Risks**: slice C reshaped `EffectiveReadService` and `EffectiveTag`, so build on `main` after PR #566.

### IC-09: The activation's vocabulary and projections

- **Purpose**: `getActivationVocabularyTerms`, `getActivationVocabularyContextive` (`application/yaml`) and
  `getActivationVocabularyAvoid`. Their bodies, order and caching follow ADR-0076 and research R6.
- **Relevant requirements**: FR-014, FR-015, FR-016, FR-017, FR-019, NFR-005.
- **Affected surfaces**: the contract (tag `Vocabulary`), `vocabulary.feature`, the `:artifacts-edge` vocabulary reads
  and projection functions, `EffectiveTag`, and the link checks for a YAML body.
- **Sequencing/depends-on**: IC-08, IC-01.
- **Risks**:
  - The contract tooling (closed-response and example checks) may assume JSON bodies. ADR-0076 describes the YAML body
    by an example, and the tooling is extended only if a check refuses it.
  - Proving a Contextive tool accepts the file: the scenario checks the structure against the Contextive v1 field
    set. It does not run the tool.

### IC-10: Contract bookkeeping, docs and closeout

- **Purpose**: the change fragment (`### Breaking` and `### Added`); `docs/api/service-contract.md` (#106's
  criterion); the divergence register (DIV-012, DIV-106, DIV-211 and the lenient-conversions entry); the
  pack-ecosystem work breakdown item 4; the next-slices and roadmap rows; the open-packs draft issue (FR-022); the
  occurrence-map exceptions checked (NFR-002).
- **Relevant requirements**: FR-021, FR-022, NFR-001, NFR-002, SC-005.
- **Affected surfaces**: `contracts/charter/CHANGELOG.md`, `changes/`, `docs/**`, `research/open-packs-draft-glossary-contexts.md`.
- **Sequencing/depends-on**: IC-07, IC-08, IC-09.
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
3. **Unsigned CLI commits** `8def7f3e` and `fedc28da`: ruled to be squashed at merge, which the PR body notes.
4. **YAML response body**: this is new to the contract tooling (IC-09).
