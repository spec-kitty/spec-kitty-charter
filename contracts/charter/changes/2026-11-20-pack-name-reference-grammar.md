### Breaking

- The pack-name part of a pack-qualified reference is a pack id (#582, ADR-0075 decision 2): a kebab slug, lower-case
  letters and digits in hyphen-separated runs, at most 63 characters, the id a pack is registered under. Before, it
  was `[a-z0-9][a-z0-9_-]*`, so a reference to a pack no pack id can name was accepted and then failed as unresolved.
  Each item names what core's seam regenerates or changes; no field changes shape.
  - `PackQualifiedReference`, `PackQualifiedReferenceWrite` and the `seeAlso` items of `GlossaryTerm`,
    `GlossaryContextTerm` and `VocabularyTerm` write the qualifier `([a-z0-9]+(-[a-z0-9]+)*::)?`. Regenerate the
    client's patterns.
  - A pattern cannot cap one part of a longer string without lookahead, and the contract carries none, so the schemas
    do not cap the qualifier; their descriptions say so. The service refuses a pack name of more than 63 characters:
    `putPackRelationship` answers `422 validation_failed`, as for any other malformed reference, and a pack that writes
    one is refused at load.
  - `PackRegistration.name` and `RequiringPack.packId` and `via` items are pack ids: `^[a-z0-9]+(-[a-z0-9]+)*$`, 1 to
    63 characters, where they were `^[a-z0-9][a-z0-9_-]*$` with at most 64. `PackRegistration.name` no longer refers to
    `Slug`.
