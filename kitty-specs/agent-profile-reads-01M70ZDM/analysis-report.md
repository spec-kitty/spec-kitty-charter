---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: agent-profile-reads-01M70ZDM
mission_id: 01M70ZDMZXTYSB91J7BQ7SKPN8
generated_at: '2026-11-09T20:59:09.776919+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: .worktrees/agent-profile-reads-01M70ZDM-coord/kitty-specs/agent-profile-reads-01M70ZDM/spec.md
    sha256: d1be309bb6ee08c41390bf10684fce173862e71abbcdccbf4ae1e50279d08905
  plan.md:
    path: .worktrees/agent-profile-reads-01M70ZDM-coord/kitty-specs/agent-profile-reads-01M70ZDM/plan.md
    sha256: 97f7ec125e59ba421f3f7971aeba62ab85cb434ab7ec635e3d6fefeb14632d93
  tasks.md:
    path: .worktrees/agent-profile-reads-01M70ZDM-coord/kitty-specs/agent-profile-reads-01M70ZDM/tasks.md
    sha256: a4ad8be1d238549627c445d3dde16d3b811ed1664eb4eaec9075be1d7082722d
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 4294d3d4acf870b61bc424ac17d3d34708093d4dbc6126f5b1f72941b23497cc
verdict: ready
issue_counts:
  medium: 3
  low: 3
  critical: 0
  high: 0
  info: 0
findings:
- id: I1
  severity: medium
  category: inconsistency
  summary: FR-007 says the generic activation-scoped read answers the winner's body; the operator ruling and WP06 answer the winner with an `artifact` link and embed the body only on the agent_profile route.
- id: I2
  severity: medium
  category: inconsistency
  summary: Plan fold C6 puts both profile operations under the AgentProfiles tag; WP07 uses a separate EffectiveAgentProfiles tag and controller to avoid a shared owned file and a duplicate mapping.
- id: I3
  severity: low
  category: inconsistency
  summary: Plan Technical Context still says verifyAll includes the whole-pack round trip; fold P3 corrects this and WP01/WP10 run it explicitly.
- id: I4
  severity: low
  category: inconsistency
  summary: data-model.md keeps the superseded single governance-entry table above its 'Superseded by the plan fold' note.
- id: U1
  severity: medium
  category: underspecification
  summary: WP03's T013 leaves open how the merged closure exposes the winning entity (MergedClosure / MergeInputs / resolutionEdges); the signature is fixed but the source is not.
- id: C1
  severity: low
  category: coverage
  summary: NFR-003 (p95 < 500 ms) is measured only by the usability script (WP09/WP10); no automated test guards it in CI.
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| I1 | Inconsistency | MEDIUM | spec.md FR-007; plan fold; WP06 | The generic activation read does not embed a body. Only the profile route does. | Read FR-007's "body" as "the body, or a link to it". This matches operator decision 01M710DQEMV7VPKDNQPWKPGD4F, which says the body *may* be embedded. Reword at the docs pass (WP10). |
| I2 | Inconsistency | MEDIUM | plan.md fold C6; WP07 | The WP uses a separate tag and controller. | Accept the WP07 deviation. It is recorded in WP07. The pre-PR review checks the generated interface names. |
| I3 | Inconsistency | LOW | plan.md Technical Context | The plan still says `verifyAll` runs the round trip, but it does not. | Covered by fold P3. No action. |
| I4 | Inconsistency | LOW | data-model.md | A superseded table is still present. | Covered by the superseded note. No action. |
| U1 | Underspecification | MEDIUM | WP03 T013 | The WP does not say where the winner entity comes from. | The implementer locates the smallest accessor. The signature is promised to the mission-types session, so the pre-PR review checks it. |
| C1 | Coverage | LOW | NFR-003 | The latency target is guarded only by the script. | Acceptable. Record the measured p95 in `research/usability-green.md`. |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 pin bump | Yes | WP01 (T001-T004) | |
| FR-002 profile body pack-scoped | Yes | WP04 | |
| FR-003 governance walk | Yes | WP03, WP04 | |
| FR-004 linked entries | Yes | WP04, WP07 | |
| FR-005 list / references | Yes | WP05 | |
| FR-006 DELETE | Yes | WP05 | |
| FR-007 activation-scoped read | Yes | WP06, WP07 | See I1 |
| FR-008 effective item links | Yes | WP08 | |
| FR-009 root activations link | Yes | WP08 | |
| FR-010 refusals | Yes | WP04, WP05, WP06 | |
| FR-011 usability script | Yes | WP09, WP10 | |
| NFR-001 round trip | Yes | WP04 T023 | Covers 31 profiles |
| NFR-002 contract discipline | Yes | WP02, WP04-WP08 | |
| NFR-003 latency | Yes | WP09, WP10 | See C1 |
| NFR-004 bounded walk | Yes | WP03 | |
| C-001 to C-006 | Yes | WP02, WP05, all | |

**Charter Alignment Issues:** None. Two divergences from core's oracle are covered: `suggests` entries are listed, and an ancestor not in force is dropped. Both are operator rulings, recorded in research D4 and in the contract descriptions (WP04, WP07), not hidden. Under the charter's 2026-10-28 ruling, the charter's own model is the judge. The cascade divergence is the operator's explicit ruling `01M7115WE6WQ7Q41HSS9ZHD1F3`, and the pre-PR squad reviews it again.

**Unmapped Tasks:** None.

**Metrics:**
- Total requirements: 26 (11 FR, 4 NFR, 6 C, 5 SC)
- Total tasks: 52, in 10 work packages
- Coverage: 100%
- Ambiguity count: 0
- Duplication count: 0
- Critical issues: 0

### Next Actions

No critical or high findings, so the mission can proceed to implementation. Fold I1 into the WP10 docs pass.
