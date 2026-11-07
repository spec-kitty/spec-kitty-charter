# Decision Moment `01M6R0TA37CQ0VEMHWSTJF2TRM`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `specify`
- **Slot key:** `specify.registration.wire`
- **Input key:** `whole_pack_wire`
- **Status:** `resolved`
- **Created:** `2026-11-06T08:50:14.759703+00:00`
- **Resolved:** `2026-11-06T08:59:16.971579+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

How is a whole pack registered (full replacement)?

## Options

- PUT /packs/{packId} with an archive of the pack directory
- JSON aggregate
- POST /packs by source
- Other

## Final answer

Both: PUT /packs/{packId} with an archive of the authored pack directory (full replacement, validated as the whole-pack loader reads it, If-Match), and POST /packs by source (ADR-0005, #374)

## Rationale

_(none)_

## Change log

- `2026-11-06T08:50:14.759703+00:00` - opened
- `2026-11-06T08:59:16.971579+00:00` - resolved (final_answer="Both: PUT /packs/{packId} with an archive of the authored pack directory (full replacement, validated as the whole-pack loader reads it, If-Match), and POST /packs by source (ADR-0005, #374)")
