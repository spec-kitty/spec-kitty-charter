---
work_package_id: WP17
title: 'A · Sources: guarded HTTPS archive and local path'
dependencies:
- WP16
requirement_refs:
- FR-004
- NFR-004
- NFR-005
- NFR-012
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T083
- T084
- T085
- T086
- T087
phase: PR A
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/GuardedHttpsExchange.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/HttpsArchiveFetcher.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/LocalSourceReader.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/PackSourceProperties.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackSourceFetcher.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/GuardedHttpsExchangeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/HttpsArchiveFetcherTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/LocalSourceReaderTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/StubArchiveExchange.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/GuardedHttpsExchange.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/HttpsArchiveFetcher.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/LocalSourceReader.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/PackSourceProperties.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/HttpsGuideFetcher.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/GlobalAddressResolverProvider.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/config/GuideFetchConfiguration.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackSourceFetcher.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/GuardedHttpsExchangeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/HttpsArchiveFetcherTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/LocalSourceReaderTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/StubArchiveExchange.java
role: implementer
tags: []
tracker_refs: []
---
# WP17: A · Sources: guarded HTTPS archive and local path

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

`POST /packs` with `kind: https_archive` or `filesystem` fetches into staging and hands the result to the
registration job unchanged (FR-004). The HTTPS exchange of `HttpsGuideFetcher` is extracted into a shared
`GuardedHttpsExchange` so every SSRF guard is kept, and `HttpsGuideFetcher` becomes a thin user whose tests stay as
they are.

Implementation command:
`spec-kitty agent action implement WP17 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concern:** IC-05 (HTTPS and local-path parts, refusals, target, locks). **Requirements:** FR-004, NFR-004,
  NFR-005, NFR-012.
- **Security squad findings:** 1 (the rebinding guard covers the union of every named allowlist, checked at
  startup), 10 (local-path TOCTOU: `SecureDirectoryStream`, `nlink == 1`, forbidden roots, uniform refusals).
- **This class is the most security-reviewed in `packs`:** the extraction needs its own review lens; keep the diff a
  pure move plus the body-policy parameter.

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

### T083: Extract GuardedHttpsExchange

Move the exact allowlist, global-unicast-only, `GlobalAddressResolverProvider`, no-redirect-but-checked-hops, one deadline, no proxy, no credentials into `GuardedHttpsExchange`, parametrised by a body policy (guides: Markdown, 1 MiB; archives: gzip/zip/octet-stream with magic sniffing, 32 MiB). `HttpsGuideFetcher` delegates; `HttpsGuideFetcherTest` and the loopback test pass unchanged.

### T084: Allowlist union

`charter.pack-sources.https.allowed-hosts`; the resolver provider guards the union of guides, archives and git allowlists, checked at startup; test that a host only in the archive list is still rebinding-guarded.

### T085: HttpsArchiveFetcher

Fetch to an owner-only staging file through the exchange; optional `sha256` must match (else `refused` `422 pack_source_refused`); upstream failure `502`, timeout `504`; reasons from a fixed set (NFR-012). `PackSourceFetcher` port.

### T086: LocalSourceReader

Path relative to `charter.pack-sources.filesystem.root`, walked with a `SecureDirectoryStream` opened on the root; only regular files with `nlink == 1`; links refused; roots under `/proc`, `/sys`, `/dev` or overlapping the config root refused at startup; every refusal reads the same.

### T087: Test seam and scenarios

`StubArchiveExchange` serves archives in the BDD suite (never the network); `POST /packs` scenarios for `https_archive` and `filesystem` green, including the descriptor-name-must-equal-target check and the refusal codes.

## Test strategy

`GuardedHttpsExchangeTest`, `HttpsArchiveFetcherTest`, `LocalSourceReaderTest`, the guide fetcher tests unchanged, the `POST` scenarios.

## Risks

- Any behaviour change in `HttpsGuideFetcher` is a regression: diff its public behaviour via its unchanged tests.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
