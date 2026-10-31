---
okf_version: "0.2"
title: A linked packs/ directory is followed, and every write keeps a linked pack read-only
description: A linked packs/ domain is served under the linked-pack rules; every write refuses a pack linked from outside the root; CI fails link tests it cannot run.
doc_status: active
updated: 2026-10-31T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0038-one-configuration-root-a-directory-per-domain
  - changelog/2026-10-31-linked-packs
audience:
  - profile: pack-author
    guidance_text: Read this before you link a whole packs/ directory into the configuration root.
  - profile: charter-service-contributor
    guidance_text: Read this for the linked pack domain, the read-only check on every write path and the requireSymlinks property.
---

# 2026-10-31 — A linked packs/ directory is followed, and every write keeps a linked pack read-only

Follow-ups of [linked packs](/changelog/2026-10-31-linked-packs/), under
[ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/) as amended 2026-10-31.

**A `packs/` directory that is a link is served**
([#285](https://github.com/spec-kitty/spec-kitty-charter/issues/285)). Before, a
`<config-root>/packs` that was a symbolic link listed no pack and logged nothing. It is now followed
under the linked-pack rules:

- Linked from outside the configuration root, every pack in it is read-only through the service
  (`PACK_UNREADABLE`, "is linked from outside the configuration root; it is read-only through the
  service"). Edit the packs where they live.
- Linked to a directory inside the configuration root, outside `activations/`, its packs stay
  writable.
- A `packs/` link that does not resolve, resolves to a file, resolves to the configuration root or a
  directory above it, or resolves into `activations/` serves no pack. It is logged as a warning that
  names the reason.

**Every write path keeps a linked pack read-only**
([#287](https://github.com/spec-kitty/spec-kitty-charter/issues/287)). The per-file saves of the
repository service refuse a pack linked from outside the configuration root with the same message
as a registration, before anything is written. No served endpoint used them.

**The whole-pack gate and validate mode enter a linked pack root**
([#284](https://github.com/spec-kitty/spec-kitty-charter/issues/284)). Their walks start at the
root's real path, as the store's does, so a linked root no longer lists as empty there. Links below
a pack's root are still not followed.

**CI fails the link tests where links cannot be made**
([#286](https://github.com/spec-kitty/spec-kitty-charter/issues/286)). CI passes the Gradle
property `requireSymlinks=true`, so a link test fails on a file system that cannot make symbolic
links instead of skipping it. A local run without the property still skips.
