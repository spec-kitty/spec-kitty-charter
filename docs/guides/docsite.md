---
title: Building the docsite
description: How the docs/ tree becomes a doc-kitty site — building and checking it locally, what the CI workflow runs, and the one-time GitHub settings it needs.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: Guide
kind: How-To
related:
  - adr/0003-docs-follow-doc-kitty
  - adr/0029-charter-glossary-is-owned-here
  - guides/contributing
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to build, check and publish the documentation site.
---

# Building the docsite

The `docs/` tree is rendered by [doc-kitty](https://github.com/spec-kitty/doc-kitty)'s
published toolkit, `@spec-kitty/doc-toolkit` (pinned to `0.5.0`, which builds on Astro 7 and
Starlight 0.42). The Astro project that does it lives in `site/`; it owns no content and
reads `../docs` directly.

## Build locally

You need Node 22.22 or later (`site/.nvmrc` pins it; npm comes with it) and a GitHub token
with the `read:packages` scope, because the toolkit is published to GitHub Packages.

1. Put the token in your **user-level** `~/.npmrc`, never in the project file:

   ```ini
   //npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
   ```

   If you use the GitHub CLI, its token already carries `read:packages`, so you can install
   without storing a second one:

   ```sh
   NODE_AUTH_TOKEN="$(gh auth token)" npm ci --userconfig <(echo '//npm.pkg.github.com/:_authToken=${NODE_AUTH_TOKEN}')
   ```

2. Install, check and build from `site/`:

   ```sh
   cd site
   npm ci                      # installs exactly what package-lock.json records
   npm run validate            # frontmatter and source links in ../docs
   npm run build               # writes site/dist (and site/dist/okf, the OKF bundle)
   npm run check:built-links   # every link in the built site resolves under the base
   npm run check:okf           # the OKF export bundle passes conformance
   npm run preview
   ```

`site/.npmrc` maps the `@spec-kitty` scope to GitHub Packages and every other package to the
public npm registry. It holds no secret. Because the registry is set there, a default
registry in your own `~/.npmrc` (a company proxy, for example) does not apply to this site.

## Writing links

Link between pages with **root-absolute routes**, such as `/architecture/overview/` or
`/adr/0001-java-service-with-openapi-contract/`. The toolkit prefixes them with the site's
base path (`/spec-kitty-charter`). A `.md`-relative link builds but 404s on the published
site; `check:built-links` fails on it. Links outside `docs/` (code, the repository root) use
full GitHub URLs.

## The changelog and ADR indexes

The lists in [Changelog](/changelog/) and [Decision Records](/adr/) are generated from each
entry's frontmatter ([ADR-0047](/adr/0047-generated-indexes-and-merge-queue/)), so no pull
request edits a shared hub:

- A changelog entry `docs/changelog/YYYY-MM-DD-slug.md` gives its date (from the filename) and
  its `title`. The list runs newest date first, then by filename.
- An ADR `docs/adr/NNNN-slug.md` gives its `title`, `decision_status` (the Status column, such
  as `Accepted` or `Proposed`) and `decision_date` (`'YYYY-MM-DD'`). The table runs in number
  order.
- Each hub commits an empty block between the `BEGIN GENERATED INDEX` and `END GENERATED INDEX`
  markers. The site build fills it, and `check_docs.py` checks the hub as it regenerates. A
  committed block that holds anything fails the check, and the hubs are exempt from the
  freshness SLA, because no pull request edits them.

To preview the lists, or to see which entry cannot be listed:

```sh
python3 scripts/docs/generate_indexes.py           # report only
python3 scripts/docs/generate_indexes.py --write   # fill the blocks; do not commit the result
```

## Glossary

The [Glossary](/glossary/) section is generated from a file this repository authors:
`site/.contextive/definitions.yaml`. This repository owns the charter glossary
([ADR-0029](/adr/0029-charter-glossary-is-owned-here/)); it is no longer imported from the core
repository, and core's copy is cleaned up separately.

- `site/.contextive/definitions.yaml` holds the terms, grouped in contexts (Charter,
  Governance, Product lines). Each term has a `name`, a short `definition`, optional
  `aliases` and a `meta` map; `meta.status` is `canonical` for a decided term, `proposed`
  where the [conceptual model](/context/conceptual-model/) leaves something open, and
  `deprecated` for a superseded term, whose definition names its replacement.
- Every build writes `docs/glossary/**/README.md` from that file and links each term where it
  is first used in a section of a page. Do not edit the pages by hand.
- Since doc-kitty 0.4.0 the glossary generator **owns** those pages: it writes an exact
  frontmatter key set (`okf_version`, `title`, `description`, `kind`, `glossary_context`,
  `doc_status` and a `generated:` stamp) and refuses to overwrite a page that carries any
  other key. So the pages are committed exactly as the generator writes them — the build no
  longer post-processes their frontmatter — and the Python docs checks exempt a generated
  glossary page from the authored-only rules (`updated`, `type`, `audience` and the
  description-length range), via `GENERATED_EXEMPT_KEYS` in `scripts/docs/check_docs.py`.
- An alias becomes an `avoid` rule of the terminology check, so only a name that never
  appears as ordinary prose (an identifier such as `in_tension_with`) is listed as an alias.
  Where an older page must keep the old name, add an `allow` entry with a reason in
  `docs/_meta/terminology.yaml`.

To add or change a term, edit `site/.contextive/definitions.yaml` in the same change as the
page or decision that introduces it, then rebuild and check:

```sh
(cd site && npm run build)
python3 scripts/docs/check_docs.py terms
```

Commit `site/.contextive/definitions.yaml` and `docs/glossary/` together. To stop a term
from being linked on one page, set `glossary_autolink: false` in that page's frontmatter.

## Check the docs locally

The docs checks need Python 3.11 or later and PyYAML (`pip install -r scripts/docs/requirements.txt`).
From the repository root:

```sh
python3 scripts/docs/check_docs.py all          # every Python check below, one report line each
python3 scripts/docs/check_docs.py links terms  # or pick checks by name
python3 scripts/docs/check_docs.py plantuml     # needs the pinned PlantUML and Java
python3 -m pytest scripts/docs/tests -q         # the checks' own tests
npx markdownlint-cli2@0.18.1                    # Markdown lint (.markdownlint-cli2.jsonc)
npx cspell@9.2.0 --no-progress                  # spelling (cspell.json)
cd site && npm run check:docs                   # same as `check_docs.py all`
```

`docs/_meta/doc-rules.yaml` lists every rule with the check that enforces it, or says why
none does. The checks:

| Check | What it enforces |
|-------|------------------|
| `frontmatter` | Required keys, description of 50-180 characters, `doc_status` vocabulary, ISO `updated`, `audience` resolving to a persona under `docs/context/audience/`. Generated glossary pages are exempt from `updated`, `type`, `audience` and the description range (doc-kitty owns their frontmatter) |
| `related` | Each `related` entry is the docs-root slug of an existing page |
| `freshness` | `updated` is within the SLA of the page's `doc_status` (draft 90 days, active 180, deprecated 365, ADRs 365; superseded, durable, changelog entries and the two generated index hubs exempt) |
| `links` | Internal links are root-absolute routes that resolve, anchors exist, no absolute GitHub links to this repository's own docs |
| `terms` | The terminology rules in `docs/_meta/terminology.yaml`: the Mission canon, the two retired terms, glossary aliases. Add a deliberate mention to its `allow` list with a reason |
| `adr` | The ADR index regenerates cleanly: every `docs/adr/NNNN-*.md` has the `title`, `decision_status` and `decision_date` the index needs, and the hub has its generated block; numbers are unique with no gaps |
| `changelog` | `YYYY-MM-DD-slug.md` names, a `title` for the generated index, under 3000 characters |
| `orphans` | Every page is linked from the `README.md` of its own directory, the generated indexes included |
| `plantuml` | Each `.puml` renders to the committed SVG |
| markdownlint, cspell | Markdown style and spelling. Add project words to `docs/_meta/spelling-words.txt`; `check_docs.py wordlist` prints glossary words not yet listed |

The `plantuml` check renders with the pinned PlantUML (`scripts/docs/plantuml_pins.json`:
version and jar SHA-256; it needs Java and Graphviz `dot`) and compares the semantic content of the SVG: the texts, plus the
ids, classes, entity names, relation types, source lines and link targets PlantUML stamps on
its elements. Geometry, styles, fonts, path data and text widths are ignored, because they
vary with the fonts and Java of the machine that renders; a changed label, class, member,
relation or link still fails. In CI a failing job uploads its renders as the
`rendered-diagrams` artifact (`PLANTUML_RENDER_DIR` sets the folder).
Pass `--strict-svg` to require identical bytes
(the committed SVGs match the pinned version byte for byte on the machine that rendered
them). Locally it uses `plantuml` on the path; set `PLANTUML_JAR` to use a downloaded jar,
which is verified against the pin. Re-render a stale diagram with `plantuml -tsvg <file>.puml`
in `docs/architecture/diagrams/`.

## What CI runs

Two workflows run on pull requests and pushes to `main` that touch the docs. The docs checks
reach a pull request through the CI gate (`.github/workflows/ci.yml`, which calls
`docs-cleanliness.yml`); the docsite workflow runs on the pull request itself. The docsite
workflow also runs when the service build changes, because the site carries its Javadoc.

`.github/workflows/docs-cleanliness.yml` holds the gates. Each is its own job or step, so a
red run names what failed:

- **Docs checks**: the unit tests, then `frontmatter`, `related`, `freshness`, `links`,
  `terms`, `adr`, `changelog` and `orphans`, each a separate step that runs even when an
  earlier one failed;
- **Markdown lint** and **Spellcheck**, with pinned tool versions;
- **PlantUML diagrams are current**, with the pinned jar, Java 21 and Graphviz;
- **External links**, with lychee. It is not required: remote sites flake, so it reports but
  never blocks a pull request. It also runs weekly, together with the freshness check, so a page that goes stale on its own is noticed.

`.github/workflows/docs-site.yml` builds and publishes the site:

1. the `service` job calls `.github/workflows/service.yml`, the main build workflow, which
   runs `./gradlew verifyAll` and uploads the aggregated Javadoc as the `javadoc` artifact;
2. `npm ci`, reading the toolkit from GitHub Packages with the workflow's own token
   (`packages: read`);
3. the `javadoc` artifact of the same run is downloaded into `site/public/javadoc` (ignored
   by Git), so it is published unchanged under `/javadoc/`
   ([Java API reference](/api/javadoc/));
4. `validate`, `build`, `check:built-links` and `check:okf`, as above; the link check walks
   the Javadoc pages too, and the OKF check validates the exported knowledge-format bundle;
5. on `main` only, upload `site/dist` and deploy it to GitHub Pages at
   `https://spec-kitty.github.io/spec-kitty-charter/`.

To see the Javadoc in a local build, run `./gradlew :charter-reports:stageJavadocForSite`
before `npm run build`.

Drafts are published (as `noindex` pages), because most pages are drafts during the
bootstrap. Set `DK_PUBLISH_DRAFTS=off` in the workflow once that changes.

## One-time settings

- **Pages source.** In this repository's *Settings → Pages*, set the source to
  *GitHub Actions*. Until then the deploy job on `main` fails.
- **Package access.** The workflow's token already reads `@spec-kitty/doc-toolkit`. If that
  ever turns into a `401` or `403` at install, check the package's *Manage Actions access*
  list for `spec-kitty-charter`.

## Dependencies and the lockfile

`site/package-lock.json` is committed and CI installs with `npm ci`, so the site on `main`
is built from the same dependency versions its pull request was checked with.

To change a dependency, edit `site/package.json`, run `npm install` in `site/`, and commit
the updated lockfile with it.
