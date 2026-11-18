---
affected_files: []
cycle_number: 1
mission_slug: model-task-routing-01M7PVHQ
reproduction_command:
reviewed_at: '2026-11-18T16:18:20Z'
reviewer_agent: claude-reviewer
wp_id: WP06
---

# WP06 review — cycle 1: changes requested

Reviewer: independent reviewer (session_019C2Tk2EBfnVS3Cj2pGMmxF). Reviewed at lane-f 35f9851f8 (content at 5ed91fa58);
WP diff 906f5827a..35f9851f8.

What is good and stays:
- The three catalogs hold R7's numbers exactly, and each file's arithmetic comment is right: built-in 0.3/0.3 with
  model-a the unique top under `quality_first`; org-base design tie 0.3/0.3, model-b before model-a, review
  0.9/0.732/0.7 with `max_tier: medium`, and triage with no fit; cost-led 0.15/0.55.
- `java-jenny` has `model: model-x` and `effort: high`; `generic-agent` has no model; the review step has
  `model_tier: low`; `routing_catalog: open` is set; the manifests are recomputed.
- The step-read scenarios cover both the pack read and the effective read.
- Deviations accepted:
  - (a) cost-led's optional members: all inert, because the risk and latency weights are 0;
  - (b) the new `model-routing` preset;
  - (c) the on-demand step's `model_tier: high`.
- The two unplanned fixes are accepted:
  - `CompositionFactsBuilder` holding activatable kinds. Only `routing_catalog` is activatable and not declarable,
    so no other kind changes.
  - `NodeKind.named()` and the contract `NodeKind` listing `routing_catalog` again. This reverses WP05's deviation 1
    and restores the plan's shape. It is recorded as an ADR-0080 amendment with a Breaking fragment line. WP05 does
    not need reopening.

## Must fix

1. **Built-in's preset must not make `routing_catalog:default` a must-have.** `built-in/presets/default.yaml` lists
   `activated_routing_catalogs: [{id: default, must_have: true}]`. The required floor is closure-scoped
   (`RequiredFloor`: every must-have item of every governing preset the closure holds), and built-in is in every
   closure. ADR-0031 decision 3 says a required artifact is always active and its deactivation is refused. So
   `routing_catalog:default` can never be deselected, and two rows of the R7 matrix this WP must hold ("The example
   packs hold the R7 matrix exactly") become impossible:
   - **row 3**: org-base with `cost-led` selected and `default` deselected, expecting `cost_first` and `model-b`
     only. This would give a 409 for the denial, or the conflict;
   - **row 9**: the project closure with `default` deselected, expecting `409 routing_catalog_not_in_force`. This
     would give a refusal to deselect a required artifact, not `not_in_force`.

   No test fails on this yet; WP08 and WP09 write those scenarios. Nothing needs the mark either. Journey 12 failed
   only because the catalog was not held, which your `CompositionFactsBuilder` fix solves. T021 asks only that
   "Built-in's preset selects its `default`".
   - Fix: drop `must_have: true` from that item, leaving `- id: default`. Recompute built-in's manifest with
     `PackManifests.recompute`, update `facts/example-packs.json` if the required floor appears there, and rerun
     `verifyAll`.
   - If you think a must-have catalog is wanted, ask the operator. It would need R7 rows 3 and 9 redesigned.

## Notes (not blocking)

- I did not rerun `verifyAll` for this cycle; the rejection does not depend on it. I will run it on the resubmission.
