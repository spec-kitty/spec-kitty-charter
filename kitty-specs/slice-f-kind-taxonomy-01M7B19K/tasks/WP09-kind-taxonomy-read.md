---
work_package_id: WP09
title: R18, the kind taxonomy read
dependencies: [WP08]
requirement_refs:
- FR-001
- FR-002
- FR-003
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T042
- T043
- T044
- T045
- T046
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- contracts/charter/paths/offering_kinds.yaml
- contracts/charter/schemas/KindTaxonomy.yaml
- contracts/charter/schemas/KindRow.yaml
- contracts/charter/schemas/RelationRow.yaml
- contracts/charter/schemas/EndpointPair.yaml
- contracts/charter/examples/KindTaxonomy.model.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/application/KindTaxonomyReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/KindTaxonomyController.java
execution_mode: code_change
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/paths/offering_kinds.yaml
- contracts/charter/schemas/KindTaxonomy.yaml
- contracts/charter/schemas/KindRow.yaml
- contracts/charter/schemas/RelationRow.yaml
- contracts/charter/schemas/EndpointPair.yaml
- contracts/charter/examples/KindTaxonomy*.yaml
- contracts/charter/schemas/Offering.yaml
- contracts/charter/schemas/ServiceRoot*.yaml
- contracts/charter/examples/Offering*.yaml
- contracts/charter/scenarios/offering.feature
- contracts/charter/scenarios/root.feature
- contracts/charter/scenarios/switch-gate.yaml
- contracts/charter/scenarios/every-operation.feature
- contracts/lint/ruleset.yaml
- docs/development/rest-resource-conventions.md
- service/packs/src/main/java/ai/speckitty/charter/packs/application/KindTaxonomyReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/KindTaxonomyController.java
role: implementer
---

# Work Package Prompt: WP09 - R18, the kind taxonomy read

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-04.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

`GET /offering/kinds` (`getKindTaxonomy`) serves every kind and every relation with its allowed endpoint kinds (R18, #112, operator ruling `DM-01M7B1YE4J84K0T4ZA27M48QKG`). It is reachable from the root and from the offering, kept in step with the model by a parity test, and follows the contract's header, ETag and lint conventions.

## Context

- The draft shape is `contracts/slice-f.openapi.yaml`.
- Squad findings T-B1, T-B2, T-M7, T-9, T-11 and T-14 apply:
  - the ETag is the sha256 of the body, in the `ContractETag` pattern;
  - `IfNoneMatch` and `NotModified`;
  - the `Charter-Contract-Version` and `Charter-Worktree` headers on 200 and 304 (like `paths/openapi.yaml:15-57`);
  - no source fingerprint, with the path added to the exceptions of the two fingerprint lint rules;
  - a `kinds` link on the root and the offering;
  - templated `artifacts` links;
  - enum refs, descriptions and `x-source`.
- The read is static: the kinds come from the kernel enums after WP08.

## Subtasks

### T042: Contract

- Add `paths/offering_kinds.yaml`, with tag `Offering`. Widen the tag's description to "the configured packs and the model's taxonomy".
- Add `KindTaxonomy {kinds[], relations[], _links}`.
- Add `KindRow {kind ($ref NodeKind.yaml), plural, artifact, activatable, authoredInGraphs, _links}`. Its `artifacts` link is templated, `/api/v1/packs/{packId}/artifacts?kind=<kind>`, for each served kind.
- Add `RelationRow {relation ($ref Relation.yaml), heldAs (relationship|tension), directed, derived, endpoints[EndpointPair]}`. Tension is the row `in_tension_with`; check whether `Relation.yaml` admits it, and if not, give `relation` a oneOf.
- Add `EndpointPair {source, target}`, both `$ref NodeKind.yaml`.
- Every property has a `description` and an `x-source` (or `x-derived`).
- Add an example with every kind and every relation.
- Lint: add `/offering/kinds` to `fingerprint-on-verified-reads` and to `fingerprint-only-on-verified-reads` (`ruleset.yaml:201-232`). Update `rest-resource-conventions.md:116-118`.

### T043: Service

- `KindTaxonomyReadService` builds the body from `NodeKind`, `ArtifactKind`, `RelationType.endpoints()` and the `Tension` rule. The order is the model's order.
- `KindTaxonomyController` implements the generated `OfferingApi#getKindTaxonomy`. It sets the `ETag` from the sha256 of the canonical JSON body, answers `304` on a matching `If-None-Match`, and declares the Charter headers through the existing filter.
- Place it in `packs` beside `OfferingReadService`, or in `charter-reports` if ArchUnit says so. Run `HexagonalArchitectureTest`.

### T044: Links

- The root and `Offering` gain `kinds` (schemas, examples, controllers).
- Mark `kinds` "In the contract" in the hypermedia table (out-of-map; WP10 owns that doc).
- `link-relations-are-known` admits `kinds`.

### T045: Gates and scenarios

- `switch-gate.yaml`: add the `getKindTaxonomy` entry, with its declared statuses (200, 304, 401, 403).
- `every-operation.feature`: add rows for 401 and 403.
- `offering.feature`: add a 200 scenario (it lists every kind, has no `mission_step_contract`, has no `impacts`, `replaces` or `delegates_to`, and every relation has endpoints), and a 304 scenario using the `ETag`.
- `root.feature`: the `kinds` link resolves.

### T046: Parity

Add a case to `WireTokenParityTest` (or `VocabularyEqualityTest`): the served body (or the read service's output) equals the enums and `file-kinds.yaml`. Its positive control skews one kind's `plural`, and must fail.

## Validation

- `./gradlew :contracts:check :packs:test :deployable:test --max-workers=1` is green.
- The switch-gate register shows `getKindTaxonomy` as ready.

## Definition of Done

- T042 to T046 are done.
- #112's acceptance (a shape, scenarios and the switch-gate case) is met.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- **A body hash that is not deterministic.** Serialize canonically: a fixed order and no whitespace variance.

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP09 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
