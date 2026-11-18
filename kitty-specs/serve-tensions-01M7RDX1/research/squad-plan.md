# Plan-point adversarial squad (2026-11-18)

Three independent lenses read the spec, the plan, ADR-0045 and the code: **domain** (D), **contract** (C) and
**tests** (T). Findings below MEDIUM are folded or dropped, as the charter says. Every finding has a disposition.

## Sent to the operator (rulings pending)

| ID | Finding | Lenses | Recommendation |
| --- | --- | --- | --- |
| G1 | Canonical storage with a foreign smaller side puts a foreign artifact as the edge's source. Then the gate, the answer, removal, inbound references and the references listing all key on the wrong pack. | D2, D3, C5, C10, T8 | Stored orientation: the side the pack owns is the source; when the pack owns both, the smaller bare URN. The reads answer the sorted pair. |
| G2 | Whether a tension survives an override depends on which URN sorts first (EdgeIndex drops on a losing source, re-points on a losing target). | D1, T3 | Symmetric: absent when the authoring pack's own version of either side loses; re-point a side it does not hold. |
| G3 | "resolutions always both" advertises an exclusion that is refused for a locked or required side. | C2 | Omit the exclude link for such a side; list `deactivate_one_side` only when a link is present. |

## Folded into the plan and ADR-0045

| Finding | Severity | Disposition |
| --- | --- | --- |
| C1: versioning classification wrong | HIGH | Folded. `tension` in `Relation`, the edge fields and the required context member go under `### Breaking`; the op and the link under `### Added`; the parameter under `### Changed`. |
| C3: link relation names clash | HIGH | Folded. `exclude-source`, `exclude-target` (PUT, If-Match = the answer's revision), `effective-source`, `effective-target`; table, ruleset and parity test. |
| C4: conditional fields invisible to clients | HIGH | Folded. A documented `oneOf` on EffectiveGraphEdge (as Artifact.yaml); `reconciledBy` unique and sorted; `x-derived` on each property; ContextTension shares the definitions. |
| T1: activations are not pack content; case 4 vacuous | HIGH | Folded. Five inline activation documents in `tensions.feature`; case 4 PUTs the reverse order and asserts the stored shard; the pack is writable. |
| T2: a registration can duplicate a hand-written tension and brick the pack | HIGH | Folded. 422 `tension_duplicated` on the write. |
| T4: example-pack enumeration tests | HIGH | Folded. Listed in the example-pack WP; expectations updated, never narrowed. Tensions not added to `built-in`. |
| T5: graph assertions blind to the words | HIGH | Folded. A step that prints `reconciledBy`, `when` and `reason`; case 5 asserts none on the `requires` edge. |
| C6, D4: removal by relation | MEDIUM | Folded. Judged per pair class; unknown `relation` is 400; absent with an authored structural edge and a registered tension is 409 `relationship_not_registered` naming `relation`. |
| C7, T11: ETag | MEDIUM | Folded. Its own `Read.TENSIONS`, the skipped report's inputs; domain v4 (v3 was released), EffectiveTagTest twins. |
| C8, T9: operation rows and switch gate | MEDIUM | Folded. Every declared status asserted; the new `relation` behaviours asserted, so `deletePackRelationship` and `getEffectiveGraph` stay truly ready. |
| C9: `_links` required list | MEDIUM | Folded. `$ref: Links.yaml`; relations named in prose and asserted; ActivationTensions carries `self` only. |
| D5, T8: a tension between two versions of one artifact | MEDIUM | Folded. Refused: load code `tension_self` and a wire refusal; TensionsInForce drops a resolved self-pair. |
| D6: GraphProjection's distinct no longer collapses | MEDIUM | Folded. Tension edges come only from TensionsInForce; merge order defined (by closure writer order, then document order). |
| D7: activations-side domain types | MEDIUM | Folded. `TensionSnapshot` in `activations.domain`; `ClosureView.tensions(states)` public; the finding model in `activations.domain`. |
| D8: `samePair` does not exist; pair-only users | MEDIUM | Folded. Public `samePair`/`sameSlot` in the kernel; `ArtifactRemovalService:175` uses an identity filter; `DoctrineGraph.merge` upserts on the slot. |
| T6: depth cannot cut a directive side | MEDIUM | Folded. AS3 merges into AS4, with depth 0 and 1 on the same activation as twins. |
| T7: no-op passable checks | MEDIUM | Folded. AS5 paired with the tensions read; follow `exclude-source` and re-read; a permutation unit test for NFR-001. |
| T10: NFR-004 not measurable | MEDIUM | Folded. Restated as unchanged operation counts in `EffectiveReadOperationBudgetTest`, extended to the new read. |
| D9: round trip omits fromPack/toPack; ADR-0027 note; javadocs | LOW | Folded. |
| C11, C12: x-source, examples, fingerprint text | LOW | Folded. |
| T12: mutation floors | LOW | Folded. Kernel equality tests; TensionsInForce and mapper unit tests. |
