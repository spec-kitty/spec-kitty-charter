---
title: Generated models stop duplicating, and a tag can publish a contract release
description: The build generates one class per contract schema (#119), and a contract-charter-v* tag now publishes the bundle, its checksum and the scenarios (#45). No release is cut.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0022-contract-versions-start-at-0-x
  - adr/0002-contract-source-of-record
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for why the X1 classes are gone and how a contract release is now published and rehearsed.
  - profile: core-maintainer
    guidance_text: Read this for the two blockers of contract 0.1.0 that are now built, and what still waits for the release itself.
---

# 2026-10-26 — Generated models stop duplicating, and a tag can publish a contract release

Both things contract 0.1.0 waited on are built
([ADR-0022](/adr/0022-contract-versions-start-at-0-x/)). The release itself is not cut: the
contract stays `0.1.0-SNAPSHOT`.

## What changed

- **One class per schema (#119).** The generator's parser read some `additionalProperties: false`
  as a schema object standing for `false`, which the generator took for an open map, so it
  emitted copies: `Problem1`, `Tactic1`, `Directive1` and eight more. The build now reads such a
  boolean schema back as its boolean before generating (`charter.contract-api`), every copy is
  gone, and tests fail if one comes back or if an open map is closed. The contract and the wire shapes are unchanged.
- **A tag publishes a release (#45).** Pushing `contract-charter-v<version>` runs the workflow
  `contract-release.yml`: it rebuilds the bundle at the tag and attaches `charter-openapi.yaml`,
  its `.sha256` and `charter-scenarios.tar.gz` to a GitHub Release. It publishes nothing unless
  the tag, `info.version` and the top CHANGELOG heading name one released version, the contract
  checks pass and the tagged commit is on `main`. CI rehearses the path on a synthetic release in
  a throwaway clone, and a manual run of the workflow rehearses a real release on its pull
  request's branch; neither publishes.

## Who is affected

Contributors: controllers use `Tactic`, `Directive` and the other schema names directly; the
release steps are in
[the contract README](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md#releasing-a-contract-version).
Core: a client generated with the same generator from the split contract needs the same
`additionalProperties` fix. Core vendors the bundle once 0.1.0 is tagged, which is still to come.
