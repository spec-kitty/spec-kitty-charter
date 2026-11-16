---
okf_version: "0.2"
title: The contract lines publish release candidates
description: The charter and packs contract gates accept X.Y.Z-<pre>, a candidate is published as a GitHub pre-release, and it is never a compatibility baseline.
doc_status: active
updated: 2026-11-16T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0022-contract-versions-start-at-0-x
  - adr/0046-service-release-line-and-native-packaging
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you cut a contract release or a service release that bundles one.
  - profile: core-maintainer
    guidance_text: Read this for what a contract release candidate promises, and what it does not.
---

# 2026-11-16 — The contract lines publish release candidates

Operator ruling R1: the first contract releases are release candidates, not 0.x finals. ADR-0022 records it in its
note of 2026-11-16.

- **The form.** The charter contract (`contract-charter-v*`) and the packs contract (`contract-packs-v*`) accept
  `X.Y.Z-<pre>`, with the service line's grammar: letters, digits and dots (`0.2.0-rc.1`). The publishing gates refuse
  a malformed one (`rc-1`, `rc..1`, `rc.01`, `+build`), naming the fault.
- **The order.** For one `X.Y.Z`, its candidates rank below `X.Y.Z-SNAPSHOT`, which ranks below the final `X.Y.Z`.
  After a candidate the next version is the same `-SNAPSHOT`, a later candidate, or the final release.
- **The baseline.** A candidate is never the compatibility baseline or the previous release: a candidate, and the
  `-SNAPSHOT` after it, are compared against the previous final release of their line.
- **Publishing.** `contract-release.yml` and `packs-contract-release.yml` make a GitHub pre-release, never marked
  latest, for a tag with a pre-release suffix.
- **The service.** A final `service-vX.Y.Z` still needs a final contract release; a service pre-release may bundle a
  contract candidate or an unreleased contract.
- **The rehearsals.** `rehearse-contract-release.sh` and `rehearse-packs-contract-release.sh` also rehearse a candidate,
  its refusals and the `-SNAPSHOT` reopened after it; the service rehearsal checks a final release over a contract
  candidate is refused.
