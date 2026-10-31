---
work_package_id: WP06
title: Every answer linked and checked
dependencies:
- WP05
requirement_refs:
- FR-008
- FR-009
- FR-011
- NFR-002
- SC-003
planning_base_branch: issue-126-offering-reads
merge_target_branch: issue-126-offering-reads
branch_strategy: Planning artifacts for this mission were generated on issue-126-offering-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-126-offering-reads unless the human explicitly redirects the landing branch.
subtasks:
- T027
- T028
- T029
- T030
- T031
phase: Phase 3 - Enforcement and records
agent: claude
history:
- at: '2026-10-31T14:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/lint/
create_intent:
- contracts/charter/schemas/RelationshipRegistered.yaml
- contracts/lint/fixtures/success-schemas-carry-links.yaml
- contracts/lint/fixtures/link-relations-are-known.yaml
- contracts/lint/fixtures/success-responses-have-link-examples.yaml
- contracts/src/test/java/ai/speckitty/charter/contracts/lint/RelationTableParityTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/LinkChecks.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/OperationCoverage.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/RelationSetParityTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/lint/**
- contracts/charter/openapi.yaml
- contracts/charter/schemas/**
- contracts/charter/paths/**
- contracts/charter/scenarios/**
- contracts/charter/examples/**
- contracts/charter/CHANGELOG.md
- contracts/build.gradle.kts
- contracts/src/test/java/ai/speckitty/charter/contracts/lint/**
- docs/development/hypermedia-hateoas.md
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/**
- service/deployable/build.gradle.kts
- service/deployable/src/test/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP06 – Every answer linked and checked

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and follow
its guidance before you read the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Make "every success answer can be navigated" enforced, not hoped for.

- `PUT /packs/{packId}/relationships` answers a linked response-only schema (R-13).
- Lint rules require `_links` on every JSON 2xx schema, require a `_links` example on every JSON 2xx
  response, and check relation keys against the guide's table (FR-011).
- The scenario harness asserts `self`, statically resolves every link, checks `Location`, checks relation
  keys, and fails when an (operation, 2xx) pair is exercised by no scenario (FR-009, R-20).

## Context

- Spec FR-008, FR-009, FR-011; research R-03 (revised), R-13, R-20; squad findings 1, 11, 12, 13, 20 and 23.
- **`RelationshipRegistration`** is the request body of `putPackRelationship`
  (`paths/packs_packId_relationships.yaml:27`), and the item type of `relationships` arrays in `Directive`,
  `Styleguide`, `Paradigm` and the registrations. Never give it `_links`.
- **The relation table**: `docs/development/hypermedia-hateoas.md:97-124`.
  - It has no rows for `service-desc`, `first` and `next`.
  - The "On" column of `collection` ("A pack"), `edit` ("An activation") and `artifacts` is stale.
  - `:27-31` still says no response carries `_links`.
- **Lint.** vacuum with core functions. A fixture must fire exactly its own rule (`build.gradle.kts:384-386`),
  and the rule count must match. Adding these rules forces `_links` and `_links` examples into `clean.yaml`
  and every existing fixture's 2xx schemas, so each still fires only its own rule.
- **Test resources.** `docs/` is not on the deployable test classpath (`service/deployable/build.gradle.kts:61-75`
  copies only the contract and the scenarios). Copy the guide into the test resources in
  `processTestResources`, the same way the scenarios are copied.

## Subtasks

### T027 — `RelationshipRegistered`

1. **Red first.** Update `relationships.feature`: the PUT answer body is
   `{relationship: {...}, _links: {artifacts: {href: /api/v1/packs/qa/artifacts}}}`. Run it and see it fail.
2. **Contract.**
   - New `schemas/RelationshipRegistered.yaml`: `relationship` (`$ref RelationshipRegistration`) and
     `_links`, both required, closed, cited.
   - `paths/packs_packId_relationships.yaml` 200/201 answers it. Add a `_links` example.
3. **Service.** `RelationshipController` wraps the answer and builds `artifacts` with `Hypermedia`. Update
   `RelationshipControllerTest`.

### T028 — Lint rules and fixtures

1. **Red first.** Add planted fixtures (inline copies of `clean.yaml`, one violation each):
   - `success-schemas-carry-links.yaml`: a 200 JSON schema without a required `_links`;
   - `link-relations-are-known.yaml`: a 200 example with `_links.bogus`;
   - `success-responses-have-link-examples.yaml`: a 200 JSON response with no example carrying `_links`.
2. **Rules.**
   - `success-schemas-carry-links`
     - `given` the 2xx `application/json` schemas: `$.paths[*][*].responses[?(@property.match(/^2/))].content['application/json'].schema`.
       Check the filter dialect vacuum supports; fall back to listing `200` and `201`.
     - `then` `schema`: `{required: {contains: {const: _links}}}`.
   - `link-relations-are-known`
     - `given` `$..examples[*].value.._links` and `$..example.._links` (example values only; never the
       inlined schema's `properties._links`).
     - `then` `schema`: `{propertyNames: {enum: [<the table's keys>]}}`.
   - `success-responses-have-link-examples`
     - Every 2xx JSON response has `example` or `examples` with a value whose top level has `_links`.
     - If this cannot be expressed with core functions, implement it as a `contracts` unit-tested check
       wired into `:contracts:check`, like WP01's refusal-example check, and say so in the lint README.
3. Update `clean.yaml` and every existing fixture so that each fires only its own rule. Update the README
   table.
4. Every 2xx JSON response in the real contract carries a `_links` example. Check the ones added in
   WP03–WP05 and add any missing.

### T029 — Relation table and parity

1. **`docs/development/hypermedia-hateoas.md`.**
   - Remove "None is in the contract yet" (:27-31). State what is in the contract.
   - Add rows:
     - `service-desc`: RFC 8631; on the root and the service info; the contract document.
     - `first`: IANA; on a page; the first page.
     - `next`: IANA; on a page; the next page, absent on the last.
   - Correct the "On" column for `collection` (also on an artifact: the pack's artifacts of its kind) and
     `artifacts` (also on the root, templated, and on an offering pack).
   - Note that `edit` is not used in this minor.
   - Keep the frontmatter's `updated:` current.
2. **`RelationTableParityTest`** (contracts). Parse the table rows, the first-column keys in backticks.
   Assert they equal the `enum` in the `link-relations-are-known` rule.
3. **`RelationSetParityTest`** (deployable, reading the copied guide). Every `Relation` wire key is a key
   in the table.

### T030 — Harness hooks and coverage

In `LinkChecks`, called from an `@After` in `CharterSteps`, for every exchange whose status is 2xx and
whose body is JSON:

1. **Self.**
   - If the contract has a GET operation for the request path, `_links.self.href` equals the base path
     plus the request path, plus the query for a GET with a query.
   - Otherwise (the relationships PUT), there is no `self`.
2. **Link resolution.** Every link's `href`, with the base path stripped and RFC 6570 expressions removed
   (`{?kind}`) or `{packId}` treated as a segment wildcard, matches a contract path template, and that
   template defines the link's `method` (default `get`). This is static: no requests.
3. **Location.** On a 201, the path of `Location` equals `_links.self.href`.
4. **Relation keys.** Every key in every `_links` in the body, nested ones included, is in the guide's
   table.

In `OperationCoverage` (`@AfterAll`, or a JUnit test after the suite reading a recorded list of exchanges):

- for each contract (method, path, 2xx status), at least one scenario exchange matched it;
- otherwise fail, listing the gaps.

Then add scenarios for every gap the check reveals. A no-filter list of each kind is a likely one: only
tactics and directives were listed before.

**Red first.** Unit-test `LinkChecks` with a body whose `self` is `/api/v1/` on a pack path, and one with a
link to `/api/v1/packs/{packId}` (which does not exist). Both must fail.

### T031 — Verify and CHANGELOG

1. Run `./gradlew verifyAll`. Green is required.
2. Append to the CHANGELOG mission block:
   - `RelationshipRegistered`;
   - the three link rules;
   - the harness checks;
   - the added scenarios.

## Definition of Done

- [ ] `verifyAll` is green.
- [ ] The three new lint fixtures fail exactly their own rule, and failed before the rules existed.
- [ ] `LinkChecks` failed its two planted bodies (logged) and passes the suite.
- [ ] Operation coverage passes, with no (operation, 2xx) unexercised.
- [ ] Both parity tests pass.
- [ ] No new suppressions. Complexity ≤ 15.

## Risks and notes

- JSONPath filters in vacuum: if `@property.match` is unsupported, enumerate the statuses.
- Expanding a URI template: keep it to the forms the contract uses (`{packId}` and `{?kind}`), and fail
  loudly on any other.

## Reviewer guidance

- Rename a relation in `Relation` to something not in the table, and check that the parity test fails.
- Delete one scenario that is the only caller of an operation, and check that coverage fails.

## Branch strategy

The planning base and the merge target are both `issue-126-offering-reads`. The lane comes from
`lanes.json`. Implement with:
`spec-kitty agent action implement WP06 --agent claude --mission gate-a-read-surface-01M691ND`.
