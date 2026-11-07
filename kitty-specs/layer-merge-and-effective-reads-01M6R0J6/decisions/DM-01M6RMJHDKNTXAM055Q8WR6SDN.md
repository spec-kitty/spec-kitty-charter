# Decision Moment `01M6RMJHDKNTXAM055Q8WR6SDN`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.ruleset.v1-upgrade-selections`
- **Input key:** `v1_upgrade_selections`
- **Status:** `resolved`
- **Created:** `2026-11-06T14:35:31.635650+00:00`
- **Resolved:** `2026-11-06T14:35:34.085558+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

v1 to v2 upgrade: selections outside the never-enforced activatedKinds gate

## Options

_(none)_

## Final answer

Keep every selection; drop the gate. The gate had no observable effect (reads 501, writes ignored it), so the upgrade changes nothing the user saw.

## Rationale

_(none)_

## Change log

- `2026-11-06T14:35:31.635650+00:00` - opened
- `2026-11-06T14:35:34.085558+00:00` - resolved (final_answer="Keep every selection; drop the gate. The gate had no observable effect (reads 501, writes ignored it), so the upgrade changes nothing the user saw.")
