---
okf_version: "0.2"
title: A whole pack loads into the aggregate and writes back, and kinds register themselves
description: The Pack aggregate and pack storage key artifacts by kind through one registry, and a whole pack can be loaded from a directory and written to a new one.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - architecture/architectural-patterns-applied
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding an artifact kind, or before driving a whole pack through the domain.
  - profile: core-maintainer
    guidance_text: Read this for the whole-pack seam the round-trip gate uses; nothing on the wire changes.
---

# 2026-10-27 — A whole pack loads into the aggregate and writes back, and kinds register themselves

**A registration can no longer drop a kind it does not know.** Each registration rebuilt the
pack by copying the five kinds across by hand, so a sixth kind would have vanished on the next
commit. The `Pack` aggregate now keys its artifacts by kind, and a registration adds or replaces
one artifact with `Pack.with`, keeping everything else
([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/), proposed, for
[#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189)).

Adding a kind no longer edits the aggregate, the store, the format or the registration services.
Storage binds each kind once (`KindBinding`, listed in `KindBindings`, with one YAML format each and
the file name a new artifact gets); the store, the source fingerprint and the repository service
handle every bound kind the same way. Non-YAML kinds and kinds that live inside a graph will widen
the registry when they are built.
The five existing kinds behave as before, and the fingerprint of an unchanged pack is unchanged.

`WholePackStore` loads a pack directory into the aggregate (the descriptor and every bound kind)
and returns beside it where each artifact's file sat, both DRG document shapes with every field
(shard `tags`, `provenance` and `generated_by`, fragment `body_path`), and the list of files nothing
models yet. It writes all of that to a new directory through a staging directory and a rename,
putting each file back at its path; it refuses an existing target, a pack with unreadable files and
a `pack.yaml` with undeclared keys, rather than drop content without a word. This is the seam the whole-pack round-trip
gate ([#187](https://github.com/spec-kitty/spec-kitty-charter/issues/187)) drives, and the kind
missions (#190 to #195) each register their kind into it.
