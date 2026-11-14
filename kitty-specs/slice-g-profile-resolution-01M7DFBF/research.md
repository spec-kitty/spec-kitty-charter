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

- **D1. The selection read.**
  - (a) Keyed by role under an activation: `GET /activations/{id}/roles/{role}/profile-selection`.
  - (b) Keyed by step: `.../mission-types/{type}/actions/{action}/profile-selection`.
  - (c) Both.

  *Recommend (c)*. Build (a) now. (b) is F-dependent; it reads the step's role and answers as (a) would, and the step
  and action-context reads link to it.
- **D2. "Fits the context".**
  - (a) No context in G: rank by lineage depth, then `routing-priority`.
  - (b) Optional `language` and `framework` query parameters, with three rules:
    1. A profile that declares an asked dimension must contain the asked value.
    2. A specialization (one with an in-force `specializes_from` ancestor) that declares descriptors fits only when
       an asked dimension matches one it declares.
    3. A lineage root, or a specialization with no descriptors (a pure refinement), fits any context.
  - (c) As (b), with all six descriptor lists.

  *Recommend (b)*. With no context, `implementer` answers `implementer-ivan`; with `language=java`, `java-jenny`;
  with `language=go`, `implementer-ivan`. Under (a), `implementer` with no context would tie `java-jenny` and
  `python-pedro`. The example packs already tie `reviewer-renata` with `project-reviewer`, which is the tie fixture.
- **D3. The pin's shape.** `policy.yaml` gains `role_pins: {<role>: <profile-id>}`. The pin in force is the most
  derived declaring pack's, as for the fallback. Refusals, judged at registration over the closure:
  - `role_pin_unknown` (422): the id resolves to no profile in the closure.
  - `role_pin_not_fitting` (422): the profile neither has the role nor lists it in `alt-roles`, and is not a
    human-performed profile.
  - `role_pin_conflict` (422): two packs of one closure, neither derived from the other, declare different pins.

  The pinned profile is a candidate whatever the context; its specializations are filtered by the context.
  *Recommend as stated.* The ADR leaves "no pinned candidate fits" uncovered; this closes it.
- **D4. The performer.** `sentinel` is replaced by `performer: agent | human`: optional in the file, absent meaning
  `agent`, and always answered on the wire.
  - `operator` becomes a reserved role. Its candidates are human-performed profiles only, it never uses the fallback,
    and with no candidate it answers `no_profile_matched`.
  - A human-performed profile is a candidate for `operator`, or for any role pinned to it.

  *Recommend as stated*, over a separate 200 "served by a human" answer with no profile, which would widen ADR-0068
  d6's three marks.
- **D5. #380, "drops a field".**
  - (a) A drop is an explicit removal: an enhancer's `excluding` that names base content. This leaves d22 intact.
  - (b) A drop is an omission. This would contradict d22, since an enhancer holds only its additions.

  The profile merge is add-only, as for steps: lists are appended and duplicates kept once, and an absent field is
  set. Replacing a scalar the base sets is refused too, since it loses the base value.
  - Code: `profile_enhancement_drops_field`, naming the enhancing pack and the field.
  - Status: 422 at registration (impact check) and 500 at read, for content stored before the rule, as the
    placeholder states.

  *Recommend (a)*, with the scalar rule.
- **D6. R8 (#102): a contract operation.** `getEffectiveAgentProfile` answers the **effective** profile: the leaf
  body with its in-force enhancers merged (D5), plus `lineage`, the ordered `specializes_from` ancestors with their
  depth. Fields are not inherited along `specializes_from`: every profile is complete under the closed schema, and
  core's root-to-leaf merge is not copied (operator steer 2026-10-19). The pack read keeps answering the stored body.
  *Recommend as stated.* It is Breaking only in meaning; `sentinel`'s removal is the Breaking schema change.

One new ADR (0078) records D1 to D6 and amends ADR-0030 (pin shape) and ADR-0068 d6 (unchanged marks; `operator`'s
rule). One `### Breaking` CHANGELOG fragment covers `sentinel` → `performer` and the effective profile's new meaning.

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
