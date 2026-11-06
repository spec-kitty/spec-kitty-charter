# Decision Moment `01M6P6T4MTB6SPK0F0F6BSBR7N`

- **Mission:** `activation-register-and-closure-01M6NXBD`
- **Origin flow:** `plan`
- **Slot key:** `plan.architecture.slice-placement`
- **Input key:** `slice_placement`
- **Status:** `resolved`
- **Created:** `2026-11-05T15:56:31.770284+00:00`
- **Resolved:** `2026-11-05T16:36:07.529786+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Where does the activation domain live: a new activations Gradle module depending on packs, or a package inside packs?

## Options

- New activations module
- Inside packs
- Other

## Final answer

Top-level slice over packs: new service/activations module depending on packs, with its own infra.api controller naming generated types; generalize the ArchUnit generated-type rule now (#140)

## Rationale

_(none)_

## Change log

- `2026-11-05T15:56:31.770284+00:00` - opened
- `2026-11-05T16:36:07.529786+00:00` - resolved (final_answer="Top-level slice over packs: new service/activations module depending on packs, with its own infra.api controller naming generated types; generalize the ArchUnit generated-type rule now (#140)")
