# Slice G grounding note: agent profile resolution, selection by role, the human performer

Mission `slice-g-profile-resolution-01M7DFBF`, 2026-11-14, against `main` at `b1d73b1`. Tracker #538: #102 (R8),
#42 (selection by role), #512 (human performer), #380 (refuse an `enhances` merge that drops fields).

Squads: [similar surfaces](research/squad-similar-surfaces.md) and [tracked issues](research/squad-tracked-issues.md).

## 1. Why now

The 2026-11-14 usability run (`docs/plans/engineering-notes/service-usability-run-2026-11-14.md` on
`ops/usability-run-2026-11-14`, finding 2) walked the review squad: the `review` step carries `role: reviewer`, but
**no operation turns a role into a profile**, so the client had to choose `reviewer-renata` itself before it could
reach `procedure:adversarial-squad-deployment`. Every step staffed by role, the squad included, needs G.

## 2. What is decided and what is not

Decided, and only built in part:

| Rule | Source | On `main` |
| --- | --- | --- |
| Steps name roles, never profiles | ADR-0032 d1 | `MissionStep.role` (`MissionStep.yaml:38`) |
| `role` plus `alt-roles`; `roles` retired | ADR-0069 d1, d2 | Schema, entity, DTO |
| Role fit is the outer key; `generic` never fits | ADR-0069 d4, ADR-0068 d4 | `RoleFit`, `AgentProfile.roleFit` |
| Specialization within a tier: lineage, then `routing-priority` | ADR-0032 d3.2, ADR-0069 d4.3 | Not built: no ordered chain or depth is exposed |
| Ties return every equal candidate | ADR-0032 d8 (ruled 2026-11-12) | Not built |
| A pin filters first and settles a tie it is part of | ADR-0032 d4, d9; ADR-0030 d5 | Not built; `policy.schema.json` reserves pins and refuses the key |
| Fallback in force: the most derived declaring pack's `fallback_profile` | ADR-0068 d5 | Declaration and three registration refusals built; "in force" not built |
| Answer marked `primary`, `alt` or `fallback` | ADR-0068 d6, ADR-0069 d5 | Not built |
| `fallback_profile_conflict` (422), `no_profile_matched` (404) | ADR-0068 d7 | Reserved (`raised: false`) |
| `operator` is a role; a profile can be performed by a human | ADR-0032 d5 | Only `sentinel: boolean` exists |
| An enhancement holds only additions; step merges are add-only | ADR-0053 d22 | Step merge built; no profile merge |

Not decided (operator rulings, section 6): where the selection read sits and what it is keyed by; what "fits the
context" means; the pin's shape; the performer's shape and how `operator` selects; what "drops a field" means for
#380; what R8's resolution answers.

## 3. Overlap with slice F (PR #567) and #106

Slice F has written mission documents only. What it changes that G touches:

- **Step shape.** F folds substeps into `MissionStep` and requires a `mission_type -requires-> action` edge for every
  step (`mission_step_unlinked`). It does **not** add or change `role`, which is already on `main`.
- **Relations.** F deletes `kernel.domain.Relation`, types `Relationship` construction by endpoint kind, and makes
  `specializes_from` and `enhances` agent_profile → agent_profile only (ADR-0064 decision 13). G walks both relations,
  so G's walks must be rebased onto F's typed API when F lands. That is a rebase cost, not a dependency on the step
  schema.
- **Kind taxonomy.** `GET /offering/kinds` adds no profile fact.

So the split the operator asked for is:

| Piece | Depends on F's step-schema change? |
| --- | --- |
| Selection given a role (domain order, fallback in force, ties, pins, `operator`) | No |
| Pins in `policy.yaml` | No |
| The performer marker replacing `sentinel` | No |
| Profile enhancement merge and the drop refusal | No (rebase onto F's typed relations) |
| R8: the effective profile resolved, and its lineage chain | No (same rebase) |
| Selection keyed by a step (`.../actions/{action}/profile`), and the step and action-context reads linking to it | **Yes**: it resolves the step through `requireAction` and `stepPlace`, which F reshapes |

#106 (ADR-0076, on `kitty/mission-glossary-contexts-and-terms-01M7B1PP`, no PR yet) adds `NodeKind` constants and edits
`policy.schema.json`, `ProfileDtos.java`, `EffectiveProfileDtos.java` and `PackPolicy.java`. G also edits
`policy.schema.json` (pins) and the profile DTOs (performer). The edits are disjoint in content; whichever lands second
rebases. No glossary term covers profile, role, selection or performer.

ADR numbers: `main` ends at 0075, 0076 is #106's, and F has none yet, so it should take 0077. **G plans 0078**, to be
checked against `main` and the open PRs when the ADR is written.

## 4. Similar surfaces (summary; detail in the squad)

Reuse `roleFit` and `RoleFit`, `RoleBearer`, the `GovernanceWalk` lineage step (extended once with an ordered chain),
`ClosureView.holds`, `winnerPackOf`, `isLocked` and `FallbackProfileRule`'s holder lookup, the effective-read order and
`EffectiveTag`, and the reserved codes already mapped in `RefusalHandler`. The example packs already give a primary
fit, an alt fit (`project-reviewer`), a lineage (`java-jenny` and `python-pedro` specialize `implementer-ivan`) and a
fallback (`generic-agent`). They lack a pin, a human-performed profile, an `operator` step, a profile `enhances` edge
and a sibling fallback conflict.

## 5. Tracked issues (summary; dispositions in the squad)

- IN: #538, #102, #42, #512, #380.
- DEPENDS, for the step-keyed read only: #537 and PR #567.
- FOLLOWS: #572 (journey 12 names the review profile until G), #108, #61, #205, #94 and #69 (tick at closeout).
- Coordinate: #556 (ADR-0075 slice 7 moves `FallbackProfileRule` behind ports; G reuses that rule).
- No close candidates. open-packs#139 (retire `human-in-charge`) follows #512 and is drafted here, not filed.
- Scope is not widened: #61 and #205 follow G, and are not folded in.

## 6. Open rulings, with a recommendation each

Revised after the post-plan squad ([squad-post-plan.md](research/squad-post-plan.md)). The first wording sent to the
operator is superseded.

- **D1. The selection read.**
  - (a) Keyed by role under an activation: `GET /activations/{activationId}/roles/{role}/profile-selection`.
  - (b) Keyed by step: `.../mission-types/{missionType}/actions/{action}/profile-selection`.
  - (c) Both.

  *Recommend (c)*: build (a) now, and (b) after F, as the same answer for the step's role.
- **D2. Order, context fit and depth.**
  - **Order.** First filter: the pin, then the performer rule, then context fit. Then take the best role-fit tier
    left, then the greatest depth, then the highest `routing-priority` (50 when absent). Then the tie rule, then the
    fallback.
  - **Context.** Optional `language` and `framework`. Only a *specialization* (a profile with an in-force
    `specializes_from` ancestor) that declares `languages` or `frameworks` is filtered. It fits when every asked
    dimension it declares contains the asked value, and at least one does. A root, or a specialization declaring
    neither, fits any context.
  - **Depth** is the longest in-force `specializes_from` chain. Several parents are allowed. A cycle is refused at
    registration with `specialization_cycle` (new, 422).
  - Results on the example packs:

    | Ask | Answer |
    | --- | --- |
    | `implementer` | `implementer-ivan` |
    | `implementer`, `language=java` | `java-jenny` |
    | `implementer`, `language=kotlin` | `implementer-ivan` |
    | `reviewer` in org-base | a marked tie |
  - Under this order a pinned profile is never tied: its specializations are deeper. ADR-0032 d9's tie-break is
    therefore unreachable, and ADR-0078 says so.

  *Recommend as stated.* Alternatives:
  - (a) no context in G;
  - (c) all six descriptor lists.
- **D3. The role pin.**
  - **Shape.** `policy.yaml` gains `role_pins: {<role>: <profile-id>}`.
  - **Registration refusals** (422), judged over the closure:
    - `role_pin_unknown`: the id resolves to no profile in the closure;
    - `role_pin_not_fitting`: the profile neither has the role nor lists it in `alt-roles`, with no human exemption;
    - `role_pin_conflict`: two packs neither derived from the other declare different pins.
  - **At selection.** `role_pin_conflict` is also raised (422), since unrelated packs can meet in one activation.
    The pinned profile passes the context filter.
  - **Sub-question (ii): which pin wins along a lineage.**
    - (ii-a) The most derived pack's pin wins, so a project may re-pin an org's role.
    - (ii-b) A pin is tighten-only (ADR-0012), so a derived pack's different pin is `role_pin_conflict`.

  *Recommend (ii-b)*: an org that pins `reviewer` to a human means it.
- **D4. The performer.**
  - **Shape.** `sentinel` is replaced by `performer: agent | human`. It is optional in the file, and on the wire it
    is omitted when the file is silent, since the read invents no default. Selection reads absent as `agent`.
  - **`operator` is reserved for human performers.**
    - A profile with `role: operator` or `operator` in `alt-roles` that is not `performer: human` is refused with
      `operator_role_not_human` (new, 422).
    - Selection for `operator` never uses the fallback; with no candidate it answers `404 no_profile_matched`.
  - **Human profiles elsewhere.** A human-performed profile is a candidate for any other role only through a pin,
    and the pin must fit (D3).
  - **Fallback.** A fallback profile that is `performer: human` is refused with `fallback_profile_not_agent`
    (new, 422).

  This amends ADR-0032 d5 ("a role like any other") and ADR-0069 d4.5 (no fallback for `operator`). *Recommend as
  stated*, over a 200 "served by a human" answer with no profile.
- **D5. #380: the profile enhancement merge, and what "drops a field" means.**
  Profiles keep their field-merge meaning (`profiles.feature:5-6`); ADR-0053 d22's add-only rule is for steps. An
  enhancer is a complete profile. Its identity fields are its own and never merged: `profile-id`, `name`,
  `purpose`, `role`, `alt-roles`, `performer`, `routing-priority`.
  - (a) **Field replacement with loss refused (recommended).** For each other top-level field the enhancer
    declares, its value replaces the base value. The replacement is refused when it loses base content: a list item
    missing, an object sub-field missing, or a scalar changed. The approved scenario stands as written: an enhancer
    whose `specialization` leaves out the base's is refused, naming the pack and `specialization`.
  - (b) **Add-only recursive merge.** Lists are unioned and objects merged; an omission is never a drop, and only
    a changed scalar is refused. This needs PR-CHARTER-REFUSAL-001 rewritten (decision 01M6NE62 amended).

  Either way:
  - the code is `profile_enhancement_drops_field`, naming the enhancing pack first and the field;
  - it answers 422 at registration, and 500 at read for content stored before the rule;
  - enhancers are never selection candidates and never in `lineage`.
- **D6. R8 (#102) is a contract operation.**
  - `getEffectiveAgentProfile` answers the effective profile: the leaf with its in-force enhancers merged (D5), and
    `lineage`, the ordered `specializes_from` ancestors with pack and depth.
  - Nothing is inherited along `specializes_from`.
  - An enhancer's own effective read answers its body plus an `enhances` link.
  - The pack read keeps the stored body.

  *Recommend as stated.*
- **D7. `excluding` (new, from the squad).** Under D5, what does `excluding` do?
  - (a) On an enhancer, it is a declared drop and is refused with `profile_enhancement_drops_field`. On a
    non-enhancer it stays as authored.
  - (b) Remove `excluding` from the profile schema outright, since nothing resolves it.

  *Recommend (a)* for G, with (b) left for a later model pass. Removing a field nobody has ruled on widens this
  slice.

ADR-0078 records D1 to D7. It amends ADR-0030 (the pin's shape), ADR-0032 d5 and d9 (reachability), ADR-0069 d4.5,
and ADR-0053 d22 (profiles keep field-merge). Two CHANGELOG fragments go in `contracts/charter/changes/`: one
Breaking (`sentinel` to `performer`, the effective profile's meaning) and one Added (the selection read, the new
codes, `role_pins` and `lineage`).

## 6a. Rulings (operator interview, 2026-11-14)

| Ruling | Decision |
| --- | --- |
| D1 | Both reads: role-keyed now, step-keyed after F |
| D2 | As recommended: filter first (pin, performer, role fit, context), then tier, longest depth, `routing-priority`. Only specializations that declare `languages` or `frameworks` are context-filtered. A `specializes_from` cycle is refused (`specialization_cycle`). ADR-0032 d9's tie-break is unreachable, and ADR-0078 records that |
| D3 | Tighten-only pins in `policy.yaml` `role_pins`: a differing pin anywhere in the closure is `role_pin_conflict`. Also refused: `role_pin_unknown` and `role_pin_not_fitting` (no human exemption) |
| D4 | `performer: agent \| human`, optional and omitted on the wire when absent; selection reads absent as `agent`. `operator` is human-only (`operator_role_not_human`) and never falls back. A fallback profile must be agent-performed (`fallback_profile_not_agent`) |
| D5 | Field replacement with loss refused (`profile_enhancement_drops_field`: 422 at registration, 500 at read). Identity fields are never merged. PR-CHARTER-REFUSAL-001 stands as written |
| D6 | **Differs from the recommendation: merged plus inheritance.** The effective profile inherits along `specializes_from`, root to leaf, with **per-field replacement**: a top-level field the descendant declares replaces the ancestor's, and a field it leaves absent is inherited. Identity fields are never inherited. `lineage` is still answered. **Selection judges each candidate by its own declared fields**, not inherited ones, so D2's descriptor-free refinement still fits any context |
| D7 | `excluding` on an enhancer is refused as a declared drop. On other profiles it stays as authored |

Derived, not asked (flagged for review):

- **Order of composition.** Each profile on the chain is first merged with its own in-force enhancers (D5), then the
  chain is folded root to leaf (D6). An ancestor's enhancement therefore reaches its descendants unless they
  redeclare that field.
- **Required fields** (`name`, `purpose`, `specialization`) are always the descendant's own, since the closed schema
  requires them on every profile.
- **D6 versus the operator steer of 2026-10-19** ("design on its own merits, not as a copy of core's call"): this
  ruling supersedes it for profile inheritance.

## 7. Coordination log

- 2026-11-14, from slice F, via the orchestrator:
  - F takes ADR-0077 (`0077-kind-taxonomy-and-step-authority.md`); G keeps 0078.
  - `MissionStep.role` is untouched.
  - F's step changes:
    - `substeps` is added, optional in the pack schema and required on the `MissionStep` wire schema;
    - the `step-contract` link, `stepContractId` and the `step` link relation are removed from the step read.
  - `Relationship` constructors become package-private. Relationships are built only through the checked
    `Relationship.of(...)` factories, enforced by ArchUnit `RELATIONSHIP_IS_BOUND`. G only *reads* relationships
    (lineage, enhancers), so its rebase cost is limited to fixtures that build relationships in tests.
  - F status: WP01 to WP09 approved; WP10, WP11 and landing remain.
- 2026-11-14, second note from slice F, via the orchestrator:
  - `Relationship.of` throws IAE on a disallowed endpoint pair, so the rebase uses the typed factories.
  - `MissionStep` gains a ninth component, `substeps`.
  - The `step` link relation is gone.
  - **A substep never delegates to an `agent_profile`** (operator ruling, keeping ADR-0032 d1). This settles WP07's
    open question: a step's profile comes only from selection by its role.
  - F is at its pre-merge folds, then accept and consolidate. F may land before #106.
