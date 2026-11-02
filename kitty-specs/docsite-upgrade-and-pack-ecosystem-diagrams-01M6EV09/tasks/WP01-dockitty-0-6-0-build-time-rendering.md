---
work_package_id: WP01
title: DocKitty 0.6.0 with build-time rendering
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-006
- NFR-001
- NFR-002
- NFR-004
- C-004
- SC-001
- SC-005
planning_base_branch: feat/docsite-upgrade-and-pack-ecosystem-diagrams
merge_target_branch: feat/docsite-upgrade-and-pack-ecosystem-diagrams
branch_strategy: Planning artifacts for this mission were generated on feat/docsite-upgrade-and-pack-ecosystem-diagrams. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/docsite-upgrade-and-pack-ecosystem-diagrams unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09
base_commit: c9c5a32465d3405541b33b0fe2bebb8b3d8ad9aa
created_at: '2026-11-02T19:58:54.990598+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
history: []
agent_profile: node-norris
authoritative_surface: site/
create_intent: []
execution_mode: code_change
model: sonnet
owned_files:
- site/package.json
- site/package-lock.json
- .github/workflows/docs-site.yml
role: implementer
tags: []
tracker_refs: []
---

# WP01: DocKitty 0.6.0 with build-time rendering

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt. From the repository root, the CLI is `uv run --project ../spec-kitty spec-kitty …`.

- **Profile**: `node-norris`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Move the docsite (`site/`) from `@spec-kitty/doc-toolkit` 0.5.0 to 0.6.0, and switch it to build-time diagram rendering. Every dependency must be verified: npm packages are pinned exactly with registry integrity, and the Docker images are pinned by digest. No URL that the 0.5.0 site published may be lost. This WP adds no content features: the API reference and schema pages are WP02.

Read first: `spec.md` (FR-001, FR-002, FR-006, NFR-001 to NFR-004, C-004), `plan.md` IC-01, and `research.md` R-01, R-04, R-05 and the post-plan dispositions P2 to P5, P8 and P11. DocKitty 0.6.0's source is at `../doc-kitty` (tag `v0.6.0`; read with `git -C ../doc-kitty show v0.6.0:<path>`). Its reference CI is `.github/workflows/deploy.yml:68-125` and `.github/actions/build-example/action.yml` at that tag.

## Branch strategy

- Planning branch and final merge target: `feat/docsite-upgrade-and-pack-ecosystem-diagrams`.
- Your execution worktree is allocated per computed lane from `lanes.json`. Run `spec-kitty agent action implement WP01 --agent claude --mission docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09`.
- Commits are authored `Stijn Dejongh <stijn.dejongh@sddevelopment.be>`. Export `GIT_AUTHOR_NAME`, `GIT_AUTHOR_EMAIL`, `GIT_COMMITTER_NAME` and `GIT_COMMITTER_EMAIL`, because the workstation has no global git identity. Use Conventional Commit subjects such as `build(docsite): …` or `ci(docs-site): …`.

## Test economy (DIRECTIVE_041)

Add no test files. The verification is the existing docs-site checks, a green dispatched CI run, and the URL inventory diff. Report evidence (commands, run URLs, outputs) in your hand-off, not as committed tests.

## Subtasks

### T001 — Pin 0.6.0 and the exact new peers

- In `site/package.json`:
  - `"@spec-kitty/doc-toolkit": "0.6.0"`;
  - add `"starlight-openapi": "0.27.0"` and `"@astrojs/markdown-satteri": "0.4.4"` (exact, no caret);
  - add `"playwright": "1.62.1"` to `devDependencies` (exact). It must equal the Playwright container image version used in T003.
- Do not change the other dependency ranges unless 0.6.0's peer ranges force it:
  - astro `>=7.2.10 <8`
  - Starlight `>=0.42.0 <0.43.0`
  - markdown-remark `>=7.3.0 <8`
  - Node `>=22.22.0` (unchanged)
- The `openapi` and schematic configuration is WP02's. Do not add it here, so the WP01 build proves the plain upgrade.

### T002 — Lockfile through a dispatched probe run, then the reviews

You cannot regenerate the lockfile locally: npm gets a 401 from npm.pkg.github.com, and the gh token has no `read:packages`. Use the proven probe pattern (research.md R-01 and P8; precedent: commit 4205bb4e on `origin/probe/docsite-doc-toolkit-0.5.0`):

1. Create the branch `probe/docsite-doc-toolkit-0.6.0` from your WP01 commit. Push it to `origin`; it is a throwaway branch on the upstream repo and is never merged.
2. On that branch only, add a temporary probe commit. It changes the docs-site build job to:
   - run `npm install --package-lock-only --ignore-scripts` before `npm ci`;
   - upload `site/package-lock.json` as an artifact (`actions/upload-artifact`, pinned by SHA as the workflow already does), and print `git diff --stat site/package-lock.json`.
3. Dispatch it with `gh workflow run docs-site.yml --ref probe/docsite-doc-toolkit-0.6.0`. Watch it with `gh run watch` or by polling `gh run list --branch …`, then download the artifact with `gh run download`.
4. Commit the downloaded lockfile on your WP branch, never the probe commit.
5. Run the mechanical lock-diff review (R-05). Compare with `git diff` of `site/package-lock.json` against the base:
   - list every added `packages` key with its version;
   - assert every `resolved` URL host is `registry.npmjs.org` or `npm.pkg.github.com/download/@spec-kitty`;
   - assert there is no new `"hasInstallScript": true`. Today only `sharp` and `fsevents` have one.
   - A tiny `python3 -c`/`jq` one-liner is fine. Put its output in your hand-off.
6. Run the OSV lookup: query `https://api.osv.dev/v1/query` (POST `{"package":{"name":…,"ecosystem":"npm"},"version":…}`) for each added `name@version`, and record the vulnerability ids, or "none".
7. Record the freshness acknowledgment in your hand-off: doc-toolkit 0.6.0 was published 2026-11-02 and is our org's own package, upgraded at the operator's explicit request.

The probe branch is deleted after WP02's probe proof (T011). Leave it in place for WP02, and say so in the hand-off.

### T003 — Build job in the digest-pinned Playwright container, with the render service

Change the `build` job of `.github/workflows/docs-site.yml`:

- **Container.** `container: mcr.microsoft.com/playwright:v1.62.1-noble@sha256:<digest>`.
  - Resolve the digest with `docker buildx imagetools inspect mcr.microsoft.com/playwright:v1.62.1-noble`, or `docker pull` followed by `docker inspect --format '{{index .RepoDigests 0}}'`. Use the multi-arch index digest, and record the command and output in a comment beside the line.
  - The image carries Chromium and its OS libraries (P3), so there is no `playwright install` step and no browser cache.
- **Render service.** Add a `services.plantuml` block:
  - image `plantuml/plantuml-server:jetty-v1.2026.9@sha256:065c66adc6728ba6dd353a29d4ada73f8358b68549a0f710c4bf46b43c103618`;
  - `env: { PLANTUML_SECURITY_PROFILE: SANDBOX }`;
  - in a container job the service is reached by name, `http://plantuml:8080/svg/`, with no port mapping needed. Check this against DocKitty's `deploy.yml`, which is not a container job, and adapt.
- **Readiness.** Add a readiness step that loops a curl until it gets a 200 from `http://plantuml:8080/svg/` with a timeout, and fails the job if it never comes. `curl` is in the Playwright image; verify that.
- **Other steps.** Confirm the steps still work in the container:
  - `actions/setup-node` (the image has Node; keep setup-node for the GitHub Packages `registry-url` and `.npmrc`, or write the `.npmrc` scope line yourself);
  - `actions/setup-python` (may not work in a container; Python 3 ships in the noble image, so prefer it and drop setup-python if needed);
  - `actions/download-artifact` for the Javadoc.
  - Keep the existing `if:` and `needs` logic exactly. The deploy job is unchanged (C-004).
- **Build env.**
  - `DK_DIAGRAM_BUILD_RENDER: build` (replacing `client`)
  - `DK_PLANTUML_SERVER_URL: http://plantuml:8080/svg/`
  - `DK_BEOE_CACHE_DIR: ${{ github.workspace }}/.dk-diagram-cache` with an `actions/cache` step (key on `hashFiles('docs/**/*.md')` plus the toolkit version; the restore key falls back to the toolkit version).
  - Update the comments that say the site renders in the browser and the "doc-kitty 0.5.0 (Astro 7)" Node comment.
- **Optional.** If it costs no more than a few lines, add DocKitty's `dockitty-assert-plantuml-no-remote-include` check from its `deploy.yml`.

### T004 — `npm ci --ignore-scripts`, the build-mode guard, verification

- Change `npm ci` to `npm ci --ignore-scripts` (R-05). `sharp` 0.34 resolves prebuilt `@img/*` binaries; the first CI run confirms that. If the build fails without scripts, do not quietly revert. Report it with the error, and propose `npm rebuild sharp` (a scoped script run) as the fallback.
- Add a guard step before the build: `test "$DK_DIAGRAM_BUILD_RENDER" = build`, using the same env as the build step (P5). This stops a later edit from silently falling back to client mode.
- Dispatch `Docs site` on the probe branch, rebased onto your WP commits. The probe commit stays on top and has served its purpose; you can drop it now that the lockfile is committed, then re-push the probe branch with your WP01 tree (force-push with lease is fine on that throwaway branch).
- All checks must be green: validate, build, built-links, okf. Every one of the 25 Mermaid blocks (21 files under `docs/`) now renders at build time. If one fails, fix the Mermaid source only when the fix is mechanical and keeps its meaning. Otherwise stop and report the failing block.
- Record the build job's duration next to the last 0.5.0 run on `main` (NFR-001: at most 10 minutes longer).

### T005 — URL inventory regression check (FR-002, SC-005)

- Download the published sitemap: `https://spec-kitty.github.io/spec-kitty-charter/sitemap-index.xml` and its children.
- Get the built sitemap from the probe run. Add a temporary probe-only artifact upload of `site/dist/sitemap-*.xml`, or of a `find dist -name index.html` listing.
- Diff the URL sets. Every URL published by the 0.5.0 site must be present in the 0.6.0 build. New URLs are fine.
- Put the diff and the counts in your hand-off. Any missing URL is a defect: fix it (a redirect, or a config change) or report it with its cause.

## Definition of Done

- `site/package.json` pins the four versions exactly, and the lockfile came from the registry via the probe artifact.
- The lock-diff review output shows only the two allowed hosts and no new install scripts. OSV results and the freshness note are recorded.
- The build job runs in the digest-pinned Playwright container, with the digest-pinned render service, pinned `build` mode, the guard, `--ignore-scripts` and the render cache. Deploy is unchanged.
- A dispatched `Docs site` run on the probe branch, carrying exactly the WP01 tree, is green; its URL is in the hand-off.
- The URL inventory diff shows 0 missing URLs.
- No file outside `owned_files` changed.

## Reviewer guidance

- Check that each pin is exact, by version or digest, against the registry or Docker Hub.
- Re-run the host and install-script assertions on the committed lockfile yourself.
- Open the probe run, and confirm it ran this tree and the build mode was `build` (a rendered Mermaid SVG in the HTML, not a client script).
- Confirm the deploy job's `if:` is untouched.

## Post-tasks squad amendments (binding; they override the text above where they conflict)

- **Container job details** (implementer lens):
  - Add `options: --ipc=host --init` to the container.
  - Make the first step a probe: `python3 --version; pip --version; git --version`. If `actions/setup-python` with `cache: pip` does not work in the image, use the image's python3 with a venv, or `apt-get install python3-pip`.
  - `actions/setup-node` with `registry-url` works in a container; keep it.
- **Render cache path:** use `$GITHUB_WORKSPACE` (shell or step env), not `${{ github.workspace }}`. In a container job the expression resolves to the host path, and the cache would silently miss.
- **Probe tree identity** (fakeable-DoD lens):
  - For every probe run you cite, record the probe head SHA and `git diff <WP01 head> <probe head> --stat`.
  - The diff may contain only probe-only upload or diagnostic steps in `docs-site.yml`; name each one.
  - "Carrying exactly the WP01 tree" means exactly that and nothing else.
- **T005 URL inventory, revised.** The sitemap leaves out drafts (43 pages, published noindex), redirect stubs and `/javadoc/`. Instead:
  - download the `github-pages` artifact of main's last successful `Docs site` run (`gh run download <run> -n github-pages`, a tar);
  - diff the set of `find . -type f -name '*.html'` paths against the probe's `site/dist` (upload it as a probe-only artifact);
  - 0 paths may be missing. Report the counts and any additions.
- **NFR-001 is a DoD gate.** The probe build job's duration is at most the 0.5.0 build job's duration (from main's last `Docs site` run) plus 10 minutes. Record both numbers.
- **`--ignore-scripts`.** If `sharp` or `esbuild` lack binaries, run `npm rebuild <pkg>` for that package only. Record it; never re-enable all scripts.
