---
work_package_id: WP13
title: A · Bounded, path-safe archive extractor
dependencies: [WP11]
requirement_refs:
- FR-001
- FR-003
- NFR-004
- NFR-005
- NFR-012
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts were generated on issue-10-layer-merge-effective-reads; execution worktrees come from lanes.json and completed changes merge back into issue-10-layer-merge-effective-reads. The four PRs (A0, A, B, C) are cut from it at closeout.
subtasks:
- T058
- T059
- T060
- T061
- T062
- T063
phase: PR A
assignee: ''
agent: claude
shell_pid: ''
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/archive/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/archive/BoundedArchiveExtractor.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/archive/ArchiveLimits.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/archive/StagedPack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/archive/package-info.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/archive/BoundedArchiveExtractorTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/archive/HostileArchiveCorpusTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackArchiveReader.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/archive/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/archive/**
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackArchiveReader.java
- gradle/libs.versions.toml
- gradle/verification-metadata.xml
- service/packs/build.gradle.kts
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
role: implementer
---
# WP13: A · Bounded, path-safe archive extractor

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Turn a hostile `tar.gz` or `zip` into a staged pack directory, or refuse it before any byte is written outside
staging (NFR-004, NFR-005). Add Apache Commons Compress 1.28.0 under DIRECTIVE_051.

Implementation command:
`spec-kitty agent action implement WP13 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concern:** IC-03 in full (formats, limits, path rules, hardening from security squad 4, 5, 6, 11).
  **Requirements:** FR-001, FR-003, NFR-004, NFR-005, NFR-012.
- **Supply chain:** plan "Supply chain (DIRECTIVE_051)" lists the coordinates, the sha256 values to check by hand,
  and the dependency entries allowed; re-check OSV/GHSA/Apache security for 1.28.0 and record the result in the
  hand-off note for the PR. Never turn verification off; if `--write-verification-metadata` is needed, review the diff
  by hand and allow only the listed entries (optional codecs `zstd-jni`, `brotli`, `xz`, `asm` must not appear).

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


## Project constraints (apply to every subtask)

- **TDD, contract first (C-001).** Write the failing test or `@contract` scenario first, watch it fail for the right
  reason, then write the code. A contract change lands with its `contracts/charter/changes/` fragment before the code
  that serves it.
- **No disabled tests, no new suppressions (C-006, NFR-001).** No `@Disabled`, no `@WIP` left on a scenario this WP
  makes green, no `@SuppressWarnings`/Checkstyle/PMD/SpotBugs suppression added, and the suppression register does not
  grow. Methods stay within CC ≤ 15, NPath ≤ 1000, ≤ 60 lines and nested-if depth ≤ 1.
- **Hexagonal slices, no shadow logic (C-002).** Merges and walks live in `packs`; `activations` reaches `packs` only
  through the `AvailablePacks` port and `activations.infra.packs`. `HexagonalArchitectureTest` must stay green.
- **No core changes (C-003).** Nothing in spec-kitty core is edited, and no PR or issue is opened in core.
- **git is a host binary (C-008).** Only the system `git` ≥ 2.50.1, never JGit, never a work-tree checkout.
- **Gradle.** Run module tasks, not the full suite (`NO_FULL_HEAVY_SUITES_IN_MISSION`):
  `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none <module>:check`.
  On a Maven Central `429`, wait and re-run the same command; never turn dependency verification off.
- **Divergences (C-007).** Every behaviour that differs from core gets a divergence-register entry in the PR that
  ships it (the docs WP of that PR writes it; tell it what you changed in your hand-off note).
- **Commits** end with the session's attribution lines; no model id in commits or PR text.

## Branch Strategy

- Planning branch: `issue-10-layer-merge-effective-reads`; final merge target: `issue-10-layer-merge-effective-reads`.
- Execution worktrees are allocated per computed lane from `lanes.json`; do not pick a base branch by hand. Dependencies are a claim gate: this WP can be claimed only when its dependencies are approved or done.
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (A).

## Subtasks

### T058: Dependency under DIRECTIVE_051

Add `commons-compress` 1.28.0 to `libs.versions.toml` with a reviewer comment and to `:packs` `implementation`; add verification entries; confirm the jar's sha256 `e1522945…42ca` matches.

### T059: Port and ring

`PackArchiveReader` application port (`StagedPack extract(InputStream, ArchiveFormat, Path stagingRoot)`); `infra.archive` is an adapter ring in `HexagonalArchitectureTest` (application must not name it).

### T060: tar.gz reader

JDK `GZIPInputStream` → `TarArchiveInputStream` created directly (no factory), strict header parsing; a counting bound on the decompressed stream (stop at 32 MiB + 64 KiB); link-flag allowlist (`0`, NUL, `5`, PAX/GNU long-name headers); names checked after PAX/GNU application (`getName()`).

### T061: zip reader

Spool to an owner-only file in staging; read the EOCD entry count first (> 10,000 → `422`); `ZipFile` via its builder, `setUseUnicodeExtraFields(false)`; ASCII names or the EFS flag; local and central names must match; overlapping entries refused; only `STORED`/`DEFLATED`; encrypted entries refused; Unix symlink mode refused.

### T062: Path rules and writing

Per segment `[A-Za-z0-9._-]+`; refuse absolute, drive letters, backslash, NUL, `.`/`..`/empty segments, > 4,096 bytes, collisions after NFC + case folding; one leading `./` stripped; one shared leading directory stripped; write with `CREATE_NEW`, `NOFOLLOW_LINKS`, files `0600`, dirs `0700`; at most 100 violations listed; each value escaped and truncated to 256 bytes (NFR-012).

### T063: Hostile corpus (NFR-005)

`HostileArchiveCorpusTest`: at least 20 archives built by code (never committed binaries): zip-slip, absolute path, symlink and hardlink chains, device, FIFO, PAX path override, GNU long name with `..`, case-fold duplicate, zip bomb, tar bomb, 10,001 entries (EOCD), local/central mismatch, encrypted entry, overlapping entries, non-ASCII name without EFS, sparse file, empty segment, backslash, drive letter. 100% refused, nothing written outside staging (assert the staging parent is unchanged).

## Test strategy

`BoundedArchiveExtractorTest` (limits, formats, shapes), `HostileArchiveCorpusTest`, `HexagonalArchitectureTest`.

## Risks

- Native image: no reflection factory, no `ServiceLoader`; the nightly native upload test (WP16) covers a zip with
  Unix and ZIP64 extra fields.
- A zip without Unix modes carries no link flag: written as a plain file, never a link.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
