# Decision Moment `01M7SBCZ8D3EFNH0S44RBJ3MBP`

- **Mission:** `pack-delete-and-update-guard-01M7SANQ`
- **Origin flow:** `specify`
- **Slot key:** `specify.wire.delete-semantics`
- **Input key:** `delete_semantics`
- **Status:** `resolved`
- **Created:** `2026-11-19T07:30:08.269213+00:00`
- **Resolved:** `2026-11-19T07:30:10.293844+00:00`
- **Resolved by:** `operator (relayed by orchestrator session_019C2Tk6DpTSEBeTcn9F6smy)`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

DELETE /packs/{packId} semantics?

## Options

- synchronous, If-Match required (428/412, * allowed), 404 pack_not_found, 409 registration_conflict for built-in or read-only, 503 when registration is off, whole-directory swap under both locks

## Final answer

Accepted as listed: synchronous; If-Match required (428 absent, 412 stale, * allowed); 404 pack_not_found; 409 registration_conflict for a built-in or read-only-location pack; 503 pack_source_unavailable while registration is off; the pack directory is removed whole in one swap under the registry lock then the pack lock.

## Rationale

_(none)_

## Change log

- `2026-11-19T07:30:08.269213+00:00` - opened
- `2026-11-19T07:30:10.293844+00:00` - resolved (final_answer="Accepted as listed: synchronous; If-Match required (428 absent, 412 stale, * allowed); 404 pack_not_found; 409 registration_conflict for a built-in or read-only-location pack; 503 pack_source_unavailable while registration is off; the pack directory is removed whole in one swap under the registry lock then the pack lock.")
