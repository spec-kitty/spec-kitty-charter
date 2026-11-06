### Changed

- `applyActivationPreset` (`PUT /activations/{activationId}/presets/{presetId}`) is served (ADR-0059 D6, ADR-0020 D6,
  FR-016) and loses `x-reserved` and its `501`. Each key the preset lists replaces that key of the document: each
  `selected.<kind>` it lists becomes exactly its members, and `activatedKinds` and `missionTypes` are replaced when it
  lists them; a key it does not list is left as it is. A preset lists a key when its file holds at least one member
  for it, so an empty list counts as not listed (the packs read an absent list and an empty one alike). Without
  `force` a member the document denies is not selected and its deny entry stays; with `force: true` the deny entries
  of the preset's members are cleared and reported in `cleared`. `applied` lists every id the write added to or
  removed from `selected`, `deactivated` or `missionTypes`, so a replaced key's dropped ids are listed too; a kind is
  never an entry. The preset is resolved in the activation's own closure (the last of two packs holding one preset id
  in merge order wins): a preset no pack of it holds, one held only outside it included, is `404 preset_not_found`,
  which becomes `raised: true` in `x-refusal`. The operation's `404` is the new `ActivationPresetNotFound` response,
  which names only `activation_not_found` and `preset_not_found`. A preset member the closure does not hold (a preset a pack upgrade left
  stale) is `422 activation_invalid` with an `unknown_artifact` violation at the whole request (pointer `""`), since
  the write has no body list to point into; nothing is stored. The write requires `If-Match` (`428`, `412`) and
  answers an `ActivationChange` with the new revision as its `ETag`; a write that changes nothing answers `200` with
  the same revision.
- This closes contract B: only the four effective reads (`listEffectiveArtifacts`, `getActivationSkippedReport`,
  `getEffectiveGraph`, `getEffectiveActionContext`) stay reserved, and none of them takes a request body, so the
  scenario that pinned the `501` before an unbindable body is bound is retired (a test now fails if a reserved
  operation declares a `requestBody`).
- Deferred (R5): `ActivationChange.skipped` reports the write-time skips only; what the read-time `enhances` pull
  leaves out is reported by the effective reads, which the effective-reads mission builds.
