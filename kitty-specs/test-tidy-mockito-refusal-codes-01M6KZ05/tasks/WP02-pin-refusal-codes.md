---
work_package_id: WP02
title: Pin the refusal code on the 15 type-only CharterRefusal checks
dependencies:
- WP01
requirement_refs:
- FR-002
- FR-003
- SC-002
- SC-003
planning_base_branch: test-tidy-mockito-refusal-codes
merge_target_branch: test-tidy-mockito-refusal-codes
branch_strategy: Planning artifacts for this mission were generated on test-tidy-mockito-refusal-codes. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-tidy-mockito-refusal-codes unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-tidy-mockito-refusal-codes-01M6KZ05
base_commit: 9d607babd17997ec59c78e9c8f7ad607afd467c6
created_at: '2026-11-04T20:36:00.139640+00:00'
subtasks:
- T005
- T006
- T007
- T008
phase: Phase 2 - Refusal codes
agent: claude
history:
- at: '2026-11-04T19:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RegistrationListsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/InterruptedCommitRepairTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/LinkedPackCommitTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackWriteSafetyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/TacticSaveContractTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — Pin the refusal code on the 15 type-only CharterRefusal checks

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Each of the 15 `assertThatThrownBy(...).isInstanceOf(CharterRefusal.class)` checks listed in #369 also asserts the
`RefusalCode` the production path raises, strengthened in place with no new test method (FR-002). A planted wrong
code turns a sample red (FR-003). No guard test that counts assertion shapes (C-001, `DIRECTIVE_041`).

## Branch Strategy

Planning base and final merge target: `test-tidy-mockito-refusal-codes`. The execution worktree is allocated per
computed lane from `lanes.json`; do not pick a base branch by hand. Depends on WP01 only for commit order.

```bash
spec-kitty agent action implement WP02 --agent claude --mission test-tidy-mockito-refusal-codes-01M6KZ05
```

## Context

The 15 sites (lines on `main` at `4b52bcb`), all under `service/packs/src/test/java/ai/speckitty/charter/packs/infra/`:

| File | Lines | What the check proves |
|---|---|---|
| `api/RegistrationListsTest.java` | 182 | a body without Content-Length, one byte over the limit, is refused (the sibling check above it already pins `VALIDATION_FAILED`) |
| `storage/DomainRepositoryServiceTest.java` | 86 | an unconfigured pack identifier is refused |
| `storage/InterruptedCommitRepairTest.java` | 339 | a write to a pack linked from outside the configuration root (dangling) is refused |
| `storage/InterruptedCommitRepairTest.java` | 382 | the `crash` helper: a commit that dies between the swap's two renames is refused |
| `storage/LinkedPackCommitTest.java` | 151 | an IOException from the swap is refused |
| `storage/PackCommitStoreTest.java` | 138 | an injected pre-swap failure is refused |
| `storage/PackWriteSafetyTest.java` | 71 | a failure between temp write and rename is refused |
| `storage/PackWriteSafetyTest.java` | 90 | a target that escapes the pack through a symlink is refused |
| `storage/PackWriteSafetyTest.java` | 106, 108 | identifiers `../elsewhere` and `a/b` are refused |
| `storage/TacticSaveContractTest.java` | 78, 85, 89 | create failing at rename; a climbing id; a dangling link at the new file's name |
| `storage/WholePackStoreTest.java` | 258, 593 | a path that leaves the pack; a sidecar whose payload is missing |

The target shape, as `ConsistentReadTest.java:68` already does:

```java
assertThatThrownBy(() -> ...)
        .isInstanceOfSatisfying(
                CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.X));
```

Keep every assertion that follows (bytes unchanged, listing unchanged, no residue, `as(...)` descriptions).

## Subtask T005 — Find each site's expected code from the production path

**Purpose**: pin the code the code raises today, not a guess.

**Steps**:
1. For each site, follow the call into main code (`FilesystemPackStore`, `PackCommitStore`, `WholePackStore`,
   `DomainRepositoryService`, `RegistrationLists`, `StorageRootLocations`) and find the `CharterRefusal` it throws
   and its `RefusalCode`.
2. Where the path is ambiguous, observe it: temporarily print `r.code()` in the test, run the class, then remove
   the print.
3. Record a table "site → code → throwing method" in the WP activity log.

## Subtask T006 — Strengthen the 15 checks in place

**Steps**:
1. Replace `.isInstanceOf(CharterRefusal.class)` with the `isInstanceOfSatisfying` shape and the code from T005.
   Add the `RefusalCode` import where missing.
2. Where a message is part of the contract and stable (as in `RegistrationListsTest:182`'s sibling), pinning the
   message as well is welcome but optional; the code is required.
3. Run `./gradlew spotlessApply` on the touched module before committing; then `:packs:checkstyleTest`.
4. No test method added or removed: the `:packs:test` execution count stays the same.

**Validation**: `./gradlew :packs:test -PrequireSymlinks=true` is green, with the same execution count as before
(record both counts).

## Subtask T007 — Planted-break proof (FR-003)

**Purpose**: show the strengthened checks are not vacuous.

**Steps**:
1. For each **distinct** expected code from T005, pick one site. In the production method that throws it,
   change the `RefusalCode` to another value (keep the exception type). Run that test class; it must fail on the
   code assertion. Revert. If two sites share a code but different throwing methods, sample both methods.
2. Capture each red run's failing assertion line (expected vs actual code) in the activity log; they go into the
   PR body.
3. Confirm `git diff -- service/packs/src/main` is empty after the reverts.

## Subtask T008 — Scope guard

**Steps**:
1. Re-run the scan from research R-01 (whole `assertThatThrownBy` statements ending in
   `.isInstanceOf(CharterRefusal.class)`) as a throwaway check: it must report 0. Do **not** commit it as a test.
2. Leave the other type-only assertions untouched (C-004).

## Definition of Done

- [ ] 15/15 sites assert a refusal code; 0 test methods added; 0 main-code changes.
- [ ] `:packs:test -PrequireSymlinks=true` green with an unchanged execution count.
- [ ] One planted break per distinct code (per throwing method) shown red, recorded.
- [ ] No counting or shape guard test; no suppressions; Spotless and Checkstyle clean.

## Reviewer guidance

- Spot-check two sites by planting a different code yourself.
- Confirm the symlink sites (`PackWriteSafetyTest:90`, `TacticSaveContractTest:89`, `InterruptedCommitRepairTest:339`)
  ran rather than skipped (`-PrequireSymlinks=true`).
- Confirm no post-condition assertion was dropped.
