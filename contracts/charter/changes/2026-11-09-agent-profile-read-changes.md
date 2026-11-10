### Changed

- `agent_profile` is a served kind of the generic pack-artifact surface (#407, #96). Three wordings change, and every
  change is additive.
  - **The served kinds.** The pack's artifact list (`listPackArtifacts`), its `?kind=` filter, the references read
    (`listArtifactReferences`) and `deletePackArtifact` serve `agent_profile` beside tactic, directive, styleguide, paradigm
    and toolguide. The descriptions that listed five kinds (`ArtifactKindToken`, the `kind` path parameter,
    `ArtifactSummary.kind`, the item, list, references and delete paths, and `kind_not_found` in `CharterRefusalCode`) now
    name six. The templated item read `getPackArtifact` never carries a profile body: the literal operation
    `getPackAgentProfile` answers `agent_profile`, and an item link to a profile is that read. A list that held no
    profile lists the same items as before; a pack with profiles lists them last, after its toolguides, and a client
    already skips a kind it does not know.
  - **`artifact_not_found` on the pack path.** A profile the pack does not hold is `404 artifact_not_found`, on
    `getPackAgentProfile`, `listArtifactReferences` and `deletePackArtifact`. The rule from here on: a kind served after
    the first five uses `artifact_not_found`, not a code of its own. The five keep `tactic_not_found` and the rest. The
    code was already 404, and the activation set already raised it; `CharterRefusalCode` states the wider scope in its
    description and adds no value.
  - **The profile's `ETag`.** `getPackAgentProfile` answers the pack revision as its `ETag`, as a directive's read does,
    and `deletePackArtifact` of a profile takes it as `If-Match` (428 without it, 412 when stale) and answers 204 with the
    revision after the removal. A profile is refused as a directive is: 409 `artifact_referenced` while another artifact,
    pack, preset or override still refers to it, `governance_policy_violation` when an ancestor of the pack locks it,
    `registration_conflict` when the pack's author-written graph documents name it, and 422 when the closure it leaves
    does not compose.
