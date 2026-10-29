---
work_package_id: WP07
title: Guide over HTTP
dependencies:
- WP06
- WP08
requirement_refs:
- FR-010
- FR-011
- NFR-004
- NFR-005
- C-001
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
subtasks:
- T038
- T039
- T040
- T041
- T042
- T043
phase: PR-A2 - HTTP
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/
create_intent: []
execution_mode: code_change
owned_files:
- contracts/charter/schemas/ToolguideRegistration.yaml
- contracts/charter/schemas/Toolguide.yaml
- contracts/charter/paths/packs_packId_toolguides.yaml
- contracts/charter/paths/packs_packId_toolguides_toolguideId.yaml
- contracts/charter/scenarios/toolguides.feature
- contracts/charter/scenarios/journeys/journey-05-remove-python.feature
- contracts/charter/CHANGELOG.md
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ToolguideController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ToolguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ToolguideReadService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ToolguideControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ToolguideRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ToolguideReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositeComponentResolverTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackAggregateTest.java
- service/deployable/src/test/**/StubGuideFetcher*.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP07 — Guide over HTTP

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

The authorized HTTP contract change: toolguide registration carries exactly one of `guide` (Markdown) or `guideUri`
(fetched through WP08's port); the toolguide response carries `guide`; `guidePath` leaves the API. Breaking on the
0.x line (ADR-0022) with a `### Breaking` CHANGELOG entry.

## Branch Strategy

Planning branch and merge target `feat/194a-procedure-antipattern-toolguide`; lanes from `lanes.json`.
`spec-kitty agent action implement WP07 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
**PR-A2.**

## Context

- Spec US5, FR-010/011, NFR-004/005, C-001; `contracts/toolguide-registration.delta.yaml` in the mission dir;
  operator decisions on #194 (2026-10-28 and 2026-10-29).
- Contract workflow (`docs/guides/service-build.md` "Change the contract"): edit split files, list new files in
  `_index.yaml`, scenario first, CHANGELOG entry, `./gradlew :contracts:check`, then `verifyAll`. Release rules:
  `contracts/lint/README.md` "The released baseline" (0.1.0-SNAPSHOT, no released tag → no baseline).
- Contract scenarios run as Cucumber in `service/deployable` (`CharterContractSuite`).
- Existing uses of `guidePath`: `toolguides.feature` (~12 request lines), journey-05, `ToolguideControllerTest`,
  `ToolguideRegistrationServiceTest`, `ToolguideReadServiceTest`, `CompositeComponentResolverTest`,
  `PackCommitStoreTest`, `PackAggregateTest`.

## Subtasks

### T038 — Contract

- `ToolguideRegistration.yaml`: remove `guidePath` from properties and `required`; add `guide` (string) and
  `guideUri` (string, `format: uri`, `pattern: '^https://'`); express exactly-one with `oneOf` of two `required`
  branches. **Spike first**: run contract generation (`./gradlew :contracts:check` and the DTO generation task the
  build uses) and inspect what the generator emits for `oneOf`; if it cannot express it, keep the schema keyword for
  documentation and rely on the controller check (T039) — record the outcome in the CHANGELOG entry.
- `Toolguide.yaml` (response): replace `guidePath` with required `guide` (string, the stored Markdown with its H1).
- Paths: add the refusal responses the change introduces (422 for an invalid guide or refused fetch, 409 for a
  companion conflict) using existing response components where they exist.
- `contracts/charter/CHANGELOG.md`: `### Breaking` entry naming both schemas, the removed field, the new fields, the
  new refusals, and the fetch posture (direct, allowlist, limits; proxy-only deployments must send content).

### T039 — Controller

- Exactly one of `guide`/`guideUri`: both, neither, null, or empty string → 422 with a refusal naming the rule,
  regardless of what the generated DTO enforces.
- Inline `guide` over 1 MiB → refused before parsing (check the decoded length; also ensure the JSON body limit allows
  slightly more than 1 MiB so the refusal is typed, not a container error).
- DTO ↔ domain mapping: `guide` text ↔ `Guide` via `GuideMarkdown` (WP06) with the toolguide title; the response
  writes the H1.

### T040 — Service

- `ToolguideRegistrationService`: when `guideUri` is given, call `GuideFetcher.fetch` **before** entering the pack's
  write lock (`writeStore.exclusively` or equivalent) — never hold the lock during the fetch; parse and validate the
  fetched text exactly like inline content; then register under the lock. A PUT with a `guideUri` fetches again.
- `ToolguideReadService` returns the guide content.

### T041 — Refusal codes

Map `GuideFetchRefusal.Reason` and guide parse/structure refusals to refusal codes: reuse existing codes where the
meaning fits (check `contracts/_shared` refusal vocabulary and `CharterRefusalCode`); any new code is added to the
contract, the CHANGELOG and `ReservedRefusalCodesTest`. Never return `pack_unreadable`/500 for a client-caused
problem.

### T042 — Scenarios

Rewrite `toolguides.feature` and journey-05 to register with `guide` content, add scenarios for `guideUri` (via a
stub `GuideFetcher` bean in `service/deployable` tests — `StubGuideFetcher`), both/neither/empty, oversized inline
guide, invalid guide (missing overview), refused fetch (stub raises `HOST_NOT_ALLOWED`), and read-back of `guide`.

### T043 — Java tests [P]

Update the listed Java tests from `guidePath` to `guide`; add controller tests for the exactly-one matrix and the size
cap; add a service test proving the fetch happens outside the write lock (e.g. a fetcher stub that asserts the lock is
not held).

## Definition of Done

- `./gradlew :contracts:check :packs:test :deployable:test` green; `verifyAll` once before review.
- CHANGELOG `### Breaking` entry present; scenarios cover US5 AS1–AS5 (AS5 via the stub; the full fetch matrix lives in
  WP08).
- Mark T038–T043 done.

## Risks / reviewer guidance

- Reviewer: no `guidePath` left in the API; exactly-one enforced in code; fetch outside the lock; client errors are 4xx.
