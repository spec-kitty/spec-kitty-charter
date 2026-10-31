---
work_package_id: WP02
title: 'Scenarios first: a 501 pin per operation'
dependencies: []
requirement_refs:
- FR-014
- FR-016
- SC-001
- SC-002
planning_base_branch: issue-125-activation-set
merge_target_branch: issue-125-activation-set
branch_strategy: Planning artifacts for this mission were generated on issue-125-activation-set. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-125-activation-set unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-set-01M691HW
base_commit: 53b40bd92e741eaf92699df6adeab11a2363dd2b
created_at: '2026-10-31T13:34:53.214886+00:00'
subtasks:
- T006
- T007
- T008
- T009
- T010
- T011
phase: Phase 1 - Red first
agent: claude
history:
- at: '2026-10-31T13:45:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: qa-test-designer-tessa
authoritative_surface: contracts/charter/scenarios/
create_intent:
- contracts/charter/scenarios/activations.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReservedOperationSteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReservedOperationCoverageTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/scenarios/activations.feature
- contracts/charter/scenarios/errors.feature
- contracts/charter/scenarios/README.md
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractConformance.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractConformanceTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReservedOperationSteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReservedOperationCoverageTest.java
- service/deployable/src/test/resources/conformance/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – Scenarios first: a 501 pin per operation

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `qa-test-designer-tessa`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Pin every activation operation's `501` and every new refusal code's declaration with
`@contract @reserved` scenarios **before** the contract declares them (FR-014, FR-016, SC-001,
SC-002). Make the checks impossible to pass on a missing declaration (squad S3, S6, S7, S11, S12).
Read `contracts/charter/scenarios/README.md`, `errors.feature:96-128`,
`bdd/ReadConsistencySteps.java` (~120-160), `bdd/CharterSteps.java` (~80-110) and
`bdd/ContractConformance.java` first.

## Branch Strategy

Planning base and merge target: `issue-125-activation-set`. Lanes from `lanes.json`.

## Subtasks

### T006 — `minItems` in `ContractConformance` (red first)

- Add a `ContractConformanceTest` case over a fixture contract (the test already builds
  `ContractConformance` over fixture trees) with a schema using `minItems: 1`: an empty array
  fails, a one-element array passes. Run it red (construction throws on the unknown keyword).
- Then add `minItems` to `CONSTRAINTS` and implement it beside `maxItems`; update the class
  Javadoc keyword list.

### T007 — The 501 step (`ReservedOperationSteps`)

`Then the operation is refused as not implemented`:
- the last exchange's status is `501`, content type `application/problem+json`;
- the body has **no** `code` member at all (`has("code")` is false), `status` is 501;
- `Charter-Contract-Version` and `Charter-Worktree` are present; `Charter-Source-Fingerprint`
  absent (consistent with the after-scenario hook in `CharterSteps`);
- it looks up, in `contract/charter/openapi.yaml` on the test classpath, the operation of the
  request's method and path template (match the concrete path against the templates) and requires
  that operation to declare `x-reserved` and a `501` response, **without** the `default`
  fallback. A path no operation declares fails with a message naming the path.
- Reuse the existing request steps (`When I PUT "<path>" with the body:`,
  `When I POST "<path>" as JSON with the body:`, `When I send <method> "<path>"`,
  `When I <method> "<path>" with the header "<name>": "<value>"`). Add a request step only if a
  scenario cannot be written without it (e.g. PUT with `If-Match` and a body already exists).

### T008 — `activations.feature`

`Feature: The activation set is reserved` (`@contract`), one `@reserved` scenario per
operation of `contracts/activation-operations.md`, each sending a **well-formed** request (valid
JSON body with `Content-Type: application/json` where the operation takes one; `If-Match` with a
quoted `"sha256:<64 hex>"` on sub-resource writes) and ending with
`Then the operation is refused as not implemented`. Use activation id `a-1`, kind
`directive`, artifact `DIRECTIVE_001`, preset `event-driven`, mission type `software-dev`,
action `implement`. Each scenario description names the issue that will build it (#103 for the
four effective reads, #125's follow-up build issue for the writes, see WP05 T031) and the unit test
`ReservedOperationCoverageTest`. Add one scenario where a reserved write is sent a malformed body
and still answers 501 (research D-11).

### T009 — Declaration pins (`errors.feature`)

For each of `activation_not_found`, `artifact_not_found`, `preset_not_found` (404),
`artifact_required`, `artifact_locked` (409), `activation_invalid` (422): a `@contract
@reserved` scenario with the existing steps `Then the contract declares the refusal code ...`,
`... is answered with status <n>`, `... may not be retried`, `... is raised by no operation`,
naming `ReservedRefusalCodesTest` and the build issue. Keep the "exactly these codes are raised"
table unchanged (the new codes are `raised: false`).

### T010 — README

Amend `@reserved` in `scenarios/README.md`: two forms. A *declaration pin* calls no operation
(refusal codes; unchanged wording). A *501 pin* calls a reserved operation and expects
`Then the operation is refused as not implemented`; it is removed when the operation is built.
Add the new step to the vocabulary and `activations.feature` to the features list.

### T011 — `ReservedOperationCoverageTest`

A plain JUnit test (no Spring) that reads `contract/charter/openapi.yaml` and
`scenarios/activations.feature` from the test classpath and asserts the set of (method, path
template) of `x-reserved` operations equals the set the 501 scenarios call (13 = 13). Today it
fails (0 declared vs 13 called): that is the red state.

## Definition of Done

- `ContractConformanceTest` green; the 501 scenarios and `ReservedOperationCoverageTest` fail
  red for the right reason (route missing / nothing declared), recorded in the WP history; they
  turn green in WP04.
- No `@WIP` on the new scenarios; no suppressions.

## Risks

- The after-scenario "observed refusal is raised" hook: a 501 has no `code`, so it must not trip.
- WorktreeGuard answers 401 before routing: use the default tokened steps.

`spec-kitty agent action implement WP02 --agent claude --mission activation-set-01M691HW`
