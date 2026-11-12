# Pre-merge adversarial squad, 2026-11-12

Three read-only lenses over the aggregate `origin/main...feat/466-slice-b-real-packs-register` (tip 8e2cdb7f, after
the consolidation, the merge of origin/main fd20faac and the regenerated glossary). Each delegate loaded its profile
YAML: Architect Alphonso (model, contract, decision record, rulings), Reviewer Renata (correctness across work-package
seams, tests, brownfield), Debugger Debbie (the swap window, FR-023, the concurrency flake; the only Gradle runner).
**Verdict: PROCEED-WITH-FOLDS (3/3).** Nothing CRITICAL or HIGH. Every finding has a disposition below; nothing is
filed (issue-tracker Rule 1: no finding is MEDIUM-or-above and unfolded).

## Dispositions

| # | Lens | Sev | Finding | Disposition |
|---|---|---|---|---|
| D-1 | concurrency | MED | A swap whose second rename fails and is rolled back restores the same inode and birth time, so the identity check reads it as "no swap" and accepts a torn answer; FR-023 accepts it too (reproduced). | **Folded** (8c7321d5). The suggested generation comparison misses it (one odd value spans the whole bracket), so each pack keeps a count of moves aside, recorded after the first rename and before any rollback; `PackDirectories.read` retries when it changes. `SwapWindowReadTest.t8…` red then green. Re-verified at f166b5c6 by the debugger lens: its repro now answers correctly, and t8 and the repro fail with the count check removed. |
| D-2 | concurrency | LOW | The `PackDirectories` javadoc claimed the identity alone suffices. | **Folded** with D-1. |
| A-1 | model | MED | The reads-during-a-replace fragment hid the open FR-023 call: a verified read whose load refuses while any pack swaps still answers `500 stale_input`. | **Folded** (f29c34d9): the fragment states it and points at ADR-0073 Risks. |
| A-2 | model | LOW | Excluded files were stripped by filtering presentation warnings. | **Folded** (3e7d8672): stripped from a typed `notHeld` list; warnings derived from it. |
| A-3 | model | LOW | Landed docs cite the `open-packs#TBD-*` placeholders. | **At landing**: the orchestrator files the two open-packs issues; the placeholders are replaced with their numbers. |
| A-4 | model | LOW | `PackRegistrationStatus.succeeded` example lacked the `doc` link. | **Folded** (f29c34d9). |
| A-5 | model | LOW | Built-in packs answer `/doc` with `pack_not_found`; no alternative recorded. | **Folded** (20a3a0fc): ADR-0073 decision 9 records the rejected read-only built-in `/doc` and why. |
| A-6 | model | LOW | `creationTime()` falls back to mtime where the platform keeps no birth time. | **Folded** (8c7321d5, 20a3a0fc): javadoc and decision 11 say so; correctness rests on `fileKey` and the move count. |
| A-7 | model | LOW | ETag advice targets operations by method name. | **Dropped**: follows the existing `ContractDocumentETag` pattern and `PackDocumentControllerTest` catches a rename. |
| A-8 | model | LOW | Slice C (#467) meets trivial textual conflicts (refusal-code lists, a positional boolean). | **No change**; the dispatcher has the list and slice C rebases onto this branch. |
| R-1 | brownfield | LOW | `load()` listed an unreadable README in `unmodelled` while an unreadable graph document was not. | **Folded** (80cb880f), load-level assertion red then green. |
| R-2 | brownfield | LOW | The Breaking notes said a stored pack with a retired file "reads as `pack_unreadable`". | **Folded** (f29c34d9, f166b5c6) against a probe of the real service: item and pack reads stay 200 except the step-contract item read (404); composed reads answer 500 for every pack; a stored `active_charter` makes that pack's descriptor reads, composed reads and `/doc` answer 500. |
| R-3 | brownfield | LOW | The nightly open-packs drift watch goes red after merge. | **Folded** (20a3a0fc): ADR-0073 Risks says it stays red until the two open-packs changes land. |
| R-4 | brownfield | LOW | The 1 s swap-window bound could fail on a cold, loaded runner. | **Folded** (d77d49a4): each read runs once without timing inside the window, then the timed read must meet NFR-001. |
| D-3 | concurrency | LOW | `DirectoryRename`'s default three-argument `swap` drops the move callback, so a future rename that moves live aside could skip the count. | **Dropped**: production has one rename (`MOVE_ASIDE`), which records the move; only test lambdas use the default. |
| D-4 | concurrency | LOW | Tests that inject a private `PackWriteGeneration` into `PackCommitStore` do not link writer moves to readers on `shared()`. | **Dropped**: those tests never fail a second rename; production wires one shared instance. |
| F-1 | fold | MED | Found while folding R-2: `/doc` answered 200 for a pack whose `pack.yaml` cannot be read while `getPack` answered 500, against the contract's "judged as getPack judges it". | **Fixed** (f166b5c6): both judge the pack through `PackSummaryQuery.described`; unit test and contract scenario red then green. |

## Evidence

- Gates at f166b5c6 (Java 25, `--max-workers=1`, `--rerun-tasks`, split under ten minutes): `:build-logic:check`,
  `:packs:test`, `:packs:check -x test`, `:kernel:check :contracts:check`, `:deployable:test`,
  `:deployable:check -x test`, `:packs:wholePackRoundTrip`, the remaining modules' `check`; `check_docs.py all`;
  markdownlint-cli2 0.18.1; cspell. All pass.
- `RegistrationConcurrencyTest`: 10/10 at 8e2cdb7f (two under CPU load), 5/5 after the folds, and 5/5 at f166b5c6
  (two under CPU load).
