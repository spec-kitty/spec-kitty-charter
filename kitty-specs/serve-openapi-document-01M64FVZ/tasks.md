# Tasks: Serve the contract document

**Mission**: `serve-openapi-document-01M64FVZ` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Branch**: `feat/209-serve-openapi-document` (planning base and merge target)

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Publish the `contractBundle` variant from `:contracts` | WP01 | |
| T002 | Resolve and package the bundle in `:deployable`; hand the build output to the tests | WP01 | |
| T003 | `ContractDocument`: bytes → strict UTF-8 text, ETag, weak `If-None-Match` matcher (+ unit tests) | WP01 | [P] |
| T004 | `ContractDocumentWiring`: load once, fail closed (+ test) | WP01 | |
| T005 | `InfoController.getOpenApiDocument` answers 200/304; `ContractDocumentETag` advice | WP01 | |
| T006 | Acceptance tests over the real context (200 bytes, 304, 401, 403, HEAD, edge values, non-ASCII) | WP01 | |
| T007 | Javadoc of `ApiConfiguration` / `DeployableConfiguration` / `InfoController` | WP01 | |
| T008 | Retire the 501 interim wording (status-codes page, ADR-0007 dated note, contract CHANGELOG) | WP02 | |
| T009 | Docs changelog entry and hub | WP02 | |

## WP01 — Package and serve the contract document

**Goal**: `GET /api/v1/openapi.yaml` answers the packaged `bundleContract` output byte for byte, with the ETag and
304, guarded like every operation; the service refuses to start without the bundle.
**Priority**: P1 · **Prompt**: [tasks/WP01-package-and-serve-the-contract-document.md](tasks/WP01-package-and-serve-the-contract-document.md)
**Independent test**: `./gradlew :deployable:test --tests '*OpenApiDocument*' :packs:test --tests '*ContractDocument*'`

T001 Publish the `contractBundle` variant from `:contracts` (WP01)
T002 Resolve and package the bundle in `:deployable`; hand the build output to the tests (WP01)
T003 `ContractDocument` value and matcher with unit tests (WP01)
T004 `ContractDocumentWiring`: load once, fail closed, with test (WP01)
T005 `InfoController.getOpenApiDocument` 200/304 and `ContractDocumentETag` (WP01)
T006 Acceptance tests over the real context (WP01)
T007 Javadoc of the configuration classes and the controller (WP01)

**Dependencies**: none. **Risks**: Spring's own conditional check (kept off the 200 path, research R3); the
converter charset (research R2).

## WP02 — Docs: the document is served

**Goal**: docs and the contract CHANGELOG say what is now true; a docs changelog entry announces it.
**Priority**: P2 · **Prompt**: [tasks/WP02-docs-the-document-is-served.md](tasks/WP02-docs-the-document-is-served.md)
**Independent test**: `uv run python -m scripts.docs.check_docs all`; `npx markdownlint-cli2` over the changed pages.

T008 Retire the 501 interim wording (WP02)
T009 Docs changelog entry and hub (WP02)

**Dependencies**: WP01 (describes the shipped behaviour).
