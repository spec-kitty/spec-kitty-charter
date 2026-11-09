# PR B closeout notes

Items collected during PR B (WP21–WP26) for the docs WP, the pre-PR squad and the PR description.

## From the WP21 review, cycle 1 (2026-11-08)

- **Same-level settlement is not level-gated (ruling a).** C21 judges cross-level overrides only, which matches C21,
  research R3 and ADR-0063 D3. Until #358 lands (only the distribution pack may settle a sibling contest), any closure
  pack can settle a contest in favour of a sibling and displace a locked artifact with no level check. Deferred to
  #358; WP26 records it in the divergence register.
- **N2: the facts cache reads the pack revision on every call** (a listing plus a digest). The out-of-band check
  needs it. The PR B description must not claim a win on #416.
- **N3: no test asserts that the deployed `OfferingReadService` reads through `CompositionFactsCache`.** Handed to
  WP25 (it serves the reads and owns the deployable wiring; WP22 is contract-only). The full `:deployable:test`
  boots the context through the new constructor.
- **Effective reads take the floor and presets from `merged()`** (the A1 union over every copy); `ClosureView.floor()`
  and `heldPresets()` keep the winners-only floor for writes and `/offering`.
- **Steps under a winning mission type:** a winning `mission_type:M` does not take its losers' unique steps, so a
  built-in `M/s2` survives under a project `M` (A7). The effective-context WP (PR C) confirms this with the architect.
- **Graph nodes count as holding:** an org graph that only declares a built-in step's node replaces that step and
  drops its built-in edges. WP26 records it; a qualified source (`built-in::action:M/s`) avoids it.
- **Divergence register (C-007), for WP26:** C21 is judged over C∖{w} and answers `500 pack_unreadable`, not
  `governance_policy_violation`; the settlement gap above; the facts cache and its revision cost; the WP21
  implementer's list (one winner function for artifacts and presets; steps share their mission type's settlement; a
  settled sibling loser has no `LayerOverride`; `MergedClosure.floor` is the A1 union while `ClosureView.floor` is
  unchanged; C36 also refuses at `merged()`; `holderOf` stays first-holder).

## From WP22 (implementation, 2026-11-08)

- **R-20 red until WP25.** WP22 unreserves the two reads; the whole `:deployable:test` fails only on OperationCoverage
  R-20 until WP25 serves and binds them (runner, accepted; the gate need only pass on the final PR state).
- **getActivation's ETag (operator decision `01M6Y11E55YP5TG1EJ1MDKCFRP`).** It covers the revision and the closure's
  `governanceFingerprint`; folded into the single `### Breaking` block of `2026-11-07-b-effective-reads.md` (with the If-Match rule, operator
  decision `01M6Y3GZFRC5WEQQ7G6GT0YMW3`: writes accept the revision or the current getActivation tag; a stale closure tag
  is `412`). WP25 serves it and updates the
  existing non-`@WIP` scenarios that assert `GET /activations/{id}`'s ETag equals the revision; WP26 amends ADR-0059 D5
  and the WP09 note that getActivation carries no fingerprint.
- **Suggestion carrier (decision `01M6Y0X5E9ANR25MC8RPFXYM72`):** optional `EffectiveArtifact.suggestion {when, reason}`.
- **`ActivationChangeMapper`** is owned by no WP; WP25 serves `warnings` through it.
- **For WP26 (divergences):** core's `kind_default` and `kind_not_activated` reasons are gone; `skippedByScope` stays,
  always empty at read; the language filter applies at read only; per-representation ETag; `satisfiedBy`, `suggestion`
  and the new buckets; `400` for a cursor from another tag.

## From the WP23 review, cycle 1 (2026-11-08, approved)

- **Planning documents to correct (WP26 or closeout; not mid-flight, `plan.md` is an analysis input):**
  - plan.md IC-07 "Facts" bullet ("so the writes and `/offering` do not change") contradicts its FR-038 bullet: the
    write cascade walks the merged edges, so a write builds the merge. FR-023 covers `/offering` only, which never
    builds it. Escalate to the operator only if FR-023 is read as covering writes.
  - Rules page E20 says the cascade change is PR C; the plan puts FR-038 in PR B. Mark E20 built in PR B.
  - Rules page E7 and data-model.md:224-226 state the superseded one-re-run rule; the code follows research R13 and A2
    (withdraw pruned overriders to a fixed point, refuse only an unstable answer). Rewrite both to R13.
  - Rules page C28: clarify "among Base ∪ Req" (the code refuses a cycle reachable from the candidates or the floor).
  - Rules page E5: resolve the Unclear note (a required artifact the language filter excludes is put in force).
- **Contract fragment (`### Changed`):** the activation write cascade walks the merged edges, and a write over a closure
  whose merge refuses answers `500 pack_unreadable` (a response the write paths already declare).
- **Divergence entries (WP26):** cascade over merged edges; C28 as reachable cycles; the E7 unstable refusal; required
  artifacts put in force despite the language filter.
- **Test additions, non-blocking (fold at closeout or a follow-up):** N1 a locked L stops the chain for satisfaction
  (`F → L(locked) → T`, T satisfied by L, never by F); N2 a unit test of the property test's seed-47 shape (a chain
  through a hidden, prunable intermediate); N3 a unit test pinning the chained E7 answer (A enhances Z and overrides B;
  B enhances Y and overrides C; A, B, C selected → C active).
- **For WP24:** map `InForceStates` through the `AvailablePacks` port, no shadow logic in `activations`; `pins`,
  `deadPins` and `pinNotHeld` are WP24's (`InForceRequest.pins` is carried, never consulted); `requiredNotInForce`
  carries the enhancer and binding drops; `unheldRequired` comes from `merged().floor()`.
- **For WP25:** one verified read per effective read (E19), retried up to three times on `stale_input`.

## From the WP22 review, cycle 2 (2026-11-08, approved)

- **Scenarios WP25 must update** (non-`@WIP` scenarios using getActivation's ETag; 28, classified by the reviewer):
  - A1, "ETag is the stored ETag" after a GET (17): activation-cascade-depth :450, 467, 484, 501, 518, 535;
    activation-kind-model :298, 319, 393; activation-presets :371, 411, 448, 470; activation-selections :749, 1273,
    1289, 1492.
  - A2, the GET tag compared with a write's tag (8): activation-register :75/:83, :106, :140, :666;
    activation-register-guards :69; activation-kind-model :447, :499; activation-ruling-changes :185. Their If-Match
    replays without a closure change (register :78, :661; kind-model :442, :449, :494; ruling-changes :180) stay valid.
  - A3, the revision sent as If-None-Match to getActivation expecting `304` (2): activation-register :414/:416, :571.
  - C, the GET tag replayed after the closure changed (now `412`): activation-ruling-changes :212 (A4); send the
    revision or GET again first. The `412` rule stays.
  - Weakened: activation-kind-model :454 ("differs from remembered header") is always true now; compare with the first
    PUT's ETag.
- **WP25 also:** bind the step "PUT … with If-Match the remembered header ETag and the body:", the B4 If-Match and
  `activationsAffected` scenarios; serve `activationsAffected`, `warnings` (through `ActivationChangeMapper`) and map
  the edge's `rationale` to `suggestion.reason`; clear R-20.
- **Contract wording nits (WP25 when it next touches the contract, else closeout):** N6 the `400` sentences ("neither a
  quoted revision nor …") at `activations_activationId.yaml:61`, `:147` and the five sub-resource writes; N7 a stray
  `),.` at exclusions `:17`, `:94`, presets `:23`, selections `:91`; N8 `EffectiveETag.yaml` omits the reserved graph
  and context reads; N9 unused archives declared in the two new scenarios.
- **WP26:** amend ADR-0020 D6 (either-tag If-Match) and ADR-0059 D5 (the closure ETag), and the WP09 note that
  getActivation carries no fingerprint.

## From the WP24 reviews (2026-11-08, approved in cycle 2)

- **Closure-scoped digest (F1).** Effective tags and getActivation's tag cover `ClosureSnapshot.closureFingerprint()`:
  `sha256("charter-closure/v1" + configured id and level per closure pack + the closure packs' files)`, taken inside
  the same `ConsistentRead` load. `fingerprint()` and `Charter-Source-Fingerprint` keep the whole-source digest.
  Shared packs edits: `PackSources.fingerprintOf`, `ConsistentRead.fingerprintOf`, `ClosureView`, `ClosureQueries`,
  `FileSystemPackSources`, `ProjectConfigSources`; `ArtifactState.overriddenBy` in `InForceStates`.
- **Suggestions (decision `01M6Y6VE6VZQ4MXD98N93JB3GJ`):** only a `not_selected` artifact carries one; `enhances` is not
  a hard relation. PR C's action context uses the same definition.
- **For WP25:** the bodies' `governanceFingerprint` is the same closure digest as the tag; serve the either-tag
  If-Match as `revision.token()` or the current `EffectiveTag.activation`; count the extra closure-file read in the
  NFR-003 timing case (N2); align `ActivationSummary.required` (it also counts locked artifacts) and the "a locked
  artifact is always in force" wording (E9 lets one be inactive) (cycle-1 F4).
- **For WP26:** divergence/ADR entries for the closure-scoped digest (no longer `ServiceInfo.sourceFingerprint`; amend
  `EffectiveArtifactList.yaml` and related schemas), per-read and per-query tags, the suggestion scope; update R5 and
  the data model's EffectiveTag text (the query is part of the preimage); `MergedSnapshot.missionTypes`/`stepBinding`
  deferred to PR C.
- **Optional (closeout):** N1 `FileSystemPackSources.fingerprintOf` skips an unknown name silently (refuse it); N3 a
  unit test that a level change alone changes the closure digest; the forced `ActivationTestWiring` edit is a
  shared-file edit to list.

## From the WP25 review, cycle 1 (2026-11-08, approved)

- **Built and verified:** the two reads; getActivation's closure tag (`EffectiveTag.activation` over
  `closureFingerprint`), `denyNotApplied` only when non-empty, no fingerprint header on it; `governanceFingerprint` is
  the closure digest; either-tag If-Match in one class (`IfMatchTags`, revision checked first, so a revision costs no
  read); `warnings`; `notices.activationsAffected` (bounded, sorted, before the commit; an unmergeable closure counts as
  changed); the NFR-003 counting gate in `check`. Full `:deployable:test` green (1,173), R-20 cleared.
- **Defects fixed in earlier WPs' code (record in the PR description):** the in-force bindings were never fed
  (`MergedView.resolutionEdges()` passed `Map.of()`); a mission step contract now binds to `mission_type:<mission>`. The
  in-force walk bound `bound³` overflowed an `int` at about 1,291 artifacts and made reads 500; now a `long`, pinned by
  a 2,000-artifact test.
- **E9 is authoritative** for a required, unselected T overridden by F (`not_selected` with `satisfiedBy`, before
  `overridden`; data-model row 6 before row 7, R11 step 3). WP22's scenario now selects T; N3 adds the unselected case.
- **Non-blocking, for the closeout:** N1 about 18 effective-reads scenarios were red at the recorded-red commit also
  from their own invalid fixtures (fixed in `4268c794`, no assertion changed; process note); N2 a write whose If-Match
  is a closure tag reads the packs twice, so a pack change between the two reads could let a just-stale tag pass
  (compute the tag from the write's own session); N3 add the unselected required-T assertion at
  `effective-reads.feature:452` and fix its "requires T and F" wording; N4 `e13a8c14` also edits WP22-owned contract
  wording (four `paths`, two `headers`, `ActivationSummary`, `EffectiveArtifact`), adds the wording fragment and drops
  21 unused archives without `out-of-map` lines (record in the PR B description); N5 remove `ActivationService.get`
  (dead outside tests); N6 a closure that could not compose before a write counts as "nothing in force" rather than "no
  answer"; N7 two compatibility constructors used only by tests (`MergedView(closure, edges)`, 7-arg
  `PackCompositionFacts`); N8 `activations_activationId.yaml:148` says the DELETE "takes no snapshot of the packs",
  untrue for a closure-tag If-Match.
- **For WP26:** getActivation answers 500 when the closure cannot compose or merge; the If-Match read cost; bindings
  cover step contracts only (`stepBinding` for step enhancers is PR C); three digests per read; page links repeat
  `pageSize` only when the request named it; amend ADR-0020 D6 and ADR-0059 D5; N8; make E9 agree with R11.

## From the WP26 reviews (2026-11-08, approved in cycle 2)

- **Contract fragment added in WP26:** `2026-11-08-b-write-cascade-merged-edges.md` (`### Changed`; the sub-resource
  writes declare `500` as `OfferingUnreadable`; PUT never builds the merge; DELETE answers `412` when its closure read
  is refused).
- **Rules-page rows still to reconcile (closeout):** G3 prose `:377` says "Ruled, not built" while its table row says
  Built (PR A); G7 table row `:1023` says "Planned, not built" while its prose says Built (PR A); G1 is stale
  (`PackRegistrationService:295-297` refuses a built-in-level upload with `registration_conflict`); check G4–G6 and
  G9–G19 in the same pass.
- **Contract wording:** `OfferingUnreadable`'s list of `pack_unreadable` reasons does not name the merge refusals (an
  override the level forbids, an override cycle, an unstable answer, `self_override`).
- **DIV-201 and DIV-202** are `open` now; the conformance gate (#72) does not exist yet, so "enforced by the gate"
  enforces nothing today.

## Pre-PR squad, 2026-11-08 (runner)

Four lenses on the combined tree (lane-x at 22a3a524): reviewer-renata (seams), architect-alphonso (architecture,
concurrency, cache), doctrine-daphne (contract and docs fidelity), qa-strategist-quinn (test adequacy, 26 throwaway
mutants). All four: ready with follow-ups. Reports: the runner's scratchpad `squad-*.md`.

### Dispositions

Accepted, each folded into PR B as its own commit(s) (branch `issue-10-pr-b-effective-reads`):
- **F1** (R1 HIGH, R2): the A1 union floor also drives the registration floor notices and the write-side floor
  refusal (decision `01M6YKZRM7VF19A57E40D59N81`; `### Breaking`: a deny of a union-required artifact is 409).
- **F2** (D1 HIGH, R4): `governanceFingerprint` described as the closure digest, not `ServiceInfo.sourceFingerprint`.
- **F3** (D2 HIGH, R3, Q HIGH, D6): the contract's reason precedence follows E9; pairwise E9 tests that kill the row-swap
  mutants; a denied-enhancer scenario; warnings on additions only (decision `01M6YKZXGFGS340D1D9TE5T809`); a pruned
  required artifact listed once (decision `01M6YM02EG3X5V8G33T1HT5N0K`).
- **F4** (A1 MEDIUM): the facts cache keys on a path-inclusive per-pack digest (an out-of-band rename misses).
- **F5** (R5): `activationsAffected` also compares the winner of each in-force artifact.
- **F6** (D3, D4, D5, D7, D8, D9, D10, R8, WP25 N8, `OfferingUnreadable`, rule rows G1/G3/G7): contract and docs wording.
- **F7** (Q MEDIUM): the property test's oracle (grounded `requiredNotInForce`, liveness, generated bindings, winner
  shuffle) kills the "nothing in force" and "report everything" mutants.
- **F8** (Q MEDIUM): the NFR-003 gate counts rounds inside a resolution.
- **F9** (Q LOW): a cursor replayed under another filter is 400.
- Review leftovers folded where quick: WP23 N1–N3, WP24 N1, WP25 N3, WP25 N5.

Deferred with rationale, tracked in #426: A2 (`activationsAffected` under the registry lock, NFR-009: needs a design
choice between dedupe, post-lock notices and a cap); A3/WP25 N2 (If-Match tag from the write's own session: no
in-JVM race under the lock, a cost only); A4 (the 304 cost, documentation and edge rate limiting); A5 (static counters);
A6 (enum mapping by name); R6 (two preset winner functions agree while closure order is level-sorted); R7 (registration
does not try the merge: needs a ruling on refusing at registration); WP25 N6, N7; the kind-model filler step; WP24 N3.
Handed to PR C: A7 (reuse the suggestion rule for the action context).
