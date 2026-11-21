---
work_package_id: WP07
title: remediate
dependencies:
- WP04
- WP06
requirement_refs:
- FR-009
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage (Mission topic branch kitty/mission-pack-doctor-coverage-01M7YMQK). During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage, which is PR'd to main.
subtasks:
- T028
- T029
- T030
- T031
phase: Phase 3 - The CLI surfaces
agent: claude
history:
- at: '2026-11-21T13:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackRemediation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRemediationTest.java
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/Remediate.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/RemediateTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackRemediation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRemediationTest.java
- service/pack-doctor/src/main/java/ai/speckitty/charter/packdoctor/Remediate.java
- service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/RemediateTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP07 - remediate

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, the mission `spec.md` and `plan.md` (the decisions this WP
names), and `research/squad-dispositions.md`.

## Objective

`pack-doctor remediate <pack-dir>...` repairs what the charter can repair mechanically (ruling R-j; spec FR-009;
plan D7):

1. Rewrite each modelled file in the round trip's canonical form (ADR-0036: same meaning, formatting and comments may
   change).
2. Then derive and stamp `pack-manifest.yaml` exactly as every service write does.

It judges nothing new. It refuses, writing nothing, when the pack has a fault it does not repair.

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP07 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Context
- Canonical writer: `DomainRoundTrip` / `PackRoundTrip.run(packId, source, target)` writes a pack into an empty
  target in canonical form.
- Manifest: `PackManifestDerivation.derivedAt` and `ManifestMembership.stamp`, both package-private in
  `packs.infra.storage`.
- `PackCommitStore` stages a sibling copy and swaps it under a lock and generation.
- pack-doctor must stay **framework-free**: the transitive walk in `HexagonalArchitectureTest`. Do not pull in Spring,
  locks or commit stores.

## Subtasks

### T028 — `PackRemediation` facade
- A public class in `packs.infra.storage`, not in `.rules`, so `STORAGE_RULES_STAY_IN_THE_STORAGE_EDGE` is unaffected.
  It has one entry point, `remediate(Path packDir) → Outcome` (`Rewritten`, `Unchanged` or `Refused(violations)`).
- Steps:
  1. Check the precondition (T029).
  2. Write the canonical form into a staged sibling directory, through the round trip's writer.
  3. Stamp the manifest in the staged directory through `ManifestMembership.stamp`, run after the rewrite.
  4. Copy the files the census does not hold (stray files) unchanged.
  5. Swap the directories in one rename.
- If `PackCommitStore` has a staging and swap helper free of framework and locks, reuse it. Otherwise write the
  minimal atomic swap: rename the old directory aside, rename the staged one in, delete the old one, and restore on
  failure.
- `Unchanged`: compare the staged tree with the source by bytes, and do not swap when equal.

### T029 — The precondition
- One `StorageRulesets` function: the storage verdict, without the manifest gate (`manifest_membership`,
  `manifest_stale`) and without round-trip form findings. Any remaining violation means `Refused`, with nothing
  written. `StorageRulesets` is owned by WP04; this is a justified edit.

### T030 — Command and architecture
- `Remediate` in pack-doctor: parse the pack directories and compose `PackRemediation` in `PackDoctorMain`'s
  composition root.
  - It prints, per pack, its outcome, then re-runs `validate` over the remediated packs and prints that result.
  - Exit codes: 0 when every pack is rewritten or unchanged and validates; 1 when one is refused or still invalid;
    2 on usage or I/O failure (a path that is not a pack directory).
- `HexagonalArchitectureTest`: the pack-doctor main rules (`COMPOSED_BY_THE_PACK_DOCTOR_MAIN`,
  `THE_PACK_DOCTOR_MAIN_NAMES_..._ADAPTERS`, `ONION_..._COMPOSES_..._ADAPTERS`) name the third adapter. The
  framework-free walk stays clean.

### T031 — Tests (red first)
- Start from a pack copy that is red for both `manifest_stale` and a round-trip form finding (for example a
  non-canonical key order). Assert that red first (`validate --round-trip` exits 1 with both).
- After `remediate`:
  - `validate --round-trip` exits 0;
  - the meaning is unchanged (the round trip's `MeaningComparator`, over the before and after trees);
  - a stray file (for example `notes.txt`) is byte-identical.
- An unrepairable pack (a domain fault, for example an unresolved reference): exit 1, the fault named, and a
  file-tree hash taken before and after is equal.
- A second run on a remediated pack reports `Unchanged`.

## Test commands

```bash
./gradlew :packs:test --tests '*PackRemediation*' :pack-doctor:test :pack-doctor:packDoctorJar
./gradlew :deployable:test --tests '*Hexagonal*'
```

## Definition of Done
- [ ] Red, then green, with the meaning kept and stray files untouched.
- [ ] Nothing written on refusal (hash equality).
- [ ] Idempotent.
- [ ] Framework-free, with the ArchUnit rules green.
