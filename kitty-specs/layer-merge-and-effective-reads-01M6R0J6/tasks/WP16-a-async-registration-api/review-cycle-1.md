---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-07T20:30:39Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP16
---

# WP16 review, cycle 1: changes requested (reviewer-renata)

Reviewed lane-n at ceadd3c1. The head fa184924 holds the same code; the commits after ceadd3c1 change only planning
files and merges. Base 53fb24c8.

The gate is green. `verifyAll --continue` passed, and `:packs:test --rerun` ran 3355 tests with 0 failures and 4
skipped. `:deployable:test` was up to date from the runner's run, 30/30 and 5/5 as reported.

These hand-offs are verified:
- **WP15 cost probe:** creating a pack among 100, 200 and 400 stored packs now takes 0.08, 0.10 and 0.17 s (it was
  0.63, 2.7 and 13.7 s). 200 dependents take 0.73 s.
- **C21 and the healed history:** both still hold on this head.
- **`POST /packs`:** still pinned 501 before binding, through `reserved-registration.feature` and
  `ReservedOperationCoverageTest`.
- **Scenario edits:** each is correct for the stated reason.
  - The 412 race scenario needs a new `pack_version`. The version rule (step 5) runs before the precondition
    (step 6), so a re-send of v1 after a per-artifact PUT moved the revision is `409 pack_version_conflict`.
  - The A6 scenario gained its `depends_on` and the qualified target.
  - The bare self-loops expect `validation_failed`, as WP15's hand-off asked.

Two things block approval.

## Blocking

**Issue 1: a deadline or a shutdown can end a job that has already started committing, so its status and its audit
record say `refused` while the pack was replaced.** `Job.endUnlessCommitting` does this:
1. Under the job's monitor, it reads the phase and returns if the job is `COMMITTING` or `ENDED`.
2. It releases the monitor.
3. It calls `end(refusal)`, which takes the monitor again and rejects only `phase == ENDED`.

Between steps 2 and 3, the worker can run `beginCommit()` (`RUNNING` → `COMMITTING`, `PackRegistrationService.decide`
~278). `end` then overwrites `COMMITTING` with `ENDED`: `registration_timed_out` from the watchdog, or the stopping
refusal from `close()`. It writes the audit entry as `refused:...`, gives back the in-flight slot, and interrupts the
worker.
- If the interrupt arrives after the swap, the commit completes, because `Files.move` and `copyTree` are not
  interruptible. The pack is replaced while `GET /pack-registrations/{id}` and the audit log say refused. That breaks
  FR-024's lifecycle and NFR-010's "one record per attempt with its outcome".
- If the interrupt lands in the history `FileChannel.force`, the commit aborts with a 500-shaped refusal, which is at
  least consistent.

The window is narrow for the 90 s deadline. It is wider for `close()`, which stops every job at once.

Fix: make the check and the end one critical section. For example, `end(Ending result, boolean unlessCommitting)`
returns false when `unlessCommitting && phase == COMMITTING`, under the same monitor that sets `ENDED`, and the worker is
interrupted only when that call ended the job. Add a deterministic test: a seam, as `JobGate` is, that pauses a test
thread between the two blocks while the worker calls `beginCommit()`. Then assert the job ends `succeeded` with a
`succeeded:*` audit entry.

**Issue 2: activation writes now answer `503` with `Retry-After` when the registry is busy, and the contract fragment
does not say so.** `ActivationService.create`, `put`, `delete` and the preset, selection and exclusion services run
under `RegistryWriteCoordination`, which is `RegistryWriteLock.exclusively`. Its `RegistryBusyException` reaches
`RefusalHandler.retryLater`. Before this WP, an activation write never met the registry lock.
`2026-11-07-a-registration-put-and-reads-served.md` lists the per-artifact pack writes' new `503` but not the
activation operations. Add them to the fragment under `### Changed`, with the reason (the WP15 hand-off), and check that
each activation operation's `default` or `503` response covers it (C-001: a contract change lands with its fragment).
`ActivationWritesExcludeRegistrationsTest` pins the lock; a test of the `503` mapping on one activation write would
complete it.

## Non-blocking

1. **Gap 1, the edge units with no unit tests.** Accepted on the BDD coverage of the red run c885ad8b. Two of them
   carry decisions a scenario does not pin, though, and deserve a unit test each:
   - `PackSummaryQuery`: a built-in pack is 404.
   - `PackRegistrationMapper.references`: it silently drops a notice URN whose kind is not an `ActivatableKind`,
     through `kindOf` → empty. A lock on a template or an asset would vanish from FR-027's notices. Log it, or list
     why no such kind can reach the notices.
2. **Gaps 2 and 3.** No separate red commits for `RegistrationAuditFilter`, `RegistrationPaths` and the guard audit,
   and the raised-flags contract commit came after the jobs code. These are process deviations only: the tests exist
   and the fragment landed before the edge that serves the codes. Accepted.
3. **Gap 4.** Update the "Registration steps" section of `contracts/charter/scenarios/README.md` for the new steps
   (`hold/release registration jobs`, `poll the registration`, `the pack registry is held by another write`, the
   archive builders). It is cheap, and the cycle is open anyway.
4. **Gap 5, the out-of-map edits.** The kernel factory, `RefusalHandler`, `WriteCoordination`, the smoke script and the
   scenario edits are each justified by a hand-off, and named in the commits. Accepted.
5. **Gap 6, the implementer's decisions.**
   - Accepted: built-in GET 404 (in the fragment), no `edit` relation, the outcome grammar
     (`succeeded:<outcome>` / `refused:<code>` / `failed:internal_error`), staging under `<config-root>/staging`, and
     no configurable lock timeout.
   - Accepted, with a note for WP20's docs: the 90 s deadline runs from acceptance, so it includes the time a job
     waits in the queue.
6. **The admission counter can drift from the real load.** A job ended by the deadline gives back its admission slot
   (`inFlight`) while its worker may still hold an executor thread (a stager that ignores interrupts). The executor's
   queue is an unbounded `LinkedBlockingQueue`, so admissions can then exceed 2 running plus 8 waiting. Consider
   bounding the queue, or releasing the slot only when the worker leaves `run()`.
7. **The reservation is taken before the body is read.** A slow upload holds an admission slot for its whole
   transfer, so ten slow uploads refuse everyone else `503`. Only holders of the worktree token can do this. Note it
   in WP20's docs, or add a body read timeout.
8. **The staging root falls back to `java.io.tmpdir/staging` when no `ConfigRoot` is present**, and the sweep then
   deletes entries older than 15 minutes in a shared temporary directory. It is also pointed at
   `java.io.tmpdir/packs` for the leftovers. If `ConfigRoot` is always present in the deployable, refuse to start the
   sweep without it rather than fall back.
9. **The size limit is not tested through Spring's real message converter.** The chunked and over-limit `413` is
   unit-tested on the advice directly. The BDD scenario covers a declared length over the limit, but not a chunked
   body through Spring's real `ResourceHttpMessageConverter`, so nothing checks that the advice's
   `ResponseStatusException` survives the converter as a `413` and not a `400`. A scenario or a MockMvc test would
   pin it.

## Anti-pattern checklist

1. Dead code: PASS. The three operations, the sweep and the census are wired, and the deployable is green.
2. Synthetic fixture: PASS. The edges go through BDD over real archives.
3. Silent empty return: one finding, the dropped notice kinds (non-blocking 1).
4. FR coverage: FR-001, FR-004 (PUT path), FR-024, FR-037, FR-039 and NFR-004/008/009/010 each have a test.
   FR-024 and NFR-010 are partial while Issue 1 stands.
5. Frozen surface: PASS.
6. Locked decision: PASS.
7. Shared-file ownership: noted.
   - `PackRegistrationService` and `PackCommitStore` (WP14).
   - `InboundReferences`.
   - `RefusalHandler` (web-transport).
   - The activations slice.
   - The contract YAML and the scenarios.
8. Production fragility: Issue 1.
