# Decision Moment `01M702A71YF7WNDTG2AA89K8ZA`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `pr-c-node-predicate`
- **Input key:** `pr-c-node-predicate`
- **Status:** `resolved`
- **Created:** `2026-11-09T11:50:19.966899+00:00`
- **Resolved:** `2026-11-09T11:50:32.037603+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

E15 and E16 restrict the graph and the action-context walk to Eff, which never holds a step (action:M/s), not even the walk's start node. What node predicate do the graph projection and the walk use?

## Options

- Eff plus each held step (OD-9) whose mission type is in Eff; kinds with no activation state left out, with a divergence entry
- Eff only (the walk cannot start)
- Eff plus every non-activatable node

## Final answer

Eff plus each held step (OD-9) whose mission type is in Eff; kinds with no activation state left out, with a divergence entry

## Rationale

Runner, 2026-11-09: E6 already binds a step to its mission type by URN, so a held step of an in-force M is the one non-activatable node with a defined in-force state; without it the walk cannot start (E16 starts at action:mt/a). Template, asset, guard and lifecycle have no activation state; listing them would widen E15 beyond the ruling, so they stay out and WP29 records the divergence. Within the spec (FR-018, FR-019); put to the plan-point squad.

## Change log

- `2026-11-09T11:50:19.966899+00:00` - opened
- `2026-11-09T11:50:32.037603+00:00` - resolved (final_answer="Eff plus each held step (OD-9) whose mission type is in Eff; kinds with no activation state left out, with a divergence entry")
