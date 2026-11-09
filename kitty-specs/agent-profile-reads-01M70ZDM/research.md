# Research: Agent profile reads

The squad's raw findings are in `research/squad-findings.md`. The decisions below are recorded as Decision
Moments in `decisions/`.

## D1 — Scope: profiles only, one PR

- **Decision:** this mission serves agent profiles, and opens with the open-packs pin bump. Mission types, steps,
  step contracts and templates run as a separate mission with its own PR. The rule is two PRs, two missions.
- **Rationale:** operator ruling of 2026-11-09. #407 is P0, and the mission-type shapes (#99, #100) are still
  "decide and shape".
- **Alternatives considered:**
  - one PR;
  - two PRs from one mission;
  - three PRs, with the pin bump separate.

## D2 — Storage needs no work

- **Decision:** no storage change is made.
- **Rationale:** every pack-stored kind has a domain type and a binding (`KindBindings.ALL`). Templates go through
  `TemplateBinding`, anti-patterns live inside directives, and step enhancements come with #437. The operator's
  steer is to focus on the read contract shapes and the REST adapter.

## D3 — The slice's rings live in a new `:profiles-edge` module

- **Decision:** the profiles slice's rings live in a new `:profiles-edge` module.
- **Rationale:**
  - `:packs` depends on `:profiles`, because `packs.infra.storage` binds `AgentProfile` (ADR-0037, amendment of
    2026-10-27).
  - Rings in `:profiles` that call `packs.application` would make a cycle.
  - `:profiles-edge` keeps `:profiles` pure. It reaches packs and activations only through their application rings,
    which is the pattern `activations.infra.packs` already follows.
- **Alternatives considered:**
  - **Split `:profiles` into a domain module.** It churns every storage import.
  - **Put the rings in `:packs`.** That grows another slice's rings inside packs, and would also need ADR-0037's
    `ENTRYPOINT_STAYS_IN_ITS_SLICE` to be loosened.
  - **A neutral view type in `packs.domain`.** Packs would own another slice's read shape.
  - **Amend the rule.** Rejected outright.

## D4 — The governance walk reuses the action-context engine

- **Decision:** a `LINEAGE` step (`specializes_from`, transitive) takes the place of `SCOPE`. `REQUIRES`,
  `SUGGESTS` (depth 0 or 1), `REQUIRES_OF_SUGGESTED` and `VOCABULARY` are seeded from the profile and its lineage.
  The walk keeps minimum-step semantics, the parent tie order, and URN order.
- **Rationale:** the operator asked for "the same walker approach and rules as other kinds". One engine means one
  set of tie-break and termination rules, and NFR-004 holds by construction (each step visits a node once).
- **Pack-scoped walk:**
  - It runs over the pack's composed `depends_on` closure.
  - Every node is in force.
  - An entry carries `held`: false when no pack of the closure holds the target.
- **Activation-scoped walk:**
  - It runs over the merged closure with the effective predicate.
  - Out-of-force suggestions are reported as candidates, as in the action context.
- **Core's oracle, for comparison:**
  - Core's profile channel walks `{requires, specializes_from, suggests}`, and `resolve_profile` merges fields from
    root to leaf.
  - Field merge is not done here. The body answered is the stored leaf body, and the lineage is answered as
    entries a client can follow.
  - #102 keeps the field-merge question. This difference is recorded here, not hidden.

## D5 — The activation-scoped read

- **Decision:**
  - `GET /activations/{id}/artifacts/{kind}/{artifactId}` answers the winner with its state, and links to the
    winner's pack-scoped read (`artifact`).
  - For `agent_profile`, a literal route in `profiles.infra.api` answers the same fields, plus the embedded body
    and the governance with states.
  - Both are wired to the same activations application service.
- **Rationale:** operator ruling. The shared surface between slices is the application layer, and one service may
  be wired to several endpoints.
- **Alternatives considered:**
  - **Embed every kind's body in the activations controller.** It needs a REST-layer rendering seam.
  - **No activation path.** Rejected earlier.

## D6 — Links and relations

- Pack-scoped governance entries link `artifact`, to the target's pack-scoped read, when the target is held.
- Effective items, graph nodes and action-context items of a served kind link `effective-artifact` (new relation)
  and `artifact` (the winner's pack-scoped read). The `artifact` relation keeps its documented meaning.
- The root links `activations` (`POST`). The relation is already proposed in the table and is now in the contract.
  This closes the root half of #454.

## D7 — Refusals

- **Decision:**
  - An unknown profile answers `404 artifact_not_found`.
  - An unknown activation answers `activation_not_found`.
  - An unserved kind answers `kind_not_found`.
  - No new refusal code is added (C-005), so `CharterRefusalCode.yaml` is touched only if a description lists the
    served kinds.

## D8 — DELETE opens for `agent_profile`

- **Decision:** DELETE opens for `agent_profile`.
- **Rationale:** operator ruling. Removal shares `SERVED`. The rules (locked, required, pack writability) are the
  ones the five kinds follow. Orphaned graph edges are judged as they are today for a directive.

## Squad dispositions

To be filled by the plan-point adversarial squad.
