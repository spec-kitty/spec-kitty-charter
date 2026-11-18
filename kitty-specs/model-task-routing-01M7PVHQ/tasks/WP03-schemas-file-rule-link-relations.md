---
work_package_id: WP03
title: Schemas, file rule and link relations
dependencies:
- WP02
requirement_refs:
- FR-001
- FR-005
- C-005
subtasks:
- T008
- T009
- T010
- T011
- T012
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-11-18T08:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/packs/
create_intent:
- contracts/packs/schemas/routing-catalog.schema.json
execution_mode: code_change
owned_files:
- contracts/charter/schemas/ActivatableKind.yaml
- contracts/charter/schemas/ArtifactKindToken.yaml
- contracts/charter/schemas/NodeKind.yaml
- contracts/charter/schemas/TaxonomyKind.yaml
- contracts/charter/schemas/CascadeKind.yaml
- contracts/charter/schemas/SelectedArtifacts.yaml
- contracts/charter/schemas/DeactivatedArtifacts.yaml
- contracts/charter/schemas/Preset.yaml
- contracts/charter/parameters/ArtifactKind.yaml
- contracts/offering/schemas/ActivationConfig.schema.json
- contracts/offering/corpus/ActivationConfig/valid/**
- contracts/packs/schemas/preset.schema.json
- contracts/packs/schemas/charter.schema.json
- contracts/packs/schemas/policy.schema.json
- contracts/packs/schemas/pack-manifest.schema.json
- contracts/packs/schemas/graph-shard.schema.json
- contracts/packs/schemas/org-fragment.schema.json
- contracts/packs/schemas/routing-catalog.schema.json
- contracts/packs/file-kinds.yaml
- contracts/packs/conformance/classifier-cases.yaml
- contracts/packs/CHANGELOG.md
- contracts/lint/ruleset.yaml
- docs/development/hypermedia-hateoas.md
- contracts/charter/examples/KindTaxonomy.model.yaml
- contracts/charter/scenarios/offering.feature
role: implementer
task_type: implement
---

# Work Package Prompt: WP03 – Schemas, file rule and link relations

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according to
its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`). Address every feedback item, and record
each change with `spec-kitty agent tasks add-history WP03 --note "..."`.

## Branch Strategy

- **Planning base branch**: `feat/108-model-task-routing`
- **Merge target branch**: `feat/108-model-task-routing`
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by hand.

```bash
spec-kitty agent action implement WP03 --agent claude --mission model-task-routing-01M7PVHQ
```

## Before you start (every WP)

1. **#355 must be merged.** No code starts before #355 (PR #599) has merged and this branch is rebased onto that main
   (C-001). If it has not merged, stop and report.
2. **Recheck every path below on the merged main.** This prompt was written against main `8284bd36` with #599 open. #599
   rewrites `PackContents`, `PackRulesets`, `PackAssembly`, `StoreRead`, `StorageRulesets` and
   `ReferenceFacts`/`ReferenceClosure`, and deletes `MissionTemplates` and `GuardClosure` (research R1). Follow the
   merged code, not this text, where they differ, and record the difference in the WP history.
3. **Read the authority:**
   - `docs/adr/0080-the-routing-catalog-kind-and-the-model-recommendation.md` (accepted);
   - `kitty-specs/model-task-routing-01M7PVHQ/{plan.md,data-model.md,research.md,contracts/operations.md}`;
   - the charter companion `.kittify/charter/charter.md`.

   Core's `/opt/spec-kitty/src/charter/offering/model_task_routing/` is evidence only (ADR-0015).
4. **Tests use the canonical shape only**, except tests of invalid content (charter companion; ADR-0070). Never exclude
   a field from a parity or round-trip check to make a test pass.
5. **Commits:** signed (`git commit -S`), ending with the `Claude-Session:` trailer, with no model names and no
   `Co-Authored-By`.

## Objective

Every enum of kinds in the contracts lists `routing_catalog`, last. The pack schema of the kind and its file rule
exist. The four link relations the reads use are registered in the lint's closed list (plan IC-01, IC-02, IC-05;
squad findings C1, C4, C7, A7).

## Subtasks

### T008 – Contract enums and activation schemas

- **Enums.** Append `routing_catalog` **last** to `schemas/ActivatableKind.yaml` (line 17), `ArtifactKindToken.yaml`
  (line 24, plus its prose at lines 4–6), `NodeKind.yaml` (line 21), `TaxonomyKind.yaml` (line 18) and
  `CascadeKind.yaml` (line 16). The order matches `ArtifactKind` (WP02).
- **Selection documents.** Add a `routingCatalogs` property to `SelectedArtifacts.yaml` and `DeactivatedArtifacts.yaml`
  (near line 66), shaped like `skills`.
- **Presets.** Add `activatedRoutingCatalogs` to `Preset.yaml` (near line 115).
- **Prose.** Add the kind to the prose of `parameters/ArtifactKind.yaml` (lines 6–13) only if that file lists the
  literal kinds. It does, and the catalog is a literal kind (WP08).
- **Activation config.** In `contracts/offering/schemas/ActivationConfig.schema.json` (lines 129 and 275), add
  `routing_catalogs`. Extend the corpus files `valid/06-every-selected-kind.json` and `07-every-deactivated-kind.json`.
- **Not here.** `Artifact.yaml`, `ArtifactSummary.yaml` and `Activation.yaml` change in WP08, with the schemas they
  reference.

### T009 – Pack schemas, and the kind's schema

- **The kind in the other pack schemas.** Add it to:
  - `preset.schema.json` (`activated_routing_catalogs`, near line 133);
  - `charter.schema.json` (near line 861);
  - `policy.schema.json` (the per-kind key and the URN pattern, lines 53 and 85);
  - `pack-manifest.schema.json` (the kind enum, line 20);
  - `graph-shard.schema.json` and `org-fragment.schema.json`, where they enumerate kinds or plurals.

  The kind is not declarable in graphs, so a graph shard must **not** gain a node branch for it. Add it only where
  the schema lists every artifact kind (a URN pattern, for example), and leave the node-kind branches alone. Explain
  each choice in the WP history.
- **Write `contracts/packs/schemas/routing-catalog.schema.json`** (draft 2020-12, `additionalProperties: false`
  everywhere), exactly as `data-model.md` "RoutingCatalog" and ADR-0080 decision 5 state it:
  - `id`: the kebab-case pattern the skill schema uses, `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, `maxLength` 64.
  - `description`, optional.
  - `generated_at`: `format: date-time`.
  - `task_types`, `models` and `sources`: each at least one item.
  - Enums: `objective` is `quality_first`, `balanced` or `cost_first`; a cost tier is `low`, `medium`, `high` or
    `premium`; a latency tier and a confidence are `low`, `medium` or `high`; `access_method` is `api`, `dataset` or
    `manual`.
  - Numbers: `score` and each weight run from 0 to 1. Write them as JSON numbers; the loader reads them as decimals
    (WP05).
  - Cross-references (an undeclared task type, duplicate ids) are **not** expressible in JSON Schema. They are WP05's
    content rules; say so in the schema's `description`.
  - No `schema_version`, no `source_snapshot`, no `override_policy`, no `freshness_policy`.

### T010 – File rule, classifier case and packs CHANGELOG

- **`contracts/packs/file-kinds.yaml`.**
  - `node_kinds`: `routing_catalog` (line 94 region).
  - `artifact_kinds`: `routing_catalog` (line 113 region).
  - `fragment_kinds`: `routing_catalogs: routing_catalog` (line 131 region), only if `fragment_kinds` must list
    every kind. Read the file's header comment. A kind no graph declares may be left out; if it is, say why in a
    comment.
  - `files`: `- match: "**/*.routing-catalog.yaml"` with `schema: routing-catalog` (beside the skill rule at lines
    168–169), and a comment naming ADR-0080.
- **`contracts/packs/conformance/classifier-cases.yaml`.** Add a case that classifies
  `routing_catalogs/default.routing-catalog.yaml` to the new rule, as the skill case at line 53 does.
- **`contracts/packs/CHANGELOG.md`.** Add a line to the top section naming the new kind, its schema, its file rule and
  the enum additions (that file's header rule).

### T011 – Link relations

Register `routing-catalogs`, `catalog`, `agent-profile` and `step` (kebab-case) in the closed enum of the rule
`link-relations-are-known` in `contracts/lint/ruleset.yaml` (lines 259–326). Add a row for each to the relation table
in `docs/development/hypermedia-hateoas.md`. Before you add a relation, check whether it already exists: reuse it
and do not duplicate it. Describe each relation as the table does.

### T012 – Kind taxonomy example and `offering.feature`

- Append the `routing_catalog` row to `examples/KindTaxonomy.model.yaml` (near lines 98–107), with `artifact: true`,
  `activatable: true`, `declarableInGraphs: false` and `edgeTarget: false`, and its `?kind=routing_catalog` link.
- Add **no** relation pairs: lines 240–470 list the relation pairs, and after WP02 there are none for the kind.
- Update `scenarios/offering.feature` (lines 383–402 and 425–432) so the taxonomy order includes the new kind last.
  The earlier indexes must not move.

## Definition of Done

- Every enum of kinds lists `routing_catalog` last. The pack schema and the file rule exist. The four link relations
  are registered.
- `./gradlew :contracts:check` passes, including the parity and corpus tests.
- `./gradlew verifyAll` passes on Java 25 for the modules this WP touches, with no new style-gate finding.
- Every subtask's status is recorded with `spec-kitty agent tasks mark-status`.
- Nothing outside `owned_files` changed, except a recorded edit outside the map, named in the WP history with its
  reason.

## Reviewer Guidance

- Check that the order is the same everywhere: kernel, contract enums and taxonomy.
- Check that the schema forbids unknown keys at every level, and holds none of the dropped fields.
- **Independent reviewer.** Review is by a separate reviewer session, never by the implementer.
