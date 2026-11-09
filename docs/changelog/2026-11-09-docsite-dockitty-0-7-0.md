---
okf_version: "0.2"
title: The docsite moves to DocKitty 0.7.0
description: The documentation site upgrades to DocKitty 0.7.0 with linked section titles, sidebar icons, source links, and richer OpenAPI and MCP discovery.
doc_status: active
updated: 2026-11-09T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0003-docs-follow-doc-kitty
  - guides/docsite
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the documentation navigation and machine-readable surfaces added by the toolkit upgrade.
---

# 2026-11-09 — The docsite moves to DocKitty 0.7.0

**The docsite lists every contract operation for agents, and its sidebar links each section title to its index**
(#449). The site is built with `@spec-kitty/doc-toolkit` `0.7.0` instead of `0.6.0`. Astro 7, Starlight 0.42 and
Node 22.22 stay as they were.

## What changed

- **Section titles are their index links.** The generated sidebar no longer adds a separate
  "Overview" entry below each group. Groups and page metadata blocks keep the new default:
  they start collapsed, except for the path to the current page.
- **Sections have icons.** The top-level entries in `docs/_meta/sections.yaml` declare
  Font Awesome solid icons for the sidebar.
- **Pages link their other forms.** Authored, published pages link to their GitHub source and
  their JSON, OKF and Atom versions. The footer lists the site's machine-readable entry points.
- **The contract is easier for agents to navigate.** `llms.txt` lists every OpenAPI operation
  under the `main` contract and advertises the generic `doc-kitty-mcp --site …` command. This
  is a client-side stdio server over the published artifacts, not a service the site deploys.
  The unreleased `main` reference stays available to readers and agents but leaves the sitemap
  and search, as DocKitty 0.7.0 intends.

The existing `main` OpenAPI label is route-safe, so the release's new label validation needs
no contract or URL change. Local setup and build commands are unchanged; see
[Building the docsite](/guides/docsite/).
