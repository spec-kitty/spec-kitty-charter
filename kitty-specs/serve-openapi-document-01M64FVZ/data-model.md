# Data Model: Serve the contract document

## ContractDocument (value, transport adapter)

| Field | Type | Rule |
|-------|------|------|
| text | String | the bundle bytes decoded as strict UTF-8; decoding failure refuses construction |
| etag | String | `"sha256:" + 64 lowercase hex of SHA-256(bytes) + "` (quotes included), a strong entity tag |

Invariants:

- Built once per service start from the packaged resource; immutable afterwards.
- `text` re-encoded as UTF-8 equals the source bytes (strict decoding guarantees it).

Behaviour:

- `matches(If-None-Match values)`: true when a value is `*`, or is a comma-separated list of entity tags
  (`W/` optional) one of whose opaque tags equals this document's opaque tag (weak comparison, RFC 9110
  §8.8.3.2). A value that is not an entity-tag list matches nothing. No header: false.

## Answers (no state machine)

| Condition (after the guard passed) | Answer |
|------------------------------------|--------|
| If-None-Match matches | 304, no body, ETag, Charter-Contract-Version, Charter-Worktree |
| otherwise | 200, `application/yaml`, text, ETag, Charter-Contract-Version, Charter-Worktree |

Never `Charter-Source-Fingerprint`.
