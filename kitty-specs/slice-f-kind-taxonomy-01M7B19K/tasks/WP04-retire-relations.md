---
work_package_id: WP04
title: Retire impacts, replaces and delegates_to
dependencies: [WP01, WP02, WP03]
requirement_refs:
- FR-017
- FR-018
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T016
- T017
- T018
- T019
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- service/packs/src/test/resources/load-fixtures/shard-retired-relation/files/pack.yaml
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Relation.java
- contracts/charter/schemas/Relation.yaml
- contracts/packs/schemas/graph-shard.schema.json
- contracts/packs/schemas/org-fragment.schema.json
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DrgEdge.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipMerge.java
- service/packs/src/test/resources/planted/c12-*/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WireTokenParityTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/RelationSetParityTest.java
role: implementer
---

# Work Package Prompt: WP04 - Retire impacts, replaces and delegates_to

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-01.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

Remove the three relations that have no endpoint row in the model (operator ruling `DM-01M7B1YXJHQCBSYJ145NJC77V5`), together with the `impacts` strength, and delete the unused `kernel.domain.Relation`. A graph document that still uses one of them is refused as an unknown relation.

## Context

- `RelationType` javadoc already calls `IMPACTS` retired (ADR-0028), and `REPLACES` is "kept for older fragments". `replaces` was sometimes used to mean `overrides`; record that in WP10's ADR.
- No `replaces` or `delegates_to` edge exists in any corpus.
- After WP01 and WP02, no valid-path `impacts` edge remains. Only the impacts error fixtures (`shard-impacts-*`, `c12-*impacts*`) do.
- `kernel.domain.Relation` has 0 callers in main code (squad D-m).

## Subtasks

### T016: The enum values and Relation.java

- Remove `IMPACTS`, `REPLACES` and `DELEGATES_TO` from `RelationType` (WP03's table rows go with them).
- Delete `Relation.java` and its one test use.
- In `contracts/charter/schemas/Relation.yaml`, drop the three values, add `implemented_by` if the enum is the authored-or-served relation set (check how `implemented_by` is shown on the wire first), and repoint the `x-source` at `RelationType.java`.
- Remove the three values from the relation enums in `graph-shard.schema.json` and `org-fragment.schema.json`.
- Fix every switch over `RelationType` that the compiler reports.

### T017: The impacts strength

- Drop the `impacts` strength property from `DrgEdge` and from both graph schemas (line 46 in each).
- Drop the strength rule (`PackPolicy.java:846`; an out-of-map edit, since WP05 owns `PackPolicy`) and the IMPACTS case in `RelationshipMerge.java:32`.
- Drop the strength from the writers (`GraphShardWriter`) and the round-trip comparison.

### T018: Fixtures

- Delete the `shard-impacts-*` load fixtures (×5) and the `c12-*impacts*` planted packs (×4), along with their expectations in `core-verdicts.json` and `docs/conformance/divergences.yaml`, if they are listed.
- Update the test classes that named them: `ExamplePacksComposeTest`, `DomainRoundTripTest`, `GraphShardWriterTest`, `PackPolicyGraphTest` and the others grep finds.
- Add one load fixture, `shard-retired-relation`, whose graph shard uses `replaces`. It is refused as an unknown relation, with today's code for that (`drg_schema_error` / `pack_unreadable`, whichever `shard-unknown-relation` expects).
- Add one scenario in `relationships.feature` (an out-of-map row, owned by WP02): a `PUT` with `type: impacts` is refused as `422 validation_failed`, as an unknown `type`.

### T019: Parity

- `RelationSetParityTest`, `WireTokenParityTest` and `VocabularyEqualityTest` (`file-kinds.yaml` relations, an out-of-map row, since WP08 owns `file-kinds.yaml`) follow the enum.
- Keep their positive controls.

## Validation

- `./gradlew :kernel:test :packs:test :deployable:test --max-workers=1` is green.
- `grep -rn 'IMPACTS\|REPLACES\|DELEGATES_TO' service/*/src/main` returns nothing.

## Definition of Done

- The three relations and the strength are gone.
- One retired-relation refusal is pinned by a fixture and a scenario.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- Per-value `impacts` lists on artifacts (ADR-0028 decision 1) are not the relation. Do not touch them.
- The `in_tension_with` spelling stays.

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP04 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
