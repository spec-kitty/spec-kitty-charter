---
affected_files: []
cycle_number: 1
mission_slug: charter-contract-and-build-retro-01M57QHJ
reproduction_command:
reviewed_at: '2026-10-18T20:48:34Z'
reviewer_agent: claude
wp_id: WP03
---

# WP03 review 1 (reviewer-renata): changes requested

Reviewed lane-c 8004180 and d136f60 (HEAD c9e4790). The build side is solid, and I reproduced each claim
below myself. The two blocking findings are both about docs and comments. In the files this WP edits, two
statements now contradict what the WP builds. Each is a small fix.

## Verified (reviewer reproductions)

- `./gradlew clean verifyAll` (JDK 25, `-PopenPacksDir=... -PcontractBaseline=none --max-workers=2`):
  BUILD SUCCESSFUL. With `--no-build-cache`, the run gives 644 tests (571 service + 73 contracts),
  0 skipped and 0 failed. A second run printed "Reusing configuration cache", and so did a run with
  `--configuration-cache-problems=fail` from an empty cache. The only deprecation is the existing
  `Configuration.setVisible` in `charter.contract-api.gradle.kts:9`; it does not come from WP03.
- Aggregation: `element-list` holds exactly the five packages. No `ai/speckitty/charter/api` page
  exists, and `charter.api` types appear only as unlinked names (`implements ContextApi`). The only
  "contracts" hits are prose, such as `contracts/charter/scenarios/...`. Every public top-level type
  of the five modules has a page; the only types without one are the four package-private ones
  (`ScalarWriter`, `PackDescriptorReader`, `Yaml`, `Absent`). The overview has one tab per module. No
  local path leaks into the HTML. The JDK API is linked by default to docs.oracle.com/.../25.
- Doclint gates the aggregate itself. I planted an unclosed `<b>`, a bad entity and
  `{@link NoSuchType}` in `Urn.java`: `:charter-reports:aggregateJavadoc` failed with 3 errors. I
  planted an undocumented public method in `ActionContextQuery`: it failed with "warnings found and
  -Werror specified". I reverted both and the run went green again.
- JaCoCo: `testCodeCoverageReport` lists exactly the five packages, from 5 sessions, and includes
  neither `charter-api` nor `contracts`. `verifyAll` runs both reports through `charter-reports:check`.
- `withSourcesJar`: nothing is published (no `maven-publish`) and nothing globs `build/libs`. The only
  effect is an extra `*-sources.jar` under `assemble`.
- Back link, as rendered: `./../api/javadoc/` on `index.html` and `search.html`, and
  `../../../../../api/javadoc/` on `ai/speckitty/charter/domain/Urn.html`. doc-kitty v0.2.0
  `assert-no-broken-links.mjs`, run over the staged Javadoc plus a stub `api/javadoc/index.html`
  with `--base /spec-kitty-charter`, passed. With the stub removed, it reported 58 broken links that
  all resolve to `/spec-kitty-charter/api/javadoc/`. So the checker does walk the Javadoc, and the
  link resolves correctly at every depth.
- Allow-prefix probes: `/nope/x.html`, `/javadoc/../nope/`, `/javadoc/x/../../nope.html`, `/javadoc`
  and `/javadocs/x.html` all still fail. doc-kitty's `dockitty-check-links` (`npm run validate`)
  skips site-absolute and `.html` links, so the new page does not trip it.
- Docs checks: all eight `check_docs.py` checks report ok (and so does `all`), and
  `scripts/docs/tests` gives 23 passed. markdownlint-cli2@0.18.1 found 0 errors in 58 files, and
  cspell@9.2.0 found 0 issues in 58 files. The frontmatter is complete. There is no "feature" wording
  and the spelling is US.
- Stylesheet: every `--dk-*` value matches doc-kitty v0.2.0 `src/themes/spec-kitty/tokens.css`, both
  light and `[data-theme='dark']`. The font stacks match `index.ts`. Every JDK property the sheet sets
  exists in the JDK 25 `stylesheet.css`, so none is a silent no-op. The screenshots show light and
  dark both applied.

## Findings

1. **[MEDIUM] build-logic/src/main/kotlin/charter.hand-written.gradle.kts:3 - The convention comment
   now states a rule that the only aggregation breaks.**
   Line 3 says "Aggregations select projects by `plugins.hasPlugin("charter.hand-written")`, never by a
   module list." This WP edited the same file, and its aggregation, `service/charter-reports`, selects
   by a module list. It has a good reason: `:contracts` applies `charter.hand-written`, so selecting by
   the plugin would pull the contract checker into the service's API reference and coverage. That
   leaves a split-brain authority. The next author of an aggregation is told to use the selection that
   leaks `:contracts`. The next author of a hand-written service module is told that aggregation is
   automatic, when in fact the module would be left out of the Javadoc and the coverage report without
   any signal. plan.md:108 says the same as line 3, but the WP prompt relaxed it, so the code comment is
   now the stale authority.
   **Recommendation:** rewrite line 3 to say what is true: the service aggregations in
   `service/charter-reports` declare their module set by name, `:contracts` is hand-written but not
   part of the service, and a new hand-written service module must be added to `serviceModules` there.
   Better still, add a cheap fail-closed guard, such as a check that every `service/*` project applying
   `charter.hand-written` appears in `serviceModules`.

2. **[MEDIUM] docs/api/README.md:3 and :21-28 - The API hub now says that no code exists, and also
   links to the Javadoc of that code.**
   This WP adds the "Java API reference (Javadoc)" entry (line 55), which describes "the Javadoc of the
   service's hand-written Java modules, built by the main build". The same page keeps the description
   "Designed only; no code exists here yet." and the banner "**No charter code exists in this
   repository yet.**" Both were already stale (the service has existed since 51e96cf, before this
   mission). This WP makes the contradiction visible on the very page User Story 4's reader goes
   through, and WP03 is the only WP that owns `docs/api/README.md` (WP05 does not). So unless it is
   fixed here, it ships.
   **Recommendation:** scope the banner and the description to what is still only designed, for
   example the Python package surface and the planned write operations. State that the Java read
   service and its contract exist and that the Javadoc documents them. While doing this, tell WP05
   (record it with add-history) about the same stale claim in `docs/README.md:61`,
   `docs/_meta/sections.yaml:42` (API purpose "Not implemented yet") and the "Designed API. Not
   implemented" banners on `docs/api/service-contract.md` (a WP05-owned file) and `compatibility.md`.

3. **[LOW] scripts/docs/check_docs.py `_under_generated_route` - a percent-encoded escape is
   accepted.** `/javadoc/%2e%2e/nope.html` passes the links check. Browsers resolve `%2e%2e` as `..`
   (WHATWG URL), so the link escapes the prefix. `check:built-links` is the backstop, so this does not
   block. Optionally, refuse a path that contains `%2e`/`%2E` before the prefix test, and add it to the
   unit test's controls.

4. **[LOW] service/charter-reports/build.gradle.kts - aggregate doclint errors name the transform-cache
   path** (`~/.gradle/caches/9.8.1/transforms/<hash>/transformed/charter-domain-sources/...`), not
   `service/charter-domain/src/main/java/...`. In `verifyAll` the module's own `javadoc` task reports
   the real path for the same defect, so this is a usability note only. Optionally, mention it in
   the build file's header comment.

5. **[LOW, for the mission review] SC-004 "one click".** The sidebar reaches `/api/javadoc/`, and the
   Javadoc is one more click ("Open the Javadoc"). The WP prompt designed it this way and SC-004 is
   tracked, not gated. Recording it so that the mission review judges it on purpose.

## Anti-pattern checklist

1. Dead code: PASS. `generated_route_prefixes` and `_under_generated_route` are called from
   `_check_target`. `stageJavadocForSite` is documented and is consumed by WP04.
2. Synthetic fixture: PASS. The unit test runs the real `links` check over a tree.
3. Silent empty return: PASS. The `return []` for generated routes is commented, and a missing rule
   means there are no prefixes, which fails closed.
4. FR coverage: PASS. FR-011: the reviewer's planted malformed comment and the planted undocumented
   member both fail the aggregate. FR-012: the page, the staging task and the link check are in place;
   the built-site proof is deferred to WP04's CI, as T021 allows.
5. Frozen surface: PASS (nothing is marked frozen).
6. Locked decision: PASS. C-005 holds: charter-api is excluded from the Javadoc and the coverage.
7. Shared-file ownership: PASS with a note. `settings.gradle.kts` (WP01) and
   `charter.hand-written.gradle.kts` (WP02) are out-of-map one-liners, recorded in the commit message
   and in the add-history note. Neither file is in WP04's or WP05's map.
8. Production fragility: PASS (no new `raise`).
