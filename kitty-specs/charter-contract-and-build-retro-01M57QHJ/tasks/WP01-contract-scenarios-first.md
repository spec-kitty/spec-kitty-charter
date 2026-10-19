---
work_package_id: WP01
title: Contract to 1.0.0-SNAPSHOT, scenarios first
dependencies: []
requirement_refs:
- FR-003
- FR-004
- C-001
- C-002
- SC-001
- SC-002
planning_base_branch: ccr-9654c444-x7y94q
merge_target_branch: ccr-9654c444-x7y94q
branch_strategy: Planning artifacts for this mission were generated on ccr-9654c444-x7y94q. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into ccr-9654c444-x7y94q unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-charter-contract-and-build-retro-01M57QHJ
base_commit: 5333ed4193376664146b48afc4eafc6f98d2714e
created_at: '2026-10-18T16:05:16.836449+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
- T007
- T008
- T032
- T033
phase: Phase 1 - Contract
history:
- at: '2026-10-18T15:42:56Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- contracts/build.gradle.kts
- contracts/lint/ruleset.yaml
- contracts/lint/README.md
- service/adapter-in-rest/src/test/java/ai/speckitty/charter/rest/WireTokenParityTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/**
- contracts/lint/**
- contracts/src/**
- contracts/build.gradle.kts
- settings.gradle.kts
- build.gradle.kts
- service/charter-domain/src/**
- service/charter-read-app/src/**
- service/adapter-in-rest/src/**
- service/adapter-out-yaml/src/test/**
- service/charter-service/src/test/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Contract to 1.0.0-SNAPSHOT, scenarios first

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- **Has review feedback?**: check the `review_ref` field in the event log (`spec-kitty agent tasks status`).
- Address every feedback item before the work is complete; record each with `spec-kitty agent tasks add-history WP01 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

Bring `contracts/charter/` to `1.0.0-SNAPSHOT` and give the charter its **own** contract
checks (a charter-authored lint ruleset, a bundle, a breaking-change check) inside
`verifyAll`, carrying the operator's 2026-10-18 rulings through the contract
scenarios **first** and the service second.

Done when:

- Every refusal and diagnostic code is snake_case on the wire, in the contract, the
  scenarios and the service; `PackLevel` keeps `built-in`.
- The action-context operation and `PackSummary.diagnostics` carry `x-provisional`; the
  answer is sorted the way core sorts it.
- The charter's lint ruleset passes on the contract and every rule fails on its planted
  fixture; the bundle is produced; the breaking-change check passes (no baseline yet, and it
  says so). Core's contract tools are not used (operator, 2026-10-18).
- `./gradlew verifyAll -PopenPacksDir=<open-packs at OPEN_PACKS_REF>` is green on Java 25.

## Context & Constraints

- Read first: `.kittify/charter/charter.md`; `spec.md` (FR-003, FR-004, C-001, C-002);
  `plan.md` IC-03 and IC-04; `research.md` §7, §10, §13 (the direction change) (rulings and
  the findings table: rows S5, S8, S9, A1–A8, D2, D4–D6, L1–L4).
- **C-001: nothing that landed changes without a ruling.** The rulings are the only
  behavior changes allowed here. In particular, `PACK_LEVEL_MISSING` behavior is ratified
  **as is** (diagnostic at every level and for an absent `pack.yaml`; `pack_version`
  required at every level). Only its spelling changes.
- **The charter owns its contract and its checks.** This is a redesign of the charter
  offering, not a port of core's conventions; core's rough edges are the integration seam's
  concern (DM 01M57WH7P0QZWHKYGBA2R0MA3K, 01M57WHEAMG4GT7PNTAQ4X42KG, 01M57WHMTYJ9X12H4A33GWFJMT).
  Core's contracts are a source of ideas, never of files: no copy of core's ruleset or tools.
- Open-packs at `7f063aecbdede29d05cc4d5fc9a5b13f9c7434d7`. Open-packs #47 is where packs get
  brought up to the strict pack rule.
- Ownership note: this WP adds the `contracts` Gradle project, so it owns `settings.gradle.kts`
  and the root `build.gradle.kts`; WP03 later adds its project there with a recorded rationale.
- Terminology: "contract scenarios" for `contracts/charter/scenarios/`; "diagnostic"
  (never "refusal") for `PACK_LEVEL_MISSING`.

## Branch Strategy

- **Strategy**: set by finalize-tasks
- **Planning base branch**: `ccr-9654c444-x7y94q`
- **Merge target branch**: `ccr-9654c444-x7y94q`

> Execution worktrees are allocated per computed lane from `lanes.json`; do not pick a base by hand.

Implementation command:

```bash
spec-kitty agent action implement WP01 --agent claude --mission charter-contract-and-build-retro-01M57QHJ
```

## Subtasks & Detailed Guidance

### Subtask T001 – Contract scenarios first

- **Purpose**: the scenarios are half of the contract (ADR-0008); every behavior change lands here before code.
- **Steps**:
  1. In `contracts/charter/scenarios/packs.feature` and `action_context.feature`, change every code to snake_case: `mission_type_not_found`, `action_not_found`, `pack_level_mismatch`, `pack_unreadable`, `drg_schema_error`, `unresolved_edge_endpoint` (unchanged), `pack_level_missing`.
  2. Tag both files `@contract`. Tag `action_context.feature` also `@provisional` (the rules are decided by the conformance corpus, issue #9).
  3. Keep the exact-body comparison, `detail` sentences included: Call 1 was ratified as is, so asserted expectations change only where a ruling says so (the code spellings, the action-context order). Add no new step.
  4. Replace the edge-order expectation in "Action scope comes first…" with core's deterministic order (Call 2 ruling: content parity with the oracle). Grounding already done: core sorts by the full URN string (`delivery_table.py` ~336 `sorted(artifact_urns)`; `Urn.compareTo` already matches). Read core's ordering first (`src/charter/activation/mission_type_profiles.py` around 1314–1330, and `src/charter/offering/drg/query.py` `resolve_context`), write down the rule in the scenario's description line, and rename the scenario to describe the outcome, not the old order.
  5. Add scenarios (each with inline doc-string fixtures, never fixture files):
     - a cross-grain duplicate shaped like the real built-in case (`DIRECTIVE_042` scoped by both `action:documentation/generate` and `mission_type:documentation`) answers the directive once (A6);
     - an action that is **not a node of the graph** is refused `404 action_not_found` (retitle the existing one; A4);
     - a context read over a pack whose graph shard is malformed answers `500` with its refusal code (pins today's behavior; A5);
     - a scope edge to an endpoint the graph does not declare: pin the current behavior (A7).
- **Files**: `contracts/charter/scenarios/*.feature`
- **Notes**: run the suite after T004–T007; expect red until then.

### Subtask T002 – Contract documents

- **Purpose**: bring the OpenAPI document to the charter's own conventions (DM 01M57WHMTYJ9X12H4A33GWFJMT) and declare what is provisional.
- **Steps**:
  1. `openapi.yaml` `info.version: 1.0.0-SNAPSHOT` (the charter's own versioning choice: the first contract version core will vendor).
  2. `schemas/CharterRefusalCode.yaml`, `DiagnosticCode.yaml`: snake_case values; rewrite the descriptions: "Codes are snake_case on the wire; the integration seam in core maps them to core's own spellings." Keep the 404/500 rule and the deprecation-window note (ratified behavior).
  3. Add `x-provisional: {open_decision: "action-context resolution rules are decided by the conformance corpus (issue #9)"}` on the action-context operation, and `x-provisional: {open_decision: "PACK_LEVEL_MISSING becomes a refusal when core ADR 2026-10-11-2 closes its deprecation window"}` on `PackSummary.diagnostics` (the Call 4 ruling marks unstable parts `x-provisional`; the strict behavior itself stays as ratified in Call 1). Shape: `x-provisional: {open_decision: <text>}`; the charter's lint ruleset (T003) enforces it.
  4. `schemas/GuidanceEntry.yaml` `via`: "the node the edge starts from" (A3).
  5. Add descriptions to `PackListing.packs`, `Diagnostic.detail`, `ActionContext.guidance` (L4).
  6. Citations (L4): `x-source` with a path this repository tracks (the Java producer) for read fields; `x-derived` with contract-path inputs for computed fields; root citations on the referenced enum and scalar files inherit. Shapes: `x-source: {path, symbol}`, `x-derived: {rule, inputs}`; the charter's lint ruleset (T003) enforces them.
  7. `CHANGELOG.md`: the `1.0.0-SNAPSHOT` heading with `Added`, `Changed` and `Provisional` sections. Every scenario change gets a line (D6).
  8. `scenarios/README.md`: title "Contract scenarios"; the step vocabulary; the rule that fixtures stay inline as doc strings (D4); the rule that a scenario change needs a changelog line; tag meanings (`@contract`, `@provisional`).
- **Files**: `contracts/charter/**`

### Subtask T003 – The charter's lint ruleset and planted fixtures [P]

- **Purpose**: the charter's own contract conventions, as executable rules (DM 01M57WH7P0QZWHKYGBA2R0MA3K).
- **Steps**:
  1. `contracts/lint/ruleset.yaml` (Spectral format, run by vacuum): author the charter's rules. Core's ruleset may be read for ideas, never copied. At least: every operation has an `operationId` (unique) and a tag; every non-2xx response is `application/problem+json` carrying the shared `Problem`; every schema property has a description; enum values are snake_case, and tier tokens use the canonical tier spelling (`built-in`, `org`, `project`); every property of a resource schema carries `x-source` or `x-derived` in the shapes of T002.6; `x-provisional` is a mapping with a non-empty `open_decision`. Each rule carries a one-line description of the convention. **Response closure is not a ruleset rule** (DM 01M584Q3NNK0B78H9NKHV7VJDK): remove `closed-response-objects` and its fixtures from `contracts/lint/`; it is the Java checker of T033.
  2. `contracts/lint/fixtures/<rule-id>.yaml`: one planted OpenAPI fixture per rule that must fail exactly that rule; and one clean fixture that must pass all rules (positive control).
  3. `contracts/lint/README.md`: the conventions, why each exists, how to run the checks, how to add a rule (rule + fixture together).
- **Grounding**: vacuum 0.30.6 counts a ruleset's rules after `extends`; the charter ruleset should switch off vacuum's built-in `oas` set and switch on only its own rules, so a vacuum upgrade cannot silently add rules.

### Subtask T033 – The recursive closure checker in Java

- **Purpose**: response schemas are closed (`additionalProperties: false`). Five review rounds found holes in the JSONPath version of this rule (review-feedback-1..5); the operator ruled to move it into a small, unit-tested Java checker with a real recursive classification (DM 01M584Q3NNK0B78H9NKHV7VJDK).
- **Where**: Java sources in the `:contracts` project (`contracts/src/main/java/**`, tests in `contracts/src/test/java/**`, fixtures in `contracts/src/test/resources/closure/**`). `:contracts` applies `charter.java-conventions` (Java toolchain, JUnit, AssertJ) and reads YAML with the catalog's `snakeyaml-engine`. No Spring, no generated code.
- **Semantics** (the reviewer judges against these; write them as the class Javadoc):
  1. Input: the bundled contract (`bundleContract`'s output, every `$ref` inlined). Every response schema (`paths.*.*.responses.*.content.*.schema`) is a position. A `$ref` left in the bundle is a violation (the bundle is wrong).
  2. Classification is positive and recursive. A schema is **closed** (admits no object with unlisted members), **not an object** (a provable non-object), or **open**. Only the first two pass; anything the classifier cannot prove is open.
  3. `false` is closed. `true`, `{}` and a schema with no type and no closing keyword are open. A scalar `type` (alone or with `null`) or a bare `const`/`enum` is not an object. An object is closed only by `additionalProperties: false` with no `patternProperties`; a schema-valued `additionalProperties` (a map) is open. Each `properties` value is a position.
  4. Arrays: `items` and each `prefixItems` entry are positions; an array with no `items` (and no `prefixItems` closed by `items: false`) is open, because its items are unconstrained.
  5. Conjunction (`allOf`, and `then`/`else`/`dependentSchemas` beside their holder): the holder together with its conjunctive parts is closed when at least one part is closed or not an object; every nested position inside every part is still judged.
  6. Disjunction (`anyOf`/`oneOf`): closed or not-an-object only when every member is. A **constraint-only member** (only `required`, `dependentRequired`, `minProperties`, `maxProperties`, `not`, `description`-like annotations) is judged together with its holder's own keywords: on a closed holder it is closed; on a holder with no object keywords it is open (round-5 finding 1). Keywords are recognised by presence, never by value (`minProperties: 0` is a constraint; round-5 finding 2).
  7. `not` never closes anything; `if` only selects `then`/`else`.
  8. **Fail closed on unknown keywords**: the checker holds an explicit list of the JSON Schema and OpenAPI keywords it understands (annotations such as `description`, `example`, `x-*` included); any other keyword in a response position is a violation naming it, so a future construct cannot slip past.
  9. Output: one line per violation with the JSON Pointer of the position and the reason; a non-zero result when any is found.
- **Tests** (test first): one JUnit case per fixture.
  - Must report: every former `closed-response-objects*.yaml` fixture (moved here); the five round-5 probes (`anyOf: [{required: [k]}, {type: 'null'}]`, `oneOf: [{required: [k]}, {required: [j]}]` on an untyped holder, `anyOf: [{not: {required: [k]}}, {type: 'null'}]`, `oneOf: [{minProperties: 1}, {type: string}]`, `anyOf: [{required: [k], not: {properties: {k: {const: a}}}}, {type: 'null'}]`); every case the round-5 review listed as firing (`items: true`, an open `prefixItems` object, `additionalProperties: true`, `required` plus `additionalProperties: true`, `minProperties` plus `patternProperties`, a constraint member nesting a `oneOf` over an open object, a `dependentSchemas` hiding an open object); an array without `items`; an unknown keyword; a leftover `$ref`.
  - Must pass: the exactly-one-of idiom on a closed holder; `minProperties: 0` as a member on a closed holder; a closed tuple (`items: false`, closed `prefixItems`); an `if` condition holding a bare `const`; the clean fixture; and the real bundled contract (an integration test or the task below).
- **Gradle**: task `checkClosedResponseObjects` (a `JavaExec` or a worker action: inputs are the bundle and the runtime classpath, output a marker file; configuration-cache safe) depends on `bundleContract` and is part of `check`. The README of `contracts/lint/` moves its closure section to a short pointer at the checker; the checker's Javadoc is the authority.
- **Files**: `contracts/src/**`, `contracts/build.gradle.kts`, `contracts/lint/**`

### Subtask T032 – The contract-checks Gradle project

- **Purpose**: run the charter's contract checks inside `verifyAll`, with verified tool binaries.
- **Steps**:
  1. `contracts/build.gradle.kts`, included from `settings.gradle.kts` as `:contracts` (project dir `contracts/`), applying no Java plugin.
  2. Tool pins in the build script or a small pins file beside it: vacuum and oasdiff, exact versions at least two weeks old, release URLs and SHA-256 per platform you support (at least Linux x86-64 for CI). A download task verifies the checksum before extracting and fails closed on mismatch; outputs live under `build/tools/` (cache-friendly, configuration-cache safe: no `project` access at execution time).
  3. Tasks: `lintContract` (vacuum with `contracts/lint/ruleset.yaml` over the resolved contract; fails on any violation), `lintFixtures` (each planted fixture fails its own rule and only it; the clean fixture passes), `bundleContract` (a single-file bundle of `contracts/charter/openapi.yaml` under `build/contract/charter-openapi.yaml` plus its `.sha256`; vacuum's bundler, or the openapi-generator `openapi-yaml` generator already in the build: pick one, and check the bundle still validates), `checkContractCompatibility` (oasdiff breaking-change check of the bundle against the latest `contract-charter-v*` tag's bundle; with no such tag it prints "no released baseline" and passes). All four are part of `check`, so `verifyAll` runs them.
  4. The root `build.gradle.kts`/`settings.gradle.kts` `verifyAll` keeps working; nothing else changes there.
- **Files**: `contracts/build.gradle.kts`, `settings.gradle.kts`, `build.gradle.kts`

### Subtask T004 – Domain and REST wire tokens; parity test

- **Purpose**: one way to spell a contract enum value in Java (finding S5), so the case change is one edit per enum.
- **Steps**:
  1. `RefusalCode` already separates constant name and value; change the values to snake_case. Give `Diagnostic.Code` the same `token()` shape.
  2. `PacksController` (`Diagnostic.CodeEnum.fromValue(d.code().name())` today), `ContextController`, `RefusalHandler`: map through the tokens, never `name()`.
  3. `WireTokenParityTest` (adapter-in-rest test): for every domain enum that crosses the wire (`RefusalCode`, `Diagnostic.Code`, `PackLevel`, `NodeKind`, `Relation`), each token round-trips through the generated enum's `fromValue`, and the generated enum has no value the domain lacks. Positive control in the same test: a deliberately wrong token is rejected.
- **Files**: `service/charter-domain/src/**`, `service/adapter-in-rest/src/**`

### Subtask T005 – ActionContextQuery

- **Steps**: implement core's sort (T001.4) in `ActionContextQuery`; keep deduplication; fix the stale `contracts/charter/features/` comment (A8); fix the misleading comment in `ActionContextQueryTest` (A7) and pin the dangling-target behavior; unit tests mirror the new scenarios.
- **Files**: `service/charter-read-app/src/**`

### Subtask T006 – Open-packs round-trip asserts diagnostics [P]

- **Steps**: in `OpenPacksRoundTripTest`, assert the exact diagnostics each real pack produces (finding S9). With the ratified strict rule, packs without `pack.yaml` (for example qa, cave-kitty, event-driven) and `internal` (no `pack_level`) produce `pack_level_missing`; `built-in` produces none. Enumerate from the pinned corpus, do not guess; the census in open-packs #47 lists them.
- **Files**: `service/adapter-out-yaml/src/test/**`

### Subtask T007 – Suite selects `@contract`

- **Steps**: `CharterContractSuite` filters on the `@contract` tag (JUnit Platform `@IncludeTags` or Cucumber's `cucumber.filter.tags`), so #43's suites, once moved under `scenarios/<cluster>/`, are not picked up unbound (D2).
- **Files**: `service/charter-service/src/test/**`
- **Notes**: the classpath narrowing (`include("scenarios/**")`) is in WP02 (it owns the build file).

### Subtask T008 – Verify

- **Steps**:
  1. `JAVA_HOME=<JDK 25> ./gradlew verifyAll -PopenPacksDir=<open-packs at OPEN_PACKS_REF>`: record the test count (548 before this WP) and confirm 0 skipped; confirm the four contract tasks ran.
  2. Planted violations on the real contract: drop one property description, see `lintContract` fail naming it, revert; respell one code in UPPER_SNAKE, see it fail, revert. Record both with `add-history`, plus the `lintFixtures` result.
  3. Run the configuration cache twice (second run reuses it).
- If Maven Central answers HTTP 429, use a local-only Gradle init script mirroring to `https://maven-central.storage-download.googleapis.com/maven2/` (keep `gradlePluginPortal()`); **never commit it**.

## Test Strategy

Scenarios first (T001), then unit and parity tests, then the full `verifyAll`. Compiler
diagnostics count: the build runs `-Werror`.

## Risks & Mitigations

- Tool binaries per platform: pin each with its checksum; a missing platform fails with a clear message, never a silent skip.
- Sorting changes every multi-entry answer: only the action-context operation is affected; the pack listing order is unchanged.

## Review Guidance

- Each changed scenario has a changelog line; the ratified pack-level behavior is byte-for-byte the same apart from code spelling.
- The ruleset is the charter's own (not a copy of core's); every rule has a planted fixture; the tool binaries are checksum-verified.
- The parity test has a positive control.
- The implementer ran verifyAll on Java 25 (contract tasks included), with counts in the history.

## Progress & Status

Progress lives in the status event log: `spec-kitty agent tasks add-history WP01 --note "..."`,
`spec-kitty agent tasks mark-status T001 --status done`, `spec-kitty agent tasks status`.
