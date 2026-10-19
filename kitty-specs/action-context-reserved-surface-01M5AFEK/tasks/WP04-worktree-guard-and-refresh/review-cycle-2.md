---
affected_files: []
cycle_number: 2
mission_slug: action-context-reserved-surface-01M5AFEK
reproduction_command:
reviewed_at: '2026-10-19T18:38:48Z'
reviewer_agent: claude
wp_id: WP04
---

# WP04 review, cycle 2 (reviewer-renata, security lens): changes requested

I re-ran `./gradlew verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs --rerun-tasks` on bff5cc5 and it passed.

**Issue 2 (operator ruling) is resolved.**
- `POST /packs/refresh` / `refreshPacks` has the Packs tag and lives in `PacksController`.
- It answers 200 `PackListing` with one fingerprint, and a body gives a 400 before anything is read.
- It declares 401, 403 and 500, and `stale_input`, `pack_unreadable` and `internal_error` are covered.
- `RefreshController`, `paths/refresh.yaml` and `ServiceInfoAnswer.yaml` are gone.

**The nits are resolved.**

**`PackListing1` is accepted.** It has the same swagger-parser 3.1 `additionalProperties` cause as `Problem1`, which I showed in cycle 1 is not fixable in the contract. Track a follow-up issue for a generator-side dedupe (`Problem1`, `PackListing1`).

One blocking issue remains: the cycle-1 S8 fix can still be bypassed.

## Issue 1 (blocking, S8, still open): `carriesTokenInJson` is a substring test and is bypassed by ordinary JSON

`carriesTokenInJson` strips the raw JSON text down to letters and digits and looks for the substring `charterworktreetoken`. That only works when `worktree-token` is the first key inside `charter`.

I reproduced these on the bootJar built from bff5cc5, with the token valid and the service answering 200 to it:

| Route | Result |
|---|---|
| `--spring.application.json={"charter":{"worktree-token":"T"}}` | refused (OK) |
| `--spring.application.json={"charter":{"worktree-id":"wt","worktree-token":"T"}}` | **started**, Bearer T → 200 |
| `-Dspring.application.json={"charter":{"worktree-id":"wt","worktree-token":"T"}}` | **started**, Bearer T → 200 |
| `--spring.application.json={"charter":{"worktree-token":"T"}}` | **started**, Bearer T → 200 |
| `SPRING_APPLICATION_JSON` env var | started (correct) |

The sibling-key case is the natural one: a launcher that passes `worktree-id` and the token in one JSON object leaks the token into argv, and the guard says nothing.

**Fix:** judge the parsed JSON, not its text. Either of these works:

- **(a)** After Boot's `SpringApplicationJsonEnvironmentPostProcessor`, the environment holds a property source named `spring.application.json` with flattened keys, such as `charter.worktree-token`. Refuse when that source names the token (`namesToken`) and a readable source (`commandLineArgs`, `springApplicationCommandLineArgs` or `systemProperties`) holds a `spring.application.json` key in any spelling. This reuses Boot's own parser, so escapes, nesting and key order cannot diverge from what Boot binds.
- **(b)** Parse each readable source's `spring.application.json` value with Boot's `JsonParserFactory` (or the app's Jackson), flatten it as Boot does, and apply `namesToken` to the flattened keys.

Keep the false-positive-refuses rule for unparseable JSON if you like, but the check must not depend on key order or on escaping.

**Tests, red first:** add `GuardWiringTest` cases for:
- a sibling key before the token (`worktree-id` first), from argv and from system properties;
- a `-`-escaped key;
- a token nested as `{"charter":{"worktree":{"token":...}}}`, if Boot binds that spelling. Check, and include it only if it binds.

Keep the existing env-var acceptance test.

## Non-blocking

- Contract changes for WP05's list are recorded in history: `POST /packs/refresh` replaces `POST /refresh`, and `PackListing1` replaces `ServiceInfo1`. No action needed.
