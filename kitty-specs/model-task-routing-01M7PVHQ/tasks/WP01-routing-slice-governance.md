---
work_package_id: WP01
title: The routing slice and its governance
dependencies: []
requirement_refs:
- NFR-003
- C-001
subtasks:
- T001
- T002
- T003
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-11-18T08:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/routing/
create_intent:
- service/routing/build.gradle.kts
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/package-info.java
- service/routing-edge/build.gradle.kts
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/application/package-info.java
execution_mode: code_change
owned_files:
- settings.gradle.kts
- service/routing/build.gradle.kts
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/package-info.java
- service/routing-edge/build.gradle.kts
- service/routing-edge/src/main/java/ai/speckitty/charter/routing/application/package-info.java
- service/charter-reports/build.gradle.kts
- service/deployable/build.gradle.kts
- .github/workflows/sonar.yml
- .github/workflows/mutation-testing.yml
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- docs/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam.md
- docs/adr/0006-onion-vertical-slices-archunit.md
role: implementer
task_type: implement
---

# Work Package Prompt: WP01 – The routing slice and its governance

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according to
its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`). Address every feedback item, and record
each change with `spec-kitty agent tasks add-history WP01 --note "..."`.

## Branch Strategy

- **Planning base branch**: `feat/108-model-task-routing`
- **Merge target branch**: `feat/108-model-task-routing`
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by hand.

```bash
spec-kitty agent action implement WP01 --agent claude --mission model-task-routing-01M7PVHQ
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

Create two governed modules that later WPs fill: `routing`, a Spring-free domain module like `service/profiles`, and
`routing-edge`, the use case and inbound adapter like `service/profiles-edge`. When this WP is done, ArchUnit knows the
slice, and nothing in it can reach where it should not (plan IC-00; squad finding A3).

## Context

- The precedent is `service/profiles` (domain; `packs` depends on it, because the storage binding names its types)
  and `service/profiles-edge` (application and `infra/api`; it depends on `profiles`, `packs`, `activations`,
  `web-transport` and `charter-api`). Read both `build.gradle.kts` files and copy their shape.
- Dependency direction: `packs → routing` (the binding, in WP05), and `routing-edge → routing, packs, activations`.
  There is no cycle: `routing` depends on `kernel` only.
- Base package `ai.speckitty.charter.routing`, as `profiles` and `profiles-edge` share `ai.speckitty.charter.profiles`.

## Subtasks

### T001 – Create the modules and wire the build

1. In `settings.gradle.kts`, add `"routing"` and `"routing-edge"` to the list beside `profiles` and `profiles-edge`,
   with a one-line comment each, as the list has.
2. Create `service/routing/build.gradle.kts` modelled on `service/profiles/build.gradle.kts`, with
   `api(project(":kernel"))` only.
3. Create `service/routing-edge/build.gradle.kts` modelled on `service/profiles-edge/build.gradle.kts`, with `routing`,
   `packs`, `activations`, `web-transport` and `charter-api`.
4. Each module gets a `package-info.java` (the null-marked package annotation the other modules use, so NullAway
   covers it) at the paths in `owned_files`.
5. Add `packs → routing` as an `api` dependency in `service/packs/build.gradle.kts` in WP05, not here: `packs` names
   no routing type yet. Record this in the history.
6. Wire the modules where every hand-written module is listed:
   - `service/deployable/build.gradle.kts`: `routing-edge`.
   - `service/charter-reports/build.gradle.kts` `serviceModules`. It throws on a missing module.
   - `.github/workflows/sonar.yml`, near line 160: the module list.
   - `.github/workflows/mutation-testing.yml`: the module list, if it enumerates modules.

**Validation:** `./gradlew :routing:build :routing-edge:build :charter-reports:tasks` succeeds.

### T002 – Register the slice in ArchUnit

In `service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java`:

- Add `routing` to `SLICES` (around line 88) and to `DOMAIN_RINGS`. `SLICE_COVERAGE` fails until you do; leave it
  failing loudly until the slice is in.
- Add `ROUTING_DOMAIN_IS_PURE`, modelled on the profiles purity rule: `routing.domain` names no Spring, no `packs`, no
  `activations` and no `infra`. Add its bite control: a planted class that the rule must reject, as the existing
  purity rules have.
- Add `routing.domain` to `ENTRYPOINT_STAYS_IN_ITS_SLICE` (around lines 741–746, which list the PROFILES and MISSIONS
  domains), so `packs.infra.api` cannot name it.
- Add `routing.domain` to `OUTSIDE_THE_DOMAIN` (around lines 297–306) if `pack-validation` will reach it. It will
  through `PackContents` facts (WP05) only if a rule names a routing type; prefer that it does not.
- Name each rule after its intent, as the file does, and give it a `because(...)` sentence.

**Validation:** `./gradlew :deployable:test --tests '*HexagonalArchitectureTest*'` passes, and each bite control
fails when its rule is disabled.

### T003 – Amend ADR-0037 and ADR-0006

- In `docs/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam.md`, add an amendment of 2026-11-18. It names
  `routing` among the slices whose domain `packs.infra.storage` binds (beside profiles and missions), cites ADR-0080,
  and gives the reason: the catalog's types and the ranker live in one pure module.
- In `docs/adr/0006-onion-vertical-slices-archunit.md`, add `routing` and `routing-edge` to the slice list, where its
  amendments list the slices.
- Keep `updated:` in the frontmatter current, and run `python3 scripts/docs/check_docs.py` (frontmatter, related,
  links, adr).

## Definition of Done

- `routing` and `routing-edge` build, are listed everywhere modules are listed, and are checked by ArchUnit with a
  purity rule that has a bite control.
- ADR-0037 and ADR-0006 name the slice.
- `./gradlew verifyAll` passes on Java 25 for the modules this WP touches, with no new style-gate finding.
- Every subtask's status is recorded with `spec-kitty agent tasks mark-status`.
- Nothing outside `owned_files` changed, except a recorded edit outside the map, named in the WP history with its
  reason.

## Reviewer Guidance

- Check the dependency direction (`routing` depends on `kernel` only) and that no rule was weakened to pass.
- Check that each bite control actually fails without its rule.
- **Independent reviewer.** Review is by a separate reviewer session, never by the implementer.
