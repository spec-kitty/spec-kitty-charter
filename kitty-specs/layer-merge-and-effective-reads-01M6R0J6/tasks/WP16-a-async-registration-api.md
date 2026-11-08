---
work_package_id: WP16
title: A · Asynchronous registration API, access, flags and audit
dependencies:
- WP15
requirement_refs:
- FR-001
- FR-004
- FR-024
- FR-037
- FR-039
- NFR-004
- NFR-008
- NFR-009
- NFR-010
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T076
- T077
- T078
- T079
- T080
- T081
- T082
- T151
phase: PR A
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationController.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationJobs.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/AuditLog.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArchiveBodyAdvice.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RegistrationProperties.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationJobsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRegistrationControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArchiveBodyAdviceTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackRegistrationSteps.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/RegistrationWiring.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StagingSweeper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FreeSpaceCheck.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StagingSweeperTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FreeSpaceCheckTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationJobs.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/AuditLog.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArchiveBodyAdvice.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RegistrationProperties.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationJobsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRegistrationControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArchiveBodyAdviceTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackRegistrationSteps.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/RegistrationWiring.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StagingSweeper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FreeSpaceCheck.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StagingSweeperTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FreeSpaceCheckTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP16: A · Asynchronous registration API, access, flags and audit

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Serve `PUT /packs/{packId}`, `POST /packs` (handing a source to the fetchers of WP17/WP18), `GET /packs/{packId}`
and `GET /pack-registrations/{registrationId}` asynchronously (FR-024): synchronous checks, then `202` + status URI;
a bounded executor runs the registration job; the client polls (no outbound callback: SSRF).

Implementation command:
`spec-kitty agent action implement WP16 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concerns:** IC-14, IC-18, IC-02 (controller). **Requirements:** FR-001, FR-004, FR-024, FR-037, FR-039, NFR-004,
  NFR-008, NFR-009, NFR-010.
- **Synchronous checks:** access (there is no principal: every request already needs the worktree bearer token, which `WorktreeGuard` checks before routing and answers `401`, so registration adds no `401` of its own; plan delta squad finding 11), `charter.registration.enabled` (default
  `false`) and `charter.pack-sources.<kind>.enabled` (`503 pack_source_unavailable`), the packId grammar, body size
  (`413` from `Content-Length` before Spring buffers), media type and magic bytes (`415`), `If-Match`/`If-None-Match: *`.
- **Executor:** 2 running, 8 waiting; the next answers `503` with `Retry-After`. `If-Match` re-checked under the lock;
  moved → `refused` `412`. Status kept 1 h after it ends, at most 1,000 entries (one replica). Identical re-send ends
  `succeeded` with outcome `unchanged`.
- **Audit (NFR-010):** one record per attempt through an `AuditLog` port (structured log default): the worktree
  identity (`ServiceInfoQuery.worktree()`, the `Charter-Worktree` value; no principal exists), `packId`, source kind and
  locator with the query cut, outcome, revision before/after. Under the development flag the guard runs without a
  token; the record then says the request carried none.

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

### T076: ArchiveBodyAdvice

Reads `Content-Length` and wraps the stream before binding: `413` over 32 MiB without reading; magic bytes vs `Content-Type` (`415`); a chunked body is counted while streaming. Test the `413` with a request stream that fails the test if any byte is read.

### T077: RegistrationJobs

Bounded executor, job states, retention (1 h, 1,000), `503 Retry-After` when full; tests with a fake clock.
- **Deadline (NFR-008):** a job not finished at the source deadline (60 s) + 30 s ends `refused`
  `registration_timed_out`; fake-clock test, no real wait.
- **Preconditions under the lock (IC-14):** `If-Match` is re-checked after the registry lock is taken; a revision that
  moved since the `202` ends the job `refused` `412` (unit test with a competing commit).
- **Lock contention:** with the registry lock held and an injected 50 ms `tryLock` timeout, the job ends `refused`
  `503` (deterministic; WP12's injectable timeout).
- **Nothing slow under the lock:** a fake fetcher/extractor asserts `RegistryWriteLock.isHeldByAnyThread()` is false
  whenever it runs.
- **Latch seam:** a test-only `JobGate` hook lets the BDD glue hold a job in `pending` or `running`; production wiring
  passes through.

### T078: Controller and mapper

The four operations on the generated interface; `Location` and `_links`; `PackRegistrationMapper` to the contract shapes; `GET /packs/{packId}` reads the stored summary.

### T079: Access, flags and properties

`RegistrationProperties` (`charter.registration.*`, `charter.pack-sources.*`); `503` paths (the `401` is the worktree guard's, before routing); `RegistrationWiring` in deployable. Replace WP11's reserved `RegistrationApi` handlers for the operations this WP serves, and bind the IC-15 impact and version-conflict scenarios WP11 marked `# bound by: WP16` (decision `DM-01M6TVMGH94ZF07KSWSCANEASV`). The status `200` carries no `Charter-Source-Fingerprint` (WP11 removed it, decision `DM-01M6TV6VDNHNCVWRHHC74WATV0`). The `packId` path variable is checked against `^[a-z0-9][a-z0-9-]{0,62}$` before the body is read (synchronous `400`, decision `DM-01M6TV6VDNHNCVWRHHC74WATV0`), table-tested with WP14's hostile ids.

### T080: Audit

`AuditLog` port + structured-log adapter. Tests assert exactly one record per attempt — accepted, refused (async),
failed, and each **synchronous** refusal (`401`, which the guard answers before the controller: the guard writes the audit record for a registration or `DELETE` path (decision `DM-01M6TR1KDAJ64GMJD6AYNZKT59`), `413`, `415`, `400` packId, queue-full `503`), which happen before
the controller — and that each record carries the NFR-010 fields: the worktree identity, `packId`, source kind, locator with the
query cut, outcome, revision before and after. (WP19 adds one record per `DELETE` outcome.)

### T081: Scenario glue and NFR-008

`PackRegistrationSteps`: build `tar.gz`/`zip` from the inline fixture packs, poll the status URI, hold jobs through the `JobGate` seam. Bind the `PUT`-path scenarios of `whole-pack-registration.feature` (the AS6 race and the status lifecycle included), record the red, remove their `@WIP`, make them green. NFR-008 latency: `202` within 1 s of the last byte at p95 over 50 runs of a 32 MiB archive, a `timing`-tagged test in `timingBudgets`, reported at 1× and 3×, report-only per ADR-0057 (decision `DM-01M6RN5SG19KXAHH4VGT9QBYA1`); the deadline is the deterministic gate (T077).

### T082: Native upload test

Add one `tar.gz` and one `zip` upload to the nightly native test set (ADR-0060), with any hint beside `ActivationRuntimeHints`' pattern.

### T151: Staging sweep and the free-space refusal (NFR-009)

- `StagingSweeper`: at startup and every 5 minutes, deletes staging directories older than 15 minutes that no running
  job owns. Fake-clock test: a 16-minute-old orphan is removed, a 16-minute-old directory of a running job and a
  14-minute-old orphan are kept.
- `FreeSpaceCheck`: before writing, a registration ends `refused` `503 pack_storage_full` (declared by WP11) when
  the pack volume has less than 2× the archive bound (64 MiB) free. Test with a fake `FileStore` at 63 MiB (refused, nothing written) and 65 MiB (accepted).

## Test strategy

Unit tests per class (fake clock for retention, deadline and sweep; fake `FileStore`; injected lock timeout);
the audit field assertions; `whole-pack-registration.feature` `PUT` paths green (race and lifecycle included); the
NFR-008 latency in `timingBudgets`, report-only.

## Risks

- A restart forgets pending jobs (their URI answers `404`); clients re-send, which the version rule makes idempotent — document in WP20.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:packs:test` and `:deployable:test --tests '*CharterContractSuite*'` pass; the `PUT`-path scenarios of `whole-pack-registration.feature` have no `@WIP`; `registration_timed_out`, `refused 412` after `202`, the `503` on contention, the sweep and the free-space refusal each have a deterministic test.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.

## Hand-off from WP12 (2026-11-07)

- The registration job maps `RegistryBusyException` to `refused` `503` with `Retry-After` (WP12 hand-off; coordinate with WP14 so the mapping lives in one place).
- When binding the AS8 scenario (`override_not_permitted` on a built-in lock), fix its assertion: the pack is created as `main` and named `main-pack`, so assert `/problem/packs/1/packId` is `main` or check `/name` (WP11 review cycle 2).

## Hand-off from WP14 (2026-11-07)

- `PackRegistrationService` lets `RegistryBusyException` propagate. Map it to `503` with `Retry-After` in one place that
  serves both the registration job and the synchronous per-artifact writes (ADR-0065 D12, WP12 hand-off); today the
  per-artifact writes still answer `500 internal_error` on contention. Add a scenario for the per-artifact `503`.
- `409 artifact_referenced` carries `violations` only (pointer `<pack>/<referrer>`), because the kernel's
  `CharterRefusal` has no factory taking both `packs` and `violations`. Add that factory (out-of-map kernel edit, with
  its rationale) and have the refusal list the referring packs as the contract asks (Q2/Q3 ruling).
- WP14 flipped `pack_version_conflict` and `artifact_referenced` to `raised: true`, because `ReservedRefusalCodesTest`
  requires it once a main class names the code. Confirm this reading of the `x-refusal` text when you replace the
  reserved handlers, and reword that text if it still says a reserved operation's codes stay `raised: false`.
- `If-Match` together with `If-None-Match: *` throws `IllegalArgumentException` in `Preconditions`; the controller
  answers it `400`.
- Crash leftovers: `.pack-commit-*` and `.pack-write-*` folders under the storage root are never removed after a
  crash. Clean them in the job sweep (or at start-up), under the registry lock.
- The tree copy in `replaceWhole`/`create` runs under the registry lock; with the 5 s `tryLock` a large pack can make
  other writers answer `503`. If the job's tests show it, copy before taking the lock.

## Hand-off from WP15 (2026-11-07)

- `whole-pack-registration.feature`: the `self_override` row `A overrides A` cannot reach `self_override`; the staged load
  refuses a bare self-loop first (`422 validation_failed`). Rewrite the row with qualified endpoints that resolve to one
  place (`base::directive:X` onto `base::directive:X`), as WP15's tests do, and keep a bare self-loop row expecting
  `422 validation_failed`. The same holds for `enhances` onto itself.
- The A6 scenario's edge `directive:A → directive:LK` is bare and resolves only in another pack, so the compose refuses
  `pack_reference_unqualified` first; write it `main-pack::directive:LK`.
- `RegistrationOutcome.notices` is now a record (`floorAdded`, `floorRemoved`, `locksAdded`, `locksRemoved`); map it
  into the job's status body as the contract's notices.
- The start-up census logs one WARN per stored pack whose closure the impact check would now refuse (decision 01M6VD94VEVPSC1JXQ1RA7YYHQ covers
  C21 through the staged gate).
- From WP15's review: activation writes do not take the registry lock, so an activation stored while a registration runs
  is judged against the old packs, and the registration's impact check does not see it. Make activation writes take the
  registry write lock (they are rare), with a test that interleaves the two.
- From WP15's review: leftover `.history/*.tmp-*` files belong in the same sweep as the commit leftovers.
- From WP15's review (must land in PR A): registration time grows with the square of the number of stored packs in
  WP14's path. Creating one unrelated pack takes 0.63 s with 100 stored packs, 2.7 s with 200 and 13.7 s with 400,
  past NFR-009's 5 s at about 300. The step-3 identity scan (`requireFreePackId`, `requireFreeName`), and plausibly
  `InboundReferences` and the staged gate, list and parse every stored pack per pack. Take the location listing once
  per registration session (as WP15's `StagedPackViewFactory` does) and pin it with a test that counts listings or
  descriptor parses, red first.
