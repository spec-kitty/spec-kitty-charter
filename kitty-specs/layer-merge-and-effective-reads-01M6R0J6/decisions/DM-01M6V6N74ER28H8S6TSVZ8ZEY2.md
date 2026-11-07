# Decision Moment `01M6V6N74ER28H8S6TSVZ8ZEY2`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `wp14-salvage-yaml`
- **Input key:** `wp14-review-cycle-2`
- **Status:** `resolved`
- **Created:** `2026-11-07T14:30:02.638431+00:00`
- **Resolved:** `2026-11-07T14:30:05.174766+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

WP14 review cycle 2: how does the salvage of an unreadable stored pack read files that are valid YAML but fail the schema (graph edges, artifact ids)?

## Options

_(none)_

## Final answer

Amends DM-01M6V4WMF8CD8NBN7A065E9ZQ5. The salvage reads every file on its own, as plain YAML, so one bad file never costs another file's facts. (1) Graph files: every edges: entry whose source can be read is gated; an entry whose relation is unknown still gates its source with RELATIONSHIP. A file that is not YAML at all contributes nothing. (2) Artifact ids: an artifact file that fails the schema counts by its id: key read as plain YAML, and by the id its path names only when that key cannot be read; when the two differ, both ids count as removed-or-kept candidates (gate and scan both). (3) A file that throws while loading is recorded as that file's read fault; the others keep their ids, digests and implied edges.

## Rationale

Review cycle 2 probes p6, p6b, p11 (schema-broken graph files lose their locked edges) and p7, p7c (a file whose id differs from its stem hides a locked or referenced artifact). Valid YAML that fails a later schema is the same old-pack case as the legacy references: field.

## Change log

- `2026-11-07T14:30:02.638431+00:00` - opened
- `2026-11-07T14:30:05.174766+00:00` - resolved (final_answer="Amends DM-01M6V4WMF8CD8NBN7A065E9ZQ5. The salvage reads every file on its own, as plain YAML, so one bad file never costs another file's facts. (1) Graph files: every edges: entry whose source can be read is gated; an entry whose relation is unknown still gates its source with RELATIONSHIP. A file that is not YAML at all contributes nothing. (2) Artifact ids: an artifact file that fails the schema counts by its id: key read as plain YAML, and by the id its path names only when that key cannot be read; when the two differ, both ids count as removed-or-kept candidates (gate and scan both). (3) A file that throws while loading is recorded as that file's read fault; the others keep their ids, digests and implied edges.")
