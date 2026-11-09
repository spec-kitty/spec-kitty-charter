# PR C plan-point squad (2026-11-09): dispositions

Three lenses read the PR C scope added by the operator rulings of 2026-11-09 (commit `d4c27892`): `architect-alphonso`
(opus), `reviewer-renata` (opus) and `planner-priti` (sonnet). Their reports are in this folder. Verdicts: READY WITH
FIXES, NOT READY (one blocker), READY WITH FIXES. Every finding is dispositioned below. Where a finding is
`accepted`, the change is in WP27, WP28, WP29 or the plan section "PR C scope added by operator rulings
(2026-11-09)". The deduplicated themes are listed, with each lens's findings mapped to them.

| # | Finding (lenses) | Disposition | Where |
|---|---|---|---|
| 1 | OD-9 contradicts `EdgeIndexTest`'s step case: a project pack layer-overrides `action:M/one` without defining `M` (renata BLOCKER; priti MAJOR) | changed: OD-9 has a per-pack form (the operator's wording) and a closure form (some pack holds it; the place is the merged winner). The test stays unchanged as the guard | WP27 T159 |
| 2 | StepHolding over unmerged facts ignores the merge; a loser's unique step survives against C17/C18 (alphonso MAJOR) | changed for the merge (closure form). A7 is kept: a winning `M` does not take its losers' unique steps, as built and documented in `resolution-model.md`. C18 governs one URN's content, and a step is a URN of its own. The dissent is reported to the operator | WP27 T159 |
| 3 | `enhancerOut` skip too broad; two targets, other kinds, mixed targets; C36 does not guarantee one target (alphonso, renata, priti MAJOR) | accepted: only `step_enhancement` with exactly one `action:` target; every other shape keeps E6, each tested | WP27 T160 |
| 4 | `ClosureView.resolutionEdges` drops enhances edges to non-winners, so OD-6 cannot see them (alphonso, priti MAJOR) | accepted: the binding is derived before the winner filter and passed in `Edges.bindings`; `ClosureView` is a listed shared edit | WP27 T160 |
| 5 | E6 vs StepHolding: an enhancer of an unheld step could stand alone (alphonso MAJOR) | changed: an unheld target keeps its edge, so the enhancer is pruned `ENHANCED_TARGET_NOT_IN_FORCE` (I11 holds); this is also #429 item 1's production-shaped source | WP27 T160, WP28 T163 |
| 6 | OD-6 deselected twin passes through the facts binding (renata MAJOR) | accepted: the fixture declares no facts binding | WP27 T160 |
| 7 | OD-6 changes write and read wire output with no fragment or activations test (renata MAJOR) | accepted: `### Changed` fragment `2026-11-09-c-step-enhancer-binding.md` (WP27-owned) and an `EffectiveSetTest` case | WP27 |
| 8 | Node predicate: only the walk's start node should widen; steps cannot be listed (`EffectiveArtifact.kind` is activatable); the predicate is faked by test-supplied lambdas; activations half has no owner (alphonso, renata, priti MAJOR) | accepted: decision `01M702A71YF7WNDTG2AA89K8ZA` amended. The walk starts at a held step, then lists and walks only `Eff`. The graph shows `Eff` plus held steps. The predicate is built in `ClosureView` from the in-force states and tested there | WP27, plan |
| 9 | D4b activations half breaks hexagonal or pins a copy (alphonso, renata MAJOR) | accepted: activations gets its own enum, mapped by an exhaustive switch; the parity row reads the record component type by reflection and drops `PRUNING_REASONS` | WP28 T164 |
| 10 | T161/T164 cannot be red first (renata MAJOR) | accepted: named red tests (reflection on the packs record; the parity row) | WP27 T161, WP28 T164 |
| 11 | #429 tests cannot fail after OD-6; D16 test cannot fail (renata, priti, alphonso) | accepted: production-shaped unheld-target source; D16 pinned by its reason (schema parity plus a fed `InForceStates`); one case per `activatable(` call site | WP28 T163 |
| 12 | Exit checks not updated (renata MAJOR) | accepted | WP27, WP28 DoD |
| 13 | Unlisted files: examples, `SkippedReport.yaml`, `MergedSnapshot`, `EffectiveSet`, client-java path, `EdgeIndexTest`, `ClosureViewTest` (renata, priti) | accepted: listed as shared edits with concrete paths | WP27, WP28 |
| 14 | `NodeReference` Breaking scope incomplete (writes, rename, widened enum) (renata MAJOR) | accepted | WP28 T162 |
| 15 | `LayerOverride`/`layerOverrides` classification; kind note only prose (renata, priti MAJOR) | accepted: `### Breaking` for the required member and the context's `skipped`, `### Added` for new schemas; a unit test and a US5 scenario | WP28 |
| 16 | Suggestion rule and conditional `suggests` entries have no owner (priti MAJOR) | accepted: the walk returns how each entry was reached; activations maps through the existing carrier in `EffectiveSet`, no copy | WP27, WP28 |
| 17 | `EffectiveTag` per representation not stated (priti MAJOR) | accepted | WP28 T140/T141 |
| 18 | C15 docs rule has no file list or acceptance (priti MAJOR; renata MINOR on fakeable docs) | accepted: file list, acceptance list, each amended rule cites its pinning test | WP29 T165 |
| 19 | "in force exactly when" should be "grounded exactly when" (alphonso MINOR) | accepted | WP29 |
| 20 | "never in force" is false inside packs (locks) (alphonso MINOR) | accepted: reworded as "absent from the effective reads" | WP27, WP29 |
| 21 | A step enhancement overriding its own `M` makes E7 unstable (alphonso MINOR) | deferred_with_rationale: the shape is the step-enhancements mission's ADR-0066 integrity check, which lands after PR C; noted to that runner | WP27; message to the phase 2 runner |
| 22 | `NodeKind.yaml` lacks guard and lifecycle; reuse rules unwritten (alphonso MINOR) | accepted: the reuse rule goes in the schema description; guard and lifecycle cannot reach a carrier | WP28 T162 |
| 23 | `satisfiedBy`/`overriddenBy` break a tie by smallest URN with no ruling (alphonso MINOR) | deferred_with_rationale: PR B behaviour, deterministic; needs an operator ruling under the standing rule. Tracked in #439; WP29 documents it as found | #439, WP29 |
| 24 | No output order for graph nodes, edges, `layerOverrides` (alphonso MINOR) | accepted: explicit sort orders | WP27 |
| 25 | StepHolding signature for both callers (alphonso NIT) | accepted | WP27 T159 |
| 26 | Divergence-register line says only DIV-204; DIV-238 claimed elsewhere (renata, priti MINOR) | accepted | WP29 |
| 27 | One consumer test per StepHolding consumer (renata MINOR) | accepted | WP27 T159 |
| 28 | "never the `action:`": the schema cannot name one (renata NIT) | accepted | WP27 T160 |
| 29 | StepHolding not in the NFR-007 counting test (priti MINOR) | accepted | WP27 T159 |
| 30 | requirement_refs vs coverage table (FR-015 on WP28, FR-017 on WP27) (priti MINOR) | accepted | WP27, WP28 frontmatter |
| 31 | `lanes.json` write_scope stale for lane-y, lane-z, lane-{ (priti MINOR) | accepted: re-pinned with `finalize-tasks --refresh-planning-commit` after this fold; if the scope does not follow, the lane rebuild carries the shared edits as out-of-map with rationale | lanes.json |
| 32 | `MergedSnapshot.missionTypes` and steps-under-winning-M have no disposition (priti MINOR) | changed: `missionTypes` is not needed (the mission type's state is read from the in-force states, E16); A7 is kept and pinned (row 2) | WP27 |
| 33 | The ~13 parts for D4(a) live only in the other branch's brief (priti MINOR) | accepted: one case per `activatable(` call site (14 at `5458e1fa`) | WP28 T163 |
| 34 | plan.md section names phase 1, and "its C-004" is ambiguous (priti MINOR) | accepted | plan.md |
| 35 | Blank lines in shared lists; `NodeReference` Java value unnamed (priti NIT) | accepted: `activations.domain.NodeRef` | WP27–WP29, WP28 |
