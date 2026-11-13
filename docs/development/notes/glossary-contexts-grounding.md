---
title: "Note: grounding for the glossary contexts and terms Mission (#106)"
description: What the R12 glossary Mission builds, the rename surface it touches, its overlap with slice C and epic #549, and the tracked issues it closes or leaves, before the spec is written.
doc_status: draft
updated: 2026-11-13T00:00:00Z
type: Guide
kind: Reference
related:
  - adr/0049-glossary-contexts-and-terms
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0074-the-remaining-cutover-reads-and-their-paths
  - plans/next-slices-2026-11-12
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you specify, plan or build the glossary kind, its reads, or the rename of glossary_pack.
---

# Note: grounding for the glossary contexts and terms Mission (#106)

**Status: grounding note, written before the spec.** The decisions it relies on are already
recorded in ADRs. This note maps the ground the Mission stands on, against `main` at `848170e`
on 2026-11-13. The Mission is kind `feature` (a model change), and it runs as a full Mission (operator
ruling at specify, 2026-11-12). It closes slice A2's last box (#536).

## What is ruled

- **Kind** ([ADR-0049](/adr/0049-glossary-contexts-and-terms/) decisions 1 to 6;
  [ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decision 3).
  - `glossary_pack` becomes `glossary`, and its artifact is a glossary context
    `{schema_version, id, name, description, tags?, applies_to?, terms}`.
  - A term is an item inside its context, `{id, name, definition, examples?, aliases?, avoid?, status, see_also?}`.
  - `confidence`, `introduced_in_mission` and `provenance` are dropped. `surface` becomes `name`, and
    `synonyms_to_avoid` and `banned_synonyms` become `avoid`.
  - The layout is `glossaries/<context-id>.glossary.yaml`.
  - The shape replaces `glossary-pack.schema.json` outright, with no reader of the old shape (charter, "the
    charter's model is canonical").
- **URNs** (ADR-0049 decision 4, amended by [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/)
  decision 13).
  - A context is `glossary_context:<id>`, and a term is `term:<context-id>/<term-id>`.
  - The split from the kind token `glossary` is intended. `term` is a node-only kind, like `action`.
- **Reads** (ADR-0074 decisions 9 to 14; ADR-0049 decision 7 stands; ruling on #106, 2026-11-12).
  - `GET /packs/{packId}/artifacts/glossary` lists id, name, description, tags and term count. `?kind=glossary`
    lists the contexts too.
  - `GET …/artifacts/glossary/{contextId}` is a literal item path with an operation of its own.
  - `GET …/artifacts/glossary/{contextId}/terms/{termId}` reads one term.
  - Each read has an activation twin at the same suffix under `/activations/{activationId}`.
- **Identifier grammar** (operator ruling, 2026-11-13, relayed by the orchestrator).
  - Context ids and term ids use the kernel's `IdentifierGrammar.LETTER_FIRST_SLUG`: kebab, letter first, at
    most 64 characters, `a--b` refused.
  - This Mission owns its `IdentifierBoundsTest` rows and depends only on the grammar constants already on `main`.
  - Epic #549 slice #551 adds `IdentifierGrammar.letterFirstSlug(max)` and renames `Constraints.isNotBlank()` to
    `notBlank()`. Whichever lands second rebases.
- **Out of scope.** Core's glossary consumers (#332 to #335, #518) are post-cutover. The open-packs glossaries
  migrate in `spec-kitty-open-packs` (a draft issue, `open-packs#TBD-glossary-contexts`).

## The rename surface

About 820 occurrences in about 60 files outside `docs/`. The domain shape is new, not renamed: today's
`GlossaryTerm` is keyed by `surface`, with core's fields.

| Area | Where |
| --- | --- |
| Kernel | `ArtifactKind.GLOSSARY_PACK`; `NodeKind.GLOSSARY_PACK` |
| Packs domain | `GlossaryPack`, `GlossaryTerm`, `GlossaryPackIdentifier` (any non-blank text today) |
| Packs storage | `GlossaryPackBinding` (`fileToken` `glossary-pack`), `GlossaryPackDso`, `GlossaryPackYaml`, `KindBindings`, `YamlFormats`, `PresetYaml` (`activated_<plural>`), `PresetMapper` |
| Packs contract | `glossary-pack.schema.json`; `file-kinds.yaml` (node, artifact and fragment kinds, the `**/*.glossary-pack.yaml` rule); `graph-shard`, `org-fragment`, `pack-manifest`, `preset`, `charter`, `policy` and `mission-step-contract` schemas |
| Charter contract | `ActivatableKind`, `CascadeKind`, `NodeKind`, `SelectedArtifacts`, `DeactivatedArtifacts`, `Preset` (`activatedGlossaryPacks`); `ArtifactKindToken` and the `Artifact` `oneOf` gain `glossary`; `effective-reads.feature` (a 400 detail lists every token) |
| Offering contract | `ActivationConfig.schema.json` and corpus cases `valid/06` and `valid/07` |
| Fixtures | example pack `built-in` (`glossary_packs/spec-kitty-core.glossary-pack.yaml`, `glossary_pack.graph.yaml`, manifest, policy, default preset); `digest-baseline` (`eta.glossary-pack.yaml`); `pack-level/presets-pack` |
| Tests | about 30 classes, including the wire-token parity tests, `VocabularyEqualityTest`, `ArtifactKindTest` and the preset tests |
| Elsewhere | `.kittify/charter/charter.yaml`, `scripts/usability/charter-to-activation.py`, `site/.contextive/definitions.yaml`, about 20 docs pages |

### The template: slice A2's skill reads

PR #546 built the same read set for `skill` and `asset`. The glossary follows `skill`, because both are
activatable:

1. Contract: tags `Glossaries` and `EffectiveGlossaries`, then the literal paths ahead of the templated ones.
2. `artifacts-edge`: the controllers and body mappers.
3. Application: `…Reads`, `Effective…Reads` and the `ActivationArtifacts` port.
4. Packs: `ForeignArtifactRead`; `ArtifactReadService` (`SERVED`, `LITERAL_ITEM_KINDS`, `ABSENT`).
5. Activations: the `EffectiveReadService` route and `heldWinners`; `EffectiveTag.Read` and its guards; the
   `ActivationMapper` links.
6. Gates: one `switch-gate.yaml` row per operation.

The term read follows the mission-step read, a part inside an item with its own 404, in both trees.

### Surprises

1. **The URN token is the kind token today.** `NodeKind.token()` is the lower-cased enum name, and
   `NodeKind.of(ArtifactKind)` is `valueOf(name)`. A `glossary_context` URN needs a URN token apart from the kind
   token, through `NodeKind`, `Urn`, `file-kinds.yaml` (which keeps `node_kinds` equal to `artifact_kinds`) and
   `VocabularyEqualityTest`. `term` joins `action` as a node-only kind.
2. **The planted `r15-glossary-urn-*` cases stay valid.** They refuse core's `glossary:` URN, and charter still
   never mints `glossary:`.
3. **`WireTokenParityTest` uses `glossary` as its example of an unknown kind.** It needs another token.
4. **Stored activations hold closed members.** `selected.glossary_pack` and `deactivated.glossary_pack` fail after
   the rename. A0 upgraded v1 to v2 in `ActivationFileCodec` before the 2026-11-10 rule; that rule now forbids a
   reader of the old shape. See question Q1.
5. **Today the glossary is not served in a pack at all.** `?kind=glossary_pack` is a 400 in a pack, and
   `getEffectiveArtifact` refuses it as `kind_not_found`.
6. **The real packs carry the old shape.** `packs/open-packs.lock.yaml` pins open-packs content with
   `glossary_packs/`. `packs/internal` and `packs/qa` (this repository's own governance, synced by
   `openPacksRef`) would no longer load until open-packs migrates. Charter's tests read charter's own example
   packs ([ADR-0070](/adr/0070-charter-tests-own-example-packs-not-open-packs-content/)), so the gates are not
   affected. The drift watch (`open-packs-drift.yml`) goes red until open-packs follows; epic #549 is retiring it.

## Overlap with in-flight work

- **Slice C (#467)**, branch `slice-c/effective-step-context`. It has 257 changed files, 7 of which touch
  glossary tokens:
  - `effective-reads.feature`, `ClosureViewGovernanceTest`, `InForceResolutionTest`, `WireTokenParityTest`,
    `EffectiveProfileReadTest`, `PacksProfileSourceTest` and `site/.contextive/definitions.yaml`;
  - also the shared seams `EffectiveReadService`, `EffectiveTag`, `ActivationMapper` and `ClosureView`.

  Ruling: implement starts only after slice C merges, or rebases onto it.
- **Epic #549.** #551 (rule vocabulary) and #552 (identifiers on the kernel grammar) change `kernel.validation`
  and the identifier types. The glossary identifiers are new types on the grammar from the start.
- **#547.** The whole-pack gate skips file kinds the round trip does not model. Check that the new `glossary`
  file kind is round-tripped, not skipped.
- **PR #561 (#553).** It has no glossary or kind code. Low risk.

## Tracked issues (dispositions)

| Issue | Disposition |
| --- | --- |
| #106 | Delivers: `Closes #106` |
| #536 | Delivers: tick #106, `Closes #536` |
| #94, #142 | Partial: `Refs`; tick R12 in #94; mark work-breakdown item 4 done |
| #361, #164, #537 | Unblocked, not delivered: `Refs` only |
| #332 to #335, #518 | Out of scope (core, post-cutover). #518 still names the old fields; a closeout comment points it at `avoid` |
| #559 | Sequencing: the packs-contract release follows this schema change |
| #552, #547 | Overlap risk, above |

The docs that move with the model:

- the [pack-ecosystem work breakdown](/plans/pack-ecosystem-work-breakdown/), item 4;
- the divergence register: DIV-012 (URN kinds), DIV-106 (vocabulary walk), DIV-211 (preset
  `activated_glossary_packs`), and the lenient-conversions entry that cites `glossary-pack.schema.json`;
- `docs/api/service-contract.md` (#106's own criterion);
- the next-slices plan and the roadmap's A2 row.

A squad claim that `glossary_context` breaks a kebab URN-token rule was checked and dropped. ADR-0053 decision 13
rules that token, and URN tokens use underscores (`mission_type`).

## Questions for the operator (asked at specify)

- **Q1. Stored activations that hold `glossary_pack`.**
  - (A) No upgrade: the closed objects refuse the old member, as any invalid stored document is refused. There is
    no deployment with stored state.
  - (B) A one-shot schema-version upgrade, as A0 did.
  - Recommendation: (A), per the charter's model rule.
- **Q2. Refusal codes for the new reads.** Recommendation:
  - a context the pack does not hold is `404 artifact_not_found`, as for skill;
  - a term the context does not hold is a new `404 term_not_found`, as a step is `action_not_found`.
- **Q3. Term ids for the example-pack terms.** Recommendation: mint each from its surface form in kebab case, and
  record the mapping for the open-packs migration.
