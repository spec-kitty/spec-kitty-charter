---
title: "Open-packs drift watch research, 2026-11-13"
description: Why the open-packs drift watch (#509) is still red on main, where its design leaks, and what keeps charter and open-packs aligned, with the operator rulings.
doc_status: draft
updated: 2026-11-13T00:00:00Z
type: Plan
kind: Planning
related:
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - development/issue-tracker
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for why the drift watch is red, where its design leaks, and the file and line of each gap.
  - profile: governance-lead
    guidance_text: Read this for the status of #509, the CI gating matrix across both repositories, and the rulings on each recommendation.
---

# Open-packs drift watch research, 2026-11-13

**Status: assessment, not a decision.** A research squad ran on #509, the issue the nightly drift
watch (`open-packs-drift.yml`, ADR-0070) keeps open. It had three lenses: an architect, a debugger
and a researcher, each with its profile loaded and reading the code itself. This note changes no code.

**Revisions:**

- charter `main` at `d73d813`, which includes #542 (manifest membership, merged 2026-11-13);
- #545's head at `928de5e` (open);
- spec-kitty-open-packs `main` at `736e613`.

## 1. Is #509 resolved? No: main is red

The squad ran `:packs:driftTest -PopenPacksDir=<open-packs @ 736e613>` locally on charter `d73d813`.
One test failed, with **23 problems**:

| Problems | Pack | Cause | Class | Fixed by |
| --- | --- | --- | --- | --- |
| 20 | `spdd` | `drg/fragment.yaml` (gate kind `org-fragment`) is not modelled, so the round trip never writes it. The five step enhancements then read back with `0 enhances edges`, and their prompt fragments and the written copies go unpaired. | harness (charter) | #545 |
| 1 | `built-in` | `pack-manifest.yaml` refused: it leaves out presets `default` and `minimal` | pack content | spec-kitty-open-packs#250 |
| 1 | `foundation` | `pack-manifest.yaml` refused: it leaves out 31 constituents under `missions/` and `toolguides/` | pack content | spec-kitty-open-packs#250 |
| 1 | `writing` | `pack-manifest.yaml` refused: it leaves out 21 constituents | pack content | spec-kitty-open-packs#250 |

**The `spdd` red.** It is the same red that the 2026-11-13 nightly run
([43365244553](https://github.com/spec-kitty/spec-kitty-charter/actions/runs/43365244553)) reported,
which ran before #542 merged.

**The three manifest reds are new with #542.** The gate now refuses a source manifest whose lists
differ from the derived ones. Charter's model is canonical, so the fix belongs to open-packs: it
regenerates or drops those manifests.

**Nothing comes from pinning.**

- The watch checks out open-packs `main` and never reads `openPacksRef`. That property pins only
  `syncPacks` and the usability run, so raising it can neither fix the watch nor turn it red.
- Open-packs `1692a3b..736e613` changes nothing under `packs/`: the #249 and #251 commits touch only
  CI, docs and test fixtures. So the run #545 proved green
  ([43315366150](https://github.com/spec-kitty/spec-kitty-charter/actions/runs/43315366150)) holds
  for today's pack content too, as far as `spdd` goes.

**Expected after #545 merges:** red on the three manifests alone, until open-packs #250 lands.

**Operator ruling:** #545 keeps `Closes #509`. When #545 merges, #509 closes. The next red nightly
run on `main` then opens a fresh `open-packs-drift` issue for the manifests, and that issue points
at open-packs #250.

## 2. Is the drift watch's design sound? Mostly, with five leaks

The core is sound. Charter's own loader puts every open-packs pack through the gate's meaning-level
round trip (ADR-0036), nightly, and blocks nothing. It earned its keep: its last red was a charter
defect that the example packs did not reach, which is the risk ADR-0070 names. The leaks are below.

1. **An unmodelled kind passes silently.** `OpenPacksDriftTest` runs the gate's `observe`, which
   iterates only the round trip's outcomes.
   - A census file whose kind `DomainRoundTrip` does not model gets no outcome and no message.
     That is how `org-fragment` surfaced only as a side effect on `spdd`.
   - `WholePackExpectation.evaluate`, which does report "no adapter models kind X", is never called
     by the drift test.
2. **The gate's kind check is tied to `KindBindings`, not `file-kinds.yaml`.**
   - The gate round-trips only `built-in`, which has no fragment.
   - `ExamplePackCoverageTest` proves `org-base` has a fragment file, but nothing round-trips it.
   - #545 adds one hand-written test for `org-base`, which fixes the kind but not the class of bug.
3. **The issue lifecycle trusts any run.**
   - `recover` and `alert` have no `github.ref == 'refs/heads/main'` guard, so the dispatch on
     `slice-b-closeout-proof` closed #509. The concurrency group is per ref, so a branch run also
     races the nightly run.
   - One issue covers every pack, so fixing one pack can close it while another stays red.
   - After a close, the next red opens a new issue rather than reopening the last one.
   - A build failure files under the same label.
4. **The report is thin.** The issue body names neither the open-packs SHA nor the failing packs.
   There is no job summary, and the artifact is kept for 14 days.
5. **The pin policy contradicts itself.**
   - ADR-0070 says `openPacksRef` "may lag".
   - The charter's `.kittify` AGENTS.md says a stale pin is a defect.
   - The pin has moved 6 times in 20 days, with gaps of 1 to 9 days (median 2.5). It is now 4 days
     and 181 open-packs commits behind.

## 3. What gates what today

| Repository, check | Required | What it checks | Against which version of the other |
| --- | --- | --- | --- |
| charter `whole-pack.yml` | yes | round trip over charter's own example packs | none |
| charter `service.yml` | yes | `syncPacks` digests against `open-packs.lock.yaml`, then `verifyAll` | `openPacksRef` (governance only) |
| charter `open-packs-drift.yml` | no, nightly | round trip of every open-packs pack, unmodelled files, load warnings | open-packs `main` HEAD, SHA not recorded |
| open-packs `validate` | yes | JSON schemas plus a Python port of charter's file classification | charter schemas at `charter.lock.yaml` |
| open-packs `charter-service` | yes | the real service composes every pack and registers org and project packs | charter `17ce3f9`, built from source |

The drift has three structural roots:

- **Pin skew in both directions.** Open-packs proves itself against charter `17ce3f9`, 30 commits
  behind `main` and before #542. Charter's watch proves charter `main` against open-packs `main`.
  So each repository can merge a change the other's CI only sees later.
- **A second implementation.** Open-packs reproduces `PackFileKind.classify`, `requiredNotHeld` and
  the required floor in Python. #249 widened the port, and the #251 fold made the charter-service
  job's `file_not_held` verdict depend on it.
- **No round trip in open-packs.** Open-packs never runs the write-back round trip, so ADR-0070's
  "round trip proven in open-packs" is not yet true. Only charter's watch proves it.

## 4. Recommendations and rulings

| # | Recommendation | Owner | Size | Order | Ruling |
| --- | --- | --- | --- | --- | --- |
| 1 | Merge #545: models `org-fragment`, raises the pin | charter | S | 1 | in flight; keeps `Closes #509` |
| 2 | Regenerate or drop the hand-written manifests of `built-in`, `foundation` and `writing` | open-packs | S | 2 | already tracked: spec-kitty-open-packs#250 |
| 3 | The drift test fails on any census file with no round-trip outcome. The gate ties its kind check to `file-kinds.yaml` and round-trips every example pack. | charter | S/M | 3 | **filed: #547** |
| 4 | `service-v*` releases, then a `pack-check` jar on the service's code path that also exposes the whole-pack round trip and file classification, so open-packs drops its Python mirror and gates on meaning | charter, then open-packs | M, then L | 4 | **commented on #478**, no new issue |
| 5 | Watch lifecycle: run `alert` and `recover` only on `main`; put the open-packs SHA and failing packs in the issue and the job summary; reopen rather than duplicate; give build failures their own label | charter | S | — | not taken now (it could fold into #480 later) |
| 6 | Pin policy: settle "may lag" against "stale is a defect", and let a green nightly run open a bot PR that raises `openPacksRef` | charter | M | — | not taken now |
| 7 | Reverse drift watch in open-packs: a nightly run against charter `main` that files on red and bumps `charter.lock.yaml` on green | open-packs | M | after 4 | draft text below; to be filed by the orchestrator |
| 8 | Meaning round trip in open-packs' `charter-service` job, through the `pack-check` jar of 4 | open-packs | M | after 4 | draft text below; to be filed by the orchestrator |

**Shared fixtures: not recommended.** ADR-0070 makes charter's example packs the only test input for
charter. The contract both repositories already share is `contracts/packs/file-kinds.yaml` plus the
schemas. Recommendations 3, 4 and 7 make that one contract cover both sides, rather than copying packs.

## 5. Draft issues for spec-kitty-open-packs

This session could not attach spec-kitty-open-packs with write access, so these are drafts.

### Draft A: a nightly watch of charter `main`

> **Title:** Nightly: prove the packs against charter `main`, and bump `charter.lock.yaml` on green
>
> `charter.lock.yaml` pins charter `17ce3f9`, 30 commits behind charter `main`. Charter #542 (manifest
> membership derived, merged 2026-11-13) makes charter refuse the hand-written manifests of
> `built-in`, `foundation` and `writing` (#250). This repository's CI could not see that until the
> lock moves.
>
> **Ask:**
>
> - Add a scheduled, non-gating workflow that runs the `charter-service` job's checks against
>   charter `main`, the mirror of charter's `open-packs-drift.yml`.
> - On red, it opens or comments on one labelled issue naming the charter SHA and the failing packs.
> - On green on `main`, it opens or updates one PR that bumps `charter.lock.yaml` to the tested SHA.
>
> **Refs:** charter #509, #542, #547; ADR-0070.

### Draft B: a meaning round trip in the gate

> **Title:** Prove the whole-pack round trip in the `charter-service` job, through charter's `pack-check`
>
> Only charter's nightly drift watch runs the write-back round trip (ADR-0036 equality of meaning)
> over these packs, so a pack-content fault shows up after merge, in the charter repository. The
> file classification in `validate` is a Python port of charter's rules (#249, #251).
>
> **Ask:** once charter publishes `pack-check` (charter #478), the `charter-service` job does two things:
>
> - it runs the round trip and the classification through `pack-check` at the pinned release, as a gate;
> - it retires the Python port of `PackFileKind.classify`, `requiredNotHeld` and the required floor.
>
> **Blocked by:** charter #478.
