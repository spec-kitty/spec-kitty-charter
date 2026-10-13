---
title: Building the docsite
description: How the docs/ tree becomes a doc-kitty site — building and checking it locally, what the CI workflow runs, and the one-time GitHub settings it needs.
doc_status: active
updated: 2026-10-13
type: Guide
kind: How-To
related:
  - adr/0003-docs-follow-doc-kitty
  - guides/contributing
---

# Building the docsite

The `docs/` tree is rendered by [doc-kitty](https://github.com/spec-kitty/doc-kitty)'s
published toolkit, `@spec-kitty/doc-toolkit` (pinned to `0.2.0`). The Astro project that
does it lives in `site/`; it owns no content and reads `../docs` directly.

## Build locally

You need Node 22 or later, pnpm (`corepack enable`), and a GitHub token with the
`read:packages` scope, because the toolkit is published to GitHub Packages.

1. Put the token in your **user-level** `~/.npmrc` (pnpm ignores a token in the project
   file):

   ```ini
   //npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
   ```

2. Install, check and build from `site/`:

   ```sh
   cd site
   pnpm install
   pnpm run validate            # frontmatter and source links in ../docs
   pnpm run build               # writes site/dist
   pnpm run check:built-links   # every link in the built site resolves under the base
   pnpm run preview
   ```

`site/.npmrc` maps the `@spec-kitty` scope to GitHub Packages and holds no secret.

## Writing links

Link between pages with **root-absolute routes**, such as `/architecture/overview/` or
`/adr/0001-java-service-with-openapi-contract/`. The toolkit prefixes them with the site's
base path (`/spec-kitty-charter`). A `.md`-relative link builds but 404s on the published
site; `check:built-links` fails on it. Links outside `docs/` (code, the repository root) use
full GitHub URLs.

## What CI runs

`.github/workflows/docs-site.yml` runs on every pull request that touches `docs/` or `site/`,
and on pushes to `main`:

1. install from GitHub Packages with the workflow's own token (`packages: read`);
2. `validate`, `build` and `check:built-links`, as above;
3. on `main` only, upload `site/dist` and deploy it to GitHub Pages at
   `https://spec-kitty.github.io/spec-kitty-charter/`.

Drafts are published (as `noindex` pages), because most pages are drafts during the
bootstrap. Set `DK_PUBLISH_DRAFTS=off` in the workflow once that changes.

## One-time settings

Two settings live outside the repository and need an administrator:

- **Package access.** The workflow's token can read `@spec-kitty/doc-toolkit` only if the
  package grants this repository access: in the package's settings on GitHub, under
  *Manage Actions access*, add `spec-kitty-charter` with the Read role. Without it the
  install step fails with `403` or `404`.
- **Pages source.** In this repository's *Settings → Pages*, set the source to
  *GitHub Actions*.

## Not done yet

- **No lockfile is committed.** The first lockfile has to be resolved against GitHub
  Packages; CI installs with `--no-frozen-lockfile` until one is committed. The toolkit
  itself is pinned exactly. Commit `site/pnpm-lock.yaml` from a successful install, remove
  it from `site/.gitignore`, and switch CI to `--frozen-lockfile`.
