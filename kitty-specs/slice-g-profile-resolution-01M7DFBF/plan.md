# Implementation Plan: Slice G: agent profile resolution, selection by role, and the human performer

**Branch**: `kitty/mission-slice-g-profile-resolution-01M7DFBF` | **Date**: 2026-11-14 | **Spec**: [spec.md](spec.md) |
**Research**: [research.md](research.md)

## Summary

Slice G makes the service answer which profile fills a role, which the usability run found missing. It is built
mostly from parts that already exist: `RoleFit` and `roleFit`, the lineage walk, `fallback_profile` with its holder
lookup, and the effective-read order. G adds what is missing on top of them:

- a selection domain service in the profiles slice;
- role pins in `policy.yaml`;
- `performer` in place of `sentinel`;
- an add-only profile enhancement merge with its drop refusal;
- the effective profile read with `lineage`.

Only the step-keyed read waits for slice F.

**Operator gate (2026-11-14, relayed):**

- Implement and review the F-independent work packages now. Leave the F-dependent ones planned.
- Do not accept or consolidate until the F-dependent WP is done after F lands, unless the slice splits cleanly into
  an F-independent PR. It does (decision P3). The orchestrator has been asked before any such PR is opened.
- Rulings D1 to D6 are open. A WP that a ruling touches is not implemented until that ruling is in.

## Technical Context

- Java 25, Gradle, Spring Boot service, onion slices held by ArchUnit (ADR-0006).
- The OpenAPI contract is in `contracts/charter/` (generated DTOs and scenarios) and the pack schemas in
  `contracts/packs/schemas/`.
- Gates: `./gradlew verifyAll`, which runs the contract checks, unit tests, ArchUnit, contract scenarios, the
  whole-pack round trip, Spotless, Checkstyle, Error Prone, NullAway and doclint, plus the docs checks.

## Charter Check

| Rule | How this plan meets it |
| --- | --- |
| The charter's model is canonical; no legacy shape | `sentinel` is removed outright. Fixtures and examples use `performer` only. A file carrying `sentinel` appears only in an invalid-pack test |
| Tests and examples carry only the canonical shape | Every rule has its example pack in charter's own packs (ADR-0070). No round-trip exemption |
| The contract follows ADR-0022 | One `### Breaking` fragment (`sentinel` → `performer`, the effective profile's meaning). New operations are additive |
| Adversarial squad at the plan point and before merge | Post-plan squad (domain, contract, migration lenses) before tasks are finalized; a pre-merge squad on the PR |
| Issues stay small | No issue filed for minors. Open-packs drafts carry `open-packs#TBD-<slug>` placeholders. Closeout closes #42, #512, #380 and #102, and ticks #538, #94 and #69 |
| ADR numbered when written | 0078 planned and rechecked against `main` and open PRs when written |

## Design

### P1. Selection domain (profiles slice)

`ProfileSelection.select(role, SelectionContext, CandidateSource) → SelectionAnswer`, a pure domain service. It takes
the candidates in force under the activation, each with its in-force lineage depth and pack, plus the pin and
fallback in force. Order (ADR-0069 d4):

1. **Reserved roles.** `generic` gives `400`. For `operator`, the candidates are human-performed profiles only, and
   the fallback is never used.
2. **Pin.** When a pin is in force for the role, the candidates narrow to the pinned profile and its in-force
   specializations. The pinned profile itself passes the context filter.
3. **Role fit** (`roleFit`). Keep the best tier present. A human-performed profile is dropped unless the role is
   `operator` or the pin names it.
4. **Context fit** (D2), for `language` and `framework`:
   - a profile that declares an asked dimension must contain the asked value;
   - a specialization that declares descriptors needs one asked match;
   - a root, or a descriptor-free specialization, fits any context.
5. **Specialization**: the greatest lineage depth, then the highest `routing-priority` (absent counts as 50).
6. **Tie.** If the pinned profile is among the tied, it alone is answered. Otherwise all are answered, sorted by
   closure order, then id, and marked `tie: true`.
7. **None.** The fallback in force is answered, marked `fallback`. For `operator`, or with no fallback in force,
   `NoProfileMatched`.

`SelectionAnswer` holds the candidates (`id`, `pack`, `fit`), `tie` and the asked role. The domain stays free of
Spring and wire types, and has unit tests per rule.

### P2. Fallback and pin in force (packs slice)

`ClosureRoleFacts` (application layer) answers the fallback and the pins in force for a closure, with one rule: **the
most derived declaring pack wins**. Two declaring packs where neither is derived from the other, with different
values, raise a conflict:

- for the fallback: `fallback_profile_conflict`, at selection (ADR-0068 d7);
- for pins: `role_pin_conflict`, at registration.

It reuses `ClosureView.holds`, `winnerPackOf` and `FallbackProfileRule`'s holder lookup. Registration adds
`RolePinRule` beside `FallbackProfileRule` in `RegistrationImpact`, raising `role_pin_unknown`,
`role_pin_not_fitting` and `role_pin_conflict`. #556 (ADR-0075 slice 7) moves `FallbackProfileRule` behind ports;
whichever of #556 and G lands second moves `RolePinRule` with it.

### P3. Delivery split

The slice splits cleanly in two:

- **PR 1, F-independent:** WP01 to WP06.
- **PR 2, after F lands:** WP07, rebased onto F.

No WP of PR 1 touches F's step or relation code. WP04 and WP05 walk `enhances` and `specializes_from` through
today's `Relationship`. When F lands first, they rebase onto F's typed factories; when G lands first, F's rebase picks
up two more call sites. Both outcomes are named to F through the orchestrator. Opening PR 1 waits for the operator's
answer. Until then, the brief's draft PR carries the planning artifacts.

### P4. Contract

New operation `selectAgentProfile`, under tag `EffectiveAgentProfiles`:

```
GET /activations/{activationId}/roles/{role}/profile-selection?language=&framework=
200 ProfileSelectionRead
    { role, context: {language?, framework?},
      candidates: [{ id, pack, fit: primary|alt|fallback, links }],
      tie: boolean, links }
304 (If-None-Match)
400 invalid role token, or `generic`
404 activation_not_found | no_profile_matched
422 fallback_profile_conflict
```

`no_profile_matched` and `fallback_profile_conflict` flip to `raised: true`, with an `operations:` override for
`selectAgentProfile`.

- **`AgentProfile`:** `sentinel` is removed and `performer` (`agent | human`, required on the wire) is added.
- **`EffectiveAgentProfileRead`:** the body is the merged effective profile, and the read gains
  `lineage: [{ id, pack, depth }]`, ordered nearest first.
- **Pack schemas:**
  - `agent-profile.schema.json`: `sentinel` out, `performer` in;
  - `policy.schema.json`: `role_pins` (a map from role token to profile id), with the reserved-pins sentence
    amended;
  - registration violations: `role_pin_*` and `profile_enhancement_drops_field`.

PR 2 adds `selectAgentProfileForStep` at `.../mission-types/{type}/actions/{action}/profile-selection`, and a
`profile-selection` link on the effective step and action-context reads.

### P5. Effective profile (R8) and the merge (#380)

`ProfileEnhancementMerge`, in the profiles domain, merges add-only:

- **Lists** (references, keywords, collaboration lists, and so on) are appended in order, keeping the first copy of
  each item.
- **An absent scalar or object** is set.
- **Refused** with `ProfileEnhancementDropsField(pack, field)`:
  - a present scalar that the enhancer replaces with another value;
  - `excluding` that removes base content (D5).

The merge never touches `role`, `alt-roles`, `performer` or `profile-id`. An enhancer that sets one of these
differently is refused the same way.

The enhancers merged are the in-force `enhances` sources of the profile, applied in closure order, nearest pack last.
`lineage` comes from the `GovernanceWalk` lineage step, extended once to yield an ordered chain with its depth
(C-002). Registration runs the same merge in `RegistrationImpact` (422). The read (500) covers content stored before
the rule.

### P6. Records

- **ADR-0078:** D1 to D6. It amends ADR-0030 (the pin's shape) and records the `operator` rule beside ADR-0068 d6.
- `docs/api/service-contract.md`: the R8 row decided.
- `docs/architecture/logical-model.md`: selection is no longer a candidate.
- `docs/context/conceptual-model.md`: the role pin.
- Glossary: *selection*, *performer*.
- `tests/scenarios/charter/PROMISES.md`: the #380 gap closed.
- `changes/`: one Breaking fragment.
- Open-packs drafts in `research/open-packs-drafts.md`:
  - `open-packs#TBD-retire-sentinel`: `human-in-charge` moves to `performer: human`, `role: operator`, which also
    covers open-packs#139;
  - `open-packs#TBD-role-pins`, optional and informational.

## Work packages

| WP | Title | Issues | Rulings | F | Depends on |
| --- | --- | --- | --- | --- | --- |
| WP01 | `performer` replaces `sentinel`, end to end (schema, contract, domain, storage, DTOs, examples, fixtures, tests) | #512 | D4 | **independent** | — |
| WP02 | Role pins in `policy.yaml`: schema, reader and writer, round trip, `RolePinRule`, and the pin in force | #42 | D3 | **independent** | — |
| WP03 | Selection domain: `ProfileSelection`, `SelectionContext`, `SelectionAnswer`, the fallback in force and its conflict, the `operator` rule; unit tests per rule | #42, #512 | D2, D3, D4 | **independent** | WP01, WP02 |
| WP04 | Lineage chain plus the add-only enhancement merge and its drop refusal (domain, registration impact) | #380, #102 | D5, D6 | **independent** (rebase onto F's typed relations) | WP01 |
| WP05 | Contract and edge: `selectAgentProfile` and the effective profile with `lineage`; raised codes; example packs per rule; `@contract` scenarios; `profiles.feature` out of `@WIP` | #42, #102, #380 | D1, D6 | **independent** | WP03, WP04 |
| WP06 | Records: ADR-0078, service-contract, logical and conceptual model, glossary, PROMISES, Breaking fragment, open-packs drafts | #102 | all | **independent** | WP05 |
| WP07 | Step-keyed selection read, and `profile-selection` links on the effective step and action-context reads | #42 | D1 | **dependent: after F (#567) lands** | WP05, slice F |

Lanes: WP01 and WP02 run in parallel; WP03 and WP04 run in parallel after them.

## Example packs to add (charter's own, ADR-0070)

| Fixture | Pack | Rule |
| --- | --- | --- |
| `reviewer-renata` and `project-reviewer` (existing) | built-in, project | Tie |
| `implementer-ivan`, `java-jenny` with `language=java` (existing) | built-in | Specialization within a tier |
| `project-reviewer` for `implementer` (existing) | project | Alt beats nothing, loses to primary |
| `facilitator` step (existing) to `generic-agent` | built-in | Fallback |
| `role_pins: {reviewer: reviewer-renata}` | org-base `policy.yaml` | Pin settles the tie |
| `human-operator` (`performer: human`, role `operator`) | org-base | `operator` |
| A profile enhancement of `reviewer-renata` (adds only) | org-base | Effective merge, positive control |
| Invalid packs: a `sentinel` key; a pin that does not fit; an `excluding` drop; sibling fallbacks | test fixtures | Refusals |

The pin is added to org-base, so every existing org-base and project reading that selects for `reviewer` changes. That
is intended. The `no_profile_matched` case needs a closure with no fallback: a stand-alone test pack, since built-in
always declares one.

## Risks

- **Rulings differ from the recommendations.** Only the WPs named in the "Rulings" column re-plan.
- **#106 and F edit neighbouring files** (`policy.schema.json`, the profile DTOs, `RegistrationImpact`). Rebase
  order is agreed through the orchestrator.
- **The org-base pin changes existing scenario answers.** WP05 updates them in the same change, with no exemption.
- **ETag for selection.** It must cover pins, the fallback and every candidate's body. WP05 adds an
  `EffectiveTag.SelectionQuery` that hashes the closure's in-force profile set and the policies, with no new
  mechanism.

## Project Structure (touched)

```
contracts/charter/{openapi.yaml, paths/, schemas/, examples/, scenarios/}
contracts/packs/schemas/{agent-profile,policy}.schema.json
service/profiles/            selection domain, merge, lineage chain
service/profiles-edge/       controller, DTOs
service/packs/               policy reader/writer, RolePinRule, ClosureRoleFacts, merge at registration
service/kernel/              RefusalCode entries
service/packs/src/testFixtures/resources/example-packs/
docs/adr/0078-*.md, docs/api/service-contract.md, docs/architecture/logical-model.md, changes/
```
