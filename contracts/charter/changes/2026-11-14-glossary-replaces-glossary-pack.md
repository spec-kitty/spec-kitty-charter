### Breaking

- The kind `glossary_pack` is replaced outright by `glossary`, in the shape of ADR-0049 (decisions 1 to 6, amended by
  ADR-0053 decisions 11 to 14; #106). There is no alias, no reader of the old shape and no upgrade (the charter's model
  is canonical). Allowed on the 0.x line (ADR-0022).
  - Renamed members and values: `ActivatableKind` and `CascadeKind` replace `glossary_pack` with `glossary`;
    `SelectedArtifacts` and `DeactivatedArtifacts` replace the member `glossary_pack` with `glossary` (glossary context
    ids), and `ActivationConfig.schema.json` and its corpus (`valid/06`, `valid/07`) follow; `Preset.activatedGlossaryPacks`
    becomes `Preset.activatedGlossaries`. `schemaVersion` stays `2`.
  - `NodeKind` replaces `glossary_pack` with two URN kinds: `glossary_context`, the URN token of a context
    (`glossary_context:<id>`), which differs from the kind token `glossary` on purpose (ADR-0053 decision 13), and
    `term` (`term:<context-id>/<term-id>`), an edge endpoint only as the target of a `vocabulary` edge, never a declared node
    (operator ruling 2026-11-14): a graph shard, an org fragment or a registered relationship that names a term as a
    source, or as the target of any other relation, is refused. The `vocabulary` relation targets both.
  - The packs contract: `glossary.schema.json` replaces `glossary-pack.schema.json`, closed at every level
    (`schema_version`, `id`, `name`, `description`, `tags?`, `applies_to?`, `terms`; a term is `id`, `name`,
    `definition`, `examples?`, `aliases?`, `avoid?`, `status`, `see_also?`). Context and term ids are letter-first
    kebab slugs of at most 64 characters; `status` is `draft`, `active` or `deprecated`; a `see_also` entry is a term reference, bare
    (`term:<context-id>/<term-id>`, a term of the same pack) or pack-qualified
    (`<pack>::term:<context-id>/<term-id>`, a term another pack holds), judged by the one reference rule: a bare entry
    that only another pack holds is `pack_reference_unqualified`, and a qualified one the named pack does not hold is
    `pack_reference_artifact_missing` (operator ruling 2026-11-14).
    `confidence`, `introduced_in_mission` and `provenance` are gone, `surface` is renamed `name`, and
    `synonyms_to_avoid` and `banned_synonyms` fold into `avoid`. The file family is `glossaries/<id>.glossary.yaml` (fragment plural
    `glossaries`), the id is the file stem, and a context elsewhere is refused by placement. `file-kinds.yaml`, the
    graph shard and org fragment URN alternations, and the `preset` (`activated_glossaries`), `charter`
    (`selected_glossaries`, `activated_glossaries`), `pack-manifest`, `policy` (`kind_defaults.glossary`; an
    `artifacts` key is `glossary_context:<id>`) and `mission-step-contract` schemas follow.
  - A write (`createActivation`, `replaceActivation`) that sends `selected.glossary_pack` or
    `deactivated.glossary_pack` is `422 activation_invalid` with an `unknown_member` violation at its pointer, as for
    any undeclared member.
  - A stored activation that holds `selected.glossary_pack` or `deactivated.glossary_pack` is refused as an invalid
    stored document, with no upgrade (ruling `DM-01M7B1Q1HW2KDEEW3MKCK2A2PT`).
  - A pack file in the old shape (`*.glossary-pack.yaml`) is refused by name as a retired file, in the load, in
    validate mode and at registration, with the reason that the shape is replaced by `glossaries/<id>.glossary.yaml`.
    A preset that names `activated_glossary_packs` makes its pack unreadable.
  - A generated-client caller regenerates: `ActivatableKind.GLOSSARY_PACK` becomes `GLOSSARY`, the
    `glossaryPack` members of `SelectedArtifacts` and `DeactivatedArtifacts` become `glossary`,
    `Preset.getActivatedGlossaryPacks()` becomes `getActivatedGlossaries()`, and `NodeKind` gains `GLOSSARY_CONTEXT`
    and `TERM` in place of `GLOSSARY_PACK`. A client that selects glossary packs selects glossary contexts by id under
    `selected.glossary`.
  - Pack content follows in `spec-kitty-open-packs`, which migrates its glossaries to contexts with the minted term
    ids. Until then a pack in the old shape is refused.
