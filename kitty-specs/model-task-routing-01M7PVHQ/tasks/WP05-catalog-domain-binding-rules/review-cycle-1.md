---
affected_files: []
cycle_number: 1
mission_slug: model-task-routing-01M7PVHQ
reproduction_command:
reviewed_at: '2026-11-18T14:07:35Z'
reviewer_agent: claude-reviewer
wp_id: WP05
---

# WP05 review — cycle 1: changes requested

Reviewer: independent reviewer (session_019C2Tk2EBfnVS3Cj2pGMmxF). Reviewed at lane-e 8f07ca802; WP diff 3f3b8ea39..8f07ca802.
Verified here: `:routing:check` passes; `:packs:test` runs 6735 tests with 4 failures, all `ExamplePackCoverageTest`, as you reported.

What is good and stays: the routing domain records, the content rules (all four faults, at the member's place, with
the positive control), the binding and its byte-for-byte write-back, the read-service registrations, the
EntrypointReachesRouting bite, KindRefTest, and the parity-test updates (none excludes a field). Deviation (1), dropping
routing_catalog from the contract NodeKind as guard and lifecycle are, is accepted. Deviation (4), treating absent and
[] alike, is accepted.

## Must fix

1. **`generated_at` must stay an ISO-8601 date-time (deviation 2 rejected).** ADR-0080 decision 5 and data-model.md
   type it as a date-time. The schema's `format: date-time` was replaced by `minLength: 1`, and nothing else checks it:
   `RoutingCatalog` and `Checks.text` accept any non-blank text. The fixture that needs the loosening is
   `GeneratedPackTree`'s `generated_at: G`, so the model was changed to fit a test. The charter companion forbids
   that: tests use the canonical shape, and the model is canonical. The graph shard's free-text `generated_at`
   (`STATIC`) is no precedent here: ADR-0080 types this field, and the WP's own fixtures (`mu`, `default`) already
   write a real date-time.
   - Fix: restore `format: date-time` and add its wording row to `SchemaKeywordCoverageTest`; or, if the schema
     validator does not assert `format`, check it in the domain (`Checks`: an ISO-8601 offset date-time). Do one of
     these so a non-date-time is refused.
   - Give `GeneratedPackTree` (and any other fixture) a real date-time, for example `2026-11-18T00:00:00Z`.
   - Add an invalid-content test: a catalog whose `generated_at` is not a date-time is refused.

2. **Numbers must not pass through `double` (deviation 3 rejected).** Research R3 ("parsed from the YAML text without
   a float step"), NFR-002, ADR-0080 decision 10 ("Arithmetic is exact decimal") and T017 ("with no double step") all
   rule this out. `RoutingCatalogBinding.decimal` falls to `BigDecimal.valueOf(number.doubleValue())` for a YAML
   float, and `number(BigDecimal)` writes a non-integral decimal back as a float. That is lossy past about 15–17
   significant digits. Two distinct authored scores can then collapse to one value and tie (decision 11), and the file
   no longer round-trips byte for byte, with nothing refusing such a value.
   - The shared loader (`Yaml.java`, snakeyaml-engine with `JsonScalarResolver`) constructs a float tag as `Double`.
     A constructor for the float tag that builds a `BigDecimal` from the scalar text fixes this at the source.
   - Fix: have the loader resolve YAML floats as `BigDecimal` for this format (or read the scalar's text), and emit a
     `BigDecimal` back in plain notation. `0.66` must stay `0.66` with no `double` in between.
   - Add a binding test with a score of more than 17 significant digits that reads back and writes back unchanged.
   - If that cannot be done within the shared loader, stop and ask the operator for a ruling recorded on #108.
     Bounding the precision is the alternative, a content rule refusing more than 15 significant digits. It must not
     be left silent.

3. **The DoD's full `verifyAll` is not met.** `:packs:check` has 4 failures, all `ExamplePackCoverageTest`. T019 makes
   WP05 the first WP whose DoD is the full suite ("Every parity test that was red since WP02 must be green here").
   I accept that the example packs are WP06's, and that a coverage test over every schema property cannot pass
   without example content. So this item asks for no example pack here. It asks for one of:
   - a recorded operator or orchestrator ruling that the green point moves from WP05 to WP06, with WP06's DoD naming
     these 4 tests; or
   - the minimal example content landed in WP05 as a recorded edit outside the map.
   Decide before resubmitting, and name it in the history. If items 1 and 2 are fixed and the ruling is recorded, this
   item does not block on its own.

## Notes (not blocking)

- `RoutingCatalogYaml.optionalNumber` maps a present non-number to empty, so `number()` throws `IllegalStateException`
  only for a required key. The schema refuses non-numbers first, so this is defensive only. Fine as it is.
