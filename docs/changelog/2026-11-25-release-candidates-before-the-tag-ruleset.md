---
okf_version: "0.2"
title: Release candidates may precede the tag ruleset
description: "The release procedures say the tag ruleset of #196 is still to be applied, and needed only before a final tag; a proposal for the next candidates."
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/release-candidates-2026-11-25
  - guides/service-build
  - adr/0046-service-release-line-and-native-packaging
  - adr/0022-contract-versions-start-at-0-x
  - development/contract-consumers
audience:
  - profile: governance-lead
    guidance_text: Read this before you push a release tag.
  - profile: charter-service-contributor
    guidance_text: Read this before a release pull request.
---

# 2026-11-25 — Release candidates may precede the tag ruleset

Operator ruling of 2026-11-25: the tag ruleset of #196 is required before the first final tag of a release line; a
release candidate may be tagged before it.

- **The procedures.** `contracts/lint/README.md`, [Building the service](/guides/service-build/), the charter and
  service release workflows' comments, ADR-0022 and the milestones page described the ruleset as in place; the packs
  README and its workflow required it before any tag. All now say it is still to be applied, and needed only before a
  final tag. ADR-0046 records the ruling as an amendment of decision 7.
- **Smaller fixes.** Both contract procedures say to rewrite a released section's opening paragraph by hand. The
  service procedure says how to dispatch the native build at the merge commit. ADR-0022's packs note names the
  released-heading gate of #663. ADR-0046's status line notes ADR-0083's rename of the pack-check jar. The service procedure states the
  same-`X.Y.Z` order after a candidate (operator ruling, 2026-11-26), and
  [For contract consumers](/development/contract-consumers/) notes that the tag `service-v0.1.0-rc.2` was never
  published.
- **The proposal.** [Next release candidates, 2026-11-25](/plans/release-candidates-2026-11-25/) proposes charter
  contract `0.2.0-rc.4`, packs contract `0.1.0-rc.3` and service `0.1.0-rc.4`, gated on open-packs#343.
