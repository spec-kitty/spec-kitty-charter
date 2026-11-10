---
affected_files: []
cycle_number: 2
mission_slug: agent-profile-reads-01M70ZDM
reproduction_command:
reviewed_at: '2026-11-10T05:41:24Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP07
---

# WP07 review, cycle 2 (reviewer-renata): changes requested

Cycle 1's B1 and B2 are resolved. I checked them against open-packs `a2a9379`, which is unchanged and clean:

- **B1:** the example's 10 entries match `ProfileCorpusTest.javaJennyUnderTheCharterSelectionListsWhatTheContractExampleShows`
  entry by entry. That test passes against the real corpus (`:deployable:openPacksProfiles --rerun`). The edge words match
  `agent_profile.graph.yaml` at the pin, and the summary is accurate.
- **B2:** a `suggests` entry in force now carries its edge words. The new schema is closed and carries `x-derived`.

One new blocker came in with the B2 fix. It is a false statement in the contract, the same kind of defect as B1, and it
is cheap to fix.

## Blocking

**B3: the contract says a `suggests` edge with no `when` or `reason` leaves `suggestion` absent. That holds only for an
entry in force. An entry out of force answers `"suggestion": {}`.**

- **Where the false statement is:**
  - `contracts/charter/schemas/EffectiveGovernanceEntry.yaml:15`: "an edge that states neither leaves suggestion absent";
  - `contracts/charter/changes/2026-11-09-effective-agent-profile-read.md:29`: "an edge that states neither leaves it
    absent".
- **Where the behaviour comes from:**
  - `service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java:435-439`
    (`edgeWords`, the `worded` gate) gives an entry in force with no words no suggestion.
  - An entry out of force goes through the `Suggested` path instead:
    1. `ClosureViewSession.governanceOf` (`ClosureViewSession.java:218-222`) builds a candidate for every `suggests` edge,
       with or without words.
    2. `EffectiveSet.suggested` (`EffectiveSet.java:292`) always answers `Optional.of(new Suggestion(when, reason))`.
    3. `ActivationsEffectiveProfiles.facts(artifact)` → `EffectiveProfileDtos.edgeWords` writes an
       `EffectiveGovernanceSuggestion` with both members omitted, which is `{}`.
- **Why it matters:**
  - Wordless `suggests` edges are common at the pin. Counted from the graph files: 46 of the 89 in `directive.graph.yaml`,
    55 of 62 in `styleguide.graph.yaml`, 20 of 48 in `procedure.graph.yaml`.
  - A client that follows the description will be wrong on half of the `suggests` entries.
  - The read also disagrees with itself: on one list, a wordless `suggests` entry has no member in force and `{}` out of
    force.
  - It also disagrees with its pack-scoped sibling. `PackGovernanceEntry.suggestion` is present exactly on `suggests`
    entries and is `{}` when the edge states neither (`PackGovernanceSuggestion.yaml`, and the invariant in
    `GovernanceEntry.java:122`). `EffectiveArtifactSuggestion` is `{}` in the same case.
- **Required fix:** pick one.
  - **(a), preferred:** drop the `worded` condition in `EffectiveReadService.edgeWords`. Every `SUGGESTS`-step entry then
    carries a suggestion, `{}` when the edge states neither. The rule becomes "present exactly on a `suggests` entry", as
    on the pack-scoped read.
    - Flip `EffectiveReadServiceArtifactTest.aSuggestsEntryInForceWhoseEdgeStatesNeitherWordCarriesNoSuggestion` to
      assert an empty suggestion. Write it red first and record the red run.
    - Reword `EffectiveGovernanceEntry.yaml:12-17`, `EffectiveGovernanceSuggestion.yaml:3-5`, the path description at
      `paths/activations_activationId_artifacts_agent_profile_artifactId.yaml:22-24`, and the fragment at `:26-29`.
    - Update the javadoc on `EffectiveArtifactAnswer.Entry` (`EffectiveArtifactAnswer.java:66-68`).
  - **(b):** keep the gate and correct the text in the two places above to state the asymmetry: an entry in force whose
    edge states neither word has no `suggestion`; one out of force has an empty object. Add a test that pins the `{}` for
    an out-of-force wordless suggestion through `EffectiveProfileDtos`.

## Not blocking, recorded for the operator and mission review

- **FR-004 and `requires` edge words.** FR-004 says each entry carries "`when` and `reason` where the edge has them". At
  the pin, 71 `requires` edges in `agent_profile.graph.yaml` carry words; java-jenny alone has DIRECTIVE_030, DIRECTIVE_034,
  the tactics and java-conventions. Neither read exposes them:
  - the pack-scoped read: `PackGovernanceEntry.suggestion` is "null on every entry whose relation is not suggests";
  - the effective read: neither does it.

  The spec's edge case (spec.md:102) and WP04's approved contract read FR-004 as being about `suggests` edges, so WP07 is
  consistent with the mission. A literal reading of FR-004 is not met on either read. The operator should rule on it: a
  follow-up, or a narrowed FR-004 wording. Exposing the words would need a new member, because "suggestion" is the wrong
  name for them; that change is additive.
- **Shared files (checklist item 7).** Cycle 2 changes these files, which WP06 owns:
  - `EffectiveArtifactAnswer.java` and `EffectiveReadService.java` (`:activations`);
  - `EffectiveReadServiceArtifactTest`.

  It also changes `ProfileJson.java`, which WP04 owns. The orchestrator decision on B2 covers these changes. WP08 branches
  from WP06, so it should expect a merge on `EffectiveReadService.java` (the `governed(...)` hunk) and on `_index.yaml`.
- **Red run for b79c4c3f.** The commit records none. It changes the example (documentation) and adds a characterization
  test, which "passed first time". I accept this, since the commit changes no production code.
- **Nit.** The fragment's `### Added` bullet on the C6 tag deviation cites a plan-fold term (C6). It is harmless.
  Consider wording it for API readers when WP10 folds the changelog.

## Checked and accepted

- `EffectiveGovernanceSuggestion.yaml`: `additionalProperties: false`, with `x-derived` on the schema and on both members.
  It is listed in `_index.yaml`.
- `EffectiveArtifact.yaml` and `EffectiveArtifactSuggestion.yaml` are unchanged, so the generic reads keep their meaning
  (#408 ruling 3).
- The fragment is still one `### Added` block, and every change is additive.
- `CHANGELOG.md` and `CharterRefusalCode.yaml` are untouched by every WP07 commit. The only change to `CharterRefusalCode.yaml`
  on the lane is WP05's fb993298.
- The tests bite:
  - `EffectiveReadServiceArtifactTest` covers both words, neither word, one word, and a `requires` entry with words.
  - `ActivationsEffectiveProfilesTest` and `EffectiveProfileDtosTest` cover the mapping and the wire.
  - The DIRECTIVE_041 scenario line asserts the words.
  - The corpus test pins every entry against the real pack.
- Anti-pattern checklist:

  | Item | Result | Note |
  |---|---|---|
  | 1. Dead code | PASS | |
  | 2. Synthetic fixture | PASS | |
  | 3. Silent empty return | PASS | `Optional.empty()` is documented |
  | 4. FR coverage | PASS | See the FR-004 note above |
  | 5. Frozen surface | PASS | |
  | 6. Locked decision | PASS | |
  | 7. Shared files | NOTED | Coordination note above |
  | 8. Production fragility | PASS | |

## What I ran

All runs were from lane-g with `JAVA_HOME=/usr/lib/jvm/temurin-25`, `--max-workers=1` and
`-PopenPacksDir=/home/user/spec-kitty-open-packs`. Both were green:

- `:activations:test :profiles-edge:test :contracts:check :deployable:test` with `--tests` filters `*CharterContractSuite*`,
  `*NullOmissionTest*`, `*HexagonalArchitectureTest*`, `*CharterRuntimeHintsTest*`, `*ProfileProblemParityTest*` and
  `*ActivationRoutingTest*`, plus `:deployable:openPacksProfiles`;
- `:deployable:openPacksProfiles --rerun`.
