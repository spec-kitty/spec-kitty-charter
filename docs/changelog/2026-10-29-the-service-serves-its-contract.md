---
okf_version: "0.2"
title: The service serves GET /openapi.yaml, the contract it was built from
description: GET /api/v1/openapi.yaml now answers the bundled contract byte for byte as application/yaml, with a SHA-256 ETag and a 304 on a matching If-None-Match.
doc_status: active
updated: 2026-10-29T00:00:00Z
type: Changelog
kind: Changelog
related:
  - changelog/2026-10-28-contract-serves-itself
  - adr/0007-rest-hateoas-contract-of-record
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for how the bundle reaches the service and why the service, not Spring, decides a 304.
  - profile: contract-consumer
    guidance_text: Read this to fetch the contract from a running service and check it against a release checksum.
---

# 2026-10-29 — The service serves GET /openapi.yaml, the contract it was built from

**A client can now fetch the charter contract from the service it talks to.** For
[#209](https://github.com/spec-kitty/spec-kitty-charter/issues/209), `GET /api/v1/openapi.yaml` no
longer answers `501`. It answers the bundled contract the service was built from, byte for byte, as
`application/yaml`, with `ETag: "sha256:<digest>"`. The digest is the SHA-256 of those bytes, the same
value as the checksum the contract build writes beside the bundle. A request whose `If-None-Match`
is `*` or holds that tag gets a `304` without a body. Like every operation it needs the worktree
token: a missing token is a `401` Problem and a browser request a `403` Problem, and neither shows the
`ETag`.

The served file is the build output, never a copy: the contract build publishes its validated bundle,
and the deployable packages it. The service refuses to start without it. Building the deployable
therefore runs the contract bundling, with its pinned tools.

The service decides the `304` itself, by the contract's rule. A value that is not an entity-tag list,
such as `garbage "sha256:…"` or a lower-case `w/` prefix, matches nothing and gets the document, where
Spring's own check would have answered `304`.

The contract is unchanged. Its scenarios come with
[#210](https://github.com/spec-kitty/spec-kitty-charter/issues/210).
