---
affected_files: []
cycle_number: 2
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command: spec-kitty agent tasks move-task WP26 --to approved --mission layer-merge-and-effective-reads-01M6R0J6 --agent claude:opus:reviewer-renata
reviewed_at: '2026-11-08T21:37:58Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP26
---

Approved by claude:opus:reviewer-renata: approval:WP26

# WP26 review, cycle 2: approved

Reviewer: claude:opus:reviewer-renata (reviewer-renata). The fix commit is `b3d3f470` on `lane-x`, above the merge
`ad0ce247`. The diff changes docs and one contract fragment only.

## Verdict

**Approved.**

- Both required fixes from cycle 1 (F1 and F2) are correct against the code, and so are the optional F3 and F5.
- The commit also fixed several PR A rows. Its claims match the code, but it leaves two rules whose prose and table
  disagree, and G1 is stale.
- All three belong to PR A and fall under closeout item 2 from cycle 1, which I ruled non-blocking. They are listed
  below for the runner.

## Cycle 1 findings, re-checked

- **F1, fixed.**
  - **W33** is now Partly built (PR B), in the prose and the table. This matches `ActivationChange.Code`, which has
    only `ENHANCED_TARGET_NOT_IN_FORCE` and `OVERRIDDEN`, and `warningOf`, which returns empty for any other reason.
    The rule no longer gives `mission_type_not_in_force` as an example, and the gap is stated.
  - **G20** is now Partly built, in the prose and the table. This matches `RegistrationImpact.Notices`: the floor and
    lock lists are PR A, `activationsAffected` is PR B, and there is no `Unheld` list.
- **F2, fixed.** `rest-resource-conventions.md:134-139` now names the four built conditional reads and says "Built:"
  for the preconditions. `If-None-Match` on PUT is declared at `activations_activationId.yaml:56-60` and pinned by the
  `activation-register` feature files.
- **F3, fixed.** The changelog line moved from Breaking to the "Writes" bullet and cites the new fragment.
- **F5, fixed.** The blank line is gone from `divergences.yaml`.

## The new fragment, checked against the 500 declarations

The fragment is `contracts/charter/changes/2026-11-08-b-write-cascade-merged-edges.md`. It is accurate.

- **Which writes build the merge.** Only `ActivationWrites` does:
  - the cascade reach goes over `ClosureView.merged()`;
  - `warned()` calls `session.inForce(...)` before anything is stored.

  Those are the writes behind the select, unselect, deactivate, reactivate and preset operations, and every one of
  them declares `500: ../responses/OfferingUnreadable.yaml`.
- **PUT and DELETE.**
  - `replaceActivation` (PUT) declares `OfferingUnreadable` and does not cascade or call the in-force resolution.
  - `deleteActivation` declares a plain `Problem` 500. It reads only a closure snapshot for an either-tag `If-Match`
    (`IfMatchTags.closureTag`), and a refusal there yields no tag, which leads to a `412`, not a `500`.

  Neither builds the merge, so the fragment is right to name the sub-resource writes only.
- **The codes.** `OfferingUnreadable` names `pack_sibling_conflict`, `pack_unreadable`, `stale_input` and
  `internal_error`, as the fragment says.
- **The self-override code.** The fragment's list adds a `self_override` edge to the `pack_unreadable` reasons. That
  matches `EdgeIndex.merged()` → `refuseSelfOverrides()` (`EdgeIndex.java:173, 204-222`).

## PR A rows the commit also changed, checked against the code

- **C36** is Built: the registration codes `enhances_cycle`, `conflicting_relations` and `self_override` are PR A,
  and the refusal at `merged()` as `500 pack_unreadable` is PR B (`EdgeIndex.refuseSelfOverrides`). Correct.
- **G8** is Partly built (PR A). `RegistrationImpact` composes and judges `ImpactRelations` over the closures the
  pack joins and reads the floor and locks only for the notices. It runs no `LayerMerge` and refuses no floor change.
  Correct.
- **G2** (whole-pack replace) is Built (PR A). Correct.
- **Section 3 intro** now says built. Correct.

## Closeout items for the runner (not blocking)

1. **G3** is inconsistent. The table row (`composition-and-activation-rules.md:1019`) says "Built (PR A)", but the
   prose (`:377`) still says "**Ruled, not built** (layer-merge PR B)". The rule is built (DIV-602), so fix the prose.
2. **G7** is inconsistent the other way. The prose (`:399`) now says "Built (PR A)", but the table row (`:1023`) still
   says "Planned, not built". It is built (DIV-603), so fix the table row.
3. **G1** is stale. It says "Ruled, not built" (`:363`, and its table row), but `PackRegistrationService:295-297`
   refuses an upload at `PackLevel.BUILT_IN` with `registration_conflict`. Rows G4–G6 and G9–G19 should be checked in
   the same pass.
4. **`OfferingUnreadable` reasons.** The shared response's list of `pack_unreadable` reasons does not name the merge
   refusals: an override the level forbids, an override cycle, an unstable answer and `self_override`. The
   effective-read path texts and the new fragment do name them. This is contract wording, for the runner or PR C.
5. **Still open from cycle 1:**
   - the planning-document corrections (data-model.md lines 224-226, plan.md IC-07, R5 and the EffectiveTag text);
   - WP29 rebases on WP26.

## Anti-pattern checklist

- Items 1–4, 6 and 8: N/A (docs and a changelog fragment only).
- Item 5, frozen surface: PASS.
- Item 7, shared-file ownership: PASS. The `out-of-map:` lines are present in `b3d3f470`.

## Commands

- `python3 scripts/docs/check_docs.py all`: all checks ok, exit 0.
- `npx --yes markdownlint-cli2@0.18.1 "docs/**/*.md"`: 294 files, 0 errors.
- `npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"`: 294 files, 0 issues.
- `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=/home/user/open-packs :contracts:check :packs:test --tests '*DivergenceRegisterTest*'`:
  BUILD SUCCESSFUL. It ran `checkContractChangelogFragments`, `checkContractCompatibility`, `verifyVacuum` and
  `verifyOasdiff`; `DivergenceRegisterTest` passed 3 of 3 with 0 failures.
