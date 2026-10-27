---
okf_version: "0.2"
title: Agent profiles round-trip through the domain
description: Agent profiles are read into a domain entity of their own slice and written back by meaning; the whole-pack gate reports all 26 built-in profiles green.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0035-charter-artifact-interface-lives-in-the-kernel
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the new profiles slice and the binding seam for a kind whose schema or id key is not its token.
  - profile: core-maintainer
    guidance_text: Read this for which agent-profile content the service now reads and writes back.
---

# 2026-10-27 — Agent profiles round-trip through the domain

**Every agent profile of a pack is now read into the domain and written back with its meaning
intact** ([#190](https://github.com/spec-kitty/spec-kitty-charter/issues/190)). An `AgentProfile`
holds every field of the `agent-profile` schema. A defaulted field the file does not declare stays
undeclared, so a write never invents the default. Lineage stays a `specializes_from` edge of the
pack's graph, and a profile mints no edges of its own. Resolving `enhances`, `overrides` or
`excluding` is not part of this change. The whole-pack gate reports all 26 built-in profiles
green. The 5 `qa` profiles and the 5 vendored ones load and reload with the same meaning.

**Agent profiles have their own slice.** The entity lives in the new `:profiles` module, as
[ADR-0035](/adr/0035-charter-artifact-interface-lives-in-the-kernel/) requires. The pack store
binds the kind and depends on that module. A kind's binding now names its schema and its id key
(an agent profile's are `agent-profile` and `profile-id`)
([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/), amendment of 2026-10-27).

What changes for a caller:

- A pack that holds agent profiles gets a new `Charter-Source-Fingerprint` and ETag once.
- A relationship registered from an agent profile of the pack now resolves. It was a
  `registration_conflict` while profiles were not loaded.
- Two profile files with one `profile-id` in a pack break the pack's load, as for every other
  kind.

The gate's comparator now also treats a declared object whose fields are all empty as absent
([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) Decision 2). The HTTP contract
does not change.
