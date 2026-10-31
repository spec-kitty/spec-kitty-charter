---
okf_version: "0.2"
title: A pack linked into the configuration root is served
description: A symbolic link under packs/ to a directory is a pack; its files are listed and digested. One linked from outside the configuration root is read-only.
doc_status: active
updated: 2026-10-31T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0038-one-configuration-root-a-directory-per-domain
  - adr/0026-write-surface-is-functional-registration
audience:
  - profile: pack-author
    guidance_text: Read this before you link a pack checkout into the configuration root instead of copying it.
  - profile: charter-service-contributor
    guidance_text: Read this for the walk from a root's real path, the link rules of StorageRootLocations and the commit through a link's target.
---

# 2026-10-31 — A pack linked into the configuration root is served

**A pack can be linked in, not only copied in.** A symbolic link under `<config-root>/packs/` to a
directory is now a pack named after the link, at the level its target's `pack.yaml` declares
([#174](https://github.com/spec-kitty/spec-kitty-charter/issues/174),
[ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/) as amended 2026-10-31).
Before, a link was skipped without a word.

**A pack whose root is a link is no longer empty.** The walk of a pack's files starts at the root's
real path. A root reached through a link used to list no file: no tactic was answered and none was
digested, while `pack.yaml` and the graph shards were still read. Links below a pack's root are
still not followed.

**A pack linked from outside the configuration root is read-only through the service.** A
registration into it is refused (`PACK_UNREADABLE`, "is linked from outside the configuration root;
it is read-only through the service"), and nothing is staged: the service does not replace a
directory it does not own, such as your checkout. Edit the pack where it lives. A link whose target
is inside the configuration root is written through the target, and the link stays.

**What changes for an operator:**

- These entries are not served, and each is logged as a warning, naming it and why:
  - a link that does not resolve, or that resolves to a file;
  - a link to the pack domain or to a directory above it;
  - a link into the configuration root outside `packs/`;
  - entries whose directories are one, or one inside the other (two links to one target, a link to
    a sibling pack or into it). None of them is served.
- A configuration root that already holds a linked pack now serves one more pack, so its
  `Charter-Source-Fingerprint` changes once.
