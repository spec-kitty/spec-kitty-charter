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
  WP22. The full `:deployable:test` boots the context through the new constructor.
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
