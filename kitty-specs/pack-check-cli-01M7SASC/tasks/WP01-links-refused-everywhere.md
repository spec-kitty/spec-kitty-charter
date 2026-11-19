---
work_package_id: WP01
title: 'Links are refused on every surface (D1, D6, #597)'
dependencies: []
requirement_refs:
- FR-008
- SC-004
- NFR-005
planning_base_branch: feat/478-pack-check
merge_target_branch: feat/478-pack-check
branch_strategy: Planning artifacts for this mission were generated on feat/478-pack-check. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/478-pack-check unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-check-cli-01M7SASC
base_commit: e369693c8030d76ec1064f5fc0e65fedeef05e96
created_at: '2026-11-19T08:21:21.123645+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: Phase 1 - Foundations
history:
- at: '2026-11-19T08:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/LinkRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/LinkedEntriesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationLinkTest.java
- contracts/charter/changes/2026-11-19-pack-entry-linked.md
- service/packs/src/testFixtures/resources/planted/pack-entry-linked-file/expected.json
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StoreRead.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageCodes.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/LinkRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/AssemblyRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/LinkedEntriesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationLinkTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCodesCoverageTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionFilesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackWriteSafetyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/TacticSaveContractTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
- service/packs/src/testFixtures/resources/planted/asset-path-escape-through-a-symlink/**
- service/packs/src/testFixtures/resources/planted/asset-path-inside-through-a-symlink-is-contained/**
- service/packs/src/testFixtures/resources/planted/asset-path-symlink-loop/**
- service/packs/src/testFixtures/resources/planted/pack-entry-linked-file/**
- service/packs/src/test/resources/golden/surfaces.json
- service/pack-validation/src/test/resources/golden/validate.json
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/scenarios/registration/whole-pack-registration.feature
- contracts/charter/changes/2026-11-19-pack-entry-linked.md
- docs/development/notes/pack-code-unification.md
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 — Links are refused on every surface (D1, D6, #597)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

A symbolic link inside a pack root is never read through. Every surface refuses it with one code, `pack_entry_linked`
(`VIOLATION`):

- registration of a staged tree;
- validate mode, and so `pack-check`;
- the service's own load of a pack in a configured directory.

The pack root itself may still be a link (#174). This delivers rulings D1 and D6 and closes #597.

## Context

- Spec: FR-008, SC-004, User Story 5. Rulings D1 and D6 (spec.md). research.md R8, squad S2, S7, S9 and S17, and the
  D6 landing check (no committed symlink in charter or inside any open-packs pack).
- The one reader walks a tree once: `PackFileSet.walk` uses `FileCollector`, which today puts a *followed* file link
  into `regular` (`Files.isRegularFile` follows links) and also into `links`. `CensusEntries` gives each link standing
  `LINK`, and no rule judges it.
- Registration and validate mode share `StorageRulesets.verdict(read)` (#554 S6). The configured load
  (`DomainRepositoryService.compositionFacts`, `WholePackStore.loadStored`/`loadStrict`) runs only the store half
  (`StoreRead.of(…, PackFileSet.walk(…), …)`) and judges nothing else. ADR-0075, amendment of 2026-11-16: "the store
  half states some content rules itself … so that the composing read refuses a pack that breaks them".
- #597's probe staged a valid org pack plus one link each to:
  - a directive file outside the pack;
  - a directory outside the pack;
  - a text file outside the pack.

  All three registered `CREATED` with no diagnostic.

## Branch Strategy

Planning base `feat/478-pack-check`, merge target `feat/478-pack-check`. Execution worktrees are allocated per computed
lane from `lanes.json`; run `spec-kitty agent action implement WP01 --agent <name> --mission pack-check-cli-01M7SASC`.

## Subtasks

### T001 — The walk lists no link as a regular file

- **Purpose:** no read on any surface follows a link (S2). Every reader takes its files from the walk
  (`Walk.regular`/`unlinked`), so this one change closes reading through a link everywhere.
- **Steps:**
  1. In `PackFileSet.FileCollector.visitFile`, add to `found` only an entry whose own attributes are a regular file
     (`attrs.isRegularFile()`). Keep `links` as is.
  2. Check every caller of `Walk.regular()` and of `Files.isRegularFile` on a pack-relative path in
     `FileSystemPackInspector`, `PackFileSet` (`PACK_YAML`, `FRAGMENT`, `MANIFEST`, `PolicyYaml.FILE`,
     `graphShards(..., Files::isRegularFile)`) and `StoreRead`. A path that resolves through a link must not be read.
     - Use `LinkOption.NOFOLLOW_LINKS` where a path is probed directly.
     - The sidecar asset path check in `FileSystemPackInspector` (its own `MAX_LINKS` resolution) judges containment
       only. Leave it, but confirm it never opens the target.
  3. Keep `walk` entering a root given through a link (`directory.toRealPath()`, #174).
- **Tests** (new `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/LinkedEntriesTest.java`; each
  plant skips where the file system cannot make a link, or fails under `-PrequireSymlinks=true`, as the existing link
  plants do):
  - a pack root given through a link is read whole (#174), with the same verdict as the real path;
  - a file link (`directives/x.directive.yaml` → a valid directive outside) is in `links`, not in `regular`;
  - a directory link (`directives/more` → outside), a dangling link and a link loop are in `links` only;
  - a link to a file *inside* the pack is still a link.
  - **Marker test:** the target file holds a unique marker string. After a full read (`FileSystemPackInspector.read`)
    and a `StoreRead`, the marker appears in no `Reason` detail, no `PackContents` value and no exception message.

### T002 — `pack_entry_linked` in the storage rules

- **Steps:**
  1. `StorageCodes`: add `PACK_ENTRY_LINKED = violation("pack_entry_linked")` to `ORDERED` (and so `ALL`), at its
     precedence place. Put it before `FILE_NOT_HELD`: it is a structural fault of the tree.
  2. New `packs.infra.storage.rules.LinkRules`, a kernel rule over `TreeRead` that returns one `Reason` per census
     entry of standing `LINK`:
     - code `pack_entry_linked`, location the link's pack-relative path;
     - detail `is a symbolic link; a pack holds no links` (the one wording; `PackCodes.detail(file, detail)` prefixes
       the file).
  3. Add it to `StorageRulesets.HOLDING`.
  4. Make sure exactly one `Reason` per link reaches the verdict: if T003's store-half statement also surfaces through
     `AssemblyRules.held`, de-duplicate there. Never state the fault twice.
  5. `PackCodesCoverageTest` (`service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/`): the new code's
     failing fixture (a link) and passing fixture (the same tree without it).
- **Tests:** `StorageRulesetsTest` gains the link case; `StorageCodesTest` covers the new token.

### T003 — The store half states it, so the configured load refuses a linked pack

- **Purpose:** D6. A pack placed in a configured directory never runs the verdict, so the store half must refuse the
  link itself.
- **Steps:**
  1. In `StoreRead.of`, when `walk.links()` is not empty, record one `pack_entry_linked` violation per link on the
     aggregate, at the link's path. Follow the pattern the store half already uses for `duplicate_step_id` and the
     prompt slots (see the `StoreRead` javadoc near the store-half content rules, about lines 390 to 430).
  2. The composing read must refuse the pack, as it refuses one with `duplicate_step_id`. The validate-mode readout
     must still list every other fault of the pack, so record the link on the aggregate rather than short-circuiting
     to `Assembly.Refused`, unless the existing pattern requires it.
- **Tests:**
  - `WholePackStore` loads a configured pack holding a link and does not serve it: the composing read refuses with
    `pack_entry_linked`, and the offering omits or refuses it exactly as for another store-half content fault. Find
    the existing test of a configured pack with `duplicate_step_id` and mirror it.
  - The same pack without the link composes.

### T004 — Planted subjects, goldens, existing link tests, and #597's cases

- **Steps:**
  1. Planted subjects that plant links (`service/packs/src/testFixtures/resources/planted/`):
     `asset-path-escape-through-a-symlink`, `asset-path-inside-through-a-symlink-is-contained` and
     `asset-path-symlink-loop`.
     - Each now also expects `pack_entry_linked` at its link (`assets/out`, `assets/in`, `assets/loop`).
     - Update each `expected.json` (canonical model change: links are refused). Keep the `asset` finding only if the
       reader still states it, and record which in the commit message.
     - Add one new subject, `pack-entry-linked-file`: a file link at a directive path.
  2. Regenerate or update the goldens: `service/packs/src/test/resources/golden/surfaces.json` (via
     `SurfaceGoldenTest`'s documented update switch) and
     `service/pack-validation/src/test/resources/golden/validate.json` (`-Dgolden.update=true` on
     `ValidateGoldenTest`). Review the diff: only link subjects may change.
  3. Existing tests that create links:
     - `CompanionFilesTest`, `PackWriteSafetyTest`, `TacticSaveContractTest`, `GeneratedPackTree`, `PackIdRulesTest`,
       `SurfaceGoldenTest`;
     - `PackCommitStore`'s root-link case (`PackCommitStore.java` about line 66);
     - `RegistrationWiringStagingTest` (deployable);
     - `LocalSourceReaderTest` and `GitHostTest` (stagers, which already refuse links: unaffected).

     Run each. Where one asserts that content behind a link is read, change it to assert the refusal, with a one-line
     reason. A root link stays allowed. Do not weaken an assertion to get green; never skip a test.
  4. #597's acceptance: new
     `service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationLinkTest.java` uses
     `PackRegistrationServiceTest.Rig`. It stages a valid org pack plus a link to:
     - a valid directive outside the pack;
     - a directory outside the pack;
     - an unmodelled text file outside the pack.

     Each answers `validation_failed` with a `pack_entry_linked` violation at the link's path, and nothing is stored.
     Prove the test bites: removing `LinkRules` from `HOLDING` makes it fail (describe the check in the commit).

### T005 — The contract and the R2 table

- **Steps:**
  1. `contracts/charter/schemas/CharterRefusalCode.yaml`: add `pack_entry_linked` to the `validation_failed`
     description's list of violation codes, next to `reserved_shard`. Say what it means: a symbolic link inside the
     pack, refused on registration and at load; the pack root itself may be a link.
  2. `contracts/charter/scenarios/registration/whole-pack-registration.feature`: a scenario modelled on the
     `duplicate_artifact_id` violation scenario: a staged pack holding a link is refused `422 validation_failed` with
     the violation at the link's path.
  3. `contracts/charter/changes/2026-11-19-pack-entry-linked.md`: one `### Breaking` block (ADR-0022; see
     `contracts/charter/changes/README.md`). It says a pack holding a link is now refused at registration and at load,
     the new violation code, and what a consumer must change (remove the link).
  4. `docs/development/notes/pack-code-unification.md`: a row for `pack_entry_linked` in the R2 table (owner
     `StorageCodes`, surfaces: registration, validate, configured load).
- **Validation:** `./gradlew :contracts:check` (the fragment check), then `verifyAll`.

## Definition of Done

- `./gradlew verifyAll` is green on Java 25, with `-PrequireSymlinks=true` where CI passes it.
- No reader follows a link. The marker test proves that no target bytes reach any output.
- One `pack_entry_linked` per link on registration, validate mode and the configured load. The pack root may be a
  link.
- #597's three cases are refused. The planted subjects and goldens are updated, and their diffs touch only link
  subjects.
- Contract: the code is documented, with a scenario and a `### Breaking` fragment.

## Risks and reviewer guidance

- **Hidden dependence on link-following in tests.** Read every changed assertion: a refusal replaces a read, never a
  deleted check.
- **Duplicate statements.** Assert the count of `pack_entry_linked` reasons equals the number of links.
- **Windows junctions** read as "other" and are skipped with no finding. Hard links are not links. Both are out of D1's
  scope and are documented by WP07, not tested here.
