---
work_package_id: WP04
title: Writes moved; every per-kind route removed
dependencies:
- WP03
requirement_refs:
- FR-005
- FR-006
- FR-015
- NFR-004
- C-005
- SC-002
planning_base_branch: issue-126-offering-reads
merge_target_branch: issue-126-offering-reads
branch_strategy: Planning artifacts for this mission were generated on issue-126-offering-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-126-offering-reads unless the human explicitly redirects the landing branch.
subtasks:
- T017
- T018
- T019
- T020
- T021
phase: Phase 2 - The resource tree
agent: claude
history:
- at: '2026-10-31T14:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- contracts/charter/scenarios/removed-routes.feature
- contracts/charter/paths/packs_packId_artifacts_directive.yaml
- contracts/charter/paths/packs_packId_artifacts_directive_artifactId.yaml
- contracts/charter/paths/packs_packId_artifacts_styleguide.yaml
- contracts/charter/paths/packs_packId_artifacts_styleguide_artifactId.yaml
- contracts/charter/paths/packs_packId_artifacts_paradigm.yaml
- contracts/charter/paths/packs_packId_artifacts_paradigm_artifactId.yaml
- contracts/charter/paths/packs_packId_artifacts_toolguide.yaml
- contracts/charter/paths/packs_packId_artifacts_toolguide_artifactId.yaml
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/paths/**
- contracts/charter/parameters/**
- contracts/charter/responses/**
- contracts/charter/schemas/_index.yaml
- contracts/charter/scenarios/**
- contracts/charter/CHANGELOG.md
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/**
- service/deployable/src/test/java/ai/speckitty/charter/deployable/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04 – Writes moved; every per-kind route removed

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and follow
its guidance before you read the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

1. Move the eight per-kind writes into the generic tree:
   - `registerPack{Directive,Styleguide,Paradigm,Toolguide}` → `POST /packs/{packId}/artifacts/<kind>`;
   - `putPack…` → `PUT /packs/{packId}/artifacts/<kind>/{artifactId}`.
   - Each keeps its operationId, typed request body, per-kind tag and every refusal, and answers the
     `Artifact` envelope (ruling 2).
2. Remove **every** per-kind route, reads and writes, with no alias (FR-006).
3. Migrate every scenario and journey so that no behaviour they pinned is lost (NFR-004).
4. Pin #176's item-read `pack_unreadable` (FR-015).

## Context

- Spec US3, FR-005, FR-006, FR-015, NFR-004; research R-21, R-23; squad findings 10, 11, 19 and 22.
- **The write operations today** are in `contracts/charter/paths/packs_packId_{directives,styleguides,paradigms,toolguides}[_<kind>Id].yaml`.
  - Writes answer 201 or 200 (PUT is 201 when it creates) with an `ETag`.
  - Refusals: 404, 409 `registration_conflict`, 412, 422 `validation_failed`.
  - Controllers are `DirectiveController` (:70-120) and its siblings.
  - `Location` comes from `ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")`, which
    produces an absolute URI. With the POST at `/packs/{p}/artifacts/directive`, that gives
    `…/artifacts/directive/{id}`, which is the right path.
- The `PUT /packs/{packId}/relationships` route **stays** in this WP. WP06 changes its answer.
- **Literal write paths** sit beside the templated GET. WP02's method-aware matching handles conformance.
  Spring prefers the literal pattern and filters by method.
  - GET `/packs/{p}/artifacts/directive` matches no GET, so it answers 405.
  - PUT `/packs/{p}/artifacts/tactic/x` matches no PUT, so it answers 405.
  - Both are pinned (R-21).
- **Scenario baseline**: 111 `Scenario`/`Scenario Outline` headers, 6 of them outlines, on `main` at
  `e2f206f`. The measure is the JUnit test count of `CharterContractSuite`, which expands examples.
- `ToolguideRegistrationBodyAdvice` matches on the body type, so it needs no change. Confirm this with its
  test.

## Subtasks

### T017 — Baseline; red route scenarios; migrated features

1. **Before any change**, run `./gradlew :deployable:test --tests '*CharterContractSuite*'` and record the
   test count from `service/deployable/build/test-results/`. Record it in the activity log and in the
   CHANGELOG mission block (T021).
2. **New `removed-routes.feature`** (`@contract`):
   - a Scenario Outline over the 10 removed GET paths: `/packs/qa/{tactics,directives,styleguides,paradigms,toolguides}`
     and the same with `/x`;
   - and over the 8 removed write method-and-path pairs: POST on the 4 collections, PUT on the 4 items.
   - Each answers 404, and `the problem has no code`. The PUTs and POSTs need a valid body, so reuse the
     registration fixtures' bodies to make sure the 404 is the routing answer and not a validation answer.
   - GET `/packs/qa/artifacts/directive` answers 405.
   - PUT `/packs/qa/artifacts/tactic/x` answers 405. Note in the README that a write to a kind that cannot
     be written is not a `kind_not_found`.
3. **Migrate `directives.feature`, `styleguides.feature`, `paradigms.feature` and `toolguides.feature`.**
   - Reads become `GET /packs/qa/artifacts/<kind>/<id>` and `GET /packs/qa/artifacts?kind=<kind>`.
   - Writes go to the new paths.
   - Expected bodies become the envelope.
   - Also migrate `relationships.feature` (any per-kind GET in it), `service.feature` (1 line) and
     journeys 01–06.
   - **`tactics.feature`**: migrate its scenarios onto the generic reads, or fold them into
     `artifacts.feature` and delete `tactics.feature`. Either way, every scenario keeps a successor.
   - Add a `Location` check to one registration scenario per kind: the path of `Location` equals
     `_links.self.href`.
4. Run the suite. The migrated scenarios fail (the new write paths do not exist yet), and the
   removed-route outline fails (the old routes still answer). Record this.

### T018 — Contract moves and removals

1. **Eight new path files.**
   - `paths/packs_packId_artifacts_<kind>.yaml` (POST) and `paths/packs_packId_artifacts_<kind>_artifactId.yaml`
     (PUT), for `<kind>` in `directive`, `styleguide`, `paradigm`, `toolguide`.
   - Each is carried over from the old file with its operationId, tag, request body, headers and
     responses. **The item parameter is named `artifactId`** (squad NIT) and is reused from
     `parameters/ArtifactId.yaml`.
   - The 201/200 schema becomes `../schemas/Artifact.yaml`.
   - Descriptions name the new paths, and say that registration is not yet advertised by a link (R-24).
2. **`openapi.yaml`.**
   - Add the 8 path entries. Order the literal paths before the templated
     `/packs/{packId}/artifacts/{kind}/{artifactId}` for readability.
   - Remove all 10 per-kind path entries and the `Tactics` tag.
3. **Delete:**
   - the 10 old path files;
   - `parameters/TacticId.yaml` (update `parameters/_index.yaml`);
   - `responses/TacticNotFound.yaml`;
   - any per-kind `*NotFound` response no remaining operation references. The writes still answer 404
     for an unknown pack or an unknown id on PUT, so keep whichever they reference.
   - Update `responses/_index.yaml`.
4. Run `:contracts:check`. It must be green (bundle, lint, closure, raised-code check).

### T019 — Service: writes answer the envelope; reads removed

1. The generated `DirectivesApi` (and its siblings) now hold only `register*` and `put*`.
   - Remove the read methods from `DirectiveController`, `StyleguideController`, `ParadigmController` and
     `ToolguideController`.
   - Delete `TacticController`. With no operations left, `TacticsApi` is no longer generated.
2. Writes return `ResponseEntity<Artifact>`, built through `ArtifactDtos` (WP03) with the same `_links` as
   the item read.
   - Keep the `ETag`, the status (201 or 200) and `Location`.
   - The path of `Location` must equal `_links.self.href`.
3. **Tests.**
   - Migrate the read cases of `TacticControllerTest` and of the four other controller tests into
     `ArtifactControllerTest`, or delete them where WP03 already covers the same behaviour. List which in
     the activity log.
   - Update the write tests to the envelope.
   - Update `RegularArtefactWiringTest` (:90-124), `ProductionWiringTest` (:105-109),
     `ProblemResponsesTest` (:84) and `ContractConformanceTest` (:22-107) to the new paths.
4. The `ArchUnit` suite stays green and unchanged.

### T020 — #176: `pack_unreadable` on an item read

In `artifacts.feature` (or the migrated tactic scenarios), add: `Given the directory of the pack "qa" is
missing`, then `When I GET "/packs/qa/artifacts/tactic/rehearse"`, then status 500, code
`pack_unreadable`, no fingerprint.

Use the missing-directory state, as `tactics.feature:69-74` does. A malformed tactic file would be rewritten
by the in-load refusal handling and would not pin the first-digest path (squad finding 34). The behaviour
already holds through `ConsistentRead`, and this scenario is red only because the route is new. Say so in
the activity log.

### T021 — Count check and mapping

1. Re-run `CharterContractSuite`. The count after must be ≥ the count before (T017). Record both numbers.
2. In the CHANGELOG mission block, add:
   - a "Scenarios" list mapping each removed or renamed scenario (by feature and scenario name) to its
     successor;
   - the removed operations: `listPackTactics`, `getPackTactic`, `listPack{Directives,Styleguides,Paradigms,Toolguides}`
     and `getPack{Directive,Styleguide,Paradigm,Toolguide}`;
   - the moved writes, with old path → new path;
   - the deleted `TacticNotFound` and `TacticId`.

## Definition of Done

- [ ] `verifyAll` is green.
- [ ] `grep -rnE "/packs/\{packId\}/(tactics|directives|styleguides|paradigms|toolguides)" contracts/charter`
  finds nothing outside the CHANGELOG.
- [ ] The removed-route outline (18 rows) and the two 405 scenarios pass. They failed first.
- [ ] The `CharterContractSuite` count is ≥ the baseline, both recorded; the scenario mapping is in the
  CHANGELOG.
- [ ] The #176 item scenario passes.
- [ ] No new suppressions. Complexity ≤ 15.

## Risks and notes

- This is the largest diff of the mission. Keep the commits reviewable:
  - contract moves;
  - service;
  - scenario migration (one commit per feature file if that helps);
  - removals.
- The exact-body scenarios get longer with the envelope. Keep them exact.

## Reviewer guidance

- Diff each old write path file against its new one: only the path, the parameter name, the response schema
  and the descriptions may change.
- Spot-check that every refusal scenario of the old writes has a successor on the new path.

## Branch strategy

The planning base and the merge target are both `issue-126-offering-reads`. The lane comes from
`lanes.json`. Implement with:
`spec-kitty agent action implement WP04 --agent claude --mission gate-a-read-surface-01M691ND`.
