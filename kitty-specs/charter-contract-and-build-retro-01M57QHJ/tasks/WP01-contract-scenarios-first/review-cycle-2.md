---
affected_files: []
cycle_number: 2
mission_slug: charter-contract-and-build-retro-01M57QHJ
reproduction_command:
reviewed_at: '2026-10-18T17:07:33Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review 2 (reviewer-renata): changes requested

I re-reviewed lane-a ffa614d..HEAD (d4c1873, 3208e1f, 34b48e9, 9075c2a, 452df1e). All 11 findings from
review 1 are fixed, not just reworded (see "Review 1 findings" below). The rework brought in one
regression in lint coverage, and one claim in the docs that the code does not honor. Both are small.

## Findings

1. **[MEDIUM] contracts/lint/ruleset.yaml:179-214 (rule at :183) (`closed-response-objects`) - an open object nested
   inside a response without a title is no longer caught.**
   The new selectors take only the response root and the *titled* schemas below it. An inline,
   untitled object property inside a response schema is never judged.
   Reproduced: I added `meta: {type: object, description, x-derived, properties: {note: ...}}` to
   `PackSummary` in `fixtures/clean.yaml`, without `additionalProperties`. The new ruleset reports no
   violation. The review-1 ruleset (`response-schema-closed` at ffa614d) reports
   `response-schema-closed` on the same document.
   README line 75 says "A response object is always a named schema in this contract (its own file)".
   Nothing enforces that, so the rule's coverage rests on an assumption that nothing checks.
   **Recommendation:** pick one, and add a planted fixture `closed-response-objects.anonymous.yaml`
   with the case above:
   - Make the rule judge untitled objects that stand on their own: response root, `properties[*]`,
     `items`, but not `allOf` parts.
   - Or add a rule, with its own fixture, that every object schema in a response is named. The README
     sentence then becomes true by rule.

2. **[LOW] contracts/build.gradle.kts:353-357 (KDoc), :388-405 and contracts/lint/README.md:47-48 - a
   documented failure case is not implemented.**
   The KDoc and the README say the check fails "when a contract-charter-v* tag exists that the
   CHANGELOG does not record as released". The code checks for stray tags only when the CHANGELOG
   records *no* release.
   Reproduced in a full scratch clone, with `## 0.1.0` recorded and tagged, plus an unrecorded
   `contract-charter-v9.9.9`: the result was "Compatible with contract-charter-v0.1.0", BUILD
   SUCCESSFUL. A release that was tagged but never recorded in the CHANGELOG is then silently
   compared against an older baseline.
   **Recommendation:** fail when `tags - released.map { "contract-charter-v$it" }` is not empty, in
   both branches. Otherwise narrow the KDoc and README to what the code does.

## Review 1 findings: verification

| # | Status | Evidence |
|---|---|---|
| 1 allOf / flatten | Fixed | `breaking()` passes `--flatten-allof`. The `breaking-refusal-code-added` pair is allOf-composed: oasdiff exits 0 on it without the flag, and `compatibilityFixtures` requires a failure, so dropping the flag breaks the build. The 2 breaking pairs fail and the 1 compatible pair passes. Against a v0.1.0 tag at the pre-WP contract: 16 errors, 0 warnings (was 1 error, 17 warnings). |
| 2 fail-open | Fixed | `--depth 1` clone: fails with "This is a shallow clone ...". `-PcontractBaseline=none` skips and says so; `-PcontractBaseline=v1` is refused. Full clone with no release and no tag: "No released baseline". Tag without a CHANGELOG release: fails. Release without its tag: fails. Release with its tag at the pre-WP commit: breaking, fails. Same tag at HEAD: "Compatible". |
| 3 comparator mapping | Fixed | Recorded in the CHANGELOG Provisional section, the operation description and `x-provisional`, and the scenario description. The core claim checks out (`context.py:315-318`: an undeclared non-bootstrap action gives the non-bootstrap result). |
| 4 dangling target | Fixed | Recorded in the same places; the asserted answer is unchanged. |
| 5 parity control | Fixed | `theParityCheckFailsOnASkewedPair` runs the same `assertParity` on an extra token and on a missing token, for all 5 pairs. The single-value `DiagnosticCode` still fails via the empty-list case. |
| 6 fixture shape | Fixed | Fixtures are inline (no `$ref`) and go through `bundle()` before `vacuum report`. |
| 7 transcription | Fixed | `error-responses-are-problems` and `closed-response-objects` are re-expressed: `@property != '200'`, `propertyNames`, and `prefixItems` on the first allOf part. The fixtures model the charter's own packs/context contract. The lines still in common with core are Spectral boilerplate only (`extends`, `function: schema` scaffolding). The README no longer claims more than that. |
| 8 citations | Fixed | `action` and `missionType` are description + allOf[Urn] + `x-derived`; the generated types are still `String`. |
| 9 timeout | Fixed | Connect 30 s, read 60 s, each with a clear error. |
| 10, 11 nits | Fixed | |

Note (c) from the coordinator: the rules are not vacuous. The rules no longer use `$defs`. I checked
`prefixItems` with probes that only it can catch:
- Problem moved to the second allOf part: `error-responses-are-problems` fires.
- The first part titled Problem but without `type: object` and open, so only the composite's
  `prefixItems` branch can judge it: `closed-response-objects` fires.

Every fixture fails exactly its own rule (14 planted fixtures plus clean).

## Regression checks

- No production code changed in the rework; only `WireTokenParityTest` changed under `service/`. The
  scenario assertions are unchanged (the only edits are description lines, and the CHANGELOG records
  them).
- `verifyAll -PopenPacksDir=<open-packs@7f063ae> -PcontractBaseline=none --rerun-tasks
  --max-workers=2` on JDK 25.0.4.1: BUILD SUCCESSFUL, 33 tasks executed. 564 tests, 0 skipped, 0
  failures (adapter-out-yaml 527, charter-service 14, adapter-in-rest 10, charter-domain 8,
  charter-read-app 5). Second run: configuration cache reused, 31 of 33 tasks up to date.
- Without the flag, the lane worktree (a shallow clone) fails the compatibility check, as intended.
