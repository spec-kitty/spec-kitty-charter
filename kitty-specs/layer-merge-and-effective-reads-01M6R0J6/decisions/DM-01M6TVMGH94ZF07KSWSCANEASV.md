# Decision Moment `01M6TVMGH94ZF07KSWSCANEASV`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.phase-a.wp11-reserved`
- **Input key:** `wp11_reserved_controller`
- **Status:** `resolved`
- **Created:** `2026-11-07T11:17:25.161214+00:00`
- **Resolved:** `2026-11-07T11:17:27.611390+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

WP11 review cycle 1: who creates the reserved RegistrationApi controller, who fixes the fingerprint header, which WP binds the IC-15 and version-conflict scenarios, and who owns Link.yaml's 'a pack write is never DELETE'?

## Options

_(none)_

## Final answer

(1) WP11 adds a minimal type-level @ReservedOperation controller implementing the generated RegistrationApi that answers 501 for the seven operations (before binding the PUT/POST bodies), plus a non-WIP @contract @reserved pin feature for the pack-registration set; ReservedOperationCoverageTest is keyed by contract set with one-to-one pins per set, and the no-body check covers the new operations. WP14/WP16/WP19 replace the reserved handlers when they serve them. (2) WP11 removes Charter-Source-Fingerprint from getPackRegistration's 200 (header, description, fragment line) and adds the status path to the lint rule's exemption list now. (3) The IC-15 impact scenarios and the version-conflict scenario are bound by WP16 (WP15 builds the check; WP16 serves the PUT). (4) Link.yaml's DELETE sentence goes to WP20.

## Rationale

WP11 review cycle 1 (reviewer-renata); keeps ADR-0020 D8's reserved-operation guard intact instead of narrowing it.

## Change log

- `2026-11-07T11:17:25.161214+00:00` - opened
- `2026-11-07T11:17:27.611390+00:00` - resolved (final_answer="(1) WP11 adds a minimal type-level @ReservedOperation controller implementing the generated RegistrationApi that answers 501 for the seven operations (before binding the PUT/POST bodies), plus a non-WIP @contract @reserved pin feature for the pack-registration set; ReservedOperationCoverageTest is keyed by contract set with one-to-one pins per set, and the no-body check covers the new operations. WP14/WP16/WP19 replace the reserved handlers when they serve them. (2) WP11 removes Charter-Source-Fingerprint from getPackRegistration's 200 (header, description, fragment line) and adds the status path to the lint rule's exemption list now. (3) The IC-15 impact scenarios and the version-conflict scenario are bound by WP16 (WP15 builds the check; WP16 serves the PUT). (4) Link.yaml's DELETE sentence goes to WP20.")
