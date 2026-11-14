# Plan-point squad: R12 glossary contexts and terms

**Point-cut**: post-plan, 2026-11-13. Run against the plan at `bf30c991`, then folded on 2026-11-14.
**Question**: do the plan and ADR-0076 (Proposed) deliver the spec coherently, with every code surface named, with no
split-brain against the Accepted ADRs and conventions, and with proofs a do-nothing change cannot pass?

**Cast**: three distinct lenses, dispatched in parallel. Each loaded its profile, all read-only.

| Lens | Profile | Verdict |
| --- | --- | --- |
| Architecture and contract consistency | `architect-alphonso` | SOUND WITH FIXES |
| Brownfield code reality | `java-jenny` | SOUND WITH FIXES |
| Spec-to-plan fidelity and non-vacuity | `analyst-annie` | SOUND WITH FIXES |

The lenses did not disagree on any consequential point. Two findings converged across lenses:

- the lost "load warns" (architect H2 and analyst H1);
- the 172-versus-139 count (analyst H2 and architect).

## Dispositions

**Accepted**: folded into the plan, research, data model, spec or ADR-0076. **Ruled**: the operator decided it.
**Recorded**: kept as written, with the reason. No finding was dropped, and none was filed as an issue (issue-tracker
Rule 1).

### Architect lens

| # | Sev | Finding | Disposition |
| --- | --- | --- | --- |
| A-H1 | HIGH | The `terms` and `avoid` bodies carry no `_links`, against the lint rules. | Accepted: `_links` on both lists and on each `ActiveTerm` (ADR-0076 d5, d7; research R6; data model). |
| A-H2 | HIGH | FR-015's "the load warns" is lost under whole-context override. | Ruled `DM-01M7CEZ6V0F8XD95NP8Q5E88KV`: the warning is retired. ADR-0076 d2, FR-015 and research R5 say so; ADR-0049 is annotated (IC-01). |
| A-M1 | MED | Two fragment plurals (`glossaries` in FR-004, `glossary_contexts` in R1). | Accepted: `glossaries: glossary_context`, the artifact kind's plural (research R1). |
| A-M2 | MED | `term`'s graph admission is undefined, and `TERM.plural()` falls through to `actions`. | Accepted: per-constant flags in the explicit `NodeKind` table; a dangling `term:` target is a broken reference (research R1; data model). |
| A-M3 | MED | The `…/vocabulary/avoid` leaf is a verb. | Ruled `DM-01M7CEZC44PK37ZHTR82P9N1VV`: `taboos` (ADR-0076 d4). |
| A-M4 | MED | No relation names are chosen for the twins and the vocabulary links. | Accepted: `glossaries`, templated `term`, and `vocabulary-terms`, `vocabulary-contextive` and `vocabulary-taboos`. The relation table is an IC-05 and IC-06 surface (ADR-0076 d9). |
| A-M5 | MED | The YAML answer's obligations are left open: 406, the fingerprint header, refusals before 304, the media-type rule. | Accepted: ADR-0076 d6 and d8. The rest-resource-conventions exception is an IC-06 surface. |
| A-L1 | LOW | The "exception to ADR-0074 d12" is mislabelled. | Accepted: reworded in ADR-0076 d4 and the Negative consequences. |
| A-L2 | LOW | The sibling refusal cites ADR-0048 d9 only. | Accepted: it cites composition rules C14 and C15. |
| A-L3 | LOW | The term refusal uses a generic code. | Recorded: an operator ruling (`DM-01M7B1Q4BPQB95EVQBRWN4Q4F8`), noted in ADR-0076's Negative consequences. |
| A-L4 | LOW | Contextive `paths` is not mapped. | Accepted: `applies_to.paths` → context `paths` (ADR-0076 d6). |

### Java lens

| # | Sev | Finding | Disposition |
| --- | --- | --- | --- |
| J-H1 | HIGH | URN kind token used as an artifact kind in profiles-edge and `EffectiveBodies`, giving a latent 500 and dead links. | Accepted: mapped through `NodeKind.artifactKind()`, with a governance test that has a `vocabulary` edge (research R1; IC-02). |
| J-H2 | HIGH | `NodeKind.plural()` and `fromPlural()` still derive from `ArtifactKind`. | Accepted: an explicit plural per constant (research R1). |
| J-H3 | HIGH | `term` conflicts with the authored-in-graphs parity pins. | Accepted: the predicate splits into node-declarable and edge-target flags, each checked against its own surface (research R1). |
| J-H4 | HIGH | The concern order does not compile. | Accepted: the kernel, packs contract, domain and charter contract are one atomic concern (IC-02). |
| J-H5 | HIGH | `.kittify/charter/charter.yaml` fails `ProjectCharterYamlSchemaTest`. | Ruled `DM-01M7CEZ19K8QRA3KX68KRWX22G`: the test narrows, and the file is not edited (research R14; IC-02). |
| J-M1 | MED | No vocabulary port or read is named, and NFR-005 conflicts with a multi-pack read. | Accepted: `ActivationArtifacts.contextsInForce` and `PacksVocabulary`. NFR-005 is restated as one session plus one read per winning pack, counted (research R6; IC-06). |
| J-M2 | MED | The YAML dependency claim is wrong, and the precedent is missed. | Accepted: `snakeyaml-engine` (already verified) is added to `:artifacts-edge`; the `/openapi.yaml` precedent is cited (research R6). |
| J-M3 | MED | Slice D's complexity gate and the mutation floors are not named. | Accepted: research R15 and plan risk 3. |
| J-L1 | LOW | Smaller gaps: the relation table, the usability script, four tests, the `EffectiveTag` guards, the term query ids. | Accepted: each is named in IC-02, IC-05 or IC-06. |

### Analyst lens

| # | Sev | Finding | Disposition |
| --- | --- | --- | --- |
| N-H1 | HIGH | Story 4 scenario 3 cannot tell a whole override from a per-term merge, and FR-015 is vacuous. | Accepted: a discriminating scenario (`lane` out of force) with its positive control; FR-015 reworded. |
| N-H2 | HIGH | NFR-005 says 172 terms against a measured 139, and an undefined budget. | Accepted: 139 terms, one session plus one read per winning pack, counted by a test. |
| N-H3 | HIGH | No concern builds the stories' scenario fixtures. | Accepted: the scenario packs are listed in IC-03. |
| N-M1 | MED | "A Contextive tool accepts it" is not what is proven. | Accepted: story 4 scenario 4 states the structural field-set check. |
| N-M2 | MED | SC-004's "one to one" cannot hold for the taboo list. | Accepted: restated (Contextive is exact; taboos are the flattened `avoid` entries), with one cross-read scenario. |
| N-M3 | MED | NFR-002's scope against the occurrence map's exceptions and spellings. | Accepted: NFR-002 is scoped to the tracked `contracts/` and `service/` trees, and the map gains the refusal-test paths and names the four spellings. |
| N-M4 | MED | The closeout duties are incomplete. | Accepted: IC-07's closeout checklist. |
| N-M5 | MED | The `site/.contextive` ruling against the map's edit. | Accepted: `do_not_change` in the occurrence map. |
| N-M6 | MED | ADR-0076 d8 and d9 have no FR or scenario. | Accepted: FR-013 and FR-014 widened; IC-06's proof list. |
| N-L1 | LOW | SC-001 is proven by one term. | Accepted: a test iterates all 139 ids (IC-04). |
| N-L2 | LOW | The `see_also` and sibling edge cases have no test. | Accepted: the planted `see_also` case (IC-03); the sibling refusal is an existing composition check, re-run on a glossary fixture in IC-05. |
| N-L3 | LOW | The preset path prose is missing from the plan. | Accepted: listed in IC-02. |
