---
title: "ADR-0050: Mission types and lifecycles as declared state machines"
description: "A mission type declares a state machine (explicit transitions, guards, emit actions); a lifecycle kind holds the per-work-package lanes; packs enhance or override steps."
doc_status: active
updated: 2026-11-07T00:00:00Z
decision_status: 'Accepted, with Amendment 2026-11-02 (ADR-0053; decision 14 superseded by ADR-0054), Amendment 2026-11-05 (ADR-0064) and Amendment 2026-11-07 (decision 6 built)'
decision_date: '2026-11-02'
type: ADR
kind: ADR
related:
  - adr/0044-mission-types-consolidated-into-one-sanitized-shape
  - adr/0020-offering-and-activations
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0049-glossary-contexts-and-terms
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - plans/pack-ecosystem-work-breakdown
  - adr/0051-event-registry-a-thin-provider-neutral-consistency-check
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
  - adr/0032-steps-name-roles-and-profile-selection-order
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - architecture/logical-model
  - plans/pack-ecosystem-design-notes
audience:
  - profile: pack-author
    guidance_text: Read this for how a mission type declares its transitions, guards and actions, how a lifecycle is declared, and how another pack may enhance or replace a step.
  - profile: charter-service-contributor
    guidance_text: Read this before changing the mission-type schema, binding the lifecycle or gate kinds, or writing the step enhancement and overrides checks.
  - profile: core-maintainer
    guidance_text: Read this for what core runs from these declarations (the lane machine, guards, emits) and what the charter no longer special-cases.
---

# ADR-0050: Mission types and lifecycles as declared state machines

## Status

**Accepted**, 2026-11-02. Decider: Stijn Dejongh (operator). Formalizes the operator rulings of 2026-11-02
recorded in the [pack ecosystem design notes](/plans/pack-ecosystem-design-notes/) (section 4, "Mission
types", the responsibility split, the work-package lifecycle, "Mission type as a state machine", and
section 7, gates and guards), on epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142) and
its children [#148](https://github.com/spec-kitty/spec-kitty-charter/issues/148),
[#155](https://github.com/spec-kitty/spec-kitty-charter/issues/155),
[#156](https://github.com/spec-kitty/spec-kitty-charter/issues/156),
[#161](https://github.com/spec-kitty/spec-kitty-charter/issues/161) and
[#164](https://github.com/spec-kitty/spec-kitty-charter/issues/164).

It **supersedes in part** [ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/)
(Accepted): decision 4 ("the list order is the only order") becomes the default of an explicit transition list,
and decision 5's directory `missions/` becomes `mission_types/`, with the step contracts moved into the bundle.
Two narrower points of ADR-0044 change too. Decision 3's references gain two neighbors rather than new `type`
values: a `terms` list of `term:` URNs (ADR-0049) and a `rejects` list of `{type: anti_pattern, id, reason}`
([ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/)); like references, both are
kept verbatim and mint no relationship until [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189)
rules on edge authority. *Amended 2026-11-05: edge authority is decided by [ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/) ([#240](https://github.com/spec-kitty/spec-kitty-charter/issues/240)),
decisions 1 and 12. The `rejects` list is not added: a mission type or step rejects an anti-pattern with a `rejects`
edge in the graph documents. A `terms` list, if the schema work builds it, mints no edge.* Gates and lifecycles are bound through `guard` and `work_package_lifecycle`, not through
references. Decision 5's path rule and the `contracts/packs/file-kinds.yaml` rules anchored to `missions/` (the
prompt and guidelines `files` rules, the README and hidden-file exclusions, the template payload rule) move to
`mission_types/`, and decision 6 gains retired rules for the old `missions/` layout so a stale copy is refused rather
than ignored. (Decision 6's `*/mission_types/*.yaml` rules match one path segment and do not catch the new bundle.)
ADR-0044 decision 3 dropped qa's own `states` and `transitions` block as behavior "no pack can define"; this
record gives that behavior a declared home. Every other ADR-0044 decision stands: the one sanitized file, the step
shape, roles, companions and the other retired file families.

Decision 6 below makes a mission type an ordinary activatable kind. That supersedes in part
[ADR-0020](/adr/0020-offering-and-activations/): its amendment of 2026-10-31 (no deny list for mission types), its
`missionTypes` rule "absent means none" (the ordinary rule is "absent means all", as the logical model's kind table
already shows), and, if the operator takes the recommended default, the `mission_type` spelling of the
`selections/{kind}` segment (open question 9). It also supersedes in part
[ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) decision 2 for the separate
domain slot (`Preset.missionTypes`, `requiredMissionTypes()`); the `activated_mission_types` key is already the
ordinary `activated_<plural>` key and stays. Each superseded record carries its "superseded in part" note.

### Amendment 2026-11-02

The operator ruled every open question on 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decisions 15 to 25). Several rulings change decisions of
this record; the original text stands and each changed point is annotated where it stands:

- decision 3: the `{kind}` path segment uses the kebab operator token (`mission-type`) ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 23);
- decisions 12 and 14: there is no `gate` kind; a transition names guard artifacts implemented by executable assets,
  [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/), which supersedes decision 14 and amends decision 12;
- decision 16: a declared action may also name a pack-delivered code asset, behind trust ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 17);
- decision 18: the lifecycle reference is the generic `submachine` field ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 19);
- decisions 22 and 23: a step enhancement is an additions artifact plus a CRG `enhances` edge, the mechanism profile
  augmentation uses, not a mechanism of its own ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 22);
- decisions 26 to 28: the defaults are ruled ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 24).

### Amendment 2026-11-05

The operator ruled on [#240](https://github.com/spec-kitty/spec-kitty-charter/issues/240) on 2026-11-05 that an edge is authored only in a pack's graph documents
([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/)). The original text stands, and each changed point is annotated:

- the ADR-0044 decision 3 neighbors named under Status: the `rejects` list is not added; a rejection is a `rejects`
  edge in the graph documents ([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/) decision 12);
- decision 22: a step enhancement's additions are guards and `emit` entries; guidance it adds is a graph edge, not a
  `references` entry ([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/) decision 12).

### Amendment 2026-11-07

Decision 6 (activation) is **built** by the layer-merge mission (`layer-merge-and-effective-reads-01M6R0J6`), PR A0,
on the operator's rulings of 2026-11-06 (A7, decision `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`; Q1 to Q4, decisions
`DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S`, `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC`, `DM-01M6RCY40CQQTHC130RWF43W0D`,
`DM-01M6RCY8MYNGNPJ247M2Y4880N`). The decision text stands; decision 6 carries a note.

- **"Absent means all" is reconciled with ADR-0053 decision 10.** The Status above says the ordinary rule for an
  absent list is "all". ADR-0053 decision 10 ruled afterwards that an absent activation key means none, and ADR-0020's
  amendment of 2026-11-05 built it (DIV-203). A mission type is an ordinary kind, so an absent
  `selected.mission_type` means none, the same as `[]`; there is no `missionTypes` list left to read otherwise.
- **The cascade source (Q1).** Selecting a mission type with a cascade crosses to the steps it holds like `requires`,
  and what the steps reach is selected. PR A0 built the selection, the cascade and the deny of mission types like any
  other kind, and the cascade to steps. That a step, and any artifact bound to the mission type, is in force only while
  the mission type is, is binding rule E6: ruled, not built, planned for PR B with the effective reads (FR-022, US3
  AS4). The earlier rule that a mission type never cascades (W32) is withdrawn.
- **Deny and listing (Q2).** A mission type is denied under `deactivated.mission_type` unless it is required or locked
  (`409 artifact_required`, `artifact_locked`). `mission_type` joins `ActivatableKind`, `SelectedArtifacts`,
  `DeactivatedArtifacts` and `CascadeKind`, and the `{kind}` segment of the selections and exclusions paths.
- **`activatedKinds` and `missionTypes` are refused** on a create or replace (`422 validation_failed`), and the
  stored document is `schemaVersion` 2 (Q4).
- **Divergences.** DIV-501 is updated, and DIV-203, DIV-210, DIV-212, DIV-213 and DIV-214 record the rest.

## Context

- **ADR-0044 consolidated the mission type** into one sanitized file: an ordered `steps` list, `on_demand_steps`,
  and per step a role, prompt and guidelines companions, expected input and output artifacts, and references.
  The order of `steps` is the run order, and a non-linear order "is not expressible; when one is needed, a later
  decision adds it" (ADR-0044 decision 4). This is that decision.
- **Today's mission type is a linear machine with no actions.** Mapped onto a finite-state machine:

  | FSM concept | Today (ADR-0044) |
  |---|---|
  | State | a `steps[]` item, plus the implicit start and terminal (Mission created, Mission completed) |
  | Composite state | a step with a `mission_step_contract` (sub-steps) |
  | Transition | implicit: list order, step *i* to *i + 1* |
  | Guard | blocking `expected_input_artifacts` (entry) and blocking `expected_output_artifacts` (exit) |
  | Triggering event | external: the runtime's `next` |
  | Global transitions | `on_demand_steps`, reachable from any state by an explicit command |
  | Branches, rework loops, parallel regions | not modelled; rework lives in core's hardcoded work-package lane machine |
  | Actions (entry, exit, transition) | none |

- **Rework is invisible.** A review rejection that sends work back to implementation is a back-edge no mission
  type declares. It lives in core's work-package lane machine (`status_lanes`, `status/transitions.py`):
  `planned -> claimed -> in_progress -> for_review -> in_review -> approved -> done`, plus `blocked` and
  `canceled`, with guards, all hardcoded. Core's census (spec-kitty/spec-kitty `9aed3a8`) finds the edges and guards
  in one authority, `specify_cli/status/wp_state.py` (`allowed_targets()`, `guard_for`), with
  `status/transitions.py` a read-only projection of it. Its initial lane is `genesis`, which cannot be authored; its
  terminal lanes are `done` and `canceled`. An operator `force` (an actor and a reason) bypasses the edge check and
  the guards, except two refusals that cannot be forced. The lane names mirror `spec_kitty_events.status.Lane`.
- **Core already binds gates to transitions, narrowly.** A step contract's `GateBinding{on_transition, handler,
  fail_open}` resolves through a registry holding one handler (`in_progress -> for_review`), and every handler runs
  fail-open.
- **Extension means copying.** To add an org's static-analysis gate before review, a pack must replace the whole
  mission type.
- **Gates have no home.** #155 asks for a versioned gate-binding schema on steps, #156 for a `gate` kind.

### Theoretical origins

The design borrows from four established bodies of work. Each is cited so a pack author or contributor can look
up the formal meaning of a term used here.

- **Finite-state machines and transducers.** A machine has states, an input alphabet and a transition function.
  In a *Mealy* machine output belongs to the transition; in a *Moore* machine it belongs to the state. Here
  `on_transition` is Mealy-style and `on_enter` and `on_exit` are Moore-style.
  [Finite-state machine](https://en.wikipedia.org/wiki/Finite-state_machine); G. H. Mealy, "A Method for
  Synthesizing Sequential Circuits", *Bell System Technical Journal* 34(5), 1955,
  [doi:10.1002/j.1538-7305.1955.tb03788.x](https://doi.org/10.1002/j.1538-7305.1955.tb03788.x); E. F. Moore,
  "Gedanken-Experiments on Sequential Machines", *Automata Studies*, 1956.
- **Harel statecharts.** Hierarchical states, guards on transitions, entry and exit actions, and parallel regions.
  A step with step-contract sub-steps is a composite state; a step that runs a lifecycle per work package holds
  a submachine state. D. Harel, "Statecharts: a visual formalism for complex systems", *Science of Computer
  Programming* 8(3), 1987,
  [doi:10.1016/0167-6423(87)90035-9](https://doi.org/10.1016/0167-6423(87)90035-9). Standardized as
  [UML state machines](https://www.omg.org/spec/UML/2.5.1/) (OMG UML 2.5.1, clause 14) and as
  [W3C SCXML](https://www.w3.org/TR/scxml/) (2015), whose `<transition event cond target>`, `<onentry>` and
  `<onexit>` are the models for `transitions[].on`, `guard`, `to`, `on_enter` and `on_exit`.
- **Workflow nets.** Petri nets applied to workflow: pre- and postconditions as places and tokens, parallel
  branches with joins. A step's `expected_input_artifacts` and `expected_output_artifacts` are exactly this.
  W. M. P. van der Aalst, "The application of Petri nets to workflow management", *Journal of Circuits, Systems
  and Computers* 8(1), 1998, [doi:10.1142/S0218126698000043](https://doi.org/10.1142/S0218126698000043); the
  [Workflow Patterns](http://www.workflowpatterns.com/) catalog (sequence, exclusive choice, parallel split
  and join, arbitrary cycles).
- **Design by Contract.** A step's inputs and outputs are its pre- and postconditions (ADR-0044 ruling Q2).
  B. Meyer, "Applying Design by Contract", *IEEE Computer* 25(10), 1992,
  [doi:10.1109/2.161279](https://doi.org/10.1109/2.161279).

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
### Responsibility split (ruled)

1. **The charter is the single source of truth for intent and declarations.** It owns the model of a state
   machine (states, transitions, guards, events), its contracts and specifications, and the delivery of pack
   assets, executable code included. Whether a declared action may name such code is open (decision 16).
2. **Running, wiring and enforcing the machine is the client's concern** (today core's CLI). The charter service
   does not run a machine and does not check that a client follows it. Every guard and action below is a
   declaration the client evaluates or performs.

### The kind, its layout and activation (M1, M2, M4; ruled)

3. **`mission_type` is an artifact kind** inside a pack (as ADR-0044 decided for the service), read at
   `/packs/{packId}/artifacts/{kind}/{id}`. The notes write this path with `mission-type`; the contract's `{kind}`
   segment takes the snake_case kind token today (`ArtifactKind.fromToken`), so the spelling is open question 9.
   The `artifacts` path segment stays
   ([ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) decision 2, re-affirmed by the operator).
   *Amended 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 23): the `{kind}` segment uses the kebab operator token, `/packs/{packId}/artifacts/mission-type/{id}`, in `selections/{kind}` and `exclusions/{kind}` too; a breaking change on the 0.x line.*
4. **A mission type is a bundle** (M1). `missions/` is renamed `mission_types/` in the named-packs split
   ([ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/)):

   ```text
   <pack>/mission_types/<type>/<type>.mission-type.yaml
   <pack>/mission_types/<type>/steps/<step>.prompt.md
   <pack>/mission_types/<type>/steps/<step>.guidelines.md
   <pack>/mission_types/<type>/step_contracts/<id>.step-contract.yaml
   <pack>/mission_types/<type>/templates/...
   ```

5. **`mission_step_contract` stays a kind** (M2); its files move into the type's bundle. The contract file name
   above keeps today's file suffix if it differs; only the directory changes.
6. **Activation** (M4). A mission type is the cascade source, and is otherwise an ordinary kind: presets, the
   deny list and listing apply to it as to any kind. No kind token has a special path (core's
   `CHARTER_KIND_TOKENS` mission-type path and `MissionTypeNotAnArtifactKind` go; DIV-501 closes).
   *Built 2026-11-07 (PR A0, see the amendment above): a mission type is selected under `selected.mission_type`, denied
   under `deactivated.mission_type` unless required or locked, and cascades to its steps. "Absent means all" in the
   Status is read as none, with ADR-0053 decision 10. DIV-501 stays `open`: the service still differs from core's
   mission-type path, so it is updated, not closed.*
7. **The shipped types stay in the foundation pack.** Topical packs plug in through `scope` edges or step
   enhancements (decision 22).

### Explicit transitions (proposed direction, now formalized)

8. **A step may carry `transitions`**: a list of `{to, on?, guard?, on_transition?}`.
   - `to` names a step id of the same type, or the reserved terminal `end`.
   - `on` names the triggering event, a free token the client interprets (`next` when absent).
     *Amended 2026-11-02 (operator, #156): the key is `event`, not `on`, because a YAML 1.1 reader takes a plain `on`
     key for the boolean true ([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)); a value a YAML 1.1
     reader takes for a boolean or null is refused. The same holds for lifecycle transitions (decision 17).*
   - `guard` and `on_transition` are decisions 12 and 15.
9. **Absent `transitions` means "to the next step in the list"**, and the last step goes to `end`. Every current
   mission-type file stays valid, and the list order is syntactic sugar for a linear machine (ADR-0044 decision 4
   becomes this default).
10. **Explicit entries add branches and back-edges**, for example `review -> implement` on `changes_requested`.
    When a step declares `transitions`, it declares all of its outgoing transitions; the implicit "next" is not
    added beside them.
11. **`end` is a reserved id**, refused as a step id. The start state is the first item of `steps`.
    `on_demand_steps` stay global transitions reachable by explicit command; they declare no `transitions`, and
    control returns to the state they were invoked from. Inter-step `depends_on` does not come back (ruled, the notes' open
    question 7): explicit transitions plus per-work-package lifecycles cover today's parallel work. A parallel
    region (fork and join) can be added when a mission type needs one.

### Guards and the `gate` kind (the notes' section 7, ruled; gate shape proposed)

12. **A guard is a named condition that must hold before a transition is taken.** Transitions carry their guards:
    `guard: [{gate: <gate-id>, config?}]`, in mission types and lifecycles alike. The charter declares it; the
    client evaluates it.
    *Amended 2026-11-02 ([ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/) decision 2): the entry is `guard: [{ref, config?, blocking?}]`, where `ref` names a guard artifact.*
13. **Blocking expected artifacts are the simplest built-in guard.** A blocking `expected_output_artifacts` entry
    guards every transition out of its step, and a blocking `expected_input_artifacts` entry guards every
    transition into it. Existing files keep their meaning unchanged.
14. **`gate` is a declared kind** (#156; the notes' recommended default, pending ruling): `gates/<id>.gate.yaml` with
    `schema_version`, `id`, `title`, `description`, `severity` on the shared `smell` / `severe` / `breaking`
    ladder ([ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/); to align with
    core's ladder, spec-kitty/spec-kitty#3416), and an optional `config_schema` (a JSON Schema the `config` of a
    guard entry is checked against at load, #164 Q3). #155's versioned gate-binding schema becomes the `guard`
    field and closes into it.
    *Superseded 2026-11-02 by [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/): no `gate` kind; a guard is an artifact `implemented_by` an executable asset or a closed built-in, run to the guard/1 interface, failing closed.*

### Actions (proposed direction, now formalized)

15. **Three action points.** A step (and a lifecycle state) may carry `on_enter` and `on_exit` (Moore-style), and a
    transition may carry `on_transition` (Mealy-style).
16. **An action only emits.** Each action point is `{emit: [<event>]}`, where `<event>` names an event type as the
    joined string or as `{namespace, type}`, resolved against an event registry in the pack's `depends_on`
    closure ([ADR-0051](/adr/0051-event-registry-a-thin-provider-neutral-consistency-check/)). The client sends
    the events, fills their payload, and anyone may subscribe (an org's review cluster, for example). An action
    that names a code asset for the client to run is not in this cut (the notes' question 10; open question 3 below); it would need
    the trust work of #157.
    *Amended 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 17): an action may also name a code asset the pack delivers, for the client to run, behind the trust policy of #157.*

### The `lifecycle` kind (ruled: option a)

17. **A lifecycle is its own declared, reusable state machine in a pack**, kind `lifecycle`, at
    `lifecycles/<id>.lifecycle.yaml` (proposed), read at `/packs/{packId}/artifacts/lifecycle/{id}`. Proposed
    shape:
    - `schema_version`, `id`, `title`, `description`;
    - `initial`: a state id;
    - `states[]`: `{id, title, description?, terminal?, on_enter?, on_exit?}`;
    - `transitions[]`: `{from, to, on?, guard?, on_transition?}`, where `from` may be a list of states or `"*"`
      for a transition allowed from every non-terminal state (core's `blocked` and `canceled`).

    *Amended 2026-11-02 (operator, #156): the event key is `event`, as in decision 8. `"*"` means every non-terminal
    state other than the transition's own target, so `"*" -> blocked` declares no `blocked -> blocked` loop, and it
    must reach at least one state. A lifecycle declares each (from, to, event) once with `"*"` expanded: an explicit
    edge that repeats a `"*"` edge is refused, and a source that needs other guards is listed instead of `"*"`. The
    action points (`on_enter`, `on_exit`, `on_transition`) are added with the event registry (work breakdown item 7).*

    *Amended 2026-11-03 (operator, #156): a lifecycle is also refused when (a) no state is terminal, (b) a state is
    not reachable from `initial` through the transitions, `"*"` expanded as above, or (c) one state and event lead to
    two different targets. An absent event counts as `next`, and `"*"` is expanded, so a `"*"` edge with no event
    clashes with every other edge from the same state that names no event; give such an edge an event.*
18. **A mission type references a lifecycle from the steps that work on work packages**, with
    `work_package_lifecycle: <lifecycle-id>` on the step (proposed field name). In statechart terms the step holds a
    submachine state, instantiated once per work package. One lifecycle can serve several types, and a type may
    pick a lighter one. The step's exit guard may require every instance to be in a terminal state.
    *Amended 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 19): the field is `submachine: {lifecycle: <id>, per: work_package, add_guards?}`.*
19. **Storage and reads are separate concerns.** Storage keeps the mission type and the lifecycle as separate
    artifacts linked by reference. A read may aggregate them (a mission-type read with its lifecycle embedded) to
    keep callers simple; the service architecture allows such CQRS-style splits. The shipped work-package
    lifecycle is core's lane machine as a pack artifact in the foundation pack, authored from `wp_state.py`'s edge
    and guard tables (`initial: genesis`; terminal `done` and `canceled`; ten guards such as `subtasks_complete` and
    `reviewer_approval`, each a declared gate). Core keeps executing it.
20. **Forcing a transition stays the client's mechanism.** A lifecycle declares the machine, not its bypass. Core's
    `force` rules, including the two refusals that cannot be forced, stay in core in this cut; declaring them is open
    question 6 below.
21. **The lane names are shared with the events provider.** While core's status events carry lane names, a
    lifecycle whose states differ from `spec_kitty_events.status.Lane` cannot be used by core without a change on
    that side. The charter does not check this (decision 2); the shipped lifecycle uses the lane names as they are.

### Extension from another pack (M3, ruled)

22. **A step enhancement extends a step without copying it.** This is the M3 ruling's add-only step enhancement, a
    mechanism of its own: the CRG's `enhances` relation keeps its field-merge meaning for the other kinds
    (profiles, for example), and a step enhancement is authored as a file (decision 23), not as that relation. A specialist or org pack attaches more to an existing step:
    references (guidance), guards on the step's outgoing transitions, and `emit` entries on its action points.
    *Amended 2026-11-05 ([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/) decision 12): an enhancement holds no `references`; guidance it adds to a step is an edge in the enhancing pack's graph documents.*
    An enhancement only adds. It cannot remove or reorder steps, change their transitions, or remove a guard.
    *Amended 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 22): the enhancement is an artifact in the enhancing pack that holds only the additions, joined to the target step by a CRG `enhances` edge, the same mechanism profile augmentation uses. It is no longer a mechanism of its own, so the sentence above that separates it from the relation no longer holds; the add-only rule stands.*
23. **How an enhancement is authored** (proposed): a step-enhancement file in the enhancing pack,
    `mission_types/<type>/enhancements/<id>.step-enhancement.yaml`, with `target: action:<type>/<step>` and the
    additions. The target's pack must be in the enhancing pack's `depends_on` closure. As ADR-0048 decision 9
    stands, two sibling packs that both enhance or override one step are refused unless the distribution pack
    settles it. Because a step enhancement only adds, letting sibling enhancements compose (a union, independent
    of load order) is recommended; it needs an operator ruling on ADR-0048 decision 9 (open question 7).
    *Amended 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 22): a step enhancement is an artifact in the enhancing pack holding only the additions, plus a CRG `enhances` edge to `action:<type>/<step>` (pack-qualified as `<pack>::action:<type>/<step>` when the step is in another pack, ADR-0053 decision 7), the mechanism profile augmentation uses; add-only for steps. Sibling enhancements are refused unless settled ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 21).*
24. **`overrides` replaces a step or a whole type**: whole replacement, with a drift warning when the replaced
    original changes (#164 D2). Only an override may remove a guard or change a transition.
25. **A new workflow is a new mission type**, not an enhancement.

### Lower-impact items (recommended defaults from the notes, pending ruling)

26. **Requirement kinds** (#164): core #5956 D1 to D5 are written into the mission-type schema. Mission types and
    steps link vocabulary through `term:` URNs
    ([ADR-0049](/adr/0049-glossary-contexts-and-terms/); #164 D1/Q5).
27. **#161** (`mission.yaml` and `mission-runtime.yaml` in a non-built-in pack level) is moot under ADR-0044's single
    file; the default is to confirm and close it.
    *Ruled 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 24): closed as moot.*
28. **#153** (mission-instance addendum layer): the default is to park it and close it as not planned until a need
    shows.
    *Ruled 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 24): closed as not planned.*

<!-- markdownlint-enable MD029 -->

## Consequences

### Positive

- Branches, rework loops and per-work-package lanes are declared, so a pack can describe a non-linear workflow,
  and core's lane machine becomes data.
- An org adds a gate or an event to an existing step without copying the mission type.
- Every current mission-type file stays valid: the linear default is the old meaning.

### Negative

- The mission-type schema grows (transitions, guards, actions, the lifecycle reference), and two kinds
  (`lifecycle`, `gate`) and one file family (step enhancements) are added.
- The charter declares guards it never evaluates; a client that ignores them is not caught by the service.

### Risks

- Moving `missions/` to `mission_types/` changes ADR-0044's fixed path rule and the payload rule
  `*/missions/*/templates/**/*`; a template left under an unmatched path is unclassified and silently dropped, so
  the rename must land with the file-kinds change and the split, in one re-pin.
- A step enhancement is a new authoring surface; if it grows fields beyond "add", it becomes a hidden override.
  The schema keeps it closed to the additions in decision 22.

### Follow-ups

Nothing below is applied in this record; each lands in a later contract-family PR.

- **Pack schemas** (#114): `mission-type.schema.json` gains `transitions`, `on_enter`, `on_exit`,
  `work_package_lifecycle`, `terms`, `rejects` (*not added:
  [ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/) decision 12*) and the requirement
  kinds; new `lifecycle.schema.json`,
  `gate.schema.json` and `step-enhancement.schema.json`. `mission-step-contract.schema.json` loses `gates` and
  `$defs.GateBinding` (whose `on_transition` is a lane string, a second meaning of the name this record gives an
  action point); an existing binding migrates to a `guard` on the matching lifecycle transition. The vocabulary
  sites gain `lifecycle` and `gate`: `file-kinds.yaml` node, artifact and fragment kind lists and new `files` rules
  (`*.lifecycle.yaml`, `*.gate.yaml`, `*.step-enhancement.yaml`, hidden-file exclusions for `step_contracts/` and
  `enhancements/`), the URN alternations of `graph-shard` and `org-fragment`, and the `pack-manifest` constituent
  kind enum.
- **Open shapes.** A guard's `config` and a gate's `config_schema` are open maps, which sanitized-shape rule S4
  refuses ([ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/)). They need an S4
  exemption, which no record states yet (`structural_lint_config` and `tooling` are classified as open maps only in
  [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)'s comparison table). The API carries them as
  canonical JSON text, as `Styleguide` does for `structuralLintConfig`.
- **Retired-rule reasons.** After the `mission_types/` rename (decision 4 of this record, ADR-0048 decision 16), the
  `retired` rules in `contracts/packs/file-kinds.yaml` that say the mission-type file "moved to
  `missions/<id>/<id>.mission-type.yaml`" are reworded to name the new layout.
- **Contract, additive** ([ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/)): the new
  `lifecycle` and `gate` paths, and an embedded-lifecycle representation of the mission-type read.
- **Contract, breaking on a 0.x minor** (a `### Breaking` entry, ADR-0022): new values in `NodeKind`, and, for
  activatable kinds, `ActivatableKind`, `SelectableKind`, `CascadeKind`, `SelectedArtifacts` and
  `DeactivatedArtifacts`; new members of the closed `Artifact` `oneOf`; new per-kind not-found and compose-refusal
  codes. Making `mission_type` ordinary also changes `ActivationConfig.missionTypes` (absent today means none; an
  ordinary kind's absent means all, or `missionTypes` retires for `selected.mission_type`), adds
  `DeactivatedArtifacts.mission_type`, and adds `mission_types` to the preset's `activated_kinds`.
- **Kind facts** ([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/), `ArtifactKind`): recommended,
  `lifecycle` and `gate` are not activatable on their own (a mission type that references them brings them into
  force), not org-requirable and not selection-overlayable; the logical model's kind table records the facts when the kinds are built. Java paths that hardcode `missions/`
  (`PackFileSet`, `MissionTypeBinding`, `TemplateBinding`) follow the rename.
- **spec-kitty-open-packs**: the `missions/` to `mission_types/` move, the step contracts into the bundles, and the
  work-package lifecycle authored from core's lane table.
- **Core** (post-cutover): run the declared lifecycle instead of `status/transitions.py`, evaluate declared guards,
  send declared emits, and drop the mission-type special path.

## Open questions

This record is accepted; the questions below still await an operator ruling, each with a recommended default. The
decisions above use the default until then.

*Ruled 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/)): every question below is ruled; each ruling follows its question.*

1. **Terminal state name** (decision 11): reserved `end`.
   **Ruled 2026-11-02:** `end` ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 15).
2. **The `gate` kind and its shape** (decision 14): a declared kind with severity and `config_schema`, as above.
   **Ruled 2026-11-02:** no `gate` kind; see [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/) ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 16).
3. **Action code assets** (decision 16, the notes' question 10): not in the first cut; actions emit only.
   **Ruled 2026-11-02:** yes, behind trust (#157) ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 17).
4. **Lifecycle file layout and shape** (decision 17): as above.
   **Ruled 2026-11-02:** as proposed ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 18).
5. **Lifecycle reference** (decision 18): `work_package_lifecycle` on the step.
   **Ruled 2026-11-02:** the generic `submachine` field ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 19).
6. **Declaring `force`** (decision 20): not in this cut; core keeps its rules.
   **Ruled 2026-11-02:** not in this cut ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 20).
7. **Sibling step enhancements** (decision 23): let add-only enhancements compose, narrowing ADR-0048 decision 9
   to `overrides` for steps.
   **Ruled 2026-11-02:** refused unless the distribution pack settles them; ADR-0048 decision 9 stands ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 21).
8. **How an enhancement is authored** (decision 23): a step-enhancement file in the type's bundle path.
   **Ruled 2026-11-02:** an additions artifact plus a CRG `enhances` edge ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 22).
9. **The `{kind}` path token spelling** (decision 3): the snake_case kind token (`mission_type`, `event_registry`,
   `anti_pattern`) everywhere, as the contract and `ArtifactKind.fromToken` accept today and as the closed
   `SelectableKind` and `ActivatableKind` enums spell it. Reading the notes' `mission-type` as that token costs
   nothing; the hyphenated operator token would break `selections/{kind}`, `exclusions/{kind}` and the URN kinds, and
   would split the token between those and the artifact paths. The artifact `{kind}` parameter is a slug
   (`^[a-z0-9][a-z0-9_-]*$`), so it admits a hyphen.
   **Ruled 2026-11-02:** the kebab operator token ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 23).
10. **Lower-impact items** (decisions 26 to 28): as stated there.
    **Ruled 2026-11-02:** all three defaults ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 24).
11. **One `emit` spelling** (decision 16): the notes rule two spellings (the joined string or `{namespace, type}`),
    which sanitized-shape rule S6 refuses for stored files. Recommended: both are accepted when authored, and the
    service's writer stores `{namespace, type}`, a change of form that keeps the meaning
    ([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)).
    **Ruled 2026-11-02:** either form when authored; stored as `{namespace, type}` ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 25).

## Alternatives considered

### Keep the list order as the only order (ADR-0044 as is)

Rejected. Rework loops and work-package lanes stay hardcoded in core, and inserting a step means replacing the
whole type.

### Inter-step `depends_on` (a DAG, as core's runtime `PromptStep` had)

Rejected for now (the notes' question 7, ruled). A DAG expresses order and parallelism but not branches, guards or
back-edges; explicit transitions do, and parallel work per work package is the lifecycle's.

### The work-package lifecycle inside the mission type (option b) or left in core (option c)

Rejected by the operator in favour of option a: a separate kind can be reused by several types and swapped for a
lighter one.

### Actions that run pack code

Not in this cut (the notes' question 10; open question 3). Emitting events keeps the charter declarative and needs no trust model.

## References

- [Pack ecosystem design notes](/plans/pack-ecosystem-design-notes/), sections 4 and 7.
- Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142); #148, #155, #156, #161, #164, #153;
  #157 (trust for delivered code).
- Core: spec-kitty/spec-kitty#3416 (severity ladder), #5956 (requirement kinds), `status/transitions.py`.
- The theory references are in "Theoretical origins" above.
