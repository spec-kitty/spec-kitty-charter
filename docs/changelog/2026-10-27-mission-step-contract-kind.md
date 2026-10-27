---
okf_version: "0.2"
title: Mission step contracts round-trip through the domain
description: The service reads every mission step contract a pack carries into the domain and writes it back with the same meaning; the whole-pack gate shows the kind green.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before binding a kind whose schema or file name is not its token.
  - profile: core-maintainer
    guidance_text: Read this for which pack fingerprints change once; no endpoint changes.
---

# 2026-10-27 — Mission step contracts round-trip through the domain

**Every mission step contract the pinned packs carry now survives a read and a write.** The
service reads each `*.step-contract.yaml` into a domain artifact of kind `mission_step_contract`
and writes it back with the same meaning
([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)). In the whole-pack
round-trip gate, the 21 built-in step contracts are green, up from expected red. A corpus test
checks all 28 pinned files, including the 7 in `qa`, which no pack-level gate loads
([#191](https://github.com/spec-kitty/spec-kitty-charter/issues/191)).

The artifact holds every field the schema declares:

- the steps in order, each with its command, inputs, delegation and guidance;
- the gate bindings, as inert data. What a gate binding does is still #155's.

`fail_open` and `optional` stay booleans. A field written with its schema default stays written,
and an omitted one stays omitted. A key outside the schema refuses the file at any level. Steps
carry no role or profile in the pinned packs, so none is added
([ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/)).

**A kind's schema and file name no longer have to be its token.** A binding now names its schema,
as it names its file token. The store finds a file's kind through the registry, and the strategy
validates against the schema the binding names. This is an
[ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/) amendment. Agent profiles
(#190) need the same mapping. The whole-pack gate takes its kinds from the registry, so a kind joins
it by binding itself.

**What changes on the wire.**

- The source fingerprint and the ETag of every pack that holds step contracts (built-in, qa) change
  once. The bytes of those packs did not change. Packs without step contracts keep theirs.
- A relationship registration whose `from` is a `mission_step_contract:<id>` URN now resolves
  within its pack, where it was refused before. Binding a kind makes its URNs known.
- No endpoint changes. The contract's description of the fingerprint still lists the older kinds.
  Whether to update it is an open question on #191.
