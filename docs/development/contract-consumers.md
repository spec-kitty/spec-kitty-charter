---
okf_version: "0.2"
title: For contract consumers
description: Where the charter and packs contracts are published, which release to pin, how to generate a client, and why core writes declared ids through it.
doc_status: draft
updated: 2026-11-25T00:00:00Z
type: Guide
kind: How-To
related:
  - guides/generate-a-client
  - guides/pin-the-packs-contract
  - guides/check-packs
  - guides/service-build
  - api/compatibility
  - adr/0022-contract-versions-start-at-0-x
  - adr/0046-service-release-line-and-native-packaging
audience:
  - profile: core-maintainer
    guidance_text: Read this for the contract release core pins, how core generates its client, and the rule that core writes declared ids, never file slugs.
  - profile: contract-consumer
    guidance_text: Read this to find, pin, verify and generate a client from the published charter contract.
  - profile: pack-author
    guidance_text: Read this for the packs contract release your tools validate against, and where pack-doctor comes from.
---

# For contract consumers

This page is for anyone who builds against the charter service from outside this repository: spec-kitty core,
spec-kitty-open-packs, and any other client. It says where the contracts are published, which release to pin, and how
to get from a release to a working client. The contract is canonical here: a consumer generates from it and follows
it; it does not hand-write the shapes
([ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/)).

There are two contracts, versioned apart:

| Contract | What it is | Release tag | Version carrier |
|---|---|---|---|
| Charter contract | The service's OpenAPI 3.1 API, its scenarios and the activation document schema | `contract-charter-v<version>` | `info.version` of `contracts/charter/openapi.yaml` |
| Packs contract | `file-kinds.yaml`, the pack JSON Schemas and the classifier conformance cases | `contract-packs-v<version>` | The top heading of `contracts/packs/CHANGELOG.md` |

## Every service release publishes its contracts

A service release (`service-v<version>`), a pre-release too, is published only when both contracts it bundles are
released, with their assets (#650). The charter contract is released with every service release, even when nothing
in it changed; the packs contract only when it changed. The release gate refuses the tag otherwise
([Release a service version](/guides/service-build/#release-a-service-version)). So, from the next service release on,
the contract a running service serves is one you can download, verify and pin, and the service release notes name both
contract releases, for example:

> It implements the charter contract release candidate 0.2.0-rc.3 (contract-charter-v0.2.0-rc.3) and the packs
> contract release candidate 0.1.0-rc.2 (contract-packs-v0.1.0-rc.2).

The releases before this rule differ: `service-v0.1.0-rc.1` bundles the charter contract `0.2.0-rc.2`, and
`service-v0.1.0-rc.3` bundles `0.2.0-SNAPSHOT`, whose content is that of `0.2.0-rc.3` but whose bytes match no release.

## Where the charter contract is published

The same contract is published in three places. Pin the release; use the other two to look at it or to check what a
running service answers.

1. **A GitHub release, `contract-charter-v<version>`** (the reference to pin). Each release, a candidate or final,
   attaches these assets at
   `https://github.com/spec-kitty/spec-kitty-charter/releases/download/contract-charter-v<version>/`:

   | Asset | Holds |
   |---|---|
   | `charter-openapi.yaml` | The single-file contract: its components kept, every `$ref` pointing inside the file |
   | `charter-openapi.yaml.sha256` | Its SHA-256 |
   | `charter-scenarios.tar.gz` | The contract scenarios, the conformance corpus |
   | `ActivationConfig.schema.json` | The JSON Schema of the activation document |

   Download and verify it:

   ```bash
   version=0.2.0-rc.3
   base="https://github.com/spec-kitty/spec-kitty-charter/releases/download/contract-charter-v$version"
   curl -fsSLO "$base/charter-openapi.yaml"
   curl -fsSLO "$base/charter-openapi.yaml.sha256"
   sha256sum --check charter-openapi.yaml.sha256
   ```

   A checksum served beside its file proves the download is whole, not where it came from. Record the SHA-256 with
   your pin and compare it on every later download.

2. **A running service, `GET /api/v1/openapi.yaml`.** The service answers the contract it was built with
   (`contracts/charter/openapi.yaml`, operation `getOpenApiDocument`). Like every operation it needs the worktree
   token. Its `ETag` is `"sha256:<digest>"`, the SHA-256 of the body, so you can check that a service answers the
   release you pinned without downloading it twice.
3. **The docsite**, which renders the snapshot of `main` as [the API reference](/reference/api/main/), with the raw
   file and its `.sha256` beside it. It shows the next version while it is being built (`info.version` ends in
   `-SNAPSHOT`), so read it, but never pin it.

[Generate your own client](/guides/generate-a-client/#step-1-get-the-contract-and-verify-it) has the commands for each
source.

## Which version to pin

Pin one `contract-charter-v<version>` release, the newest one your client is built for. The releases are listed at
`https://github.com/spec-kitty/spec-kitty-charter/releases?q=contract-charter-v`.

- **Only release candidates exist so far** (operator ruling R1, 2026-11-16): `0.2.0-rc.1` to `0.2.0-rc.3`, published
  as GitHub pre-releases, never marked latest. Pin the newest candidate, and move to the final `0.2.0` once it is
  released.
- **The 0.x rules** ([ADR-0022](/adr/0022-contract-versions-start-at-0-x/),
  [Versioning and compatibility](/api/compatibility/)): while the major is 0, a minor may break and a patch may not.
  Each break is listed under `### Breaking` in the contract `CHANGELOG.md`, and a candidate may break the one before
  it. Read every CHANGELOG section between your pin and the new one, `### Breaking` included, before you raise your
  pin, and regenerate your client in the same change.
- **Generate tolerantly.** Under 0.x a minor may bring a new enum value or a new member in a response, and lists it
  under `### Breaking`, since response objects are closed. Generate with the options that tolerate both (below), so
  your client survives the change until you regenerate.
- **The URL prefix `/api/v1`** stays through 0.x and at 1.0.0.

## Generate a client

Follow [Generate your own client](/guides/generate-a-client/): it fetches the pinned release, verifies it, and generates
a Java or a Python client. The pins and options the guide uses; they match this repository's own Java client
(`client-java/`) on everything that affects decoding:

- **Generator:** openapi-generator **7.26.0** (`openapi-generator` in `gradle/libs.versions.toml`), the
  `openapi-generator-cli-7.26.0.jar` from Maven Central, checked against its SHA-1.
- **Java:** `-g java --library okhttp-gson` with
  `enumUnknownDefaultCase=true,disallowAdditionalPropertiesIfNotPresent=false,openApiNullable=false,containerDefaultToNull=true,dateLibrary=java8,hideGenerationTimestamp=true,useJakartaEe=true`,
  over a copy of the contract whose closed objects are opened.
- **Python:** `-g python` with `enumUnknownDefaultCase=true,disallowAdditionalPropertiesIfNotPresent=false,hideGenerationTimestamp=true`,
  and the enum template that maps an unknown value to `UNKNOWN_DEFAULT_OPEN_API`.

The guide's [options table](/guides/generate-a-client/#the-options-and-why) says why each is set.

## Core writes declared ids through its generated client

Operator ruling, 2026-11-25 (#485): *"Core will be made to write id's through the canonical client."*

- Core calls the service through the client it generates from the published charter contract, as above. It does not
  hand-write requests or translate shapes on the side.
- Core writes the **declared id** of each artifact: `DIRECTIVE_034`, `NO_FULL_HEAVY_SUITES_IN_MISSION`. A file slug,
  `NNN-title-slug` such as `034-test-first-development`, is not an id. The service does not translate it, and an
  activation that names one is refused `422 activation_invalid` with an `unknown_artifact` violation for it.
- The activation write normalizes only two spellings of a declared id: its full URN and a different letter case
  ([ADR-0020](/adr/0020-offering-and-activations/) D4).

So core maps a slug to its id where it reads the slug, from the pack the slug came from, before it calls the service.

## For pack authors: the packs contract and pack-doctor

Pack authors and spec-kitty-open-packs validate against the **packs contract**, not the charter contract.

- **Pin a `contract-packs-v<version>` release**: `packs-contract.tar.gz`, with `packs-contract.tar.gz.sha256` for the
  archive and `packs-contract.sha256` for each file in it. [Pin the packs contract](/guides/pin-the-packs-contract/)
  has the commands, and how to run the classifier conformance cases against your own reader. Only candidates exist so
  far (`0.1.0-rc.1`, `0.1.0-rc.2`).
- **Check packs with `pack-doctor`**, the pack checker attached to each service release, from the next one on, as
  `pack-doctor-<version>.jar` (Java 25, no service needed); `service-v0.1.0-rc.3` carries it under its old name,
  `pack-check-0.1.0-rc.3.jar`. It runs the checks a registration runs, in the same codes and words, against the packs
  contract that service release bundles. [Check packs](/guides/check-packs/) shows how to download, verify and run it.
- **Align to the latest charter.** spec-kitty-open-packs checks its packs with the `pack-doctor` CLI and aligns to the
  latest charter, `main`, without waiting for a release (operator ruling, 2026-11-25, #549). A pack author outside it
  pins a release, as above. When the charter's model changes, open-packs follows it:
  the charter keeps no reader of an old shape beside the new one.

The service release notes name the packs contract its `pack-doctor` checks against, so pinning the service release
pins the packs contract too.
