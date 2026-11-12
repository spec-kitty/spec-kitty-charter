# Research: the in-force rulings of #439 and #447

Both joined slice C on 2026-11-12 (operator, relayed by the orchestrator, after the triage session offered them).

**Ruled (2026-11-12): #439 option (b), #447 option 1, both `### Breaking`.** Built as WP10 and WP11 (spec FR-013, FR-014).

## #439: which overrider `satisfiedBy` and `overriddenBy` name

**Where it applies.** `InForceResolution.overriderOf` picks the smallest URN when several overriders in force satisfy or
hide one target (E9). C14 refuses two packs **at one level** that both override one artifact, so a tie arises when:

- overriders at **different levels** both override the target, each through its own edge; or
- **one pack** authors two artifacts that override the target (C14 counts packs, not artifacts).

**What the model says.**

- Every overrider in force satisfies or hides the target (E4, E5). The state and the reason do not depend on which one
  is named, so the choice is a reporting artifact, not a winner.
- The operator ruling cited at C15 says a contest, winner or tie is never settled by accidental order; it is refused or
  ruled.

**Options.**

- **(a) Rule an order explicitly.** For example the highest level first, then the smallest URN. This mirrors layer
  precedence and ADR-0066 d5, which puts the project nearest. Record it on E5 and E9 and pin it. The cost is the
  smallest (no shape change), but the answer still hides the other overriders.
- **(b) List every overrider in force** (recommended). `satisfiedBy` and `overriddenBy` become sorted lists, by
  `(level, URN)`, in `EffectiveArtifact`, `EffectiveContextArtifact` and the skipped report's `overridden`. This is
  `### Breaking` on the 0.x line, and core is not yet a consumer. It is the truthful answer: no order chooses
  anything, and a client sees every artifact that stands in for the target.
- **(c) Refuse the read** when two overriders in force reach one target. This is harsh: the state is unambiguous, and
  a refusal would break every effective read of the activation over a naming question.

**Recommendation: (b).** If the operator prefers no shape change, then (a) with level-then-URN.

## #447: an `overrides` edge from a non-activatable kind

**Where it applies.** An `overrides` edge whose source is a template, an asset, a guard or a lifecycle (kinds outside
`Kₐ`).

- Inside packs, a lock can put such a source in force, so the target reads `overridden` with
  `satisfiedBy = overriddenBy = <template>`.
- The activations side drops non-activatable nodes from those carriers (D16). The read therefore says
  "overridden by nobody", and the IC-13 write finds no satisfier and refuses a deny entry with
  `409 artifact_required`.

**What the model says.**

- E4 and E5 work on the candidate set `Cand(D) ∪ Lk` of activatable kinds. The floor and the carriers hold activatable
  kinds only (C31, D16).
- An `overrides` edge expresses that one governance artifact stands in for another (C27). A template, an asset, a
  guard or a lifecycle has no activation state and cannot stand in for governance.
- Evidence: open-packs `a6cfeab` authors no `overrides` edge at all, so refusing it breaks no real pack.

**Options.**

1. **Refuse at registration** (recommended): an `overrides` edge whose source kind is not activatable. Refuse it at
   registration, on `putPackRelationship`, and on composing reads that meet it in a stored pack (`500 pack_unreadable`).
   The refusal is a violation inside `422 validation_failed`, pointing at the graph document, with a violation code
   such as `overrides_source_not_activatable`. This is `### Breaking` (content accepted today is refused). The read and
   the write then never disagree, because the shape cannot exist.
2. **Let it satisfy on the write too.** This needs a carrier beyond `ArtifactReference`, which reopens D16.
3. **Keep and document the asymmetry** as a divergence. This leaves a contradictory answer in place.

**Recommendation: 1.** It removes the disagreement at its source and costs nothing in the open-packs content.
