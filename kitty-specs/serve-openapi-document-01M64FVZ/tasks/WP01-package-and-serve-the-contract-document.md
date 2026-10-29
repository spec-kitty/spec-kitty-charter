---
work_package_id: WP01
title: Package and serve the contract document
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- NFR-001
- NFR-002
- NFR-003
- C-001
- C-003
- SC-001
- SC-002
- SC-003
planning_base_branch: feat/209-serve-openapi-document
merge_target_branch: feat/209-serve-openapi-document
branch_strategy: Planning artifacts for this mission were generated on feat/209-serve-openapi-document. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/209-serve-openapi-document unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-serve-openapi-document-01M64FVZ
base_commit: 7489a58be62ccd7f8d8aea06729fbb0d8c10645e
created_at: '2026-10-29T19:00:59.323516+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
- T007
phase: Phase 1 - Serve
history:
- at: '2026-10-29T19:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: implementer-ivan
authoritative_surface: service/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ContractDocument.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ContractDocumentETag.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ContractDocumentTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContractDocumentWiring.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContractDocumentWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/OpenApiDocumentTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/build.gradle.kts
- service/deployable/build.gradle.kts
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ContractDocument.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ContractDocumentETag.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/InfoController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/config/ApiConfiguration.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ContractDocumentTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/ContractDocumentWiring.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/DeployableConfiguration.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ContractDocumentWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/OpenApiDocumentTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Package and serve the contract document

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `implementer-ivan`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Replace the interim `501` of `getOpenApiDocument` (`GET /api/v1/openapi.yaml`) with the real answer: the
packaged `:contracts:bundleContract` output, byte for byte, as `application/yaml`, with
`ETag: "sha256:<hex>"` and `304` on a matching `If-None-Match`. Issue #209. Read `spec.md`, `plan.md` and
`research.md` (R1–R6 and the squad dispositions) first; they are binding.

## Branch Strategy

Planning base and merge target: `feat/209-serve-openapi-document`. Execution worktrees are allocated per lane
from `lanes.json`; the branch lands on `main` by pull request (never push to `main`).

## Subtasks

### T001 — Publish the bundle variant (`contracts/build.gradle.kts`)

- After `bundleContract` is registered, add a consumable configuration `contractBundle` with
  `Category.CATEGORY_ATTRIBUTE` set to a custom value (`charter-contract-bundle`) so it never matches the Java
  variants, and add `bundleContract.flatMap { it.bundle }` as its artifact (the provider carries the task
  dependency). Also publish the `.sha256` checksum as a second variant or artifact only if the deployable tests
  need it through Gradle (they do: see T002).
- Comment: why a variant (configuration-cache safe; consumers never reach the task), what "validated" means
  (`oasdiff validate` in `bundleContract`).
- **Validation**: `./gradlew :contracts:outgoingVariants` lists it.

### T002 — Package it (`service/deployable/build.gradle.kts`)

- `dependencyScope` + `resolvable` configurations on `project(":contracts")` with the same attribute.
- `tasks.processResources { from(bundle) { into("ai/speckitty/charter/deployable") } }`: the resource sits
  beside `ContractDocumentWiring`, away from the test tree's `contract/` copy of the split sources.
- `tasks.test`: pass the resolved bundle file and its `.sha256` to the JVM as system properties through a
  `CommandLineArgumentProvider` with `@InputFiles`-style inputs (configuration-cache safe), so the acceptance
  test compares with the build output itself, never with the packaged copy.
- **Validation**: `unzip -l service/deployable/build/libs/*.jar | grep charter-openapi` after `bootJar`.

### T003 — `ContractDocument` (`packs.infra.api`, public final) [P]

- Constructor from `byte[]`: decode strict UTF-8 (`CharsetDecoder` with `CodingErrorAction.REPORT`), throw
  `IllegalArgumentException` on malformed input; ETag `"sha256:" + hex(MessageDigest SHA-256 of the bytes)`
  wrapped in double quotes. Never `kernel.domain.Fingerprint` (length prefixes).
- `text()`, `etag()`, `boolean matchedBy(List<String> ifNoneMatch)`: each header line is a field value; the
  combined value matches when it is exactly `*` (after trimming OWS) or is a comma-separated list of
  entity-tags (`W/` uppercase optional, `"` opaque `"` with etagc chars `%x21 / %x23-7E / obs-text`), empty
  list elements allowed per RFC 9110 §5.6.1, one of which has this document's opaque tag. Any element that is
  not an entity-tag makes the whole value match nothing.
- Unit tests (`ContractDocumentTest`): fixture bytes with non-ASCII; ETag equals an independently computed
  digest; strict decoding refuses invalid UTF-8; matcher cases: exact, `W/` weak, list with the tag second,
  two header lines, `*`, ` * `, non-matching tag, `garbage "…"`, `"…" junk`, lower-case `w/"…"`, unquoted tag,
  empty header, no header, `*, "…"` (not a valid list → no match).

### T004 — `ContractDocumentWiring` (`deployable`)

- `@Configuration(proxyBeanMethods = false)` with a `@Bean ContractDocument contractDocument()` that reads
  `charter-openapi.yaml` beside the class with `getResourceAsStream`; missing → `IllegalStateException`
  naming the resource; malformed → `IllegalStateException` wrapping the decoder's failure. Loaded once.
- A package-private static `load(Class<?>, String)` (or similar) so `ContractDocumentWiringTest` can prove
  the missing-resource refusal without a broken classpath; and a context test that the real wiring builds a
  document whose bytes equal the build output.

### T005 — Controller and advice

- `InfoController(ServiceInfoQuery, ContractDocument)`. `getOpenApiDocument(@Nullable String ifNoneMatch)`:
  the generated parameter joins header lines with commas; prefer reading every line from the request is not
  possible through the interface, so treat the joined string as one field value (commas are list separators
  anyway). Match → `ResponseEntity.status(NOT_MODIFIED).eTag(document.etag()).build()`. Else →
  `ResponseEntity.ok().contentType(MediaType.parseMediaType("application/yaml")).body(document.text())` with
  **no** ETag on the entity.
- `ContractDocumentETag implements ResponseBodyAdvice<Object>`, `@ControllerAdvice(assignableTypes =
  InfoController.class)`, `supports` only `getOpenApiDocument`; `beforeBodyWrite` adds the ETag to the response
  headers. Explain in its Javadoc why (Spring's `checkNotModified` runs before body write on every GET/HEAD 200
  with an ETag and is lenient; research R3).

### T006 — Acceptance tests (`OpenApiDocumentTest`, `@SpringBootTest` + MockMvc)

One request builder for `/api/v1/openapi.yaml` with the valid token; every refusal derives from it.

- 200: status, exact `Content-Type: application/yaml`, body bytes equal the build output file (system property
  from T002), `Content-Length` equal to the byte count, `ETag` equal to `"sha256:" + <hex from the .sha256
  file>`, `Charter-Contract-Version` equal to `info.version` parsed (snakeyaml-engine) from the build output,
  `Charter-Worktree` present, `Charter-Source-Fingerprint` absent.
- 304 (exact, `W/`, list, two lines, `*`): empty body, same ETag, Charter headers, no `Content-Type`.
- 200 for `garbage "<etag>"`, `w/"…"`, a non-matching tag (Spring would 304 the first two).
- 401 without token and with a wrong token (even with a matching `If-None-Match`): Problem, `WWW-Authenticate:
  Bearer`, no `ETag`. 403 with `Origin` and a valid token and a matching tag: Problem, no `ETag`.
- HEAD: 200 without body with ETag; HEAD with matching tag 304.
- Non-ASCII through the converter chain: a `@TestConfiguration` slice (or a second context) supplying a
  `@Primary ContractDocument` of `é—€` text proves the exact UTF-8 bytes leave the converter.

### T007 — Javadoc

- `ApiConfiguration` and `DeployableConfiguration`: name `ContractDocumentWiring` among the wiring classes
  that supply the api layer; `InfoController` class/method Javadoc: drop "501 until #209".

## Definition of Done

- All T006 cases pass and each would fail with the serving removed (the 200 and 304 assertions by content,
  the refusals alongside the 200 positive control).
- `./gradlew verifyAll` green on Java 25 (Spotless, Checkstyle, Error Prone/NullAway, doclint, ArchUnit with no
  new exemption, the Cucumber contract suite).
- No change under `contracts/charter/`.

## Risks / Reviewer guidance

- Verify Spring's own check is truly off the 200 path: the `garbage "<etag>"` → 200 case is the proof.
- Verify the bytes come from the build output (T002 property), not from the packaged resource.
- The `@After` hooks of the Cucumber suite are untouched; no scenario calls this operation (#210).

## Implementation command

```bash
spec-kitty agent action implement WP01 --agent claude --mission serve-openapi-document-01M64FVZ
```
