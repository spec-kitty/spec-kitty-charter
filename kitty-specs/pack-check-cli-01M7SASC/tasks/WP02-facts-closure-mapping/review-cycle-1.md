---
affected_files: []
cycle_number: 1
mission_slug: pack-check-cli-01M7SASC
reproduction_command:
reviewed_at: '2026-11-19T12:12:00Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP02
---

# WP02 review: changes requested (reviewer-renata)

Range reviewed: `e6069ec7a..e630bdcf6` (commits 8789c7ad4, 8e0abf6bf, e630bdcf6), excluding kitty-specs.

What holds: there is one derivation. `readoutFacts` and `underResolution` both go through the private `built`, and
`read()` takes the store half from the same `OneRead`. A planted second read (`readWithStore(root).store()` in
`read()`) is caught by `theFactsCostNoSecondRead` (killed). `closure(presetIdsOf)` is right, and its control is real:
a planted `return closure();` fails 3 of the 4 closure tests. The catch is `CharterRefusal` only, not
`RuntimeException`. `PackRefusalMapping` is a verbatim move, and `DOMAIN_IS_PURE` is green. `:packs:test` and
`:pack-validation:test` are green (6819 and 413 tests). The commits are signed and carry no trailer.

## Findings

### F1 (MEDIUM): `Unreadable` carrying the refusal's own code is untested, as is the `Assembly.None` branch (S10)

- **Where:** `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilder.java:105`
  (`new PackReadout.Unreadable(refusal.code(), …)`) and `:98-101` (the `Assembly.None` branch);
  `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ReadoutFactsTest.java:83-95`.
- **Evidence:** a planted mutant that replaces `refusal.code()` with `RefusalCode.PACK_UNREADABLE` **survives the
  whole `:packs:test` and `:pack-validation:test` run** (BUILD SUCCESSFUL). Every `Unreadable` the tests reach carries
  `pack_unreadable`. The test named "a file the load refuses: Unreadable with the load's code" asserts
  `PACK_UNREADABLE`. The "no pack.yaml" test goes through `Assembly.Refused`, not `Assembly.None`, since
  `FileSystemPackInspector` never yields `None`, so the `None` branch has no test either.
- **Failure scenario:** squad S10 (accepted) says `Unreadable` carries **any** `CharterRefusal` code, and that each
  branch gets a test. D3 and FR-007 report the refusal as the pack's problem. A regression that collapses
  `validation_failed`, `drg_schema_error` or any other load code into `pack_unreadable` would make pack-check report
  the wrong code, breaking parity with registration (NFR-003), and no test would fail.
- **Fix:** add a test whose `Unreadable.code()` is not `PACK_UNREADABLE`. For example, unit-test the package-private
  `readoutFacts` with a `StoreRead` whose assembly is `Assembly.Refused(…, RefusalCode.VALIDATION_FAILED, msg, …)`
  (`StoreRead.breached` builds one), or use a real pack the load refuses with another code. Assert the code and the
  detail. Add a unit test of the `Assembly.None` branch with a `StoreRead` built on `new Assembly.None()`. Rename or
  reword the "with the load's code" test so its name says what it asserts.

### F2 (MEDIUM): `PlantedFixtures` gives real reads a fabricated `Unreadable`

- **Where:** `service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PlantedFixtures.java:94-96, 114-121`
  (`NOT_COMPOSED`).
- **Problem:** the planted and example subjects are real packs that the one reader reads. Example packs read as
  `Built`, as `ReadoutFactsTest` proves. The fixture labels every one of them
  `Unreadable(PACK_UNREADABLE, "a planted subject's composition facts are not composed")`. The one reader never
  produces that state or that detail. AGENTS.md (operator, 2026-11-10) says tests use only the canonical shape,
  except tests of error handling. T009 asks for "a `Built` from a canonical example pack's facts, or a documented
  `Unreadable`", and a documented falsehood does not qualify. `Readouts`, `Placements` and `PackCheckTest` differ:
  their hand-written contents carry `Assembly.None`, and `Unreadable(pack_unreadable, "…has no assembly")` is exactly
  what `readoutFacts` answers for that input, so those three are canonical.
- **Failure scenario:** WP03 checks pack-check "for a valid example pack and for a planted subject". Any test that
  builds pack-check input from `Planted.readout()` would see every subject as unreadable. CLOSURE would be skipped for
  all of them (D3), and the CLOSURE parity on planted and example subjects would pass vacuously.
- **Fix:** take the readout from the one reader rather than assembling it. For example, have `TreeReads` (or a sibling
  helper in `infra.storage`) return `new FileSystemPackInspector().read(root)`, or expose `OneRead`, so
  `Planted.readout()` carries the real `facts`. A subject whose tree is not on disk as read (`+pack.yaml`) should get
  the facts the reader gives for the tree it actually read. Delete `NOT_COMPOSED`.

## Notes below MEDIUM (folded, no action required)

- The detail `"the pack X cannot be read: it has no assembly"` is spelled out in four places:
  `CompositionFactsBuilder`, `Readouts.noAssembly`, `Placements` and `PackCheckTest`. Consider one test-fixture
  constant.
- For a root that is not a directory, the facts' detail names the directory (`pack absent is not a directory`) while
  the verdict names the configured pack. This is pre-existing `StoreRead` behaviour, and the test pins it. WP03 should
  report the verdict's root fault, not both.
- `PackRefusals` delegating `of`/`diagnostics` is one implementation behind a package-private façade. T008 allows it
  ("choose the smaller diff"), and it is not a second reader.
- `underResolution` keeps its own `StoreRead.of`. T006 step 3 sanctions this, and its only callers are now tests.
  WP03 must use `PackReadout.facts`, never `underResolution`.
- Mutation: targeted PIT (pit-targets.init.gradle) on PackRefusalMapping, PackRulesets and PackReadout*: 11 mutants, 10 killed; the 1 survivor is pre-existing `PackRulesets.content` (null return), outside the targeted tests. Hand mutants: M1 (code hardcoded) SURVIVED the full suites, M2 (no preset phase) KILLED, M3 (second read) KILLED.
- `ReachabilityBudgetTest` fails identically on the base `e6069ec7a`: `IllegalArgumentException: a scope edge may not
  connect tactic to tactic` from its fixture (`EndpointRule`). The test is tagged `timing`, runs only in
  `:packs:timingBudgets` (nightly, report-only, `timing-nightly.yml`) and is not in `verifyAll`. It is not caused by
  WP02.
