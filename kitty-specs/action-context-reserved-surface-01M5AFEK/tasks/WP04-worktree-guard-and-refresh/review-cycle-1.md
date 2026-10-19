---
affected_files: []
cycle_number: 1
mission_slug: action-context-reserved-surface-01M5AFEK
reproduction_command:
reviewed_at: '2026-10-19T18:28:50Z'
reviewer_agent: claude
wp_id: WP04
---

# WP04 review, cycle 1 (reviewer-renata, security lens): changes requested

I re-ran `./gradlew verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs --rerun-tasks` on ff64134/be2b3ca and it passed: 96 tasks, security.feature 30 and refresh.feature 8 green, WorktreeGuardTest 51, WorktreeTokenTest 14, GuardWiringTest 18.
`gradle/verification-metadata.xml` and `libs.versions.toml` are untouched. The red run over inert stubs was real: a fail-open guard gives 200s, and `matches()` returns false with an empty `GuardWiring`, so every failure was an assertion failure.
The guard itself, the constant-time compare, redaction, header shapes, refresh and the contract surface hold up under attack (see the notes at the end).
Two blocking issues remain (Issue 2 is an operator decision received during review).

## Issue 1 (blocking, S8): the token is accepted from argv through `spring.application.json`

`GuardWiring.refuseReadableSources` checks only the property *names* in `commandLineArgs`, `springApplicationCommandLineArgs` and `systemProperties`.
Spring Boot's `SpringApplicationJsonEnvironmentPostProcessor` expands `--spring.application.json=…` and `-Dspring.application.json=…` into a separate property source named `spring.application.json`. That source is not checked, and the argv/sysprop key is `spring.application.json`, which normalizes to `springapplicationjson`, not `charterworktreetoken`.

I reproduced it on the shipped bootJar:

```
java -jar charter-service-0.1.0-SNAPSHOT.jar --server.port=18777 \
  '--spring.application.json={"charter":{"worktree-token":"argvtoken-0123456789abcdef0123456789abcdef"}}'
-> starts; GET /api/v1/info without token 401, with that Bearer token 200;
   the token is in /proc/<pid>/cmdline (world-readable)
```

The plain `--charter.worktree-token=` form is correctly refused. S8 and D9 say argv and system-property sources are refused, and this route gets around that control. It is not documented as a residual anywhere: not in the Javadoc, the contract or the history.

**Fix:**
- Refuse the start when the token resolves from the `spring.application.json` property source and either readable source (command line or system properties) holds a `spring.application.json` key, in any spelling, normalized as `namesToken` does.
- An equivalent stricter rule is fine too. For example: refuse when any readable source holds a `spring.application.json` value that names the token.
- `SPRING_APPLICATION_JSON` in the environment must stay accepted.
- Never put the token in the message.

**Prove it in `GuardWiringTest`:**
- one test for the argv form and one for the system-property form;
- one positive test for the env-var `SPRING_APPLICATION_JSON` form.

`ApplicationContextRunner` does not run `EnvironmentPostProcessor`s. Either run `SpringApplicationJsonEnvironmentPostProcessor` in an initializer, or use a `SpringApplication` / `SpringApplicationBuilder` with `WebApplicationType.NONE`.

Add one sentence to the `GuardWiring` Javadoc naming this route.

## Issue 2 (blocking, operator decision, Stijn, 2026-10-19): refresh moves under the Packs domain

The operator asked: "What would refresh do? Force a retrieval of the packs and clear the cache? If so: keep it, but add it under its relevant API domain: e.g. `packs/refresh`, similar to `packs/register`."
The orchestrator resolved it as follows, and the change is required:

- **Operation:** `POST /packs/refresh`, `operationId: refreshPacks`, tag **Packs** (not Service). Replace `POST /refresh` / `refreshSources`.
- **Meaning:** it forces the service to re-read every configured pack and the project configuration and to discard any built read model (cache). This version keeps none, so it re-reads and re-verifies. Update the Javadoc and the contract description to match. The #73 note stays: once a built graph exists, refresh rebuilds it.
- **Request:** no body. A non-empty body is a 400 Problem, checked before anything is read. Keep the current `Content-Length`/first-byte probe and never parse the body.
- **Answers:**
  - 200: the refreshed `PackListing`, the same schema as `GET /packs`, with exactly one `Charter-Source-Fingerprint` header, the verified digest;
  - 500: `OfferingUnreadable` (pack codes, `stale_input`);
  - 401 and 403 (`Unauthorized`/`Forbidden`);
  - `default`: shared Problem.
- **Behavior:** idempotent. It is a two-digest consistent read through `ConsistentLoad`, as `GET /packs` is (the same path `PackListingQuery` uses).
- **Code:** the Packs controller implements it (`PacksController implements` the generated `PacksApi`, grouped by the Packs tag), not `InfoController`.
  - Remove the `InfoController` → `RefreshController` delegation.
  - Fold `RefreshController` into `PacksController`, or keep it as its collaborator.
  - This should also remove the generator's duplicate `ServiceInfo1` DTO. Confirm in the generated model directory and drop the "as it does Problem1" Javadoc.
  - Drop `responses/ServiceInfoAnswer.yaml` (and its `_index.yaml` entry) if nothing else needs it, and restore `info.yaml`'s 200 as before or keep it local.
- **Files:**
  - Rename `paths/refresh.yaml` to `paths/packs_refresh.yaml`, following the existing `paths/` naming convention, for example `mission-types_missionType_actions_action_context.yaml`.
  - Update `openapi.yaml`'s path map (`/packs/refresh`).
  - Move `refresh.feature` to the new path and the listing body. Its scenarios assert the `PackListing` shape. Assert that the fingerprint equals the one `/info` reports, and that the listing equals `GET /packs` after the same inputs. Keep the body → 400, no token → 401, browser → 403, `internal_error` and idempotency scenarios. Add a pack-file change → new fingerprint and new listing.
  - Update `ProductionWiringTest.aRefreshOverTheRealServer…`, `ConnectorErrorsTest`'s `Allow` expectation if it changes, `ProblemResponsesTest`, and `scenarios/README.md` for the new path.
- **History:** record this in WP04 history as an **operator decision** (Stijn, 2026-10-19). Name the contract change for WP05's CHANGELOG/doc list: `POST /packs/refresh` replaces `POST /refresh`; `ServiceInfo1`/`ServiceInfoAnswer` are gone if removed.

## Non-blocking notes (no action required for approval)

- **`ServiceInfo1` duplicate DTO (superseded by Issue 2, which should remove it).** I tried three contract restructurings in a scratch copy with openapi-generator 7.25.0 / swagger-parser 2.1.46, and none removes it:
  - (a) refresh 200 `$ref: './info.yaml#/get/responses/200'` → `ServiceInfo1`;
  - (b) root `components.schemas.ServiceInfo: $ref: schemas/ServiceInfo.yaml` → `ServiceInfo2`;
  - (c) the 200 inlined in both paths with a direct schema `$ref` → `ServiceInfo1`.

  The `openapi-yaml` dump shows the cause. swagger-parser's 3.1 resolution yields `additionalProperties: {}` on the first resolution of the external schema and `false` on the second, so the two copies compare unequal. It is the same cause as the existing `Problem1`. The shared `responses/ServiceInfoAnswer.yaml` is already the right contract shape, and a bundler that dedupes by file (Redocly, for example) produces one schema.

  Follow-up outside this WP: a generator-side mapping in build-logic (for example `schemaMappings`/`importMappings` of `ServiceInfo_1`/`Problem_1`) so core's generator config can mirror it. Recorded for WP05/core.
- **Refresh `stale_input`.** The contract declares 500 `stale_input` for `POST /refresh`, and the code path is the same `ServiceInfoQuery.answer()` that `ServiceInfoQueryTest` covers. A `POST /refresh` row in an errors.feature stale outline would make the declaration observed. Optional.
- **`ScenarioHttp.bearerToken(@Nullable String)`** has no caller (WP02 glue). Delete it, or use it from `SecuritySteps`. Nit.
- **`WorktreeGuard.sentByBrowser`.** `getHeader(h) != null || getHeaders(h).hasMoreElements()` is redundant, and one check suffices. Nit.
