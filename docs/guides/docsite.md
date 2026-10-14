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

To change a term, change it in the core glossary. Then, with a checkout of the core
repository, import again from this repository's root and rebuild:

```sh
python3 site/tools/import_core_glossary.py /path/to/spec-kitty
cd site && npm run build
```

Commit `site/.contextive/definitions.yaml` and `docs/glossary/` together. To stop a term
from being linked on one page, set `glossary_autolink: false` in that page's frontmatter.

## What CI runs

`.github/workflows/docs-site.yml` runs on every pull request that touches `docs/` or `site/`,
and on pushes to `main`:

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
