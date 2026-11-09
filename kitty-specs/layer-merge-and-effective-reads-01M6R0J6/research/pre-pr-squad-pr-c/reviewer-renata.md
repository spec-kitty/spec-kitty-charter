# PR C aggregate squad review: reviewer-renata (correctness, fakeable tests, cross-WP seams)

Branch `issue-10-pr-c-graph-context` (a07524cc WP27, d74382fd WP28, c893a7ec WP29) against `origin/main`. Read-only.

## Profile and governance applied
- `spec-kitty agent profile show reviewer-renata` loaded. Directives applied: 001 (boundaries at the packs/activations
  port), 024 (locality), 030 (test gate: targeted Gradle run below), 032 (terms: "active", "satisfied", "held step"
  read as the rules page and DIV-204/225 define them), 041, 051. Tactics: code-review-incremental (only the aggregate
  seams, building on the per-WP cycles), reverse-speccing (DIV-204 text vs code), test-readability-clarity-check,
  delete-the-assertion-not-the-test (mental mutations below), test-scaffolding-as-design-smell.
- `spec-kitty charter context --action review --json`: charter directives 001/003/010/025/030/032/034/037/041/051,
  QA_003_NO_FAKE_GREEN, NO_FULL_HEAVY_SUITES_IN_MISSION. The missing `internal`/`qa` packs are the known environment gap.
- Excluded from re-raising: everything dispositioned in `research/pr-c-working-notes/` (incl. WP29 MINOR-5/6 and the
  NITs queued for closeout), #439, the A7 dissent.

## Gradle (one foreground run, in .worktrees/pr-c)
`:activations:test --tests '*DocumentRulesOverrideTest*' '*EffectiveReadServiceTest*' '*EffectiveTagTest*'
'*ClosureViewSessionStepRefsTest*' :packs:test --tests '*ActionContextWalkTest*' '*ReachabilityTest*' '*StepHoldingTest*'`
(JAVA_HOME=temurin-25, --max-workers=1, -PopenPacksDir, -PcontractBaseline=none): green on the aggregate head.
No mutation worktree was needed; the mutations below are reasoned against the tests and were already confirmed for
the cache pins in WP28 cycle 2.

## Findings

- [MINOR] service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java:31-35 -
  The tag's domain string stays `charter-effective-read\0v1\n`, while the class's own rule says it "is bumped in any
  change that alters what the reads answer for the same inputs (a merge rule change, a new reason)". PR C changes the
  answers of the PR B reads (`getActivation`, `listEffectiveArtifacts`, `getActivationSkippedReport`) for the same
  revision and the same closure digest: WP27's OD-6 binding puts a step enhancer in force that was out of force
  (`contracts/charter/changes/2026-11-09-c-step-enhancer-binding.md`: "it is now in force"), the skipped report's step
  references become `NodeReference` and `layerOverrides` is a required member (`### Breaking` in WP28's fragments), and
  D16 drops non-activatable entries. A client that cached a PR B body gets `304` for a body that is now different, so
  the strong tag names two bodies across the release. This is a seam: WP27 changed the rule, WP28 owns the tag, and
  neither per-WP review checked the version. No test pins the domain string, so the invariant cannot fail either way.
  Recommendation: bump to `v2` in PR C and add a golden-vector test (a fixed revision, closure digest and query giving a
  literal `sha256:` token) so the next rule change has to touch the test on purpose. If no PR B build was ever served,
  record that and still bump, because the class promises it.

- [MINOR] docs/conformance/divergence-register.md:500 (DIV-204) - The text says "The resolution is asked only when a
  write adds a deny entry on a required artifact, so a layer merge that refuses ... reaches that write as `500
  pack_unreadable`". That holds for create and replace (`ActivationService.checked`/`judged`), but not for the
  per-artifact writes. `ActivationWrites.commit` always calls `warned(...)` (ActivationWrites.java:392, 402-406), which
  asks `session.inForce` whenever the write applied anything (IC-17, from PR B). So `PUT /activations/{id}/exclusions/directive/X`
  on a free `X`, under a merge that refuses, is `500`, not `200`. The scenario's "merge not consulted" control
  (activation-deny-satisfied.feature:446-455) uses a create, so it cannot show the difference, and a reader of DIV-204
  would expect the deactivate to succeed. DIV-217/DIV-230 already say every write that builds the merge answers it.
  Recommendation: limit the "only" sentence to create and replace (or to "the floor judgement asks ... only when"),
  and cite DIV-230 for the per-artifact writes. This is docs only, so it can be folded at closeout with MINOR-5.

- [NIT] service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationExclusionService.java:156-168
  with ClosureViewSession.java:115-123 - When a deactivate names a required target, the request now runs two in-force
  resolutions: the early refusal asks for `before.withDeny(T)`, and the commit asks for `after` with the cascade.
  `statesOf` caches one request only, so the second request evicts the first. This is correct, since the commit is the
  definitive judgement (hand-off item 2), and NFR-007 is about one verified read, not one resolution. But the WP28
  "resolved once per request" wording (its Javadoc and the pins) is about reads only. Recommendation: say "per read
  request" in the `statesOf` Javadoc, or leave as is. No action needed for the merge.

## Seams checked, no finding (with evidence)
- **One judgement, read and write, on the same document.** The write asks `facts.inForce(EffectiveSet.queryOf(stored))`
  (DocumentRules.java:414), where `stored` is `built.document()`, `after`, or the stale-dropped `kept`. The reads ask
  `session.inForce(EffectiveSet.queryOf(stored.document()))` (EffectiveReadService.java:192, 334, and `effectiveSet`).
  Both use the same function over the same `PacksSession::inForce` → `ClosureViewSession.inForceOf`. So the D16
  filter on `satisfiedBy` (ClosureViewSession.java:288) applies to both sides alike. The graph (WP28) and the context
  read the same `statesOf`. Mutations that pass `denying` or `before` instead of `stored` are caught by the replace
  "adds both" and the deactivate scenarios (the added-only document has no `F` selected, so it would refuse). The
  scenario reads back after the write: the active list, `denyNotApplied` empty, and the unselect case lists
  `required`. It is not write-only.
- **The cascade change (stepsOf follows held steps only).** The commit re-judges `checkWrite(before, after)`, cascade
  included (ActivationWrites.java:387). So a cascade that denies the overrider (`T requires F`, `F overrides T`) is
  refused at commit, even though the early refusal passed. The order is safe. On the read side, the graph nodes use the
  same `StepHolding.stepsHeldByClosure` as `EdgeIndex.stepsOf`/`heldSteps`, so the cascade and the graph agree on which
  steps exist.
- **ETag/304 of the new reads.** Each tag is computed over the read, the revision, `closureFingerprint` and the
  normalized query (depth included). The context judges its names before the 304 (`requireAction`, before the tag in
  EffectiveReadService.actionContext). EffectiveTagTest covers five reads with five tags, a closure change, a pack
  outside the closure, a document change and each context query. Scenario `FR-020` (effective-graph-and-context.feature:877)
  covers the cross-read tag and the tag moving on a write. Apart from the version above, this is sound.
- **Concurrency and stale input.** One `PacksSession` per request. `latest`'s CAS retry reuses the session, so the facts
  and the snapshot come from one verified read. `stale_input` can only come from opening the session, which PR C does
  not change. Writes run inside `coordination.exclusively`.
- **Error paths.** A merge refusal reaches a create/replace deny write as `500 pack_unreadable`, and nothing is stored
  (scenario at :370, with a GET 404 afterwards). `InForceResolution.pruningReasonOf` throws `IllegalStateException` on
  an impossible reason (InForceResolution.java:410-416). That is a deliberate invariant, reachable only by a resolution
  bug.

## Where my lens does not apply or did not reach
- I did not check whether a pack can author an `overrides` edge from a non-activatable kind (for example a locked
  `template`) onto a required directive. If it can, packs would report `satisfiedBy` for it and the D16 filter
  (ClosureViewSession.java:288) would drop it, so the write would refuse `409` while the read shows `T` out of force,
  overridden. I could not confirm the policy/edge schemas in the timebox. If the architect lens knows this shape is
  unauthorable, it is a non-issue.
- I did not run the full `CharterContractSuite` or `verifyAll` on the aggregate head (the lanes ran them, and the PR C
  gate will). My run was targeted at the seam units only.
- Docs prose beyond DIV-204, performance (the StepHolding quadratic is deferred), and supply chain (no new
  dependency) are outside this lens.

## Verdict: GO-WITH-FIXES
No blocker or major. The IC-13 write and the WP27/28 reads judge the same document through the same resolution, and
the tests at that seam would fail if it broke. Fix the tag version (MINOR, code plus a golden test) in PR C. The DIV-204
wording (MINOR, docs) can be folded at closeout.
