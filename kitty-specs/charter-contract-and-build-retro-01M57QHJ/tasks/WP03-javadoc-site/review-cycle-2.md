---
affected_files: []
cycle_number: 2
mission_slug: charter-contract-and-build-retro-01M57QHJ
reproduction_command:
reviewed_at: '2026-10-18T20:57:19Z'
reviewer_agent: claude
wp_id: WP03
---

# WP03 review 2 (reviewer-renata): changes requested

Reviewed fix commit 82b6756 (d136f60..82b6756) on lane-c. Findings 1 and 3 of review 1 are closed. Finding 2
is only half closed: the banner no longer says that no code exists, but it now overstates what is built.
That is the one blocking item, and the fix is a single phrase.

## Verified (reviewer reproductions)

- `./gradlew clean verifyAll --no-build-cache --configuration-cache-problems=fail`, starting from an
  empty `.gradle/configuration-cache`: BUILD SUCCESSFUL, 644 tests (571 service + 73 contracts),
  0 skipped, 0 failed. The next identical run printed "Reusing configuration cache" and "Configuration
  cache entry reused".
- Module-list guard, planted cases (all reverted, worktree clean):
  - A: I added a hand-written `service/charter-planted` and included it in `settings.gradle.kts`,
    with the configuration cache warm. Gradle reported "configuration cache cannot be reused because
    file 'settings.gradle.kts' has changed", then "Not listed: [charter-planted]", BUILD FAILED.
    Running it again failed the same way: a failed configuration is never cached, so a reused cache
    cannot bypass the guard.
  - B: removing `charter-read-app` from `serviceModules` failed `help` with "Not listed:
    [charter-read-app]".
  - C: listing `charter-api` failed with "Listed but not a hand-written service project:
    [charter-api]".
  - After reverting, the run reused the cache and the build passed.
- Configuration cache and isolation: the guard only reads other projects' `projectDir` and
  `pluginManager.hasPlugin`. It mutates nothing and touches no task, so it meets the plan's "no
  cross-project mutation" rule. It is cross-project *reading*, and so not compatible with Isolated
  Projects (see finding 3, LOW). The WP prompt already sanctioned selecting by
  `plugins.hasPlugin("charter.hand-written")`, which is the same kind of read.
- The `charter.hand-written.gradle.kts` header now matches the code and names the guard.
- Docs:
  - All eight `check_docs.py` checks pass.
  - `scripts/docs/tests`: 23 passed.
  - markdownlint-cli2@0.18.1: 0 errors in 58 files.
  - cspell@9.2.0: 0 issues in 58 files.
  - No "feature" wording.
  - The add-history note routes the stale `docs/README.md`, `sections.yaml`, `service-contract.md` and
    `compatibility.md` claims to WP05.

## Findings

1. **[MEDIUM] docs/api/README.md:3 and :21-30 - The banner and description now claim that the read
   side is built and only the writes are designed.** The banner says "its read operations are served
   from the contract … Still only designed: the Python package surface … and the planned write
   operations". The description says "the Java read service, its contract and Javadoc, and the
   still-designed Python package surface and write operations". `contracts/charter/openapi.yaml` has
   two operations: `/packs` and `/mission-types/{missionType}/actions/{action}/context`.
   `docs/api/service-contract.md` § "Planned read surface" lists seven reads. Five of them are not in
   the contract or the code: profile resolution, active charter, artifact lookup, graph query and
   diagnostics. This hub's audience is `contract-consumer`, with the guidance "how far to trust it". A
   consumer reading the banner would assume those five reads exist.
   **Recommendation:** name what is built, for example: "Two read operations are in the contract and
   served by the Java service: pack listing and action-scoped context. The other planned reads, the
   write operations and the Python package surface are still only designed." Make the matching change
   in the description, which must stay within 50–180 characters.

2. **[LOW] scripts/docs/check_docs.py `_under_generated_route` - Decoding mostly tightens the check,
   but in two shapes it loosens it.** It now rejects `%2e%2e`, `%2E%2E` and `x%2F..%2F..%2F`, which is
   the intent. But two links that were refused before are now accepted:
   - `/%6Aavadoc/x.html`: this one is harmless, because it is the same URL as `/javadoc/x.html` under
     RFC 3986.
   - `/javadoc%2Fx.html`: here a server treats `%2F` as part of a single segment, so the link would
     404.

   `check:built-links` would catch the second one, so this does not block. Optionally, accept a link
   only when both the raw path and the normalized decoded path start with the prefix, and add
   `/javadoc%2Fx.html` to the test's negative controls. A double-encoded `%252e%252e` stays inside the
   prefix, which is correct.

3. **[LOW] service/charter-reports/build.gradle.kts guard - It reads other projects' state in
   `gradle.projectsEvaluated`.** That is fine under the configuration cache and under the plan's
   no-mutation rule, but it would have to be reworked if the build ever enables Isolated Projects. For
   example, settings could pass the service project list in, or each service project could publish a
   marker. Consider one sentence on this in the comment. No action is needed now.

## Anti-pattern checklist

1. Dead code: PASS. The guard runs on every configuration.
2. Synthetic fixture: PASS. The test drives the real `links` check, and the `%2e%2e` control fails as
   expected.
3. Silent empty return: PASS.
4. FR coverage: PASS, unchanged from review 1. The guard strengthens FR-011's "every hand-written
   module".
5. Frozen surface: PASS.
6. Locked decision: PASS. C-005 holds, and nothing is mutated across projects.
7. Shared-file ownership: PASS. `charter.hand-written.gradle.kts` (WP02) gets a comment-only edit,
   which is recorded. The WP05 hand-off is recorded in add-history.
8. Production fragility: PASS. The new `GradleException` is a deliberate fail-closed configuration
   error that names the remedy, and no race can trigger it.
