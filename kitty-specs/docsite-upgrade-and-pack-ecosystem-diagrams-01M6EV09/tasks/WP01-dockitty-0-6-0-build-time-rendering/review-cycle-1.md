---
affected_files: []
cycle_number: 1
mission_slug: docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09
reproduction_command:
reviewed_at: '2026-11-02T20:35:50Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review feedback (reviewer-renata, cycle 1)

Verdict: changes requested. One blocking defect. Everything else checked out (see the verified list at the end).

## Blocking

**Issue 1: the render-mode guard can never fail, so the build-mode pin is unguarded.**
Location: `.github/workflows/docs-site.yml`, step "Guard the diagram render mode" (around lines 197-201).

The guard step sets its own `env: DK_DIAGRAM_BUILD_RENDER: build` and then runs `test "$DK_DIAGRAM_BUILD_RENDER" = build`. It tests a constant it declares itself. The Build step has a separate `DK_DIAGRAM_BUILD_RENDER: build`. If a later edit changes the Build step to `client`, or removes it, the guard still passes. That is exactly the regression P5 and T004 say the guard exists to catch ("stops a later edit from silently falling back to client mode"). As written, it is a fake-green check (QA_003_NO_FAKE_GREEN). T004's wording "using the same env as the build step" means the guard reads the value the build actually uses, not a copy of it.

Fix (pick one, and keep a single declaration of the value):
- Preferred: delete the separate guard step and make the guard the first line of the Build step's `run:`, reading the Build step's own env. For example:
  `test "$DK_DIAGRAM_BUILD_RENDER" = build || { echo "::error::DK_DIAGRAM_BUILD_RENDER must be build"; exit 1; }`
  then `export DK_BEOE_CACHE_DIR=...` and `npm run build`.
- Or: move `DK_DIAGRAM_BUILD_RENDER: build` to job-level `env:`. Remove it from both steps, and keep the guard step without its own `env:`.
  - This is weaker: a step-level override on Build would still slip past.
  - If you choose it, say why in the comment.

Then re-push the probe branch with the WP01 tree plus the probe-only `site/dist` upload, dispatch `Docs site`, and record:
- the new run URL;
- the probe head SHA;
- `git diff <WP01 head> <probe head> --stat`.

No negative-control run is required. The structure makes the guard's dependence on the build value self-evident.

## Non-blocking (fold in this cycle if cheap, otherwise leave)

- `docs-site.yml`, step "Set up Python for the index generator": `python3-pip` is not needed.
  - The venv gets pip through `python3-venv`, which pulls in `python3.12-venv` and `python3-pip-whl`, and you call `$RUNNER_TEMP/docs-venv/bin/pip`.
  - Installing only `python3-venv` shrinks the unpinned apt surface.
- `docs-site.yml`, step "Wait for the PlantUML server": add `--max-time 5` to the curl call. A stalled connection would otherwise hang one iteration far beyond the 60 s budget, because curl's default connect timeout is 300 s.
- `docs-site.yml`, step "Restore the diagram render cache": the `0.6.0` in the cache key is hard-coded. Add a one-line comment saying it must be bumped with the `@spec-kitty/doc-toolkit` pin, as you already did for the Playwright tag.

## Verified (no action needed)

- Lane commits d4ae57b2..70066aea are the deliverable. The c5cce266 merge on top only brings status files from the mission branch.
- Probe 0e973538 = 70066aea + one PROBE-ONLY `site/dist` upload step (5 lines, `docs-site.yml`).
- Run 41518814592:
  - every step is green, and build ran in the digest-pinned container with the digest-pinned PlantUML service;
  - the build env was `build`, and PlantUML answered 200;
  - the render cache saved about 101 kB under the expected key;
  - deploy was skipped.
- The Playwright index digest re-resolved to `sha256:dcc5531e…7580e` (identical). The PlantUML digest equals DocKitty v0.6.0 `deploy.yml:87`.
- Lockfile: 59 added, 1 changed (doc-toolkit 0.5.0 -> 0.6.0), 0 removed. The only hosts are `registry.npmjs.org` (742) and `npm.pkg.github.com` (1, under `/download/@spec-kitty/`). Every resolved entry has an integrity hash.
- The only new `hasInstallScript` is `playwright/node_modules/fsevents@2.3.2` (darwin-only, optional, dev). It is accepted: it never installs on linux, and CI runs `--ignore-scripts`.
- Mermaid: 25 of 25 blocks rendered to inline SVG in the probe `dist`, and no `class="mermaid"` client blocks remain.
- The node rename `graph` -> `crg` keeps the labels and edges. Only the internal id changed.
- HTML inventory: 565 on main (run 41508080228) and 565 on the probe; 0 missing, 0 added.
- NFR-001: 2m15s against 55s on 0.5.0, within +10 min.
- The deploy job and its `if:` are untouched (C-004).
