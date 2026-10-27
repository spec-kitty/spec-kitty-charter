---
work_package_id: WP02
title: Round-trip seam and domain adapter
dependencies: []
requirement_refs:
- FR-002
- FR-005
- FR-011
- NFR-004
planning_base_branch: feat/whole-pack-round-trip-gate
merge_target_branch: feat/whole-pack-round-trip-gate
branch_strategy: Planning artifacts for this mission were generated on feat/whole-pack-round-trip-gate. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/whole-pack-round-trip-gate unless the human explicitly redirects the landing branch.
subtasks:
- T004
- T005
- T006
- T007
- T008
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTripTest.java
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTripTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — Round-trip seam and domain adapter

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Define the harness's own seam, `WholePackRoundTrip`, and today's adapter behind it, `DomainRoundTrip`: read a
pack directory **into the domain**, write it into an empty directory **through the existing storage**, read the
written pack back, and report one outcome per source file of the gate kinds it models (spec FR-002, FR-005,
FR-011; research R-3). #189 will later put a whole-pack adapter behind the same seam (C-001: never edit
`Pack.java` or `FilesystemPackStore`).

## Branch Strategy

Planning base and final merge target: `feat/whole-pack-round-trip-gate`; lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP02 --agent claude --mission whole-pack-round-trip-gate-01M5Y272
```

## Subtask T004 — The seam

```java
/** Reads a pack into the domain and writes it back; the gate's only dependency on storage. */
interface WholePackRoundTrip {
    /** The gate kinds this adapter models (e.g. "directive", "pack-descriptor", "graph-shard"). */
    Set<String> modelledKinds();

    /**
     * @param source the pack directory to read (never written)
     * @param target an empty directory that becomes the written pack
     * @param kindOf the gate kind of every pack-relative source path (from the census)
     * @return one outcome per source path whose kind is modelled
     */
    Map<String, Outcome> roundTrip(Path source, Path target, Map<String, String> kindOf);

    sealed interface Outcome permits Written, Refused {}
    /** @param writtenPath the target-relative path of the written file */
    record Written(String writtenPath) implements Outcome {}
    record Refused(String reason) implements Outcome {}
}
```

Javadoc names #189 as the intended replacement owner of the adapter (see-reference, not an instruction).

## Subtask T005 — Fixture packs

Build fixtures **in the test** (`@TempDir` + text blocks), not as resources, so each test shows its input:
a minimal pack with `pack.yaml` (pack_id, pack_version, name, pack_level built-in), one file each of directive,
tactic, styleguide, toolguide, paradigm (copy small real examples from the built-in pack — keep them valid against
the bundled schemas), and one `tactic.graph.yaml` with two nodes and two edges. Put tactic files in a
`tactics/` subdirectory to prove written paths differ from source paths.

## Subtask T006 — Artefacts and descriptor

`DomainRoundTrip(SerializationStrategy strategy)` (production use: `new YamlSerializationStrategy()`):

1. `source` service: `new DomainRepositoryService(() -> List.of(new PackLocation(NAME, PackLevel.BUILT_IN,
   source)), strategy)`; `target` service likewise over `target` (create the directory). `NAME` = source dir name.
2. `Pack pack = sourceService.load(new PackIdentifier(NAME))`.
3. `targetService.save(id, pack.descriptor())` → `pack.yaml` outcome `Written("pack.yaml")`.
4. Save every tactic, directive, styleguide, toolguide, paradigm of `pack` through `targetService.save(..)`.
5. Pair source and written files by `(kind, id)`: parse each source file of a modelled artefact kind with
   `Yaml.load` and take its `id`; scan `target` for `*.<token>.yaml` likewise. A source file with no written twin
   → `Refused` with the matching `pack.violations()` text for that file, or "not written (no domain element)".
6. Read violations (`pack.violations()`, each names its file) map to `Refused(<field>: <message>)` for that path.
   Inspect `PackElementViolation` for its accessors.

## Subtask T007 — Graph shards and read-back

- For each source path of kind `graph-shard`: `GraphDocumentReader.read(text, Shape.SHARD, path)` →
  `PackPolicy.graph(NAME, raw)` → `GraphShardWriter.write(build.graph(), generatedBy)`, with `generatedBy` taken
  from the source document's `generated_by` (null when absent). Write to the same relative path under `target`.
  Unreadable → `Refused`. Check `GraphBuild` for diagnostics and surface them in the reason if the graph is partial.
- Read-back: `targetService.load(id)`; any violation in the written pack turns the paired source path's outcome
  into `Refused("read-back: …")`. Graph shards: re-read the written text with `GraphDocumentReader`; unreadable →
  `Refused`.
- Deterministic: iterate in sorted path order.

## Subtask T008 — Tests (write alongside T006/T007, first as failing)

- Happy path: every fixture file → `Written`; written `pack.yaml` exists; written tactic is at the target root as
  `<id>.tactic.yaml` while the source lived in `tactics/`.
- Unknown field: add `x_unknown: 1` to the fixture tactic → that path is `Refused` naming `x_unknown`; siblings
  still `Written`.
- **Positive control (lossy writer)**: wrap the strategy so `writeTactic` drops `notes` (decorator delegating all
  other calls); the outcome is still `Written`, and `MeaningComparator`-free assertion: the written text lacks the
  key that the source has — proves the gate will see it (the comparator itself is WP01).
- Source directory unchanged after the run (hash every file before/after).
- `modelledKinds()` equals exactly the seven gate kinds.

## Definition of Done

- `./gradlew :packs:test --tests '*DomainRoundTripTest'` green; style gates clean; no production change.
- Note for #189 drafted in the WP's activity log (posted by WP04/closeout).

## Reviewer guidance

Check that nothing calls `FilesystemPackStore` directly or edits `Pack.java`; that pairing is by `(kind, id)`
and fails loudly on a duplicate id; that a refused read is never reported as written.
