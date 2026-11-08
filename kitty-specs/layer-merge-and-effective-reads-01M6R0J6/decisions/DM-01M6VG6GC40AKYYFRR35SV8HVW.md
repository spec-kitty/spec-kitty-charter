# Decision Moment `01M6VG6GC40AKYYFRR35SV8HVW`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `per-artifact-impact`
- **Input key:** `per-artifact-impact-check`
- **Status:** `resolved`
- **Created:** `2026-11-07T17:16:46.340960+00:00`
- **Resolved:** `2026-11-07T17:39:37.718193+00:00`
- **Resolved by:** `operator-stijn`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

Should the per-artifact writes (PUT/POST /packs/{packId}/artifacts/...) also run IC-15's impact check? WP15's review showed a PUT can leave a closure that no longer composes (two org packs defining one id: PACK_SIBLING_CONFLICT), which a registration would refuse. IC-15 covers registration and DELETE only.

## Options

- Defer: record for the operator in PR A, follow-up issue (recommended)
- Extend IC-15 to the per-artifact writes in PR A

## Final answer

Yes: the per-artifact writes (PUT/POST /packs/{packId}/artifacts/..., and the relationship writes) run IC-15's impact check too, in PR A.

## Rationale

Operator ruling 2026-11-07 (chat): a per-artifact write must not leave a closure that no longer composes or breaks a relation rule.

## Change log

- `2026-11-07T17:16:46.340960+00:00` - opened
- `2026-11-07T17:39:37.718193+00:00` - resolved (final_answer="Yes: the per-artifact writes (PUT/POST /packs/{packId}/artifacts/..., and the relationship writes) run IC-15's impact check too, in PR A.")
