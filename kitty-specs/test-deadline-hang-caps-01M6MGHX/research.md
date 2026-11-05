# Research: Deadline checks become hang caps (Phase 0)

## R-01 Findings re-verified on `origin/main` (`85c20fa`)

All eight sites from #320's comment of 2026-11-04 are present at the stated lines:

| Site | Bound today | What it proves | Cause already asserted |
|---|---|---|---|
| `ParsedYamlTest:130` | `assertTimeoutPreemptively` 2 s | the node budget stops an alias bomb in `pack.yaml` | yes: `Unreadable`, `TOO_LARGE`, `PACK_UNREADABLE` finding |
| `ParsedYamlTest:146`, `:153` | same, 2 s | same, for a shard and a fragment | yes: `DRG_SCHEMA_ERROR` finding with `TOO_LARGE` |
| `EmbeddedSchemasTest:80` | `assertTimeoutPreemptively` 5 s | a catastrophic pattern runs in linear time | yes: `containsExactly("pattern at /a")` |
| `InterruptedCommitRepairTest:166` | `succeedsWithin` 10 s | a read waits for the swap and serves the committed state | yes: the committed tool guide is served |
| `InterruptedCommitRepairTest:200` | `assertTimeoutPreemptively` 10 s | a listing inside another pack's lock skips a held lock instead of deadlocking | yes: the listing's content |
| `HttpsGuideFetcherTest:540` | elapsed < 2 s (lookup sleeps 5 s, deadline 200 ms) | the deadline covers the DNS lookup | yes: `Reason.TIMEOUT`; no request sent |
| `HttpsGuideFetcherLoopbackTest:93` | elapsed < 5 s (drip 10 s, deadline 1.5 s) | the deadline covers a dripping body | yes: `Reason.TIMEOUT`; headers seen |

## R-02 Decision: 30 s caps (operator ruling)

- **Decision**: every bound becomes 30 s; the fetcher's slow work becomes 60 s. Decision `01M6MGJA27NG72JCTBXB1TWG80`.
- **Rationale**: at least 15x today's bounds and orders of magnitude above the work's own time, so a starved
  runner cannot miss it; a real regression still fails within 30 s.
- **Alternatives considered**: 60 s (matches the existing `@Timeout(60)` caps, but doubles a regression's cost);
  tagging `timing` (rejected by ADR-0057 and the landing pass: it would make security guards report-only).
- **Correction (2026-11-05)**: as first merged, the claim above did not hold everywhere. The pre-PR squad found
  that the fetcher's elapsed checks ran only after the fetch returned, so a broken deadline waited out the 60 s
  slow work, and that two 2 s bounds in the fetcher's `Deadline` tests and the 10 s latch helpers of
  `InterruptedCommitRepairTest` fired before the cap. The fetches now run under `assertTimeoutPreemptively` with
  the cap, and those bounds and helpers wait up to it; planted breaks now fail at about 30 s or on their cause.

## R-03 Overlap

- `HttpsGuideFetcherTest.send` (nested class) is on #367's refactor list, owned by draft PR #368's mission;
  this mission edits only the nested `Deadline` class of that file, never `FakeTransport.send`.
- PR #377 (Mission A) edits `InterruptedCommitRepairTest` at lines ~336–344 and ~381–390; this mission at ~166,
  ~200, ~214, ~418–426 and ~441. Separate hunks; whichever lands second rebases trivially.
