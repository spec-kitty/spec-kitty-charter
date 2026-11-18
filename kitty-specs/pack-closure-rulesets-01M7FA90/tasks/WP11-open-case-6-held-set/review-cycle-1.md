---
affected_files: []
cycle_number: 1
mission_slug: pack-closure-rulesets-01M7FA90
reproduction_command:
reviewed_at: '2026-11-16T21:22:58Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP11
---

**Issue 1 (MEDIUM): remove the shim.** Delete the 11-argument `PackContents` constructor (`PackContents.java:92-117`). Pass `List.of()` at the 9 test call sites. If it is cheap, also delete the older 9- and 8-argument constructors (`:133`, `:170`) and repoint `FileSystemPackInspector.java:178`. Otherwise record them as WP05's, since `PackReadout` replaces the record. Delete `ExcludedFile.isUnmodelled()` (`WholePackWriteStore.java:78`); it is test-only and duplicates `kind() == UNMODELLED`. Repoint its tests to `kind()`.

**Issue 2 (MEDIUM): excluded files on both surfaces.** Orchestrator decision: validate mode warns `file_not_held` for EXCLUDED files as well as UNMODELLED ones, with the same detail and reason registration gives. The operator's rule is about holding, not about why a file is not held. data-model's `StorageCodes` row already says "excluded or unmodelled".
- Drop the filter at `PackPolicy.java:216`.
- Let `ExamplePacksTest.everyExamplePackValidatesWithNoFinding` (`:43`) allow `file_not_held` warnings.
- Regenerate the golden. Exactly 2 more validate rows should change: `example-packs/built-in` (`missions/software-dev/templates/README.md`) and `example-packs/org-base` (`docs/authoring-notes.md`).
- Make `HeldSetAgreementTest` assert that the warnings equal *every* not-held entry.
- The orchestrator amends NFR-002's diff list on the mission branch; you do not edit kitty-specs.

**Issue 3 (MEDIUM): stale records.**
- Update `docs/conformance/divergences.yaml` DIV-021 and its register entry: the service no longer validates an in-pack `charter.yaml`. It warns `file_not_held`, so record DIV-021 as resolved by #554 WP11, following the register's own convention for resolved entries.
- Update `scripts/packs/core_verdicts.py` REGISTER_IDS for the plants that no longer exercise their rule: `charter-invalid`, `charter-cli-timeout-not-a-number`, `c2-charter-nested-unknown-key`, `c8-shard-without-generated-by-is-accepted`, `c16-shard-edge-provenance`, `r15-glossary-urn-in-a-shard`.
- Fix those plants' `covers` lists so they no longer claim coverage they do not give. Where a rule loses its only plant, move that plant's shard to the pack root, as the open-packs draft says ("a fixture that needs a graph fault moves its shard to the pack root"). Alternatively, name the unit test that covers the rule.

**Issue 4 (LOW): record the L3 routing.** Add open case 9 to `docs/development/notes/pack-code-unification.md`. It names the mapped-but-untaken files (a `policy.yaml` the descriptor does not name, and an orphan fragment): today the store lists them as UNMODELLED with a false reason, while validate mode judges them. WP05's census decides. The orchestrator adds the matching line to WP05's prompt.

**Issue 5 (LOW): scope and records.**
- State `HeldSetAgreementTest`'s scope in its javadoc: planted and example packs plus the synthetic every-class pack. Scenario packs are built in Cucumber steps, and no feature plants a nested shard, a `charter.yaml` or an excluded file.
- Note in the `ExcludedFile` javadoc that it and `NotHeldFile` coexist until WP12 deletes `ExcludedFile`.
- Coordination: WP03 (approved, lane-c) moved `PackPolicy`'s residue to `packs.domain.rules`. Your `notHeldFindings` in `PackPolicy` will meet it at the lane merge. Keep it in `PackPolicy` (a rule entry point, which WP03 left there), and expect a textual conflict only.

Re-run `:packs:test` with `--rerun`, `:pack-validation:wholePackRoundTrip` and `verifyAll -PcontractBaseline=none`, all through the Gradle lock.
