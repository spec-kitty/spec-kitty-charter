### Breaking

- `getEffectiveActionContext` applies the ownership rule (operator ruling of 2026-11-12, Decision Moment
  `01M774HQ8N5EVYDRTP16MBJCN3`; ADR-0018 D5 and rule E16 as amended). Beside what the action's walk reaches, `artifacts`
  now lists the directives, tactics and paradigms in force that the action's mission type scopes, and those that no
  action and no mission type scopes, each with what ADR-0018's steps 2 to 5 reach from it. One scoped only by other
  actions or other mission types is still absent. An answer that was the action's walk alone now lists more for the
  same activation, revision and packs: the listing's meaning changes (ADR-0022, 0.x line).
- The items of `EffectiveActionContext.artifacts` are `EffectiveContextArtifact` (new), where they were
  `EffectiveArtifact`: the same members, copied, plus a required `appliesThrough` (`action`, `mission_type`,
  `project`), the part of the rule that lists the item; a suggestion carries the value of the entry whose `suggests` edge
  reached it. The schema is closed (ADR-0022 D4), so a consumer that validates an item against `EffectiveArtifact`
  refuses the new member. `listEffectiveArtifacts` and the other copies of `EffectiveArtifact` do not change.
- Every effective read answers a new `ETag` once: rule E17's recipe moves to `v3` (`listEffectiveArtifacts`,
  `getActivationSkippedReport`, `getEffectiveGraph`, `getEffectiveActionContext`, `getEffectiveArtifact`,
  `getEffectiveAgentProfile` and `getActivation`), because the context changes meaning under an unchanged revision and
  closure fingerprint. A tag kept from before never matches, so the first conditional read after the upgrade answers
  `200`, not `304`.
- Core: nothing while it is not a consumer (epic #186).
