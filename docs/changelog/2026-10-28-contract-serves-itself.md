---
okf_version: "0.2"
title: The contract declares GET /openapi.yaml, the document a client fetches from the service
description: The charter contract adds getOpenApiDocument, which answers the bundled contract as application/yaml with a strong ETag and a 304 on If-None-Match; the service serves it from #209.
doc_status: active
updated: 2026-10-28T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0018-action-context-read-and-seam-access-surface
audience:
  - profile: core-maintainer
    guidance_text: Read this for the new Service operation in the vendored contract; nothing existing changes on the wire.
  - profile: charter-service-contributor
    guidance_text: Read this before serving the bundle (#209) or writing its scenarios (#210).
---

# 2026-10-28 — The contract declares GET /openapi.yaml, the document a client fetches from the service

**A client can fetch the contract from the service it talks to.** The charter contract adds
`getOpenApiDocument` (`GET /api/v1/openapi.yaml`, under the Service tag) for
[#208](https://github.com/spec-kitty/spec-kitty-charter/issues/208), part of
[#207](https://github.com/spec-kitty/spec-kitty-charter/issues/207). It answers the bundled contract
the service was built from, byte for byte, as `application/yaml` (RFC 9512), with a strong `ETag`
that is the SHA-256 digest of those bytes. A request whose `If-None-Match` matches it gets a `304`
without a body. Like every operation it needs the worktree token, and a missing token or a browser
request is refused with the usual `401` or `403` Problem.

The contract is still the unreleased `0.1.0-SNAPSHOT`, so there is no version bump. The
`service-desc` link to the document is left to
[#126](https://github.com/spec-kitty/spec-kitty-charter/issues/126), which sets how the contract
expresses links.

The contract lint now lets a `304` go without a Problem body, and a new rule,
`not-modified-has-no-body`, refuses a `304` that declares one.

Until [#209](https://github.com/spec-kitty/spec-kitty-charter/issues/209) packages the bundle, the
service answers the operation with a `501` Problem. Its scenarios come with
[#210](https://github.com/spec-kitty/spec-kitty-charter/issues/210).
