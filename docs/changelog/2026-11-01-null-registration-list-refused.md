---
okf_version: "0.2"
title: A registration that sends a list as null is refused
description: An explicit null on any list of a directive, paradigm, styleguide or toolguide registration is a 422 validation_failed; an absent list still means empty.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0026-write-surface-is-functional-registration
audience:
  - profile: contract-consumer
    guidance_text: Read this if a client sends null for a registration list; leave the list out, or send [].
  - profile: charter-service-contributor
    guidance_text: Read this for where an explicit null list is refused and how the guarded lists are found.
---

# 2026-11-01 — A registration that sends a list as null is refused

**An explicit `null` list is a `422 validation_failed`**
([#292](https://github.com/spec-kitty/spec-kitty-charter/issues/292)). The contract types every
registration list as an array, so `null` is never a value. Until now the service read
`"references": null` as `[]` on a directive, paradigm, styleguide or toolguide registration. On a
styleguide, `"principles": null` failed with a server error. Both `POST` and `PUT` now refuse it
with a `validation_failed` refusal whose `detail` names the list. A list that is left out still means
empty, and `[]` is still an empty list. A `null` on a member the contract types as nullable, such as a
directive's `scope`, is unchanged.

The generated DTO cannot tell a `null` list from an absent one, so the body is checked before it is
read, as the toolguide guide fields already were. `RegistrationBodyAdvice` guards the directive,
paradigm and styleguide registrations. `ToolguideRegistrationBodyAdvice` adds the lists to its own
check, after its size bound. The guarded lists are read off the generated DTOs, so a list the
contract adds is guarded without a code change.
