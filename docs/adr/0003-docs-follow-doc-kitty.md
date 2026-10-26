---
title: "ADR-0003: Documentation follows the doc-kitty convention"
description: The docs tree uses doc-kitty's structure — README-as-index sections registered in _meta/sections.yaml, frontmatter on every page, numbered ADRs and a dated changelog.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: ADR
kind: ADR
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the rules a documentation page in this repository must follow.
---

# ADR-0003: Documentation follows the doc-kitty convention

## Status

**Accepted** — 2026-10-13. Decider: Stijn Dejongh (operator), who asked for this
repository's documentation to use doc-kitty's structure.

**Amendment 2026-10-19** (operator): the toolkit moved to `@spec-kitty/doc-toolkit` `0.4.0`
(Astro 7, Starlight 0.42, Node 22.22+). The decision stands; three convention points change.
First, the glossary generator now **owns** `docs/glossary/**/README.md`: it writes an exact
frontmatter key set (including a `generated:` stamp) and refuses to overwrite a page that
carries any other key, so those pages are committed exactly as generated and the Python docs
checks exempt a page **carrying that stamp** from the authored-only frontmatter rules (there
is no post-build `--finalize` step any more; an unstamped page under `docs/glossary/` is still
treated as authored). Second, `updated` (and `stale_after`) are written as UTC instants
(`YYYY-MM-DDT00:00:00Z`), which doc-kitty 0.4.0 expects and the checks accept. Third, every
build now writes an **Open Knowledge Format (OKF)** bundle under `site/dist/okf` — doc-kitty's
structured export of the docs for agent consumption — and CI conformance-checks it
(`npm run check:okf`); section hubs carry an `okf_version` marker (`0.2` under this toolkit
version). OKF is defined and versioned by doc-kitty, not this repository. See
[Building the docsite](/guides/docsite/) and `docs/_meta/doc-rules.yaml`.

Clarification 2026-10-26 (the decision is unchanged): the pin is now `0.5.0`. It makes `kind`
required on every page again, which every page already carries; OKF stays at `0.2`, and the
conventions above are unchanged.

## Context

The repository started with a placeholder `docs/` tree (`index.md` pages for architecture,
guides, development, api and adr). doc-kitty, the Spec Kitty docs toolkit, defines a
convention for a `docs/` tree that it can render into a site with feeds and an agent API.

## Decision

- Every section is a folder whose `README.md` is its index (doc-kitty ADR-0002).
- Sections are registered in `docs/_meta/sections.yaml`: context, architecture, adr, plans,
  api, guides, changelog.
- Every page carries frontmatter: `title`, `description`, `doc_status`, `updated`, and
  `type` and `kind` where they help.
- ADRs are numbered `NNNN-<slug>.md` from [the template](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/_meta/templates/adr.template).
- Notable changes get a dated page under `changelog/`.
- The former `development/` section folds into `guides/` (contributor how-tos), since
  doc-kitty has no separate development section.

## Consequences

### Positive

- The tree can be rendered by doc-kitty without changes.
- Agents find the same shape here as in doc-kitty.

### Negative

- Pages link to each other with root-absolute routes (`/section/page/`), which the
  toolkit base-prefixes; `.md`-relative links do not survive a based deployment. GitHub's
  file view does not follow those routes.
- The convention is enforced by the docsite workflow (frontmatter, source links, built
  links); see [Building the docsite](/guides/docsite/).

## References

- [spec-kitty/doc-kitty](https://github.com/spec-kitty/doc-kitty), `docs/` and its ADRs
  0002, 0004 and 0009
