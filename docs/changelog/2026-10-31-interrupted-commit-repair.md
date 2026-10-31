---
okf_version: "0.2"
title: A pack whose commit a crash interrupted is restored on the next listing
description: A commit that died between its swap's two renames is repaired on the next listing of packs/, for a plain and a linked pack, under the pack's write lock.
doc_status: active
updated: 2026-10-31T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0038-one-configuration-root-a-directory-per-domain
  - changelog/2026-10-31-linked-pack-hardening
audience:
  - profile: pack-author
    guidance_text: Read this if a pack disappeared after the service stopped during a registration.
  - profile: charter-service-contributor
    guidance_text: Read this for where the interrupted-commit repair runs, its lock, and what it leaves alone.
---

# 2026-10-31 — A pack whose commit a crash interrupted is restored on the next listing

Fixes [#281](https://github.com/spec-kitty/spec-kitty-charter/issues/281), under
[ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/) as amended 2026-10-31.

**Before:** a registration's commit swaps a staged copy into place with two renames. If the service
died between them, the pack's directory was missing and its last committed state sat beside it as
`<name>.pack-old-*`. For a linked pack, the target was missing and the link dangled. The pack then
silently stopped being served, and the repair meant for this state was never reached. The aside copy
had to be moved back by hand.

**Now:** the next listing of `packs/`, made by any read or write, restores the pack under its write
lock.

- **A plain pack** is restored from its sole `<name>.pack-old-*` copy.
- **A linked pack** is restored at its link's target, from the sole aside copy beside the target. The
  link stays as it was.
- **A read** that meets the half-swapped state is served the restored pack. One that meets a
  registration in flight in the same process waits for it, for at most ten seconds, and is served
  its result.

A read can therefore rename directories in `packs/`.

**Left alone:**

- zero or several aside copies (several are warned: restore one by hand);
- a restore target outside the pack domain, or in or around another pack;
- a pack linked from outside the configuration root, which is read-only through the service.

A restore that fails is logged as a warning, and the pack is not served.

**A stale aside copy is hidden, never restored.** An aside copy beside a pack that exists is left
over from a swap that finished. A commit, and for a plain pack the next listing, renames it to a
hidden `.pack-discard-*`, so it is never restored over a pack you removed on purpose. A successful
commit hides its own aside copy the same way before it deletes it.
