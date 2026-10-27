---
work_package_id: WP05
title: Gate and open-packs ratchets
dependencies:
- WP03
requirement_refs:
- FR-003
- FR-008
- FR-009
- FR-010
- FR-013
- NFR-001
- NFR-003
- C-002
- C-005
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T023
- T024
- T025
- T026
- T027
phase: Phase 3 - Proofs, gate and records
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
role: implementer
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackCensus.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackOpenPacksRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackReport.java
- service/packs/src/test/resources/whole-pack/expected-red.yaml
tags: []
task_type: implement
---

# Work Package Prompt: WP05 — Gate and open-packs ratchets

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Turn the whole-pack gate (#187) green for our three kinds — `asset`, `asset-payload`, `template` — with exactly six
pinned reds (the built-in sidecars), and make the open-packs round trip prove **file-level** fidelity over every pack.
Flip ONLY our kinds (collision rule). Plan "Acceptance checks" 1, 2, 3, 4, 7, 8, 9 and post-plan P8–P11, P13–P15
bind this WP. Read `research.md` D7, D8 first, then the gate classes listed in `owned_files`.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Lanes from `lanes.json`. Implement with:

```bash
spec-kitty agent action implement WP05 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Context

- Gate: `WholePackRoundTripTest` (tag `whole-pack`, task `:packs:wholePackRoundTrip`) → `WholePackCensus.of(pack)`
  (gate kind per file: mapped kind or `other:<first segment>`) → `DomainRoundTrip.roundTrip` (outcome per source path
  of a modelled kind) → `observe` (reads source and written text, `MeaningComparator.differencesOfTexts`) →
  `WholePackExpectation.evaluate` (two-way ratchet against `expected-red.yaml` + `exclusions.yaml`).
- After WP01/WP03 the census gives `asset-payload` to built-in's 7 payload files (5 audience `.md`,
  `docs_structural_lint.py`, `docs_structural_lint.config.yaml`), `template` to 2 files, `asset` to 6 sidecars;
  the two `README.md` under `assets/` are `Excluded` in file-kinds and so census as `other:assets` and stay excluded
  by the gate's `**/README.md`.
- WP03's refusal reason for the six: `payload assets/<path> does not exist`, e.g.
  `payload assets/assets/audiences/line_manager.md does not exist`, on the sidecar's pack-relative file.
- `DomainRoundTrip` writes artefacts at the pack root as `<id>.<kind>.yaml` and pairs by kind and id; asset files and
  templates are written in place (same pack-relative path) and pair by path.

## Subtasks

### T023 — Census and expectation

- `WholePackCensus`: expose the content form of each file (from `PackFileKind.classify`), so `observe` can branch.
- `WholePackExpectation.FileEntry`: optional `refused` (the exact refusal reason, matched verbatim after the
  `refused: ` prefix) and optional `pack` (default `built-in`). An entry with `refused` matches only a refused file
  with that reason; a different refusal reason is an unexpected red. Keep `at` for written-but-different files. Update
  `load` (allowed keys), the error messages, and `WholePackExpectationTest` (a matching reason passes, a different
  reason fails, an unknown key fails, `refused` and `at` together fail).

### T024 — `DomainRoundTrip`

- Append, in one block commented `#193`: `asset` to `ARTEFACT_KINDS` (sidecars pair by id like other artefacts);
  `asset-payload` and `template` to `MODELLED`; in `writeAll` save `pack.assets()`, every `pack.assetFiles()` (via the
  package-private `writeAssetFile`) and `pack.templates()` (via `writeArtifact`); outcomes for `asset-payload` and
  `template` files are `Written(<same path>)` when the read-back pack holds the same path, `Refused(...)` otherwise.
- `DomainRoundTripTest`: a fixture with a sidecar + payload, a sidecar-less payload and a template yields three
  `Written` outcomes at the expected paths; a sidecar with a missing payload yields `Refused` with the exact reason.

### T025 — Gate comparison and controls

- `observe`: branch on the file's content form **before** any text read: `BYTES` → compare source and written bytes;
  the red detail names the first differing offset (`bytes[<offset>]: source 0x.., written 0x..`) or the length
  difference (`length: source N, written M`); `duplicateEdges` is called for YAML files only.
- `REQUIRED_KINDS` gains exactly `asset`, `asset-payload`, `template` (equality assertion stays).
- Lossy control (P11), through the production save path: a `DomainRoundTrip` variant whose loaded `Pack` has one byte
  flipped in every asset file and template before the save (rebuild the pack with mutated `Payload`s; do not touch
  the written files afterwards). Assert every `asset-payload` and `template` file that is green under the faithful run
  is red under the lossy run at the flipped offset; a truncation variant (last byte dropped) is red at `length`.
  Assert the count of bytes-kind outcomes equals the faithful pack's asset files plus templates (7 + 2 at the pin).
- Unit test of the bytes branch (P13) over a PNG/OTF-like fixture (NUL bytes, CR/LF pairs, no final newline): equal
  bytes green, one-byte change red at that offset.
- Exact counts (check 8): after evaluation, `asset` has 6 `EXPECTED_RED` and 0 `GREEN`, `asset-payload` 7 `GREEN`
  0 `EXPECTED_RED`, `template` 2 `GREEN` 0 `EXPECTED_RED`; the 6 expected-red asset paths equal the six named paths;
  each red detail names its `assets/assets/…` path (check 3).

### T026 — `expected-red.yaml`

- Remove the three `kinds` entries for `asset`, `other:assets`, `other:templates` (issue #193). If `other:assets` is
  still a census kind (only the excluded READMEs), confirm the ratchet does not report it as stale or unexpectedly
  green; if it does, report it in the handoff (gate mechanics are shared) rather than adding an exclusion.
- Add six `files` entries, one per built-in sidecar, each `refused: payload assets/<their path> does not exist`,
  reason "the built-in sidecars anchor `path` at the pack root, not at `assets/` (pack inconsistency, ADR-0036
  Decision 5; fixed in spec-kitty-open-packs)", issue `"#193"` (the open-packs issue number is added when filed).
- Change nothing else in the file.

### T027 — Open-packs fidelity

- `WholePackOpenPacksRoundTripTest`:
  1. Read the pinned refused entries (the same `expected-red.yaml` entries, by `pack`) and require each pack's
     violations to equal its pinned set, matched by file **and** reason — a new violation or a pinned one that no
     longer occurs fails (R10). Packs with no pinned entries must have zero violations.
  2. For every pack that loads clean: write, reload and compare the aggregate as today **and** compare files: every
     sidecar with `MeaningComparator.adr0036()`, every asset file and template by SHA-256 (source vs written);
     extend `meaning()` with `assetFiles()` and `templates()`.
  3. Pin counts per pack (check 1): internal 10 sidecars, 10 asset files, 4 binary (3 `.otf`, 1 `.png`); qa 3/3;
     event-driven 1/1; repository `packs/qa` and `packs/internal` the same as their open-packs counterparts or their
     own counts (read them). `unmodelled()` holds no file under `assets/` or `templates/` except `README.md`.
  4. Corrected built-in copy (check 2): copy built-in into a temp directory, strip the `assets/` prefix from the six
     sidecar `path` values (test fixture only — the open-packs fix, not service logic), and assert zero violations,
     6 sidecars, 7 asset files, 2 templates, full write + reload + file comparison green. The unmodified built-in in the
     same test gives exactly the six pinned violations (same fixture, positive and negative).

## Definition of Done

- `./gradlew :packs:wholePackRoundTrip` green; report the kind table it prints. `./gradlew :packs:openPacksRoundTrip`
  green. `./gradlew :packs:test :packs:check` green.
- `expected-red.yaml` diff: exactly three kind entries removed and six file entries added.
- `git diff` touches no sibling's lines in `REQUIRED_KINDS`, `ARTEFACT_KINDS`, `MODELLED` or `expected-red.yaml`.

## Risks / reviewer guidance

- Reviewer: run the lossy control yourself after reverting the bytes branch (the gate must go red), then restore.
- Sibling merges will conflict in the same lines: rebase and keep only our additions.
