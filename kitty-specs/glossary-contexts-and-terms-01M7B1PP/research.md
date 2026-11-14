# Research: R12 glossary contexts and terms

Grounding: [the grounding note](../../docs/development/notes/glossary-contexts-grounding.md), written against `main`
at `848170e` and re-checked against `main` at `d71e79bb` after slice C (#467, PR #566) merged.

Status words below:

- **Ruled**: an operator ruling, recorded as a Decision Moment.
- **Plan call**: a reversible call made in this plan. The plan-point squad may challenge it, and the operator may
  overturn it.

## R1. Kind, node kinds and tokens (plan call)

- **Decision**:
  - `ArtifactKind.GLOSSARY_PACK("glossary_pack", "glossary_packs", true)` becomes
    `ArtifactKind.GLOSSARY("glossary", "glossaries", true)`. Its keys follow: `activated_glossaries`,
    `selected_glossaries`, `required_glossaries` and `selected.glossary`.
  - `NodeKind.GLOSSARY_PACK` becomes two node kinds:
    - `GLOSSARY_CONTEXT`, URN token `glossary_context`, standing for `ArtifactKind.GLOSSARY` (ADR-0053 decision 13);
    - `TERM`, URN token `term`, node-only like `ACTION`.
  - `NodeKind` stops deriving its facts from the constant name. Each constant declares:
    - its token;
    - its artifact kind (empty for `ACTION` and `TERM`);
    - its fragment plural (none for `TERM`);
    - whether a graph document may declare it as a node;
    - whether an edge may target it.
  - `NodeKind.of(ArtifactKind)`, `plural()` and `fromPlural()` map through that table.
  - `TERM` is on the wire (`NodeKind.yaml`) and is an edge target of `vocabulary`. It is never declared as a node and
    has no fragment plural.
  - The single `authoredInGraphs()` predicate splits into the two flags.
  - `WireTokenParityTest` and `VocabularyEqualityTest` check each flag against its own contract surface. That changes
    what they check; it is not a weakening.
  - A `term:` edge target that no held context resolves is a broken reference (squad, Java lens H2 and H3; architect
    lens M2).
- **Every place that turns a URN's kind token into an artifact kind maps through `NodeKind.artifactKind()`**, never
  `token()`. Today `glossary_pack` is spelled the same in both, which hides the conflation. The places:
  - `EffectiveProfileDtos` (lines 106, 114, 118 and 148);
  - `ProfileDtos` (115);
  - `EffectiveBodies` (259);
  - the `linksArtifact` paths of the profile reads.

  A governance test with a `vocabulary` edge proves it (squad, Java lens H1).
- **Rationale**:
  - ADR-0053 decision 13 splits the URN token from the kind token on purpose.
  - Name-derived mapping (`valueOf(kind.name())`, `fromToken(token())`) cannot express that split.
  - An explicit table is also what slice F's kind taxonomy (#537) records per kind.
- **Alternatives**: keep `glossary` as the URN token too. Rejected: it reverses ADR-0053 decision 13, and it collides
  with the planted cases that refuse core's `glossary:` URN.
- **Contract**:
  - `NodeKind.yaml` replaces `glossary_pack` with `glossary_context` and `term`.
  - `ActivatableKind`, `CascadeKind`, `SelectedArtifacts`, `DeactivatedArtifacts`, `ActivationConfig.schema.json`
    and `Preset` (`activatedGlossaries`) replace it with `glossary`.
  - `ArtifactKindToken` and the `Artifact` `oneOf` gain `glossary`.
- **`file-kinds.yaml`**:
  - `node_kinds` gains `glossary_context` and `term`, and `artifact_kinds` gains `glossary`.
  - `node_kinds` stops equalling `artifact_kinds` plus `action`. `VocabularyEqualityTest` checks the explicit table
    instead.
  - The fragment plural is `glossaries: glossary_context`, the artifact kind's plural, so no new plural mechanism is
    needed (squad, architect lens M1). A term is never declared in a fragment: it is derived from its context file, so
    `term` has no fragment key.
  - The file rule is `**/*.glossary.yaml` → schema `glossary`.

## R2. Stored activations (ruled, `DM-01M7B1Q1HW2KDEEW3MKCK2A2PT`)

- **Decision**:
  - No upgrade. `schemaVersion` stays `2`. A stored document that holds `selected.glossary_pack` or
    `deactivated.glossary_pack` fails the closed objects, and the store refuses it as it refuses any invalid stored
    document (`ActivationStorageException`).
  - A write that sends either member is `422 validation_failed` with `unknown_member`.
- **Rationale**:
  - The charter's model rule allows no reader of the old shape.
  - No deployment holds stored state.
  - Bumping to version 3 would only add a version with no upgrade behind it.

## R3. Refusals (ruled, `DM-01M7B1Q4BPQB95EVQBRWN4Q4F8`)

- **Decision**:
  - Order: pack, then context, then term. `404 pack_not_found` comes first. A missing context is
    `404 artifact_not_found` with detail ``glossary context `<id>` is not held``. A missing term is
    `404 artifact_not_found` with detail ``term `<termId>` is not held in glossary context `<contextId>` ``.
  - Under an activation: `activation_not_found` first, then the same.
  - No new refusal code, so `CharterRefusalCode.yaml`, `RefusalCode` and `RefusalHandler` keep their sets. The raised
    map gains the new operations.
- **Rationale**: the operator ruling. The detail names the segment, so a client still knows which one missed.

## R4. Term ids for existing terms (ruled, `DM-01M7B1Q77QRB2PNDG0CDBNVVH3`)

- **Decision**:
  - Rule: lower-case the surface, replace each run of characters other than `[a-z0-9]` with `-`, then trim `-`.
  - Measured on the example pack `built-in` (`spec-kitty-core`): 139 terms give 139 distinct ids, all letter-first
    slugs of at most 64 characters, so none needs a hand-picked id.
  - Statuses: 112 `active`, 15 `deprecated`, 12 `draft`.
  - The mapping is written to `research/term-id-map.md` during the fixture move, and the open-packs draft reuses it.
- **Field moves**:
  - `surface` → `name`;
  - `synonyms_to_avoid` and `banned_synonyms` → `avoid` (deduplicated, in order);
  - `see_also` surfaces → `term:spec-kitty-core/<id>` URNs;
  - `confidence`, `introduced_in_mission` and `provenance` are dropped;
  - the context gains `name` (`Spec Kitty core`).

## R5. Override across levels (ruled, `DM-01M7B85ADQMWP9ZEG99MXFGJKF`)

- **Decision**:
  - A context is an artifact and overrides across levels whole, as every artifact does: project over org over
    built-in. Same-level siblings are refused unless settled (ADR-0048 decision 9).
  - A term is in force when its context's winning copy holds it. There is no per-term merge.
  - ADR-0049 decision 13's "clash on the same `term:` URN" can therefore only arise between two copies of one context,
    and the context's override settles it.
- **Warning** (ruled, `DM-01M7CEZ6V0F8XD95NP8Q5E88KV`): ADR-0049 decision 13's "the load warns" is retired. An
  override is reported by the effective reads as for every kind, with no new notice code.
- **Record**: ADR-0076 decisions 1 and 2 state this reading of ADR-0049 decision 13, and ADR-0049 carries a dated
  annotation.

## R6. The activation's vocabulary reads (ruled: paths, media; shaped in ADR-0076)

- **Paths** (`DM-01M7B8570B11V5ZNYA65Y1GVS1`):
  - `GET /activations/{activationId}/vocabulary/terms`;
  - `GET …/vocabulary/contextive`;
  - `GET …/vocabulary/taboos` (leaf ruled `DM-01M7CEZC44PK37ZHTR82P9N1VV`, a noun).

  They are reads of the activation, not of an artifact (ADR-0049 decision 10), so they do not collide with
  `{contextId}`.
- **Media** (`DM-01M7B85D68EEHK3TTE0DCTA8RT`): `contextive` answers `application/yaml` in the Contextive v1 file
  format. `terms` and `taboos` answer JSON.
  - The YAML body follows the `/openapi.yaml` precedent: `InfoController` answers a string as `application/yaml`, and
    the contract's `paths/openapi.yaml` describes it as `type: string`. The lint and `LinkChecks` already skip non-JSON
    bodies.
  - A structural step in `vocabulary.feature` proves the Contextive field set.
  - An `Accept` that excludes YAML is a 406.
  - The edge renders YAML with `snakeyaml-engine`. It is already in the version catalog and the verification
    metadata, so it becomes a dependency of `:artifacts-edge`, not a new artifact (squad, Java lens M2; DIRECTIVE_051:
    nothing new is fetched).
- **What is in force**: the winning copy of each `glossary` context whose effective state is in force. A context that is
  deselected, denied or not selected contributes nothing. Every term of a context in force is listed, whatever its
  `status`, and each answer carries the status.
  - Plan call: filtering by status is left to the client, as ADR-0049 decision 14 leaves `applies_to` advisory.
- **Bodies** (plan call; ADR-0076 decisions 5 to 7 pin them):
  - `terms`: `{activationId, revision, governanceFingerprint, terms: [ActiveTerm], _links}`. An `ActiveTerm` is:
    - `urn`, `contextId`, `termId`, `name`, `definition`, `status`;
    - `examples?`, `aliases?`, `avoid?`, `seeAlso?`;
    - `packId` and `level`;
    - `_links` (`effective-term`, `effective-glossary`).

    Terms are ordered by context id, then term id.
  - `taboos`: `{activationId, revision, governanceFingerprint, entries: [{word, term, name}], _links}`. `term` is the
    URN. There is one entry per avoided word per term, ordered by word, then URN.
  - `contextive`: `contexts[] → {name, domainVisionStatement, paths?, terms[] → {name, definition, examples?,
    aliases?, meta: {urn, status}}}`.
    - `domainVisionStatement` comes from the context's `description`, and `paths` from its `applies_to.paths`
      (squad, architect lens L3).
    - It is ordered as `terms` is.

  `_links` on every JSON success answer follows the lint rules `success-schemas-carry-links` and
  `success-responses-have-link-examples` (squad, architect lens H1).
- **Caching**: each read gets an `EffectiveTag.Read` value, with the `winner`/`winners` guards of `EffectiveTag`
  taking it. Each answers `ETag` and `Charter-Source-Fingerprint`, and honors `If-None-Match` with `304`. Refusals are
  judged first, including the closure's `500 pack_sibling_conflict` (squad, architect lens M4).
- **The read path** (squad, Java lens M1):
  - The `ActivationArtifacts` port gains `contextsInForce(activationId)`: the winning `glossary` contexts with state
    in force, their packs, and the closure fingerprint. `ActivationsArtifacts` and `EffectiveReadService` implement
    it.
  - A `PacksVocabulary` adapter reads each winning pack's contexts once, checked against that fingerprint.
  - NFR-005 is measured as one activation session plus at most one read per winning pack, counted by a test.
- **Rationale**:
  - The site's Contextive file already uses `meta.status`.
  - The URN in `meta` lets a tool link back.
  - An empty activation answers empty lists, not an error.

## R7. Effective context and term reads (plan call)

- **Decision**:
  - `glossary` is activatable, so it follows the skill route of slice A2 (R1 of A2): the effective item answers
    whatever the state, with state and reason.
  - The effective term read finds the winning context the same way, then the term inside it. It carries the context's
    state, because a term has no state of its own.
  - Each read is a new `EffectiveTag.Read`, so no two URLs share a tag.
- **Alternatives**: answer a term only when its context is in force. Rejected: the item read answers every state, and
  the term read would split the tree.

## R8. Module placement (plan call)

- **Decision**:
  - The domain (`GlossaryContext`, `GlossaryTerm`, `GlossaryContextIdentifier`, `TermIdentifier`, `TermStatus`) and its
    storage binding live in `:packs`, beside the other kinds.
  - The six pack and twin reads and the three vocabulary reads live in `:artifacts-edge`, as the skill and asset reads
    do (A2 R13). The edge renders `packs.domain` types under an activation.
  - The projections are pure functions of the list of active terms, in `artifacts-edge.application`.
- **Alternatives**:
  - Put the vocabulary reads in `:activations`. Rejected: it would make that slice render `packs.domain` types, which
    the hexagonal rules forbid.
  - A new `:glossary-edge` module. Rejected: one more module for a read set A2 already hosts.

## R9. Identifier grammar (ruled, `DM-01M7B1QF8FQ38JNYVARN95AQ9F`)

- **Decision**:
  - `GlossaryContextIdentifier` and `TermIdentifier` check through `IdentifierGrammar.isLetterFirstSlug`.
  - `glossary.schema.json` pins `^(?!.*\n)[a-z][a-z0-9]*(-[a-z0-9]+)*$` with `maxLength` 64 on both ids, as
    `skill.schema.json` does.
  - The path parameters `ContextId` and `TermId` carry the same pattern.
  - `IdentifierBoundsTest` gains one row for each type.
- **Coordination**:
  - #551 adds `IdentifierGrammar.letterFirstSlug(max)` and renames `Constraints.isNotBlank()` to `notBlank()`.
  - #552 adds rows only for the types it migrates.
  - Whichever lands second rebases onto the other.

## R10. Fixtures and example packs (plan call)

- **Decision**: these move by hand (the term ids by the R4 rule):
  - `built-in`: `glossary_packs/spec-kitty-core.glossary-pack.yaml` → `glossaries/spec-kitty-core.glossary.yaml`;
    `glossary_pack.graph.yaml` → `glossary.graph.yaml`, with URN `glossary_context:spec-kitty-core`; the manifest; the
    policy; `presets/default.yaml`;
  - `digest-baseline`: `glossary_packs/eta.glossary-pack.yaml` → `glossaries/eta.glossary.yaml`;
  - `pack-level/presets-pack`: the preset.

  Pinned digests and file-set expectations are re-recorded from the new files, with each change named in the commit.
  The planted `r15-glossary-urn-*` cases keep refusing core's `glossary:` URN, and their expected verdict is unchanged.
  A new planted case refuses a pack in the old shape.
- **Rationale**: the charter's rule that tests and examples carry only the canonical shape.

## R11. This repository's own packs and the drift watch (accepted risk)

- **Fact**: `packs/internal` and `packs/qa` are synced from open-packs content in the old shape. After the rename,
  the charter service refuses them, and the open-packs drift watch reports it until open-packs migrates.
- **Decision**:
  - No compatibility.
  - The open-packs draft issue (`research/open-packs-draft-glossary-contexts.md`, `open-packs#TBD-glossary-contexts`)
    carries the migration and the term-id map.
  - The spec-kitty CLI's own reading of those packs does not depend on the charter service.
- **Rationale**: ADR-0070. Charter's gates read charter's example packs, and open-packs follows the model.

## R12. Overlap with in-flight work (re-checked 2026-11-13 against `main` `d71e79bb`)

| Work | Overlap | Handling |
| --- | --- | --- |
| Slice F, #537, PR #567 (session `…5y5C3P8wCtoNrH4TU`) | Its plan says the `vocabulary` target kind is `glossary` once #106 lands. Its kind taxonomy will touch `NodeKind`, `ArtifactKind` and `file-kinds.yaml`. | R1's explicit `NodeKind` table is shared ground. Coordinate through the orchestrator; whichever lands second rebases. |
| #559, PR #568 (packs contract release) | Adds `contracts/packs/CHANGELOG.md` and touches `file-kinds.yaml`. | If it lands first, this Mission adds a `### Breaking` entry to the packs CHANGELOG too. |
| Slice D close-out, PR #569 | `RefusalCode.java` and `PackPolicy`. This Mission adds no refusal code. | Rebase only. |
| #551 and #552 (epic #549) | `kernel.validation` and the identifier types. | R9. |
| #547 | The whole-pack gate skips unmodelled file kinds. | The proof checks that `glossaries/*.glossary.yaml` is round-tripped, not skipped. |

## R14. This repository's charter file (ruled, `DM-01M7CEZ19K8QRA3KX68KRWX22G`)

- **Fact**: `.kittify/charter/charter.yaml` holds `selected_glossary_packs` and `activated_glossary_packs`. The
  spec-kitty CLI writes and reads them, and `ProjectCharterYamlSchemaTest` validates the file against charter's closed
  `charter.schema.json` (squad, Java lens H5).
- **Decision**: the test stops validating this repository's core-written file. It is core's format, and the charter
  keeps no compatibility with core's formats. The file is not edited, so the CLI keeps working. Any `charter.schema.json`
  coverage the test gave moves to a canonical example charter file in the test resources.

## R15. Gates on new code (squad, Java lens M3)

- Slice D's close-out (PR #569) adds an ADR-0058 complexity gate: Checkstyle `CyclomaticComplexity` 15,
  `NPathComplexity` 1000, `MethodLength` 60 and `NestedIfDepth` 1, with no suppressions.
- The new decode, the route switches and the projections are written to it from the start.
- Mutation floors: kernel 75; packs, activations and artifacts-edge 85. No floor is lowered.

## R13. ADR number

ADR-0076 was free on `main` and in the open PRs on 2026-11-13, and is announced to the orchestrator.

## R16. Integration by rebase (operator, 2026-11-14)

- `main` is integrated by rebase, never by merge. Force-push is allowed only for a rebase of this Mission's own branch,
  with a lease.
- On 2026-11-14 the branch was rebased onto `main` `d71e79bb`. Every commit was re-signed, including the two
  CLI-written ones that were unsigned.

## Squad dispositions

The plan-point adversarial squad's findings and their dispositions are recorded in `research/squad-post-plan.md`.
