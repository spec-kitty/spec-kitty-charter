---
affected_files: []
cycle_number: 1
mission_slug: pack-level-files-round-trip-01M60H1A
reproduction_command:
reviewed_at: '2026-10-28T07:41:33Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review 1 (reviewer-renata, independent delegate): changes requested

1. **[HIGH] `uniqueItems` on `activated_kinds` restored.** The WP prompt, the contract and grounding §4 require it,
   and dropping it left the schema and the contract disagreeing with no recorded reason. The NPE that motivated the
   drop is a defect in `PackSchemas.violation`: networknt gives `uniqueItems` null arguments. Fix that with a null
   guard, and declare `uniqueItems` in `SchemaKeywordCoverageTest.FIXED_FALLBACK`.
2. **[HIGH] Duplicate-kind test.** `PackLevelSchemasTest` must assert that a duplicate kind is refused, as
   `uniqueItems` at `activated_kinds`, paired with the clean preset.
3. **[MEDIUM] Pointers on refusals.** `anIdOutsideTheGrammarIsRefused` asserts the keyword (`pattern`, or
   `minLength` for `''`) at pointer `id`. The item-shape, version and manifest refusals also assert their pointers,
   and the unknown-key cases assert `property()`.
4. **[MEDIUM] Validate re-pin.** The built-in validate re-pin also pins the `presets/minimal.yaml` item-level findings
   and the exact total finding count.

The LOW findings are optional: the javadoc wording, the duplicated "preset" constant, a comment reflow, and the key
order of the hash properties.

Correction to the review: WP02 has a duplicate-kind refusal test, `PresetTest.kindGateNamesEachKindOnce`.
