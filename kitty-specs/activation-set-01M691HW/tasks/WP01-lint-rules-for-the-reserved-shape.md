---
work_package_id: WP01
title: Lint rules for the reserved shape
dependencies: []
requirement_refs:
- FR-012
- FR-013
- SC-004
- NFR-002
planning_base_branch: issue-125-activation-set
merge_target_branch: issue-125-activation-set
branch_strategy: Planning artifacts for this mission were generated on issue-125-activation-set. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-125-activation-set unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-set-01M691HW
base_commit: d9976249315b12438c2476ccf4d0d752da77dee5
created_at: '2026-10-31T13:32:35.342112+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: Phase 1 - Red first
agent: claude
history:
- at: '2026-10-31T13:45:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: implementer-ivan
authoritative_surface: contracts/lint/
create_intent:
- contracts/lint/fixtures/x-reserved-shape.yaml
- contracts/lint/fixtures/x-reserved-shape.empty.yaml
- contracts/lint/fixtures/reserved-operation-declares-501.yaml
- contracts/lint/fixtures/built-operation-declares-no-501.yaml
- contracts/lint/fixtures/no-content-has-no-body.yaml
execution_mode: code_change
model: ''
owned_files:
- contracts/lint/ruleset.yaml
- contracts/lint/README.md
- contracts/lint/fixtures/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Lint rules for the reserved shape

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `implementer-ivan`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Make a half-reserved or half-built operation a lint error, and admit `204` as a success with no
body (ADR-0020 D8, D9; FR-012, FR-013; SC-004). Red first: commit the planted fixtures while
`lintFixtures` fails, then add the rules. Read `contracts/lint/README.md` (fixture rules) and
`research.md` squad S10 before starting.

## Branch Strategy

Planning base and merge target: `issue-125-activation-set`. Execution worktrees are allocated per
lane from `lanes.json`. Never push to `main`.

## Subtasks

### T001 — Positive controls in `clean.yaml`

- Add to `contracts/lint/fixtures/clean.yaml` (inline, no `$ref`, the bundled shape):
  - one reserved operation, e.g. `PUT /reserved-things/{thingId}` with `x-reserved:
    {contractSet: activation, issue: "#125"}`, a `200`, a `501` whose content is
    `application/problem+json` with the Problem schema, and the three Charter headers on every
    response (reuse the file's YAML anchors);
  - one `DELETE` answering `204` with the Charter headers and **no** `content`.
- Every existing rule must still pass on `clean.yaml`; this makes the new rules non-vacuous.

### T002 — Planted fixtures (red)

Each is a copy of `clean.yaml` with exactly one element removed or changed and a first-line
comment naming the rule; each must fail exactly that rule:
- `reserved-operation-declares-501.yaml`: the reserved operation without its `501`.
- `built-operation-declares-no-501.yaml`: `x-reserved` removed, the `501` kept.
- `x-reserved-shape.yaml`: `issue: "125"` (no `#`). `x-reserved-shape.empty.yaml`:
  `x-reserved: {}` (JSONPath truthiness on an empty object varies; this pins it).
- `no-content-has-no-body.yaml`: the `204` with a `content`.
- The amended `error-responses-are-problems` has no new failing fixture: the `204` on
  `clean.yaml` is its positive control (T001).
- Run `./gradlew :contracts:lintFixtures`: it must fail, naming the new fixtures. Commit this
  red state (message says "red").

### T003 — The three rules

In `contracts/lint/ruleset.yaml`, core functions only:
- `x-reserved-shape`: given `$..['x-reserved']`, `schema` with
  `forceValidationOnCurrentNode`: object, `additionalProperties: false`,
  `required: [contractSet, issue]`, `contractSet: {type: string, pattern: '^[a-z][a-z0-9_-]*$'}`,
  `issue: {type: string, pattern: '^#[0-9]+$'}`.
- `reserved-operation-declares-501`: given operations
  `$.paths[*][?(@.responses)]`, `schema`: `if: {required: [x-reserved]}` then
  `properties: {responses: {required: ['501']}}`.
- `built-operation-declares-no-501`: same `given`; `if: {not: {required: [x-reserved]}}`
  then `properties: {responses: {not: {required: ['501']}}}`.
- Descriptions cite ADR-0020 D8. Severity `error`. Mirror the comment style of the file.
- Check `LintContract`'s rule count (`contracts/build.gradle.kts` ~330-347) still matches.

### T004 — 204 is a success

- `error-responses-are-problems`: add `&& @property != '204'` to `given`; update the comment
  (ADR-0020 D9 admits 201 and 204; ADR-0018 D10's "200 only" superseded).
- New `no-content-has-no-body`: given `$.paths[*][*].responses['204']`, `field: content`,
  `function: falsy` (as `not-modified-has-no-body`).

### T005 — README

Add the four rules to the table in `contracts/lint/README.md` (~114-128) and note the amended
rule. Keep the house style (short sentences, no em dashes).

## Definition of Done

- `./gradlew :contracts:lintFixtures :contracts:lintContract` green; each new fixture fails
  exactly its rule; `clean.yaml` passes every rule.
- Evidence in the WP history: the red run before T003/T004 and the green run after.
- No suppressions; no other rule changed.

## Risks

- vacuum's JSONPath dialect: if `?(@.responses)` misbehaves on `parameters` siblings, test
  with the fixtures, not by reading.

## Reviewer guidance

Check each fixture differs from `clean.yaml` by one element and that the rule count matches.

`spec-kitty agent action implement WP01 --agent claude --mission activation-set-01M691HW`
