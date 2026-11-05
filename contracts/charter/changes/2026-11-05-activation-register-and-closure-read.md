### Breaking

- The activation register is built, and an activation's packs get a read of their own (#125, #360; ADR-0020 D3–D6,
  ADR-0059 D4–D5). `createActivation`, `getActivation`, `replaceActivation` and `deleteActivation` lose `x-reserved`
  and their `501`, so the activation set reserves nine operations, not thirteen. `Activation` loses two required
  members, `summary` and `governanceFingerprint`: its `ETag` is the quoted `revision` and its bytes depend only on the
  stored document, so a stale document (an id the offering no longer has, a deny entry on an id now required or
  locked) is served as stored and refused on write (`422`, `409`). `summary` moves to `EffectiveArtifactList`, which
  requires it. `getActivation` and the four effective reads take `If-None-Match` and answer a new success status,
  `304`, without a body (ADR-0020 D8 lists a new success status as breaking). The new `listActivationPacks`
  (`GET /activations/{activationId}/packs`) answers an `ActivationPackList`: the `depends_on` closure rooted at the
  document's `packs` (every configured pack when absent), in dependency order, each item an `OfferingPack` with its
  `ResolvedDependency` facts, with an `ETag` over the revision and the closure's packs, `304`, `404
  activation_not_found` and the offering's `500` composition codes (`pack_reference_pack_missing` among them). A
  `replaceActivation` whose `If-None-Match` is not `*` is `400`. Absent means none (ADR-0053 d10): an absent
  `activatedKinds`, `missionTypes` or `selected.<kind>`, `skill` included, puts nothing in force, like `[]`, and the
  two stay distinct on the wire; an absent `packs` means every configured pack, and its text drops "a later pack
  wins". Core's seam: stop reading `summary` and `governanceFingerprint` from an `Activation` (read the counts from
  `EffectiveArtifactList.summary`), treat `304` on these reads as "unchanged", and stop relying on an absent key's
  per-kind default. For #359, `PackPin` is unchanged; a pinned pack's dependency facts are on the closure read. The
  four activation refusal codes, `activation_not_found`, `activation_invalid`, `artifact_required` and
  `artifact_locked`, become raised by an operation in this change (their `raised` flags are set with the code that
  raises them).
