# Decision Moment `01M6NXNBA1J808JV4SHT743SCF`

- **Mission:** `activation-register-and-closure-01M6NXBD`
- **Origin flow:** `specify`
- **Slot key:** `specify.closure.exposure`
- **Input key:** `closure_exposure`
- **Status:** `resolved`
- **Created:** `2026-11-05T13:16:37.569757+00:00`
- **Resolved:** `2026-11-05T13:17:44.415930+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Where do the activated-pack closure (#360) and the PackPin depends_on facts (#359) show, given ADR-0059 D5 keeps offering-derived data out of the Activation answer?

## Options

- Writes validate against the closure; facts on a closure sub-resource
- Facts on the Activation answer
- Other

## Final answer

New GET /activations/{id}/packs sub-read: closure rooted at the activation's packs in dependency order with version and depends_on facts; /offering stays the available catalog; writes and cascade see only the closure (outside -> 422); @WIP scenario rewritten against the new read

## Rationale

_(none)_

## Change log

- `2026-11-05T13:16:37.569757+00:00` - opened
- `2026-11-05T13:17:44.415930+00:00` - resolved (final_answer="New GET /activations/{id}/packs sub-read: closure rooted at the activation's packs in dependency order with version and depends_on facts; /offering stays the available catalog; writes and cascade see only the closure (outside -> 422); @WIP scenario rewritten against the new read")
