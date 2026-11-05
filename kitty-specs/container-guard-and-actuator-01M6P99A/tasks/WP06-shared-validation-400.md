---
work_package_id: WP06
title: Shared 400 for method-validation violations
dependencies: []
requirement_refs:
- FR-018
planning_base_branch: feat/container-guard-and-actuator
merge_target_branch: feat/container-guard-and-actuator
branch_strategy: Planning artifacts for this mission were generated on feat/container-guard-and-actuator. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/container-guard-and-actuator unless the human explicitly redirects the landing branch.
subtasks:
- T021
- T022
history: []
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ProblemHandler.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ProblemHandlerTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ProblemHandler.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactController.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ProblemHandlerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArtifactControllerTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP06: Shared 400 for method-validation violations

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Branch Strategy

Planning branch and final merge target: `feat/container-guard-and-actuator`. Execution worktrees are allocated per computed lane from
`lanes.json`; do not pick a base by hand.

Implementation command:
`spec-kitty agent action implement WP06 --agent claude --mission container-guard-and-actuator-01M6P99A`

## Objective

A `ConstraintViolationException` raised by method validation on a request parameter of **any** controller
answers the plain 400 Problem (no code, no detail), as `ArtifactController` does locally today. Any other
violation stays a 500 `internal_error` (FR-018; the #268 note).

## Subtasks

### T021 — `ProblemHandler`

Add `@ExceptionHandler(ConstraintViolationException.class) public ResponseEntity<Object> invalidParameter(ConstraintViolationException e)`:

- When every violation's `getPropertyPath()` has a final node of kind `ElementKind.PARAMETER` or
  `CROSS_PARAMETER`, or there are no violations, answer `Problems.of(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY)`.
- Otherwise log and answer as `unexpected` does (500).
- The body never carries the violation text.
- Javadoc says why: the generated interfaces are `@Validated`, so the method-validation proxy raises this
  before the controller runs.
- Within one advice, the most specific handler wins over `Exception.class`.

### T022 — Remove the local copy

- Delete `ArtifactController.outOfBounds` and its now-unused imports.
- Move its unit test to a new `ProblemHandlerTest`, which checks:
  - a parameter violation gives 400 with `new Problem("Bad Request", 400)` and
    `application/problem+json`;
  - a return-value violation gives 500 `internal_error`;
  - an empty set gives 400.

  Build violations with a real `Validator` on a small annotated method (`ExecutableValidator.validateParameters`
  and `validateReturnValue`); no mocks needed.
- Keep `ArtifactControllerTest`'s behaviour assertion green by deleting only the direct-call test.
  `artifacts.feature` still pins the real path end to end.

## Definition of Done

`./gradlew :packs:test :deployable:test` passes. The `pageSize=0` scenario in `artifacts.feature` still answers
400.
