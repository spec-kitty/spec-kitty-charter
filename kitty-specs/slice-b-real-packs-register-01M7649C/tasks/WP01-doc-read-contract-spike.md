---
work_package_id: WP01
title: "Spike: the doc read's contract shape"
dependencies: []
requirement_refs:
- FR-006
- FR-024
planning_base_branch: feat/466-slice-b-real-packs-register
merge_target_branch: feat/466-slice-b-real-packs-register
branch_strategy: Planning artifacts for this mission were generated on feat/466-slice-b-real-packs-register. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/466-slice-b-real-packs-register unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
phase: Phase 1 - Foundation
history:
- at: '2026-11-11T21:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: architect-alphonso
authoritative_surface: kitty-specs/slice-b-real-packs-register-01M7649C/research/
create_intent:
- kitty-specs/slice-b-real-packs-register-01M7649C/research/doc-read-spike.md
execution_mode: planning_artifact
model: sonnet
owned_files:
- kitty-specs/slice-b-real-packs-register-01M7649C/research/doc-read-spike.md
role: architect
tags: []
tracker_refs: []
---

# Work Package Prompt: WP01 - Spike: the doc read's contract shape

## ⚡ Do This First: Load Agent Profile

Load your assigned profile before reading anything else: run `/spk-charter-profile-load architect-alphonso`, or read
`/root/.cache/spec-kitty/open-packs/a79c36318b7a506ca2ab26d74705c5e3e406004f/packs/built-in/agent_profiles/architect-alphonso.agent.yaml`
and adopt its identity, governance scope and boundaries.

## Objective

Decide, with a compiled proof, how `GET /api/v1/packs/{packId}/doc` is declared in the contract and served, before WP05
builds it. One operation answers `200` with two media types (`text/markdown` and `application/json`), `304` on a
matching `If-None-Match`, `404`, and `406`. No path in the contract has two 200 media types today (squad finding A3).
The output is a decision note; **no production code or contract change is committed by this WP**.

## Context

- Read: `kitty-specs/slice-b-real-packs-register-01M7649C/{spec.md,plan.md,research.md}` (D4, D5, D9, §C),
  `research/squad-plan-point.md` (A3, A4, A7), `contracts/pack-doc.openapi.yaml` (the sketch).
- ADR-0004 (`docs/adr/0004-public-api-returns-generated-dtos.md`): handlers return generated DTOs.
- Code generation: `build-logic/src/main/kotlin/charter.contract-api.gradle.kts:25-60` (spring generator,
  `interfaceOnly`, `useTags`, `skipDefaultInterface`). The generated client is `client-java`.
- Precedents: `contracts/charter/paths/openapi.yaml:28-54` and `service/.../InfoController.java:98-107` with
  `ContractDocumentETag.java:12-20` (a non-JSON 200, Spring's implicit 304); activation reads that declare 304
  (`contracts/charter/responses/NotModified.yaml`, `parameters/IfNoneMatch.yaml`); `getPack` in
  `contracts/charter/paths/packs_packId.yaml:130-179` and `PackRegistrationController.java:100-107`.
- Lint rules that bind the operation: `contracts/lint/ruleset.yaml` (headers on every response, fingerprint on verified
  reads, success schemas carry links, link examples, error responses are problems).

## Subtasks

### T001 - Generate the sketch and inspect the interface

1. In your lane worktree, add the operation **temporarily**: `contracts/charter/paths/packs_packId_doc.yaml`, a path
   entry in `contracts/charter/openapi.yaml`, a named schema `schemas/PackDocumentRead.yaml`
   (`{title?, body, _links}`, closed, every property described).
2. Run `./gradlew --max-workers=1 :charter-api:generateContract` (find the exact task name with
   `./gradlew tasks --all | grep -i generat`). Gradle in the foreground, one run at a time.
3. Read the generated `RegistrationApi` method for `getPackDocument`: its return type, its `produces`, how the two
   contents are typed. Do the same for the generated client in `client-java` if it builds from the same contract.
4. Run `./gradlew --max-workers=1 :contracts:check` (or the contract lint task) and record which rules the sketch trips.

### T002 - Prove a serving pattern

Try, in order, and stop at the first that compiles, passes the lint, and keeps ADR-0004:

- **(a)** The generated interface method with `ResponseEntity<Object>` or `ResponseEntity<String>`: does the generator
  give a usable type for both contents?
- **(b)** Two `@GetMapping` handlers on the same path with disjoint `produces` (`text/markdown` and `application/json`),
  one implementing the generated method and one hand-written; check Spring picks by `Accept` (q-values, `*/*`,
  `text/*`) and that an unmatched `Accept` gives `HttpMediaTypeNotAcceptableException` → the service's 406 Problem
  (`ProblemHandler.java:92-97`).
- **(c)** One handler that negotiates itself (`MediaType.sortBySpecificityAndQuality` or Spring's
  `ContentNegotiationManager`) and returns the generated DTO for JSON and a `String` for Markdown.

For the chosen pattern, write a throwaway `@WebMvcTest` (not committed) that proves: `Accept: text/markdown` → 200
`text/markdown;charset=UTF-8`; `application/json`, `*/*`, absent → JSON; `text/*` → Markdown;
`text/markdown;q=0.5, application/json` → JSON; `image/png` → 406 with the shared headers; `If-None-Match` equal to the
ETag → 304 with `ETag` and `Vary: Accept`; `Vary: Accept` on every 200.

### T003 - Record the decision

Write `kitty-specs/slice-b-real-packs-register-01M7649C/research/doc-read-spike.md`: the contract YAML you would commit
(path, schema, responses, headers), the generated signature, the chosen pattern with a code sketch of the controller
method(s), how 304 and 406 are produced, the lint results, and anything that needs the operator (an unchecked cast, a
handler outside the generated interface). Then revert every temporary file: `git status` must show only the note.

## Branch Strategy

Planning base and merge target: `feat/466-slice-b-real-packs-register`. Execution worktrees are allocated per computed
lane from `lanes.json`. Implement with:

```bash
spec-kitty agent action implement WP01 --agent claude --mission slice-b-real-packs-register-01M7649C
```

## Definition of Done

- The note exists, names one pattern, and quotes the generated signature and the throwaway test's passing assertions.
- No contract or source file is changed in the commit.

## Reviewer guidance

Check the pattern keeps ADR-0004 for the JSON form, that 304 and 406 are produced by declared means, and that the note
says what WP05 must not do.
