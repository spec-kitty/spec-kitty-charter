---
title: "ADR-0078: A role selects its profile, a profile names its performer, and the effective profile is resolved"
description: "Slice G: profile selection by role under an activation and per step, role pins, the human performer, the effective profile with lineage, and the enhancement drop refusal."
doc_status: active
updated: 2026-11-26T00:00:00Z
decision_status: Accepted, with Amendments 2026-11-26 to decisions 5 and 6 on agent-profile references (#673)
decision_date: '2026-11-14'
type: ADR
kind: ADR
related:
  - adr/0022-contract-versions-start-at-0-x
  - adr/0030-removal-is-distinct-from-deactivation
  - adr/0032-steps-name-roles-and-profile-selection-order
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0068-a-pack-declares-its-fallback-profile
  - adr/0069-a-profile-has-one-primary-role-and-optional-alt-roles
  - adr/0077-kind-taxonomy-and-step-authority
  - context/conceptual-model
audience:
  - profile: pack-author
    guidance_text: Read this for how a role pin, a human-performed profile, a profile enhancement and a specialization chain change which profile serves a step.
  - profile: contract-consumer
    guidance_text: Read this for the two selection reads, what the effective profile read now answers, and the refusals each raises.
  - profile: charter-service-contributor
    guidance_text: Read this before you change the selection order, the profile merge, the lineage chain or a profile registration rule.
  - profile: core-maintainer
    guidance_text: Read this for the reads that replace core's weighted profile selection and its profile resolution, and what the generated client changes.
---

# ADR-0078: A role selects its profile, a profile names its performer, and the effective profile is resolved

## Status

**Accepted**, 2026-11-14. Decider: Stijn Dejongh (operator).

- **The rulings.** The operator ruled slice G ([#538](https://github.com/spec-kitty/spec-kitty-charter/issues/538))
  in an interview on 2026-11-14: seven rulings, D1 to D7, recorded in section 6a of the research note of the mission
  `slice-g-profile-resolution-01M7DFBF`. Decisions 1 to 7 below are D1 to D7, in that order, and each is marked
  *(operator, Dn)*. D6 differs from the recommendation that was put to the operator.
- **The squad.** A three-lens post-plan squad (domain, contract, migration) found 2 blockers and 9 majors in the plan.
  All were folded before the rulings were asked (the mission's `research/squad-post-plan.md`). It contested no ruling.
- **Derived, not asked.** Four points follow from the rulings without being asked. They are listed in
  [Flagged for review](#flagged-for-review), and one of them is a known gap.

It closes [#42](https://github.com/spec-kitty/spec-kitty-charter/issues/42) (selection by role),
[#102](https://github.com/spec-kitty/spec-kitty-charter/issues/102) (R8, profile resolution),
[#512](https://github.com/spec-kitty/spec-kitty-charter/issues/512) (the human performer) and
[#380](https://github.com/spec-kitty/spec-kitty-charter/issues/380) (an enhancement that drops fields).

It **amends**, each with a dated note where it stands:

- [ADR-0030](/adr/0030-removal-is-distinct-from-deactivation/) decisions 5 and 7: the role pin's shape;
- [ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/) decision 5 (`operator` is reserved for a
  human-performed profile) and decision 9 (the pin's tie-break is unreachable under the ruled order);
- [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 22: a profile enhancement keeps
  its field-merge meaning; a step merge stays add-only;
- [ADR-0068](/adr/0068-a-pack-declares-its-fallback-profile/) decisions 4 and 7: a fallback profile is
  agent-performed, and the two reserved codes are raised;
- [ADR-0069](/adr/0069-a-profile-has-one-primary-role-and-optional-alt-roles/) decision 4: the order of its steps 1
  to 4 as ruled, and no fallback for `operator` (step 5).

## Context

- **No operation turned a role into a profile.** Steps name roles (ADR-0032 decision 1), and the order was ruled
  (ADR-0032 decisions 3, 8 and 9; ADR-0069 decision 4; ADR-0068), but nothing answered it. The usability run of
  2026-11-14 showed the cost: the client chose `reviewer-renata` by hand before it could reach the review squad's
  procedure.
- **`operator` had no performer.** ADR-0032 decision 5 said a profile can be marked as performed by a human. The
  profile carried only `sentinel: boolean`, a flag core read as "inject no agent identity".
- **The pin had no shape.** ADR-0030 put role pins in the pack's `policy.yaml` and left the shape open, and
  `policy.schema.json` refused the key.
- **The profile read answered the stored leaf.** `getEffectiveAgentProfile` walked the lineage for governance, but its
  body was the stored winner, so an `enhances` edge to a profile changed nothing a client saw, and R8 stayed open.
- **#380.** An enhancement that would drop part of the profile it enhances had a placeholder scenario (`@WIP`) and an
  unbound promise, PR-CHARTER-REFUSAL-001.

## Decision

1. **Two selection reads: keyed by role now, and keyed by step after slice F** *(operator, D1)*.
   - `selectAgentProfile`: `GET /activations/{activationId}/roles/{role}/profile-selection?language=&framework=`
     (tag `EffectiveAgentProfiles`). It answers `ProfileSelectionRead`: the role, the asked context and the
     `candidates`, each with its `id`, the `pack` that holds the version in force, its `fit` (`primary`, `alt` or
     `fallback`, ADR-0068 decision 6) and an `effective-artifact` link, and `tie` when several are answered.
   - `selectAgentProfileForStep`:
     `GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/profile-selection?language=&framework=`. It answers what
     `selectAgentProfile` answers for the role the step names, the step judged as the step and action-context reads
     judge it. A step that names no role answers the fallback in force, so `ProfileSelectionRead.role` is optional.
     The effective step read and the action context link it as `profile-selection`.
   - A substep never delegates to an `agent_profile` (operator, 2026-11-14, ADR-0077 decision 2): a step's profile
     comes only from selection by its role.
2. **The order: every filter first, then the tier, the depth and the priority** *(operator, D2)*.
   - Filters, in this order: the role pin; the performer rule (decision 4); role fit, primary or alt-role; then the
     context. Then the best role-fit tier left, then the greatest depth, then the highest `routing-priority`, 50 when
     absent. More than one left is a tie, answered whole (ADR-0032 decision 8), sorted by closure position and then
     id. Nothing left: the fallback in force, else `404 no_profile_matched`.
   - **Context** is an optional `language` and `framework`. Only a *specialization*, a profile with an in-force
     `specializes_from` ancestor, that declares `languages` or `frameworks` is judged: every asked dimension it
     declares must contain the asked value, and at least one must. A root, a specialization that declares neither, and
     the pinned profile fit any context.
   - **Depth** is the longest in-force `specializes_from` chain; a profile may have several parents. A cycle among a
     closure's profiles is refused at registration with `422 specialization_cycle`, naming the cycle's ids in order.
   - **An enhancer is never a candidate** and never in a lineage. Selection judges each candidate by its own
     declared fields, never inherited ones (decision 6).
   - On the example packs: `implementer` answers `implementer-ivan`; with `language=java`, `java-jenny`; with
     `language=kotlin`, `implementer-ivan`; `reviewer` in org-base, a marked tie.
3. **Role pins are tighten-only, in `policy.yaml` `role_pins`** *(operator, D3)*.
   - The shape is `role_pins: {<role>: <profile-id>}`, in authored order: a role token `^[a-z][a-z0-9-]*$`, never
     `generic`, to a profile id `^[a-z0-9][a-z0-9_-]*$`, each 1 to 64 characters. Role locks stay reserved.
   - A pin narrows the candidates to the pinned profile and its in-force specializations, before the order runs.
   - Refused at registration (`422`), judged over the closure with the write in place: `role_pin_unknown` (the id
     names no profile the closure holds), `role_pin_not_fitting` (the profile neither has the role nor lists it in
     `alt-roles`; no human exemption) and `role_pin_conflict` (two packs of one closure pin one role differently).
   - **Tighten-only.** A differing pin anywhere in the closure is `role_pin_conflict`, whatever the packs' derivation:
     a project does not re-pin the role an org pinned (ADR-0012). The selection reads raise it too (`422`), since two
     unrelated packs can first meet in one activation.
4. **`performer: agent | human` replaces `sentinel`; `operator` is human-only** *(operator, D4)*.
   - `performer` is optional in the profile file and omitted on the wire when the file is silent: the read invents
     no default. Selection reads an absent performer as `agent`. `sentinel` has no reader: a file that carries it is
     refused as any undeclared key of the closed schema is.
   - **`operator` is reserved for a human-performed profile.** A profile whose `role` or `alt-roles` take `operator`
     and that is not `performer: human` is invalid content.
   - **`operator` never falls back.** With no human candidate, the answer is `404 no_profile_matched`.
   - **A human-performed profile serves another role only through a pin**, and the pin must fit (decision 3).
   - **A fallback profile is agent-performed.** A human-performed `fallback_profile` is refused at registration with
     `422 fallback_profile_not_agent`, since a fallback serves every role.
5. **#380: an enhancement replaces fields, and a replacement that loses content is refused** *(operator, D5)*.
   - Profiles keep their field-merge meaning (`profiles.feature`); ADR-0053 decision 22's add-only rule is for steps.
     An enhancer is a complete profile.
   - Its identity fields are its own and never merged: `profile-id`, `name`, `purpose`, `role`, `alt-roles`,
     `performer`, `routing-priority`.
   - Every other top-level field it declares replaces the enhanced profile's. The replacement is refused when it
     loses base content: a list item missing (in any order), an object sub-field missing (recursively), or a scalar
     changed. The approved scenario stands as written, and PR-CHARTER-REFUSAL-001 is wired.
   - The code is `profile_enhancement_drops_field`, naming the enhancing pack first and the field. It answers `422`
     at registration and on each write judged over the closures its pack joins, and `500` on a read of content stored
     before the rule, the stored packs being at fault. The fallback and pin conflicts answer `422` on a read because
     ADR-0068 decision 7 fixed `422` for the conflict; a drop is a fault of stored content, so it keeps the `500`.
   - *Amended 2026-11-26 (operator, #673): a selection reference (a `directive-references` code, a tactic, toolguide
     or styleguide reference id, an operating procedure) is kept by what it names, not by how it is written. A bare
     reference of the enhanced profile names its own pack's artifact, so an enhancer of another pack keeps it by
     writing it qualified with that pack (`built-in::DIRECTIVE_001`); written bare, it names the enhancer's own pack
     and drops it. An `excluding` value under one of those lists' keys is compared by what it names too, and is judged
     as a reference.*
6. **R8 is a contract operation: the effective profile is merged, then inherited** *(operator, D6; differs from the
   recommendation)*.
   - `getEffectiveAgentProfile` answers the effective profile. Each profile on the in-force `specializes_from` chain
     is merged with its own in-force enhancers (decision 5), and the chain is then folded root to leaf by per-field
     replacement: a top-level field the descendant declares replaces the ancestor's, and a field it leaves absent is
     inherited. Identity fields are never inherited.
   - The answer gains the required `lineage`: the in-force ancestors, nearest first, each `{id, pack, depth}`, empty
     for a root. It comes from the governance walk's lineage step, extended once with the ordered chain; there is no
     second `specializes_from` traversal.
   - An enhancer's own effective read answers its stored body and links what it enhances as `_links.enhances`. The
     pack read keeps the stored body.
   - Recommended and not chosen: no inheritance along `specializes_from`.
   - *Amended 2026-11-26 (operator, #673): the effective profile is answered as its own pack writes it. A field
     merged from an enhancer or inherited from an ancestor of another pack has each bare selection reference qualified
     with that pack, and each one qualified with the answered profile's pack written bare, so every reference names
     what it named where it was written (ADR-0053 decision 7).*
7. **`excluding` on an enhancer is a declared drop** *(operator, D7)*. A non-empty `excluding` on a profile that
   enhances another is refused with `profile_enhancement_drops_field`, naming `excluding`. On any other profile it stays
   as authored. Removing the field from the schema is left to a later model pass.
8. **Records and delivery.** One pull request into `main`, held until slice F landed, then replayed onto it. The
   contract break ships in a 0.x minor (ADR-0022): one `### Breaking` fragment (`sentinel` to `performer`, and the
   effective profile's meaning) and `### Added` and `### Changed` fragments for the reads, codes and rules.

### Reviewed calls of the build

These were made in the work packages and approved in their reviews; none changes a ruling.

- **`operator_role_not_human` is not a wire code** (WP01). D4 named it as a new code. The rule is a constraint of the
  entity (`AgentProfile.OPERATOR_ROLE_HUMAN_ONLY`), and the reader reports it as a schema violation (keyword `not`)
  at `/role` or `/alt-roles/<n>`: `422 validation_failed` at registration and `500 pack_unreadable` on a read, as
  ADR-0069 decision 3 does for the other rules a schema cannot state.
- **The asked role is a path pattern that refuses `generic`** (WP05): `400`, with no new code. The pattern is
  RE2-safe, with no lookahead.
- **A mission step's `role` is never `generic` and is at most 64 characters** (WP08, ADR-0068 decision 4, #446), in
  `mission-type.schema.json`, the step's domain rules and `MissionStep.role`. A step never asks for the role that marks
  a fallback, so the step-keyed read never meets it. The first review of WP08 rejected the build for missing this.
- **The refusal order of the selection reads** (WP05, WP08): `404 activation_not_found`; for the step read,
  `404 mission_type_not_found` or `404 action_not_found`; then `422 fallback_profile_conflict`, then
  `422 role_pin_conflict`, then `404 no_profile_matched`. `no_profile_matched` is raised before the `304`.
- **The ETag** names the role, or the step, and the context (`EffectiveTag.SelectionQuery` and `STEP_SELECTION`). The
  effective profile read keeps its inputs and its tag version, `v3`, since no release served the earlier body.

### Flagged for review

Derived from the rulings, not asked. Each is built as written here; a reviewer who disagrees asks the operator.

- **The composition order.** Each profile on the chain is first merged with its own in-force enhancers, and the chain
  is then folded root to leaf. An ancestor's enhancement therefore reaches its descendants unless they redeclare the
  field. The other order, folding first and enhancing the leaf last, would let an enhancer of `implementer-ivan` miss
  `java-jenny`.
- **The fold order of several parents.** The chain holds every in-force ancestor, the deepest first; ancestors at one
  depth are ordered by their pack's closure position and then by URN, and the profile itself comes last. So where two
  parents declare one field, the one later in that order wins. Depth stays the longest chain.
- **D6 supersedes a steer.** The operator's steer of 2026-10-19, "design on its own merits, not as a copy of core's
  call", is superseded for profile inheritance: D6 makes the effective profile a root-to-leaf fold, as core's
  `resolve_profile` is.
- **Known gap: a pin naming a profile out of force.** The spec's edge case says such a pin narrows to the pinned
  profile's in-force specializations, or to nothing. The build narrows to nothing whenever the pinned profile is out
  of force, so the fallback answers (for a role other than `operator`) even when an in-force specialization of it
  exists: a specialization is found through its in-force lineage, which leaves an out-of-force ancestor out. It is
  deferred: no example pack or open-packs content meets it.

### Why ADR-0032 decision 9's tie-break is unreachable

After the pin filter the candidates are the pinned profile P and its in-force specializations. Each specialization
has P on its in-force chain, so its depth is greater than P's. If a specialization is in a better role-fit tier than
P, P is out at the tier step. If it is in the same tier, it is deeper and P is out at the depth step. So P is answered
only when no specialization survives the filters and the tier, and then it is answered alone. P is never among tied
candidates; a tie among P's specializations is answered whole, as decision 8 says. The selection domain has no
tie-break branch for the pin.

## Consequences

### Positive

- A client asks for a role, or for a step, and gets the profile with how it fits; the usability run's review hop needs
  no client-side walk.
- An org pins a role to a human, or to its own specialist, without editing a step, and a project cannot loosen it.
- The effective profile is one read, with its lineage, so core can drop its own `specializes_from` and `enhances`
  walks.
- #380's promise is wired: an enhancement adds, and never silently drops.

### Negative

- **Breaking.** `sentinel` is gone from the pack file and the wire, and `getEffectiveAgentProfile` answers a different
  body. Core regenerates its `AgentProfile` and `EffectiveAgentProfileRead` DTOs and writes `performer`.
- **The packs schemas change.** `agent-profile.schema.json` (`performer` for `sentinel`), `policy.schema.json`
  (`role_pins`) and `mission-type.schema.json` (a step's `role`) change, recorded in `contracts/packs/CHANGELOG.md`.
- **open-packs breaks until it follows.** Its `human-in-charge` profile carries `sentinel: true` and the role
  `human-in-charge`. The drafts are in the mission's `research/open-packs-drafts.md`; open-packs proves its content
  against a pinned charter release (ADR-0070), so `main` is not blocked.

### Risks

- **Inheritance surprises.** A specialization now shows the fields it does not declare from its ancestors, so content
  authored for a stored-leaf read can read differently. The example packs and the corpus test (`ProfileCorpusTest`,
  `ResolvedProfileCorpusTest`) hold the new answers.
- **The known gap above** answers the fallback where the spec expects a specialization.
- **Core aligns later**, through the generated client, with no core-first ordering (charter.md).

## Alternatives considered

### No context in slice G, or all six descriptor lists

Not chosen *(operator, D2)*: `language` and `framework` answer the example table, and only a specialization that
declares them is judged.

### The most derived pack's pin wins

Not chosen *(operator, D3)*: an org that pins `reviewer` to a human means it, so a pin is tighten-only.

### A `200` "served by a human" answer with no profile

Not chosen *(operator, D4)*: `operator` is served by a human-performed profile, selected like any other.

### An add-only recursive merge for profiles

Not chosen *(operator, D5)*: it would read an omission as no drop and needed PR-CHARTER-REFUSAL-001 rewritten.

### No inheritance along `specializes_from`

The recommendation. Not chosen *(operator, D6)*.

### Remove `excluding` from the profile schema

Not chosen *(operator, D7)*: nobody has ruled on the field, and removing it widens this slice.

## References

- Mission `slice-g-profile-resolution-01M7DFBF`: its spec, plan, research note (sections 6 and 6a) and the squads.
- [#538](https://github.com/spec-kitty/spec-kitty-charter/issues/538) (slice G), epic
  [#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94), slice F
  ([ADR-0077](/adr/0077-kind-taxonomy-and-step-authority/)) and #106
  ([ADR-0076](/adr/0076-the-activation-vocabulary-reads-and-projections/)).
- The scenarios `profile-selection.feature`, `step-profile-selection.feature`, `profiles.feature` and
  `effective-agent-profile.feature`, and the registration and error scenarios they extend.
