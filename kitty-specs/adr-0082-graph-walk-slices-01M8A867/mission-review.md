# Mission review: ADR-0082 slices 2 to 4 (adr-0082-graph-walk-slices-01M8A867)

Reviewed on 2026-11-28 by `claude::java-jenny`, against `main` at 06cdfbe94.

## Verdict

**Pass, with follow-ups.**

- All thirteen functional requirements are met on `main`, as the acceptance matrix records.
- NFR-001 to NFR-003 hold.
- The WPs reached `done` by an operator ruling, not by `consolidate`.
- The machine report (`mission-review-report.md`) fails only its dead-code scan, for want of a `baseline_merge_commit`. That field is written only by `consolidate`, so the gap is spec-kitty/spec-kitty#7903, not a code finding.

## Delivery

| Slice | Issue | WPs | PR | Landed on `main` |
|---|---|---|---|---|
| 2: `PackGraph` holds every pack-graph walk | #637 | WP01 | #665 | fb1f0c827 |
| 3: typed nodes, the driver, the walker, the own-closure rules | #638 | WP02, WP03 | #674 | 8c943a70a |
| 4: relation and edge-index refusals as rules; REFERENCES on a walk | #639, #667 | WP04, WP05, WP07 | #683 | merged 2026-11-28 07:03 |
| #632 item 3: pack-doctor judges the relations | #632 (part) | WP06 | #684 | 06cdfbe94 |

The operator landed each PR with landing changes, for example:

- the `ancestorsOf` map overload;
- `members.put` and the `declaring_pack_visited` guard;
- `PackGraph.ofPacks` taking the closure's facts;
- the `test(landing)` commits.

So `main` differs from the lane tips. The acceptance evidence was re-verified on `main` itself.

## FR trace

Each FR's tests, the PR and the verification are recorded in `acceptance-matrix.json`: all pass, re-run on `main` 06cdfbe94, exit 0.

| FR | Where it is pinned |
|---|---|
| FR-001 to FR-003 | `PackGraphTest`, `PackGraphWalkBoundaryTest` and its bad fixture |
| FR-004 to FR-006 | `PackGraphWalkTest`, `ValidationWalkerTest`, `OwnClosureRulesTest`, `ClosureImpactCheckTest` |
| FR-007 | `ReferencesWalkTest`; the `ONLY_A_GRAPH_WALK_FEEDS_THE_WALKER` ArchUnit rule (WP07, operator ruling 01M8DT4CGSQZBPJCC6K9CN3ZX4) |
| FR-008 to FR-011 | `RelationRulesTest`, `RelationRulesEnhancementTest`, `EdgeIndexTest`, `ClosureViewMergedTest`, `LayerMergeTest`, `RefusalHandlerTest`, the contract scenarios |
| FR-012 | `RelationsPhaseTest`, `FullPassParityTest` (nine relation and policy rows) |
| FR-013 | `ResolverDeterminismTest`, order pins; no hash-ordered collection added |

## Non-functional requirements

- **NFR-001 (unchanged answers).** The only changes are those the operator ruled:
  - D2, Q-A, Q-B and Q-C (FR-011, recorded under `### Breaking` in `contracts/charter/changes/2026-11-26-edge-index-refusal-codes.md`);
  - the new pack-doctor `RELATIONS` check (contract fragment 2026-11-27).

  The WP05 review found one behaviour change no ruling covered (F1: a merge's own `pack_sibling_conflict` refusing a write). It was reverted to the old behaviour, which keeps the standing rule.
- **NFR-002 (no stack exhaustion).** The 200,000-node chain tests stay in `PackGraphTest` and `PackGraphWalkTest`.
- **NFR-003 (cost).** `PackRegistrationScaleTest`, five alternating runs, medians:
  - #674: +5%, +8% and −1%;
  - #683: −7% to +2%.

  All are within 10%, and the operator noted the #683 range as fine.

## Drift from the plan

- **WP07 was added mid-mission** (decision 01M8DT4CGSQZBPJCC6K9CN3ZX4). Main's landing of #638 had promised that REFERENCES would move onto a graph walk in slice 4. WP07 reuses `Walk.PACKS` as the members mode rather than adding a duplicate constant, which the WP prompt allows.
- **WP06's PR stacked on #683.** Lane-f does not hold WP07, so the merge with WP07 (review finding F5) was resolved on the PR branch only.
- **The edge-index phase is not in pack-doctor.** That gap is pinned by a parity row and left to #636 slice 7.

## Risks and follow-ups

- **spec-kitty/spec-kitty#7903.** There is no closeout path for a mission whose slices land on `main` by PR. The recorded target `feat/adr-0082-graph-walk-slices` is stale, so the WPs reached `done` by a forced move under the operator ruling of 2026-11-28.
- **spec-kitty/spec-kitty#7902.** A claim by the generic actor `user` leaves no implementer of record. WP01's `REVIEW_NOT_INDEPENDENT` cannot be lifted.
- **spec-kitty/spec-kitty#5197.** CLI commits are unsigned. Re-signing them moved the approval stamps off the lanes (`APPROVAL_STAMP_NOT_ON_LANE`). Lane-a and lane-e still carry unsigned CLI commits that were pushed before re-signing.
- **#675.** `notPermitted` names a pack in hash order on a tie. This was on `main` before the mission and is left out of it because the fix changes behaviour.
- **#682.** A `RegistrationJobsTest` deadline race, seen once locally; #681 fixes it.
- **doc-kitty.** It may match `pack_unreadable` on merge-time read refusals, which now answer their own codes. This is flagged in the handover.
- **#632** stays open for its edge-index and own-closure parts (#636 slice 7).

## Security

No new input surface. The changes judge pack content already parsed and bounded by the existing readers. The new refusals narrow what registration accepts.

## Reviews

- An in-session adversarial review ran on every WP, three rounds on WP05. Every finding was folded, and each was posted on its PR.
- Every WP was approved through the CLI by `claude::reviewer-renata`, an in-session reviewer the operator authorized.
