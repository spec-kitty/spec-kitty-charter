# Implementation Plan: Serve the contract document

**Branch**: `feat/209-serve-openapi-document` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/serve-openapi-document-01M64FVZ/spec.md`

## Summary

`:contracts` publishes the `bundleContract` output (bundled and validated by oasdiff) as a variant;
`:deployable` resolves it and packages it as a classpath resource. A deployable configuration loads that
resource once at startup into a `ContractDocument` (bytes, strict UTF-8 text, strong ETag
`"sha256:<hex>"`) and fails the start when it is missing. `InfoController.getOpenApiDocument` stops throwing
501 and answers from it: `304` with the ETag when `If-None-Match` matches, else `200` `application/yaml` with
the document and the ETag. The guard filter, the Charter headers filter and the Problem handling are untouched
and apply as to every operation.

## Technical Context

**Language/Version**: Java 25 (toolchain; `-Pcharter.javaVersion=21` only locally), Kotlin DSL Gradle 9.8
**Primary Dependencies**: Spring Boot 4 / Spring Framework 7 (webmvc); no new dependency
**Storage**: a classpath resource in the deployable jar; no file system read at request time
**Testing**: JUnit 5 + AssertJ; `@SpringBootTest` + MockMvc in `:deployable`; plain unit tests in `:packs`
**Target Platform**: the charter service, local (loopback) and hosted (ADR-0034)
**Project Type**: single (Gradle multi-module service)
**Performance Goals**: digest computed once per start (NFR-002); a request copies no bytes beyond the response write
**Constraints**: no contract change (C-001); configuration-cache safe cross-project wiring through a published
variant, never another project's task or source set (the convention `service/charter-reports` states);
ArchUnit gate unchanged (C-003)
**Scale/Scope**: about 4 production files, 2 build scripts, 3 test classes, 5 docs touches

## Charter Check

- **Contract honored, no break** (charter "What must never go wrong" 2): the operation is already in the
  contract; this mission makes the service answer what it declares. No contract file changes except a CHANGELOG
  note. PASS.
- **Supply chain**: no new dependency. `:deployable` now depends on `:contracts:bundleContract`, which needs the
  pinned, SHA-256 verified vacuum and oasdiff binaries; every build that packages the deployable already runs
  them through `verifyAll`. PASS (risk recorded below).
- **Adversarial squad at plan point** (architecture change: a new cross-project variant): run before tasks;
  dispositions in `research.md`. 
- **Test first, outcome assertions**: acceptance tests assert exact bytes, headers and empty bodies (NFR-001).
- **Designing during the rewrite**: nothing depends on core. PASS.

## Project Structure

### Documentation (this mission)

```
kitty-specs/serve-openapi-document-01M64FVZ/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md        (from /spec-kitty.tasks)
```

### Source Code (repository root)

```
contracts/build.gradle.kts                       # + consumable variant `contractBundle` carrying the bundle
service/deployable/build.gradle.kts              # + resolvable variant; processResources; test system property
service/deployable/src/main/java/ai/speckitty/charter/deployable/
└── ContractDocumentWiring.java                  # loads the packaged bundle once; fails the start if absent
service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/
├── ContractDocument.java                        # bytes, text, ETag, If-None-Match weak match
└── InfoController.java                          # getOpenApiDocument answers 200/304
service/packs/src/test/java/.../infra/api/ContractDocumentTest.java
service/deployable/src/test/java/ai/speckitty/charter/deployable/
├── OpenApiDocumentTest.java                     # acceptance: 200 bytes, 401, 403, 304, *, 406
└── ContractDocumentWiringTest.java              # fail closed on a missing resource
docs/…, contracts/charter/CHANGELOG.md           # interim 501 wording retired; changelog entry
```

**Structure Decision**: the transport value (`ContractDocument`) lives in `packs.infra.api` beside its only
user, like `ContractVersion`; loading the resource is composition, so it lives in `deployable`, which already
builds the guard (`GuardWiring`) the same way. No new port: the document is a transport artefact, not domain
data, and ADR-0006's `*Repository`/`*Gateway` placement rules do not apply.

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — Package the build output

- **Purpose**: the deployable carries exactly the validated bundle `bundleContract` writes (FR-001).
- **Relevant requirements**: FR-001, C-001, C-003
- **Affected surfaces**: `contracts/build.gradle.kts`, `service/deployable/build.gradle.kts`
- **Sequencing/depends-on**: none
- **Risks**: the variant must not collide with `:contracts`' Java variants (own `Category` value); tool
  download now precedes every deployable build (already true for `verifyAll`).

### IC-02 — Serve with ETag and 304

- **Purpose**: answer the document, its ETag, and 304 on a weak match; fail closed at startup (FR-002–FR-006).
- **Relevant requirements**: FR-002, FR-003, FR-004, FR-005, FR-006, NFR-001, NFR-002
- **Affected surfaces**: `ContractDocument`, `InfoController`, `ContractDocumentWiring`, tests
- **Sequencing/depends-on**: IC-01
- **Risks**: Spring's `HttpEntityMethodProcessor` runs its own conditional check on a GET 200 with an ETag;
  the service's own parser must agree with it or the test pins where they differ. `StringHttpMessageConverter`
  must not re-encode or add a `charset` (set the content type explicitly; body is strict UTF-8 text).

### IC-03 — Retire the interim wording

- **Purpose**: docs and contract CHANGELOG say what is now true; a docs changelog entry (FR-007).
- **Relevant requirements**: FR-007
- **Affected surfaces**: `docs/development/http-status-codes.md`, `docs/adr/0007-*.md` (dated note),
  `contracts/charter/CHANGELOG.md`, `docs/changelog/` + hub, `InfoController` Javadoc
- **Sequencing/depends-on**: IC-02
- **Risks**: docs checks (`check_docs.py`, markdownlint, cspell).
