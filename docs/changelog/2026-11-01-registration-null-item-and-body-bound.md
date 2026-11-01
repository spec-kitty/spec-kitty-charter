---
okf_version: "0.2"
title: A null list item is refused, and every registration body is bounded
description: A null item in a registration list is a 422 validation_failed instead of a 500, and a directive, paradigm or styleguide registration body over 1 MiB is refused.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0026-write-surface-is-functional-registration
  - adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides
audience:
  - profile: contract-consumer
    guidance_text: Read this if a client can send a null inside a registration list, or a registration body over 1 MiB.
  - profile: charter-service-contributor
    guidance_text: Read this for where a null list item and an oversized registration body are refused.
---

# 2026-11-01 — A null list item is refused, and every registration body is bounded

**A `null` item in a registration list is a `422 validation_failed`, not a `500`**
([#309](https://github.com/spec-kitty/spec-kitty-charter/issues/309)). The contract types the
items of every registration list as strings or objects, so `null` is never one. Until now
`"references": [null]` on a directive, paradigm, styleguide or toolguide registration passed the
converter and bean validation and failed in the controller's mapping as `500 internal_error`. Both
`POST` and `PUT` now refuse it with a `validation_failed` refusal whose `detail` names the list and
the item, for example `Item 0 of the references list is null; a list holds no null item.`. A `null`
*field* inside an object item is unchanged: bean validation refuses a required one `400 Bad Request`.

**A directive, paradigm or styleguide registration body over 1 MiB is a `422 validation_failed`**
([#310](https://github.com/spec-kitty/spec-kitty-charter/issues/310)). Nothing bounded these
bodies, and the null-list check of
[#292](https://github.com/spec-kitty/spec-kitty-charter/issues/292) holds one copy of the body. The
limit is the toolguide registration's room for the fields beside its guide, so every registration
holds its fields to the same budget; the largest pack artifact the service ships is about 14 KiB. A
body is refused by its `Content-Length` before a byte is read, and otherwise one byte past the
limit, so an oversized body is never held whole. The toolguide registration keeps its own limit,
six times the guide cap plus 1 MiB (7 MiB by default).

`RegistrationLists` holds the check and the bounded read for both body advices. It still reads
the body as the converter does, now matching by exact name the encodings the converter leaves for JSON to detect.
A body with a token after its object is left to the converter's `400`.

The contract does not yet state the null-item refusal or the 1 MiB limit; that is a contract
change of its own.
