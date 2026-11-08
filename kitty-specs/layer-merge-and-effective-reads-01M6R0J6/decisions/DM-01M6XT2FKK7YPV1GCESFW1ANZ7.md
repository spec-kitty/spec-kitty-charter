# Decision Moment `01M6XT2FKK7YPV1GCESFW1ANZ7`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `wp21-facts-cache-vs-416`
- **Input key:** `wp21-facts-cache-vs-416`
- **Status:** `resolved`
- **Created:** `2026-11-08T14:47:49.107518+00:00`
- **Resolved:** `2026-11-08T14:47:51.563144+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

WP21 T107 plans a CompositionFactsCache keyed by pack revision and the per-pack write generation; #416 asks that any cache follow its design, not a second mechanism. Build it in WP21 under #416's invariants, or drop it and leave caching to #416?

## Options

- build it under #416 invariants (generation + revision key, no fill from odd/moved or staged reads, failures not cached, bounded)
- drop the cache from WP21; #416 owns caching

## Final answer

build it under #416 invariants (generation + revision key, no fill from odd/moved or staged reads, failures not cached, bounded)

## Rationale

Runner, 2026-11-08: T107 is in the plan (NFR-007) and caches composed facts, not #416's listing or descriptors. It uses #416's invalidation rule, the per-pack PackWriteGeneration keyed by the configured name (#418 R5), plus the pack revision (content digest) so an out-of-band edit misses (#416 invariant 4). It never fills from a read whose generation was odd or moved, nor from a RegistryWriteLock holder's staged session (inv. 2, 3). Failures are not cached (inv. 6). Entries are capped with a test above the cap (inv. 5). It stays in packs.application with no storage types, and HexagonalArchitectureTest stays unchanged (inv. 8). The PackWriteGeneration javadoc about 'the facts cache' becomes true. #416 may later fold it into option A/B.

## Change log

- `2026-11-08T14:47:49.107518+00:00` - opened
- `2026-11-08T14:47:51.563144+00:00` - resolved (final_answer="build it under #416 invariants (generation + revision key, no fill from odd/moved or staged reads, failures not cached, bounded)")
