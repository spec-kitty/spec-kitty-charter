---
okf_version: "0.2"
title: The PlantUML check caches Graphviz, so an Ubuntu mirror outage no longer turns pull requests red
description: The diagram check installs Graphviz from cached packages that runs on main save, and falls back to the mirror only when the cached set does not install.
doc_status: active
updated: 2026-11-11T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/docsite
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if the PlantUML check failed while installing Graphviz.
---

# 2026-11-11 — The PlantUML check caches Graphviz

**An outage of the runners' Ubuntu mirror no longer turns a pull request red.** On 2026-11-11,
`azure.archive.ubuntu.com` did not resolve on the GitHub runners for over two hours. The PlantUML check
failed on every attempt while it installed Graphviz, before it compared a diagram, and the CI gate held
[PR #481](https://github.com/spec-kitty/spec-kitty-charter/pull/481).

- **The cache.** The check restores the Graphviz packages from an `actions/cache` entry. On a miss it
  downloads them through apt, which checks them against the signed archive index, and saves the entry at
  once. Pull requests restore the entry that runs on `main` saved, and the weekly run keeps it alive, as
  for the PlantUML jar ([#420](https://github.com/spec-kitty/spec-kitty-charter/issues/420)).
- **The install.** A hit installs the cached packages offline with `dpkg`. If they do not install, for
  example because the runner image changed, the check repairs any partial install and installs Graphviz
  from the mirror, with retries, and logs a warning.
- **Unlike the jar,** the cached packages carry no pin in the repository: Ubuntu ships security fixes to
  Graphviz under the same package names. They are trusted as apt verified them when `main` saved them.

The check still renders and compares every diagram on every pull request it ran on before.
