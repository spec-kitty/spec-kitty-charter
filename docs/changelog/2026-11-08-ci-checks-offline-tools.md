---
okf_version: "0.2"
title: CI keeps its pinned tools, so a github.com outage no longer turns pull requests red
description: The vacuum and oasdiff archives and the PlantUML jar are cached by their pinned checksum, and their downloads retry, so a release-download outage no longer fails CI.
doc_status: active
updated: 2026-11-08T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/docsite
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if a CI job failed while downloading vacuum, oasdiff or the PlantUML jar.
---

# 2026-11-08 — CI keeps its pinned tools

**A github.com outage on release downloads no longer turns a pull request red** (#420). On 2026-11-08,
github.com answered `504` on release assets, and every service job and the PlantUML check failed while
downloading a tool, before they checked anything.

- **vacuum and oasdiff.** `:contracts:installVacuum` and `:contracts:installOasdiff` keep each verified
  archive in `~/.gradle/caches/charter-tools/`, named by its pinned SHA-256, and use it before they go to
  the network. CI saves and restores that directory with the Gradle user home. Before, the tools survived
  only in the Gradle build cache, and a pull request that changed the build missed it and downloaded
  them. A download now retries up to four attempts. A cached archive is used only if it hashes to the pin,
  and `verifyVacuum` and `verifyOasdiff` still re-verify the installed tool on every build.
- **PlantUML.** The diagram check restores the pinned jar from an `actions/cache` entry keyed on
  `scripts/docs/plantuml_pins.json` and downloads it, with retries, only on a miss. The job also runs on
  the weekly schedule, which keeps the cache entry alive. `check_docs.py` still verifies the jar against
  the pin before every use.

Every check still runs on every pull request it ran on before.
