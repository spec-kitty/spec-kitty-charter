# Research: Mutation testing floors, and PIT on packs (Phase 0)

## R-01 Baseline (`4b52bcb`, re-measured on main `85c20fa`)

| Module | Mutants | Score | Strength | No coverage | Ruled floor |
|---|---:|---:|---:|---:|---:|
| kernel | 378 | 72.5 % | 90.7 % | 76 | 65 |
| missions | 144 | 75.0 % | 95.6 % | 31 | 70 |
| profiles | 66 | 53.0 % | 94.6 % | 29 | 50 |
| packs domain+application | — | not measured | | | `floor5(score − 3)`, to be ruled |
| packs infra.storage | — | not measured | | | `floor5(score − 3)`, to be ruled |

With PR #384 (not yet merged): kernel 78.3 %, profiles 97.0 %. The operator ruled to ship the floors above and
raise them by a follow-up PR after #384 merges.

## R-02 Rulings (2026-11-04 and 2026-11-05)

- Adopt #239 as designed: fixed floors, raised by PR when the score clears the floor by 5, never lowered without
  an ADR amendment.
- Floors are kernel 65, missions 70, profiles 50; packs floors come from the first measured run and are reported
  before they are written.
- `packs.infra.storage` is in scope, with its own floor in the same `:packs:pitest` run (decision of 2026-11-05).
- Ship the ruled floors now, raise later.

## R-03 Known facts from #239

- Packs cannot apply `charter.domain-conventions`, which refuses external dependencies; hence a separate
  mutation-conventions plugin.
- Applying PIT to packs fails dependency verification until the pitest plugin pom and the Spring-BOM-resolved
  `junit-jupiter-api` module are recorded deliberately.
- `mutations.xml` contains raw `<` in mutator descriptions: never parse it strictly.
- `service/packs/pit-targets.init.gradle` narrows targets by system properties; keep it working.

## R-04 Overlap

- #377 regenerates `verification-metadata.xml` from scratch, and #368 edits `charter.hand-written.gradle.kts`.
  Whichever lands second rebases and regenerates the metadata additively.
- The parallel session's #359, #360 and #291 work may touch `service/packs/build.gradle.kts`; it was notified on
  2026-11-05.
