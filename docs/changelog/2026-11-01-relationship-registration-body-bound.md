---
okf_version: "0.2"
title: A relationship registration body is bounded
description: A relationship registration body over 1 MiB is a 422 validation_failed, held to the same budget as the other registrations.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0026-write-surface-is-functional-registration
audience:
  - profile: contract-consumer
    guidance_text: Read this if a client can send a relationship registration body over 1 MiB.
  - profile: charter-service-contributor
    guidance_text: Read this for where a relationship registration body is bounded, and where the activation bodies will be.
---

# 2026-11-01 — A relationship registration body is bounded

**A relationship registration body over 1 MiB is a `422 validation_failed`**
([#323](https://github.com/spec-kitty/spec-kitty-charter/issues/323)). `PUT
/packs/{packId}/relationships` read a body of any size. It is now held to the same 1 MiB budget as
the directive, paradigm and styleguide registrations, by the same request-body guard. A body is
refused by its `Content-Length` before a byte is read, and otherwise one byte past the limit. The
`detail` reads `The relationship registration body is larger than 1048576 bytes.`. A relationship
registration has no list, so there is no null-list or null-item check. A `null` in `from`, `to` or
`type` stays bean validation's `400 Bad Request`.

The selection, exclusion and preset application bodies are not guarded yet. Their operations are
reserved and answer `501` before the body is read. The contract refuses a body that fails their
schema with `activation_invalid` and a `violations` entry, and that code is raised for the first
time when the operations are built
([#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291)). Their guard lands with them.

The contract states the limit on the relationship write's 422 and on `RelationshipRegistration`, with a
scenario in `relationships.feature`.
