# Implementation Plan: Docsite on DocKitty 0.6.0 and pack-ecosystem diagrams

**Branch**: `feat/docsite-upgrade-and-pack-ecosystem-diagrams` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09/spec.md`

## Summary

Move the docsite (`site/`) from `@spec-kitty/doc-toolkit` 0.5.0 to 0.6.0. Switch its diagram rendering from browser to build time, against DocKitty's PlantUML render service pinned by digest and a pinned Playwright Chromium. Publish the charter contract as API reference pages from the single-file bundle that `:contracts:bundleContract` already produces. Publish one `kind: Schematic` page per pack schema from copies staged inside `site/`. Separately, redraw the five pack-model PlantUML diagrams to the decided design of ADR-0048 to ADR-0052, add one mission-type and lifecycle diagram, mark every open-question element as pending, and bring the page text around them in line.

## Technical Context

**Language/Version**: Node 22.22 (the docsite: Astro 7.4, Starlight 0.42.6, `@spec-kitty/doc-toolkit` 0.6.0); PlantUML 1.2026.6 (repository jar, pinned in `scripts/docs/plantuml_pins.json`) on Java 21; Python 3.12 (docs scripts); Gradle (the existing `:contracts:bundleContract` task); GitHub Actions YAML.
**Primary Dependencies**:
- `@spec-kitty/doc-toolkit` 0.6.0 (GitHub Packages; release run 41403232196 succeeded on 2026-11-02).
- New exact-pinned peers: `starlight-openapi@0.27.0` and `@astrojs/markdown-satteri@0.4.4` (OpenAPI renderer), and `playwright@1.62.1` (build-time Mermaid, DocKitty's own pin).
- CI service image `plantuml/plantuml-server:jetty-v1.2026.9@sha256:065c66adc6728ba6dd353a29d4ada73f8358b68549a0f710c4bf46b43c103618`, run with `PLANTUML_SECURITY_PROFILE=SANDBOX`.
**Storage**: N/A. Generated inputs (the bundled contract, the staged schema copies) are git-ignored build inputs under `site/`.
**Testing**:
- The existing docs gates: `check_docs.py all`, markdownlint, cspell, PlantUML diagrams-current, `npm run validate`, `check:built-links` and `check:okf`.
- Fail closed by the toolkit in pinned build mode, plus a build-mode guard and a built-page grep (NFR-003; research.md P5).
- Fail-closed toolkit errors: `[dk-openapi]` and `[dk-schematic]`.
- No unit tests: there is no application code (DIRECTIVE_041).
**Target Platform**: GitHub Pages (static site), built on `ubuntu-24.04` runners.
**Project Type**: single repository, docs toolchain and docs content only.
**Performance Goals**: the docs-site PR build is at most 10 minutes slower than on 0.5.0 (NFR-001). Cache the Playwright browser and the diagram render cache (`DK_BEOE_CACHE_DIR`).
**Constraints**:
- C-001: no change to contracts, pack schemas or service code.
- C-003: committed architecture SVGs are rendered with the repository jar, not the render service.
- C-004: deploy only from `main`.
- Every new dependency is pinned by exact version or digest. The charter forbids unverified dependencies: lockfile integrity hashes for npm, an image digest for Docker, and a sha256 for the jar.
**Scale/Scope**:
- 1 contract (24 split files, bundled to 1).
- 23 pack schemas, 23 pages plus a hub.
- 25 Mermaid blocks in 21 files, all now rendered at build time.
- 5 diagrams redrawn, 1 new, 2 code-structure diagrams with one note corrected.
- About 8 docs pages touched around the diagrams.

## Charter Check

- **Non-trivial work runs as a mission and lands by PR.** Pass: this mission, PR-bound.
- **Architecture changes get an adversarial squad at the plan point and before merge.** Pass: a post-spec brownfield squad ran (two lenses, findings folded into the spec); a post-plan squad and a pre-PR squad are planned.
- **No unverified dependency enters the build.** Pass by design: npm packages are pinned exactly, with lockfile integrity; the render image is pinned by digest; the PlantUML jar is checked by sha256 (`plantuml_pins.json`). DIRECTIVE_051 and `supply-chain-install-safety` are applied in research.md (R-05).
- **Do not break the OpenAPI contract.** Pass: the contract is read, never edited (C-001).
- **Design without regard to core.** Not applicable: docs only.
- **Re-check after design (post-plan squad):** no new violations. Chromium now comes from a digest-pinned container, so every dependency is verified; R-05 records freshness, the IoC feed, install hooks and the lock-diff review. No Complexity Tracking entries.

## Project Structure

### Documentation (this mission)

```
kitty-specs/docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── checklists/requirements.md
└── decisions/            # DM-* records from specify
```

### Source Code (repository root)

```
site/
├── package.json, package-lock.json     # 0.6.0 + exact peers + playwright
├── astro.config.mjs                    # openapi option, schematic integration
├── .gitignore                          # + openapi/, schemas/ (staged build inputs)
└── scripts/stage-docs-inputs.mjs (new) # predev/prebuild: stages schemas + bundle, asserts a page per schema
.github/workflows/docs-site.yml         # contract-bundle on PR, Playwright container, render service, build mode, guards
scripts/docs/
└── (check_docs.py, plantuml_pins.json — used, unchanged unless a gate needs a companion)
docs/
├── api/service-contract.md, api/README.md            # links to /reference/api/main/
├── architecture/pack-schemas/README.md + 23 pages (new)  # kind: Schematic
├── architecture/descriptor-schemas.md, descriptors.md, architectural-patterns-applied.md  # page text (FR-013)
├── architecture/diagrams/_style.iuml                 # «pending» stereotype
├── architecture/diagrams/{pack-write-model,descriptor-kinds-overview,descriptor-governance,
│   descriptor-doctrine-kinds,descriptor-mission-skill-glossary}.puml/.svg   # redrawn
├── architecture/diagrams/descriptor-mission-type-lifecycle.puml/.svg (new)
├── architecture/diagrams/{class-diagram,c4-components}.puml/.svg            # #150 note only
└── changelog/2026-11-0X-docsite-dockitty-0-6-0.md (new fragment)
```

**Structure Decision**: everything stays inside the existing `site/`, `docs/`, `scripts/docs/` and `.github/workflows/` layout. The only new directory is `docs/architecture/pack-schemas/`, which holds the schema pages next to the descriptor pages that discuss them.

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — Toolkit bump, lockfile and build-time rendering (merged with the former IC-02, P11)

- **Purpose**: pin `@spec-kitty/doc-toolkit` 0.6.0 and the exact peers, and regenerate `site/package-lock.json` against the real registry.
- **Relevant requirements**: FR-001, FR-002, FR-006, NFR-001, NFR-002, NFR-003, NFR-004, C-004.
- **Affected surfaces**:
  - `site/package.json`, `site/package-lock.json`.
  - The `build` job of `docs-site.yml`:
    - the digest-pinned Playwright container and the render service block;
    - `DK_DIAGRAM_BUILD_RENDER=build` and the render cache;
    - the build-mode guard and `npm ci --ignore-scripts`.
- **Sequencing/depends-on**: none.
- **Risks**:
  - The local npm gets a 401 from GitHub Packages, so the lockfile cannot be regenerated locally without a token that has `read:packages`.
  - Fallback: the proven probe pattern (`origin/probe/docsite-doc-toolkit-0.5.0`), where a throwaway CI run writes the lockfile and prints its diff.
  - See research.md R-01.

### IC-03 — Charter API reference pages

- **Purpose**: publish the bundled charter contract as reference pages at `/reference/api/main/` (label `main`, `released: false`), with its raw file and `.sha256`.
- **Relevant requirements**: FR-003, FR-004, FR-011, SC-002.
- **Affected surfaces**:
  - `docs-site.yml`: a `contract` job runs `./gradlew :contracts:bundleContract` on both PR and main and uploads the `charter-contract` artifact; the build job downloads it into `site/openapi/`.
  - `site/astro.config.mjs` (the `openapi` option).
  - `docs/api/service-contract.md` and `docs/api/README.md` (links).
- **Sequencing/depends-on**: IC-01.
- **Risks**:
  - Not in the OKF export (doc-kitty #257).
  - Whether `dockitty-check-links` accepts a source link to a generated route is unverified. If not, link with an absolute site URL or adjust the check's ignore list, and record the choice.

### IC-04 — Pack schema pages

- **Purpose**: one `kind: Schematic` page per pack schema, with a hub page, and schema copies staged inside `site/` before validation and build.
- **Relevant requirements**: FR-005, FR-011, NFR-003, SC-003.
- **Affected surfaces**:
  - `site/scripts/stage-docs-inputs.mjs`, wired into npm `prevalidate` and `prebuild`, and `site/.gitignore`.
  - `docs/architecture/pack-schemas/**`.
  - A one-line built-page `grep` guard in `docs-site.yml` (no new script, P5).
  - A link from `docs/architecture/descriptor-schemas.md`.
- **Sequencing/depends-on**: IC-01 (diagrams need build mode).
- **Risks**:
  - Each page needs the repo's frontmatter (`audience`, doc_status and the other fields) and a link from the hub, or the orphans check fails.
  - The schema is published next to the page route; collisions with existing routes fail the build.

### IC-05 — Pack-model diagrams redrawn, plus the mission-type diagram

- **Purpose**: redraw `pack-write-model`, `descriptor-kinds-overview`, `descriptor-governance`, `descriptor-doctrine-kinds` and `descriptor-mission-skill-glossary` to the decided design, and add `descriptor-mission-type-lifecycle`.
  - Every element that rests on an open question carries a «pending» stereotype or field suffix that cites the ADR and the open-question number.
  - Each diagram carries a legend and the ADRs it draws from.
  - Correct the #150 note in `class-diagram` and `c4-components`.
- **Relevant requirements**: FR-007, FR-008, FR-009, FR-010, FR-012, C-002, C-003, SC-004.
- **Affected surfaces**: `docs/architecture/diagrams/*.puml`, `.svg` and `_style.iuml`.
- **Sequencing/depends-on**: none (it runs in parallel with IC-01 to IC-04).
- **Risks**:
  - Drawing an open question as settled. The per-diagram element list and pending list in research.md R-06 is the review checklist.
  - Rendering locally needs Graphviz, which is not installed. Render in a pinned container with the repository jar (quickstart.md).
  - `c4-components` includes C4-PlantUML over the network.

### IC-06 — Page text around the diagrams

- **Purpose**: make the prose match the diagrams.
  - Replace the source lines and today's built-in counts with ADR citations.
  - Reword the "Decided, not built yet" notes beside each redrawn diagram.
  - Fix the regeneration instructions in `descriptor-schemas.md`: use the pinned jar, and note the site now renders at build time.
  - Embed the new diagram in the mission-type section.
  - Add a changelog fragment.
- **Relevant requirements**: FR-009, FR-013.
- **Affected surfaces**: `docs/architecture/descriptor-schemas.md`, `descriptors.md`, `architectural-patterns-applied.md`, `docs/changelog/`.
- **Sequencing/depends-on**: IC-05.
- **Risks**:
  - Editing a long page brings its whole markdownlint violation set into scope.
  - The terminology guard and `check_docs` must stay clean.
