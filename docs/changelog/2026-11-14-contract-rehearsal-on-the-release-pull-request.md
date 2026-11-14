---
okf_version: "0.2"
title: The contract rehearsal passes on the release pull request
description: rehearse-contract-release.sh now rehearses a release as it stands when info.version is already the release, so the release pull request's required gate no longer goes red.
doc_status: active
updated: 2026-11-14T00:00:00Z
type: Changelog
kind: Changelog
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you open a charter contract release pull request.
---

# 2026-11-14 — The contract rehearsal passes on the release pull request

`scripts/release/rehearse-contract-release.sh` refused to run unless `info.version` of
`contracts/charter/openapi.yaml` was a `-SNAPSHOT`
([#571](https://github.com/spec-kitty/spec-kitty-charter/issues/571)). Step 1 of "Releasing a contract version"
(`contracts/lint/README.md`) sets the release version in the release pull request, so that pull request's
`release-rehearsal` job, and with it the required `CI gate / gate`, would have gone red on the first charter
release ([#45](https://github.com/spec-kitty/spec-kitty-charter/issues/45)). The packs rehearsal had the same flaw and
was fixed in [#568](https://github.com/spec-kitty/spec-kitty-charter/pull/568).

- **Before:** on a commit whose `info.version` is a release, the rehearsal exited 1 ("rehearse on a commit before the
  release").
- **After:** on such a commit it tags the release in its throwaway clone, runs `contract-release-assets.sh` and checks
  the four assets. The publishing gate still refuses a release whose top CHANGELOG heading differs or that leaves a
  changelog fragment behind, so the rehearsal still fails there.
- On a `-SNAPSHOT` commit it still expects the unreleased tag to be refused. Its synthetic releases, one with a
  fragment left and one assembled, are now rehearsed by running the script itself on them, so every pull request
  exercises the release pull request's path. A synthetic release that is still a `-SNAPSHOT` stops the run, so the
  script cannot call itself again.
- Both the charter and the packs rehearsal now tag the release with `git tag -f`, so a re-run on the tagged release
  commit does not fail on the existing tag.
- Nothing is published, and no tag leaves the clone.
