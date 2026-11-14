# Mission Specification: Slice G: agent profile resolution, selection by role, and the human performer

**Mission Branch**: `kitty/mission-slice-g-profile-resolution-01M7DFBF` (target `slice-g-538-profile-resolution`)
**Created**: 2026-11-14
**Status**: Draft, revised after the post-plan squad. Rulings D1 to D7 are ruled (operator interview, 2026-11-14;
`research.md` §6a). Scenarios name the ruling they follow.
**Input**: Slice G (#538): #102 (R8), #42, #512, #380. Grounding: [research.md](research.md).

## Intent Summary

A mission step names the role it needs, never a profile (ADR-0032 d1). Today no operation turns that role into a
profile, so a client has to pick one itself. The 2026-11-14 usability run hit this while staffing the review squad.
This mission makes the service answer "which profile fills role R here", in ADR-0069's order:

- a pin narrows the candidates first;
- role fit comes next;
- then specialization, then `routing-priority`;
- a tie returns every equal candidate;
- with no candidate, the fallback in force answers, and with no fallback either, `no_profile_matched`.

It also does three more things:

- It lets a profile be performed by a human, replacing `sentinel` outright.
- It answers the *effective* profile: the leaf with its in-force enhancers merged, and its lineage.
- It refuses an enhancement that would drop part of the profile it enhances.

Operator gate (2026-11-14): this mission specifies and plans now. It implements only the F-independent work packages
now. The step-keyed read waits for slice F (#537, PR #567).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Select a profile for a role (Priority: P1)

A client running a mission under an activation asks which profile fills a role, for example `reviewer`. It optionally
names a context (a language, a framework). The answer is one profile, or every tied candidate, or the fallback, each
marked with how it fits.

**Why this priority**: it is the missing hop of the usability run, and core cannot switch its selection until it is a
contract operation (#42, #102).

**Independent Test**: against charter's example packs, under an activation, ask for each rule's role and check the
answer and its mark.

**Acceptance Scenarios**:

1. **Given** `implementer-ivan` (primary `implementer`) and `project-reviewer` (primary `reviewer`, alt
   `implementer`) in force, **When** I select for `implementer` with no context, **Then** `implementer-ivan` is
   answered marked `primary`: an `alt` fit never outranks a primary fit.
2. **Given** `java-jenny` and `python-pedro` specialize `implementer-ivan` and declare their languages, **When** I
   select for `implementer` with `language=java`, **Then** `java-jenny` is answered: the deeper specialization wins
   within the tier.
3. **Given** the org-base context, with `reviewer-renata` and `reviewer-rita` both primary `reviewer`, both roots,
   both at `routing-priority` 50, **When** I select for `reviewer`, **Then** both are answered in a stable order
   (closure position, then id), and the answer is marked a tie.
7. **Given** every primary candidate is filtered out by the context, **When** an alt candidate fits, **Then** it is
   answered marked `alt`, not the fallback.
4. **Given** a step role no profile fills (`facilitator`), **When** I select, **Then** the fallback in force,
   `generic-agent`, is answered marked `fallback`.
5. **Given** a context whose closure has no fallback and no candidate, **When** I select, **Then** `404
   no_profile_matched`.
6. **Given** two packs of one closure, neither derived from the other, declaring different fallbacks, **When** I
   select and no candidate fits, **Then** `422 fallback_profile_conflict`, naming both packs.

---

### User Story 2 - A derived pack pins the profile for a role (Priority: P1)

An org pins `reviewer` to its profile in its `policy.yaml`. Every selection for `reviewer` in a context that holds
the org pack gets that profile, or a profile that specializes it.

**Why this priority**: it is how an org staffs a role without editing a step (ADR-0032 d4, d9). It is also the only
way to settle a tie except `routing-priority`.

**Independent Test**: an example pack with a pin; select with and without it.

**Acceptance Scenarios**:

1. **Given** the project pack pins `reviewer` to `reviewer-renata`, **When** I select for `reviewer` in the project
   context, **Then** `project-reviewer` is no candidate and `reviewer-renata` is answered.
2. **Given** a pin to a profile that has a specialization fitting the context, **When** I select, **Then** the
   specialization is answered: a pin narrows, it does not freeze.
3. **Given** a pin naming an unknown profile, a profile that does not fit the role, or two packs pinning
   differently, **When** the pack is registered, **Then** it is refused with `role_pin_unknown`,
   `role_pin_not_fitting` or `role_pin_conflict`, and nothing is stored. *(Ruled: D3.)*
4. **Given** two unrelated packs that pin differently meet in one activation, **When** I select, **Then** `422
   role_pin_conflict`.

ADR-0032 d9's tie-break (the pinned profile wins a tie it is part of) cannot arise under this order: every candidate
left by a pin other than the pinned profile is deeper than it. ADR-0078 records this, and no scenario claims it.

---

### User Story 3 - A human performs a profile (Priority: P2)

A pack marks a profile `performer: human`. A step that asks for `operator` is served only by a human-performed
profile, and never by the fallback. A pin can staff any other role with a human.

**Why this priority**: it replaces `sentinel` (#512) and unblocks open-packs#139. No shipped step asks for `operator`
yet, so it is P2, not P1.

**Independent Test**: an example human-performed profile; select for `operator`, and for a role pinned to it.

**Acceptance Scenarios**:

1. **Given** org-base's `human-operator` (`performer: human`, role `operator`), **When** I select for `operator`,
   **Then** it is answered marked `primary`, and its read shows `performer: human`. A profile whose file is silent
   answers no `performer`.
2. **Given** no human-performed profile, **When** I select for `operator`, **Then** `404 no_profile_matched`, never
   the fallback.
3. **Given** a human-performed profile with role `reviewer` and no pin, **When** I select for `reviewer`, **Then** it
   is no candidate. **Given** `reviewer` pinned to it, **Then** it is answered.
5. **Given** a profile with role `operator` that is not `performer: human`, or a fallback profile that is
   `performer: human`, **When** its pack is registered, **Then** it is refused with `operator_role_not_human` or
   `fallback_profile_not_agent`.
4. **Given** a profile file that still carries `sentinel`, **When** it is read or registered, **Then** it is
   refused as unreadable or invalid, like any undeclared key.

---

### User Story 4 - Read the effective profile and its lineage (Priority: P2)

A client reads a profile under an activation and gets the effective profile: the stored body with its in-force
enhancers merged, and its `specializes_from` ancestors in order with their depth.

**Why this priority**: R8 (#102) is one of the last undecided reads of epic #94.

**Independent Test**: an example pack with a profile enhancement; read the effective profile.

**Acceptance Scenarios**:

1. **Given** an org-base enhancer of `reviewer-renata` that keeps every base field it declares and adds one directive
   reference, **When** I read the effective `reviewer-renata`, **Then** the base content and the addition are
   answered. The enhancer's own `name` and `purpose` are not merged.
2. **Given** `java-jenny` specializes `implementer-ivan`, **When** I read the effective `java-jenny`, **Then**
   `lineage` is `[{implementer-ivan, depth 1}]`. Every top-level field `java-jenny` declares is its own: its
   `languages` stay `[java]`. Every field it leaves absent is inherited from `implementer-ivan`. Identity fields are
   never inherited (D6, ruled).
3. **Given** the enhancing pack's enhancer is out of force, **When** I read, **Then** its additions are absent.

---

### User Story 5 - An enhancement that drops a field is refused (Priority: P2)

**Why this priority**: promise `PR-CHARTER-REFUSAL-001` (#380) is the last `@WIP` profile scenario.

**Acceptance Scenarios** *(ruled: D5, D7)*. Profiles keep their field-merge meaning: a top-level field the
enhancer declares replaces the base's, and a replacement that loses base content is a drop.

1. **Given** an enhancer whose `specialization` leaves out the base's (the approved scenario of
   PR-CHARTER-REFUSAL-001), **When** its pack is registered, **Then** `422 profile_enhancement_drops_field` names the
   enhancing pack and `specialization`.
2. **Given** an enhancer whose list leaves out a base item, that changes a base scalar, or that declares
   `excluding`, **Then** the same refusal.
3. **Given** such content stored before the rule, **When** I read the effective profile, **Then** `500
   profile_enhancement_drops_field`, with the enhancing pack first in `packs`.
4. **Given** an enhancer that keeps every base value it restates and only adds, **Then** it is accepted (positive
   control).
5. **Given** an in-force enhancer whose role is `reviewer`, **When** I select for `reviewer`, **Then** it is no
   candidate.

---

### User Story 6 - Select for a step (Priority: P3, F-dependent)

A client asks for the profile of a step, `.../mission-types/{type}/actions/{action}/profile-selection`. The answer is
what the role read answers for the step's role. The effective step and action-context reads link to it. A step with no
role answers the fallback, since any role may act.

**Why this priority**: it removes the last client hop. It waits for slice F, which reshapes how a step is resolved.

### Edge Cases

- A role token that is not kebab-case, or is `generic`: `400` invalid request. `generic` is never asked for
  (ADR-0068 d4).
- An unknown activation: `404`, as for the other effective reads.
- A candidate whose ancestor is out of force: the depth counts only in-force ancestors, as the governance walk does.
  The depth is the longest chain. A `specializes_from` cycle is refused at registration (`specialization_cycle`).
- `routing-priority` absent: 50, the schema default.
- A pin whose profile is out of force under the activation: the pin narrows to its in-force specializations, or to
  nothing. With nothing left, the fallback answers for a non-`operator` role. *(Ruled: D3.)*
- A conditional ETag on the selection read: `304` when unchanged, as for the other effective reads.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Issue | F-dependent |
| --- | --- | --- | --- |
| FR-001 | The service selects profiles for a role under an activation in ADR-0069 d4's order. The filters (pin, performer, role fit, context) run first, then the best tier, the longest depth, `routing-priority`, the tie, the fallback, and `no_profile_matched`. Enhancers are never candidates | #42 | No |
| FR-002 | The answer carries one or more candidates, each with its id, pack and mark (`primary`, `alt`, `fallback`), and whether it is a tie | #42 | No |
| FR-003 | Context fit (D2: `language`, `framework`). Only a specialization that declares `languages` or `frameworks` is filtered: every asked dimension it declares must contain the asked value, and at least one must. Roots, descriptor-free specializations and the pinned profile fit any context | #42 | No |
| FR-004 | The fallback in force is the most derived declaring pack's `fallback_profile`. Sibling declarations that differ raise `422 fallback_profile_conflict` | #42 | No |
| FR-005 | `no_profile_matched` (404) and `fallback_profile_conflict` (422) become raised codes | #42 | No |
| FR-006 | `policy.yaml` accepts `role_pins`, validated at registration and judged for conflict at selection (D3). It round-trips. `specialization_cycle` is refused at registration | #42 | No |
| FR-007 | `sentinel` is removed everywhere, and `performer: agent \| human` replaces it. The read omits `performer` when the file is silent | #512 | No |
| FR-008 | `operator` is reserved for human-performed profiles (`operator_role_not_human`), and never uses the fallback. A human-performed profile is a candidate only for `operator` or through a fitting pin. A fallback profile must be agent-performed (`fallback_profile_not_agent`) | #512 | No |
| FR-009 | `getEffectiveAgentProfile` answers the effective profile: each profile on the chain is merged with its in-force enhancers (D5), then the chain is folded root to leaf by per-field replacement (D6), and `lineage` is answered. Selection reads declared fields, never inherited ones | #102, #380 | No |
| FR-010 | An enhancement whose replacement loses base content (D5a), or that declares `excluding` (D7a), is refused: 422 at registration, 500 at read. Identity fields are not merged | #380 | No |
| FR-011 | `docs/api/service-contract.md` records R8's decision, the operations, scenarios and corpus cases | #102 | No |
| FR-012 | The step-keyed selection read, and links from the effective step and action-context reads | #42 | **Yes** |

### Non-Functional Requirements

- NFR-001: every rule has an example pack in charter's own example packs (ADR-0070) and a `@contract` scenario.
- NFR-002: the whole-pack round trip stays green with `role_pins`, `performer` and the profile enhancement fixture.
  No field is exempted.
- NFR-003: `./gradlew verifyAll` on Java 25, with the style, ArchUnit and docs gates.

### Constraints

- C-001: the canonical shape only. `sentinel` gets no reader, and examples and fixtures use `performer`.
- C-002: one lineage walk. Extend the `GovernanceWalk` lineage step; no second `specializes_from` traversal.
- C-003: the selection domain sits in the profiles slice as a pure service. `service/activations` supplies the
  in-force candidates, the pin and the fallback (ADR-0006).
- C-004: the F-independent work packages must not touch F's step or relation code. When F lands, G rebases onto F's
  typed `Relationship` API.
- C-005: open-packs content (retiring `human-in-charge`, `performer`, the pins) is drafted with
  `open-packs#TBD-<slug>` placeholders, never filed from here.

## Success Criteria *(mandatory)*

- SC-001: the usability run's review hop needs no client-side walk: selecting for `reviewer` under the run's
  activation answers a profile, or a marked tie the client picks from (ADR-0032 d8).
- SC-002: #42's acceptance holds: one example pack per rule, the tie and pin examples, and the two refusals raised.
- SC-003: no `sentinel` remains outside history.
- SC-004: `profiles.feature` has no `@WIP`, and `PROMISES.md` closes the #380 gap.
- SC-005: `verifyAll` is green.

## Key Entities

- **ProfileSelection** (new): a role, an optional context, and the answer (candidates, marks, tie).
- **SelectionContext** (new): the asked `language` and `framework`.
- **RolePin** (new): role → profile id, held by a pack's policy.
- **Performer** (new): `agent` or `human`, on `AgentProfile`, replacing `sentinel`.
- **EffectiveAgentProfile**: gains the merged enhancers and `lineage`.
- **Reused**: `RoleFit`, `AgentProfile.roleFit`, `SpecializationContext`, `PackDescriptor.fallbackProfile`.

## Domain Language

- *Selection*, not *resolution*, for role → profile. *Resolution* is the effective profile of one id.
- *Fit* is `primary`, `alt` or `fallback`. A *tie* is equal candidates after the order.
- *Pin* is a role pin, never a version pin.
- *Performer* is who carries out a profile: an agent or a human. *Operator*, as a role, is the human-only role. The
  operator as the ruler of this project is not the role.

## Assumptions

- The rulings follow the recommendations; a different ruling re-plans only the WPs it touches.
- Slice F does not change `MissionStep.role`.
- Pins and the fallback share one "most derived declaring pack" rule.
