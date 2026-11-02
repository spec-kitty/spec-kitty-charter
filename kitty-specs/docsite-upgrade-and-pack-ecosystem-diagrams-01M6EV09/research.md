# Research: Docsite on DocKitty 0.6.0 and pack-ecosystem diagrams

Sources: post-spec brownfield squad, 2026-11-02.
- researcher-robbie read DocKitty at tag `v0.6.0` (`../doc-kitty`), the site, the CI and the contracts.
- architect-alphonso read ADR-0048 to ADR-0052, all `.puml` files and the embedding pages.

## R-01 Lockfile regeneration

- **Decision (revised by P8: when no `read:packages` token is available, dispatch `gh workflow run docs-site.yml --ref <probe-branch>`, upload the lockfile as an artifact, commit it, then delete the probe branch):** regenerate `site/package-lock.json` with `npm install --package-lock-only --ignore-scripts` against GitHub Packages, with a token that has `read:packages`.
  - If no local token is available, use the proven CI probe pattern: a throwaway branch commit (as `origin/probe/docsite-doc-toolkit-0.5.0`, 4205bb4e) runs the install in CI and prints the lockfile, which is then committed verbatim.
- **Rationale:** the local npm gets a 401 from npm.pkg.github.com. The lockfile must come from the real registry so its integrity hashes are genuine; never hand-edit it.
- **Alternatives:**
  - Hand-editing the lock: rejected, because it produces unverifiable integrity.
  - Dropping `npm ci`: rejected, because it breaks reproducibility.

## R-02 Contract input for the API reference pages

- **Decision (revised by P1):**
  - The existing `contract-bundle` artifact (`build/contracts/contract/charter-openapi.yaml` and its `.sha256`) is reused. `service.yml` uploads it on main.
  - On a PR, the `javadoc` job runs `./gradlew :charter-reports:aggregateJavadoc :contracts:bundleContract` and uploads `contract-bundle` with the same paths.
  - The build job downloads it into `build/contracts/contract/`, and the stage script copies it into a git-ignored `site/openapi/`.
  - Config: `openapi: { renderer: starlightOpenAPI, section: 'reference/api', specs: [{ path: 'openapi/charter-openapi.yaml', label: 'main', released: false }] }`.
- **Rationale:**
  - DocKitty 0.6.0 refuses a `$ref` that does not start with `#` (`src/lib/openapi/options.mjs:99-152`). The contract has 24 file `$ref`s, and C-001 forbids changing it.
  - The repository already bundles with vacuum 0.30.6, pinned by SHA (`contracts/build.gradle.kts:719-731`). That bundle is what a contract release publishes.
  - Labels must match `a-z0-9._-`, so `0.1.0-SNAPSHOT` is invalid. A version-tracking label would also move the URL at each release.
- **Alternatives:**
  - Committing a bundled copy: rejected, because it is a second source of truth.
  - Bundling with a new npm tool: rejected, because it is a new dependency where one already exists.
- **Known gap:** the OpenAPI pages are not in the OKF export, the agent API or the frontmatter gate (doc-kitty #257). `check:okf` still passes.

## R-03 Schema pages

- **Decision (revised by P6 and P10):**
  - `site/scripts/stage-docs-inputs.mjs` runs as npm `predev` and `prebuild`. It:
    - empties a git-ignored `site/schemas/` and copies `../contracts/packs/schemas/*.json` into it;
    - copies the contract bundle from `../build/contracts/contract/` into `site/openapi/`;
    - fails when a schema has no page whose `schematic_path` names it.
  - It does not run before `validate`: the frontmatter and link checks do not read the staged files.
  - Each page under `docs/architecture/pack-schemas/` sets `kind: Schematic` and `schematic_path: schemas/<file>.json`.
- **Rationale:**
  - DocKitty takes the build's working directory (`site/`) as the project root (`src/lib/integrations/schematic.ts:52`). It refuses a path outside it, a symlink included (`src/lib/schematic/core.mjs:72-82`).
  - Building from the repository root would break `docsDir: '../docs'`.
- **Alternatives:**
  - Building with `--root`: an unproven spike.
  - An upstream DocKitty option: slow.
  - Both are rejected for this mission.
- **Facts:**
  - All 23 schemas have a `description` and an `$id`, and none references another file.
  - A page publishes its schema next to its route.
  - The toolkit adds the description and the `## Diagram`, `## Properties` and `## Source` sections; a heading we write with one of those names replaces the generated one.

## R-04 Build-time rendering

- **Decision:**
  - The build job gets a `services.plantuml` block: image `plantuml/plantuml-server:jetty-v1.2026.9@sha256:065c66adc6728ba6dd353a29d4ada73f8358b68549a0f710c4bf46b43c103618`, ports `8091:8080`, env `PLANTUML_SECURITY_PROFILE: SANDBOX`.
  - After a curl readiness loop it sets `DK_DIAGRAM_BUILD_RENDER=build`, `DK_PLANTUML_SERVER_URL=http://localhost:8091/svg/` and `DK_BEOE_CACHE_DIR` (with `actions/cache`).
  - Playwright `1.62.1` is an exact devDependency, matching the image version.
  - The build job runs in `container: mcr.microsoft.com/playwright:v1.62.1-noble@sha256:<digest resolved in the WP and recorded>`, so the browser and its OS libraries are digest-verified (P3). In a container job the render service is reached at `http://plantuml:8080/svg/`, not localhost.
  - Optionally add `dockitty-assert-plantuml-no-remote-include`.
  - Fail closed (P5): the toolkit throws on any render error in pinned `build` mode. A workflow guard asserts the mode is `build`, and a one-line `grep` asserts every built schema page carries a diagram figure. There is no new script.
- **Rationale:**
  - In client mode DocKitty silently leaves out `## Diagram`, which breaks NFR-003. A pinned build mode fails instead of falling back.
  - The reference is DocKitty's `.github/workflows/deploy.yml:68-125` and `.github/actions/build-example/action.yml:60-90` at `v0.6.0`.
- **Note:** two PlantUML versions coexist on purpose. The render service (1.2026.9) draws only the schema diagrams; the repository jar (1.2026.6) draws the committed architecture SVGs (C-003).

## R-05 Supply chain (DIRECTIVE_051, supply-chain-install-safety)

| Dependency | Pin | Provenance and integrity | Install-time scripts |
| --- | --- | --- | --- |
| `@spec-kitty/doc-toolkit` 0.6.0 | exact | Our org's GitHub Packages, from the release workflow run 41403232196; lockfile integrity | `npm ci` as today |
| `starlight-openapi` 0.27.0 | exact | npm registry; lockfile integrity; DocKitty's declared optional peer | none expected; checked in the lockfile diff |
| `@astrojs/markdown-satteri` 0.4.4 | exact | Already in the lock tree through astro 7.4.1 | — |
| `playwright` 1.62.1 | exact | npm registry, the version DocKitty pins; the Chromium download is pinned by Playwright's own build number | browser via an explicit `playwright install`, cached |
| PlantUML render server | image digest | Docker Hub `plantuml/plantuml-server`, the digest DocKitty pins; SANDBOX profile; no remote includes | — |

- **Transitive additions** (P2): `starlight-openapi@0.27.0` pulls in `httpsnippet ^3.0.10`, `@readme/openapi-parser ^4.1.2`, `url-template` and `github-slugger`. The lockfile pins each one exactly, with integrity.
- **Freshness:**
  - `@spec-kitty/doc-toolkit` 0.6.0 was published 2026-11-02, inside the tactic's freshness window. It is our org's own package, and the operator explicitly asked for this upgrade on 2026-11-02; record that as the acknowledgment.
  - `starlight-openapi` 0.27.0 was published 2026-10-07.
- **IoC feed:** query OSV (osv.dev, the `npm` ecosystem) for every added `name@version` in the WP, and record the result in the WP evidence.
- **Install hooks:** CI switches to `npm ci --ignore-scripts`. The lock has two `hasInstallScript` entries, `sharp` (prebuilt `@img/*` binaries) and `fsevents` (macOS only). The first CI run confirms the build works without scripts; record that.
- **Mechanical lock-diff review:**
  - list the added `packages` keys;
  - assert every `resolved` host is `registry.npmjs.org` or `npm.pkg.github.com/download/@spec-kitty`;
  - assert there is no new `hasInstallScript`.
- No floating tags or ranges for new dependencies (NFR-002).
- Adversarial evidence: the post-plan squad challenges this table (see the disposition section below).

## R-06 Diagram changes (review checklist)

Pending marker:
- `_style.iuml` gains `skinparam class<<pending>> { BackgroundColor #FFF3CD; BorderColor #B8860B }`.
- Pending fields carry the suffix `<color:#8A6D00>[pending ADR-00xx OQn]</color>`.
- Every diagram that uses either one has a legend line.

C-002 reading: a pending element is drawn as the decision text shapes it and is marked. It is never left out.

### pack-write-model (pages: descriptor-schemas, descriptors)

Changes:
- `depends_on: [{pack_id, range}]` (48 d1, d4).
- `parent_pack` is lineage only, with a ULID pattern (48 d5-6).
- File list: `*.anti-pattern.yaml` (52 d8), `*.glossary.yaml` (49 d1), `*.lifecycle.yaml` (50 d17), plus the pending gate, event-registry and step-enhancement files.
- `mission_types/` bundles (48 d16, 50 d4).
- `FragmentKind` `glossary_packs` → `glossary`, and add `lifecycles`.
- Remove "Refused: glossary" (R14, 49 d1).

Pending:
- `range` grammar (48 OQ4) and `pack_level` required (48 OQ7)
- glossary layout (49 OQ1), anti-pattern layout (52 OQ2)
- gate (50 OQ2), lifecycle layout (50 OQ4), step-enhancement file (50 OQ8)
- registry layout and node kind (51 OQ3)

### descriptor-kinds-overview

Changes:
- `GlossaryPack` → `Glossary` (context), plus a node-only `term` (49 d1, d4).
- Add `AntiPattern`, activatable (52 d8, d13).
- `MissionType` becomes an ordinary kind (50 d3, d6), and `Lifecycle` is added (50 d17).
- Add `EventRegistry` «not activatable» (51 d1, d5).
- `depends_on` self-association (48 d8).
- `rejects` → `AntiPattern` (52 d9).
- Drop today's edge counts.

Pending: `Gate` (50 OQ2); `StepEnhancement` (50 OQ7, OQ8); `EventRegistry` activation and node (51 OQ1, OQ3); the `glossary:` URN (49 OQ3).

### descriptor-governance

Changes:
- `depends_on` and the lineage-only `parent_pack` (48 d1, d5).
- `NodeKind` `glossary_pack` → `glossary` + `term` (49 d4), and add `lifecycle` (50 d17).
- `required_glossary_packs` and `selected_glossary_packs` renamed (49 d1).
- `mission_type` in `selected_*` (50 d6).
- `glossary_scope` retired (49 d11).

Pending: gate (50 OQ2); the `event_registry` node kind (51 OQ3); `pack_level` required (48 OQ7); the core `glossary` node clash (49 OQ3).

### descriptor-doctrine-kinds

Changes:
- Remove the inline anti-pattern classes and fields (52 d4, d16).
- Add `rejects: RejectsRef{type,id,reason}[]` on directive, tactic, paradigm, styleguide and procedure (52 d9).
- Add `AntiPattern` with severity `smell|severe|breaking` (52 d8, d10).

Pending: the `AntiPattern` field shape (52 OQ2) and its default severity (52 OQ4).

### descriptor-mission-skill-glossary

Changes:
- `GlossaryContext{schema_version,id,name,description,tags?,applies_to?,terms}` and `Term{id,name,definition,examples?,aliases?,avoid?,status,see_also?}`.
- Drop `confidence`, `introduced_in_mission`, `provenance` and `banned_synonyms`; `banned_synonyms` folds into `avoid` (49 d2-5).
- `MissionStepContract` loses `gates`/`GateBinding` (50 d12), and the step contract moves into the bundle (50 d5).
- `PackSkill` is unchanged.

Pending: term status values (49 OQ4); `applies_to` advisory (49 OQ2); layout (49 OQ1).

### NEW descriptor-mission-type-lifecycle (mission-type section of descriptor-schemas)

Elements:
- `MissionType`, `Step` and `Transition{to,on?,guard?,on_transition?}` (50 d8-11).
- `ActionPoint{emit}` (50 d15-16).
- `Lifecycle`, `Gate` and `StepEnhancement`.
- `end` reserved (50 d11).
- `term:` URN references (50 d26).

Pending:
- `end` (50 OQ1), `Gate` (OQ2), the `Lifecycle` shape (OQ4)
- the `work_package_lifecycle` name (OQ5)
- `StepEnhancement` (OQ7, OQ8)
- `{kind}` in read paths (OQ9), the `emit` spelling (OQ11)

Not drawn: a named-pack instance graph. Most of it is open (48 OQ1, OQ2, OQ3, OQ8), so `depends_on` is shown at the schema level only.

### Unchanged diagrams

- `descriptor-agent-profile` and `descriptor-directive-example`: unchanged.
- `class-diagram` and `c4-components`: unchanged except the note "Procedure deferred pending #150" → "Procedure stays a kind (ADR-0052 decision 1); not built yet".

### Page text (FR-013)

`descriptor-schemas.md`:
- Source lines at 42-46, 76-78, 84, 100-104 and 152-153.
- Notes at :106, :119 and :155.
- The regeneration section at 205-209: unpinned `plantuml`, and "no PlantUML step".

`descriptors.md`:
- The status note at 37-39.
- Light pointers at 85, 279, 319, 388 and 402.

No change: the `resolution-model`, `events-and-ledger` and `pack-registration-distribution` notes (no redrawn diagram on those pages).

## Post-plan squad dispositions (2026-11-02)

The squad had two lenses: architect-alphonso (CI design and supply chain) and randy-reducer (duplication). Each finding is dispositioned below.

| # | Finding | Disposition | Where |
| --- | --- | --- | --- |
| P1 | The bundle path is `build/contracts/contract/charter-openapi.yaml`, not `contracts/build/…`. `service.yml:118-127` already uploads it as `contract-bundle` on main, so a new `contract` job duplicates it. | accepted: there is no new job. On a PR, the existing `javadoc` job also runs `:contracts:bundleContract` and uploads `contract-bundle` with `service.yml`'s paths. The build downloads `contract-bundle`. | R-02 (revised) |
| P2 | R-05 does not record transitive additions, freshness, the IoC feed, the install-hook posture or a review criterion. | accepted. | R-05 (revised) |
| P3 | Chromium is pinned by version, not by integrity. `--with-deps` installs floating apt packages, and a restored cache is not re-verified. | changed: the build job runs inside `mcr.microsoft.com/playwright:v1.62.1-noble`, pinned by digest. The browser and its system libraries come with the image, and there is no browser download or apt step. The render service is reached at its service hostname. | R-04 (revised) |
| P4 | Cache-key and install-deps details. | changed: P3 removes the browser download and its cache. The diagram render cache (`DK_BEOE_CACHE_DIR`) stays. | R-04 |
| P5 | `check_schematic_diagrams.py` duplicates what the toolkit guarantees. Pinned `build` mode never falls back, and render errors throw (`diagrams.ts:151-155`, `diagram-figure.ts:231-251,354-360`). | accepted: the script is dropped. One workflow guard asserts `DK_DIAGRAM_BUILD_RENDER=build`, and one `grep` asserts every built schema page has a diagram figure. | R-04 |
| P6 | `prevalidate` is not needed and `predev` is missing; a stale copy is possible; the script should also stage the bundle. | accepted: `predev` and `prebuild` run the stage script. It empties `site/schemas/`, copies the schemas, copies the bundle from `../build/contracts/contract/`, and fails when a schema has no page. | R-03 |
| P7 | The quickstart local build never bundles the contract. | accepted. | quickstart.md |
| P8 | The probe branch needs `workflow_dispatch`, and copying the lockfile from a log is fragile. | accepted: the probe is dispatched on its branch and uploads `site/package-lock.json` as an artifact. The PR's `npm ci` confirms it, and the branch is then deleted. | R-01 |
| P9 | `check-links` skips site-absolute links, so `/reference/api/main/` is checked only by `check:built-links`. | accepted as the verification: the built-link check runs on every PR build. | IC-03 |
| P10 | Generating the 23 schema pages is not worth it, and nothing ties a page to its schema. | accepted: pages stay committed. The stage step asserts one page per schema (P6). | R-03 |
| P11 | IC-01 and IC-02 touch the same files in sequence. | accepted: merged into one concern. | plan.md |
| P12 | Reuse the not-yet-built note colour for «pending». | accepted: `#FFF3CD`, as already planned. | R-06 |

## Post-tasks squad dispositions (2026-11-02)

The squad had two lenses: reviewer-renata (fakeable DoDs and coverage) and implementer-ivan (realism in this environment). Every finding is folded into the WP prompts as "Post-tasks squad amendments (binding)".

| Finding | Disposition |
| --- | --- |
| The `<svg` diagram guard matches icon SVGs, and an empty glob passes | accepted: WP02 T011 counts pages and requires `class="plantuml-diagram"`, with a negative control as evidence |
| `sha256sum -c` is self-referential | accepted: WP02 compares the published file's hash with the bundler's `.sha256` |
| A dispatch run never exercises the PR-only `javadoc` path | accepted: WP02 states the path is unproven; the orchestrator verifies it on the draft PR |
| `term:` URN references are drawn as decided (ADR-0050 OQ10); R-06 misses OQs | accepted: WP03 and WP04 require a full table of every open question in ADR-0048 to 0052; WP04 T019 marks `term:` |
| WP02 cannot pass orphans or cspell within its owned files | accepted: WP02 owns `docs/architecture/README.md` and `spelling-words.txt`; WP05 edits the latter only as a recorded out-of-map edit |
| URL inventory via the sitemap misses drafts, redirects and Javadoc | accepted: WP01 T005 diffs every `*.html` path against main's Pages artifact |
| Probe tree identity is unverifiable | accepted: WP01 records the probe SHA and a diff limited to probe-only steps |
| `${{ github.workspace }}` in a container job | accepted: use `$GITHUB_WORKSPACE` |
| NFR-001 was not gated | accepted: it is a WP01 DoD gate |
| SC-002 and FR-011 had no delivering subtask | accepted: WP02 checks reachability in the built sidebar |
| WP05 was mis-typed as `planning_artifact` | accepted: `code_change`; its base must contain both lanes |
| T023 target text is absent; wrong line refs; `tests/architectural/` is absent | accepted: verify absent, find by content, use `check_docs.py terms` |
| Container job details (`--ipc=host`, python in the image) and recipe details (venv, `--user`) | accepted: WP01 and WP03 amendments |
| WP01 is oversized | deferred_with_rationale: a split would put two WPs on the same owned files (`package.json`, `docs-site.yml`), which the ownership rule forbids. WP01 stays one WP, and its CI round trips are bounded by the probe pattern. |
