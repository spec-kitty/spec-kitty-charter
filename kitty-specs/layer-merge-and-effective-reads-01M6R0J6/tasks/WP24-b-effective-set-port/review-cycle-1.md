---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-08T18:30:24Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP24
---

# WP24 review, cycle 1 (claude:opus:reviewer-renata, 2026-11-08)

**Verdict: changes requested.** One blocking finding (F1) and one finding that needs a decision before approval (F2).
The rest of the WP is sound. The port, the value snapshot, the mapping, the NFR-007 reuse, the buckets, the summary and
the shared `overriddenBy` edit in packs are all approvable as they stand.

Base `a4719231`; WP24 is `fba3cc39..7463e5bb` (8 commits).

## Commands run

- `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=/home/user/open-packs :activations:check :packs:check`: **BUILD SUCCESSFUL**. EffectiveSetTest 24/24, EffectiveTagTest 7/7, PacksAvailabilityTest 18/18 and InForceResolutionTest 53/53, with 0 skipped.
- The same flags with `:deployable:test`: 1132 tests, 1 failure, the accepted OperationCoverage R-20 (`GET /activations/{activationId}/artifacts 200`, `.../skipped 200`). 509/509 scenarios pass. HexagonalArchitectureTest passes 61/61, and the WP does not touch its exclusions.
- Red sampling, in a throwaway worktree that was removed afterwards:
  - `57e14be6`: `EffectiveSetTest` gives 23/24 failures, all AssertionErrors against the stub (`queryOf` returns an empty query, `of` lists nothing).
  - `80698b9c`: `EffectiveTagTest` gives 6/7 failures, AssertionErrors against a constant all-zero tag. The one test that passed at red is `aChangedPackOutsideTheClosureLeavesTheTagAsItIs`. See F1.
- The diff adds no `@SuppressWarnings`, `@Disabled`, `NOPMD`, `CHECKSTYLE` or `@WIP`, and removes no assertion.

## Findings

### F1 (High, blocking): the tag claims a pack outside the closure leaves it unchanged, but the fingerprint covers every configured pack, and the test that proves it is fake green

- `EffectiveTag.java:17-20` and `:132`: the tag digests `closure.fingerprint()`. The javadoc says "A pack outside the closure is not in the fingerprint and leaves the tag as it is."
- That fingerprint is `ClosureView.fingerprint()`, set at `PacksAvailability.java:135`. It is the `ConsistentRead` digest, which is `PackSources.fingerprint()`. `FileSystemPackSources.java:34` digests **every configured pack** (`locations.get()`), whatever the roots are.
- So in production, a change to a configured pack outside the activation's closure changes the fingerprint, and the tag with it.
- This breaks:
  - T123 case 3;
  - `headers/EffectiveETag.yaml` ("a pack outside the closure leaves it unchanged");
  - the closure scope that decision `01M6Y11E55YP5TG1EJ1MDKCFRP` names.
- Under the either-tag If-Match of decision `01M6Y3GZFRC5WEQQ7G6GT0YMW3`, a remembered getActivation tag then answers a spurious `412` whenever an unrelated pack changes.
- `EffectiveTagTest.java:55-69`: the test builds both snapshots with the same hand-set fingerprint `sha256:one`. It adds a held id to the closure's own pack, which is not a pack outside the closure. So it asserts only that the tag ignores everything except the fingerprint. It passes against the constant-tag stub, which proves nothing about the scope. That is fake green (QA_003), and its `as(...)` message states a property the code does not have.
- **Fix:** do one of these.
  - (a) Give the closure a closure-scoped fingerprint from the same verified read. For example, `ClosureView` and `ClosureSnapshot` carry a digest over the closure packs' own file digests, in closure order. This is a packs shared edit, listed with its rationale. Feed that digest to `EffectiveTag`. Prove both directions in `PacksAvailabilityTest` or a packs test over two configured packs, using the real digest:
    - a change to the pack outside the closure leaves the tag the same;
    - a change to a closure pack gives a new tag.
  - (b) If the runner or operator rules that the all-configured source fingerprint is accepted, record that decision. Then:
    - amend `EffectiveETag.yaml`, the `governanceFingerprint` descriptions and T123 case 3;
    - replace the test with one that pins the accepted behaviour;
    - correct the javadoc;
    - hand WP26 a divergence entry, including the `412` consequence.

  Either way, delete or rewrite the current case-3 test.

### F2 (Medium, decision needed): the suggestion is attached to every out-of-force reason, not only to `not_selected`

- `EffectiveSet.java:336-351`: any artifact that is not ACTIVE gets a `suggestion` when an ACTIVE artifact suggests it and no ACTIVE artifact reaches it by a hard relation. That includes an artifact that is:
  - `excluded/deactivated`: the document explicitly denies it;
  - `overridden`: an in-force version replaces it;
  - `language_filtered` or `mission_type_not_in_force`: a read-time filter removed it.
- #408 ruling 3 lists an artifact "reached only through suggests" as a suggestion. For those reasons, the suggests edge is not what decides that the artifact is out of force. Telling an agent to consider a denied or replaced artifact contradicts the document or the merge.
- The tests cover only `NOT_SELECTED` targets (`EffectiveSetTest.java:425-498`).
- **Fix:** limit suggestions to targets whose reason is `NOT_SELECTED`. Add one test per other out-of-force reason that asserts no suggestion. If you read ruling 3 differently, ask the runner for a decision and pin the ruled behaviour with tests. I am not making the product call here.

### F3 (Low): `enhances` counts as a hard relation that "reaches" an artifact

- `EffectiveSet.java:255-262`: `HARD_RELATIONS` is `CASCADE_RELATIONS` without `suggests`, so it includes `ENHANCES`. If an active E enhances S and an active D suggests S, S gets no suggestion. But an enhancement "pulls nothing in" (IC-09 step 4, E12).
- **Fix:** remove `ENHANCES` from the set, or name the hard relations explicitly (`requires`, `refines`, `scope`, `instantiates`), and add a test. Agree this set with PR C's action-context walk so the list and the context share one definition.

### F4 (Low, no change needed in this WP): `summary.required` also counts locked artifacts

- `EffectiveSet.java:241-245`: the count is `required && (active || satisfiedBy)`. The packs flag `required` is `required || locked` (`InForceResolution.java:400`).
- So a locked artifact that no preset requires is counted in both `required` and `locked`. That matches the data model's field definition, but the contract's `ActivationSummary.required` says "Artifacts a governing preset requires".
- Note too that `locked` counts by policy, while E9 puts the mission-type and enhancer rows before `locked`. A locked artifact can therefore be inactive, so "a locked artifact is always in force" (`ActivationSummary.locked`) is not strictly true.
- **Fix:** hand both points to WP25 or the closeout to align the contract wording. Changing the count instead needs a ruling.

### F5 (Nit): `ActivationTestWiring.java` (deployable) changed outside `owned_files`

The edit is forced, because the port grew two methods. List it with the packs shared edit in the hand-off.

## Rulings on the implementer's questions

1. **The `overriddenBy` shared edit in packs: correct and minimal.**
   - It reuses `overriderOf` over the same `inForce` and `reach` as `satisfiedBy`, and is set exactly when the reason is `OVERRIDDEN`.
   - It names the top of a chain, the active overrider (E9 "hidden by E4"), and the smallest URN of several.
   - Recomputing it in activations would be a second walk (C-002).
   - The three tests cover the direct, chain, several-overriders and other-reason cases.
2. **C-002 and the hexagonal rules: hold.**
   - Only `activations.infra.packs` imports packs types.
   - `MergedSnapshot` and its nested `InForceQuery`, `InForceAnswer` and `ArtifactState` hold values only.
   - `EffectiveSet` maps the answer and does not recompute the precedence.
   - The suggestion rule reads one hop over the edges that packs already re-homed, joined against the states. It builds no transitive closure, no fixed point and no merge, so it is not a walk and not shadow logic.
   - Its scope (F2) and its relation set (F3) need fixing. **Open question 1:** yes, an artifact in force by its own route carries no suggestion, which is consistent with "reached only through suggests" and with the contract's `### Added` entry.
3. **Precedence and buckets: correct.**
   - The precedence lives in `InForceResolution` (WP23, approved). The E9 row order puts the mission-type and enhancer rows before `locked`, as the rules page orders them.
   - `EffectiveSetTest` has one test per reason and bucket, including the A7 mission-type row.
   - Buckets and artifacts are sorted by `ArtifactRef` (kind token, then id in code points).
   - `skipped` is the bucket total, as the data model and contract define it. `required` and `locked`: see F4.
4. **NFR-007: met.** `theMergedClosureAndTheInForceStatesAnswerFromTheOneReadOfTheSession` counts one read across a snapshot, two `merged()` calls and two `inForce()` calls. `ClosureView.inForce` reuses the memoized `merged()`.
5. **EffectiveTag: partly met.**
   - The five cases exist, but case 3 is fake green (F1).
   - The preimage is the domain tag, the read name, the revision, the fingerprint and the normalized query. The query includes the resolved `pageSize`, which is right because another page size is another body. It is unambiguous, because `pageSize` is an int at the end of the preimage.
   - `activation(revision, closure)` matches decision `01M6Y11E55`, apart from the closure scope in F1.
   - The either-tag check is not in WP24's code. It is WP25's serving work, and `revision.token()` or `EffectiveTag.activation(...)` is the right predicate for decision `01M6Y3GZ`.
   - R5 and the data model's EffectiveTag text ("revision and fingerprint", "query needs no field") are now superseded by the read name and the query. WP26 must update them.
6. **Open question 2: throwing `IllegalStateException` is acceptable and better than dropping the entry.**
   - Packs guarantees the invariant.
   - Dropping the entry silently would break E14's "listed once" and the summary count.
   - The exception surfaces as a `500`, which is what a broken invariant should be.
7. **What is not built: fine for PR B.**
   - `missionTypes` and `stepBinding` are not needed for the list, because the mission-type state arrives from packs as a reason.
   - Mission types and step contracts are activatable and are listed.
   - Templates, assets, guards and lifecycles are not activatable kinds in the contract, and the rules page notes them under E11 Unclear.
   - Defer the rest to PR C.
8. **Red-first: adequate for the domain tests, except for F1.**
   - The tests that cannot fail against a stub (the NFR-007 one-read test, the unresolved session and enum parity) are acceptable: they pin structural properties, and the mapping tests beside them fail properly.
   - `aChangedPackOutsideTheClosureLeavesTheTagAsItIs` is not acceptable: it passes because it tests nothing real (F1).
   - No test was weakened and no suppression was added.

## Hand-offs

- **WP25:**
  - Serve the either-tag If-Match (`revision.token()` or the current `EffectiveTag.activation`).
  - Use the closure-scoped fingerprint from F1, once it lands, for `governanceFingerprint` too.
  - Align the `ActivationSummary.required` and `locked` wording (F4).
  - Map the edge's `rationale` to `suggestion.reason`. This is already done in the domain.
- **WP26:**
  - Divergences: per-read and per-query tags; the F1 outcome; suggestion scope (F2).
  - Update R5 and the data model's EffectiveTag text to the read name and normalized query.
  - Record that `MergedSnapshot` defers `missionTypes` and `stepBinding` to PR C.
- **Closeout and PR C:** one definition of "hard relation" and suggestion for the list and the action context (F3). The action context's suggestion should come from the packs action-context walk.
