---
okf_version: "0.2"
title: A pack linked into the configuration root is served and written through its target
description: A symbolic link under packs/ to a directory is a pack; its files are listed and digested, and a commit keeps the link and changes the target.
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

# 2026-10-31 — A pack linked into the configuration root is served and written through its target

**A pack can be linked in, not only copied in.** A symbolic link under `<config-root>/packs/` to a
directory is now a pack named after the link, at the level its target's `pack.yaml` declares
([#174](https://github.com/spec-kitty/spec-kitty-charter/issues/174),
[ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/) as amended 2026-10-31).
Before, a link was skipped without a word.

**A pack whose root is a link is no longer empty.** The walk of a pack's files starts at the root's
real path. A root reached through a link used to list no file: no tactic was answered and none was
digested, while `pack.yaml` and the graph shards were still read. Links below a pack's root are
still not followed.

**A commit keeps the link.** A registration into a linked pack stages and swaps beside the link's
real target. The link stays, and the target holds the new content.

**What changes for an operator:**

- These links are not served, and each is logged as a warning once, naming it:
  - a link that does not resolve, or that resolves to a file;
  - a link to the pack domain or to a directory above it;
  - two entries that resolve to one directory (two links to one target, or a link to a sibling
    pack). None of them is served.
- With a target outside the configuration root, a commit briefly writes its `.pack-commit-*`
  staging copy, and the `<name>.pack-old-*` aside copy, beside the target.
- A configuration root that already holds a linked pack now serves one more pack, so its
  `Charter-Source-Fingerprint` changes once.
