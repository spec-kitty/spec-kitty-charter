### Breaking

- A tension is a relation of its own, `tension`, and it is answered where the graph is (#602, ADR-0045, rulings G1 to
  G3). Each item names what core's seam regenerates or changes:
  - `Relation` gains `tension`, so every schema that carries it does: `EffectiveGraphEdge`, `ReferencedArtifact`, the
    governance entries and `RelationRow` of `getKindTaxonomy`, whose tension row is now `tension` (it was the
    `in_tension_with` row, held as a `Tension`). Regenerate the client's `Relation` enum.
  - The pack schemas no longer read `in_tension_with`: a pack file that writes it is refused at load (unknown
    relation), and `putPackRelationship` refuses it `422 validation_failed` with `relationship_type_unknown`. Packs
    write `tension` (spec-kitty-open-packs #316).
  - A pack whose graph documents write a tension twice, against its stored orientation, or from an artifact to itself
    is refused at load with `tension_duplicated`, `tension_orientation` or `tension_self` (new codes of
    `validation_failed`). The stored orientation: the side the pack holds is the source; when it holds both, or
    neither, the smaller bare URN.
  - `EffectiveGraphEdge` gains `when`, `reason` and `reconciledBy` on a tension edge, and only there; a tension edge
    always carries `reconciledBy`. The graph's tension edges are the tensions in force, each once, from the smaller
    URN.
  - `EffectiveActionContext` gains the required `tensions`: the tensions in force whose two sides the context lists in
    force. A client with a closed schema reads the new member.
  - The effective-read entity tag's domain moves to `v4`: every effective read's tag changes once for unchanged inputs,
    so a stored `If-None-Match` from before answers `200`.
  - The `governanceFingerprint` text of `EffectiveGraph` and `EffectiveActionContext` now says what both reads answered
    already, the closure digest (not `ServiceInfo.sourceFingerprint`); no value changes.
