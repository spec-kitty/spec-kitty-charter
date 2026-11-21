### Breaking

- The pack-name part of a pack-qualified reference is a pack id (#582, ADR-0075 decision 2): a kebab slug, lower-case
  letters and digits in hyphen-separated runs, at most 63 characters. Before, it was `[a-z0-9][a-z0-9_-]*`, so a
  reference to a pack no pack id can name was accepted and then failed as unresolved. No field changes shape; each
  item names what core's seam regenerates or changes.
  - `PackQualifiedReference`, `PackQualifiedReferenceWrite` and the `seeAlso` items of `GlossaryTerm`,
    `GlossaryContextTerm` and `VocabularyTerm` write the qualifier `([a-z0-9]+(-[a-z0-9]+)*::)?`. Core regenerates the
    client's patterns; its validation then refuses a qualifier with `_` or a leading, trailing or doubled hyphen.
  - The URN part of `Urn`, `PackQualifiedReference` and `PackQualifiedReferenceWrite` does not start with a colon
    (`[a-z_]+:[^:\n\r].*`, where it was `[a-z_]+:.+`). Before, `a_b::directive:D1` and
    `directive::x` passed as a bare URN, so the qualifier grammar alone could not refuse them. Core regenerates the
    patterns.
  - The charter contract writes no lookahead, so no pattern caps the qualifier inside a reference; the descriptions
    say so. The service refuses a pack name of more than 63 characters: `putPackRelationship` answers
    `422 validation_failed`, as for any other malformed reference, and a pack that writes one is refused at load. Core
    sends no such name.
  - The descriptor name, wherever an answer or a request carries it, is shaped as a pack id: `^[a-z0-9]+(-[a-z0-9]+)*$`,
    1 to 63 characters. That is `PackRegistration.name` (which no longer refers to `Slug`), `RefusalPack.name`,
    `HoldingPack.name`, `OfferingPack.name` and `EffectivePromptFragment.packName`; `RequiringPack.packId` and its `via`
    items, which are pack ids, take the same pattern. They were `^[a-z0-9][a-z0-9_-]*$` with at most 64, or
    unconstrained. Core regenerates the client; the service never answers another name, since the descriptor schema
    holds the same pattern.
  - A pack stored before this change is refused until it is registered again with pack ids. One whose graph documents
    hold a qualifier that is no pack id is refused by the reads that judge its graph (`500 pack_unreadable`): the edge
    read and a removal name the file and the reason, the composing read does not yet (#628), and the lenient load
    leaves the document out. One registered by archive whose descriptor `name` is no pack id is `500 pack_unreadable`
    on every read of its descriptor. Core re-registers such a pack; the spec-kitty-open-packs packs hold none.
    A pack stored with such a qualifier is refused on read, single-relationship and single-artifact removal included;
    it is recovered by uninstalling the pack, or by registering it again with pack ids.
