---
okf_version: "0.2"
title: A relative open-packs checkout path resolves against the repository root
description: The build resolves a relative open-packs checkout path against the repository root, so the documented sibling path ../spec-kitty-open-packs works from there.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - development/cloud-session-setup
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you point the build at an open-packs checkout.
---

# 2026-11-01 — A relative open-packs checkout path resolves against the repository root

Fixes [#322](https://github.com/spec-kitty/spec-kitty-charter/issues/322).

**Before:** the build resolved a relative `-PopenPacksDir` against `service/packs`, the project that
reads it, not the repository root. The documented
`./gradlew verifyAll -PopenPacksDir=../spec-kitty-open-packs`, run from the repository root, looked
for `service/spec-kitty-open-packs` and failed the open-packs round trip. Only an absolute path
worked.

**After:**

- A relative `-PopenPacksDir` resolves against the repository root, the directory `./gradlew` runs
  from. An absolute path and the default (`../spec-kitty-open-packs`, beside the repository) work as
  before.
- The corpus tests get the checkout as an absolute, normalized path. When that directory does not
  exist, Gradle stops before the tests and names it; when it exists but holds no `packs/built-in`,
  the tests' refusal names it and says how a relative path resolves.
- An empty `-PopenPacksDir=` is refused; it would have named the repository root itself.
- A build-logic test (`OpenPacksCheckoutTest`) covers the resolution, and the `whole-pack` workflow
  now passes the relative `-PopenPacksDir=.open-packs`, so CI runs the documented form end to end;
  the `service` workflow still passes an absolute path.
