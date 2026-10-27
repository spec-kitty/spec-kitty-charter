---
work_package_id: WP01
title: Companion-file seam
dependencies: []
requirement_refs:
- FR-005
- C-004
planning_base_branch: feat/192-glossary-pack-skill-round-trip
merge_target_branch: feat/192-glossary-pack-skill-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/192-glossary-pack-skill-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/192-glossary-pack-skill-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-glossary-pack-skill-round-trip-01M5YVEC
base_commit: 43ce93f9faed174f583cb58b0d726f32b78e46b3
created_at: '2026-10-27T16:18:39.753298+00:00'
subtasks:
- T001
- T002
- T003
- T004
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Companion
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompanionContent.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionFilesTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompanionContent.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionFilesTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP01 — Companion-file seam

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Let a `KindBinding` declare **companion files**: text files that an artifact names, relative to its own file, which
are read, contained, and upserted together with it (spec FR-005; plan "Companion files (IC-01)"; post-plan squad
P1 to P9). No real kind uses the hooks in this WP; a test-only fixture binding proves them. The existing six bound
kinds must behave byte for byte as before.

Out of scope here (WP05): `WholePackStore` and `PackFileSet.digested`. They iterate the registry, so they are
tested once the skill is bound.

## Branch Strategy

Planning base and merge target: `feat/192-glossary-pack-skill-round-trip` (stacked on #191's branch; rebased onto
`main` when #191 merges). Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP01 --agent claude --mission glossary-pack-skill-round-trip-01M5YVEC`.

## Patterns to mirror

- `FilesystemPackStore.writeAtomically`, `realPathContained`, `readText` (UTF-8 with `REPORT`), `newFile`
  ("never over an existing file").
- `PackElementViolation(file, kind, reason)` for read failures; `CharterRefusal(PACK_UNREADABLE, …)` for write
  refusals.
- Javadoc density and wording of the surrounding storage classes; no Lombok, records for values.

## Subtasks

### T001 — `CompanionContent`, `Companion`, and the `KindBinding` hooks

**Files**: `CompanionContent.java` (new), `KindBinding.java`.

1. `CompanionContent` is a package-private `sealed interface` in `packs.infra.storage` with one case,
   `record Text(String value)` (null-checked). Javadoc: bytes (`Bytes`) are #193's to add; until then a companion
   is UTF-8 text, read and written byte for byte.
2. In `KindBinding`, add a nested `record Companion(String path, Optional<CompanionContent> content)`:
   `path` is relative to the artifact's file, exactly as the artifact declares it; `content` is empty before the
   store reads it.
3. Add two default methods:

   ```java
   default List<Companion> companions(D dso) { return List.of(); }
   default D withCompanions(D dso, Map<String, CompanionContent> contents) { return dso; }
   ```

   `withCompanions` receives contents keyed by the `path` strings `companions(dso)` returned.
4. Amend the class javadoc sentence "A binding knows no format and no path" (ADR-0040, P8): a binding names no
   format, and no path except its companions', relative to its artifact's file.

### T002 — Reading companions

**File**: `FilesystemPackStore.java` (`readKind`, `readOne`, plus private helpers).

After a file of the kind reads (`FileRead.Read<D>`), resolve its companions:

1. For each `Companion` of `kind.companions(dso)`:
   - **Path rule** (a static helper, reused by T003 and by WP05): the path is not blank, not absolute, and does not
     end in `.yaml` (case ignored). Resolve it against the artifact file's parent directory and normalize; the
     result must start with the pack directory and differ from it.
   - The target must be a regular file **without following links** (`Files.isRegularFile(p, NOFOLLOW_LINKS)`) and
     `realPathContained(packRoot, target)` must hold.
   - Read it with `readText` (UTF-8, malformed input reported).
2. Any failure → one `PackElementViolation(file, "companion", "<path>: <why>")` where `why` is one of
   `missing`, `leaves the pack`, `is a YAML file`, `is not a regular file`, `cannot be read as UTF-8 text`,
   `named by two artifacts`; the artifact is **not** stored.
3. A companion already claimed by another artifact in this read (compare the normalized pack-relative path,
   lower-cased with `Locale.ROOT`) is `named by two artifacts` for the second one (P5). Keep the claimed set across
   all kinds of one `read`.
4. Otherwise store `kind.withCompanions(dso, contents)` (contents keyed by the declared path).
5. `readOne` applies the same resolution; a companion failure there yields `Optional.empty()`.

Keep `body_path`'s declared text untouched in the DSO; only the normalized form is used for file access.

### T003 — Upserting companions

**File**: `FilesystemPackStore.java` (`write`).

1. Before writing the artifact file, for each `Companion` of `kind.companions(dso)`:
   - Content must be present; else refuse `CharterRefusal(PACK_UNREADABLE, "<token> '<id>': companion <path> has
     no content")`.
   - Apply the path rule against the **target** file's directory; refuse on a breach.
   - New artifact (no located file): refuse when the companion target already exists (`NOFOLLOW_LINKS`), like
     `newFile`.
   - Write through `writeAtomically` (it re-checks containment).
2. Replaced artifact (a located file): read the old file through the strategy; for each companion it named that the
   new DSO does not name (normalized path), delete it if it is a regular file inside the pack (P3). A failed old read
   deletes nothing.
3. Then write the artifact file as today. Document in the method javadoc: companions are written first, then the
   artifact; atomic as a pair only through the commit path's directory swap (`PackCommitStore`), which is the only
   writer of companion-bearing kinds (P4).

### T004 — Tests

**File**: `CompanionFilesTest.java` (new, `packs.infra.storage`).

Build a test-only fixture: a small DSO record (`id`, `Optional<String> notePath`, `Optional<String> note`) and a
`KindBinding` over it whose `kind()` is `ArtifactKind.TACTIC` (so `PackFileSet.files` finds its `*.tactic.yaml`
files), paired with a stub `SerializationStrategy` that reads and writes a minimal `id:` / `note_path:` text. The
fixture never enters `KindBindings` or `YamlFormats`. Drive `FilesystemPackStore.readAll`, `readOne` and `write`
directly with the fixture binding and the stub strategy.

Cases (each a `@Test` with a `@DisplayName`):

- reads an artifact with its companion text byte for byte (CRLF, no final newline, a non-ASCII char preserved);
- missing companion, `../` escape, absolute path, `.yaml` target, a symlinked companion, non-UTF-8 bytes,
  two artifacts naming one companion (case-different) → one violation each, naming the path; artifact absent;
- upsert of a new artifact writes the companion and refuses an existing companion target;
- upsert of a replaced artifact whose path changed deletes the old companion and writes the new;
- upsert with an empty content is refused; nothing is written;
- an artifact with no companions behaves as before (positive control on an existing kind's file text).

## Definition of Done

- `./gradlew :packs:test --tests '*CompanionFilesTest*' --tests '*FilesystemPackStore*'` green on Java 25.
- Every refusal case asserts the exact reason text and that no file changed.
- Existing tests unchanged and green (`./gradlew :packs:test`).
- Spotless/Checkstyle/Error Prone/NullAway clean (`./gradlew :packs:check`).

## Risks / reviewer guidance

- The path rule must be one helper used by read, upsert and (WP05) digest; a reviewer should find no second copy.
- Deleting an old companion must never reach outside the pack or delete a `.yaml`.
- No change in behaviour for kinds without companions: diff the hot paths carefully.
