---
title: "Mutation testing no-report research, 2026-11-13"
description: Why each red mutation-testing run since 2026-11-10 left a module or group with no report, what main already fixes, and the failure class still open (#461).
doc_status: draft
updated: 2026-11-13T00:00:00Z
type: Plan
kind: Planning
related:
  - adr/0062-nightly-mutation-floor
  - guides/service-build
  - development/issue-tracker
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the cause of each red mutation run, with the run, the step and the log line that shows it.
  - profile: governance-lead
    guidance_text: Read this for what still stands between #461 and a green scheduled run on 2026-11-16.
---

# Mutation testing no-report research, 2026-11-13

**Status: research, not a decision.** It feeds #461 (parent #494). Main was at `848170ec` when this
note was written. Every claim comes from a run's job log (`gh run view <id> --log-failed`) or its
step timings. The artifact downloads were blocked by the session's network policy, so the mutant
counts come from PIT's `>> Generated` lines in the job logs.

## Short answer

The "no report" rows came from two causes, and **no floor was ever missed**:

1. **A test that fails with no mutation applied** (11-10, 11-11). PIT then refuses the whole
   module (`PitHelpError: N tests did not pass without mutation`), so the module writes no
   `mutations.xml`, and the summary shows "no report".
2. **The mutation step ran out of time** (11-11 dispatch, 11-12, 11-12 dispatch). `:packs:pitest`
   was cut off mid-run, so it wrote no report.

Main already fixes both instances: `95a078e5` (activations), `ecd257d9` (`PackFileSetExactnessTest`,
shards, 120-minute budget) and `cad3f433` (`RegistrationJobsTest`). The first sharded run,
`43209733136`, was green in 48 minutes, with the slowest shard at 47 of its 110 minutes.

**What is still open:** the first cause is a *class* of failure, and main fixes only its instances.
A module's `test` task can set a system property that a test reads. PIT's minion JVM does not
inherit it, so each module has to copy it into `pitest { jvmArgs }` by hand. `:activations`
forgot, and the 11-10 run went red. Nothing stops the next module from forgetting the same way.
The green run is also not a proof of main. It ran on `ops/mutation-nightly-timeout` at
`f7ef7dc7`, which is not an ancestor of main. Main has 115 commits after `ecd257d9`, the landed
form of that branch, and no run has been made on it since. Several things on main have therefore
never run: the renamed `mutation-testing.yml` (`98f3288c`), the summary turning red on a group no
shard reported (`63d49d55`) and `:artifacts-edge` (`84db84e2`). `pack-validation` comes next, in #561.

## Run by run

| Run | Event | Head | What failed | Cause | Fixed by |
|---|---|---|---|---|---|
| [42745064601](https://github.com/spec-kitty/spec-kitty-charter/actions/runs/42745064601) | schedule, 11-10 | `1d962f26` | `:activations` no report | `ActivationWireTokenParityTest` (2 tests) failed with no mutation applied. It reads `charter.contractSchemas`, which only `tasks.test` set. The test hit 120,000 ms before failing. | `95a078e5` passes the property to the minion |
| [42948335390](https://github.com/spec-kitty/spec-kitty-charter/actions/runs/42948335390) | schedule, 11-11 | `7b9ff348` | `:packs` no report | `PackFileSetExactnessTest.theFingerprintChangesIfAndOnlyIf…` failed with no mutation applied, after coverage took 256 s | `ecd257d9` writes its trees on first use |
| [43098731862](https://github.com/spec-kitty/spec-kitty-charter/actions/runs/43098731862) | dispatch, 11-11 | `d3d74c31` | `:packs` no report | Mutation step timed out at 80 min. `:activations` finished at 21:20, and `:packs` was still running | shards (`ecd257d9`) |
| [43143758531](https://github.com/spec-kitty/spec-kitty-charter/actions/runs/43143758531) | schedule, 11-12 | `9cd0ad85` | `:packs` no report | Same: timed out at 80 min, with `:packs` still running. The other six modules all passed | shards (`ecd257d9`) |
| [43176780670](https://github.com/spec-kitty/spec-kitty-charter/actions/runs/43176780670) | dispatch, 11-12 | `e2d438d1` | `:packs` shard | `:packs` alone on one runner timed out at 90 min (1,106 test classes) | two `:packs` shards (`ecd257d9`) |
| [43209733136](https://github.com/spec-kitty/spec-kitty-charter/actions/runs/43209733136) | dispatch, 11-12 | `f7ef7dc7` | — | **green** | — |

No run showed an OOM. Every PIT status line reads `MEMORY_ERROR 0 … RUN_ERROR 0`, and no
`OutOfMemoryError` appears. The `:deployable` ArchUnit heap issue in #549's handover is in
`:deployable:test`, which PIT never runs (`:deployable` applies no mutation convention). It is
not a cause here.

## Mutants and time per group, green sharded run 43209733136

| Shard | Module / groups | Mutants | Detected | Tests run | PIT time | Step time |
|---|---|--:|--:|--:|--:|--:|
| `others` | kernel | 692 | 579 | 12,495 | 114 s | 13 min |
| | missions | 150 | 118 | 230 | 37 s | |
| | profiles | 85 | 83 | 88 | 23 s | |
| | profiles-edge | 122 | 121 | 432 | 86 s | |
| | missions-edge | 177 | 164 | 274 | 97 s | |
| | activations | 1,414 | 1,309 | 7,189 | 540 s | |
| `packs-core` | domain+application, infra.fetch | 4,060 | 3,612 | 26,772 | 2,318 s | 41 min |
| `packs-storage` | infra.storage, infra.archive | 3,069 | 2,752 | 27,664 | 2,598 s | 47 min |

**Trend.** On one runner, `:packs` took 48 min on 11-05 (ADR-0062). On 11-12 it was 66 min locally
and about 93 min on a runner. Its mutants grew from 3,999 (the sum of ADR-0062's first-run table) to 7,129. The 11-12
log shows `:kernel` at 692 mutants (378 at first run) and `:activations` at 1,414 (581 at its
amendment). The shards take this growth with room to spare: the slowest used 43 % of its step
budget. Since `f7ef7dc7`, main has changed 65 `:packs` main files and 117 `:packs` test files,
so the next run will be slower by an amount nobody has measured.

## Similar surfaces

| Workflow | Same shape? | Disposition |
|---|---|---|
| `timing-nightly.yml` | No. Green 6/6; its tasks pass `charter.requireSymlinks` themselves | None |
| `sonar.yml` | No. Green 6/6 | None |
| `docs-nightly.yml` | No. Green 5/5 | None |
| `open-packs-drift.yml` | No. Red on content (the 3 hand-written manifests, open-packs#250 and #255), not on a missing report | Known; tracked under #549 |
| `service-native.yml` | No. Red 11-11 and 11-12, green since 11-12 dispatch | None |
| Other `Test` tasks in `:packs` (`wholePackRoundTrip`, `gitHostTest`, `timingBudgets`, `driftTest`) | Each sets its own properties; none feeds PIT | None |
| `pitest` in every module with `charter.mutation-conventions` | **Yes, the hazard.** `:packs` copies its shared map, and `:activations` copies one property by hand. The other six (kernel, missions, profiles and the three edges) set no test property today, so they are safe only by accident | Fixed: the convention passes them (Remediation) |

## Tracked issues

| Issue | Relation | Disposition |
|---|---|---|
| #461 | This work | `Refs`: it closes on a green scheduled run (2026-11-16) |
| #494 | Parent epic | Closes with #461 and #463 |
| #463 | SonarCloud sibling under #494 | Not touched |
| #530 | PIT minion JVM flags (an A/B measurement) | Open. Not needed for green at 43 % of budget; stays a speed follow-up |
| #531 | Remaining costly `:packs` tests under PIT | Open. Same reason |
| #549 / #561 | `pack-validation` joins the `others` shard at floor 85 with **margin 0** (1 mutant) | Overlap: #561 edits the `mutation-testing.yml` header comment. A single surviving mutant there turns the run red. That is the operator's ruled floor, so it is flagged, not changed |
| #387, #388, #389 | Mutation CI hardening | Delivered in `8626feae` |

## Remediation

1. **Close the failure class, not just its instances.** `charter.mutation-conventions` now passes
   every system property of the module's `test` task, and the JUnit tags it leaves out, to PIT's
   minion. A property a test needs therefore reaches PIT by construction. `:activations` and
   `:packs` drop their copies, and `:packs` still leaves `stress` out of PIT alone.
   - The values are read through `provider {}`. A mapped `test` provider would have made `pitest`
     depend on `test`: the review squad caught this, and `:activations:test` ran before `pitest`
     in the first local runs.
   - A property the minion cannot receive intact fails the build and is named: a `null`, a
     provider, or a comma, since the plugin joins `jvmArgs` with commas.
2. **Prove main before Monday.** A `workflow_dispatch` of `mutation-testing.yml` on the fix
   branch is the gate before the 2026-11-16 run. Without it, the renamed workflow, the summary's
   gap check and `:artifacts-edge` run for the first time on the scheduled run itself.
3. Every floor, the shards and the 120/110-minute budget stay as ruled.

**Not done (latent, no Monday risk).** `profiles-edge`, `missions-edge` and `artifacts-edge` keep
PIT's default `targetClasses` (`ai.speckitty.charter.*`), and their groups exclude `*.config`. A
whole-module run never tells PIT to leave those packages out. Today their config classes are empty
`@ComponentScan` classes and produce no mutants. A `@Bean` added to one would put a mutant into a
package that no group holds, and `checkMutationFloors` would fail the module.
