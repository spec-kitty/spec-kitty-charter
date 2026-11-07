### Breaking

- Removed members (issue #240, ADR-0064): a relationship is authored only in a pack's graph documents and written
  only through `putPackRelationship`, so no artifact shape carries one any more.
  - Registration bodies: `DirectiveRegistration` loses `procedures`, `references` and `relationships`; `ParadigmRegistration` loses
    `directiveRefs`, `references` and `relationships`; `StyleguideRegistration` and `ToolguideRegistration` lose
    `references`.
  - Read shapes: `Directive` loses `procedures`, `references` and `relationships`; `Paradigm` loses `directiveRefs`, `references`
    and `relationships`; `Styleguide` loses `references` and `relationships`; `Tactic`, `TacticStep` and `Toolguide`
    lose `references`. The item schemas `DirectiveReference`, `ParadigmReference` and `TacticReference` are deleted.
  - A directive's steps are procedure artifacts, reached through a graph edge from the directive, not a list of
    prose steps on it.
  - Added refusal: a registration body (directive, paradigm, styleguide, toolguide, relationship) that sends a member
    its schema does not declare is refused 422 `validation_failed` (existing code) naming the member, before the body
    is read, so a client still sending `relationships` is never answered as though the edges were written.
  - `listArtifactReferences` answers the edges of the pack's graph documents whose source is the artifact, each with
    its edge's relation, then the artifact's derived relations (a guard's `implemented_by`). A target is listed once
    per relation, and `ReferencedArtifact.relation` is now required: every reference has one.
  - Scenarios: `directives.feature`, `paradigms.feature` (a registered artifact has no relationship field; a
    relationship registered through the relationship write is read through the references; an outline refusing each
    removed member), `styleguides.feature`, `toolguides.feature`, `artifacts.feature`, `composition.feature` (the
    cross-pack references now come from a graph shard) and journey 06.

  Breaking on the unreleased 0.2.0-SNAPSHOT line, which ADR-0022 allows; no release carried these members. Core's
  seam: none until the service is incorporated into core; the pack schemas under `contracts/packs/schemas` drop the
  same fields (a directive's `procedures` among them), and an org fragment's edge gains `when`.
