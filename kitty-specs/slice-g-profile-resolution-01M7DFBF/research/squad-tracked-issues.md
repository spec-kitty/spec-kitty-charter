# Squad: tracked issues (pre-spec, 2026-11-14)

Lens: every open issue and PR that overlaps slice G (#538). Read: all 131 open issues and the open PRs, with term searches
(profile, role, sentinel, enhances, specializes_from, fallback_profile, alt-roles, human-in-charge, lineage, operator,
routing-priority, pin, policy.yaml, R8, agent_profile, performer). No close candidates were found.

## Disposition

| Issue | Disposition | Why |
| --- | --- | --- |
| #538 | IN (tracker) | Slice G. Its sub-issues are #42, #512, #380; #102's parent is #94, so #538 tracks it by checkbox. |
| #102 | IN | R8: decide and shape; record in `docs/api/service-contract.md`. Ticks #94 on close. |
| #42 | IN | Selection by role in ADR-0069's order; `fallback_profile_conflict` and `no_profile_matched` raised; one example pack per rule. |
| #512 | IN | A human performer marker replaces `sentinel` outright; an `operator` step never falls back. |
| #380 | IN | The `@WIP` placeholder becomes `@contract`; the drop question needs a ruling (see the research note). |
| #537, #520, #514, #515, PR #567 | DEPENDS (partly) | Slice F. Only the step-keyed reads and the typed relation API depend on it (research note, section 3). |
| #556 | ADJACENT, coordinate | ADR-0075 slice 7 moves `FallbackProfileRule` behind ports. G reuses that rule for `fallback_profile_conflict`; land order to be agreed. |
| #572 | FOLLOWS | Journey 12, scenario 5 names the review profile until G selects it by role. |
| #108 | FOLLOWS | R14 runs after G. |
| #61 | FOLLOWS | The `policy.yaml` how-to documents the role pin G defines. Not folded into G. |
| #205 | FOLLOWS | The profiles PlantUML refresh after G. Not folded. |
| #94, #69 | FOLLOWS (tick) | Tick R8 and slice G at closeout. |
| #55, #513, #519, #82, #461, #178, #339, #536, #142, #143, #478, #549 | ADJACENT | No action. #461's mutant may be killed by G's tests. |

Out of this repository: open-packs#139 (retire `human-in-charge`) follows #512; drafted with an `open-packs#TBD-<slug>`
placeholder, never filed from here.

## ADR numbers

`main` ends at 0075. 0076 is #106's (`0076-the-activation-vocabulary-reads-and-projections.md`, on its mission branch).
Slice F has written no ADR yet and should take 0077, so G plans **0078**, rechecked when the ADR is written.
