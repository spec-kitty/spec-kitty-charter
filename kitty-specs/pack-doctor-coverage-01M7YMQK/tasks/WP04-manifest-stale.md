---
work_package_id: WP04
title: manifest_stale (#630)
dependencies:
- WP02
requirement_refs:
- FR-004
- FR-005
- FR-010
- FR-011
- SC-003
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-doctor-coverage-01M7YMQK
base_commit: 45f6345f79641d9ede5f911d7a0fe22868120e82
created_at: '2026-11-21T17:59:23.548517+00:00'
subtasks:
- T017
- T018
- T019
- T020
- T021
phase: Phase 2 - The rules
history:
- at: '2026-11-21T13:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/ManifestRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ManifestStampHelper.java
- contracts/charter/changes/2026-11-21-manifest-stale.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/TreeCensus.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/ManifestRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageCodes.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifests.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackRoundTrip.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/roundtrip/RoundTripObservation.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/roundtrip/WholePackExpectation.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/roundtrip/RoundTripObservationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackFlowTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/ManifestRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ManifestStampHelper.java
- service/packs/src/testFixtures/resources/example-packs/*/pack-manifest.yaml
- contracts/charter/scenarios/registration/whole-pack-registration.feature
- contracts/charter/changes/2026-11-21-manifest-stale.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP04 - manifest_stale (#630)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, the mission `spec.md` and `plan.md` (the decisions this WP
names), and `research/squad-dispositions.md`.

## Objective

A stale `content_hash` or `manifest_hash` in `pack-manifest.yaml` is refused on every surface with the specific code
`manifest_stale` at the manifest, naming the field (ruling R-b; spec FR-004, FR-005; plan D4). With `--round-trip`,
the cause still gives one problem.

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP04 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Context
- `PackManifests.stale(read, bytesAt)` already computes the stale fields:
  - `constituents{<path>}.content_hash`;
  - `presets{<path>}.content_hash`;
  - `manifest_hash`;
  - `missing: <path>`.

  Today only `PackRoundTrip` (around lines 186-197) calls it, and the result reaches the user as a generic
  `round_trip_red` ("source stale").
- The manifest gate (`StorageRulesets.manifestMembership` → `ManifestRules.membership`) runs only when nothing else
  failed, and is shared by validate mode and registration.
- Registration restamps the manifest after the write (`ManifestMembership.stamp`). That stays, because stale hashes
  are judged on the **incoming** bytes only.
- Operator question in flight: folding #636's round-trip-codes slice (retiring `EXCLUDED_WRITTEN`, merging the
  `STALE_*` wordings in `WholePackExpectation`) into this WP. **Do it only if the dispatcher relays a yes;** otherwise
  leave `WholePackExpectation` alone except as T018 needs.

## Subtasks

### T017 — Detection and the code
- Red first, in `ManifestRulesTest`: a manifest whose constituent's `content_hash` does not match gives one
  `manifest_stale` at `pack-manifest.yaml`, with pointer `constituents{<path>}.content_hash`. Add cases for a preset's
  `content_hash`, for `manifest_hash`, for two stale fields giving two reasons, and for a path that membership already
  names, which gives no `manifest_stale`.
- `FileSystemPackInspector.manifest` already holds the parsed DSO. Fill `TreeCensus.Manifest` with the stale fields
  from `PackManifests.stale` over the incoming bytes, minus the `missing:` entries (membership owns them).
  `ManifestRules` derives nothing.
- `StorageCodes.MANIFEST_STALE` (`violation("manifest_stale")`) is the one owner of the token.
- `ManifestRules.mismatch` returns the paths it names beside its reasons, so the stale check skips them without
  parsing prose again.
- The gate states membership, then stale, both behind the same "nothing else failed" condition (read
  `StorageRulesets.verdict` and `LAST`). `manifest_stale` sits last, after `manifest_membership`.
- The contract: `CharterRefusalCode.yaml` documents violation codes under `validation_failed`'s description. Add
  `manifest_stale` beside `manifest_membership` there. That file is owned by WP03, so make a justified out-of-map
  edit, or coordinate. Add a change fragment `2026-11-21-manifest-stale.md` under `### Breaking`.

### T018 — The round trip drops source-stale
- `PackRoundTrip` / `RoundTripObservation`: no longer report the source side's stale fields (now `manifest_stale`).
  **Keep the written-stale check:** it judges the writer, and is a separate cause.
- Do not rely on `PackSetCheck.notReported`'s file dedupe: it would also hide an unrelated red at
  `pack-manifest.yaml`.

### T019 — Rewrite the pins (do not delete)
- `WholePackFlowTest:127-166` and `RoundTripObservationTest:176` pin "source stale". Rewrite them: keep the
  written-stale assertions, and move the source-stale case to `ManifestRulesTest` (T017).
- `MalformedDescriptorTest:148`, `ProblemsTest:193`, `PackDoctorJarTest:227/310`, `PackSetCheckTest:756-781` and
  `PackDoctorReportContractTest:233/577`: each `doesNotContain("round_trip_red")` gains a positive assertion of the
  expected code. These files belong to other WPs; make minimal, justified out-of-map edits.
- A new test: a source-stale pack under `pack-doctor validate --round-trip` yields **exactly** `["manifest_stale"]`
  (asserted by equality), exit 1. The same pack without `--round-trip` gives the same answer.

### T020 — Scenarios at `whole-pack-registration.feature:528-584`
- "partial" (around line 536): give it correct hashes, so it still isolates `manifest_membership` (exactly one item).
- The scenario at 560 ("stale hashes restamped") becomes a refusal: `manifest_stale` at `pack-manifest.yaml`, naming
  the field.
- Add a control: a correctly hashed uploaded manifest registers and is restamped unchanged.
- Update the comment at 533-535, which says the hashes are not judged.

### T021 — Restamp the example manifests
- `ManifestStampHelper` (test scope) stamps each example pack's manifest through `ManifestMembership.stamp`, the
  service's own code, not a copy. Run it and commit the result. If no example manifest was stale (CI's `--round-trip`
  suggests none was), say so, and the helper stays only as a test fixture tool, or delete it.
- Run the whole-pack gate. Update its expected-red baseline only if a case truly changes, with a one-line reason per
  change.

## Test commands

```bash
./gradlew :packs:test --tests '*Manifest*' --tests '*WholePackFlow*'
./gradlew :pack-validation:test :pack-doctor:test
./gradlew :deployable:test
./gradlew :pack-validation:wholePackRoundTrip
```

## Definition of Done
- [ ] A stale hash gives exit 1 and exactly `["manifest_stale"]`, with and without `--round-trip`, under `--pack` and
  `--config-root`.
- [ ] A whole-pack upload with a stale hash is refused, and a correctly hashed one registers.
- [ ] A per-artifact write still stamps.
- [ ] The written-stale round-trip red still reports.
- [ ] No test was deleted, and each rewritten test keeps its intent.

## Risks and reviewer guidance
- A reviewer checks that the order of the gate's faults is unchanged for packs without stale hashes.
