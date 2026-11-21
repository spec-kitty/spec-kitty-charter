# Pre-spec alignment and design squad: dispositions

Run 2026-11-21 at the operator's request, over ADR-0082 and docs/architecture/architectural-patterns-applied.md
(PR #634). Lenses: architect-alphonso, planner-priti, researcher-robbie. Verdicts: all ALIGNED-WITH-CHANGES.

| # | Finding (lens) | Disposition | Evidence |
|---|---|---|---|
| A1 | #629 and #632.1 are one cause: one rule, one fault per item (architect) | accepted | spec FR-002 |
| A2 | RequiredFloor stays the read model, not the rule (architect) | accepted | spec FR-003, US1.4 |
| A3 | The preset refusal does not go into the compose, which answers 500 on reads (architect) | accepted | FR-003 |
| A4 | `--full` reuses collect-all domain forms; SpecializationCycleRule moves to the domain (architect, researcher) | accepted | FR-006, C-001; plan |
| A5 | No new closure computation in pack-validation (architect) | accepted | NFR-003 |
| A6 | The cycle scope differs per surface; test a sibling-pack cycle (architect) | accepted | US3.3, edge case |
| A7 | manifest_stale at the storage edge; the census carries the stale fields (architect) | accepted | FR-004; plan |
| A8 | The round trip drops only source-stale, not by file dedupe (architect) | accepted | FR-005 |
| A9 | remediate is a storage-edge facade that judges nothing, behind the verdict (architect, researcher) | accepted | FR-009, US5.2 |
| A10 | Sequencing on PR #618 (architect, planner) | changed | #618 merged 2026-11-21T10:30Z; FR-001 is WP01 |
| A11 | Docs and ADR amendments; the patterns page scope rows (architect) | accepted | FR-012 |
| A12 | One report model, two serializers (architect) | accepted | FR-008 |
| P1 | One mission, but slice it (planner) | accepted | plan/tasks: ordered WPs in one PR (R-f) |
| P4 | Contract fragments: Breaking for refusals and the report rename (planner) | accepted | FR-011 |
| P5 | Rename blast radius, release scripts move together (planner) | accepted | FR-007; occurrence map in plan |
| P6/P7 | Example presets would go red (spdd tactics, default anti-pattern) (planner) | accepted, ruled R-l | FR-010 |
| P8/P9 | Example manifests and the whole-pack baseline (planner) | accepted | FR-010, SC-003 |
| P10 | Exclusions recorded (planner) | accepted | C-005 |
| P11 | open-packs#TBD drafts (planner) | accepted | C-006 |
| R-closure | The own closure differs from the composed closure (researcher) | changed, ruled R-k | Domain Language, Assumptions |
| R8 | "pack-doctor" echoes core's doctor and the #115 name (researcher) | deferred_with_rationale | Operator ruling R-c chose the name deliberately |
