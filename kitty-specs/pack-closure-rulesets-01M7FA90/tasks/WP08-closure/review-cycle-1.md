---
affected_files: []
cycle_number: 1
mission_slug: pack-closure-rulesets-01M7FA90
reproduction_command:
reviewed_at: '2026-11-17T08:14:31Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP08
---

# WP08 review, cycle 1: changes requested

Reviewer: `claude::reviewer-renata` (independent). `:packs:test` is green, and both golden files are byte-equal.

First-fault parity holds against the old code. The reviewer checked it with a differential fuzz: the old resolver against the new over 300,000 random sets, with zero differences in `resolve`, in the resolved order, and in the first preset contest. All 23 moved tests map to covering cases. The new code is pure, and the offering and reference checks did not regress.

## Must fix

1. **M1. Overlapping cycles are hidden.** `PackCompositionResolver.java:1158-1185` (`cyclesOf`) follows only the first unplaced dependency.
   - Example: A depends on B and C; B and C each depend on A. `faults` returns one CYCLE, `a -> b -> a`, and nothing names `c`. FR-003 and the DoD ("every composition fault is collected") require it to be named.
   - Keep the first walk as it is (parity). Then make sure every leftover pack inside a strongly connected component of size two or more is named by at least one CYCLE fault, for example the shortest cycle through it, found by a BFS over the leftover edges.
   - Do not report a pack that is only downstream of a cycle.
   - Add the A/B/C test: two CYCLE faults, with the first one unchanged.
   - Correct the R2 note (`pack-code-unification.md:189`, "each reported once").

## Fold in this cycle

2. **m1. Twins report one cause several times.** `siblingContests(ordered)` (`:344`) runs over the twin `pack_id` holders. Pass `distinct` so a twin is read as its first holder, as the javadoc and the note already say. The first fault stays DUPLICATE_ID.
3. **m2. The preset rule depends on input order.** `CompositionRules.presets` should order the packs itself: the resolver's merge order when the set resolves, otherwise `STABLE_ORDER`. Use a small resolver accessor that returns `Composition.order()` followed by the unplaced packs in stable order. `PackCompositionResolver.presetContests(ordered, …)` keeps taking the order it is given, for the offering. The golden must stay byte-equal.
4. **n1.** `docs/architecture/composition-and-activation-rules.md:198` and `:216` still cite `cycleOf` and `indexByName`. Rename them.
5. **n2.** Add a passing `Presets` case for a direct parent and child holding one preset id.

## Exit

- `verifyAll -PcontractBaseline=none` and `:pack-validation:wholePackRoundTrip` are green.
- `golden/composition.json` and `surfaces.json` are byte-equal.
- Report the PIT numbers on the changed code.
- Commits are signed, end `(#554)`, and carry no trailer.
