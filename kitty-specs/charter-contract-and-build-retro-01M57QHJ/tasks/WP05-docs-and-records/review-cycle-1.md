---
affected_files: []
cycle_number: 1
mission_slug: charter-contract-and-build-retro-01M57QHJ
reproduction_command:
reviewed_at: '2026-10-18T21:34:09Z'
reviewer_agent: claude
wp_id: WP05
---

# WP05 review 1: changes requested

Commit under review: `9ada102`. One MEDIUM finding blocks. The LOW items are optional, but each is a one-line fix in a file this WP owns.

## Blocking

### M1 (MEDIUM): the earlier changelog entry still claims a CI run that has not happened

`docs/changelog/2026-10-18-contract-gradle-build-and-decisions.md:36-38` says the Gradle build is "run in CI on Java 25". `.github/workflows/service.yml` has never run. GitHub Actions lists only `Docs cleanliness` and `Docs site` as workflows, and every run so far belongs to one of them. Research §8 called the same wording in `roadmap.md` "untrue until FR-005", and this WP fixed the roadmap. The changelog entry, which is published on the docsite, still carries the claim. The WP's own review guidance says that nothing may claim a CI result that has not happened.

The premise that changelog entries are fixed history is not backed by a rule:

- `docs/_meta/doc-rules.yaml` `changelog-style` covers only naming, order and length.
- `freshness-sla` exempts changelog entries from the SLA. It does not forbid editing them.
- ADR-0003 says only that notable changes get a dated page.

The new entry also does not correct the old claim. It says "done pending the first CI run", and a reader of the earlier entry never sees that.

Fix: amend that line of the earlier entry, for example "...the open-packs round-trip; CI on Java 25 is configured, and its first run is pending ([2026-10-18 — Contract and build brought under governance](/changelog/2026-10-18-retroactive-contract-and-build-mission/))". Another option is a dated "Correction" line. Make the fix in the old entry: the new one is at 2777 of its 3000 characters.

Same pass, same class: in the new entry, "The Javadoc is published in the docsite" states a deploy that has not happened. `docs-site.yml` with its `service` job has not run yet. Reword it, for example "The Javadoc is built for the docsite and published with it ([Java API reference](/api/javadoc/))", or say that publishing happens on the first `docs-site` run on `main`.

## Non-blocking (LOW)

- L1 `docs/architecture/pack-registration-distribution.md:29`: this WP edited the page but left the banner saying "no code exists in this repository yet". The commit message says the stale banners were scoped. Suggested wording: "design only; none of this design is built yet (the service skeleton under `service/` predates it)".
- L2 `docs/architecture/overview.md:25` and `:29` (left untouched on purpose). The banner says "nothing in the diagram exists in this repository yet", but a REST entry point and a file-based read adapter exist, in the older horizontal module shape. Line 29 still points at open question 2 as if it were open; it is decided. Suggested: scope the banner the way `hexagonal-service.md:22` does, and say "is decided in open question 2", as `docs/README.md` now does. Leaving it is defensible, since the diagram draws the ADR-0006 slice shape, but the line 29 wording is stale.
- L3 `docs/guides/service-build.md`: "The generated `charter-api` applies only `charter.java-base`". It actually applies `charter.contract-api`, which applies `charter.spring-conventions`, which applies `charter.java-base`. The true point is that it does not apply `charter.hand-written`.
- L4 `docs/api/compatibility.md:19`: the opening sentence still reads "How the designed charter package and service contract are versioned", which contradicts the new description and the "Partly built" banner.

## Verified (no action)

- **Claims checked against the repository: 40.** All match except those above. They cover:
  - the contract: `1.0.0-SNAPSHOT`, exactly two operations (`listPacks`, `getActionContext`), `x-provisional` on the context operation, and the `CharterRefusalCode`/`DiagnosticCode` enums;
  - the six `:contracts:check` tasks, the 10 lint rules each with a fixture, and the vacuum and oasdiff pins with SHA-256 checks on four platforms;
  - `-PcontractBaseline=none`, which appears nowhere in `.github/`, plus the shallow-clone refusal and `fetch-depth: 0`;
  - the `contract-bundle`, `javadoc` and `coverage` artifacts, and `docs-site.yml` calling `service.yml`, then `npm ci`, the download into `site/public/javadoc` (gitignored) and the link check;
  - the hand-written gate set: `-Xlint:all -Werror`, NullAway with `OnlyNullMarked` on main only, doclint on main, Spotless on main and test, Checkstyle with zero warnings;
  - `suppressions.xml` with 0 entries, five modules in `charter-reports`, the fail-closed module list, `stageJavadocForSite`, PIT on `charter-domain` and `charter-read-app`, the nightly workflow with no pull-request trigger, and tool plugins in the catalog;
  - the ArchUnit rules, the `@IncludeTags("contract")` suite, `include("scenarios/**")`, and the three scenario homes on disk;
  - 644 tests with 0 skipped, from local test results;
  - the upstream issues: open-packs #46 and #47 (#47 includes the qa and stem defects), core #6402 and #6403, charter #12 (one comment), #36, #37, #31 and #9.
- **Decision Moments:** the three scenario homes and the rulings (questions 13 to 20) match DM `…QPDJY`, `…SQ9E3`, `…SQ2MG`, `…SQ5X9`, `…SQGC0` and `…RMVAZ`, and research §13.
- **Roadmap:** steps 1 and 2 both read "done pending the first CI run on the pull request".
- **Out-of-map edits:** the `docs/README.md` Status section, the `sections.yaml` purpose and the `compatibility.md` banner are accurate. The body sentence is L4.
- **Doc rules:** `pack_level_missing` links to `DiagnosticCode` on all four pages. There is no edit under `docs/glossary/`. The spelling is US and "feature" appears only in path literals. The new frontmatter is complete. All three cspell words are needed.
- **`hexagonal-service.md`:** leaving it untouched is fine (C-006). Its banner already says the skeleton predates ADR-0006.
- **Checks:** all eight `check_docs.py` checks report ok. `scripts/docs/tests`: 23 passed. markdownlint-cli2 0.18.1: 0 errors in 59 files. cspell 9.2.0: 0 issues in 59 files. The diff touches no build file, so no Gradle run is needed.
