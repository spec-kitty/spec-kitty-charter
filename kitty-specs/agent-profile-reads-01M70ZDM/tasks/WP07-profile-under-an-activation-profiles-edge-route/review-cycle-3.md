---
affected_files: []
cycle_number: 3
mission_slug: agent-profile-reads-01M70ZDM
reproduction_command:
reviewed_at: '2026-11-10T08:05:34Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP07
---

# WP07 review, cycle 3 (reviewer-renata): changes requested

The B3 rework applies operator ruling DM-01M7232ZMR4EWNDA1P2VF455Y1 well. It is consistent across:

- the walk;
- both domains;
- the schemas (`GovernanceEdgeWords` is closed, carries `x-derived` and is a required `$ref` on both entries; the two
  `*Suggestion` schemas are gone from the files, `_index.yaml`, the code and the hints);
- both path descriptions and both fragments (one `### Added` each);
- the scenarios and the scenarios README;
- the coord data-model;
- ProfileCorpusTest.

The effective example matches open-packs `a2a9379` entry for entry. I checked every `edge` against the pack graph files
with a script. One blocker remains, in the pack-scoped example, and it is cheap to fix.

## Blocking

**B4: `contracts/charter/examples/Artifact.agent_profile.yaml` contradicts the contract's own rule against the pinned
pack. The worst case is `java-conventions`, which shows `edge: {}`.**

- **Where:**
  - `examples/Artifact.agent_profile.yaml`, the `styleguide:java-conventions` entry (`edge: {}`, about line 120).
  - Less important: the DIRECTIVE_030 entry (about lines 94-97) and the DIRECTIVE_041 entry (about lines 111-113).
- **Failure:**
  - In `agent_profile.graph.yaml` at `a2a9379`, the edge `java-jenny requires styleguide:java-conventions` states
    `reason: "A language-specialist profile requires the conventions styleguide of each of its applies_to_languages
    (java); operator ruling on spec-kitty-charter#405."`. This is the #405 edge that FR-001 names.
  - The service answers that reason. ProfileCorpusTest pins it, and so does the effective example.
  - The pack-scoped example tells a client the edge states nothing, so the example contradicts the rule that its own
    schema now states: "every entry carries the words of its edge".
  - This line came from the mechanical `suggestion: null` → `edge: {}` replacement in 59a4fcf5. The commit message says
    the example "gains a real requires entry with words". The implementer's report says "both examples are real service
    output at a2a9379". That holds only for the effective example.
  - The DIRECTIVE_030 and DIRECTIVE_041 reasons are cut after the first sentence. The real reason goes on with
    "Composition-only in the profile channel, authored per the #3063 family-D operator attestation."
  - DIRECTIVE_099 (`held: false`) is not a real java-jenny edge. It was carried over from WP04, which illustrated
    `held: false` with it.
  - The summary only says "trimmed".
- **Required fix:**
  - Give the `java-conventions` entry its real `edge` (`reason` only, with no `when`). Use the full strings for the
    DIRECTIVE_030 and DIRECTIVE_041 reasons.
  - Alternatively, keep shortened strings, but then the summary must say that the words are shortened. Either way, the
    `java-conventions` entry must not be `{}`.
  - Reword the summary so it does not read as service output. For example: "trimmed and illustrative: the entries and
    words are java-jenny's at a2a9379, shortened; DIRECTIVE_099 is invented to show an entry no pack holds."
  - Optional: a cheap guard. Add a test that checks every `edge` in both examples against the pack graph files, as
    ProfileCorpusTest does for the wire, skipping the invented `held: false` entry. That stops this class of defect, the
    same kind as B1, from coming back.

## Not blocking

- **Action-context wire.** It is unchanged.
  - `ClosureViewSession.contextOf` (`ClosureViewSession.java:175-190`) never reads `ActionContextWalk.Entry.when/reason`:
    it maps `Reached(ref, enhancedBy)` only.
  - Suggestion candidates come from `recordRefusedSuggestion`, which handles `suggests` edges only.
  - So the new `suggestsOnly` gate (`ActionContextWalk.java:326`) keeps the `Entry` API meaning, not a wire.
  - Coverage of the gate is one-sided. PIT kills "negated conditional" through
    `aSuggestedEntryCarriesTheWhenAndTheReasonOfTheEdgeThatReachedIt`. No test feeds a `scope` or `requires` edge that
    carries words into the action walk and asserts that the entry gets none: the `tactic:T1` assertion at
    `ActionContextWalkTest.java:162` passes with the gate removed, because T1's `scope` edge has no words. Consider adding
    a worded `scope` or `requires` edge to that test.
- **The read-level `suggestion` on `EffectiveAgentProfileRead`** agrees with the ruling. The ruling covers the
  governance entries. The read-level member is `EffectiveArtifactRead`'s `EffectiveArtifactSuggestion` for the winner
  itself (#408 ruling 3), unchanged on every generic effective read. `EffectiveArtifact`, `EffectiveArtifactSuggestion`
  and `EffectiveArtifactRead` have no cycle-3 diff.
  - Nit: `EffectiveProfileRead.Facts.suggestion` is now typed `Optional<EdgeWords>`, so in the application layer one type
    carries two facts. A dedicated record, or a javadoc line, would keep "one name for one fact".
- **Wording nit.** `schemas/EffectiveGovernanceEntry.yaml:25` says "A suggestion sits inline on the entry; there is no
  suggestions list". Now that the `suggestion` member is gone, this reads as if it were still there. Reword it as "a
  suggested artifact is an entry of the list; there is no suggestions list".
- **Fragments.** `2026-11-09-agent-profile-read.md` and `2026-11-09-effective-agent-profile-read.md` each call
  `GovernanceEdgeWords` "the new closed schema". Let WP10 de-duplicate this when it folds the changelog.
- **Description precision.** "The first reach: the fewest hops, then …" holds within a step. Across steps, the earlier
  step wins whatever the hops. The next sentence ("the walk lists an artifact once, at the earliest step") covers this, so
  no change is required.
- **Per-commit green.** ad6280f1 alone fails `spotlessCheck`: the `new Listed<>(…)` at `ActionContextWalk.java:527` is
  split one argument per line. 59a4fcf5 reformats it. Squash it at history cleanup. I did not check out each commit
  separately.
- **`EdgeWords.NONE`** (both slices) is used only by tests and javadoc. It is acceptable as a value constant.
- **Shared files (item 7).** Cycle 3 changes WP04-owned files (PackGovernanceEntry, the pack path, the
  Artifact.agent_profile example, agent-profile-reads.feature, the agent-profile-read fragment, ProfileDtos, ProfileJson,
  PacksProfileSource, GovernanceEntry), WP03-owned files (`:packs` walk) and WP06-owned files (EffectiveArtifactAnswer,
  EffectiveReadService). The ruling covers all of them. WP08 should expect merges on EffectiveReadService.java,
  `_index.yaml` and scenarios/README.md.

## Checked and accepted

- Red runs are recorded in ad6280f1, 59a4fcf5 and ce045748. 2778b32a is test-only and its PIT evidence is in the
  message.
  - The tests bite:
    - GovernanceWalkTest covers every relation, first reach across steps and the parent tie-break.
    - EffectiveReadServiceArtifactTest and ClosureViewSessionArtifactTest cover in force and out of force.
    - The new scenarios cover a worded `requires` entry and a wordless `suggests` entry in force and out of force
      (DIRECTIVE_052/053).
    - The new step `is an empty object` asserts an object with no members.
    - ProfileCorpusTest keys every wire entry's `edge` by via + relation + urn against the graph files for all 31
      profiles, and asserts that some entries carry words.
- `PackGovernanceEntry` has no OmitAbsent mix-in, and `edge` is always set by `ProfileDtos.edge`. On the effective entry
  it is always set too, so it is never null or absent.
- Hexagonal: `profiles.application.EdgeWords` and `activations.application.EffectiveArtifactAnswer.EdgeWords` are owned
  per slice. HexagonalArchitectureTest is green.
- `CHANGELOG.md` and `CharterRefusalCode.yaml` are untouched by every WP07 commit. The only lane change to
  `CharterRefusalCode.yaml` is WP05's fb993298.
- All four cycle-3 commits carry an SSH signature and the trailer.
- Open-packs is at `a2a9379` and clean.

| Item | Result | Note |
|---|---|---|
| 1. Dead code | PASS | |
| 2. Synthetic fixture | PASS | |
| 3. Silent empty return | PASS | |
| 4. FR coverage | PASS | FR-004 is now met as written, on both reads |
| 5. Frozen surface | PASS | |
| 6. Locked decision | PASS | |
| 7. Shared files | NOTED | See above |
| 8. Production fragility | PASS | |

## What I ran

All runs were from lane-g with `JAVA_HOME=/usr/lib/jvm/temurin-25`, `--max-workers=1` and
`-PopenPacksDir=/home/user/spec-kitty-open-packs`. Both were green:

- `:packs:test` (`*GovernanceWalkTest*`, `*ActionContext*`, `*ProfileGovernance*`, `*ClosureView*`), `:packs:spotlessCheck`,
  `:activations:test`, `:activations:spotlessCheck`, `:profiles-edge:check`, `:contracts:check`, and `:deployable:test`
  (`*CharterContractSuite*`, `*NullOmissionTest*`, `*HexagonalArchitectureTest*`, `*ActionContext*`,
  `*CharterRuntimeHintsTest*`, `*ProfileProblemParityTest*`, `*ActivationRoutingTest*`), plus
  `:deployable:openPacksProfiles --rerun`. Results: 102, 854, 116 and 786 tests, plus 4; 0 failures.
- `:activations:test --rerun :profiles-edge:test --rerun`.

I also used:

- a Python check of every `edge` in both examples against `packs/built-in/*.graph.yaml` at `a2a9379`;
- the existing `service/packs/build/reports/pitest/mutations.xml` (82 mutants: 73 killed, 1 timed out, 2 survived and 6
  with no coverage). The survivors are in pre-existing lines, and `suggestsOnly` is killed.
