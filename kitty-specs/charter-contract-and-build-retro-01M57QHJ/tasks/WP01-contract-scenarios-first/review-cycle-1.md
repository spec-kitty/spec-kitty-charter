---
affected_files: []
cycle_number: 1
mission_slug: charter-contract-and-build-retro-01M57QHJ
reproduction_command:
reviewed_at: '2026-10-18T16:46:22Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review 1 (reviewer-renata): changes requested

Reviewed lane-a cedf498..31b1439 (HEAD f3c270d). Most of the WP is solid: C-001 holds (codes respelled,
sort by full URN, action entry wins on dedup, pack-level diagnostics byte-identical apart from the code,
`detail` still compared exactly). Checksums match the vendors' checksums.txt, a wrong pin fails closed,
the configuration cache invalidates on a ruleset edit, and verifyAll is green. The blocking findings are
all about the breaking-change check and about divergences from the oracle that the rulings say must be
recorded.

## Red-first evidence (recorded by the reviewer; the implementer did not record any)

- Scenarios at cedf498 over the pre-WP code (`:charter-service:test --tests '*CharterContractSuite'`):
  11 scenarios, 9 failed for the expected reasons. The 6 code scenarios got `ACTION_NOT_FOUND`,
  `MISSION_TYPE_NOT_FOUND`, `DRG_SCHEMA_ERROR` (x2), `PACK_LEVEL_MISMATCH` and `PACK_LEVEL_MISSING` (x2
  bodies). The sort scenario and the dangling-endpoint scenario failed on the old edge order. The 2 that
  passed are expected to pass: the DIRECTIVE_042 dedup scenario (pins existing behavior) and
  `unresolved_edge_endpoint` (spelling unchanged).
- At 0c48478 (contract docs changed, Java not yet): the same 9 failed, the refusals with
  `IllegalArgumentException: Unexpected value 'PACK_LEVEL_MISSING'` from the generated `fromValue`.
  This shows e7d8b9e was needed.

## Findings

1. **[MEDIUM] contracts/build.gradle.kts:310-311 - The breaking-change check does not see a refusal code
   added to `CharterRefusal`.**
   `CharterRefusal` composes `Problem` through `allOf`. Without `--flatten-allof`, oasdiff compares the
   allOf branches one at a time and reports an enum value added to a response as a warning only, and
   `--fail-on ERR` lets it pass. Reproduced: I added `brand_new_code` to the three refusal-code enums of
   the current bundle and got `3 changes: 0 error, 3 warning`, exit 0. With `--flatten-allof` it exits 1.
   Against a `contract-charter-v0.1.0` tag on the pre-WP contract, the same thing happens: 1 error and
   17 warnings, and all the refusal-code changes are among the warnings. These codes are the part of
   the contract consumers branch on.
   **Recommendation:** pass `--flatten-allof` to `oasdiff breaking`, and give the check its own planted
   fixtures, the way `lintFixtures` does for the ruleset. That means a baseline/revision bundle pair
   that must fail (a removed required property, and an enum value added inside allOf) and a pair that
   must pass. Today the check is the only contract task with no planted fixture or positive control, and
   the path that runs when a baseline exists is never exercised by verifyAll.

2. **[MEDIUM] contracts/build.gradle.kts:288-293 - The breaking-change check fails open when tags were
   not fetched.**
   "No contract-charter-v* tag" and "the tags are not in this clone" look the same to the check.
   Reproduced: in a `--depth 1` clone of a repository that has `contract-charter-v0.1.0` (which is
   breaking against HEAD), the check printed "No released baseline" and passed. A default CI checkout is
   shallow, and the WP04 note about `fetch-depth: 0` depends on a workflow setting the build cannot see.
   **Recommendation:** fail when `git rev-parse --is-shallow-repository` is `true`, or when the repository
   has no tags at all and no explicit `-PcontractBaseline=none` opt-out is given. Then a missing baseline
   is always a stated fact, never an artifact of the checkout.

3. **[MEDIUM] contracts/charter/CHANGELOG.md:53 (Provisional) and
   contracts/charter/scenarios/action_context.feature:166 - The "comparator mapping recorded" part of
   the Call 2 ruling is not carried out.**
   DM-01M57SQ5X988XG4FHYFDS8RAA3 and research.md §10 (A4) say to keep the 404 for an action that is not a
   node "with the comparator mapping recorded". Nothing in the contract, the scenarios, the changelog or
   the code records how the conformance comparator maps the charter's `404 action_not_found` to core's
   answer for the same input. No other WP owns this.
   **Recommendation:** record the mapping where the corpus (#9) will read it: a line in the CHANGELOG
   Provisional section, plus the scenario's description line or the operation's `x-provisional` text.
   State what core answers and how the comparator treats the difference.

4. **[MEDIUM] contracts/charter/scenarios/action_context.feature:137 and
   contracts/charter/CHANGELOG.md:30-31 - The answer for a dangling scope endpoint diverges from the
   oracle, and the divergence is not declared as a provisional gap.**
   Core skips an artifact URN with no node (`delivery_table.py:336-339`:
   `node = merged.get_node(urn); if node is None: continue`). The charter lists it with `label: null`.
   A7 says to pin today's behavior, and the scenario does that correctly. But C-002 (oracle parity) means
   the difference has to be visible: the Provisional section lists the walks, activation filtering and
   the load-error degrade, and leaves this one out.
   **Recommendation:** add it to the CHANGELOG Provisional list and to the scenario description ("core
   omits it; the corpus decides, #9"). Keep the asserted answer as it is.

5. **[LOW] service/adapter-in-rest/src/test/java/ai/speckitty/charter/rest/WireTokenParityTest.java:75 -
   The positive control does not show that the parity check can fail.**
   It shows that `fromValue` is strict, and only for 2 of the 5 enums. The set comparison at line 70
   ("the generated enum has no value the domain lacks") has no control.
   **Recommendation:** extract the per-pair check into a method, and assert that it throws
   `AssertionError` on a deliberately skewed pair: the domain tokens plus an extra token, or minus one.

6. **[LOW] contracts/lint/fixtures/clean.yaml:21 (and every fixture) - The fixtures are written in a
   different shape from the document the rules judge.**
   The fixtures use internal `$ref`s to `components`. `lintContract` lints the fully inlined bundle. A
   rule's fixture therefore proves the rule on a referenced document, and the rule then judges an inlined
   one. For example, `$..properties[*]` and the `response-schema-closed` selectors see different nodes in
   the two shapes.
   **Recommendation:** run each fixture through the same `vacuum bundle` step before `vacuum report`, or
   author the fixtures inline. The fixtures then exercise the shape the contract is linted in.

7. **[LOW] contracts/lint/ruleset.yaml:44-86, 186-226; contracts/lint/fixtures/clean.yaml;
   contracts/lint/README.md:6 - Parts are transcribed from core, and the README says nothing is copied.**
   This is not a wholesale copy: about a third of the normalized non-comment lines match core's
   ruleset, mostly Spectral boilerplate, and four of the twelve rules are the charter's own
   (`tier-token-spelling` and the three `x-*-shape` rules). Two rules are transcriptions of core's:
   - `error-response-is-problem` has the same selector and schema skeleton as core's
     `non-2xx-is-problem`.
   - `response-schema-closed` has the same id, the same first three `given` selectors and the same
     if/then shape, minus `unevaluatedProperties`.

   The clean fixture's skeleton (`Lint fixture`, `/things`, `listThings`, `Things`) is also core's
   `clean.yaml`. DM-01M57WH7P0QZWHKYGBA2R0MA3K says "not a copy of core's".
   **Recommendation:** re-express those two rules and the fixture skeleton from the charter's own
   contract shape, or add a short provenance note ("convention adopted from core; expressed
   independently") and correct README line 6. The operator decides which.

8. **[LOW] contracts/charter/schemas/ActionContext.yaml:10-13 - `action` and `missionType` are
   computed, but they carry only the inherited type citation.**
   Both come from the path parameters, like `via` and `guidance`. They pass `resource-property-cited`
   only because Urn.yaml's root `x-source` (the `Urn` type, not a producer) is inlined.
   **Recommendation:** give them `x-derived` with `parameters/MissionType.yaml` and
   `parameters/Action.yaml`, using the same description + allOf shape as `via`. Alternatively, record
   in README that a referenced type's citation is enough for a computed property.

9. **[LOW] contracts/build.gradle.kts:95 - The download has no timeout.**
   `URI(...).toURL().openStream()` has no connect or read timeout, so a stalled mirror hangs verifyAll
   instead of failing it.
   **Recommendation:** open a `URLConnection` and set `connectTimeout`/`readTimeout`, so the failure is
   clear.

10. **[NIT] contracts/charter/schemas/PackSummary.yaml:64** - The `open_decision` text still spells the
    code `PACK_LEVEL_MISSING`, though the contract now spells every code snake_case. The WP prompt
    dictated this text; consider `pack_level_missing`.
11. **[NIT] contracts/charter/CHANGELOG.md:12** - "the changes below are relative to that draft", but
    `Added` also lists what the draft already had (`GET /packs`, the context operation,
    `CharterRefusal`). Reword one of the two.

## Coordination notes (not blocking WP01)

- `docs/api/service-contract.md:51` still says the codes "carry over unchanged, for example
  `PACK_LEVEL_MISMATCH`, `PACK_LEVEL_MISSING`". The file is owned by WP05; it must follow the snake_case
  ruling.
- The bundle lands at `build/contracts/contract/charter-openapi.yaml` (WP04 T023 uses that path).
  `settings.gradle.kts` is shared with WP03, as the WP prompt states.

## Anti-pattern checklist

1. Dead code: PASS (`RefusalCode.token`/`Diagnostic.Code.token` used by `RefusalHandler`/`PacksController`).
2. Synthetic fixture: PASS (scenarios and unit tests run the production path; red-first shown above).
3. Silent empty return: PASS. The no-baseline return is documented, but see finding 2.
4. FR coverage: PASS (FR-003: scenarios carry the rulings; FR-004: lint, fixtures, bundle, compat in
   `check`).
5. Frozen surface: PASS (`contracts/_shared/` untouched).
6. Locked decision: PASS on "core's tools are not used". Partial on "not a copy of core's ruleset"
   (finding 7, LOW).
7. Shared-file ownership: PASS (`settings.gradle.kts` noted for WP03).
8. Production fragility: PASS (the new raises are fail-loud by design and documented).

## Verification by the reviewer

- `JAVA_HOME=<JDK 25.0.4.1> ./gradlew verifyAll -PopenPacksDir=<open-packs@7f063ae> --rerun-tasks
  --max-workers=2`: BUILD SUCCESSFUL, 32 tasks executed. 560 tests, 0 skipped, 0 failures, 0 errors
  (adapter-out-yaml 527, charter-service 14 of which 11 scenarios, charter-domain 8, adapter-in-rest 6,
  charter-read-app 5). The :contracts tasks ran: installVacuum, installOasdiff, bundleContract,
  lintContract (12 rules), lintFixtures (12 + clean), checkContractCompatibility ("No released
  baseline"). Second run: configuration cache entry reused, 30 of 32 tasks up to date.
- Planted on the real contract: a dropped `Diagnostic.detail` description fails `property-described`;
  `ACTION_NOT_FOUND` fails `enum-snake-case`. A wrong vacuum SHA-256 fails `installVacuum` with
  "Nothing was extracted" and leaves the tool directory empty. Adding a rule without a fixture
  invalidates the configuration cache and fails `lintFixtures`.
- The generated `GuidanceEntry` is identical before and after the `via` reshape (`String via`).
  `oasdiff breaking source bundle` reports no breaking changes (the bundle only inlines references).
