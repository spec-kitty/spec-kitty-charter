---
work_package_id: WP08
title: Validate mode and non-vacuous open-packs acceptance
dependencies:
- WP07
- WP09
requirement_refs:
- FR-010
- FR-011
- FR-012
- NFR-001
- NFR-002
- SC-001
- SC-002
- SC-005
- SC-006
planning_base_branch: feat/pack-schemas-and-level-policy
merge_target_branch: feat/pack-schemas-and-level-policy
branch_strategy: Planning artifacts for this mission were generated on feat/pack-schemas-and-level-policy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-schemas-and-level-policy unless the human explicitly redirects the landing branch.
subtasks:
- T036
- T037
- T038
- T039
- T040
- T041
history:
- at: '2026-10-19T18:10:00Z'
  event: created
  by: /spec-kitty.tasks
- at: '2026-10-19T19:05:00Z'
  event: revised
  by: post-tasks squad (renata, priti, ivan)
agent_profile: java-jenny
authoritative_surface: service/adapter-out-yaml/
create_intent:
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackValidation.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackValidationTest.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/FileSystemPackInspector.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackFileKind.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/FileSystemPackInspectorTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/OpenPacksCorpus.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/ValidateModeTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/OpenPacksAcceptanceTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/ScopeParityTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PlantedFilesTest.java
- service/adapter-out-yaml/src/test/resources/planted/core-verdicts.json
- scripts/packs/core_verdicts.py
execution_mode: code_change
model: sonnet
owned_files:
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackValidation.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackValidationTest.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/FileSystemPackInspector.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackFileKind.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/FileSystemPackInspectorTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/ValidateModeTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/OpenPacksAcceptanceTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/OpenPacksCorpus.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/ScopeParityTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PlantedFilesTest.java
- service/adapter-out-yaml/src/test/resources/planted/**
- scripts/packs/core_verdicts.py
role: implementer
tags: []
tracker_refs: []
---

# WP08 — Validate mode and non-vacuous open-packs acceptance

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `java-jenny`). Then load governance context: `spec-kitty charter context --action implement`.
The charter wins over this prompt: no fake green. Read the **mission-wide rules** in
[tasks.md](../tasks.md).

## Objective

This WP finishes validate mode and proves #114's acceptance without a vacuous pass:

- **Validate mode.** It gets a **production entry point**, a pure domain service `PackValidation`
  over the `PackInspector` port, and the `FULL` scope. It collects every finding and classifies
  every YAML file (FR-010, SC-005).
- **Open-packs acceptance** (FR-011, SC-001). At the pinned **clean** HEAD it asserts exact counts
  (409 mapped, 107 excluded, 0 unclassified, and mapped plus excluded equals the corpus constant),
  and every mapped file validates.
- **Planted invalid files.** One per change made on import and per domain rule, each failing with
  its code (FR-012, SC-002). Each is cross-checked against core in a machine-readable record, and
  agreement is **derived** from core's raw output, not written by hand.
- **I2 across scopes.** LOAD_PATH load and the FULL judge, restricted to the load path, must agree.
- **NFR-002.** It is measured against its thresholds.
- **The pre-merge pin gate** for open-packs #52 (risk K1).

Read first:
- [spec.md](../spec.md): US4, US5, FR-010 to FR-012, NFR-002, SC-001, SC-002 and SC-005;
- [plan.md](../plan.md): IC-07 and "#115 doctor parity test design";
- `contracts/packs/VENDORED.md` (C1-C16) and `contracts/packs/file-kinds.yaml`;
- [contracts/pack-finding-codes.md](../contracts/pack-finding-codes.md), including the core
  mapping;
- `docs/conformance/divergences.yaml` (DIV-001 to DIV-019, from WP09).

## Branch Strategy and environment

- **Branches.** The planning base and merge target are `feat/pack-schemas-and-level-policy`. You
  work in the lane worktree, the same lane as WP07.
- **Open-packs checkout.** Pass `-PopenPacksDir=/home/user/spec-kitty-open-packs` (absolute) to
  every Gradle command.
- **Implement command:**
  `spec-kitty agent action implement WP08 --agent claude --mission pack-schemas-and-level-policy-01M5AFMA`.

## Subtasks

### T036 — `PackValidation`, the FULL scope, and the three-defect test

**Steps**:

1. **The domain entry point.** Add `final class PackValidation` in `charter-domain`, plain Java.
   It is constructed with a `PackInspector`, and `PackVerdict validate(PackRoot root)` returns
   `PackPolicy.judge(inspector.inspect(root, FULL))`. Javadoc it as FR-010's use case and the
   seam #115 composes. Test `PackValidationTest` with a fake inspector.
2. **Red.** `ValidateModeTest` goes **through `PackValidation` with the real
   `FileSystemPackInspector`**. Its pack root has three defects: an unknown key in `pack.yaml`, a
   duplicate step id, and a bare endpoint in the fragment. It expects exactly three findings, each
   with its own file and place:
   - `pack_unknown_key` (a warning);
   - `duplicate_step_id`;
   - `drg_schema_error`.

   Then `repository.load` of the same pack refuses with the `drg_schema_error`.
3. **FULL** in `FileSystemPackInspector`, replacing WP07's `UnsupportedOperationException`:
   - Walk every `*.yaml` under the root, sorted, and classify each with `PackFileKind`.
   - Parse each mapped artifact, `org-charter.yaml` or `charter.yaml`, and validate it through
     `PackSchemas`.
   - Collect `ArtifactFacts`, `StepContractFacts` and `AssetFacts`. Containment is
     **symlink-aware**: use `toRealPath` where the path exists, and lexical normalization
     otherwise, mirroring core's `resolve_relative_path_within_root`.
   - Never throw on content.

### T037 — `OpenPacksAcceptanceTest`

**Steps** (`@Tag("open-packs")`, never skipped):

1. Call `OpenPacksCorpus.assertPinned()` first.
2. **Classification.** For every YAML file under `packs/`, `PackFileKind` returns exactly one
   class. Assert the per-kind mapped counts:

   | Kind | Count |
   |---|---|
   | pack-descriptor | 2 |
   | graph-shard | 14 |
   | org-fragment | 4 |
   | org-charter | 4 |
   | directive | 55 |
   | tactic | 137 |
   | styleguide | 34 |
   | toolguide | 24 |
   | paradigm | 15 |
   | procedure | 41 |
   | agent-profile | 31 |
   | mission-step-contract | 28 |
   | asset | 20 |
   | **mapped total** | **409** |

   Also assert:
   - the per-class excluded counts, 107 in total;
   - 0 unclassified;
   - mapped plus excluded equals `OpenPacksCorpus`'s YAML-file constant (516).

   Re-measure these at the pin before committing them.
3. **Validation.** For each pack, `PackValidation.validate(PackRoot(name, Optional.of(level), path))`
   has zero blocking findings, and its warnings equal `OpenPacksCorpus.DIAGNOSTICS`. Any unexpected
   finding is printed in the failure message.
4. **Non-vacuity.**
   - Load a **copy** of `file-kinds.yaml` with one exclusion removed, through a package-private
     `PackFileKind(String yaml)` constructor that you add: the classification assertion fails.
   - Add an unknown-class file to a temporary pack copy: it fails too.

### T038 — Planted invalid files

**Steps**:

1. **The plants.** Under `src/test/resources/planted/<id>/`, write one minimal pack or file, with
   `expected.json` (code, severity, file, place).
   - **One per schema change C2 to C12 and C16.** Examples:
     - a nested unknown key in an agent profile;
     - a bad URN, a URN prefix that differs from the kind, a bare endpoint in a shard and in a
       fragment;
     - the unknown fragment kinds `mission_steps` and `mission_type`;
     - a fragment carrying `pack_name`;
     - an org `pack.yaml` without `pack_version`;
     - an uppercase role, a quoted integer, `routing_priority: 101`, lenient adherence without
       `explicit_allowances`;
     - `schema_version: 1.0` unquoted;
     - the relation `extends` (P2);
     - an edge with `provenance` (C16).
   - **Per URN kind**, a `glossary:` URN (R15).
   - **C1, C13, C14 and C15** are schema-shape changes proven by WP04's `PackSchemasTest`. Cite
     those tests in `expected.json` as `"proven_by"`, so the bijection still holds.
   - **One per domain rule**: `requires_core_invalid`, `org_fragment_missing`,
     `duplicate_step_id`, `duplicate_artifact_id`, `asset_path_escape` (including a symlink case),
     `asset_mime_invalid`, and each `impacts` rule (missing, extraneous, NaN, out of range).
   - **`charter.yaml`**, one valid and one invalid (P7).
   - **Positive controls**:
     - no `generated_by`;
     - no `pack_level`;
     - an unknown and a retired `pack.yaml` key, which give warnings only;
     - a valid `impacts` edge, accepted.
2. **`PlantedFilesTest`**, parametrized over `planted/*`. It runs `PackValidation`, and also
   `repository.load` for the load-path plants, and asserts `expected.json`.

### T039 — `core-verdicts.json` with derived agreement

**Steps**:

1. **The script.** `scripts/packs/core_verdicts.py` is run with `~/.venv-sk/bin/python -I`. It is
   non-gating and not part of the build. For each plant it runs
   `spec-kitty charter pack validate --json <pack>` in a scratch `HOME`, with a scratch project
   where one is needed (research R5). It writes `planted/core-verdicts.json`, one record per plant,
   holding:
   - the core SHA, the command and the raw `{ok, errors, advisories}`, with paths normalized to be
     pack-relative;
   - a `register_id` for every plant whose rule is a recorded divergence. Use DIV ids from
     `docs/conformance/divergences.yaml` only.
2. **`PlantedFilesTest` asserts the bijection.**
   - Every plant has exactly one record, and every record has exactly one plant.
   - Agreement is **derived in the test**, never read from the file. Map core's raw findings
     through the FR-016 mapping in contracts/pack-finding-codes.md, then compare the multiset of
     `(file, code, severity)` with ours. The result is either `agree` or `differ`.
   - A `differ` record must carry a `register_id` that exists in `divergences.yaml` with status
     `open`.
   - An `agree` result with a `register_id` fails as a stale divergence.
   - Core blocking while we pass, with no register id, fails.

### T040 — I2 across scopes; NFR-002

**Steps**:

1. **`ScopeParityTest`.** For every open-packs pack and every load-path plant, `repository.load`
   (LOAD_PATH) must agree with `PackPolicy.judge(inspect(FULL))` restricted to
   `PackPolicy.loadPath`. Agreement covers the refusal (code and text), or the diagnostics against
   the restricted warnings.

   **Prove that it bites.** A test-only variant that skips the `loadPath` restriction must make the
   parity fail on a plant whose only finding is outside the load path, such as
   `duplicate_step_id`.
2. **NFR-002.**
   - In `OpenPacksAcceptanceTest`, measure validate mode over all mapped files, cold and warm
     (median of 5), and the repository load (median of 5). Print the numbers.
   - The guard asserts validate mode warm is at most **15 s**, and load at most baseline + **2 s**.
     The baseline value is committed as a constant beside the measurement record. Use
     `assertTimeout` with 2× headroom only as a backstop against hangs.
   - **Baseline.** Use a scratch harness in the scratchpad, not committed. It runs
     `FileSystemCharterPackRepository.load` 5 times:
     - at the mission base `61d0980`, through a `git worktree`, against the old-layout clone
       (`/home/user/spec-kitty/spec-kitty-open-packs` at `7f063ae`);
     - at this branch's head, against the pin.

     Record both medians, the commits, the JVM and the machine in the WP evidence. NFR-002 is met
     only if the recorded numbers satisfy 15 s and +2 s.

### T041 — The `charter.yaml` measurement, the pre-merge pin gate and the final gate

**Steps**:

1. **`charter.yaml`.** Validate this repository's own `.kittify/charter/charter.yaml` against
   `charter.schema.json`, and record the result. Do not change that file, and do not relax the
   schema (P7).
2. **Pre-merge pin gate (K1).** Run
   `git -C /home/user/spec-kitty-open-packs fetch origin main` and
   `git -C … merge-base --is-ancestor <pin> origin/main`, and record the result.
   - While #52 is unmerged, this fails. That is expected, and the evidence must say **"BLOCKED on
     open-packs#52"**.
   - After the operator merges #52, the orchestrator moves `openPacksRef` to the merge commit,
     re-runs `openPacksRoundTrip`, and re-verifies the counts.

   The charter PR must not merge before this gate passes.
3. **Final gate.**
   `./gradlew verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs --max-workers=2`, all
   green.

## Definition of Done

- [ ] `PackValidation` exists in production code, and `ValidateModeTest` runs through it, after a
      red run that failed on assertions.
- [ ] The acceptance check asserts the pinned clean HEAD, exact counts (with mapped plus excluded
      equal to the constant) and all-valid. Both non-vacuity probes fail it as expected.
- [ ] Every change C1-C16, every domain rule, R15, P2, P7 and every positive control has a plant
      or a cited proving test.
- [ ] `core-verdicts.json` exists. Agreement is derived in the test, and every divergence resolves
      to an open register id.
- [ ] `ScopeParityTest` holds and has been shown to bite.
- [ ] NFR-002's recorded numbers meet 15 s and +2 s, with the baseline and head medians recorded.
- [ ] The `charter.yaml` measurement and the pin-gate result ("BLOCKED on open-packs#52" until it
      merges) are recorded. `verifyAll` is green.
- [ ] Commits carry the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Reviewer guidance (opus, reviewer-renata)

- Try to make the acceptance pass vacuously. Each attempt must fail.
- Re-run core's CLI on three plants yourself and compare with the derived agreement.
- Check that `ScopeParityTest`'s bite test really removes the restriction.
