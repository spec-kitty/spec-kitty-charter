# PR C pre-merge squad: synthesis and dispositions (runner, 2026-11-09)

Point-cut: pre-merge, on the rebuilt branch `issue-10-pr-c-graph-context` (a07524cc WP27, d74382fd WP28, c893a7ec WP29).
Question: is the aggregate diff correct and non-fakeable, are the boundaries and the 0.x classification right, does it
collide with the step-enhancements mission, and do the docs tell one story that matches the code?

Cast (distinct lenses, loaded profiles): reviewer-renata (opus), architect-alphonso (opus), curator-carla (sonnet).
Verdicts: GO-WITH-FIXES ×3. Reports: `reviewer-renata.md`, `architect-alphonso.md`, `curator-carla.md`.

Disposition vocabulary (procedure contract): accepted (artifact changed, evidence noted when it lands), changed (resolved
another way), deferred_with_rationale (with where it is tracked). Owner: C = code implementer (`.worktrees/pr-c`),
D = docs implementer (`.worktrees/pr-c-docs`), R = runner.

## Divergence adjudicated

- **A-1 [MAJOR] the IC-13 fragment should be `### Breaking`** (architect; new evidence: the writes answer `500
  pack_unreadable` where they answered `409` when the layer merge refuses). **changed** (R + D). Decision
  `01M70N4GK3PGAEVFPYT3DN1Q9F`: the section stays `### Changed` on the PR B precedent
  (`2026-11-08-b-write-cascade-merged-edges.md`, the same 500-on-merge-refusal for writes filed as `### Changed`; the 500
  of `OfferingUnreadable` is declared on these writes on `main`). The fragment names both changed answers explicitly
  (409 → 200/201; 409 → the declared 500). Surfaced to the operator through the dispatcher; a ruling for `### Breaking`
  only moves the block.

## reviewer-renata

- **R-1 [MINOR] `EffectiveTag` DOMAIN stays `v1`** although PR C changes what the reads answer (OD-6, NodeReference,
  layerOverrides, D16). **accepted** (C): bump to `v2`, add a golden-vector test pinning a literal token.
- **R-2 [MINOR] DIV-204 overstates when the merge is consulted**: per-artifact writes ask `inForce` through `warned()`
  whenever they apply anything. **accepted** (D): limit the sentence to the floor judgement of create/replace and cite
  DIV-230 for the per-artifact writes.
- **R-3 [NIT] two in-force resolutions on a deactivate of a required target.** **accepted** (C): `statesOf` Javadoc
  says "per read request".
- **R-4 (unchecked) an `overrides` edge from a non-activatable kind onto a required directive**: packs would report
  `satisfiedBy`, D16 drops it, write and read could disagree. **accepted as an investigation** (C): establish whether it
  can be authored; if it can, pin the actual behaviour with a test and report it to the runner (no rule change without a
  ruling).

## architect-alphonso

- **A-2 [MINOR] "the mission type of `action:M/s`" parsed in four places** (`InForceResolution.bindingOf`,
  `LayerMerge`, `ClosureViewSession`, `StepHolding.missionTypeOf`), disagreeing on `action:M/`. **accepted** (C): route
  them through `StepHolding.missionTypeOf`, pin `action:M/`.
- **A-3 [MINOR] `ContractDescriptionsTest` pins `.doesNotContain("stay reserved")` over the whole `CharterRefusalCode`
  description**, which phase 1 (#437 WP04) will trip. **accepted** (C): narrow the pin to the removed sentence.
- **A-4 [MINOR] textual conflicts with phase 1 in `CharterRefusalCode.yaml` (main :30-35, :56-60).** **accepted** (C):
  revert any PR C rewrap there that is not part of the IC-13 text; R names the remaining spots in the merge notice.
- **A-5 [MINOR] no client round trip of a `NodeReference` step target.** **accepted** (C): `client-java` round trip with
  `{kind: action, id: M/s}`.
- **A-6 [NIT] `requireAction` runs the walk and discards it.** **accepted** (C): expose the walk's name check; reflow the
  comment (also WP28 cycle-2 NIT).
- **A-7 [NIT] stale summary of `paths/activations_activationId_graph.yaml:5`.** **accepted** (D).
- **A-8 [NIT] phase 1's planning names `GraphWalk`, which PR C does not ship.** **changed** (R): stated in the merge
  notice on #437/#438; the other mission owns its files.
- **Public symbols #438 consumes:** match `prc-symbols.md` on the phase 2 branch (architect report); used in the notice.

## curator-carla

- **C-1 [MAJOR] I11 contradicts the step-enhancer binding (E6, OD-6).** **accepted** (D): amend I11 (and I9/I10
  "grounded target") for a step enhancer grounded by `bnd(u) = mission_type:M ∈ Eff`.
- **C-2 [MAJOR] `SkippedReport.enhancedTargetNotInForce` says an enhancer "of a mission type that is not in force" is
  named there**; it is in `missionTypeNotInForce`. **accepted** (D): drop the phrase.
- **C-3 [MINOR] the MINOR-5 checklist is longer**: logical-model, the exclusions path description, the feature header
  (:28-30), the `DocumentRules` class Javadoc, DIV-204 yaml+md, the trace Q9 row and the DIV-204 evidence missing the
  two new IC-13 scenarios. **accepted** (D for docs/yaml; C for the feature header and the Javadoc).
- **C-4 [MINOR] service-contract.md:181 "Effective context (R9): Not built".** **accepted** (D).
- **C-5 [MINOR] ADR-0018 D4 depth 0..2 default 2 vs built 0..1 default 0; DIV-244 cites DIV-110 wrongly; ADR-0018 has no
  "built" note.** **accepted** (D): an ADR-0018 note pointing to the narrowing (DIV-244), fix the cite.
- **C-6 [MINOR] ADR-0013 D2 and ADR-0020 D7 untouched.** **accepted** (D): one-line pointer to ADR-0059 R6 in each.
- **C-7 [MINOR] "reserved" wording left behind** (rest-resource-conventions, glossary "Activated pack", roadmap).
  **accepted** (D).
- **C-8 [MINOR] glossary lacks held step, NodeReference, layer override, effective graph/context.** **accepted** (D).
- **C-9 [MINOR] `ActivatableKind.yaml` "every kind of NodeKind except template and asset" is false.** **accepted** (D).
- **C-10 [MINOR] the three `2026-11-07-c-*` fragments (and the changelog file) are named 2026-11-07 but written
  2026-11-09.** **deferred_with_rationale**: the names are the WP files' `create_intent` paths that the planning
  artifacts, the ruleset trace and the hand-offs cite; renaming them now makes those references stale for no reader
  benefit. The release assembly orders fragments by name, and the text inside carries the real date. Tracked in the PR C
  closeout notes for release assembly (#45 holds the release).
- **C-11 [MINOR] `enhancedBy` "sorted by kind and id" (contract) vs "URN order" (rules page); `enhancements` order
  unstated.** **accepted** (C confirms the comparators agree, D states one order).
- **C-12 [NIT] MD038 in `contracts/charter/scenarios/README.md:119`.** **accepted** (D).
- **C-13 [NIT] rules page :62 omits W20 and E12.** **accepted** (D).
- **C-14 [NIT] StepHolding Javadoc "does not take" vs "keeps".** **accepted** (C).
- **C-15 [NIT] ADR-0059 frontmatter description dates.** **accepted** (D).
- **C-16 [NIT] ADR-0059 amendment label "R6" collides with research R6.** **deferred_with_rationale**: the label is cited
  by the rules page, the register, DIV entries, the fragments and the planning files; a rename now churns every PR C
  reference. Amendments are always written "ADR-0059 amendment R6" and research "research R6", which disambiguates.
  Tracked in the closeout notes as a candidate for the next ADR-0059 amendment.
- **C-17 [NIT] DIV-239..244 "corpus case:" evidence is core-side.** **deferred_with_rationale**: by design of the
  register (every DIV names the core corpus case it diverges from); checked by core's conformance run, not this repo.

## Carried folds (approved WPs)

WP27 NIT (ActionContextWalk Javadoc reflow), WP28 NITs (ClosureViewSession comment, EffectiveActionContext.yaml:8,
HexagonalArchitectureTest:341-343, ClosureViewSessionStepRefsTest:276-278, EffectiveTag.java:17), WP28 MINOR-3
(out-of-map lines in the WP28 commit message: R), WP29 MINOR-5 (D, with C-3), MINOR-6 (C: rename the force-preset test),
NIT-1 (C: stored-file pin), NIT-2 (C: test-only `InForceFacts.NONE` and the `check` overload, mark or move).
