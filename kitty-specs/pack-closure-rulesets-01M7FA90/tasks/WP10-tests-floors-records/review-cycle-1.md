---
affected_files: []
cycle_number: 1
mission_slug: pack-closure-rulesets-01M7FA90
reproduction_command:
reviewed_at: '2026-11-18T00:36:16Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP10
---

# WP10 review feedback, cycle 1: REJECT

Verified and passing:

- PIT claims match `mutations.xml`. No floor is lowered, and the shard filters do not overlap.
- Goldens are byte-unchanged.
- `Findings.at` and the `asPackNotFound` deletion preserve behaviour.
- The WP04, WP09 and WP13 carried items are closed.
- The ADR-0075 and ADR-0065 amendments are accurate.
- Commits are signed, with no trailers.

## Issue 1 (MAJOR): `StorageRulesets.verdict:108` is not an equivalent mutant; the trailing-order test cannot see order

`FileSystemPackInspectorTest.theManifestsReadFaultAndTheNamedPolicysFaultTrailOnceEachTheManifestFirst` (:1761) asserts through the helper `verdict(PackRoot)` (:249-252). That helper sorts with `Precedence.comparator`, so "manifest first" holds only alphabetically: `pack-manifest.yaml` sorts before `policy.yaml`.

Remove `policy.ifPresent(trailing::add)` and read the unsorted `StorageRulesets.verdict(full(dir)).details()` on that same fixture. The order reverses: `policy.yaml` comes before `pack-manifest.yaml`. The unmutated code puts the manifest first, and the registration keeps this order on the wire.

**Fix:** assert the order without sorting, either on `StorageRulesets.verdict(full(dir)).details()` or on the registration's refusal violations. Then correct the records:

- the R2 note's "Outcomes of WP10": `docs/development/notes/pack-code-unification.md:206` and `:221-224`;
- ADR-0062's amendment, `:388-393`;
- the survivor count and score. Expect 717 of 732, with `verdict:108` killed.

## Issue 2 (MINOR): record the `StoreRead.namedPolicyFaults` filter survivor (line 307) as equivalent

With the filter forced to true, the extra store-only faults it admits are the manifest's. The reader states each of them in the same words:

- parse;
- schema;
- not UTF-8;
- misplaced preset entries, via `FileSystemPackInspector.misplacedPresetEntries`.

`AssemblyRules.sameFault` then removes the duplicate. Probes over eight manifest and policy fixtures showed no difference.

**Fix:** record it in the note as equivalent, with this reason, instead of "not proven equivalent".

## Issue 3 (MINOR): two enumeration checks compare hand-kept lists with themselves

The checks are `PackCodesCoverageTest.theEnumerationIsEveryCode` (:337-346) and `PlacementCodesCoverageTest.everyCodeHasAFixture` (:57-60). `PackCodes.ALL` is built from `CONTENT` and `CLOSURE`, and `StorageCodes.ALL` from `ORDERED`, so a constant left out of those lists is never checked.

**Fix:** enumerate the `public static final ReasonCode` fields of `PackCodes`, `StorageCodes` and `PlacementCodes` by reflection, and assert they equal the enumerated sets. A new constant missing from `CONTENT` or `ORDERED` then fails NFR-005 and names itself.

## NITs (optional)

- Delete the temp directory created at `PackCodesCoverageTest:298`, or use `@TempDir`.
- Add `doesNotHaveDuplicates()` to the `b.graph.yaml` faults in `FileSystemPackInspectorTest:171-175`.
