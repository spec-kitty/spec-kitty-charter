---
title: The contract is published as 0.x, and the release gate breaks on a 0.x minor
description: The charter contract returns to 0.1.0-SNAPSHOT, and while its major version is 0 the release gate treats a minor version as the breaking boundary.
doc_status: active
updated: 2026-10-20T00:00:00Z
type: Changelog
kind: Changelog
related:
  - api/compatibility
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the contract version and what the release gate now checks in 0.x.
  - profile: core-maintainer
    guidance_text: Read this for which contract version core will pin first and what a 0.x minor costs.
---

# 2026-10-20 — The contract is published as 0.x, and the release gate breaks on a 0.x minor

The operator ruled on 2026-10-20 that the charter contract is published as 0.x while it
iterates with core's generated client, and that 1.0.0 is minted once it is stable. The decision
is ADR-0022 (proposed in [#123](https://github.com/spec-kitty/spec-kitty-charter/pull/123)); this change implements its D1 and D2
([#124](https://github.com/spec-kitty/spec-kitty-charter/issues/124)).

## What changed

- **The contract is `0.1.0-SNAPSHOT`.** `info.version` and the top heading of the contract
  `CHANGELOG.md` return to `0.1.0-SNAPSHOT`. The `1.0.0-SNAPSHOT` label is withdrawn; no release
  was cut under it. The first tag will be `contract-charter-v0.1.0`.
- **A 0.x minor is the breaking boundary.** While the major version is 0, the release gate
  compares a patch with the newest release of the same `0.y` and fails it on a breaking change.
  A minor bump (`0.y` to `0.(y+1)`) has no baseline of its own minor, needs a `### Breaking`
  subsection, and the build shows what it breaks against the previous minor, for information
  only. `0.y` to `1.0.0` is a major bump. From 1.0.0 the gate works as before.
- **The URL prefix stays `/api/v1`.**

The rules are stated in the `ReleaseGate` class Javadoc and summarized in
[Versioning and compatibility](/api/compatibility/). Release publishing is tracked in
[#45](https://github.com/spec-kitty/spec-kitty-charter/issues/45).
