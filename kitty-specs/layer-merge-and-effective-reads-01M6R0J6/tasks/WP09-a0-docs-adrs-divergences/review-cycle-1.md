---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-07T08:48:19Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP09
---

# WP09 review, cycle 1 (reviewer-renata): changes requested

The docs checks pass (`check_docs.py all`, markdownlint-cli2 0.18.1 and cspell 10.4.0 on the 9 changed Markdown files).
All 42 scenario names in the trace's A0 rows exist by exact name on lane-| and none is `@WIP`. DIV-209 to DIV-215 are
unique and the same in the register and `divergences.yaml`. The depth semantics, the refusal codes, the round-trip
gap, `removed_member`/`validation_failed`, the v1 to v2 upgrade, the mission-type path id, B5, the preset kinds, the
eight retries and `mission_type_not_found` all match the code and the `2026-11-07-a0-*` fragments.

Two claims describe a planned read rule as built, and the hand-over list is missing an item. All three are small text
fixes.

**Issue 1 (planned rule described as built): ADR-0050, the amendment of 2026-11-07, the "cascade source (Q1)" bullet
(line ~100).** "a step, and any artifact bound to the mission type, is in force only while the mission type is"
appears under "Decision 6 (activation) is **built**". That is the binding rule E6, which the rules page marks
"Ruled, not built". The trace also lists US3 AS4 ("A mission type's steps are in force only while it is") as planned
for PR B, and the effective reads still answer `501`. Fix: remove the clause from the built bullet, or mark it as the
reads' rule, planned for PR B (E6, FR-022/US3 AS4).

**Issue 2 (planned rule described as built): `docs/architecture/composition-and-activation-rules.md`, C31 status
(line ~308).** "the floor restricts to held ids and reports the rest (A10; built, PR A0)". Only the restriction is
built. The unheld items are computed internally (`RequiredFloor.unheld()`), but no client sees them until PR B's
`requiredNotHeld`. The `@WIP` scenario "A10 - the unheld must-have item is in requiredNotHeld" is still open, and the
trace marks "A10 (report)" as planned for PR B. Fix: for example, "the floor restricts to held ids (A10; built, PR A0);
the report of the rest (`requiredNotHeld`) and the union (A1) are ruled, planned (PR B)". Update the C31 row of the
status table to match.

**Issue 3 (required item missing): the changelog's "Hand-over to core" list leaves out the cascade depth.** The list
is the text the PR description quotes. The bullet above it and the register's hand-over paragraph mention `depth`, but
the five numbered items do not. Add an item, for example: "`SelectionRequest` and `ExclusionRequest` take an optional
`depth` (counting `suggests` hops; absent is unlimited, as core), and `SkippedReport` gains `skippedByDepth`. An older
server refuses `depth` with `422 unknown_member`" (DIV-215).

## Nits (fix while you are in the files)

- ADR-0059 amendment of 2026-11-07: the preamble resolves the label clash. Inside the list, though, the bullet headings
  "**A4.**" and "**B1.**" use the ruleset's labels while "It refines A3 above" and "It refines A5 above" use this ADR's
  labels. Write "**Ruleset A4.**" and "**Ruleset B1.**" (as the D4 note already does) so a reader who skips the
  preamble cannot misread them.
- ADR-0020 amendment item 5 and the ADR-0059 A7 bullet say `activatedKinds` and `missionTypes` are refused "on any
  write". Only `createActivation` and `replaceActivation` carry the document and answer `removed_member`. The same
  member on a per-artifact body is an ordinary `unknown_member` (`activation_invalid`). Say "on a create or replace",
  as the contract fragment does.
- The commit message ends with `Co-Authored-By` but has no `Claude-Session` line. Add it on the next commit.
