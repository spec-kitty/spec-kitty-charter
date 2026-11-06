# Decision Moment `01M6NXQJ61D2GSFJFK02XW6F9N`

- **Mission:** `activation-register-and-closure-01M6NXBD`
- **Origin flow:** `specify`
- **Slot key:** `specify.document.absent-key`
- **Input key:** `absent_key_meaning`
- **Status:** `resolved`
- **Created:** `2026-11-05T13:17:50.145045+00:00`
- **Resolved:** `2026-11-05T13:18:40.990415+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What does an absent activation key mean in the service now: ADR-0053 d10 'none' or the contract's per-kind default?

## Options

- None, now (ADR-0053 d10)
- Per-kind default until the split
- Other

## Final answer

None, now: absent and [] mean nothing in force for every selected kind, activatedKinds and missionTypes; select adds; kernel InForceWhenAbsent changes; absent packs still means every configured pack; divergence entry until the split

## Rationale

_(none)_

## Change log

- `2026-11-05T13:17:50.145045+00:00` - opened
- `2026-11-05T13:18:40.990415+00:00` - resolved (final_answer="None, now: absent and [] mean nothing in force for every selected kind, activatedKinds and missionTypes; select adds; kernel InForceWhenAbsent changes; absent packs still means every configured pack; divergence entry until the split")
