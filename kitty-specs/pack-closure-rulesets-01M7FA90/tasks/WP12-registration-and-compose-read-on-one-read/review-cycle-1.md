---
affected_files: []
cycle_number: 1
mission_slug: pack-closure-rulesets-01M7FA90
reproduction_command:
reviewed_at: '2026-11-17T16:29:20Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP12
---

# WP12 review, cycle 1: changes requested

Reviewer: `claude::reviewer-renata`, independent. Gates are green, with `:deployable:test` re-run fresh. Confirmed:
- One read per registration.
- `packs.application` names no `packs.infra` type, and ArchUnit is unchanged.
- The golden delta is fully explained: 43 rows, S8 38 and S2 9, with 4 rows in both. The compose read and `validate.json` are unchanged.
- `PackRefusalsTest` pins each bare plant against its twin.
- The fragments and scenarios are accurate, and no mechanical test change weakens an assertion.
- Decisions 3, 4 and 5 are accepted.

Two MAJOR findings block approval. Both have one root cause.

## Must fix

### MAJOR-2: registration still uses the store half's duplicate judging

`AssemblyRules.held` (`:115`) takes every entry of `held.violations()`. That includes what `StoreRead.assembly` (`:378`) computes with the moved `LoadChecks` logic:
- step-enhancement faults;
- `duplicate_step_id`;
- prompt-slot faults;
- `mission_step_unlinked`;
- `substep_delegation_*`.

`content()` already judges all of these, so registration runs two judges. The Definition of Done says nothing in the store judges, and registration judges only through the rules.

**Fix:** `held()` takes an explicit allow-list of store-only kinds:
- the template read twice (row 16);
- the value-object breach (row 17);
- the reference and template closure (row 18);
- `companion` and the fragment read (row 19);
- the named policy file (row 32);
- the facts refusal (the qualified self-loop).

The rule families are left to `content()` alone. `StoreRead.assembly` keeps computing them for the compose read (S7) only.

### MAJOR-1: "stated once" drops distinct faults

`AssemblyRules.java:125` and `:143` (`!files.contains(violation.file())`) and `StorageRulesets.java:113` suppress by file. The reviewer reproduced two losses on both surfaces:
- **Case 1.** An enhancement names `spdd:reasons-block` twice, and its fragment `plan-risk-notes.prompt-fragment.md` is blank. Only the two "named more than once" faults are answered. The blank fragment is reported nowhere.
- **Case 2.** `presets/dup.yaml` has `id: other` (the stem fault) and also lists `DIRECTIVE_001` twice (a value-object breach, row 17). Only the stem fault is answered.

**Fix:**
- After MAJOR-2, suppress a store fault only when the same fault is already stated: the same code and subject, or an explicit mapping from the store fault to the rule fault. Never suppress merely because something sits at the same file.
- In `verdict`, drop the reader's faults at a fragment only when the store's fragment fault is actually emitted.
- Add both cases as plants in `StoreHalfFaultsTest`.

## Fold in this cycle

1. **MINOR-1.** `SurfaceSubsetTest:174`, the reverse subset. Freeze the registration answers from before WP12 (from `surfaces.json` at `59f14afcc`) as a baseline. Assert equality for every row outside NFR-002's list, as the class javadoc promises.
2. **MINOR-2.** `OneReadRegistrationTest`: around `rig.register`, count `PackFileSet.walks()`, `StoreRead.runs()` and `FilesystemPackStore.reads()`, allowing for the one listing that `revisionOf` makes.
3. **MINOR-3.** Row 19: a blank fragment next to an enhancement whose id is not its stem answers three reader faults at the fragment, where every other case answers one fault at the enhancement. Make the answer consistent, ideally through the MAJOR-1 fix. Otherwise record the exception in the S8 fragment and in the R2 note.
4. **MINOR-4.** Record in `held()`'s javadoc that the qualified self-loop is answered only when nothing else is (`AssemblyRules.java:129`). Resolving the pack's own qualifier in `GraphBinding` is optional; if you do it, any wording change needs a cited row.
5. **NIT-1.** Build the `packReading` bean (`StorageConfiguration.java:67`) from the `SerializationStrategy` bean, not hardcoded YAML.

## Carried elsewhere

- **NIT-2**, renaming the "LoadChecks" test classes, goes to WP13, which owns them.
- **`WholePackCensus` on the `TreeCensus`** goes to WP13 (operator, 2026-11-17): extend the census and point the gate at it.
- **`revisionOf`'s listing of the final tree is accepted.** It digests the committed bytes after the strip and the stamp.

## Exit

- `verifyAll -PcontractBaseline=none -PrequireSymlinks=true` and `:pack-validation:wholePackRoundTrip` are green.
- The golden changes only by the listed rows, plus any row the MAJOR fixes change. Explain each one; a row with no cause is a stop.
- Commits are signed, end `(#554)`, and carry no trailer.
