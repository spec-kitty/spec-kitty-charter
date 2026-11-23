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

## Post-plan squad (2026-11-21): architect-alphonso, randy-reducer, reviewer-renata

Verdicts: architect "split-brain in D3 and D5", reducer REVISE, reviewer "do not approve for tasking as written". All
findings dispositioned below; the plan is revised accordingly.

| # | Finding | Disposition | Evidence |
|---|---|---|---|
| PA1 | case-only has nothing to defer to in pack-doctor | accepted | plan D3: the preset phase computes caseOnlyMatches first |
| PA2 | `defined` mixes files and graph-only nodes | accepted | plan D3: `held`/`declared` replace `defined` |
| PA3 | WriteFacts rebuilds facts on writes | accepted | plan D3 |
| PA4 | a sibling specializes_from cycle is invisible per own closure | accepted; D5 held | plan D5, message to dispatcher |
| PA5 | `--full` is not "everything registration judges" | changed: proposal (a) narrows it, ruling pending | plan D5 |
| PA6 | moving SpecializationCycleRule drags ImpactRelations.cyclesIn (slice 2) | accepted | plan D5 (not moved in this mission unless ruled) |
| PA7 | name the ClosureImpactCheck call site; own-closure rules in two layers | accepted | plan D3 (walkFaults; slice-3 follow-up in ADR-0083) |
| PA8 | order of findings differs between surfaces | accepted | plan D3 order, D5 order |
| PA9 | wrong ArchUnit rule named; the precondition wording is split | accepted | plan D7 |
| PA10 | `stale()` emits missing entries; membership reasons are prose | accepted | plan D4 |
| PR1 | the report duplicates the problem model | accepted | plan D6, data-model |
| PR2 | an XSD plus a JSON Schema are two sources of truth | accepted (within R-n: own shape, not JUnit) | plan D6 |
| PR3 | remediate staging duplicates PackCommitStore | changed: reuse the staging helper only if it is framework-free and lock-free (architect: pack-doctor stays framework-free) | plan D7 |
| PR4 | the facade is thin | accepted | plan D7 (composes the existing pieces) |
| PR5 | the occurrence map is incomplete | accepted | occurrence_map.yaml exceptions |
| PR6 | check-packs.md path contradicts | accepted | plan IC-08, occurrence map |
| PR7 | `declared` duplicates facts | changed: `held`/`declared` replace `defined` outright (architect PA2 shows `defined` is wrong) | plan D3 |
| PR8 | twin collect-all and first-fault bodies | accepted | plan D5 (first of the collect-all form) |
| PR9 | the D9 hedge | accepted | plan D6 (checked in WP06 first, recorded) |
| PR10 | nothing droppable | noted | — |
| PV1 | A10 and the must_have scenarios cannot reach requiredNotHeld | accepted | plan D3 (test-only seed, count guard) |
| PV2 | placeholder-hash scenarios at 528-584 | accepted | plan D4 |
| PV3 | source-stale tests not listed | accepted | plan D4 |
| PV4 | vacuous doesNotContain assertions | accepted | plan D4 |
| PV5 | FR-001's "unchanged" vs the signature change; the deletion guard | accepted | plan D1 |
| PV6 | NFR-002 not measurable for --full | accepted | plan D11 |
| PV7 | fixture positive controls | accepted | plan D3 |
| PV8 | remediate test design | accepted | plan D7 |
| PV9 | red-first for the rename | accepted | plan D11 |
| PV10 | SC-001 parity table | accepted | plan D11 |
