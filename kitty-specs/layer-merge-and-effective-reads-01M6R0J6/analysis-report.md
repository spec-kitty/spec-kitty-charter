---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: layer-merge-and-effective-reads-01M6R0J6
mission_id: 01M6R0J642MF7F33XVMMPSBSAT
generated_at: '2026-11-09T16:35:26.982895+00:00'
analyzer_agent: claude:opus:orchestrator
input_artifacts:
  spec.md:
    path: kitty-specs/layer-merge-and-effective-reads-01M6R0J6/spec.md
    sha256: 167c725dd0160a673f03d12b50684714cc75b97b250be9350e017a1c6323d975
  plan.md:
    path: kitty-specs/layer-merge-and-effective-reads-01M6R0J6/plan.md
    sha256: 2554ffa92313e0c95ade66ad3584a73fa96f495c00576241e7c7716b5a1a2301
  tasks.md:
    path: kitty-specs/layer-merge-and-effective-reads-01M6R0J6/tasks.md
    sha256: 6350eeed13c1816dd05db5dca72b2a679d3c1e388ed92c70c84efdc8f924fe46
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 4294d3d4acf870b61bc424ac17d3d34708093d4dbc6126f5b1f72941b23497cc
verdict: blocked
issue_counts:
  low: 3
  medium: 3
  high: 2
  critical: 0
  info: 0
findings:
- id: G1
  severity: high
  category: underspecification
  summary: 'E15 and E16 restrict the graph and the action-context walk to Eff, which never holds a step, so the walk could not start at action:mt/a. Fixed: runner decision 01M702A71YF7WNDTG2AA89K8ZA (Eff plus held steps of mission types in Eff); WP27, WP29.'
- id: G2
  severity: medium
  category: inconsistency
  summary: 'E6 as built prunes every step enhancement (an action: target is never in Eff). Fixed: OD-6 amendment planned in WP27 (T160) and WP29 (T165).'
- id: G3
  severity: medium
  category: duplication
  summary: 'Two predicates for "pack P holds step M/s" (graph-declared action: nodes vs the mission type declaring the step). Fixed: OD-9 StepHolding in WP27 (T159), reused by the other mission.'
- id: G4
  severity: medium
  category: coverage
  summary: '#429 (step references dropped from the skipped report) had no WP. Fixed: WP28 T162-T163 (NodeReference, D4(a), D16); FR-017 mapped to WP28.'
- id: G5
  severity: low
  category: inconsistency
  summary: 'RequiredNotInForce.reason typed as the ten-value Reason against the contract two values (D4b). Fixed: WP27 T161, WP28 T164.'
- id: S1
  severity: high
  category: inconsistency
  summary: 'Plan-point squad (35 findings, one blocker): OD-9 contradicted EdgeIndexTest; OD-6 scope and binding source; node predicate scope; D4b boundary; #429 tests falsifiable. Fixed in c957a2de; decisions 01M7037V93Y077H8M4W9V8Y6B3, 01M7037ZNKJ6JTRKGD7G695ZV1; deferred #439.'
- id: I9
  severity: low
  category: inconsistency
  summary: plan IC-11 names effective-reads.feature for US5; WP28 owns the new effective-graph-and-context.feature (WP22 owns effective-reads.feature). The WP is authoritative (carried).
- id: C1
  severity: low
  category: coverage
  summary: NFR-001, C-004 and C-005 are cited in WP bodies but mapped to no WP's requirement_refs (carried).
---

> Re-recorded on 2026-11-09 by the PR C runner (session_01E5wJUBQ4VPs6CrRRu4Q8ov): the analysis receipt is local to a clone's
> `.git`, and this fresh container had none, so the implement gate refused the WP29 fix-cycle claim. Every input hash
> (spec, plan, tasks, charter, material, templates) was checked equal to the 12:07 report's, so this is that analysis,
> findings and body unchanged.

## Specification Analysis Report (re-run, 2026-11-09, before phase C, after the plan-point squad)

Re-run before WP27 starts. PR B (#427) and #430/#436 are on `main`; the planning branch was rebased onto `main` at
`5458e1fa` (only the 2026-11-09 hand-over and its matrix rows remained to replay). The inputs changed in one commit
(`d4c27892`): the operator rulings of 2026-11-09 from the step-enhancements mission's plan point (OD-6, OD-9, D4b, and
#429 with D4(a), D15, D16) were added to WP27-WP29, tasks.md (T159-T165) and a plan section after IC-11.

### Findings in this delta

| ID | Category | Severity | Location(s) | Summary | Disposition |
|----|----------|----------|-------------|---------|-------------|
| G1 | Underspecification | HIGH | rules page E15, E16; plan IC-10 | The graph and the walk are restricted to `Eff`; a step is never in `Eff`, so the walk's start node is refused. | Fixed: decision `01M702A71YF7WNDTG2AA89K8ZA`; WP27 states the predicate, WP29 the docs and divergence. Put to the plan-point squad. |
| G2 | Inconsistency | MEDIUM | `InForceResolution` E6; ADR-0066 | No step enhancement can be in force as built. | Fixed: OD-6 in WP27 T160 (red test production-shaped) and WP29 T165. |
| G3 | Duplication | MEDIUM | `EdgeIndex.stepsOf`, WP27 `action_not_found`, other mission's FR-009 | Two predicates for one fact. | Fixed: OD-9 `StepHolding` (WP27 T159). |
| G4 | Coverage | MEDIUM | #429 | No WP carried it. | Fixed: WP28 T162, T163; FR-017 mapped to WP28. |
| G5 | Inconsistency | LOW | `InForceStates`, `EffectiveSkippedReport` | A ten-value type behind a two-value contract enum. | Fixed: WP27 T161, WP28 T164. |
| I9 | Inconsistency | LOW | plan IC-11 | Scenario file name differs from WP28. | Carried; the WP is authoritative. |

The step-enhancements mission's C-004 boundary is respected: it edits no PR C file; the symbols it consumes are named
in the plan section and stay stable once reviewed.

| S1 | Inconsistency | HIGH | WP27-WP29, plan section | The plan-point squad's 35 findings (`research/plan-point-squad-pr-c/`). | Fixed in `c957a2de`; decisions `01M7037V93Y077H8M4W9V8Y6B3`, `01M7037ZNKJ6JTRKGD7G695ZV1`; #439 and the `override_unstable` shape deferred with rationale. |

### Coverage summary (phase C)

| Requirement | WPs |
|---|---|
| FR-015, FR-019 | WP27, WP28 |
| FR-018 | WP27, WP28 |
| FR-017 | WP27 (D4b), WP28 (#429) |
| FR-020, FR-021, NFR-002, SC-001, SC-002 | WP28 |
| NFR-007 | WP27 |
| FR-013, FR-014, FR-016, C-002, C-007 | WP29 |

FR coverage: 43/43 mapped. Critical: 0. The one HIGH is fixed by a recorded decision inside the spec's FR-018/FR-019.

### Next actions

Phase C may proceed: WP27, then WP28, then WP29. `lanes.json` write scopes predate the new files; the lanes carry them as out-of-map edits with rationale.
