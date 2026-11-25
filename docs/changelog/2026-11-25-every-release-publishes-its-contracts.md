---
okf_version: "0.2"
title: Every service release publishes its contracts
description: Every service release, pre-releases too, ships only with released charter and packs contracts; a new page tells consumers where to get them.
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - development/contract-consumers
  - guides/service-build
  - guides/generate-a-client
  - adr/0046-service-release-line-and-native-packaging
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you cut a service release, a pre-release included.
  - profile: core-maintainer
    guidance_text: Read this for where the contract core generates its client from is published, and the declared-id rule.
---

# 2026-11-25 — Every service release publishes its contracts

Operator rulings of 2026-11-25 (#650): the charter publishes its OpenAPI contract so downstream consumers, core
included, generate their client from it, and a charter release is cut only with that publishing in place.

- **The gate.** `ServiceReleaseGate` refuses a `service-v*` tag, a pre-release too, unless the charter contract it
  bundles is released (`contract-charter-v<info.version>` merged into the commit) and so is the packs contract
  (`contract-packs-v<version>` merged, or, while the packs CHANGELOG heading is a reopened `-SNAPSHOT` and no packs
  contract file changed since, the release it is unchanged since). ADR-0046 records it in its amendment of
  2026-11-25; the operator accepted its three calls the same day (PR #653). Until now a pre-release could bundle an
  unreleased contract.
- **The bytes.** A released contract version must hold its tag's files: an edit that keeps the version is refused.
- **Final releases.** A final service release refuses a packs contract release candidate, as it already refused a
  charter one.
- **The workflow.** `service-release.yml` also checks that both contract releases are published with all their
  assets, and that those match the commit: the bundle's checksum, the scenarios, the activation document and the packs
  contract's files. The release notes name both contract releases. `gate.properties` names `packsContract` in place of `contractReleased`.
- **The rehearsal.** `rehearse-service-release.sh` rehearses a pre-release of an unreleased contract, an unreleased,
  unmerged, changed and unchanged packs contract, a final release over a packs candidate, and a released contract
  edited without reopening its version.
- **The docs.** [For contract consumers](/development/contract-consumers/) says where both contracts are published,
  what to pin under the 0.x rules, the generator pin and options, and that core writes declared ids through its
  generated client, never file slugs (#485). [Generate your own client](/guides/generate-a-client/) pins the newest
  `contract-charter-v*` release, no longer the `main` snapshot.
