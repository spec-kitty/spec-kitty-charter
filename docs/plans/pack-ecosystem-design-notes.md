---
title: Pack ecosystem design notes
description: "Working notes of the operator design pass on epic #142: the rulings made so far, not yet formalized as ADRs."
doc_status: draft
updated: 2026-11-02T00:00:00Z
type: Plan
kind: Planning
related:
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0019-packs-resource-model-and-hypermedia
  - architecture/logical-model
audience:
  - profile: governance-lead
    guidance_text: Read this for the pack-ecosystem rulings made so far and the questions still open.
  - profile: charter-service-contributor
    guidance_text: Read this before formalizing any of these rulings; the ADR pass works from this page.
---

# Pack ecosystem design notes

Working record of the operator design pass on epic
[#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142), started 2026-11-02. These are rulings
in conversation, **not yet formalized**. The architectural, planning and debrief pass turns them into
ADRs and issues. Composition and the named-packs split are already drafted as
[ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/) (Proposed).

## 1. Composition and the split (drafted in ADR-0048)

- `depends_on: [{pack_id, range}]`. Versions are per pack. Artifact links stay unversioned, and the
  CRG closure check covers consistency. Skills keep their own `version`.
- `parent_pack` is lineage only. It must resolve to a registered pack, and the level rule applies
  (project < org < built-in).
- Packs compose into a graph that may form diamonds. A shared dependency loads once, and the version
  ranges on it must overlap. Specialist packs only add. Sibling overrides are refused unless the
  distribution pack settles them.
- Topical packs are full top-level packs (`foundational`, `writing`, `java`, `drupal`, …).
  `built-in` is a distribution pack with no artifacts of its own, and keeps the presets.
- The foundation pack keeps the shipped Mission types and base agent profiles, and is slimmed. Stack
  packs are opt-in.

## 2. Glossaries

- **Kind.** `glossary_pack` is renamed to `glossary`. The artifact is a **glossary context**, a
  ubiquitous-language bounded context modelled close to Contextive:
  - `id`, `name`;
  - `description` (its applicability, Contextive's domain vision statement);
  - `tags`;
  - optional `applies_to` (`paths` globs, `languages`, stacks once #163 lands);
  - `terms[]`.
- **Term.** An item inside a context, not an artifact on its own:
  - `id`, `name`, `definition`, `examples`, `aliases`;
  - `avoid` (synonyms not to use);
  - `status`;
  - `see_also` (term URNs).
  - Its URN is `term:<context-id>/<term-id>`, so its link to the context is structural.
  - `confidence` and `introduced_in_mission` are dropped.
- **Reads.**
  - `GET /packs/{packId}/artifacts/glossary` lists the contexts (id, name, description, tags, term
    count).
  - `GET /packs/{packId}/artifacts/glossary/{contextId}` returns one context with its terms.
  - Reading every active term across packs belongs on the active-charter side.
- **Scope** comes from the pack level. Core's four hardcoded glossary scopes are retired.
  `mission_local` stays a runtime concern on the Mission instance.
- **Clashes.** Two contexts may define the same name. Only a clash on the same `term:` URN is a
  conflict: precedence by level with a warning, and a clash at the same level is refused unless it is
  settled explicitly.
- **One source.** The pack glossary is the only authored source of vocabulary. Contextive YAML and
  the terminology gates' banned lists (fed by `avoid`) become projections of the active contexts.
  Mission types and steps link to `term:` URNs (#164's D1/Q5 ruling).
- **Core's `src/glossary`.**
  - The model and storage parts are replaced by the artifact kind, read through the seam.
  - The terminology check (`chokepoint`, `strictness`, `conflict`) stays as a consumer.
  - The learning and clarification pipeline is retired, subject to a census of what is live.
  - This is post-cutover core work.

## 3. API paths

- The `artifacts` segment in `/packs/{packId}/artifacts/{kind}/{artifactId}` **stays**
  (ADR-0019 D2). It keeps kinds apart from the other pack sub-resources, gives the cross-kind listing
  a home, and lets one route serve every kind.

## 4. Mission types

- **Kind.** `mission_type` is an artifact kind inside a pack, served at
  `/packs/{packId}/artifacts/mission-type/{id}`. This matches ADR-0044. Core aligns with it (#148) and
  retires `MissionTypeNotAnArtifactKind` and DIV-501.
- **M1, layout.** A mission type is a bundle artifact: `mission_types/<type>/` holds the type file,
  `steps/` and `templates/`. `missions/` is renamed to `mission_types/` during the named-packs split.
- **M2, step contracts.** `mission_step_contract` stays a kind. Its files move into the type's bundle
  (`mission_types/<type>/step_contracts/`).
- **M3, extending from another pack** (operator refinement):
  - **Replace** a step or a whole type with `overrides`: whole replacement, with a drift warning when
    the replaced original changes (the #164 D2 rule).
  - **Extend or enhance** a step with `enhances`: a specialist or org pack attaches more guidance to an
    existing step without copying it. For example, an org links the `review` step to its internal
    static-analysis or review-agent cluster.
  - An enhancement only adds. It cannot remove or reorder steps, or change their transitions.
  - A new workflow is a new mission type.
- **M4, activation.** A mission type is the cascade source, and is otherwise an ordinary kind:
  presets, the deny list and listing all apply. Core's special `CHARTER_KIND_TOKENS` path goes.
- **The shipped types stay in the foundation pack.** Topical packs plug in through `scope` edges or step
  enhancements.

### Responsibility split (operator ruling, 2026-11-02)

- **The charter is the single source of truth for intent and declarations.** It owns the model of a
  state machine (states, transitions, guards, events), its contracts and specifications, and the
  delivery of pack assets, executable code included.
- **Running, wiring and enforcing it is the client's concern** (today core's CLI). The charter service
  does not run a machine and does not check that a client follows it.

### Work-package lifecycle (operator ruling, 2026-11-02)

- **A kind of its own (option a).** The work-package lifecycle core runs today
  (`planned -> claimed -> in_progress -> for_review -> in_review -> approved -> done`, plus `blocked`
  and `canceled`, with guards) becomes a declared, reusable `lifecycle` state machine in a pack.
  - It is hardcoded in core today (`status_lanes`, `status/transitions.py`).
  - Core keeps executing it, by the responsibility split.
- **Mission types reference a lifecycle.** The Mission type is the outer machine (steps). The
  lifecycle is a sub-machine, instantiated once per work package inside the steps that work on work
  packages. In statechart terms, it is a submachine state.
  - One lifecycle can serve several types, and a type may pick a lighter one.
- **Storage and reads are separate concerns.** Storage keeps the two as separate artifacts linked by
  reference. The API may aggregate them into one response, for example a mission-type read with its
  lifecycle embedded, to keep callers simple. The service architecture exists to allow MVC/CQRS-style
  splits between display, storage and domain logic.

### Event declarations (Q9, proposal under discussion)

**Findings in spec-kitty-events.**
- The catalogue is `producer_contract/registry.json` (`registry_version` 1.1.0, non-normative until
  the ADR 0001 cut-over).
  - It has 36 CloudEvents types under the prefix `ai.spec-kitty`, each versioned by suffix
    (`...v1`).
  - Each type has a closed data schema addressed by a `urn:ai.spec-kitty:schema:` dataschema URN,
    plus scope and subject rules.
  - A shared envelope profile and conformance fixtures sit beside the registry.
- **Some core event types name software-dev's steps:**
  `ai.spec-kitty.mission.{specify,plan,tasks}.{started,completed}.v1`. A pack-declared mission type
  with other steps has no event of its own. Generic step events are needed (the
  `run.step.issued` and `run.step.autocompleted` events are close).

**Proposal:**
- **E1, two namespaces, two sources of truth.** spec-kitty-events owns the core namespace
  `ai.spec-kitty.*`. A pack may declare domain event types only in its own reverse-DNS namespace
  (for example `com.acme.review.requested.v1`), never inside `ai.spec-kitty`. This follows the
  CloudEvents convention for the `type` attribute
  ([CloudEvents spec](https://github.com/cloudevents/spec/blob/main/cloudevents/spec.md)).
- **E2, a declared kind.** A pack declares its event types as artifacts (an `event_type` kind): type,
  version suffix, a closed data schema, scope and subject. For pack events the charter is the source
  of truth. The charter checks each declaration against the envelope profile of the pinned events
  contract.
- **E3, pinning.** A pack declares the range of the core catalogue it targets
  (`requires_events: <range>` against `registry_version`), the same way `depends_on` works. The charter
  pins one events contract release. An `emit` must resolve to a core type within range, or to a type
  declared by the pack or a pack in its `depends_on` closure. Otherwise the load is refused.
- **E4, generic standard events.** The standard events the client always emits (step entered, step
  exited, transition taken) must not depend on the mission type: the step and type travel in the data,
  not in the type name. The step-specific core types are deprecated in favour of the generic ones.
  This is an issue to raise on spec-kitty-events.
- **E5, payload.** An `emit` entry's payload is filled from the transition context (Mission id, step
  id, work-package id, actor) and checked against the type's data schema where that is possible at
  load.
- **The client emits.** A client emits both core and pack events through its own transport (the
  responsibility split).

**Revision after operator review (2026-11-02): no coupling to any events provider.** The charter
service must not depend on spec-kitty-events or any other events provider. E1 to E3 are replaced by:

- **R1, event contracts are registered.** An events contract is an artifact (kind `event_contract`),
  registered into a pack through the ordinary registration route (ADR-0026:
  `PUT /packs/{packId}/artifacts/event-contract/{id}`), and read like any other artifact.
  - The service knows the *shape* of a contract, never who publishes it.
  - spec-kitty-events becomes one registrant among others: its release registers, or is vendored as,
    a pack holding the `ai.spec-kitty` contract.
- **R2, a neutral shape.** A contract declares:
  - its `namespace`, a reverse-DNS prefix it claims;
  - its envelope format as a token (for example `cloudevents/1.0`), stored and returned but not
    interpreted;
  - `types[]`, each with `type`, `version`, `description` and a closed JSON Schema for its data.
  - Ingesting an [AsyncAPI](https://www.asyncapi.com/docs/reference/specification/latest) document
    could come later as an import format.
- **R3, ownership of a namespace.** A namespace is claimed by exactly one contract in a compose.
  Overlapping claims are refused. Reserved namespaces, if any, are deployment configuration, not
  code.
- **R4, resolution.** A pack that emits events lists the contract's pack in `depends_on`, with a
  version range, like any other dependency. An `emit` must resolve to a type in a contract within the
  pack's `depends_on` closure. Otherwise the load is refused. There is no separate pin mechanism.
- **R5, standard events stay out of the charter.** Which lifecycle events a client always sends is
  the client's concern. A lifecycle or mission type may *declare* them in `on_enter` and `on_exit` like
  any other event, but the service holds no built-in list.
- **E4 stays an issue for the events provider:** step-specific core event types do not fit declared
  mission types. E5 (payload from the transition context) is unchanged.

**Second revision (operator, 2026-11-02): a thin event registry for consistency only.** R1 and R2 are
replaced:

- **A pack may contain an `event_registry` artifact.** It only states, for each entry, a
  `namespace`, a `type` and a `name`. There are no data schemas, no envelope format and no payload
  rules.
- **Its only purpose is a convenience check:** an `emit` in a mission type or lifecycle must name an
  entry of a registry in the pack's `depends_on` closure (R4). Namespace overlaps between registries
  are refused (R3).
- **The client is responsible for sending, shaping and using events.** E5 (payload from context)
  moves to the client, and the charter does not check payloads.
- R5 stands: no built-in standard events in the charter.
- **Entry shape (ruled, option b):**
  - `namespace` is the reverse-DNS prefix (`com.acme`);
  - `type` is the event's local name with its version (`review.requested.v1`);
  - `name` is the human label ("Review requested").
  - The full identifier is always `namespace + "." + type` and is never stored. An `emit` names it
    either as the joined string or as `{namespace, type}`.

### Mission type as a state machine (client-side use)

On the client, a mission type is the **description of a finite-state machine**: it must say which
states exist, which transitions connect them, what guards a transition, and what happens when one is
taken.

**Theoretical origins**, for the logical-model docs:

- **Finite-state machines and transducers.** States, an input alphabet, and a transition function.
  - In a Mealy machine, output (an action) belongs to the transition.
  - In a Moore machine, output belongs to the state.
  - References: [Finite-state machine](https://en.wikipedia.org/wiki/Finite-state_machine);
    G. H. Mealy, "A Method for Synthesizing Sequential Circuits", *Bell System Technical Journal*
    34(5), 1955, [doi:10.1002/j.1538-7305.1955.tb03788.x](https://doi.org/10.1002/j.1538-7305.1955.tb03788.x);
    E. F. Moore, "Gedanken-Experiments on Sequential Machines", *Automata Studies*, 1956.
- **Harel statecharts.** Hierarchical (nested) states, guards on transitions, entry and exit actions,
  and parallel regions.
  - A step with its step-contract sub-steps is a composite state.
  - Reference: D. Harel, "Statecharts: a visual formalism for complex systems", *Science of Computer
    Programming* 8(3), 1987,
    [doi:10.1016/0167-6423(87)90035-9](https://doi.org/10.1016/0167-6423(87)90035-9).
  - Standardized as [UML state machines](https://www.omg.org/spec/UML/2.5.1/) (OMG UML 2.5.1, clause 14)
    and as [W3C SCXML](https://www.w3.org/TR/scxml/) (2015), which defines `<transition event cond target>`,
    `<onentry>` and `<onexit>`.
- **Workflow nets** (Petri nets applied to workflow). Pre- and postconditions as places and tokens,
  and parallel branches with joins.
  - The step's `expected_input_artifacts` and `expected_output_artifacts` are exactly this.
  - Reference: W. M. P. van der Aalst, "The application of Petri nets to workflow management",
    *Journal of Circuits, Systems and Computers* 8(1), 1998,
    [doi:10.1142/S0218126698000043](https://doi.org/10.1142/S0218126698000043).
  - Catalogue: [Workflow Patterns](http://www.workflowpatterns.com/) (sequence, exclusive choice,
    parallel split and join, arbitrary cycles).
- **Design by Contract.** A step's inputs and outputs are its pre- and postconditions (ADR-0044
  ruling Q2).
  - B. Meyer, "Applying Design by Contract", *IEEE Computer* 25(10), 1992,
    [doi:10.1109/2.161279](https://doi.org/10.1109/2.161279).

**How the current shape maps** (ADR-0044):

| FSM concept | Today |
|---|---|
| State | a `steps[]` item, plus the implicit start and terminal (mission created and completed) |
| Composite state | a step with a `mission_step_contract` (sub-steps) |
| Transition | **implicit**: list order, step *i* to *i+1* (ADR-0044 decision 4, "the list order is the only order") |
| Guard | blocking `expected_input_artifacts` (entry) and blocking `expected_output_artifacts` (exit) |
| Triggering event | external: the runtime's `next` (`NextStepIssued` in spec-kitty-events) |
| Global transitions | `on_demand_steps`, reachable from any state by an explicit command |
| Branches, rework loops, parallel regions | **not modelled**. Rework lives in core's separate, hardcoded work-package lane machine (`status/transitions.py`) |
| Actions (entry, exit, transition) | **none** |

**Gaps:**

- **Transitions are positional.** They cannot be named, guarded on anything but artifacts, or
  extended. Inserting a step means replacing the whole type.
- **Core's runtime schema already had more.** Its `PromptStep` and `AuditStep` carry `depends_on`
  (a DAG), RACI and significance. The sanitized charter shape dropped `depends_on` for the list order.
- **No loop is modelled.** A review rejection back to implementation is a back-edge that no mission
  type declares.

**Proposed direction** (to refine):

- **Explicit transitions.** Each step may carry
  `transitions: [{to, on?, guard?, on_transition?}]`. Absent means "to the next step in the list",
  so every current file stays valid and the list order is syntactic sugar for a linear machine.
  Explicit entries add branches and back-edges. There is an explicit terminal state.
- **Entry and exit actions.** `on_enter` and `on_exit` on a step (Moore-style actions), beside
  `on_transition` (Mealy-style actions).
- **Actions are declarations.** An action is `emit: [{event_type, payload?}]`: the declaration says
  which events a transition produces, and the client sends them. An org's review cluster *subscribes*
  to the event. Whether a declared action may also name a code asset the pack delivers, for the client
  to run, is open (question 10). Running it would be the client's decision, gated by the trust work in
  #157.
- **Event types are validated at ingestion** against the event catalogue of the pinned
  spec-kitty-events contract. An unknown type is refused.
- **The engine always emits the standard lifecycle events** (step entered, step exited, transition
  taken). A pack declares only *additional*, domain-specific events.
- **Enhancement may add actions** (M3). An `enhances` may append `emit` entries and references to an
  existing step, but may not change its transitions.

## 5. Procedures (#150, ruled 2026-11-02)

- **`procedure` stays a kind** (option 1 of #150).
- **Its definition is tightened** with a check: a procedure may not produce the Mission's
  deliverables, may not span steps, and may not be a rule.
- **Sorting of the 22 shipped procedures** (by title and description; the census confirms against
  the bodies):
  - **About 15 fit:** `disciplined-defect-diagnosis`, `test-first-bug-fixing`, `refactoring`,
    `example-mapping-workshop`, `bdd-scenario-lifecycle`, `event-storming-discovery`,
    `domain-aware-decision-interview`, `situational-assessment`, `legacy-codebase-triage`,
    `documentation-gap-prioritization`, `drill-down-documentation`, `adversarial-squad-deployment`,
    `development-assist-test-cleanup`, `post-merge-arch-gate-adjudication`,
    `glossary-maintenance-workflow`.
  - **Re-homed during curation:**
    - `spike-timebox-policy` and `mission-tracer-files` become directives, with tactics for the
      technique;
    - `issue-triage-state-machine` becomes a `lifecycle`;
    - `mission-wrap-up-sequence` becomes a mission-type step (on demand) or its own mission type;
    - `onboard-external-agent-to-pack`, `migrate-project-guidance-to-spec-kitty-charter` and
      `meeting-minutes-pipeline` become small mission types, or the last stays a procedure of a
      `writing` pack if it is kept small.
  - **Internal-pack candidates** (how the Spec Kitty team works, not product guidance):
    `mission-tracer-files`, `mission-wrap-up-sequence`, `post-merge-arch-gate-adjudication`, and
    possibly `adversarial-squad-deployment`.
- Every procedure carries inline `anti_patterns`, so the ruling on #154 applies to procedures too.

## 6. Anti-patterns (#154, ruled 2026-11-02)

- **AP1, its own file.** An anti-pattern is authored as its own artifact,
  `anti_patterns/<id>.anti-pattern.yaml`, with title, description, bad and good examples, and
  severity.
  - Artifacts reference it with `rejects` references `{type: anti_pattern, id, reason}`.
  - The 131 inline blocks (18 styleguides, 16 procedures) are migrated out and deduplicated in a
    curation pass.
  - This reverses the 3.x squad's "keep them inline" approach. Its reason (graph nodes could not hold
    a description) no longer holds under the sanitized storage shapes (ADR-0043).
- **AP2, severity.** `smell`, `severe` or `breaking`.
- **AP3, placement.** An anti-pattern lives in the pack whose guidance rejects it. Foundation holds
  the general ones. A `rejects` across packs needs `depends_on`.
- **AP4, glossary `avoid`.** Kept separate for now. `avoid` is about words, an anti-pattern is about
  practice. They have different consumers (a lexical terminology check versus guidance and review),
  different shapes, and different activation (terms are not activated alone). A later optional
  cross-link (an anti-pattern's `see_also` to term URNs) is additive and can be added if a need shows.

## 7. Gates and guards (#156, #155, ruled 2026-11-02)

- **Guards are special checks on a transition.** A guard is a named condition that must hold before
  a transition is taken. The charter declares it; the client evaluates it.
- **Binding.** Transitions carry their guards: `transitions: [{to, guard: [{gate: <id>, config}]}]`,
  in mission types and lifecycles alike. Today's blocking `expected_*_artifacts` are the simplest
  built-in guard, so existing files stay valid. #155's versioned gate-binding schema becomes this
  `guard` field and closes into it.
- **Extension** follows the "specialist packs only add" rule. An `enhances` may add guards to a
  transition (for example an org's static-analysis gate before `review -> approved`). It may not
  remove one; only an `overrides` can.
- **Left for formalization** (recommended default): a gate is its own declared kind, with id,
  description, severity on the `smell` / `severe` / `breaking` ladder (to align with core's ladder,
  #3416) and a config schema (#164 Q3).

## Lower-impact items: defaults for the formalization pass

| Item | Recommended default |
|---|---|
| #153 addendum per Mission instance | Park; close as not planned until a need shows. |
| #161 `mission.yaml` and `mission-runtime.yaml` | Moot under ADR-0044's single mission-type file; confirm and close. |
| #163 stack scope | Add `applies_to_stacks`, with the same admission rules as `applies_to_languages`. |
| #164 requirement kinds | Write the ruled design (core #5956 D1 to D5) into the mission-type schema. |
| #183 more than one writer process | Not supported; document the single daemon per worktree. |
| Tier of our opt-in packs | `org`, unless the pass finds a reason for a new tier. |
| `applies_to` on glossary contexts | Advisory, unless the pass finds a reason to filter. |
| Action code assets (question 10) | Not in the first cut; actions emit events only. |
| Range grammar | A minimal semantic-version subset (`^`, `~`, comparison operators). |
| Settling a sibling conflict | The distribution pack declares its own `overrides` edge on the contested artifact. |

## Open questions

1. The tier of our own opt-in packs (`java`, `qa`, …).
2. Foundation membership and the curation cuts (from a census).
3. Whether `built-in` aggregates any topical pack besides foundation.
4. The version-range grammar.
5. The edge form a distribution pack uses to settle a sibling conflict.
6. Whether a context's `applies_to` filters what is in force (like `applies_to_languages`), or is
   only advisory.
7. *(Ruled 2026-11-02, for now.)* Inter-step `depends_on` does not come back. Explicit transitions
   plus per-work-package lifecycles cover today's parallel work. A statechart parallel region (fork
   and join) can be added later if a mission type needs one.
8. *(Ruled, see "Work-package lifecycle" above.)*
9. Where the event catalogue a pack may reference is published, and how its version is pinned.
10. Whether a declared action may reference a code asset the pack delivers, for the client to run.
