---
work_package_id: WP07
title: Whole-pack gate for assets and templates
dependencies:
- WP05
requirement_refs:
- FR-002
- FR-003
- FR-008
- FR-009
- FR-013
- NFR-001
- NFR-003
- C-002
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T029
- T030
- T031
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
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackReport.java
create_intent: []
tags: []
task_type: implement
---
# Work Package Prompt: WP07 — Whole-pack gate for assets and templates

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Turn the whole-pack gate (#187) green for our three kinds — `asset`, `asset-payload`, `template` — with exactly the
six refusals pinned by WP04, and prove the gate can see a lossy byte writer. Flip ONLY our kinds. Plan "Acceptance
checks" 3, 4, 8, 9; post-plan P9, P11, P13, P14. Read `research.md` D7, `WholePackRoundTripTest`, `DomainRoundTrip`,
`WholePackCensus`, `WholePackReport` first.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Execution worktrees are allocated per
computed lane from `lanes.json`; do not pick a base branch by hand. Implement with:

```bash
spec-kitty agent action implement WP07 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Validation discipline

Run the targeted tests for the files you touch and the owning module's fast tier (`./gradlew :packs:test
:packs:check`; `:deployable:test` when named). Never run a whole-repo heavy sweep (`verifyAll` is CI's,
directive NO_FULL_HEAVY_SUITES_IN_MISSION); record the exact commands and pass/fail counts in the review handoff.
Java 25 via `JAVA_HOME=/usr/lib/jvm/temurin-25`; Maven Central HTTP 429 → wait and retry.

## Context

- Gate: `WholePackRoundTripTest` (tag `whole-pack`, `:packs:wholePackRoundTrip`, CI `whole-pack.yml`) →
  `WholePackCensus.of(pack)` → `DomainRoundTrip.roundTrip` → `observe` (reads source/written text,
  `MeaningComparator.differencesOfTexts`) → `WholePackExpectation.evaluate`.
- After WP01/WP05, built-in's census: `asset` 6 sidecars, `asset-payload` 7 files (5 audience `.md`,
  `docs_structural_lint.py`, `docs_structural_lint.config.yaml`), `template` 2 files; the 2 READMEs under `assets/`
  census as `other:assets` and are excluded by the gate's `**/README.md`.
- `DomainRoundTrip` writes artefacts at the pack root as `<id>.<kind>.yaml` and pairs by kind and id; asset files and
  templates are written in place and pair by path. WP05 provides package-private
  `DomainRepositoryService.saveArtifact(PackIdentifier, CharterArtifact)` and `writeAssetFile(PackIdentifier,
  AssetFile)`.

## Subtasks

### T029 — Census content form

- `WholePackCensus`: expose each file's content form (from `PackFileKind.classify`; `yaml` for unclassified YAML,
  and for `other:*` files keep today's behaviour) so `observe` can branch.
- Census test: built-in's mission templates (`missions/*/templates/**`) census as `other:missions` (FR-013), on the
  same pack where the top-level templates census as `template`.

### T030 — `DomainRoundTrip`

- One appended block commented `#193`: `asset` joins `ARTEFACT_KINDS`; `asset-payload` and `template` join
  `MODELLED`; `writeAll` saves `pack.assets()`, every `pack.assetFiles()` and `pack.templates()` through the WP05
  entry points; outcomes for `asset-payload` and `template` are `Written(<same path>)` when the read-back pack holds
  that path, `Refused(...)` otherwise.
- `DomainRoundTripTest`: sidecar + payload, sidecar-less payload and template give three `Written` outcomes at the
  expected paths; a sidecar with a missing payload gives `Refused` with the exact reason.

### T031 — Bytes branch, controls and exact counts

- `observe`: branch on content form **before** any text read; `BYTES` compares bytes; the red detail names the first
  differing offset (`bytes[<offset>]: source 0x.., written 0x..`) or `length: source N, written M`; `duplicateEdges`
  only for YAML.
- `REQUIRED_KINDS` gains exactly `asset`, `asset-payload`, `template` (equality assertion stays).
- Lossy control through the save path: a `DomainRoundTrip` variant whose loaded `Pack` has one byte flipped in every
  asset file and template before the save (rebuild the pack with mutated `Payload`s; never edit written files). Every
  `asset-payload`/`template` file green under the faithful run is red under the lossy run at the flipped offset; a
  truncation variant is red at `length`. The count of bytes-kind outcomes equals the faithful pack's asset files plus
  templates (7 + 2).
- Unit test of the bytes branch over a PNG/OTF-like fixture (NUL bytes, CR/LF pairs, no final newline): equal → green;
  one-byte change → red at that offset.
- Exact counts after evaluation: `asset` 6 `EXPECTED_RED`, 0 `GREEN`; `asset-payload` 7 `GREEN`, 0 `EXPECTED_RED`;
  `template` 2 `GREEN`, 0 `EXPECTED_RED`; the six asset reds are the six pinned paths, each detail naming its
  `assets/assets/…` path.
- `WholePackReport`: the kind table shows the new kinds (adapt only if rendering needs it).

## Definition of Done

- `./gradlew :packs:wholePackRoundTrip` green — paste its kind table into the handoff; `:packs:test :packs:check`
  green.
- No sibling lines changed in `REQUIRED_KINDS`, `ARTEFACT_KINDS`, `MODELLED`.
- NFR-003 (`verifyAll` on Java 25) is owned by CI on the PR (directive NO_FULL_HEAVY_SUITES_IN_MISSION); the PR is
  opened only after this WP is approved.

## Risks / reviewer guidance

- Reviewer: revert the bytes branch (compare as text) and confirm the lossy control still goes red only via bytes;
  revert the lossy mutation and confirm the control fails loudly (the control itself is non-vacuous).
