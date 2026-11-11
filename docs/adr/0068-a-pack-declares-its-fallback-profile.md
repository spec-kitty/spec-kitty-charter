---
title: "ADR-0068: A pack declares its fallback profile in pack.yaml"
description: "pack.yaml names the fallback agent profile: found in the closure, locked along it, with the reserved role generic. A derived pack may name a more specific one."
doc_status: active
updated: 2026-11-10T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-10'
type: ADR
kind: ADR
related:
  - adr/0012-pack-mutability-policy
  - adr/0013-persistent-deactivation
  - adr/0030-removal-is-distinct-from-deactivation
  - adr/0032-steps-name-roles-and-profile-selection-order
  - adr/0056-a-refusal-code-may-declare-a-status-per-operation
  - adr/0063-pack-mutability-policy-file
  - adr/0065-pack-registration-replaces-the-whole-pack
  - adr/0069-a-profile-has-one-primary-role-and-optional-alt-roles
audience:
  - profile: pack-author
    guidance_text: Read this for how a pack names its fallback profile, what that profile must be, and what a derived pack may change.
  - profile: contract-consumer
    guidance_text: Read this for how a selection result says it is a fallback, and the refusal when there is none.
  - profile: charter-service-contributor
    guidance_text: Read this before building the fallback checks or the selection's fallback step.
---

# ADR-0068: A pack declares its fallback profile in pack.yaml

## Status

**Accepted**, 2026-11-10. Decider: Stijn Dejongh (operator), on
[charter#457](https://github.com/spec-kitty/spec-kitty-charter/issues/457) (tracking
[open-packs#69](https://github.com/spec-kitty/spec-kitty-open-packs/issues/69)). It settles the declaration that
[ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/) decision 3.3 and the
[conceptual model](/context/conceptual-model/) of [PR #199](https://github.com/spec-kitty/spec-kitty-charter/pull/199)
("Selection order", step 3) leave open. The operator ruled on the two open points: the declared profile resolves in
the pack's closure and is judged locked along it, and the fallback declares the reserved role `generic`.

## Context

- ADR-0032: when no profile fits the role a step asks for, the service returns the pack's declared fallback, marked
  as a fallback; built-in ships a role-neutral, `locked` one; a derived pack may declare a more specific fallback but
  cannot remove built-in's; the service hard-codes the rule, never the content.
- Nothing lets a pack declare one today. Built-in's `generic-agent` is an implementer (`routing-priority: 10`), and
  built-in has no mutability policy file, so every built-in artifact is `open`.
- [ADR-0065](/adr/0065-pack-registration-replaces-the-whole-pack/) D5: a pack's own policy never binds the pack; a
  level is the highest along the closure (ADR-0012 decision 3, ADR-0063 decision 3).

## Decision

1. **Declared in `pack.yaml`, not on the profile.** The descriptor gains an optional `fallback_profile: <profile-id>`
   (a string, 1 to 64 characters, in the identifier grammar `^[a-z0-9][a-z0-9_-]*$`). It is a fact about the pack, checked in one file, and a whole-pack
   replace (ADR-0065) carries it with the rest of the pack. A flag on the profile would let several profiles of one
   closure claim the role and would need a rule to pick one. `null` is not a value: a pack that declares no fallback
   leaves the key out.
2. **It resolves in the pack's closure.** The id names an agent profile held by the declaring pack or by a pack in its
   closure (`depends_on`, and the parent chain), so an org baseline can name a built-in or parent specialist without
   copying it. Otherwise: `fallback_profile_unknown`.
3. **It is locked along the closure.** The named profile's level, the highest any pack of the declaring pack's closure
   declares for it (ADR-0063 decision 3), must be `locked`. Otherwise: `fallback_profile_not_locked`. A pack's own
   policy counts here: it declares the level its own artifacts have for every pack built on it.
4. **It is role-neutral: `role: generic`.** `generic` is a reserved role token. The fallback profile declares
   `role: generic` and no `alt-roles`; otherwise: `fallback_profile_not_role_neutral`. `generic` never earns a role
   fit (ADR-0069 decision 4): a profile whose role is `generic` is served only at the fallback step, and a step does
   not ask for `generic`.
5. **Built-in ships one; a derived pack may name a more specific one.**
   - The built-in pack declares a fallback, locked by built-in's policy file. open-packs ships the declaration, the
     policy file and a role-neutral profile together (open-packs#69).
   - A derived pack may declare its own fallback (an org baseline). The fallback in force for a context is the one
     the most derived declaring pack of the closure names; built-in's still answers in any context whose closure has
     no other declaring pack.
   - A derived pack cannot remove built-in's: the descriptor has no value that removes it, and the locked profile
     cannot be replaced or removed (`409 governance_policy_violation`, ADR-0065 D5) or excluded or deactivated
     (`409 artifact_locked`, ADR-0013, ADR-0030).
6. **How the result is marked.** A selection answer says how it fits: `primary`, `alt` (ADR-0069) or `fallback`. The
   field belongs to the selection read, which is not built yet; this record fixes the three values.
7. **Refusals.**

   | Code | When | Status |
   | --- | --- | --- |
   | `fallback_profile_unknown` | the id resolves to no profile in the closure (decision 2) | `422` |
   | `fallback_profile_not_locked` | the profile is not `locked` along the closure (decision 3) | `422` |
   | `fallback_profile_not_role_neutral` | the profile's role is not `generic`, or it declares alt-roles (decision 4) | `422` |
   | `fallback_profile_missing` | the built-in pack declares no fallback | `422`, reserved (`raised: false`) until open-packs ships the declaration |
   | `fallback_profile_conflict` | two packs of one closure, neither derived from the other, declare different fallbacks | `422`, reserved for the selection read |
   | `no_profile_matched` | no profile fits and no fallback is in force for the context (ADR-0032 decision 3.4) | reserved for the selection read, `404` |

   The three raised codes are judged over the declaring pack's own closure, with the write in place, by the
   registration impact check (ADR-0065 D6): a whole-pack registration ends `refused` with the code, naming the closure,
   and nothing is stored. The profile is read as the write leaves it, and a write to a pack is judged for every pack
   whose closure holds it: a write to a dependency that removes, unlocks or re-roles the profile a derived pack names is
   refused with the derived pack's closure named. Like the other impact rules, a pack stored before the rule is not re-judged on a read; the
   startup census logs it.

## Consequences

### Positive

- The fallback is data a pack ships and the service checks; the service holds no fallback content.
- A client can always tell a real match from a fallback.

### Negative

- Built-in must ship a policy file and a new role-neutral profile; until it does, `fallback_profile_missing` cannot
  be raised.

### Risks

- A sibling conflict is only refused once the selection read is built. Mitigation: the code is reserved now.

## Alternatives considered

### A flag on the profile

Not chosen: any profile of a closure could raise it, so a pick rule and a tie rule would be needed, and a derived pack
could change the choice only by overriding a locked profile.

### Only the declaring pack's own profiles

Not chosen (operator): an org would have to copy a parent's profile to use it, forking its content.

### A fallback with no role

Not chosen (operator): `role: generic` says what the profile is; an absent role reads as a mistake.

## References

- [charter#457](https://github.com/spec-kitty/spec-kitty-charter/issues/457),
  [open-packs#69](https://github.com/spec-kitty/spec-kitty-open-packs/issues/69)
- [ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/), [ADR-0063](/adr/0063-pack-mutability-policy-file/),
  [ADR-0065](/adr/0065-pack-registration-replaces-the-whole-pack/)
