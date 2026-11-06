---
affected_files: []
cycle_number: 2
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-06T17:24:06Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP05
---

**Issue 1 (gate, blocking): spotless fails on WP05's new test code.** `:activations:check` fails `:activations:spotlessJavaCheck` on ActivationSelectionServiceTest, CascadeRulesDeactivateTest, CascadeRulesReportingTest and CascadeRulesSelectTest, and `:packs:spotlessJavaCheck` fails on ReachabilityTest. Every violation is in a line this commit added. Fix: run `./gradlew :activations:spotlessApply :packs:spotlessApply`, then re-run `:activations:check` (only the 5 WP08-owned failures in ActivationBodyAdviceTest/ActivationJsonTest should be left) and `:packs:check`.

**Issue 2 (blocking): Q1 does nothing in production, and the contract scenario for Q1 cannot pass.** `EdgeIndex.stepsOf` reads `PackUnderResolution.definedArtifacts()`, and `EdgeIndex.node/holds` refuses any place not in that list. In production that list never holds an `action:` URN:
- `DomainRepositoryService.compositionFacts` adds only file artifacts plus the graph-declared `anti_pattern` nodes (`declaredWithoutFile`).
- A mission type's steps are values inside the MissionType file.
- No production code calls `Urn.action`.

So `steps(M)` is always empty, and a mission-type select only walks M's own edges.

The ReachabilityTest fixture puts `action:m/*` in `definedArtifacts`, a shape production never builds. The ActivationSelectionServiceTest and CascadeRules tests stub the Reach. So no test runs the production path for FR-040 Q1 (anti-pattern 2).

The WP01 contract scenario "Selecting a mission type with a cascade selects what its steps reach" (activation-kind-model.feature, still @WIP; WP08 binds it) declares `action:software-dev/implement` as a node in `steps.graph.yaml` with `requires -> directive:SD`. It expects `directive:SD` in `applied`. With this code it will fail when WP08 drops @WIP, and WP08 owns none of the files that could fix it.

No WP prompt assigns "packs hold the steps", so this is a planning gap. It is still FR-040/Q1, which is this WP's. Fix it here unless the orchestrator rules otherwise:
- **Preferred:** treat graph-declared `action` nodes as held. Add `action` to `declaredWithoutFile`, whose javadoc already says "the nodes its graph documents declare for a kind that is never a file". Record DomainRepositoryService as an out-of-map edit.
  - Decide how this interacts with `refuseSiblingContests(... definedArtifacts, "define")`. Two sibling packs declaring one action node would now be refused. Either exclude `action` from the define contest or get a ruling.
  - Check `ClosureQueries.heldOf`, which skips kinds with no `ArtifactKind`.
- **Or:** derive steps in packs from what the closure declares, without changing `defined`. Make sure `EdgeIndex.holds` accepts a step place so the walk can descend from it.
- Either way, add a test that builds the index through the production facts. For example, a packs test that composes a pack holding mission type M plus a graph shard declaring `action:M/s requires directive:D`, then shows a reach from `mission_type:M` reaching D.
- If the orchestrator rules that holding steps is out of A0, record that decision and re-tag or move the Q1 scenario instead.

**Issue 3 (DoD wording, note only):** "a mission-type select names its steps in applied" cannot hold as written. `action` is not an ArtifactKind, so a step is never stored or answered. The implementer's reading, that `applied` names what the steps reach, matches the contract scenario. Say so in the hand-off note.

**Merge notes (not blocking):**
- WP04 (lane-d) made `ActivationWrites.deniable` accept mission types. WP05 switched ActivationExclusionService to `selectable`, which leaves `deniable` with no caller after the merge. At the merge, either keep the `deniable` call (WP04's version) or delete `deniable`.
- ActivationExclusionServiceTest: both lanes rewrote `aMissionTypeHasNoDenyListSoItsKindIs422AtThePath`, and the two versions agree in intent (path accepts mission_type, required is 409, unknown is 404). Keep both versions. WP04's covers spelling normalisation; WP05's covers locked, and deny plus re-activate.
- Tell the WP21 owner about `EdgeIndex.stepsOf`.

**Verified OK:**
- Q2: requireDeniable allows mission types; locked and required mission types answer 409.
- B5: clearing is the selected set ∩ Den. The A←B←C test keeps A's deny entry.
- The rewritten tests each follow a ruled change (W21, W22/B5, W23, W32 withdrawn) and none was weakened. The ActivationWriteOrderTest swap to `template` is right.
- Red log: 19 failures. The 6 non-assertion ones (IllegalArgumentException from requireDeniable, NoSuchElement, a refusal) are the old rule's guards and count as valid red for a withdrawn rule.
- Timing: `:packs:timingBudgets` Reachability p95 is 13.3 ms.
- Gates: HexagonalArchitectureTest 59/59. ReachabilityTest 19/19. `:packs:checkstyleMain` green. `:activations:test` has exactly the 5 WP08-owned failures. The deployable suite has 109 failures, matching the WP08 count.
