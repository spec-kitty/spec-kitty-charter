# Pre-spec squad: ADR-0082 slices 2 to 4

Point-cut: pre-spec. Question: does the brief for #637, #638 and #639 hold against the code on `main`, the charter,
AGENTS.md, ADR-0082 and ADR-0083 (ruling R-s), and how does it break into work packages?

Lenses, each profile-loaded and read-only: `planner-priti` (scope and breakdown), `researcher-robbie` (code
grounding on `origin/main` ebe6eca72), `architect-alphonso` (design and boundary alignment). A separate review of
commit 03e8ab92c (PR #665) checked behaviour equivalence walk by walk.

## Findings and disposition

| # | Sev | Finding | Disposition |
|---|---|---|---|
| S1 | HIGH | The order of findings decides the refusal code. Relations run rule first (every self-override, then every conflict, ...). A walker that orders subject first changes the first code. | Accepted for WP02: the walker keeps one bucket per rule and joins them in rule order. |
| S2 | HIGH | `PackRulesets.references()` hand-codes the same phase-gated loop the new walker provides: two mechanisms. | Accepted for WP02: `references()` runs on the walker. |
| S3 | HIGH | `EdgeIndex.merged()`'s refusals are read-time `500 pack_unreadable`; their registration twins answer other codes, and the stray step-link check has no registration twin. | Decision D2 (operator). |
| S4 | HIGH | #639 does not name `SpecializationCycleRule`, the domain subject replacing `ComposedClosure`, or `EdgeIndex`'s home (application, used by seven read classes). | Decision D4 (issue text); the design goes into the plan. |
| S5 | HIGH | #638's done-when names the composing read and pack-doctor; neither runs the own-closure rules today, and pack-doctor lacks the profile reader and merge (slice 7). | Decision D4. |
| S6 | HIGH | `Scope`, `PackGraphNode` and the visitor driver have no owning slice; the draft walker is a phase runner, not a `GraphWalker`. | Decision D1. |
| S7 | MED | `ownClosure(declaring, fallbackProfile, ...)` passes subject facts as factory arguments; the fallback predicate ignores its subject. | Accepted, subject to D1: `ClosureFacts` gains `declaring()` and `fallbackProfile()`. |
| S8 | MED | Gates: relations gate the edge index; preset items are reported ungated beside own-closure breaches. | Accepted: kept, pinned by red-first multi-fault fixtures (WP02 to WP04). |
| S9 | MED | Census and write path decorate details differently. | Accepted: both kept; the prefix move is slice 6. |
| S10 | MED | Five walks stay in `PackCompositionResolver` outside `PackGraph`; `activations.domain.ClosureSnapshot.extendsChain` walks the parent line in another module. | WP01: reworded to "the walks of the application layer"; the resolver's walks and `extendsChain` named in the PR as not moved, with the reason. |
| S11 | MED | `GraphWalk`, `Reachability`, `GovernanceWalk`, `ActionContextWalk` are artifact walks no slice owns. | Deferred with rationale: an epic #636 checklist line (outside slices 2 to 4). |
| S12 | MED | `PackGraph.reachable` lets a caller pick the edges. | WP01: named descents; the generic walk stays private. |
| S13 | MED | The walk-containment ArchUnit guard has no owner (#662 holds no ArchUnit test). | WP01 (dispatcher, 2026-11-25). |
| S14 | MED | #632 item 3 is not closed by slices 2 to 4. | Decision D3. |
| S15 | LOW | Gate on `VIOLATION` severity, as the architecture page says. | Accepted for WP02. |
| S16 | LOW | `PackGraph` imports `PackUnderResolution`, a class cycle with the resolver. | WP01: `compositionClosure` takes descriptors. |
| S17 | LOW | The #637 review's 13 LOW and NIT findings (docs overclaims, ADR-0083 line 96, test gaps, Javadoc wording, naming). | WP01: folded. |
| S18 | LOW | #659's exclusion still names the lifted hold. | Raised with the dispatcher. |

## Open decisions

D1 to D4 are recorded as Decision Moments of this Mission and were sent to the dispatcher on 2026-11-25.
