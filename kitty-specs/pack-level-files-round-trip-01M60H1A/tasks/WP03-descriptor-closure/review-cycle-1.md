---
affected_files: []
cycle_number: 1
mission_slug: pack-level-files-round-trip-01M60H1A
reproduction_command:
reviewed_at: '2026-10-28T07:51:40Z'
reviewer_agent: claude
wp_id: WP03
---

# WP03 review 1 (reviewer-renata, independent delegate): changes requested

1. **[MEDIUM] Remove `Diagnostic.Code.PACK_UNKNOWN_KEY`** and its token assertion, as T008 and acceptance check 8
   require. No parity test pairs the Java enum with the contract.
2. **[MEDIUM] Ordering test.** `FileSystemPackInspectorTest`'s ordering test no longer exercises the TimSort
   regression: every place is the root. Drop `isSorted()`, rename the test, and name `PackPolicyOrderTest` as the
   comparator's guard.
3. **[MEDIUM] Descriptor docs.** `docs/architecture/descriptors.md:88-96` still describes tolerance and "Planned"
   closure. Update it to the built behaviour, as a recorded out-of-map edit.

LOW findings, which the author takes:

- a clean control in `CharterWiringTest`;
- the `PackWriteStore` wording.

Deferred to a follow-up:

- renaming the planted and load-fixture directories, which needs a core-verdict regeneration;
- the "Was:" wording on the retired register entries.
