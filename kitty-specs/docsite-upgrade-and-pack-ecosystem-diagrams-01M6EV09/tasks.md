# Tasks: Docsite on DocKitty 0.6.0 and pack-ecosystem diagrams

**Mission**: `docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09`
**Planning / merge branch**: `feat/docsite-upgrade-and-pack-ecosystem-diagrams`
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [quickstart.md](quickstart.md)

Two independent tracks:
- **Docsite track:** WP01 → WP02.
- **Diagram track:** WP03 → WP04.
- **Page text:** WP05 joins both tracks.

The site build needs a GitHub Packages token that the workstation lacks, so every site build is proven by a dispatched `Docs site` run on a probe branch (research.md R-01, P8). That probe run is the WP's verification evidence.

## Subtask Index

| ID | Description | WP | Parallel |
| --- | --- | --- | --- |
| T001 | Pin doc-toolkit 0.6.0 and the exact new peers in `site/package.json` | WP01 | |
| T002 | Regenerate `site/package-lock.json` through a dispatched probe run, then run the mechanical lock-diff review and the OSV lookup | WP01 | |
| T003 | Build job in the digest-pinned Playwright container, with the digest-pinned PlantUML render service and pinned `build` mode | WP01 | |
| T004 | `npm ci --ignore-scripts`, the build-mode guard and the render cache | WP01 | |
| T005 | URL inventory regression check: the published 0.5.0 sitemap against the built 0.6.0 sitemap | WP01 | |
| T006 | Bundle the contract on a PR (`javadoc` job) and download `contract-bundle` in the build job | WP02 | |
| T007 | `openapi` option in `astro.config.mjs`: label `main`, unreleased | WP02 | |
| T008 | Stage script (`predev`/`prebuild`): schemas, the bundle, and the one-page-per-schema assertion; plus `.gitignore` | WP02 | |
| T009 | 23 `kind: Schematic` pages and the hub under `docs/architecture/pack-schemas/` | WP02 | |
| T010 | Links: `docs/api/service-contract.md` and `docs/api/README.md` → `/reference/api/main/` | WP02 | [P] |
| T011 | Built-page diagram guard step, and the probe proof of the whole docsite track | WP02 | |
| T012 | Render toolchain: a pinned container recipe that mirrors the CI PlantUML job | WP03 | |
| T013 | «pending» stereotype, field suffix and legend in `_style.iuml` | WP03 | |
| T014 | Redraw `pack-write-model` | WP03 | [P] |
| T015 | Redraw `descriptor-kinds-overview` | WP03 | [P] |
| T016 | Redraw `descriptor-governance` | WP03 | [P] |
| T017 | Redraw `descriptor-doctrine-kinds` | WP04 | [P] |
| T018 | Redraw `descriptor-mission-skill-glossary` | WP04 | [P] |
| T019 | New `descriptor-mission-type-lifecycle` | WP04 | |
| T020 | Correct the #150 note in `class-diagram` and `c4-components` | WP04 | [P] |
| T021 | `descriptor-schemas.md`: source lines, notes, embeds, new diagram, regeneration section, pack-schemas link | WP05 | |
| T022 | `descriptors.md` status note and pointers | WP05 | [P] |
| T023 | `architectural-patterns-applied.md` #150 wording | WP05 | [P] |
| T024 | Changelog fragment, docs gates and freshness | WP05 | |

## Work packages

### WP01 — DocKitty 0.6.0 with build-time rendering (P1)

- **Prompt**: [tasks/WP01-dockitty-0-6-0-build-time-rendering.md](tasks/WP01-dockitty-0-6-0-build-time-rendering.md)
- **Goal**: the docsite builds on 0.6.0 in pinned build mode, every dependency is verified, and no URL is lost.
- **Independent test**: a dispatched `Docs site` run on the probe branch is green, and the URL inventory diff is empty.
- **Subtasks**:
  - T001 Pin doc-toolkit 0.6.0 and exact peers (WP01)
  - T002 Regenerate the lockfile via the probe, with the lock-diff review and OSV (WP01)
  - T003 Playwright container, render service, build mode (WP01)
  - T004 `--ignore-scripts`, guard, render cache (WP01)
  - T005 URL inventory regression check (WP01)
- **Dependencies**: none.
- **Risks**:
  - Any one of the 25 Mermaid blocks can fail the first build-mode render.
  - Running in a container changes how the render service is reached.
  - `sharp` without install scripts.

### WP02 — Charter API reference and pack schema pages (P2)

- **Prompt**: [tasks/WP02-api-reference-and-schema-pages.md](tasks/WP02-api-reference-and-schema-pages.md)
- **Goal**: the charter contract is browsable at `/reference/api/main/`, with its raw file and checksum, and every pack schema has a page with a diagram.
- **Independent test**: the probe run is green, the reference pages and 23 schema pages appear in `dist/`, each schema page has a diagram, and the raw contract's `.sha256` verifies.
- **Subtasks**:
  - T006 Contract bundle on a PR, downloaded by the build (WP02)
  - T007 `openapi` option (WP02)
  - T008 Stage script and `.gitignore` (WP02)
  - T009 23 schema pages and the hub (WP02)
  - T010 API links (WP02)
  - T011 Diagram guard step and the probe proof (WP02)
- **Dependencies**: WP01.
- **Risks**:
  - Out-of-map edits to `site/package.json` (scripts) and `docs-site.yml` (bundle steps), which WP01 owns; they are sequential in one lane and recorded.
  - Frontmatter and orphans gates on 24 new pages.

### WP03 — Diagram toolchain and the pack-composition diagrams (P1)

- **Prompt**: [tasks/WP03-pending-style-and-pack-model-diagrams.md](tasks/WP03-pending-style-and-pack-model-diagrams.md)
- **Goal**: the pending convention exists, and `pack-write-model`, `descriptor-kinds-overview` and `descriptor-governance` draw the decided design, with current SVGs.
- **Independent test**: `check_docs.py plantuml` passes in the CI-mirroring container, and every element in research.md R-06's pending list for these three diagrams carries the pending mark.
- **Subtasks**:
  - T012 Render toolchain recipe (WP03)
  - T013 «pending» convention (WP03)
  - T014 `pack-write-model` (WP03)
  - T015 `descriptor-kinds-overview` (WP03)
  - T016 `descriptor-governance` (WP03)
- **Dependencies**: none.
- **Risks**: drawing an open question as settled; the Graphviz and Java versions must match CI so the semantic SVG comparison holds.

### WP04 — Doctrine, glossary and mission-type diagrams (P1)

- **Prompt**: [tasks/WP04-doctrine-glossary-and-mission-type-diagrams.md](tasks/WP04-doctrine-glossary-and-mission-type-diagrams.md)
- **Goal**: `descriptor-doctrine-kinds` and `descriptor-mission-skill-glossary` are redrawn, the new `descriptor-mission-type-lifecycle` exists, and the #150 note is corrected.
- **Independent test**: `check_docs.py plantuml` passes, the new diagram's `@startuml` name equals its stem, and R-06's pending items are marked.
- **Subtasks**:
  - T017 `descriptor-doctrine-kinds` (WP04)
  - T018 `descriptor-mission-skill-glossary` (WP04)
  - T019 New `descriptor-mission-type-lifecycle` (WP04)
  - T020 #150 note in `class-diagram` and `c4-components` (WP04)
- **Dependencies**: WP03 (the pending convention and the render recipe).
- **Risks**:
  - The mission-type diagram has the most open questions (ADR-0050 OQ1, OQ2, OQ4, OQ5, OQ7, OQ8, OQ9, OQ11).
  - `c4-components` includes C4-PlantUML over the network.

### WP05 — Page text around the diagrams, and the changelog (P2)

- **Prompt**: [tasks/WP05-page-text-and-changelog.md](tasks/WP05-page-text-and-changelog.md)
- **Goal**: no page claims today's sources or data for a diagram that now draws the decided design; the new diagram and the schema pages are linked; a changelog fragment records the change.
- **Independent test**: `check_docs.py all`, markdownlint, cspell and the terminology guard are clean, and each R-06 "Page text" location is addressed.
- **Subtasks**:
  - T021 `descriptor-schemas.md` (WP05)
  - T022 `descriptors.md` (WP05)
  - T023 `architectural-patterns-applied.md` (WP05)
  - T024 Changelog fragment and gates (WP05)
- **Dependencies**: WP02, WP04.
- **Risks**: editing long pages brings their whole lint violation set into scope.

## Parallelism and MVP

- **Parallel:** WP01 and WP03 start together; WP02 follows WP01, and WP04 follows WP03. WP05 runs last.
- **MVP:** WP01 (a green 0.6.0 build) and WP03 (the decided pack model drawn).
