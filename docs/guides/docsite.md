---
title: Building the docsite
description: How the docs/ tree becomes a doc-kitty site — building and checking it locally, what the CI workflow runs, and the one-time GitHub settings it needs.
doc_status: active
updated: 2026-10-14
type: Guide
kind: How-To
related:
  - adr/0003-docs-follow-doc-kitty
  - guides/contributing
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to build, check and publish the documentation site.
---

# Building the docsite

The `docs/` tree is rendered by [doc-kitty](https://github.com/spec-kitty/doc-kitty)'s
published toolkit, `@spec-kitty/doc-toolkit` (pinned to `0.2.0`). The Astro project that
does it lives in `site/`; it owns no content and reads `../docs` directly.

## Build locally

You need Node 22 or later (npm comes with it) and a GitHub token with the `read:packages`
scope, because the toolkit is published to GitHub Packages.

1. Put the token in your **user-level** `~/.npmrc`, never in the project file:

   ```ini
   //npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
   ```

2. Install, check and build from `site/`:

   ```sh
   cd site
   npm ci                      # installs exactly what package-lock.json records
   npm run validate            # frontmatter and source links in ../docs
   npm run build               # writes site/dist
   npm run check:built-links   # every link in the built site resolves under the base
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

## Glossary

The [Glossary](/glossary/) section is generated. Its terms are copied from the core
repository's glossary (`docs/context/charter.md`, `governance.md` and `product-lines.md` in
`spec-kitty/spec-kitty`), which stays the authority for them.

- `site/.contextive/definitions.yaml` holds the imported terms.
- Every build writes `docs/glossary/` from that file and links each term where it is first
  used in a section of a page. Do not edit either by hand.
- The toolkit writes only minimal frontmatter for those pages. `npm run build` therefore ends
  with `site/tools/import_core_glossary.py --finalize`, which adds the rest the docs checks
  require: a description, `updated` (the import date), `type`, `kind`, `audience` and
  `source_ref` (the core commit the terms came from). The import date and the commit are
  recorded in the header of `definitions.yaml`.

To change a term, change it in the core glossary. Then, with a checkout of the core
repository, import again from this repository's root and rebuild:

```sh
python3 site/tools/import_core_glossary.py /path/to/spec-kitty
cd site && npm run build
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
| `frontmatter` | Required keys, description of 50-180 characters, `doc_status` vocabulary, ISO `updated`, `audience` resolving to a persona under `docs/context/audience/` |
| `related` | Each `related` entry is the docs-root slug of an existing page |
| `freshness` | `updated` is within the SLA of the page's `doc_status` (draft 90 days, active 180, deprecated 365, ADRs 365; superseded, durable and changelog entries exempt) |
| `links` | Internal links are root-absolute routes that resolve, anchors exist, no absolute GitHub links to this repository's own docs |
| `terms` | The terminology rules in `docs/_meta/terminology.yaml`: the Mission canon, the two retired terms, glossary aliases. Add a deliberate mention to its `allow` list with a reason |
| `adr` | Every `docs/adr/NNNN-*.md` is in the ADR index; numbers are unique with no gaps |
| `changelog` | `YYYY-MM-DD-slug.md` names, listed newest first, under 3000 characters |
| `orphans` | Every page is linked from the `README.md` of its own directory |
| `plantuml` | Each `.puml` renders to the committed SVG |
| markdownlint, cspell | Markdown style and spelling. Add project words to `docs/_meta/spelling-words.txt`; `check_docs.py wordlist` prints glossary words not yet listed |

The `plantuml` check renders with the pinned PlantUML (`scripts/docs/plantuml_pins.json`:
version and jar SHA-256) and compares the semantic content of the SVG: the texts, plus the
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

Two workflows, both on pull requests and pushes to `main` that touch the docs.

`.github/workflows/docs-cleanliness.yml` holds the gates. Each is its own job or step, so a
red run names what failed:

- **Docs checks**: the unit tests, then `frontmatter`, `related`, `freshness`, `links`,
  `terms`, `adr`, `changelog` and `orphans`, each a separate step that runs even when an
  earlier one failed;
- **Markdown lint** and **Spellcheck**, with pinned tool versions;
- **PlantUML diagrams are current**, with the pinned jar and Java 21;
- **External links**, with lychee. It is not required: remote sites flake, so it reports but
  never blocks a pull request. It also runs weekly.

`.github/workflows/docs-site.yml` builds and publishes the site:

1. `npm ci`, reading the toolkit from GitHub Packages with the workflow's own token
   (`packages: read`);
2. `validate`, `build` and `check:built-links`, as above;
3. on `main` only, upload `site/dist` and deploy it to GitHub Pages at
   `https://spec-kitty.github.io/spec-kitty-charter/`.

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
