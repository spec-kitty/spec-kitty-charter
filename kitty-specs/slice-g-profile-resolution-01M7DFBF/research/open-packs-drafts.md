# Open-packs drafts for slice G

Slice G (spec-kitty-charter#538, ADR-0078) changes the model that `spec-kitty-open-packs` follows. This session
cannot write to open-packs (charter.md; `docs/development/issue-tracker.md`, Rule 4), so each change is drafted here
with an `open-packs#TBD-<slug>` placeholder. **These are drafts only.** The orchestrator files each one in
`spec-kitty/spec-kitty-open-packs` as written, with the `follow-up` label, and replaces the placeholder with the issue
number here.

Every count below was taken against open-packs at `1692a3b` (the charter's `openPacksRef`), on a scratch clone.

| Placeholder | What it does | Size |
|---|---|---|
| `open-packs#TBD-retire-sentinel` | `human-in-charge` moves to `performer: human`, `role: operator`; the role references follow | 1 profile, 5 references in 4 profiles, the docs that name the role |
| `open-packs#TBD-specialization-acyclic` | The validator refuses a `specializes_from` cycle; the content already passes | Validator and docs; no content change |
| `open-packs#TBD-role-pins` | Informational: `policy.yaml` may now pin a role | None required |

## Census (what slice G's schemas refuse in open-packs today)

| Rule | Open-packs content that breaks it |
|---|---|
| `sentinel` has no reader (`agent-profile.schema.json` is closed) | 1: `packs/foundation/agent_profiles/human-in-charge.agent.yaml` (`sentinel: true`) |
| `operator` is reserved for `performer: human` | 0: no profile takes `operator` as `role` or alt-role |
| A step's `role` is never `generic` and is at most 64 characters (`mission-type.schema.json`) | 0: the 32 step roles are `analyst`, `architect`, `facilitator`, `implementer`, `planner`, `qa-reporter`, `qa-strategist`, `researcher`, `reviewer`, `test-designer` and `test-executor` |
| A fallback profile is agent-performed (`fallback_profile_not_agent`) | 0: `generic-agent`, named by `foundation` and `built-in`, declares no `performer` |
| `specializes_from` is acyclic (`specialization_cycle`) | 0: 7 edges, each from a language implementer to `foundation::implementer-ivan` |
| `role_pins` in `policy.yaml` | 0: no pack pins a role; `foundation/policy.yaml` is the one policy file |

Procedures keep `role: operator` on their steps (29 uses in 16 procedures); that is a procedure's role, which slice G
does not change.

## Ordering

Open-packs proves its content against `charter.lock.yaml` (ADR-0070), so the charter's `main` is not blocked. **The
pin can move only with `open-packs#TBD-retire-sentinel` in the same pull request**: the new pin refuses
`sentinel`, and the current pin refuses `performer`, so neither half passes CI alone. The other two drafts need no
content change.

---

## open-packs#TBD-retire-sentinel

**Title:** Retire `sentinel`: `human-in-charge` becomes `performer: human`, `role: operator`

**Labels:** `follow-up`

**Follows:** open-packs#139 (retire `human-in-charge`), which this issue carries out for the model slice G ruled.
spec-kitty-charter#512, ADR-0078 decision 4.

### Why

The charter replaced the agent profile's `sentinel: boolean` with `performer: agent | human` (ADR-0078 decision 4),
with no reader of the old key: the profile schema is closed, so a profile that carries `sentinel` is refused as an
undeclared key. The role `operator` is reserved for a human-performed profile, and a step or procedure that asks for
`operator` is served by one, never by the fallback. `human-in-charge` is open-packs' human profile, and its role
`human-in-charge` is what the charter now calls `operator`.

### Change

1. `packs/foundation/agent_profiles/human-in-charge.agent.yaml`:
   - remove `sentinel: true`;
   - add `performer: human`;
   - `role: human-in-charge` becomes `role: operator`;
   - `description` no longer says "Workflow sentinel": for example "A human performs this work package. No agent
     identity is injected. The Human in Charge remains accountable for the deliverable."
   - The `profile-id` stays `human-in-charge`, so the manifest entry, the graph node
     (`agent_profile.graph.yaml`) and `.kittify/charter/charter.yaml` are unchanged.
2. The role references in `collaboration` follow the role (5 entries in 4 profiles):
   - `packs/architecture/agent_profiles/paula-patterns.agent.yaml:47` (`handoff-from`);
   - `packs/planning-analysis/agent_profiles/retrospective-facilitator.agent.yaml:46` (`handoff-to`) and `:55`
     (`works-with`);
   - `packs/refactoring/agent_profiles/randy-reducer.agent.yaml:53` (`handoff-from`);
   - `packs/testing/agent_profiles/debugger-debbie.agent.yaml:47` (`handoff-from`).

   Each `- human-in-charge` becomes `- operator`. Prose that says "the human-in-charge" (procedures, a tactic) names
   the person, not the role, and may stay.
3. Docs: `packs/foundation/agent_profiles/README.md` (the role column: `operator`), `packs/foundation/README.md`,
   `docs/packs/foundation.md` and the root `README.md` describe the profile as human-performed, with the role
   `operator`. The changelog records the break.
4. Restamp `packs/foundation/pack-manifest.yaml` and any pack whose profile changed.
5. With the change, in the same pull request: raise `charter.lock.yaml` to the charter release that carries slice G.

### Acceptance

- No `sentinel` key remains under `packs/`.
- `human-in-charge` reads with `performer: human` and `role: operator`, and validates against the pinned charter's
  `agent-profile.schema.json`.
- Under an activation that holds `foundation`, the charter's `selectAgentProfile` for `operator` answers
  `human-in-charge` with `fit: primary`, and the fallback is never answered for `operator`.
- No step of a mission type asks for `generic` (already true: 0 today).

---

## open-packs#TBD-specialization-acyclic

**Title:** Refuse a `specializes_from` cycle among agent profiles in the validator

**Labels:** `follow-up`

**Refs:** spec-kitty-charter#42, ADR-0078 decision 2.

### Why

The charter now ranks a role's candidates by their depth, the longest in-force `specializes_from` chain, and refuses
a cycle among a closure's agent profiles at registration (`422 specialization_cycle`, naming the cycle's ids in
order). Open-packs' content has no cycle: its 7 `specializes_from` edges each go from a language implementer
(`go-gwen`, `java-jenny`, `python-pedro`, `rust-ruben`, `frontend-freddy`, `node-norris`, `drupal-dries`) to
`foundation::implementer-ivan`. The validator should keep it so, before the charter refuses it.

### Change

- The pack validator (`validate-packs.sh` and its `--cross-pack` mode) reports a cycle among the `specializes_from`
  edges of the composed packs, naming the profile ids in order.
- A test with a two-profile cycle as the positive control.
- Authoring docs: `specializes_from` is acyclic, and a profile may have several parents.

### Note for authors (no change asked)

Under ADR-0078 decision 6 the charter's effective profile read now inherits along `specializes_from`: each
language implementer shows the top-level fields it leaves absent from `implementer-ivan`, identity fields aside.
Selection still judges each profile by its own declared fields. An author who does not want a field inherited
declares it on the specialization.

---

## open-packs#TBD-role-pins (informational)

**Title:** A pack may pin a role in `policy.yaml` (`role_pins`)

**Labels:** `follow-up`

**Refs:** spec-kitty-charter#42, ADR-0030, ADR-0078 decision 3.

Nothing to change; this records a capability. A pack's `policy.yaml` may declare `role_pins: {<role>: <profile-id>}`.
The pinned profile must be held in the pack's closure (`role_pin_unknown`) and fit the role as its `role` or an
alt-role (`role_pin_not_fitting`, with no exemption for a human-performed profile). Pins are tighten-only: two packs of
one closure that pin one role to different profiles are refused (`role_pin_conflict`), whichever builds on the other.
A pin narrows a role's candidates to the pinned profile and its specializations. A human-performed profile serves a
role other than `operator` only through a pin.

No open-packs pack pins a role today, and none is asked to. An org pack that wants every review done by a human would
pin `reviewer` to a human-performed profile whose `role` or `alt-roles` take `reviewer`.
