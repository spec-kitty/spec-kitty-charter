---
okf_version: "0.2"
title: An Ubuntu mirror outage no longer turns a pull request's docs gate red
description: The PlantUML check renders on a pull request only when a diagram input changed, installs Graphviz from cached packages, and retries apt with a fallback mirror on a miss.
doc_status: active
updated: 2026-11-11T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/docsite
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if the PlantUML check failed or was skipped while installing Graphviz.
---

# 2026-11-11 — An Ubuntu mirror outage no longer turns the docs gate red

**An outage of the runners' Ubuntu mirror no longer turns a pull request red.** On 2026-11-11,
`azure.archive.ubuntu.com` did not resolve on the GitHub runners for over two hours. The PlantUML check
failed on every attempt while it installed Graphviz, before it compared a diagram, and the CI gate held
[PR #481](https://github.com/spec-kitty/spec-kitty-charter/pull/481), which changes no diagram.

- **Render only when it can matter.** On a pull request the check renders only if a diagram input
  changed: a `.puml` or `.svg` file, `scripts/docs/`, or its workflow. Otherwise it passes with a note.
  Pushes to `main`, the weekly schedule and a manual dispatch always render.
- **Graphviz from a cache.** The Graphviz packages are cached per runner image, saved by runs on `main`
  and restored by pull requests, as for the PlantUML jar
  ([#420](https://github.com/spec-kitty/spec-kitty-charter/issues/420)). A hit installs them with `dpkg`
  and needs no mirror.
- **Retries and a fallback mirror on a miss.** apt retries, and from the third attempt it uses
  `mirrors.edge.kernel.org`. A cached set that does not install is repaired first, so it cannot make
  every retry fail.
- **Unlike the jar,** the cached packages carry no pin in the repository: Ubuntu ships security fixes to
  Graphviz under the same package names. They are trusted as apt verified them when `main` saved them.
- **Docs site.** Its `python3-venv` install retries too. That job is not a required check.
