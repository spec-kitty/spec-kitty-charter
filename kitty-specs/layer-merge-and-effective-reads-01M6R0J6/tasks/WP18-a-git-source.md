---
work_package_id: WP18
title: A · Git source with the host git binary
dependencies:
- WP17
requirement_refs:
- FR-004
- NFR-005
- NFR-011
- NFR-012
- C-008
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T088
- T089
- T090
- T091
- T092
- T093
phase: PR A
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/git/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/git/GitSourceFetcher.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/git/GitCommand.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/git/GitVersionCheck.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/git/package-info.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/git/GitSourceFetcherTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/git/GitCommandTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/GitFixtureServer.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/git/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/git/**
- service/packs/src/test/java/ai/speckitty/charter/packs/NetworkCodeBoundaryTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/GitFixtureServer.java
role: implementer
tags: []
tracker_refs: []
---
# WP18: A · Git source with the host git binary

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

`POST /packs` with `kind: git` fetches one ref of a public repository over HTTPS with the system `git` binary
(≥ 2.50.1, C-008, NFR-011), never a work-tree checkout: `init --bare --template=`, `fetch --depth 1 --no-tags` into
`refs/charter/fetched`, `rev-parse --end-of-options`, `ls-tree -r -z`, `cat-file --batch`, each blob written into
staging through the extractor's path rules and limits.

Implementation command:
`spec-kitty agent action implement WP18 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concern:** IC-05 (git part) and the plan's "The system git binary" threat model in full (argument injection,
  transport abuse, SSRF/rebinding with `http.curloptResolve`, hostile content, config and credentials, resource
  exhaustion, cleanup). **Requirements:** FR-004, NFR-005, NFR-011, NFR-012, C-008.
- **Security squad 3:** the `curloptResolve` pin is built from the canonical URL the service rebuilt from parsed
  parts (lower-case host, IPv6 bracketed, port 443), never from the URL as sent.
- **Squad 7:** a request naming a `commit` must resolve to it; a branch ref without a commit requires `If-Match` on
  the target pack.

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

### T088: Version check

`GitVersionCheck` resolves `git` once (`PATH` or `charter.pack-sources.git.executable`), parses `git --version`, disables the git source below 2.50.1 or without the binary (`503 pack_source_unavailable`, NFR-011); the other kinds keep working. Unit tests over version strings.

### T089: GitCommand

`ProcessBuilder` with a fixed argv and no shell; environment built from empty (`PATH` of the git dir, `HOME`/`XDG_CONFIG_HOME` = temp dir, `GIT_CONFIG_NOSYSTEM=1`, `GIT_CONFIG_GLOBAL=/dev/null`, `GIT_TERMINAL_PROMPT=0`, `LC_ALL=C`, `GIT_ALLOW_PROTOCOL=https`, `GIT_LFS_SKIP_SMUDGE=1`); `-c` pins (`protocol.allow=never`, `protocol.https.allow=always`, `http.followRedirects=false`, `submodule.recurse=false`, `core.hooksPath=/dev/null`, `core.symlinks=false`, `core.fsmonitor=false`, `transfer.fsckObjects=true`, `filter.lfs.*=`); deadline 60 s with forced tree destroy; stdout/stderr drained and capped.

### T090: URL and ref validation

URL `https://`, allowlisted host, port 443 or none, no userinfo/query/fragment, no leading `-` in any part; ref a 40/64-hex commit or `refs/heads/…`/`refs/tags/…` per `check-ref-format`, no leading `-`, ≤ 255 bytes; both after `--`. Table tests including hostile values.

### T091: Fetch, resolve, read objects

Resolve and check the host with `AddressPolicy`; pin with `http.curloptResolve=<canonical-host>:443:<address>`; fetch; `rev-parse`; compare to a requested `commit`; `ls-tree -r -z` accepting modes `100644`, `100755`, `040000` only; `cat-file --batch` blobs into staging with the IC-03 rules; a size watch (32 MiB of blobs + a fixed allowance) kills the fetch; at most two git fetches at once; temp dir owner-only, removed in every outcome.

### T092: ArchUnit boundary

`NetworkCodeBoundaryTest`: only `packs.infra.fetch.git` names `ProcessBuilder` or `Process`.

### T093: Fixture server and scenarios

`GitFixtureServer`: a local bare repository served over HTTPS by a test server (`git http-backend` or a smart-HTTP stub) with a test CA; scenarios for a good ref, a hostile ref, a symlink/submodule in the tree, a redirect, a commit mismatch; skip-free: if the CI image lacks git ≥ 2.50.1 the test fails loudly (and the README documents the requirement, WP20).

## Test strategy

`GitCommandTest`, `GitSourceFetcherTest`, `NetworkCodeBoundaryTest`, the git scenarios.

## Risks

- git's own resolver is outside the JVM: the pinned address closes rebinding; the proxy environment is removed.
- CI images need git ≥ 2.50.1: check the image; if it is older, report it in the hand-off note rather than weakening the test.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
