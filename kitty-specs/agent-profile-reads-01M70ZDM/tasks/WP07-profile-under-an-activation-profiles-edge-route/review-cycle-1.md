---
affected_files: []
cycle_number: 1
mission_slug: agent-profile-reads-01M70ZDM
reproduction_command:
reviewed_at: '2026-11-10T04:58:39Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP07
---

# WP07 review, cycle 1 (reviewer-renata): changes requested

The code, the routing flip, the problem parity, the hints and the ArchUnit rules are sound. The targeted Gradle run
is green, and the nine `@contract` scenarios run against the whole service. Two contract-documentation defects
block approval. Both are in WP07-owned files and are cheap to fix. No WP06 code change is required in this cycle.

## Blocking

**B1: the example misstates the corpus it says it comes from.**
`contracts/charter/examples/EffectiveAgentProfileRead.java-jenny.yaml:1-5` and `:103-120`.

- **What is wrong:**
  - The summary says the activation's selection "is this repository's charter.yaml".
  - The example lists `directive:DIRECTIVE_024` as a `suggests` entry and gives it edge words ("when keeping the
    change local matters", ...). At the pinned open-packs (`a2a9379`), `agent_profile.graph.yaml:417-419` has
    java-jenny **require** DIRECTIVE_024, with no `when` or `reason`. `charter.yaml` does not select DIRECTIVE_024,
    so under that activation it is a `requires` target out of force. The walk would not list it at all.
  - `self` is `?depth=1`. At depth 1 under `charter.yaml`, `DIRECTIVE_041` (selected) would be listed as a
    `suggests` entry in force. The governance list also leaves out the other required targets in force
    (DIRECTIVE_025, 030, 034 and 051), and it does not say that it is abridged.
  - The summary calls DIRECTIVE_010 and java-conventions "required", but both entries have `required: false`. The
    summary means the relation, not the field.
- **Why it matters:** this is the published contract example (NFR-002). It is also what WP09's usability run and
  WP10's docs will be compared with. A made-up edge, presented as coming from the real corpus, misleads clients.
- **Required fix:** pick one of these.
  - (a) Make the example true to `a2a9379` and `charter.yaml` plus `agent_profile: java-jenny`:
    - requires entries in force;
    - `DIRECTIVE_041` as a `suggests` entry in force;
    - a real out-of-force suggestion with its real edge words, for example `directive:DISCIPLINED_REFACTORING` or
      `directive:USE_MUTATION_TESTING_TO_VALIDATE_TEST_QUALITY`;
    - `implementer-ivan` absent, because it is not selected (W1).
    - If you shorten the list, say "abridged" in the summary.
  - (b) Drop the provenance claim and use plainly fictional ids.

  Also fix the word "required" in the summary.

**B2: under an activation, a `suggests` entry in force has no `when` or `reason`, and the divergence from FR-004 is
not recorded.**

- **What is wrong:** FR-004 is in WP07's `requirement_refs`. It says each entry carries "`when` and `reason` where the
  edge has them". The spec's edge case (spec.md, "A `suggests` edge that carries `when` and `reason`. Both appear on the
  entry.") says the same. Under the activation, a `suggests` entry in force (DIRECTIVE_041 in
  `effective-agent-profile.feature:205`) has no `suggestion`.
- **Why this is not an implementation bug:**
  - Plan fold C1 says the suggestion sits inline "as in the action context".
  - `EffectiveArtifactSuggestion` (#408 ruling 3) is defined only for an artifact with reason `not_selected`.
  - WP06's `EffectiveArtifactAnswer.Entry` does not carry the edge words.
  - Adding them later is additive: a new optional member, or a widened definition.
  - So I do not require a change to WP06's `governed(...)` now.
- **The defect:** the contract never says this. Readers of these files would expect the edge words on DIRECTIVE_041,
  and nothing tells them otherwise:
  - `schemas/EffectiveGovernanceEntry.yaml:9-16`;
  - the path description, `paths/activations_activationId_artifacts_agent_profile_artifactId.yaml:21`;
  - the fragment, `changes/2026-11-09-effective-agent-profile-read.md:14`;
  - the scenario text at `effective-agent-profile.feature:205`.

  RECONCILE_CHANGE_SCOPE_TENSIONS requires the tension to be stated.
- **Required fix:**
  1. State it in the `EffectiveGovernanceEntry` description, the path description and the fragment. Suggested
     wording: "a `suggests` entry in force carries no `suggestion`: under an activation the suggestion is the
     effective set's (#408 ruling 3), present only on an artifact out of force; the pack-scoped read carries the edge
     words on every `suggests` entry".
  2. Say the same in the scenario's description text at `:205`. The `effective governance` line for DIRECTIVE_041
     already pins the behaviour.
  3. Record the divergence from FR-004 for the operator: a line in `research.md` D4, beside divergences 1 and 2, or a
     note for WP10 to fold. Note that the follow-up option is to carry the edge words on `EffectiveArtifactAnswer.Entry`
     (WP06) and add an optional member to the entry.

## Checked and accepted (no change needed)

- **Tag `EffectiveAgentProfiles` and the separate controller.**
  - This is consistent with `useTags=true` (`build-logic/.../charter.contract-api.gradle.kts:45`). The generated
    `EffectiveAgentProfilesApi` is implemented by `EffectiveAgentProfilesController`.
  - The deviation from fold C6 is set by the WP07 prompt. Its one-line rationale is in `openapi.yaml`, and the commit
    message records it.
- **`EffectiveReadService.listedKinds()`** passes through the `AvailablePacks.listedKinds()` port. It is not a second
  source of truth, and it keeps rule (d) intact: the adapter names only `activations.application` and
  `activations.domain`.
- **The ActivationRoutingTest flip, ProfileProblemParityTest and the hints.** The ActivationRoutingTest flip is real.
  ProfileProblemParityTest compares the bodies of the generic route and the profile route byte for byte, and its red
  run is recorded. The `ProfileJson.Hints` extension adds nothing twice.
- **Frozen files.** `CharterRefusalCode.yaml` and `CHANGELOG.md` are untouched.
- **Red runs.** Every commit records its red run.
