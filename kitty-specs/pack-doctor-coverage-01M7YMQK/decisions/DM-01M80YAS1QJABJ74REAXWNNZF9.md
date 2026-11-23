# Decision Moment `01M80YAS1QJABJ74REAXWNNZF9`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `plan`
- **Slot key:** `reference-resolution-set`
- **Input key:** `reference_resolution_set`
- **Status:** `resolved`
- **Created:** `2026-11-22T06:15:40.343634+00:00`
- **Resolved:** `2026-11-22T06:15:42.962712+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

With defined split into held and declared, what does a reference (compose, case-only, registration/uninstall diffs) resolve against?

## Options

- Derived referable()
- Steps go into held
- Compose reads declared

## Final answer

Derived referable()

## Rationale

Operator ruling R-v (2026-11-22): references resolve against a derived referable() view = held files plus held mission types' step (action:) nodes; no stored third field. Rename the retired-word carriers (PackUnderResolution.definedArtifacts, PackRegistrationService Before.held/heldBy, DomainRepositoryServiceTest.definedArtifactsNameEveryKindThePackHolds) to referable. A file-less node (directive:GHOST) does not resolve.

## Change log

- `2026-11-22T06:15:40.343634+00:00` - opened
- `2026-11-22T06:15:42.962712+00:00` - resolved (final_answer="Derived referable()")
