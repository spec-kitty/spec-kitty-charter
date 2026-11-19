---
affected_files: []
cycle_number: 1
mission_slug: pack-delete-and-update-guard-01M7SANQ
reproduction_command:
reviewed_at: '2026-11-19T10:14:02Z'
reviewer_agent: reviewer-claude
wp_id: WP05
---

# WP05 review (independent reviewer, cycle 1): REJECT

Target: feat/517-pack-delete-and-update-guard @ 85d47e3. `./gradlew verifyAll` green.

## R5-1 (HIGH): a PUT dry run can write
For `registerPack` (PUT) the mode comes from `RegistrationAdmission.modeOf`, which accepts only
`"true".equalsIgnoreCase(...)`. The bound `Boolean dryRun` is ignored (see the comment in
`PackRegistrationController`). Spring binds the query boolean with `StringToBooleanConverter`. A probe showed:
  `1`=true, `yes`=true, `on`=true, `" true"`=true.
So `PUT /packs/x?dryRun=1` passes binding as a dry run but runs in `Mode.COMMIT`: the pack is written, which is
the one thing a dry run must never do. POST and DELETE use the bound value, so the three operations disagree.
The audit filter uses the same strict test, so on DELETE `?dryRun=1` gives a dry run that is audited (NFR-002,
lower severity).
Fix: one parse for all three operations and the audit filter. Either accept exactly what Spring accepts, or
(better, matching the contract's boolean) refuse any `dryRun` value other than `true`/`false` with 400 before
the body is spooled. Add a `@contract` scenario: PUT `?dryRun=1` must not store the pack (or answers 400).

## R5-2 (MEDIUM): the job's audit skip has no test
T018 says "a dry-run job ... records no audit entry". No test asserts `Job.record` skips `DRY_RUN`, and no
scenario checks the audit log after a dry-run PUT/POST.

## R5-3 (LOW): I2, the defaulting overloads
`RegistrationJobs.Origin`'s 5-argument constructor and the 4-argument `register(...)` keep the old call shape
beside the new one. They are internal, not a model shape, so they do not breach the charter. Prefer passing
`Mode` explicitly at the few call sites.

## Judged sound
- P5: asynchronous 202/job; `dryRun: true`; no `pack` link on a dry-run status; `self` is the would-be URI
  (404 after a would-be create, documented); `previousVersion` and `dryRun` are not on `getPack`.
- D5: one code path. The dry run stops after `beforeCommit` and before `commit`/history/`impact.committed`.
