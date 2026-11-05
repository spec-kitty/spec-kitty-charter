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

## R-03 Overlap

- `HttpsGuideFetcherTest.send` (nested class) is on #367's refactor list, owned by draft PR #368's mission;
  this mission edits only `aSlowLookupCountsAgainstTheSameDeadline`.
- PR #377 (Mission A) edits `InterruptedCommitRepairTest` at lines ~339 and ~382; this mission at ~166 and ~200.
  Separate hunks; whichever lands second rebases trivially.
