---
work_package_id: WP02
title: Charter API reference and pack schema pages
dependencies:
- WP01
requirement_refs:
- FR-003
- FR-004
- FR-005
- FR-011
- NFR-003
- SC-002
- SC-003
planning_base_branch: feat/docsite-upgrade-and-pack-ecosystem-diagrams
merge_target_branch: feat/docsite-upgrade-and-pack-ecosystem-diagrams
branch_strategy: Planning artifacts for this mission were generated on feat/docsite-upgrade-and-pack-ecosystem-diagrams. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/docsite-upgrade-and-pack-ecosystem-diagrams unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09
base_commit: d943ebdd58440ef0cb363c862874dde83c474c78
created_at: '2026-11-02T20:53:20.702407+00:00'
subtasks:
- T006
- T007
- T008
- T009
- T010
- T011
history: []
agent_profile: node-norris
authoritative_surface: site/
create_intent:
- site/scripts/stage-docs-inputs.mjs
- docs/architecture/pack-schemas/README.md
execution_mode: code_change
model: sonnet
owned_files:
- site/astro.config.mjs
- site/scripts/**
- site/.gitignore
- docs/architecture/pack-schemas/**
- docs/api/service-contract.md
- docs/api/README.md
- docs/architecture/README.md
- docs/_meta/spelling-words.txt
role: implementer
tags: []
tracker_refs: []
---

# WP02: Charter API reference and pack schema pages

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt. From the repository root, the CLI is `uv run --project ../spec-kitty spec-kitty …`.

- **Profile**: `node-norris`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Building on WP01's 0.6.0 build-mode site:
- Publish the charter contract as API reference pages at `/reference/api/main/`, with its raw file and `.sha256`.
- Publish one `kind: Schematic` page per pack schema (23 today), each with a rendered diagram.
- Make both reachable.

Read first:
- `spec.md`: US2, US3, FR-003 to FR-005, FR-011, NFR-003.
- `plan.md`: IC-03, IC-04.
- `research.md`: R-02, R-03, R-04, and P1, P5, P6, P7, P9, P10.
- DocKitty 0.6.0 guides at `../doc-kitty` tag `v0.6.0`: `docs/guides/openapi-reference.md`, the Schematic guide, and `src/lib/openapi/options.mjs`, `src/lib/schematic/core.mjs`, `src/lib/integrations/schematic.ts`.

## Branch strategy

- Planning branch and final merge target: `feat/docsite-upgrade-and-pack-ecosystem-diagrams`.
- You share WP01's lane and build on its commits.
- Run `spec-kitty agent action implement WP02 --agent claude --mission docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09`.
- Author commits as `Stijn Dejongh <stijn.dejongh@sddevelopment.be>` through the `GIT_AUTHOR_*` and `GIT_COMMITTER_*` env vars.

## Out-of-map edits (recorded, sanctioned by the plan)

WP01 owns `site/package.json` and `.github/workflows/docs-site.yml`. This WP needs two small edits there, in the same lane after WP01:
- `predev` and `prebuild` scripts in `package.json`;
- the contract-bundle steps in `docs-site.yml`.

Record each edit with a one-line rationale in the commit body. Do not touch anything else in those files.

## Test economy (DIRECTIVE_041)

Add no test files and no new Python checks; P5 dropped the planned `check_schematic_diagrams.py`. Fail-closed comes from the toolkit in pinned build mode, plus one workflow `grep` guard (T011). Evidence goes in the hand-off.

## Subtasks

### T006 — The contract bundle on PR and main

- `:contracts:bundleContract` writes `build/contracts/contract/charter-openapi.yaml` and `charter-openapi.yaml.sha256` (`contracts/build.gradle.kts:39`, `:719-731`).
- `service.yml:118-127` already uploads them as the `contract-bundle` artifact on main. Read it to copy the exact paths and settings.
- PR path, in the `javadoc` job of `docs-site.yml`:
  - change the Gradle step to `./gradlew :charter-reports:aggregateJavadoc :contracts:bundleContract`;
  - add an `actions/upload-artifact` step named `contract-bundle` with `service.yml`'s paths and `if-no-files-found: error`.
- `build` job: add an `actions/download-artifact` step (same pinned SHA as the Javadoc download) for `contract-bundle` into `build/contracts/contract/`. The build's `needs` and `if:` stay unchanged.
- Check that the service workflow, called on main, really runs `bundleContract` on that path. If it uploads only on some condition, say so and handle it.

### T007 — The `openapi` option

In `site/astro.config.mjs` add, following the guide:

```js
openapi: {
  renderer: starlightOpenAPI,   // import starlightOpenAPI from 'starlight-openapi'
  section: 'reference/api',
  specs: [{ path: 'openapi/charter-openapi.yaml', label: 'main', released: false }],
}
```

- Put it where `defineDocKittyIntegrations` takes it. Check the exact option name and shape at the tag.
- `section` must not start with `api/`. Labels must match `a-z0-9._-`.
- Published at `<base>/reference/api/main/`, with `openapi.yaml` and `openapi.yaml.sha256` beside it.
- Any error starts `[dk-openapi]`.

### T008 — Stage script and `.gitignore`

Create `site/scripts/stage-docs-inputs.mjs`: Node 22, plain ESM, no dependencies, about 40 lines. It:
1. Empties and recreates `site/schemas/`, then copies every `../contracts/packs/schemas/*.json` into it.
2. Copies `../build/contracts/contract/charter-openapi.yaml` into `site/openapi/charter-openapi.yaml`. If the bundle is missing, it exits non-zero with: `contract bundle missing: run ./gradlew :contracts:bundleContract (CI: the contract-bundle artifact)`.
3. Reads each `docs/architecture/pack-schemas/*.md` frontmatter `schematic_path`. It exits non-zero listing every schema in `site/schemas/` that no page names, and every page whose `schematic_path` names a missing file (P10).

Then:
- Wire it in `site/package.json` as `"predev"` and `"prebuild"` (`node scripts/stage-docs-inputs.mjs`); this is the recorded out-of-map edit. Not `prevalidate` (P6).
- Add `schemas/` and `openapi/` to `site/.gitignore`.

### T009 — 23 schema pages and the hub

For each `contracts/packs/schemas/<stem>.json`, create `docs/architecture/pack-schemas/<page-stem>.md` with:
- `kind: Schematic`
- `schematic_path: schemas/<stem>.json` (relative to `site/`, the toolkit's project root)
- this repo's required frontmatter. Copy the field set from a neighbouring architecture page, and check `docs/_meta/doc-rules.yaml` and `scripts/docs/check_docs.py` (frontmatter, related, orphans). Fields include `title`, a `description` of 50 to 180 characters if a gate enforces it, `doc_status`, `updated`, `type`, `kind`, and `audience` with `pack-author` guidance.
- a body of one or two sentences in your own words, naming when a pack author reads this schema.
- no `## Diagram`, `## Properties` or `## Source` headings: the toolkit generates them.

Choose page stems that cannot collide with existing routes; the toolkit refuses to overwrite and publishes `<route>.json` next to each page.

Create the hub, `docs/architecture/pack-schemas/README.md`. It has a short intro, one row or bullet per schema page (title, link, one-line purpose taken from the schema's `description`), and a link to `../descriptor-schemas/`. That link back is added by WP05, so do not edit `descriptor-schemas.md`.

Writing 23 near-identical pages by hand is error-prone. Generate them once with a throwaway script in `/tmp`, then commit only the pages. Run `python3 -m scripts.docs.check_docs all` and fix every frontmatter, related or orphans finding.

### T010 — Links to the API reference (FR-011)

- `docs/api/service-contract.md`: add a sentence near the top linking to the browsable reference (`/reference/api/main/`), and the raw contract with its checksum (`/reference/api/main/openapi.yaml`, `.sha256`).
- `docs/api/README.md`: add an entry for the reference pages.
- The source link checker skips site-absolute links. The built-link check covers them on every PR build (P9), so write them as site-absolute links in the form the repo already uses for internal links.

### T011 — Diagram guard step, and probe proof of the whole docsite track

- In the `build` job, after `npm run build`, add a guard step (P5):

  ```bash
  missing=$(grep -L '<svg' dist/architecture/pack-schemas/*/index.html || true)
  test -z "$missing" || { echo "schema pages without a diagram: $missing"; exit 1; }
  ```

  - Adapt the `dist` path to the real output (check one built page), and the marker to the toolkit's real diagram element. `class="dk-diagram"` or the figure class from `src/lib/rehype/diagram-figure.ts` at the tag is more precise than `<svg`.
  - Exclude the hub page.
- Push your WP02 tree to `probe/docsite-doc-toolkit-0.6.0` (force with lease; it is a throwaway branch), dispatch `gh workflow run docs-site.yml --ref probe/docsite-doc-toolkit-0.6.0`, and watch until it finishes.
- The run must be green. From the run's artifacts or logs, record:
  - the count of `reference/api/main` pages;
  - 23 schema pages, each with a diagram;
  - `sha256sum -c` passing on the published `openapi.yaml.sha256` against `openapi.yaml`. Fetch both from the build artifact; add a temporary probe-only `dist` upload if you need it.
- After a green proof, delete the probe branch: `git push origin --delete probe/docsite-doc-toolkit-0.6.0`. If that is refused, report it.

## Definition of Done

- The contract bundle is produced on PR and main, downloaded by the build, staged, and published at `/reference/api/main/` as unreleased, with a verifying `.sha256`.
- There are 23 `kind: Schematic` pages and a hub, every built schema page carries a diagram, and the stage script fails on a schema with no page.
- The API reference is linked from `docs/api/service-contract.md` and `docs/api/README.md`.
- `check_docs.py all` is clean, and a dispatched probe run with this tree is green; its URL is in the hand-off.
- The out-of-map edits are limited to `package.json` scripts and `docs-site.yml` bundle and guard steps, each with a rationale.

## Reviewer guidance

- Open the probe run. Confirm it built this tree, that one API page and three schema pages show content and a diagram, and that the checksum verifies.
- Rename a schema copy locally and run the stage script (`node site/scripts/stage-docs-inputs.mjs`) to confirm it fails closed.

## Post-tasks squad amendments (binding; they override the text above where they conflict)

- **Owned files added.**
  - `docs/architecture/README.md`: the hub must be linked from the architecture section README, or `check_orphans` fails (`check_docs.py:533-536`). Add one bullet.
  - `docs/_meta/spelling-words.txt`: the new pages and schema property names will trip cspell. Add only correct words.
  - WP05 then edits `spelling-words.txt` only as a recorded out-of-map edit, after WP02.
- **T011 diagram guard, revised.** The `<svg` grep is replaced, because every page carries inline icon SVGs. The guard must:
  1. Assert the number of built schema pages (hub excluded) equals the number of `contracts/packs/schemas/*.json` (23 today). Fail when the glob matches nothing, with no `|| true`.
  2. Assert that every one of those pages contains `class="plantuml-diagram"` (DocKitty v0.6.0 `src/lib/rehype/diagram-figure.ts:208`; confirm the exact class at the tag).
  - Show in your hand-off that the guard fails on a negative control. For example, run it once against a `dist` copy with one page's figure removed; that run is evidence only and is not committed.
- **FR-004 checksum, revised.** DocKitty computes `openapi.yaml.sha256` over its own published copy, so `sha256sum -c` always passes. The real check: sha256 of the published `reference/api/main/openapi.yaml` equals the hex in the bundler's `charter-openapi.yaml.sha256` from the `contract-bundle` artifact. Add this as a step after the build in `docs-site.yml` (a one-line `test` comparison), plus the `sha256sum -c`.
- **The PR path of T006 is not proven by a dispatch.** On `workflow_dispatch` the `service` job runs, and `javadoc` is skipped.
  - The probe proves the download, stage, build and guards.
  - The `javadoc` job's `bundleContract` and upload are proven only by the mission's draft-PR run. Say so in the hand-off, and do not claim it is proven.
  - The orchestrator verifies it on the draft PR.
- **SC-002 and FR-011 reachability.** In the probe's built `dist`, confirm that the sidebar on the home page (`dist/index.html`) links the API reference section, and that `/reference/api/main/` links each operation (≤3 clicks from home). Confirm the pack-schemas hub is linked from the architecture section's sidebar or README. Record the evidence (grep lines).
- **Gates.** Run the docs checks as CI does: `python3 scripts/docs/check_docs.py all`, plus markdownlint and cspell (`npx --yes` with the versions pinned in `docs-cleanliness.yml`). `npm run validate` (including `kind: Schematic` acceptance) runs only in the probe.
- **T009 frontmatter.** Each page needs `title`, a `description` of 50 to 180 characters, `doc_status`, `updated`, `type`, `kind`, and `audience` (`- profile: pack-author` with `guidance_text`). `related:` is optional. The hub also needs `kind: Hub` and `okf_version: "0.2"`, like `docs/architecture/README.md`. `kind: Schematic` replaces `kind` on the schema pages; check the toolkit's frontmatter schema at the tag.
