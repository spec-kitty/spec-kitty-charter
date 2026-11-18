---
affected_files: []
cycle_number: 2
mission_slug: pack-closure-rulesets-01M7FA90
reproduction_command:
reviewed_at: '2026-11-17T17:45:50Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP12
---

# WP12 review, cycle 2: changes requested

Reviewer: `claude::reviewer-renata`, independent. Gates are green and fresh, including `--rerun` of `:packs:test` and
`:pack-validation:test`.

The following are verified:

- Both cycle-1 MAJOR cases are fixed.
- MAJOR-2 is fixed: `held()` reads only `storeOnly`, and `storeOnly` holds only the allowed kinds.
- The goldens are unchanged since cycle 1.
- The frozen baseline equals the registration half at `59f14afcc` (108 of 108).
- The walk count is correct.
- There are no regressions.

One MAJOR remains.

## Must fix

1. **MAJOR-A: the mission-type mapping hides a store-only fault.**
   - **Where:** `AssemblyRules.java:174` and `:187-198` (`statesMissionTypeRefusal`). It drops the store's fault whenever the reader states any `artifact_schema_error` at that file starting `step '` or `names `. It checks neither the store fault's kind nor its subject.
   - **Reproduced:** `/tmp/claude-0/-home-user-spec-kitty-charter/76693da7-baf1-5654-a397-b4dee7c54cf7/scratchpad/probe2/src/Probe2.java`, on `missions/software-dev/software-dev.mission-type.yaml`.
     - Delete `templates/research-template.md`. The reader answers `names templates/research-template.md (named by the type): missing`.
     - Rename the `evidence.gap-analysis` key to `evidence.quickstart`. This is a row 17 breach: "An artifact key stands once in a mission type's expected outputs".
     - With both changes together, the breach is reported nowhere. `PackAssembly.collect` runs `toEntity` before `firstUnresolved`, so the store's fault is the breach, which is a distinct fault.
   - **Fix:**
     - Apply the mapping only to the store faults it describes: a step listed twice, or an output template or companion that does not resolve.
     - Match the subject too: the step id, or the path after `names`.
     - Never match a `Constraint breached` fault.
     - Add the combined case as a plant in `StoreHalfFaultsTest`.
     - Also check the variant with a repeated step plus the same breach. If the breach is lost only because the store leaves the type out of the assembly, record that cascade in the R2 note. If the mapping drops it, fix it the same way.

## Fold in this cycle

2. **MINOR-A:** the companion branch of `sameFault` (`AssemblyRules.java:169`) matches on file alone. Also require the reader's code (`companion` or `artifact_schema_error`), as the javadoc says.
3. **NIT-A:** rewrap the R2 note paragraph near lines 210-211 to the wrap width.
4. **NIT-B:** in `StoreRead.metOnlyByTheStore` (`StoreRead.java:320`), replace the message-prefix test (`"Constraint breached"`, `"template "`) with a typed marker if the change is small. If it is not, note it for WP13.

## Exit

- `:packs:test`, `:pack-validation:test` and `:deployable:test` are green.
- `verifyAll -PcontractBaseline=none -PrequireSymlinks=true` is green.
- The goldens are unchanged, or each changed row is explained.
- Commits are signed, end `(#554)`, and carry no trailer.
