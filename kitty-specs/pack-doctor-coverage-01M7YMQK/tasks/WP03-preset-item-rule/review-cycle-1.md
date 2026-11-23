---
affected_files: []
cycle_number: 1
mission_slug: pack-doctor-coverage-01M7YMQK
reproduction_command:
reviewed_at: '2026-11-22T06:16:12Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP03
---

# WP03 review, cycle 1 (reviewer-renata): CHANGES REQUESTED

Range 5aa0ce786740..ed43c652d252 (lane-c). Gates re-run fresh with `--rerun-tasks`: BUILD SUCCESSFUL, 11,517 tests,
0 failures, 0 errors, 7 skipped. The rule, the codes, the contract, the fixtures and the WP11 merge resolution are sound.
One test gap must be closed. One model question goes to the operator; the implementation already follows the
recommended option.

## Must fix

**Issue 1 [MEDIUM]: no test shows a write to a dependency refused for a dependent's preset.**
Location: `service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureImpactCheckTest.java` (after line 877).

FR-003 and R-a say "every registration and write". `walkFaults` (ClosureImpactCheck.java:463-496) judges only the
declaring pack of each single-root closure. Dependents are covered because `ClosureRoots` gives every dependent its own
single-root closure, so the design is correct. But every new test judges only the written pack:
- `aMustHaveTheOwnClosureDoesNotHoldIsRefusedAtTheItemAndTheHeldControlRegisters`
- `aMustHaveHeldByADependencyIsHeldAndTheDependentRegisters`
- `anItemNoClosurePackDeclaresIsRefusedUndeclared`

If someone later changes the `declaring` filter or the `roots.ids().size() != 1` guard, the case where a dependent's
preset loses its holding would stop being refused, and no test would fail.

Fix: add a test. Register `base`, which holds `directive:X_ONE`. Register `top` (`depends_on: base`), whose preset has
`must_have X_ONE`. Then do one of these to `base`:
- re-register it without X_ONE; or
- (`DELETE`/per-artifact path) remove X_ONE through `ArtifactRemovalService`.

Assert all of the following:
- `preset_item_not_held`;
- the violation pointer `presets/team.yaml#/activated_directives/0`;
- the detail names `top`'s preset and its own closure `` (`top`, `base`) ``;
- nothing is stored.

`FallbackProfileImpactTest.aWriteToThePackHoldingTheFallbackIsJudgedForThePackThatDeclaresIt` is the model to follow.

## Low (fold into this cycle, no issue)

- **[LOW] Naming:**
  - `PackCompositionResolver.java` (`PackUnderResolution.definedArtifacts`): the component keeps the retired word
    "defined" and now carries `referable()`.
  - `PackRegistrationService.java:582-596`: `Before.held` and `heldBy(...)` hold `referable()` (which includes action
    steps) under the name "held", the very word D3 now reserves for artifact files.
  - Fix: rename to `referableArtifacts` / `referable` once the operator settles the question below (option a). Also
    rename the test `DomainRepositoryServiceTest.definedArtifactsNameEveryKindThePackHolds`.
- **[LOW] Map keys:** `ClosureImpactCheck.java:283`: `composed` is keyed by `judgedRoots.get()`, but `changes` is keyed
  by `roots`. The two agree only because `judgeWrite` passes `Optional::of`; under a non-identity `judgedAs` (the
  removal path), `walkFaults` would silently find no composed closure. Fix: key both by `roots`.
- **[LOW] Composing-read test:** `OfferingReadServiceTest.aComposingReadNeverRunsThePresetItemRule` asserts that the
  read composes, but not the `requiredNotHeld` report that T014 asks for. That report is asserted by the three
  store-seeded read scenarios:
  - `activation-ruling-changes.feature:513`;
  - `effective-reads.feature:1577` and `:2074`.

  Either cite those scenarios in the test's javadoc, or assert the floor's unheld item there.
- **[LOW] Out-of-map edits:** list them by name in the review notes. They are each justified:
  - `RefusalHandler`, `ClosureFault` (new `of` overload with violations), `Problems`;
  - `directives.feature`, `errors.feature`;
  - `ReservedRefusalCodesTest`, `PackDoctorReportContractTest`, `PackCodesTest`, `PackCodesCoverageTest`;
  - `PerArtifactImpactTest`, `PresetRoundTripTest` and the presets-pack fixture;
  - `facts/example-packs.json`.
- **[NIT]** `service/packs/src/test/resources/facts/example-packs.json` lost its trailing newline.

## Operator question (non-blocking; the implementation follows the recommendation)

Plan D3 said "every reader of `defined` moves to the one it means". One reader means neither field: the compose, i.e.
`holds()`, the reference resolution, the case-only check, and the uninstall and registration diffs. It resolves
references against artifact files *plus* mission-type step nodes (`action:M/s`). A step is held by a different mechanism
than a file: it is a value of its mission-type file (StepHolding, A7 Q1, data-model "a mission type step is a value of
its type's file"). With `held` = files and `declared` = every node, neither field gives that set. Feeding `declared` to
the compose would make `directive:GHOST` resolve. The implementer added a derived accessor,
`PackCompositionFacts.referable()` = held + declared action nodes, which equals the old `defined` for every valid pack.

**Question R-?: what does a reference resolve against, now that `defined` is split?**

- **(a) Recommended:** a derived view, `referable()` (held files plus the mission-type steps the graphs declare). It is a
  view, not a stored field. Rename `PackUnderResolution.definedArtifacts` to `referableArtifacts` so no "defined"
  vocabulary remains.
- **(b)** Steps are held: `held` = files plus the declared steps of a held mission type; the compose reads `held`; no
  third set. This changes R-l's "held as an artifact file" wording for `held` and the T011 assertion. It has no effect
  on presets, since an action is never a preset item.
- **(c)** The compose reads `declared`: any graph node resolves a reference. This reverses pinned behaviour
  (`directive:GHOST`, `tactic:declared-only` would resolve). Not recommended.

## Operator ruling R-v (2026-11-22, DM-01M80YAS1QJABJ74REAXWNNZF9)

"Derived referable() (Recommended)": references resolve against the derived `referable()` view (held files plus held
mission types' step nodes); no stored third field. So the LOW naming finding is now required in this cycle:
rename `PackUnderResolution.definedArtifacts` -> `referableArtifacts`, `PackRegistrationService` `Before.held`/`heldBy`
(which carry `referable()`) -> `referable`/`referableBy`, and `DomainRepositoryServiceTest.definedArtifactsNameEveryKindThePackHolds`
to a referable/held name; no "defined" left for this concept. Update data-model.md's note if it names the old word
(coord worktree; tell the orchestrator instead of editing kitty-specs from the lane).
