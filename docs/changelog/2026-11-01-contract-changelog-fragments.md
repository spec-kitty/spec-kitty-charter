---
okf_version: "0.2"
title: A contract change adds a changelog fragment, and the release pull request assembles them
description: Contract pull requests add a fragment under contracts/charter/changes/ instead of editing CHANGELOG.md; the release gate refuses a tag while any fragment is left.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0047-generated-indexes-and-merge-queue
  - adr/0022-contract-versions-start-at-0-x
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you change the contract or its scenarios.
---

# 2026-11-01 — A contract change adds a changelog fragment, and the release pull request assembles them

Under [ADR-0047](/adr/0047-generated-indexes-and-merge-queue/).

**Before:** every contract pull request edited the one `## 0.1.0-SNAPSHOT` section of
`contracts/charter/CHANGELOG.md`. Two large contract pull requests, #303 and #290, conflicted there.

**Now:**

- **A contract change adds a fragment.** It is one file, `contracts/charter/changes/YYYY-MM-DD-<slug>.md`,
  holding one `###` block.
  - A heading that names an existing subsection, such as `### Added` or `### Breaking`, adds to the top
    of that subsection.
  - Any other heading becomes a subsection of its own, after `### Breaking`.
- **Fragments are checked on every build.** `:contracts:checkContractChangelogFragments` runs in
  `verifyAll`. A malformed or misnamed fragment fails the pull request that adds it.
- **The release pull request assembles them.** It runs `./gradlew :contracts:assembleContractChangelog`,
  which moves every fragment into the top `-SNAPSHOT` section, newest first.
  - `:contracts:checkReleaseTag` refuses a release tag while any fragment is left.
  - The rehearsal in `service.yml` proves the refusal and the assembly.
- **The release gate is unchanged.** The version headings and `### Breaking` stay in `CHANGELOG.md`, so
  `ReleaseGate` reads the same text as before.

**What to do:** in a contract pull request, add a fragment and leave `CHANGELOG.md` alone. The format
is in `contracts/charter/changes/README.md`.
