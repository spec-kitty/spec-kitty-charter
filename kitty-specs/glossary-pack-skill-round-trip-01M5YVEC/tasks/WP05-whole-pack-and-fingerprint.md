---
work_package_id: WP05
title: Whole pack and fingerprint carry companions
dependencies:
- WP04
requirement_refs:
- FR-003
- FR-004
- NFR-002
planning_base_branch: feat/192-glossary-pack-skill-round-trip
merge_target_branch: feat/192-glossary-pack-skill-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/192-glossary-pack-skill-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/192-glossary-pack-skill-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-glossary-pack-skill-round-trip-01M5YVEC
base_commit: bb335f920fd22994ef714d7728c3046654170018
created_at: '2026-10-27T17:00:03.531410+00:00'
subtasks:
- T017
- T018
- T019
phase: Phase 2 - The kinds
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionDigestTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionDigestTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP05 — Whole pack and fingerprint carry companions

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Make the whole-pack seam and the source fingerprint carry companion files (spec US2 scenarios 3-4, FR-003, FR-004,
NFR-002; plan IC-01 "Whole-pack write", "Whole-pack load", "Digest"; post-plan P2, R4, R6).

## Branch Strategy

Planning base and merge target: `feat/192-glossary-pack-skill-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP05 --agent claude --mission glossary-pack-skill-round-trip-01M5YVEC`.

## Subtasks

### T017 — `WholePackStore`

- **Load**: for every stored artifact of a binding, add its companions' normalized pack-relative paths
  (`parent(file)/path`) to the `modelled` set, so no body is listed in `unmodelled` (R6). Use WP01's path helper.
- **Write**: after `put` of an artifact at its layout path, `put` each companion of `kind.companions(dso)` at
  `parent(layout path)/path` with its text. A companion without content refuses the write
  (`CharterRefusal(PACK_UNREADABLE, …)`) — it cannot happen after a load, so it signals a caller bug.
- Update the class javadoc: the write puts each artifact's companions beside it (ADR-0040).

### T018 — `PackFileSet.digested`

After the files of each bound kind (inside the per-kind loop, after each file), add that file's companions:

- Read the file text; read it through the store's schema-valid read path (a static `YamlSerializationStrategy`
  instance, as `PackFileKind`/`locate` already parse YAML statically — javadoc says why: one source of truth,
  `binding.companions(dso)`, and no constructor change for the three digest callers; ADR-0040, post-plan P2).
- A companion is added when it passes WP01's path rule, is a regular file (no links followed) inside the pack, reads
  as UTF-8, and is not already in the list (R4).
- A file that does not read contributes only itself.
- Kinds without companions take no extra read. `PackFileSet` may not name a kind (ADR-0037), so the binding says
  it: add `default boolean hasCompanions() { return false; }` to `KindBinding` beside WP01's hooks (an out-of-map
  edit to a WP01 file, recorded in the commit), overridden `true` by `SkillBinding` (out-of-map, WP04's file).
- Update the class javadoc ("a file the service loads cannot escape the fingerprint" now includes companions).

### T019 — Tests

- `WholePackStoreTest`: add a case with a pack holding a skill and its body (and a glossary pack): load → the body
  is not in `unmodelled`; write → the written pack has the body byte-identical beside the skill; reload → same DSOs
  (body included). A pack whose skill names a missing body loads with a violation and the write refuses.
- `CompanionDigestTest` (new): `PackFileSet.digested` of a pack with a skill lists `skills/x.skill.yaml` followed by
  `skills/x.skill.md`; a body-only change changes `FileSystemPackSources` fingerprint; a companion that is not UTF-8,
  a symlink, a `.yaml` or a duplicate is not digested; a pack with no skill digests exactly the files it digested
  before this mission (compare with a pack tree from `GeneratedPackTree` or a hand-built pack of the six old kinds).
- Run `PackFileSetExactnessTest`, `FileSystemPackSourcesTest`, `WholePackOpenPacksRoundTripTest`
  (`:packs:openPacksRoundTrip`).

## Definition of Done

- `./gradlew :packs:test :packs:openPacksRoundTrip :packs:check` green on Java 25; the seam test reloads
  `internal` (5 skills with bodies, 1 glossary pack) with equal meaning.
- No digest change for a pack without companions (asserted).

## Risks / reviewer guidance

- The digest must not throw on a malformed companion: the revision paths read every digested file as text.
- The digest order: companion directly after its artifact file; new kinds last.
