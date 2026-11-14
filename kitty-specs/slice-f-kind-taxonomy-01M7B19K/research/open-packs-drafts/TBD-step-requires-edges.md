# open-packs#TBD-step-requires-edges: link every step from its mission type

> Draft for `spec-kitty/spec-kitty-open-packs`, written by charter mission `slice-f-kind-taxonomy-01M7B19K` (WP11).
> The orchestrator files it and replaces `TBD-step-requires-edges` with the issue number.

## Title

Add the 26 missing `mission_type -requires-> action` edges and their 23 action nodes

## Why

Charter slice F (ADR-0077 decision 4, operator ruling `DM-01M7B1YQR5ZKQPDW6DX64KPNRN`) keeps the
`mission_type:<t> -requires-> action:<t>/<s>` edge authored and makes it complete: one edge per step, on-demand steps
included. A pack that misses one, or holds such an edge to an action that is no step of the type, is refused with
`mission_step_unlinked`, at load per pack and again at compose on the winning type.

At `1692a3b` the six mission types have 47 steps (on-demand included) and 21 such edges. Cave-dev and qa have none,
and neither has an `action` node at all.

## What

Add one `requires` edge per row below, in the graph document of the pack that holds the mission type, and an
`action:<t>/<s>` node where none exists.

- **Sharded packs** (foundation, writing): the edge goes in `mission_type.graph.yaml` and the node in
  `action.graph.yaml`, in the shard's form:

  ```yaml
  # mission_type.graph.yaml, edges:
  - source: mission_type:software-dev
    target: action:software-dev/discovery
    relation: requires
  # action.graph.yaml, nodes (kept sorted by urn):
  - urn: action:software-dev/discovery
    kind: action
    label: discovery
  ```

- **Unsharded org packs** (qa, cave-kitty): both go in `drg/fragment.yaml`, the node in the fragment's plural form
  with the step's title:

  ```yaml
  nodes:
  - id: qa/discovery
    kind: actions
    title: Discovery
  edges:
  - source: mission_type:qa
    target: action:qa/discovery
    relation: requires
  ```

`apply-drafts.py --drafts 2` in this directory adds exactly these on a copy of the repository. Then restamp the
digests (`python3 scripts/ci/pack_digest.py --update foundation writing qa cave-kitty`) and regenerate the four pack
pages (`scripts/docs/gen_pack_reference.py`). `action` and `mission_type` nodes are manifest-exempt
(`MANIFEST_EXEMPT_NODE_KINDS`), so no manifest changes.

Sizes: 26 edges (cave-dev 8, qa 7, software-dev 8, research 2, documentation 1) and 23 action nodes (cave-dev 8,
qa 7, software-dev 7, research 1). Three steps already have their node and lack only the edge:
`research/retrospect`, `software-dev/retrospect` and `documentation/retrospect`.

This draft is additive and valid under the current charter pin: it can land before the pin moves (see
[README.md](README.md)).

## Acceptance

- For each of the six mission types, every entry of `steps` and `on_demand_steps` has its `requires` edge in its own
  pack, and no `requires` edge from a type points at an action that is not one of its steps.
- `census-check.py` in this directory reports 0 unlinked steps.
- `scripts/ci/validate-packs.sh` passes; under a pin that includes slice F, the charter service job registers every
  pack with 0 `mission_step_unlinked` refusals.

## Files

- `packs/foundation/mission_type.graph.yaml` (10 edges), `packs/foundation/action.graph.yaml` (8 nodes)
- `packs/writing/mission_type.graph.yaml` (1 edge)
- `packs/qa/drg/fragment.yaml` (7 edges, 7 nodes)
- `packs/cave-kitty/drg/fragment.yaml` (8 edges, 8 nodes)
- `packs/{foundation,writing,qa,cave-kitty}.source.yaml` (digests), `docs/packs/{foundation,writing,qa,cave-kitty}.md`
  (regenerated)

## The edges (generated)

| Pack | Edge `mission_type:<t> -requires-> action:<t>/<s>` | Step kind | Action node added |
|---|---|---|---|
| cave-kitty | `mission_type:cave-dev` → `action:cave-dev/discovery` | step | yes |
| cave-kitty | `mission_type:cave-dev` → `action:cave-dev/specify` | step | yes |
| cave-kitty | `mission_type:cave-dev` → `action:cave-dev/plan` | step | yes |
| cave-kitty | `mission_type:cave-dev` → `action:cave-dev/tasks` | step | yes |
| cave-kitty | `mission_type:cave-dev` → `action:cave-dev/analyze` | step | yes |
| cave-kitty | `mission_type:cave-dev` → `action:cave-dev/implement` | step | yes |
| cave-kitty | `mission_type:cave-dev` → `action:cave-dev/review` | step | yes |
| cave-kitty | `mission_type:cave-dev` → `action:cave-dev/accept` | step | yes |
| foundation | `mission_type:research` → `action:research/accept` | step | yes |
| foundation | `mission_type:research` → `action:research/retrospect` | on-demand | no (exists) |
| foundation | `mission_type:software-dev` → `action:software-dev/discovery` | step | yes |
| foundation | `mission_type:software-dev` → `action:software-dev/analyze` | step | yes |
| foundation | `mission_type:software-dev` → `action:software-dev/accept` | step | yes |
| foundation | `mission_type:software-dev` → `action:software-dev/charter` | on-demand | yes |
| foundation | `mission_type:software-dev` → `action:software-dev/tasks-finalize` | on-demand | yes |
| foundation | `mission_type:software-dev` → `action:software-dev/tasks-outline` | on-demand | yes |
| foundation | `mission_type:software-dev` → `action:software-dev/tasks-packages` | on-demand | yes |
| foundation | `mission_type:software-dev` → `action:software-dev/retrospect` | on-demand | no (exists) |
| qa | `mission_type:qa` → `action:qa/discovery` | step | yes |
| qa | `mission_type:qa` → `action:qa/specify` | step | yes |
| qa | `mission_type:qa` → `action:qa/plan` | step | yes |
| qa | `mission_type:qa` → `action:qa/tasks` | step | yes |
| qa | `mission_type:qa` → `action:qa/implement` | step | yes |
| qa | `mission_type:qa` → `action:qa/review` | step | yes |
| qa | `mission_type:qa` → `action:qa/accept` | step | yes |
| writing | `mission_type:documentation` → `action:documentation/retrospect` | on-demand | no (exists) |
