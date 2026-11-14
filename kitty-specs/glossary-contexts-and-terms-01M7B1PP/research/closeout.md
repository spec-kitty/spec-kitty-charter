# Closeout: R12 glossary contexts and terms

**Mission**: `glossary-contexts-and-terms-01M7B1PP` (#106, the last box of slice A2, #536). Written 2026-11-14 by WP08
from the code as built in lane `lane-f` (WP01 to WP07, all approved), before the lanes were consolidated.

Nothing was filed, closed or commented on from this Mission. The orchestrator does each step in "For the
orchestrator" (issue-tracker Rule 2).

## For the PR body

```text
Closes #106
Closes #536
Refs #94 #142 #361 #164 #537 #518
```

- #94: R12 is decided and built; the epic stays open for its other reads.
- #142: item 4 of the pack-ecosystem work breakdown is done; the epic has other items.
- #361, #164, #537: unblocked, not delivered. #537's kind taxonomy now includes `glossary`.
- #518: core's glossary evaluator is out of scope (C-004); it is pointed at `avoid` and the taboo list (below).

The branch was rebased onto `main` `d71e79bb` on 2026-11-14, and every commit was re-signed, including the two
CLI-written commits that were unsigned before (operator ruling; research R16).

### Not done

Squad and review minors recorded rather than folded (issue-tracker Rule 1). The orchestrator fixes the first five at
consolidation.

- The refusal detail texts (``glossary context `<id>` is not held``, ``term `<t>` is not held in glossary context
  `<c>` ``) are written in three places, not one shared kernel text (WP05 review). The scenarios pin them.
- Two path descriptions carry swapped FR labels: `getActivationVocabularyContextive` says FR-017 (it is FR-016), and
  `getActivationVocabularyTerms` says FR-016 (WP07 review).
- The contract's Contextive example (`Contextive.two-contexts.yaml`) is not mechanically tied to the renderer; a
  drift test would pin it (WP07 review).
- `vocabulary.feature` has no term with a non-ASCII name, so the code point ordering and the UTF-8 body are proven by
  unit tests only (WP07 review).
- The Gherkin of the glossary scenarios needs an alignment pass, raised in review; the orchestrator does it at
  consolidation.
- `PolicyYaml`'s glossary `kind_defaults` mapping has no direct `PolicyYamlTest` case, and two `GlossaryContextTest`
  display names claim an inline positive control they do not hold (WP02 review).
- `NodeKind.of()` has an unreachable null check, and `byArtifactKind()` does not refuse a duplicate mapping (WP01
  review).
- Three surviving mutants on the WP03 changed lines are defensive conjuncts (WP03 review).
- NFR-005 is proven piecewise in unit tests and end to end by the `vocabulary.feature` 139-term scenario; the
  `ActivationsArtifacts` package's mutation score was low before this Mission (WP06 review).

## What was delivered

| Requirement | Delivered by | Evidence |
| --- | --- | --- |
| FR-001 to FR-004 | `glossary.schema.json`, closed; `GlossaryContext`, `GlossaryTerm`, the two identifier types and `TermStatus` in `:packs`; `glossaries/<id>.glossary.yaml` in `file-kinds.yaml` | `GlossaryAndSkillSchemaTest`, `GlossaryAndSkillCorpusTest`, the planted `glossary-*` cases |
| FR-005 | `glossary_pack` replaced in every enum, schema, fixture and the stored document, with no reader | `GlossaryOldShapeRefusalTest`, planted `glossary-pack-old-shape`, corpus `ActivationConfig/invalid/19-glossary-pack-member`, `activation-glossary-pack-refused.feature` |
| FR-006 | `NodeKind` `GLOSSARY_CONTEXT` (`glossary_context:<id>`) and `TERM` (`term:<context>/<term>`), an explicit table (WP01) | `NodeKindTest`, `WireTokenParityTest`, planted `r15-glossary-urn-*` |
| FR-007 to FR-010, FR-012 | `listPackGlossaryContexts`, `getPackGlossaryContext`, `getPackGlossaryTerm` | `glossary.feature` |
| FR-011, FR-013 | `listEffectiveGlossaryContexts`, `getEffectiveGlossaryContext`, `getEffectiveGlossaryTerm`; `getActivation` links `glossaries` and the three vocabulary relations | `effective-glossary.feature`, `activation-links.feature` |
| FR-014 to FR-017 | `getActivationVocabularyTerms`, `getActivationVocabularyContextive` (`application/yaml`), `getActivationVocabularyTaboos` | `vocabulary.feature` |
| FR-018 | ADR-0076, Accepted 2026-11-14, after the squad and the operator | `docs/adr/0076-the-activation-vocabulary-reads-and-projections.md` |
| FR-019 | Every new operation has `@contract` scenarios, an example and a `ready` switch-gate row | `CharterContractSuite` |
| FR-020 | Charter's example packs and fixtures carry only the new shape; 139 term ids minted | `term-id-map.md`; WP02 commit `3a918d57` names each re-recorded pin |
| FR-021 | The change fragments, the ADR-0049 and ADR-0074 annotations (lane `lane-f`); the service contract, divergence register, plans and pack schema page (WP08) | this WP's commit |
| FR-022 | The open-packs draft issue | `open-packs-draft-glossary-contexts.md` |
| SC-001 | 139 terms read one by one in a pack | WP04's term-by-term test over the example pack |
| SC-002 | Glossary contexts served in a pack and under an activation by ADR-0074's paths, each through the switch gate | `glossary.feature`, `effective-glossary.feature` |
| SC-003 | The old shape refused in every registration path | `GlossaryOldShapeRefusalTest` (load, validate mode, registration) |
| SC-004 | The Contextive URNs are the active terms' URNs; the taboo entries are their `avoid` entries | `vocabulary.feature`, the SC-004 outline; `VocabularyReadsTest` |
| SC-005 | Closed with evidence by the orchestrator | below |

## Decisions

Operator rulings, each a Decision Moment unless dated:

| Ruling | Decision |
| --- | --- |
| `DM-01M7B1Q1HW2KDEEW3MKCK2A2PT` | No upgrade of stored activations; one that holds `glossary_pack` is refused |
| `DM-01M7B1Q4BPQB95EVQBRWN4Q4F8` | A context or term not held is `404 artifact_not_found`, its detail naming the segment; no new code |
| `DM-01M7B1Q77QRB2PNDG0CDBNVVH3` | Term ids are minted from surface forms, and the map is recorded |
| `DM-01M7B1QF8FQ38JNYVARN95AQ9F` | Context and term ids use the kernel's letter-first slug grammar |
| `DM-01M7B85ADQMWP9ZEG99MXFGJKF` | A context overrides across levels whole, with no per-term merge |
| `DM-01M7CEZ6V0F8XD95NP8Q5E88KV` | ADR-0049 decision 13's load warning is retired (ADR-0076 decision 2) |
| `DM-01M7CEZC44PK37ZHTR82P9N1VV` | The words-to-avoid projection is spelled `…/vocabulary/taboos`, a noun |
| `DM-01M7B8570B11V5ZNYA65Y1GVS1`, `DM-01M7B85D68EEHK3TTE0DCTA8RT` | The vocabulary reads live under `/activations/{activationId}/vocabulary/`; Contextive answers `application/yaml` |
| `DM-01M7CEZ19K8QRA3KX68KRWX22G` | `ProjectCharterYamlSchemaTest` stops validating this repository's core-written charter file |
| Operator, 2026-11-14 | A `see_also` entry is a term reference that may carry a pack qualifier: bare for the same pack, `<pack>::term:…` for another; ADR-0049 carries the annotation |
| Operator, 2026-11-14 | `main` is integrated by rebase; every commit re-signed (R16) |

Other calls recorded in the records:

- Contextive answers `application/yaml` with no `charset` parameter, since RFC 9512 registers none, and a UTF-8 body.
  The contract declares the `406`.
- The vocabulary lists link `activation`, not `up` (ADR-0076 decisions 5 and 7). `hypermedia-hateoas.md` says why.
- Terms, contexts and taboo entries are ordered in Unicode code point order: case-sensitive, with no locale.
- `getActivation` gains four links within `EffectiveTag`'s recipe `v3`, since no release served a `v3` tag without
  them.

## Deviations from the plan

- **`activation_invalid`, not `validation_failed`.** A write that sends `selected.glossary_pack` or
  `deactivated.glossary_pack` is `422 activation_invalid` with an `unknown_member` violation, as for any undeclared
  member (`activation-register.feature`). Research R2 first said `validation_failed`; WP03 corrected it.
- **`packId` is optional in `GlossaryContextList`.** The schema serves both trees: present in a pack, absent under an
  activation, where each item names its own pack (as `SkillList` does).
- **The effective list items carry no state.** `listEffectiveGlossaryContexts` lists every winner whatever its
  state, unpaged; a client that wants the state reads `listEffectiveArtifacts?kind=glossary`. The context and term
  reads carry the state.

## Mutation results

| Module | Result | Floor |
| --- | --- | --- |
| `artifacts-edge` (WP04) | 87.5% | 85 |
| `packs`, WP03's changed lines (scoped) | 29 of 32 killed; 3 survivors are defensive conjuncts | 85 |
| `artifacts-edge` (WP06) | 90%, the new classes 100% | 85 |
| `activations` (WP06, narrowed) | 98% | 85 |
| The two modules WP07 touched | 91.5% and 92.6% | 85 |

WP02 did not run mutation testing locally (it is not in `verifyAll`). WP05 did not re-run the full `activations`
module. The orchestrator verifies both floors at landing.

## Left for consolidation and landing

- **The packs CHANGELOG.** #559 (PR #568) landed on `main` after this branch's base, with
  `contracts/packs/CHANGELOG.md`. The branch has no such file, so the entry is drafted here. Packs contract
  `0.1.0-SNAPSHOT` is not released, so it goes under `### Changed`; if `0.1.0` is released first, use `### Breaking`
  in the next version:

  ```markdown
  - The artifact kind `glossary_pack` is replaced by `glossary` (#106; ADR-0049, ADR-0076): `glossary.schema.json`
    replaces `glossary-pack.schema.json`, and a glossary context lives at `glossaries/<id>.glossary.yaml`. A
    `*.glossary-pack.yaml` file is refused by name as retired. `file-kinds.yaml` gains the node kinds
    `glossary_context` and `term`, the artifact kind `glossary` and the fragment plural `glossaries`; the graph-shard,
    org-fragment, preset (`activated_glossaries`), charter (`selected_glossaries`, `activated_glossaries`),
    pack-manifest, policy and mission-step-contract schemas follow. There is no reader of the old shape.
  ```

- **`contracts/packs/conformance/classifier-cases.yaml` on `main`** maps `core/glossary/a.glossary-pack.yaml` to kind
  `glossary-pack`. After the rebase that case must expect `retired`, and a mapped case for
  `**/*.glossary.yaml` is added, or the classifier conformance run fails.
- **The pack schema page.** `docs/architecture/pack-schemas/glossary.md` replaces `glossary-pack.md` and names
  `schemas/glossary.schema.json`. The site's `stage-docs-inputs` check passes only once the lanes are consolidated;
  `lane-f` alone still has the old page, which names a schema it deleted.
- **The open-packs placeholder** `open-packs#TBD-glossary-contexts` is in the draft, the change fragment
  `2026-11-14-glossary-replaces-glossary-pack.md`, the work breakdown (P5) and the roadmap's A2 row.

## For the orchestrator

1. Tick #106 in #536, and close #536 with the PR (its last box).
2. Tick R12 in #94.
3. File `open-packs-draft-glossary-contexts.md` in `spec-kitty/spec-kitty-open-packs`, and replace the placeholder
   everywhere it is listed above.
4. Comment on #518:

   > Charter #106 replaced `glossary_pack` with glossary contexts (ADR-0049, ADR-0076). The fields this issue names,
   > `banned_synonyms` and `synonyms_to_avoid`, are gone: each term has one list of words not to use, `avoid`. A
   > glossary evaluator does not keep its own list. It reads the taboo list of the vocabulary in force,
   > `GET /activations/{activationId}/vocabulary/taboos`: one entry per avoided word per term, with the URN and name
   > of the term to use instead. The terms themselves are `GET /activations/{activationId}/vocabulary/terms`. This
   > issue stays a core consumer, post-cutover.

## Evidence per issue

| Issue | Evidence |
| --- | --- |
| #106 | `contracts/charter/scenarios/glossary.feature` (the pack reads); `vocabulary.feature` (the activation's vocabulary); `docs/api/service-contract.md`, the R12 rows (#106's criterion) |
| #536 | Its glossary clause: `contracts/charter/scenarios/effective-glossary.feature`; every new operation `ready` in the switch-gate register |
| #94 | R12 built: the service contract's planned-read table, row "Assets (R11), glossary contexts and terms (R12), pack skills (R13)" |
| #142 | Work breakdown item 4 marked done |
