# Squad: post-plan review

This review ran on 2026-11-13 against plan `545331b1`. Three read-only lenses took part:

- domain and architecture;
- contract and tests;
- brownfield, migration and sequencing.

The operator's rulings were fixed; the lenses contested only how the plan carries them out. All BLOCKER and MAJOR
findings are folded into the plan, the spec and the data model. MEDIUM findings are folded where they are in scope.
No finding is filed as an issue (issue-tracker.md, Rule 1).

| # | Lens | Severity | Finding | Disposition |
|---|---|---|---|---|
| D-B1 | domain | BLOCKER | A factory typed by source group × target group lets disallowed pairs compile, because `requires` and `enhances` are unions of rectangles. Overloads erase to the same signature. | Folded: one named factory per rectangle, and `KindRef` built only through leaf factories (plan IC-01). |
| D-M1 | domain | MAJOR | `Relationship`'s constructors are public, and 7 main sites build one from untyped URNs. | Folded: the constructor becomes package-private, all 7 sites are routed, and an ArchUnit rule guards it. |
| D-M2 | domain | MAJOR | The table would have two sources, the sealed groups and `RelationType`. | Folded: `RelationType` is the source, checked by a reflection agreement test with a skewed positive control. |
| D-M3 | domain | MAJOR | `PackPolicy` cannot depend on `missions.domain`, and the load path has no closure. | Folded: `PackContents.MissionTypeFacts` for the in-pack check; qualified candidates are checked at compose (FR-005 reworded). |
| D-M4 | domain | MAJOR | A delegation kind that is "activatable" can never pass the `scope` rule. | Folded: the delegated kind is limited to the `scope` targets from `action`. |
| D-M5 | domain | MAJOR | The census counted mission-type edges as covering a delegation. | Folded: 80 edges are missing, not 72. The coverage note and the draft sizes are corrected. |
| D-M6 | domain | MEDIUM | A `requires` edge that is repointed at compose goes stale. | Folded: a compose-time check on the winning type (IC-02). |
| D-M7 | domain | MEDIUM | `NodeRef` already exists in activations. | Folded: renamed `KindRef<K>`. |
| D-M8 | domain | MEDIUM | The wire drops the candidate's pack, and states the kind twice. | Folded: candidates are written `[<pack>::]<id>`, with the kind given once. |
| D-M9 | domain | MEDIUM | Tension falls outside the bridge and the read. | Folded: `bindTension`, and an `in_tension_with` row with `heldAs: tension`. |
| D-M10 | domain | MEDIUM | New `scope` edges change the effective step context. | Folded: added to the risks, the ADR consequence and a scenario. |
| D-m | domain | MINOR | `Relation` has 0 callers in main code, so it is deleted rather than merged. The `RelationshipMerge` impacts rule must go too. Pairs should use the enum. | Folded. |
| T-B1 | contract | BLOCKER | An `ETag` equal to the contract version breaks the `ContractETag` pattern, and the Charter headers are missing. | Folded: the `ETag` is the sha256 of the body, and the headers are declared. |
| T-B2 | contract | BLOCKER | Leaving out the fingerprint fails a lint rule. | Folded: the path is added to both rules' exceptions, and the conventions doc is updated. |
| T-B3 | contract | BLOCKER | `mission_step_unlinked` turns the inline scenario packs red. | Folded: IC-00 migrates every inline pack and fixture first. |
| T-M4 | contract | MAJOR | The violation codes are registered inconsistently. | Folded: all are registered C's way, `ExampleCoverageTest` is extended, and `DUPLICATE_STEP_ID` is retargeted. |
| T-M5 | contract | MAJOR | The `/relation` pointer does not exist. | Folded: `/type`, with C's fault keeping `/from`. The read pointer is the graph document, as C's is. |
| T-M6 | contract | MAJOR | The delegation-candidate schema regresses. | Folded: the components are kept and renamed, with `Links.yaml` and `effective-artifact`. |
| T-M7 | contract | MAJOR | The hypermedia side is incomplete. | Folded: a `kinds` link on `Offering` and the root, templated `artifacts` links, and examples. |
| T-M8 | contract | MAJOR | The removal list misses step-contract mentions. | Folded into IC-03's list. |
| T-9 to T-13 | contract | MEDIUM | Switch gate and `every-operation` rows; retired routes pinned; enums, `x-source` and descriptions; one fault per pack; a scan of `contracts/`. | Folded (IC-03, IC-04). |
| T-14, T-15 | contract | MINOR | The tag description; the `impacts` strength in the graph schemas. | Folded. |
| M-B1 | migration | BLOCKER | About 34 valid-path edges in scenarios and fixtures are refused by the table. | Folded: IC-00 rewrites them, and the census is re-run over the scenarios and fixtures. |
| M-B2 | migration | BLOCKER | The example pack's delegations do not resolve, and IC-03 cannot land on its own. | Folded: IC-00 repoints candidates and adds `scope` edges. IC-03 and IC-04 merge. |
| M-M3 | migration | MAJOR | IC-01 and IC-02 were split so that `verifyAll` goes red. | Folded: content first (IC-00), then the model and its refusal in one work package. |
| M-M4 | migration | MAJOR | Slice C is already on `main`, and the plan cited a stale ref. | Folded: `main` merged at `ffb6b4929`, references moved to `main`, C's scenario rewritten, and C's step-contract code added to the removals. |
| M-M5 | migration | MAJOR | The fault order was fixed only for `overrides`. | Folded: `StepEnhancementRules`, then `OverrideSourceRule`, then the table. |
| M-M6 | migration | MAJOR | The open-packs drafts were undersized. | Folded: 26 `requires` edges with 23 nodes, 80 `scope` edges, qualified cross-pack candidates, and the lifecycle and guard authored. |
| M-7 | migration | MEDIUM | The example pack already has the lifecycle transition. | Folded: a guard file and a lifecycle entry. |
| M-8 | migration | MEDIUM | The example pack lacks 8 `requires` edges. | Folded into IC-00. |
| M-9 | migration | MINOR | `packs/qa` and the order of open-packs pin bumps. | Folded: a note in IC-05. |
| M-10 | migration | MINOR | The manifest hashes must be regenerated. | Folded into IC-00. |

No finding needed a new operator ruling. Each is about how a ruling is carried out.
