---
work_package_id: WP05
title: GET /packs/{packId}/doc and the doc link
dependencies:
- WP01
- WP04
requirement_refs:
- FR-007
- FR-008
- FR-018
- SC-003
planning_base_branch: feat/466-slice-b-real-packs-register
merge_target_branch: feat/466-slice-b-real-packs-register
branch_strategy: Planning artifacts for this mission were generated on feat/466-slice-b-real-packs-register. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/466-slice-b-real-packs-register unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-b-real-packs-register-01M7649C
base_commit: 6810b489855d2810c8764d4c2d8508a8826a98e6
created_at: '2026-11-12T01:17:01.933050+00:00'
subtasks:
- T022
- T023
- T024
- T025
- T026
- T027
phase: Phase 3 - The doc read
history:
- at: '2026-11-11T21:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- contracts/charter/paths/packs_packId_doc.yaml
- contracts/charter/schemas/PackDocumentRead.yaml
- contracts/charter/responses/PackDocumentNotFound.yaml
- contracts/charter/examples/CharterRefusal.pack_document_not_found.yaml
- contracts/charter/scenarios/pack-document.feature
- contracts/charter/changes/2026-11-11-pack-document-read.md
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackDocumentRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackDocumentText.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDocumentFiles.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackDocumentQuery.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackDocumentQueryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDocumentFilesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackDocumentControllerTest.java
execution_mode: code_change
model: sonnet
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/paths/packs_packId.yaml
- contracts/charter/paths/packs_packId_doc.yaml
- contracts/charter/schemas/PackDocumentRead.yaml
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/responses/PackDocumentNotFound.yaml
- contracts/charter/examples/PackDocumentRead*.yaml
- contracts/charter/examples/CharterRefusal.pack_document_not_found.yaml
- contracts/charter/examples/PackRegistration.stored.yaml
- contracts/lint/ruleset.yaml
- contracts/charter/scenarios/pack-document.feature
- contracts/charter/scenarios/README.md
- contracts/charter/changes/2026-11-11-pack-document-read.md
- docs/development/hypermedia-hateoas.md
- docs/api/service-contract.md
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/RefusalHandler.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackDocumentRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackDocumentText.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDocumentFiles.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackDocumentQuery.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackSummaryQuery.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/Relation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackDocumentQueryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDocumentFilesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackDocumentControllerTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/CharterSteps.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP05 - GET /packs/{packId}/doc and the doc link

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` (or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/a79c36318b7a506ca2ab26d74705c5e3e406004f/packs/built-in/agent_profiles/`) and adopt
it. Then read `kitty-specs/slice-b-real-packs-register-01M7649C/research/doc-read-spike.md` (WP01's decision): **follow
its pattern**; if you cannot, stop and report.

## Objective

Serve the pack document (operator ruling on #451): `GET /api/v1/packs/{packId}/doc`.

| Request | Answer |
|---|---|
| `Accept: text/markdown` (or `text/*`, or Markdown wins by quality) | `200`, `text/markdown;charset=UTF-8`, the held `README.md` text |
| `Accept: application/json`, `application/*`, `*/*`, or none | `200`, `PackDocumentRead` `{title?, body, _links: {self, pack}}` |
| `If-None-Match` matching | `304` |
| no document | `404 pack_document_not_found` |
| unknown or built-in pack | `404 pack_not_found` |
| `Accept` matching neither | `406` Problem, no code, with the shared headers |

Every `200` and `304` carries `ETag` = the pack revision (as `getPack`), `Vary: Accept`, `Charter-Source-Fingerprint`
(200), and the shared headers. The pack resource carries `doc` iff the pack has a document. Requirements: FR-006,
FR-007, FR-008, FR-018 (Added), FR-024.

## Context

`research.md` D4 to D6, D9, §C (all file:line); `research/squad-plan-point.md` A3, A4, A7, A11, A12 to A14;
`data-model.md` ("Pack document", "Refusal codes", "Link relation"); `contracts/pack-doc.openapi.yaml` (sketch).
**Coordination:** `CharterRefusalCode.yaml` and `RefusalCode.java` are shared with the #496 session; add one entry, in
enum order, nothing else.

## Subtasks

### T022 - Contract

- `paths/packs_packId_doc.yaml` + entry in `openapi.yaml` next to `/packs/{packId}` (:85-89). Operation
  `getPackDocument`, tag `Registration`, `If-None-Match` parameter (reuse `parameters/IfNoneMatch.yaml`), responses as
  the table above, using WP01's shape. `400/401/403/500/default` as `getPack`.
- `schemas/PackDocumentRead.yaml`: closed; `title` (string, minLength 1, "the document's H1; omitted when it has none"),
  `body` (string, "the Markdown below the H1 in canonical form; the frontmatter is only in the text/markdown form"),
  `_links` (`Links.yaml`, self and pack); `x-derived`/`x-source` as `PackRegistration.yaml:111+`.
- `CharterRefusalCode.yaml`: enum + `x-refusal` entry `pack_document_not_found` (404, not retryable, raised: true,
  operations: getPackDocument). `responses/PackDocumentNotFound.yaml` modelled on `PackNotFound.yaml`; refusal example
  `examples/CharterRefusal.pack_document_not_found.yaml` (checkRefusalExamples).
- Examples for both 200 forms (JSON with `_links`, lint `success-responses-have-link-examples`).
- `paths/packs_packId.yaml:134-136`: the `_links` description lists `doc` (when the pack has a document);
  `examples/PackRegistration.stored.yaml:9-14` adds `doc`.
- `contracts/lint/ruleset.yaml:270-310`: add `doc` to the known relations. If `fingerprint-on-verified-reads` or the
  headers rule needs a new exclusion, stop and report; the doc read is a verified read and must declare the fingerprint.
- Fragment `contracts/charter/changes/2026-11-11-pack-document-read.md`, one `### Added` block: the operation, the code,
  the `doc` relation, `Vary`/304/406; core's seam: nothing; additive.

### T023 - Domain port and storage

- `domain/PackDocumentText.java`: record `(String markdown, Optional<String> title, String body)`.
- `domain/PackDocumentRepository.java`: `Optional<PackDocumentText> document(PackIdentifier pack)`.
- `infra/storage/PackDocumentFiles.java` (`@Repository`/as siblings are wired): resolve the pack directory the way
  `DomainRepositoryService` does (reuse its resolution; do not add a fourth `resolve()` copy, see #495/#184), read
  `README.md` through `PackDocumentMarkdown.read`, `markdown` = the file's text, `title` = the model's H1, `body` = the
  codec's canonical rendering of preamble and sections without frontmatter and title. A refused README (CR, BOM,
  ambiguous heading) surfaces as the store does today (`pack_unreadable`), never normalized.
- `PackDocumentFilesTest`: present, absent, no H1, frontmatter excluded from `body`, refused README.
- ArchUnit (`HexagonalArchitectureTest`): port in domain, implementation in `infra.storage`.

### T024 - Application query

`application/PackDocumentQuery.java` (`@Service`): resolve the pack like `PackSummaryQuery.java:85-126` (built-in →
`pack_not_found`); read document and revision in one consistent read as `ProcedureReadService.java:58-63,94-99`
(`PACK_UNREADABLE` while resolving → `PACK_NOT_FOUND` exactly as there). Absent document → `CharterRefusal`
`PACK_DOCUMENT_NOT_FOUND`. `RefusalCode.java`: add the code and its `notFound()` membership;
`RefusalHandler.java:324-386`: the 404 arm. `PackDocumentQueryTest`.

### T025 - Controller

In `PackRegistrationController` (tag `Registration`), per WP01's pattern: negotiation by quality then specificity, ties
to JSON (RFC 9110 §12.5.1); `ETag` = revision token; `Vary: Accept`; 304 on a matching `If-None-Match`
(`ContractDocument.java:64-80` has a matcher; reuse, do not copy); 406 through `ProblemHandler`.
`PackDocumentControllerTest` (`@WebMvcTest` or the module's style): every row of the table, q-values
(`text/markdown;q=0.5, application/json` → JSON), `text/*` → Markdown, headers on 200/304/406.

### T026 - The `doc` link

`Relation.java`: `DOC("doc")`. `PackSummaryQuery` reports whether the pack has a document (through the port);
`PackRegistrationMapper.java:129-145` adds `doc` → `/packs/{packId}/doc` only then (also in a registration's stored
answer, :98). `docs/development/hypermedia-hateoas.md:114-136`: a `doc` row (why not IANA `describedby`: the ruling
names `doc`), and the `pack` row now also appears on the doc read. `RelationSetParityTest` must stay green.
`docs/api/service-contract.md:283`: the operations table.

### T027 - Contract scenarios

`contracts/charter/scenarios/pack-document.feature` (`@contract`), over charter's example packs: Markdown read (body
starts with the README's H1, `ETag` equals the pack's `/revision`, `Vary: Accept`), JSON read (title, body, links
resolve), 304, 404 `pack_document_not_found` (the `project` example pack has no README), 404 `pack_not_found`, 406,
`*/*` → JSON, `doc` link present on a pack with a document and absent on one without. A raw-text body step is new:
add it to `CharterSteps.java` and document it in `scenarios/README.md`. `OperationCoverage` must see both 200s and the
304 exercised.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :contracts:check
./gradlew --max-workers=1 :packs:test :web-transport:test
./gradlew --max-workers=1 :deployable:test
```

## Branch Strategy

Planning base and merge target: `feat/466-slice-b-real-packs-register`; worktrees per lane. Implement with
`spec-kitty agent action implement WP05 --agent claude --mission slice-b-real-packs-register-01M7649C`. Commits signed,
no model trailers, refs `(#451)`.

## Definition of Done

All rows of the table proven by scenarios; lint, closed-object, refusal-example, compatibility and fragment checks
green; ArchUnit green.

## Risks and reviewer guidance

Spring's implicit 304 must not answer for a request the contract does not declare. Check the link is absent when the
document is. Check the shared refusal-code edit is one entry.
