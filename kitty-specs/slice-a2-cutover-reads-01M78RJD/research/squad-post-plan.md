# Post-plan brownfield squad: slice A2

Run 2026-11-12 against plan commit `3a31708`. Three read-only, profile-loaded delegates:

- **architect-alphonso.** Verdict: not sound as written (1 BLOCKER, 4 MAJOR).
- **researcher-robbie.** Verdict: pass with changes (3 MAJOR).
- **randy-reducer.** Verdict: proceed with folds (1 MAJOR).

In every delegate, the charter context loaded degraded: packs `internal` and `qa` are absent on disk, a known and accepted gap (charter.md). Each delegate applied the profile's directives and no tactics.

## Findings and dispositions

| # | Sev | Finding (lens) | Disposition | Evidence |
| --- | --- | --- | --- | --- |
| 1 | BLOCKER | The effective asset and skill controllers cannot live in `:activations`. `ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER` forbids naming `packs.domain` `Asset`/`Skill` outside `activations.infra.packs` (architect). | **accepted** | plan.md IC-00: a new edge module `:artifacts-edge`, modelled on `:missions-edge`, holds both trees' asset and skill reads and the references twin, with one body mapper. research.md R13. |
| 2 | MAJOR | Adding ASSET and SKILL to `LISTED` makes `DELETE …/{asset\|skill}/{id}` live. An asset delete would orphan its payload (architect, researcher). | **changed** | research.md R14: removal keeps its own `REMOVABLE` set (today's kinds), so a DELETE of an asset or skill answers as today. A scenario pins it. Asset and skill removal is "Not done". |
| 3 | MAJOR | The references twin over `MergedView.edges` answers `ArtifactReferenceList` by a second rule (architect, researcher). | **accepted** | R10 rewritten: the twin is the pack read (`ArtifactReadService.references`) of the winner's pack, verified against the session fingerprint, plus `effective-artifact` links. A parity scenario backs it. |
| 4 | MAJOR | R3 overrides Accepted ADR-0074 d9 and Approved FR-011 by a "status note" (architect, reducer). | **accepted** | FR-011 amended. ADR-0074 gets a dated amendment section for d9 (asset under an activation), citing packs decision `01M702A71YF7WNDTG2AA89K8ZA`. It is logged as an open review item for the operator check-in (charter: "a call made without review is logged"). |
| 5 | MAJOR | The effective payload has no verified byte read (architect). | **accepted** | R15: the missions-edge pattern. Activations answers winner, pack and fingerprint; packs reads the bytes in a `ConsistentRead` checked against that fingerprint, and a mismatch is `500 stale_input`. A unit test covers it. |
| 6 | MAJOR | `AssetReadService` and `SkillReadService` duplicate `ForeignArtifactRead` (reducer). | **accepted** | IC-03/IC-04: the items ride `ForeignArtifactRead.read`; `held(pack, kind)` and `payload(pack, assetId)` are added beside it. |
| 7 | MAJOR | `heldWinner` duplicates `ClosureView.winnerPackOf` (researcher, architect). | **accepted** | R2: the item uses `winnerPackOf` / `effectiveArtifact`. Only a held-assets listing is new. |
| 8 | MAJOR | The payload bytes are already in the aggregate (`Pack.assetFiles()`), and `getPackDocument` is the non-JSON pattern (researcher). | **accepted** | IC-03: modelled on `PackRegistrationController#getPackDocument` and `PackDocumentETag` (ETag, 304, `*`), as a lookup of `asset.path()` in the same read. |
| 9 | MEDIUM | Two MIME rules (architect, reducer) versus "the rule must stay the validate rule" (researcher). | **deferred_with_rationale** (adjudicated) | R6: one domain rule, the existing `mimeProblem` slash rule, decides "well-formed" for validate and for the payload. Tightening it to RFC 6838 would make `asset_mime_invalid` (BLOCKING) refuse packs that register today, which ADR-0074 rejected ("refuse a malformed mime at load"). The infra transport falls back to octet-stream only when Spring cannot emit the header. That is a transport limit, not a second judgment, and it is documented in the path file. |
| 10 | MEDIUM | `EffectiveAssetRead` should keep `policy`; `kind` must not be `ActivatableKind` (architect). | **accepted** | data-model.md. |
| 11 | MEDIUM | Asset id pattern: copy the skill pattern exactly (with the newline guard) and use `IdentifierGrammar.isLetterFirstSlug` (architect, researcher). | **accepted** | R11. |
| 12 | MEDIUM | Relation table: `packs` would take two meanings; the write rows' "On" column changes; the lint ruleset enum must match (`RelationTableParityTest`) (architect, researcher, reducer). | **accepted** | IC-07: the `packs` row is rewritten for both placements; the write rows are rewritten; `unselect` and `include` are dropped from the activation (reducer); `edit` row; ruleset enum. |
| 13 | MEDIUM | The payload contract shape is unspecified: headers, Accept, 304 (architect). | **accepted** | IC-03. |
| 14 | MEDIUM | `Preset.description` is required; reuse the `PresetFiles`/`PresetYaml` kind naming (researcher). | **accepted** | data-model.md, R7. |
| 15 | MEDIUM | SC-002 does not hold when `executable.entry` names another payload file (ADR-0054) (researcher). | **changed** | SC-002 narrowed to the asset's own payload. The gap is listed under "Not done" in the PR. |
| 16 | MEDIUM | FR-011's templated refusal cannot be reached over HTTP (literal routes win); the activation-side templated read needs a gate (researcher, reducer). | **accepted** | FR-011 amended. The activation templated read gates on activatable ∩ listed. The pack-tree refusal is proven by unit tests and a routing test. |
| 17 | MEDIUM | `ITEM_KINDS` is derived from `SERVED`: adding kinds puts them in the item switch, which answers 500 (researcher, reducer). | **accepted** | IC-02: one `LITERAL_ITEM_KINDS` set, and `ITEM_KINDS = LISTED − LITERAL`. |
| 18 | MINOR | `payload` and `collection` links go on the `Artifact` envelope (architect). | **accepted** | data-model.md. |
| 19 | MINOR | New `EffectiveTag.Read` values; the ETag/304 choice (architect). | **accepted** | IC-05. |
| 20 | MINOR | Pinned tests and scenario text to update: `artifacts.feature:290`, `ClosureViewSessionArtifactTest:248`, `ForeignArtifactReadTest:237`, `ActivationRoutingTest` (architect, researcher). | **accepted** | IC-08 "Touches". |
| 21 | MINOR | Unpaged lists (reducer). | **accepted** | R7–R9 record it as deliberate. |
| 22 | MINOR | List the skill fields left out (researcher). | **accepted** | R8. |
| 23 | MINOR | Reuse the `ArtifactReadService.Reference` record and mapper for the twin (reducer). | **accepted** (folded into #3) | R10. |
| 24 | MINOR | Keep `PresetReadService` thin, on `ConsistentRead` (reducer). | **accepted** | IC-01. |
