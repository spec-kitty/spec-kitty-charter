---
work_package_id: WP03
title: Pack revision over bytes
dependencies: []
requirement_refs:
- FR-011
- C-001
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-asset-template-round-trip-01M5YY7G
base_commit: e6429ca8251ae651e970d83c79621604377680e4
created_at: '2026-10-27T17:23:06.100576+00:00'
subtasks:
- T011
- T012
- T013
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRevision.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackCommitStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CommitStorePackWriteStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRevisionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
role: implementer
tags: []
task_type: implement
---
# Work Package Prompt: WP03 — Pack revision over bytes

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Make the pack revision (the ETag and the commit store's If-Match check) computable over files that are not UTF-8
text, before WP05 adds binary payloads to the fingerprinted file set. Today `PackCommitStore.revisionOf` and
`CommitStorePackWriteStore.currentRevision` read every digested file with `Files.readString(UTF_8)` and call
`PackRevision.compute(List<String>)` (application ring); a PNG or OTF payload would throw and turn every ETag and
commit on `packs/internal` into `PACK_UNREADABLE` (post-plan P4). Read `plan.md` D5/D6, `research.md` P4, and
`PackRevision`, `PackRevisionTest`, `PackCommitStore`, `CommitStorePackWriteStore`, `PackCommitStoreTest` first.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Execution worktrees are allocated per
computed lane from `lanes.json`; do not pick a base branch by hand. Implement with:

```bash
spec-kitty agent action implement WP03 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Validation discipline

Run the targeted tests for the files you touch and the owning module's fast tier (`./gradlew :packs:test
:packs:check`; `:deployable:test` when named). Never run a whole-repo heavy sweep (`verifyAll` is CI's,
directive NO_FULL_HEAVY_SUITES_IN_MISSION); record the exact commands and pass/fail counts in the review handoff.
Java 25 via `JAVA_HOME=/usr/lib/jvm/temurin-25`; Maven Central HTTP 429 → wait and retry.

## Subtasks

### T011 — `PackRevision.computeOverBytes`

- Read `compute(List<String>)` (domain tag, per-entry framing, SHA-256, hex). Add
  `public static PackRevision computeOverBytes(List<byte[]> committedContent)` with the **same framing over the raw
  bytes**, and make `compute(List<String>)` delegate to it by UTF-8 encoding each entry (an overload on `List<byte[]>`
  would clash by erasure with `List<String>`; keep the String method — ~15 test callers outside this WP use it).
- `PackRevisionTest`: for several text inputs (empty list, one entry, entries with non-ASCII text and newlines)
  `compute(texts)` equals `computeOverBytes(utf8(texts))` **and** equals a literal hex captured from `main` at 90de8f9
  (capture it by running the current `compute` before your change; record how in a comment) — so the values every
  existing pack has did not move. A byte array with a 0x89 lead and NUL bytes computes; a one-byte variant differs.

### T012 — Revision readers over bytes

- `PackCommitStore.revisionOf` and `CommitStorePackWriteStore.currentRevision`: read with `Files.readAllBytes`, call
  `computeOverBytes`. No other behaviour change.
- `PackCommitStoreTest`: a staged pack holding a non-UTF-8 file in its digested set (until WP05 lands, plant it as a
  file the digest already covers — e.g. a YAML-kind file path with invalid UTF-8 bytes — or drive `revisionOf` through
  the narrowest seam the class offers) commits and reports a revision without `PACK_UNREADABLE`; for a pack of UTF-8
  files the revision equals the value before this change (literal).

### T013 — If-Match over bytes

- Where `CommitStorePackWriteStore.currentRevision` is tested (find its test; if none, add the case to
  `PackCommitStoreTest`): If-Match with the reported revision passes; after a one-byte edit of a digested file it
  fails with the existing precondition refusal. Same fixture for both.

## Definition of Done

- `./gradlew :packs:test --tests '*PackRevision*' --tests '*PackCommitStore*'` then `:packs:test :packs:check` green;
  `:deployable:test` green (ETag paths).
- Revision values for UTF-8 packs unchanged (literal pins).

## Risks / reviewer guidance

- Reviewer: confirm the framing is identical (a re-implementation with a different separator would silently move
  every ETag); the literal pin must come from `main`.
