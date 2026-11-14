# Implementation Plan: Slice G: agent profile resolution, selection by role, and the human performer

**Branch**: `kitty/mission-slice-g-profile-resolution-01M7DFBF` | **Date**: 2026-11-14 | **Spec**: [spec.md](spec.md) |
**Research**: [research.md](research.md) | **Post-plan squad**: [research/squad-post-plan.md](research/squad-post-plan.md),
2 blockers and 9 majors, all folded

## Summary

Slice G makes the service answer which profile fills a role, which the usability run found missing. It is built
mostly from parts that already exist: `RoleFit` and `roleFit`, the lineage walk, `fallback_profile` with its holder
lookup, and the effective-read order. G adds what is missing on top of them:

- a selection domain service;
- role pins in `policy.yaml`;
- `performer` in place of `sentinel`;
- the profile enhancement field merge with its drop refusal;
- the effective profile read with `lineage`.

Only the step-keyed read waits for slice F.

**Operator gate (2026-11-14, relayed):**

- Implement and review the F-independent work packages now. Leave the F-dependent ones planned.
- Do not accept or consolidate until the F-dependent WP is done after F lands. The slice ships as one PR, held until
  F lands (operator, 2026-11-14; P3).
- Rulings D1 to D7 are open. A WP that a ruling touches is implemented only once that ruling is in.

## Technical Context

- Java 25, Gradle, Spring Boot service, onion slices held by ArchUnit (ADR-0006).
- The contract is in `contracts/charter/` and the pack schemas in `contracts/packs/schemas/`.
- Gates: `./gradlew verifyAll`, covering the contract checks, unit tests, ArchUnit, the contract scenarios, the
  whole-pack round trip, Spotless, Checkstyle, Error Prone, NullAway, doclint and the docs checks.

## Charter Check

| Rule | How this plan meets it |
| --- | --- |
| The charter's model is canonical; no legacy shape | `sentinel` is removed outright. A file carrying it appears only in an invalid-pack test |
| Tests and examples carry only the canonical shape | Every rule has its example in charter's own packs (ADR-0070). Each WP carries its own example (`ExamplePackCoverageTest`). No round-trip exemption |
| ADR-0022 | One Breaking fragment (`sentinel` → `performer`, the effective profile's meaning) and one Added fragment, in `contracts/charter/changes/` |
| Adversarial squad at the plan point and before merge | Post-plan squad done and folded; a pre-merge squad on the PR |
| Issues stay small | No issue filed for minors. Open-packs drafts carry `open-packs#TBD-<slug>` placeholders. Closeout closes #42, #512, #380 and #102, and ticks #538, #94 and #69 |
| ADR numbered when written | 0078 (F has 0077, #106 has 0076), rechecked when written |

## Design

### P1. Selection domain (`service/profiles`)

`ProfileSelection.select(role, SelectionContext, Candidates) → SelectionAnswer` is a pure domain service.
`Candidates` holds the following, all supplied by `service/activations`:

- the in-force, non-enhancer agent profiles of the activation, each with its pack, its closure position and its
  in-force `specializes_from` depth;
- the pin in force for the role;
- the fallback in force.

The order follows ADR-0069 d4, as ruled in D2. All filters run before the tier is picked:

1. **Reserved.** The path pattern refuses `generic` (`400`). For `operator`, only `performer: human` candidates
   remain, and the fallback is disabled.
2. **Pin.** When a pin is in force, keep only the pinned profile and its in-force specializations.
3. **Performer.** Drop a human-performed candidate unless the role is `operator` or the pin names it.
4. **Role fit.** Drop `RoleFit.NONE`.
5. **Context** (`languages`, `frameworks`):
   - a specialization that declares either list fits only when every asked dimension it declares contains the
     asked value, and at least one does;
   - roots, descriptor-free specializations and the pinned profile always fit.
6. **Tier.** Keep the best `RoleFit` left.
7. **Depth.** Keep the greatest depth, the longest in-force chain.
8. **Priority.** Keep the highest `routing-priority`, counting absent as 50.
9. **Tie.** If more than one candidate is left, answer all of them, sorted by closure position then id, with
   `tie: true`. The pinned profile can never be among the tied (D2), so there is no pin tie-break branch. ADR-0078
   records why.
10. **None.** Answer the fallback in force, marked `fallback`. With no fallback in force, or for `operator`, the
    answer is `NoProfileMatched`.

Unit tests cover each step, including the alt-only answer when every primary is filtered out (squad M1).

### P2. Closure facts (`service/packs`)

`ClosureRoleFacts` answers the fallback and the pins in force for a set of packs:

- **Fallback:** the most derived declaring pack wins (ADR-0068 d5). Unrelated packs with differing fallbacks raise
  `fallback_profile_conflict`.
- **Pins:** D3(ii). Under the recommendation, pins are tighten-only: a differing pin anywhere in the set raises
  `role_pin_conflict`.

It reuses `ClosureView.holds`, `winnerPackOf` and `FallbackProfileRule`'s holder lookup.

Registration rules sit in `RegistrationImpact`, beside `FallbackProfileRule`:

- `RolePinRule`: `role_pin_unknown`, `role_pin_not_fitting`, `role_pin_conflict`;
- `fallback_profile_not_agent`, an extension of `FallbackProfileRule`;
- `SpecializationCycleRule`: `specialization_cycle`.

`operator_role_not_human` is an entity rule in `AgentProfile`, reported as a schema violation at the item, as the
alt-role rule is (ADR-0069 d3). #556 moves `FallbackProfileRule` behind ports; whichever of #556 and G lands second
moves the new rules with it.

### P3. Delivery and overlaps

The slice lands as **one PR (#573)**, by operator decision (2026-11-14). WP01 to WP06 may be implemented on the
mission branch before F lands. The PR stays a draft until F has landed, WP07 is done and the branch is rebased onto
F. There is no separate F-independent PR.

G builds no `Relationship` in main code; it only reads relationships. Its overlaps with F are these:

- the example packs and their manifests;
- `CharterRefusalCode.yaml` and `RefusalCode.java`;
- `EffectiveReadService`;
- test code that calls the `Relationship` constructor, which F makes package-private.

#106 also edits `EffectiveReadService`, `EffectiveTag`, `policy.schema.json` and the profile DTOs. Whichever lands
second rebases; the order is agreed through the orchestrator. 

### P4. Contract

`selectAgentProfile`, tag `EffectiveAgentProfiles`:

```
GET /activations/{activationId}/roles/{role}/profile-selection?language=&framework=
  role: ^(?!generic$)[a-z][a-z0-9-]{0,63}$
200 ProfileSelectionRead { role, context{language?, framework?}, candidates[{id, pack, fit, links}], tie, links }
304 If-None-Match; ETag over (role, context, the in-force profile set and their bodies, policies, descriptors)
404 activation_not_found, then no_profile_matched
422 fallback_profile_conflict | role_pin_conflict
```

- **Refusal order:** activation first, then conflicts, then matching. `stale_input` follows the existing effective
  reads (double digest).
- **Codes:** `no_profile_matched` and `fallback_profile_conflict` become raised; they already default to 404 and
  422, so no override is needed. The new codes are registered with their statuses, named in main code
  (`ReservedRefusalCodesTest`) and listed in `errors.feature`. Each raised code gets a pin scenario.
- **`AgentProfile`:** `sentinel` is out and `performer` (`agent | human`) is in, omitted when the file is silent.
- **`EffectiveAgentProfileRead`:**
  - the body is the effective profile;
  - `lineage: [{id, pack, depth}]`, nearest first;
  - an `enhances` link when the profile is itself an enhancer;
  - the description and the `_links.artifact` text are reworded.
- **Pack schemas:**
  - `agent-profile.schema.json`: `sentinel` out, `performer` in;
  - `policy.schema.json`: `role_pins` in, and the reserved-pins sentence amended;
  - the registration violations of P2 and P5.

WP07 (after F) adds `selectAgentProfileForStep` at `.../mission-types/{missionType}/actions/{action}/profile-selection`, and a
`profile-selection` link on the effective step and action-context reads.

### P5. Effective profile (R8) and the merge (#380), D5 and D6

`ProfileEnhancementMerge` (profiles domain) merges each in-force enhancer of a profile in closure order, nearest pack
last.

- **Identity fields are skipped:** `profile-id`, `name`, `purpose`, `role`, `alt-roles`, `performer`,
  `routing-priority`.
- **Every other top-level field the enhancer declares replaces the base value**, under D5(a). The replacement is
  refused with `ProfileEnhancementDropsField(pack, field)` when it loses base content:
  - a list item missing;
  - an object sub-field missing or changed;
  - a scalar changed.
- **`excluding`** on an enhancer is a declared drop and is refused (D7a).

The same merge runs in `RegistrationImpact` (422). The read raises 500 for content stored before the rule.
`lineage` comes from the `GovernanceWalk` lineage step, extended once to yield the ordered chain with the longest
depth (C-002). Enhancers are excluded from `Candidates` and from `lineage`.

### P6. Records

- **ADR-0078:** D1 to D7. It amends:
  - ADR-0030: the pin's shape;
  - ADR-0032 d5 (`operator` reserved) and d9 (the tie-break is unreachable);
  - ADR-0069 d4.5: no fallback for `operator`;
  - ADR-0053 d22: profiles keep field-merge.
- `docs/api/service-contract.md`: the R8 row.
- `docs/architecture/logical-model.md`: selection decided.
- `docs/context/conceptual-model.md`: role pin and operator.
- Glossary: *selection*, *performer*.
- `tests/scenarios/charter/PROMISES.md`: the #380 gap closed.
- `contracts/charter/changes/`: one Breaking fragment and one Added fragment.
- Open-packs drafts in `research/open-packs-drafts.md`:
  - `open-packs#TBD-retire-sentinel`: `human-in-charge` moves to `performer: human`, `role: operator` (follows
    #139);
  - `open-packs#TBD-specialization-acyclic`: a check that open-packs content has no `specializes_from` cycles.

## Work packages

One code lane runs WP01 to WP05 in order, so each WP restamps the manifests once. WP06 follows.

| WP | Title | Issues | Rulings | F | Depends on |
| --- | --- | --- | --- | --- | --- |
| WP01 | `performer` replaces `sentinel`, end to end, with `operator_role_not_human`. Example: org-base `human-operator` (`performer: human`, role `operator`) | #512 | D4 | **independent** | — |
| WP02 | Role pins: schema, `PolicyYaml` reader and writer, round trip, `RolePinRule`, `ClosureRoleFacts`, `fallback_profile_not_agent`, `specialization_cycle`. Example: `project/policy.yaml` with `role_pins: {reviewer: reviewer-renata}` and no `kind_defaults` | #42 | D3, D4 | **independent** | WP01 |
| WP03 | Selection domain (P1) with unit tests per step | #42, #512 | D2, D3, D4 | **independent** | WP02 |
| WP04 | Lineage chain, enhancement merge and drop refusal (P5), in the domain and at registration. Example: an org-base enhancer of `reviewer-renata` that keeps every base field and adds one | #380, #102 | D5, D6, D7 | **independent** (rebase test fixtures onto F's factories) | WP02 |
| WP05 | Contract and edge (P4): `selectAgentProfile`, the effective profile with `lineage`, `service/activations` `Candidates` and `EffectiveTag.SelectionQuery`, raised codes, `@contract` scenarios, `profiles.feature` out of `@WIP`. Example: org-base `reviewer-rita` (the org-base tie) | #42, #102, #380 | D1, D6 | **independent** | WP03, WP04 |
| WP06 | Records (P6) | #102 | all | **independent** | WP05 |
| WP07 | Step-keyed read and the `profile-selection` links. A substep never delegates to an `agent_profile` (F, operator ruling), so the step's role is the only source | #42 | D1 | **dependent: after F (#567) lands** | WP05, slice F |

## Example packs (charter's own, ADR-0070)

| Context | Ask | Answer | Rule |
| --- | --- | --- | --- |
| built-in | `implementer` | `implementer-ivan`, primary | Primary fit |
| built-in | `implementer`, `language=java` | `java-jenny` | Depth within the tier |
| built-in | `implementer`, `language=kotlin` | `implementer-ivan` | Root fits any context |
| built-in | `facilitator` | `generic-agent`, fallback | Fallback |
| built-in | `operator` | `404 no_profile_matched` | No fallback for `operator` |
| org-base | `reviewer` | `reviewer-renata` and `reviewer-rita`, tie | Tie |
| org-base | `operator` | `human-operator`, primary | Human performer |
| project | `reviewer` | `reviewer-renata` (pin; `project-reviewer` filtered out) | Pin |
| project | `implementer` | `implementer-ivan`; `project-reviewer` (alt) loses | Primary over alt |
| test packs | — | the refusals: `sentinel` key, a pin that does not fit, sibling pins, sibling fallbacks, a human fallback, an agent `operator`, a cycle, an enhancer drop | Refusals |

Alt-only answer: the test pack asks for `implementer` with only `project-reviewer`.

## Risks

- **A ruling differs from the recommendation.** Only the WPs in its column re-plan. D5(b) changes WP04's merge and
  rewrites PR-CHARTER-REFUSAL-001. D3(ii-a) changes `ClosureRoleFacts` only.
- **F and #106 neighbours** (P3). The rebase order is agreed through the orchestrator.
- **The new org-base and project examples change pack manifests and existing reads of those packs** (profile lists,
  counts). WP01, WP02 and WP05 update the affected scenarios in the same change, with no exemption.
- **The ETag must cover pins, the fallback and every candidate's body.** It is a sibling query of the existing
  `EffectiveTag` mechanism.

## Project Structure (touched)

```
contracts/charter/{openapi.yaml, paths/, schemas/, examples/, scenarios/, changes/}
contracts/packs/schemas/{agent-profile,policy}.schema.json
service/profiles/        selection domain, merge, operator rule
service/profiles-edge/   controller, DTOs
service/activations/     Candidates under an activation, EffectiveTag.SelectionQuery, effective profile wiring
service/packs/           PolicyYaml, RolePinRule, ClosureRoleFacts, cycle and fallback rules, merge at registration
service/kernel/          RefusalCode entries
service/packs/src/testFixtures/resources/example-packs/{org-base,project}/
docs/adr/0078-*.md, docs/api/service-contract.md, docs/architecture/logical-model.md, docs/context/
```
