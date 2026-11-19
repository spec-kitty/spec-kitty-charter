### Breaking

- A pack is removed whole, its writes have a dry run, and a replacement moves its version forward (#517, ADR-0081,
  amending ADR-0065 D2, D4 and D9):
  - **The version order.** `registerPackFromArchive` and `registerPackFromSource` refuse a replacement whose
    `pack_version` is not newer than the stored pack's by PEP 440 precedence, the same version included, with
    `409 pack_version_conflict` (the problem of a refused status); before, any version replaced, an older one included.
    Sending the stored content is still `unchanged`. `PackRegistration` gains `previousVersion`, the version a replace
    moved from. The version history matches labels by PEP 440 equality, so `1.1` and `1.1.0` are one label.
  - **`deletePack`.** `DELETE /packs/{packId}` removes a registered org or project pack whole and answers `204`. It
    requires `If-Match` (`428`, `412`, `*` accepted), answers `404 pack_not_found` for a pack that is not there,
    `409 registration_conflict` for a built-in, read-only or linked pack, the new `409 pack_has_dependents` while a pack
    (`depends_on`, `parent_pack`), an activation that names it, or another pack's artifact depends on it, and `422`
    when a closure that holds it no longer composes without it. It keeps the version history.
  - **Dry runs.** `?dryRun=true` (`parameters/DryRun.yaml`) on `deletePack`, `registerPackFromArchive` and
    `registerPackFromSource` judges the write and writes nothing. `deletePack` answers `200` with the new `PackImpact`;
    a registration's job ends with `dryRun: true` in its result.
- Core's seam: a client that registered an older `pack_version` over a newer one now reads `409`; publish the content
  under a newer version, or delete the pack first. A client that removes a pack calls `deletePack`; one that wants to
  know first sends `dryRun=true`.
