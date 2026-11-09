---
affected_files: []
cycle_number: 1
mission_slug: regex-bounds-and-sonar-cleanup-01M706DF
reproduction_command:
reviewed_at: '2026-11-09T13:32:20Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP03
---

# WP03 review feedback (cycle 1) - reviewer-renata

## Blocking

1. **T011 scenario incomplete.** The WP requires a case where an executable entry of 256 characters gives `500 pack_unreadable`. `contracts/charter/scenarios/pack-id-length.feature` only covers the 64-char cap (directive id, 64 vs 65, lines 18-41). Nothing at contract level tests any 255 cap (asset `entry`, lifecycle guard `ref`, `artifact_key`). Add the 256-char executable entry scenario, plus a 255-char positive control if the existing step glue allows it. Reuse existing glue only. The lines are long, so keep the feature readable (for example, a Scenario Outline).

## Non-blocking

2. The domain and the schema disagree on the guard ref cap. Lane-b's domain caps each part of a guard reference at 64 (MAX_PART_LENGTH), so its effective ceiling is about 137. The schema's `lifecycle.schema.json` `GuardEntry.ref` is capped at 255. A ref with a 100-char part passes the schema, then the domain refuses it. Either note this in the fragment or accept it consciously; the domain is stricter, so it is safe.
3. T011 suggested a guard or procedure id; a directive id was used instead. That is fine, because directives are the only kind with the upper-case grammar. It would still be worth covering a kebab id too.

## Verified OK

- All 12 owned schemas have maxLength next to pattern. The numbers match T009 (64 for ids, states and pack name; 255 for ref, entry and artifact_key).
- I enumerated every pattern field that still has no maxLength. The remaining ones are schema versions, hashes, ULIDs, fixed-shape paths (prompt/guidelines/guide/preset paths), graph-shard and org-fragment URNs, roles, lifecycle `event`, guard `implemented_by`, and the agent-profile and preset fields outside scope. They are out of T009 scope by its own exclusions. Validation uses RE2/J, so none of them can overflow.
- The fragment sits under `### Breaking`, lists every field and cites #440 and the DMs.
- Real packs are within the caps (commit body: longest id is 55).
- `:contracts:check` passes, and the scenario suite passes, including both new scenarios.
