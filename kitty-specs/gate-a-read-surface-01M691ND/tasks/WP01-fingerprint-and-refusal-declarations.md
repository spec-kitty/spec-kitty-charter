---
work_package_id: WP01
title: 'Fingerprint and refusal declarations (#175, #176)'
dependencies: []
requirement_refs:
- FR-012
- FR-013
- FR-014
- NFR-001
- NFR-002
- NFR-003
- C-007
- SC-004
planning_base_branch: issue-126-offering-reads
merge_target_branch: issue-126-offering-reads
branch_strategy: Planning artifacts for this mission were generated on issue-126-offering-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-126-offering-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-gate-a-read-surface-01M691ND
base_commit: 4f6b643ac2de3441ea0ff1bb31b2d29dc19c319d
created_at: '2026-10-31T13:43:03.283051+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: Phase 1 - Foundation
agent: claude
history:
- at: '2026-10-31T14:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/
create_intent:
- contracts/lint/fixtures/fingerprint-on-verified-reads.yaml
- contracts/lint/fixtures/fingerprint-on-verified-reads.optional.yaml
- contracts/lint/fixtures/fingerprint-only-on-verified-reads.yaml
- contracts/lint/fixtures/fingerprint-only-on-verified-reads.write.yaml
- contracts/lint/fixtures/charter-headers-on-every-response.missing-worktree.yaml
- contracts/src/main/java/ai/speckitty/charter/contracts/refusal/RefusalExamples.java
- contracts/src/test/java/ai/speckitty/charter/contracts/refusal/RefusalExamplesTest.java
- contracts/src/test/resources/refusal/unraised-example.yaml
- contracts/src/test/resources/refusal/raised-examples.yaml
execution_mode: code_change
model: ''
owned_files:
- contracts/lint/ruleset.yaml
- contracts/lint/README.md
- contracts/lint/fixtures/**
- contracts/charter/headers/CharterSourceFingerprint.yaml
- contracts/charter/responses/**
- contracts/charter/paths/**
- contracts/_shared/responses/Problem.yaml
- contracts/_shared/VENDORED.md
- contracts/build.gradle.kts
- contracts/src/main/java/ai/speckitty/charter/contracts/refusal/**
- contracts/src/test/java/ai/speckitty/charter/contracts/refusal/**
- contracts/src/test/resources/refusal/**
- contracts/charter/CHANGELOG.md
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractDescriptionsTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProblemResponsesTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Fingerprint and refusal declarations (#175, #176)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and follow
its guidance before you read the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Make the contract declare `Charter-Source-Fingerprint` exactly where the service sends it, and lint both
directions (#175). Make `OfferingUnreadable`'s examples and description name only codes the service raises,
with a check that keeps them so (#176, its contract half; the item-read scenario is WP04/T020).

This WP adds no operation, schema or Java type to the generated API. Headers are not part of the generated
interfaces, so the service compiles unchanged.

## Context

- Spec: FR-012, FR-013, FR-014; plan IC-01; research R-08 (revised), R-22, and squad findings 14, 16 and 20.
- **Today**:
  - `headers/CharterSourceFingerprint.yaml` is `required: false` (:18).
  - Every response declares the fingerprint, because `charter-headers-on-every-response`
    (`contracts/lint/ruleset.yaml:99-113`) requires all three Charter headers on every response.
  - `_shared/responses/Problem.yaml:7-8` declares it too (`VENDORED.md` lists that change).
- **Verified reads today** (the service sets the header through `CharterHeaders.fingerprint`):
  - `getServiceInfo`;
  - `listPackTactics`, `getPackTactic`;
  - `listPackDirectives`, `getPackDirective`;
  - `listPackStyleguides`, `getPackStyleguide`;
  - `listPackParadigms`, `getPackParadigm`;
  - `listPackToolguides`, `getPackToolguide`.

  WP03 adds two more (`listPackArtifacts`, `getPackArtifact`) and WP04 removes the per-kind ones. The rule
  must therefore be written by shape, not by operationId.
- **Lint mechanics** (`contracts/lint/README.md:114-150`, `contracts/build.gradle.kts:700-720`, `:384-386`):
  - Each fixture is an inline (no `$ref`) copy of `clean.yaml` with one planted violation and a
    first-line comment.
  - Each fixture must fail **exactly** its own rule.
  - `lintContract` fails if vacuum's loaded rule count differs from the ruleset's.
  - The fixtures use the YAML anchor `&charter-headers`.
- vacuum (Spectral format) has core functions only. `@property` filters are already used in the ruleset.

## Subtasks

### T001 — Red lint fixtures for the fingerprint rules

1. Replace `charter-headers-on-every-response.missing-fingerprint.yaml`. Once the rule is narrowed it fires
   nothing, and `lintFixtures` would fail. The replacement is `.missing-worktree.yaml`, a response missing
   `Charter-Worktree`. Keep `charter-headers-on-every-response.yaml` itself.
2. Add `fingerprint-on-verified-reads.yaml`: a GET 200 of a pack path that does not declare the fingerprint.
3. Add `fingerprint-on-verified-reads.optional.yaml`: the same GET 200, declaring it with `required: false`.
4. Add `fingerprint-only-on-verified-reads.yaml`: a 404 refusal that declares it.
5. Add `fingerprint-only-on-verified-reads.write.yaml`: a POST 201 that declares it.
6. Update `clean.yaml`:
   - the fingerprint is declared `required: true` on its GET 200s;
   - it is removed from every other response;
   - it is removed from the `&charter-headers` anchor where the anchor is reused on refusals. Split the
     anchor into `&charter-headers` (the two always-on headers) and `&read-headers` (those two plus the
     required fingerprint).
7. Update every existing fixture that shares the anchors, so that it still fires only its own rule.
8. Run `./gradlew :contracts:lintFixtures` and record that the new fixtures fail **for the wrong reason or
   not at all**: no rule exists yet. This is the red.

### T002 — Ruleset changes

1. Narrow `charter-headers-on-every-response`:
   - the `required` list becomes `[Charter-Contract-Version, Charter-Worktree]`;
   - the description and the ADR comment say the fingerprint is governed by the two rules below.
2. Add `fingerprint-on-verified-reads`:
   - `given`: `$.paths[?(@property != '/' && @property != '/openapi.yaml')].get.responses['200'].headers`
   - `then`:
     - field `Charter-Source-Fingerprint`, `truthy`;
     - and `schema` asserting the header object has `required: {const: true}`.
   - Check the resolved document: the header is inlined after bundling. Lint runs on the bundle
     (`bundleContract`).
3. Add `fingerprint-only-on-verified-reads`, with `given` as a list:
   - `$.paths[*][put,post,delete,patch].responses[*].headers`
   - `$.paths[*].get.responses[?(@property != '200')].headers`
   - `$.paths['/openapi.yaml'].get.responses[*].headers`
   - `$.components.responses[*].headers`

   `then`: field `Charter-Source-Fingerprint`, `falsy`.
4. `/` is not in the contract yet. The exclusion is written now so that WP05 needs no rule change.
5. Update the rule table and the "add a rule" note in `contracts/lint/README.md`, one row per rule.
6. Run `:contracts:lintFixtures` and see that the new fixtures fail their own rule only.

### T003 — Contract declarations

1. `headers/CharterSourceFingerprint.yaml`:
   - the header itself stays a reusable definition;
   - its description says it is sent on, and required on, the 200 of every verified read, and on no other
     answer;
   - drop the sentence about being declared everywhere because of the lint rule.

   Where the `required` flag lives: the header file is `$ref`ed per response. If `required` sits in the
   header file, every reference inherits it. Make the file `required: true`, and simply stop referencing it
   from non-read responses.
2. In every `paths/*.yaml`:
   - remove the `Charter-Source-Fingerprint` header from POST, PUT and inline 409/422 responses;
   - remove it from the 304 and 200 of `paths/openapi.yaml`;
   - keep it on GET 200s.
3. In every `responses/*.yaml` (Unauthorized, Forbidden, *NotFound, OfferingUnreadable, ActionNotFound):
   remove the fingerprint header.
4. `_shared/responses/Problem.yaml`: remove the fingerprint header. In `_shared/VENDORED.md`'s change list,
   change the entry to say the shared Problem declares the two always-on Charter headers, and that the
   fingerprint is declared only on verified-read 200s (#175).
5. Run `:contracts:check`. Green is required.

### T004 — #176: refusal examples name raised codes

1. **Red first.** Add a small checker under `contracts/src/main/java/.../contracts/refusal/`, for example
   `RefusalExamples`.
   - It reads the bundled contract and the `x-refusal` map of `CharterRefusalCode`.
   - It fails when an example (`examples.*.value.code` or `example.code`) of a response whose schema is
     `CharterRefusal` names a code that is not `raised: true`.
   - It also fails when a response **description** names an unraised code in backticks (for example
     `` `pack_level_mismatch` ``). The description of `OfferingUnreadable` is what misled clients.
   - Unit test `RefusalExamplesTest`, with resources `refusal/unraised-example.yaml` (must report) and
     `refusal/raised-examples.yaml` (must pass).
   - Wire it into `:contracts:check` the same way `checkClosedResponseObjects` is wired (a task in
     `contracts/build.gradle.kts`), so the real contract is checked.
   - Run it against today's `OfferingUnreadable` and see it fail.
2. **Then** `responses/OfferingUnreadable.yaml`:
   - add `examples` for `pack_unreadable` and `stale_input`, shaped like the existing
     `examples/CharterRefusal.*.yaml` with title, status 500, code and detail;
   - rewrite the description so it names only raised 500 codes: `pack_unreadable`, `stale_input` and
     `internal_error`.
3. `:contracts:check` goes green.

### T005 — Keep the service and harness green; CHANGELOG lines

1. Run `./gradlew :deployable:test :packs:test`.
   - Tests that read declared headers may need updating, for example `ProblemResponsesTest` and
     `ContractDescriptionsTest`. Update their expectations to the new rule; do not delete them.
   - The scenario hook in `CharterSteps.fingerprintHeaderIsTheVerifiedDigest` needs no change in this WP.
2. Append under `## 0.1.0-SNAPSHOT` → `### Breaking`, in a block headed "Gate-A read surface (mission
   gate-a-read-surface, #126, #175, #176; operator rulings of 2026-10-31)":
   - the fingerprint is required on verified read 200s and no longer declared on refusals, writes, the
     contract document or the shared Problem;
   - the narrowed lint rule and the two new rules;
   - the `OfferingUnreadable` examples and description;
   - the raised-code check.

## Definition of Done

- [ ] `./gradlew :contracts:check :contracts:test :deployable:test :packs:test` is green.
- [ ] Each new lint fixture fails exactly its own rule. The rule-count check passes.
- [ ] `grep -rn "Charter-Source-Fingerprint" contracts/charter/responses contracts/_shared/responses` finds nothing.
- [ ] Every GET 200 of a pack read and of `/info` declares the fingerprint, and the header is `required: true`.
- [ ] The raised-code check failed on the old `OfferingUnreadable` (record the output in the activity log),
  and passes now.
- [ ] No new suppressions. Complexity ≤ 15.

## Risks and notes

- The vacuum JSONPath filter dialect: if `?(@property != …)` is not supported in `given`, scope with the
  explicit path list instead, and pin the behaviour with the fixtures.
- Do not touch `x-provisional` leftovers in old fixtures (#46, out of scope).

## Reviewer guidance

- Check both directions on the real bundle:
  `./gradlew :contracts:bundleContract`, then grep the bundle for the header under each response kind.
- Confirm the raised-code check reads the real `x-refusal` map. It must not hard-code the codes.

## Branch strategy

The planning base and the final merge target are both `issue-126-offering-reads`. The execution worktree
for this WP comes from the lane computed in `lanes.json`. Implement with:
`spec-kitty agent action implement WP01 --agent claude --mission gate-a-read-surface-01M691ND`.
