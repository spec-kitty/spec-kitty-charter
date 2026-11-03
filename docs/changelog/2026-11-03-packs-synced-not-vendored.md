---
okf_version: "0.2"
title: "The internal and qa packs are synced from the pinned open-packs, not committed"
description: "packs/internal and packs/qa are no longer committed. A Gradle sync task fetches them from the pinned open-packs commit and verifies their digests."
doc_status: active
updated: 2026-11-03T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - adr/0005-service-registers-and-distributes-packs
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the one-time sync a new worktree needs and how a pin bump now works.
  - profile: core-maintainer
    guidance_text: Read this for how this repository now gets the packs, the same way core does.
---

# 2026-11-03 — The internal and qa packs are synced from the pinned open-packs, not committed

The operator ruled that vendored copies cause drift. This repository no longer commits `packs/internal` and
`packs/qa`; it takes them from the live `spec-kitty-open-packs` commit pinned by `openPacksRef`, as core does.

**Before:**

- Both packs were committed here, with a local `PROVENANCE.md` and `README.md`, and one local edit to
  `validate-pr-body.py` ([open-packs#92](https://github.com/spec-kitty/spec-kitty-open-packs/issues/92)).
- `VendoredPacksTest` compared the copies with the pin on every round-trip run.
- A pin bump meant re-vendoring by hand.

**Now:**

- **Sync.** `./gradlew syncPacks` (once per worktree) copies both trees from the pinned commit, read with
  `git archive` so a dirty checkout cannot leak in. The directories are ignored by Git.
- **Digests.** `packs/open-packs.lock.yaml` records a tree digest per pack at the pin. A mismatch fails closed;
  `-PcheckPacks=true` only verifies. CI runs the sync after the open-packs checkout.
- **Tests.** The corpus tests that read the copies now read the pinned open-packs checkout and run in
  `:packs:openPacksRoundTrip`. `VendoredPacksTest` is gone: the digest check replaces it.
- **Pin bump.** One commit: `openPacksRef`, then `./gradlew syncPacks -PupdatePacksLock=true`, then commit the
  lockfile. [Syncing the packs](/guides/service-build/#syncing-the-packs) has the steps.

**Unchanged:** `.kittify/config.yaml` and the agent-profile manifest keep their `packs/...` paths; they resolve once
the packs are synced.

**Lost with the copies:** the colon-form fix to `validate-pr-body.py` (`Closes: #5`) is not at the pin. It returns
when [open-packs#92](https://github.com/spec-kitty/spec-kitty-open-packs/issues/92) lands and the pin moves.
