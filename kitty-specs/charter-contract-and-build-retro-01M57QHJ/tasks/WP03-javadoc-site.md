---
work_package_id: WP03
title: Javadoc site and docsite page
dependencies:
- WP02
requirement_refs:
- FR-011
- FR-012
- SC-004
planning_base_branch: ccr-9654c444-x7y94q
merge_target_branch: ccr-9654c444-x7y94q
branch_strategy: Planning artifacts for this mission were generated on ccr-9654c444-x7y94q. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into ccr-9654c444-x7y94q unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-charter-contract-and-build-retro-01M57QHJ
base_commit: 1d09cfddc100c518b2580f2d59df95ed6d49a044
created_at: '2026-10-18T20:23:23.516453+00:00'
subtasks:
- T017
- T018
- T019
- T020
- T021
phase: Phase 3 - Published API reference
history:
- at: '2026-10-18T15:42:56Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/charter-reports/
create_intent:
- service/charter-reports/build.gradle.kts
- service/charter-reports/src/javadoc/charter-docsite.css
- docs/api/javadoc.md
execution_mode: code_change
model: ''
owned_files:
- service/charter-reports/**
- scripts/docs/**
- docs/_meta/doc-rules.yaml
- docs/api/javadoc.md
- docs/api/README.md
- site/.gitignore
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 – Javadoc site and docsite page

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- Check `review_ref` in the event log (`spec-kitty agent tasks status`); address every item and record it with `spec-kitty agent tasks add-history WP03 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

One aggregated, searchable Javadoc for the five hand-written modules (never the generated
`charter-api`), styled like the docsite, reachable from the docsite sidebar as a dedicated
set of pages. Also the aggregated JaCoCo report.

Done when:

- `./gradlew :charter-reports:aggregateJavadoc` (name as you see fit) builds, with doclint as errors, a single site grouped per module; `verifyAll` depends on it.
- A docsite page `docs/api/javadoc.md` appears in the API section and links to `/javadoc/index.html`; with the Javadoc staged under `site/public/javadoc/`, `check:built-links` passes and `check_docs.py links` passes.
- The pages use the docsite's colors and fonts, in light and dark.

## Context & Constraints

- Read first: `spec.md` FR-011, FR-012, User Story 4; `plan.md` IC-05/IC-06 and the Complexity Tracking row on aggregation; `research.md` §5 (docsite stack, tokens, link checker behavior, the `links` check's root-absolute rule, Gradle aggregation options, JDK 25 stylesheet properties).
- Gradle 9 has no core Javadoc-aggregation plugin. Use the configuration-cache-safe variant pattern: each hand-written module publishes its sources variant (`java { withSourcesJar() }` lives in `charter.hand-written`, owned by WP02: if it is missing, add it there with a one-line out-of-map rationale), and `charter-reports` resolves `Category=DOCUMENTATION`, `DocsType=SOURCES` plus the modules' compile classpath. Select modules by `plugins.hasPlugin("charter.hand-written")` or declare the dependency set explicitly in `charter-reports` (no cross-project mutation).
- JaCoCo aggregation: Gradle's `jacoco-report-aggregation` plugin in `charter-reports`.
- The docsite sidebar is generated from `docs/_meta/sections.yaml`; do **not** pass an explicit sidebar (it would replace the generated one). A page in `docs/api/` lands in the API section.
- Terms guard: no "feature" in docs prose; cspell may need "Javadoc", "doclint".

## Branch Strategy

- **Strategy**: set by finalize-tasks
- **Planning base branch**: `ccr-9654c444-x7y94q`
- **Merge target branch**: `ccr-9654c444-x7y94q`

```bash
spec-kitty agent action implement WP03 --agent claude --mission charter-contract-and-build-retro-01M57QHJ
```

## Subtasks & Detailed Guidance

### Subtask T017 – Reports project

- **Steps**:
  1. `settings.gradle.kts` (owned by WP01, which adds the `contracts` project; this one-line include and the `verifyAll` wiring are an out-of-map edit: record the rationale with `add-history`): include `service/charter-reports`; root `build.gradle.kts`/`settings.gradle.kts` `verifyAll` depends on the aggregate Javadoc and the aggregate coverage report.
  2. `service/charter-reports/build.gradle.kts`: apply `charter.java-base` only (it has no sources), the `jacoco-report-aggregation` plugin, and an aggregate `Javadoc` task: unnamed-module Javadoc over the five packages, `-group "<module>" <package>` per module so the overview lists each module, `-Xdoclint:all`, `-Werror`, `-notimestamp`, `-windowtitle`/`-doctitle` "Charter service API", `-link` to the JDK 25 API docs if reachable (otherwise omit; no network at build time is acceptable), output `build/docs/javadoc`.
  3. A `stageJavadocForSite` `Sync` task copying the output to `site/public/javadoc` (used locally and by CI).
- **Check**: run twice; the second run reuses the configuration cache.

### Subtask T018 – Stylesheet and back link

- **Steps**: `service/charter-reports/src/javadoc/charter-docsite.css`, passed with `addFileOption("-add-stylesheet", file)` (Gradle prefixes one `-`, giving `--add-stylesheet`). Remap the JDK 25 stylesheet's custom properties (`--body-font-family`, `--code-font-family`, `--body-text-color`, `--body-background-color`, `--section-background-color`, `--navbar-background-color`, `--navbar-text-color`, `--subnav-*`, `--selected-*`, `--title-color`, `--link-color`, `--link-color-active`, `--border-color`, `--search-tag-highlight-color`) to the doc-toolkit tokens in research §5 (light in `:root`; dark in `@media (prefers-color-scheme: dark)`, since Starlight's `data-theme` does not reach these pages). Fonts: the docsite's family stacks (no font files ship). Add `-bottom` (or `-top`) with a **relative** link back to the API page (`../api/javadoc/`), because the built-link checker refuses anything that resolves outside the site base.

### Subtask T019 – Docsite page; staging and ignore

- **Steps**: `docs/api/javadoc.md` with the full frontmatter the docs checks require (`title`, `description` 50–180 characters, `doc_status`, `updated`, `type: API`, `kind: Reference`, `audience` resolving under `docs/context/audience/`, for example `contract-consumer` and `charter-service-contributor`), a short explanation of what the reference covers (the hand-written modules, not the generated DTOs) and a link to `/javadoc/index.html`. Link it from `docs/api/README.md` (the orphans check). `site/.gitignore`: ignore `public/javadoc/`.

### Subtask T020 – `check_docs.py` allow-prefix [P]

- **Steps**: in `scripts/docs/check_docs.py` `links`, accept a root-absolute link under an allow-listed prefix that the site build generates (`/javadoc/`), read from `docs/_meta/doc-rules.yaml` (one new rule, documented), not hard-coded. Add a unit test under `scripts/docs/tests/` with a positive control (an unlisted root-absolute link to a missing file still fails). Run `python -m pytest -p no:cacheprovider scripts/docs/tests -q`.

### Subtask T021 – Local proof

- **Steps**: `./gradlew :charter-reports:stageJavadocForSite`; then in `site/`: `npm ci` (needs `NODE_AUTH_TOKEN` for `@spec-kitty/doc-toolkit` from GitHub Packages; if this environment cannot authenticate, record that and rely on the CI proof in WP04), `npm run validate`, `npm run build`, `npm run check:built-links`. Run all eight `check_docs.py` checks, markdownlint and cspell. Record results with `add-history`.

## Test Strategy

The aggregate Javadoc runs with doclint as errors (a planted malformed comment must fail
it); the `check_docs.py` change has its unit test; the built-link check covers the
published pages.

## Risks & Mitigations

- Cross-project configuration breaking the configuration cache: resolve variants, never reach into other projects' tasks.
- Javadoc's search index and frames generate many relative links: the built-link checker must pass on them; if a JDK-generated link pattern trips it, report the exact pattern rather than excluding the directory.

## Review Guidance

- The generated `charter-api` is not in the Javadoc.
- The page appears in the API section without an explicit sidebar.
- Light and dark styling checked in a browser (Playwright screenshot if available).

## Progress & Status

`spec-kitty agent tasks add-history WP03 --note "..."`, `spec-kitty agent tasks mark-status T017 --status done`, `spec-kitty agent tasks status`.
