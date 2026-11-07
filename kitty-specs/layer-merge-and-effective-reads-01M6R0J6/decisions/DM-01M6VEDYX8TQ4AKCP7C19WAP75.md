# Decision Moment `01M6VEDYX8TQ4AKCP7C19WAP75`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `wp15-c21-gate`
- **Input key:** `wp15-review-cycle-1`
- **Status:** `resolved`
- **Created:** `2026-11-07T16:45:53.448740+00:00`
- **Resolved:** `2026-11-07T16:45:56.055746+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

WP15 review cycle 1: the staged gate treats an id an ancestor holds as a REGISTER, so an extendable ancestor id can be overridden. Where and how is that fixed, and how does the history survive a crash after the swap?

## Options

_(none)_

## Final answer

Amends 01M6VD94VEVPSC1JXQ1RA7YYHQ. (1) In WritePolicyGate's two entry points (authorizeStaged for registration, authorize for the per-artifact writes), a write whose id a pack in the closure below the written pack already holds is judged as a REPLACE against that holder's policy (A15 scope, B8: never the pack's own policy): refused 409 governance_policy_violation on locked and extendable, allowed on open. WP14's anExtendableArtifactOfTheAncestorMayBeRegisteredButNotReplaced flips its first step. The per-artifact change is a behaviour change on main (A0 permitted it): a ### Breaking fragment. (2) Version history: staging the next history appends the stored pack's own (version, revision) when the history lacks it, so a lost publish heals on the next registration; a publish failure after the swap is a logged WARN, never a refusal (the pack was replaced). (3) The impact check reads and resolves the configured descriptors once per check, shared by every closure and by the before and after views; a test counts reads or resolves.

## Rationale

Review probes: an extendable ancestor id was CREATED/REPLACED by a derived pack (C21 hole on both the registration and per-artifact paths); a crash between swap and publish allowed reusing a version; 200 dependents took 7.2 s under the registry lock. Same 409 code, so no contract shape change; the per-artifact behaviour is stricter, which 0.x records as Breaking.

## Change log

- `2026-11-07T16:45:53.448740+00:00` - opened
- `2026-11-07T16:45:56.055746+00:00` - resolved (final_answer="Amends 01M6VD94VEVPSC1JXQ1RA7YYHQ. (1) In WritePolicyGate's two entry points (authorizeStaged for registration, authorize for the per-artifact writes), a write whose id a pack in the closure below the written pack already holds is judged as a REPLACE against that holder's policy (A15 scope, B8: never the pack's own policy): refused 409 governance_policy_violation on locked and extendable, allowed on open. WP14's anExtendableArtifactOfTheAncestorMayBeRegisteredButNotReplaced flips its first step. The per-artifact change is a behaviour change on main (A0 permitted it): a ### Breaking fragment. (2) Version history: staging the next history appends the stored pack's own (version, revision) when the history lacks it, so a lost publish heals on the next registration; a publish failure after the swap is a logged WARN, never a refusal (the pack was replaced). (3) The impact check reads and resolves the configured descriptors once per check, shared by every closure and by the before and after views; a test counts reads or resolves.")
