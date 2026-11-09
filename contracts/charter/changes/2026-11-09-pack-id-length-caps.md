### Breaking

- The pack schemas cap the length of these ids (#440; operator decisions `DM-01M706DW5XHBA4781BYHVDFNT5`,
  `DM-01M706DYNJRHY13DDN5C269JTT` and `DM-01M706E15NPBQMX2AGC88F65NF`). A pack file whose id is longer is a schema
  error, so the pack is refused as `500 pack_unreadable` on read, as for any other schema error. The caps:
  - **64 characters**: the `id` of a guard, lifecycle, procedure, toolguide, mission type, mission step, step
    enhancement, styleguide, tactic, paradigm and directive; a lifecycle state's `id`; and the pack descriptor's
    `name`. A preset id and a skill id were already capped at 64.
  - **136 characters**: a lifecycle guard entry's `ref` (`<pack>::guard:<id>`): two 64-character parts and the
    `::guard:` separator, the same ceiling the domain's guard reference applies.
  - **255 characters**: an executable's `entry` (asset.schema.json) and an expected input or output artifact's
    `artifact_key`.
- Not capped: a preset item id, a lifecycle transition `event`, the `role` of a step or procedure, an agent profile's
  `roles`, the URNs, and the ids of an asset, an agent profile, a glossary pack and a contract. A guard's
  `implemented_by` has no schema cap; the domain caps its `<pack>::` qualifier at 64 characters.
- The domain applies the same caps to the ids it has a type for, before it matches them: the directive, tactic,
  paradigm, styleguide, procedure, toolguide, guard, lifecycle, mission type and mission step ids, a guard reference,
  an executable's entry and an expected artifact's key. A lifecycle state's id, a step enhancement's id and the pack
  descriptor's `name` are capped by the schema only: the domain has no type that checks their length. Every pattern the domain matches with is possessive, so an id of any length is judged without
  overflowing the stack. Before this change, an id of about 10 000 characters threw `StackOverflowError`, which
  answered a bare `500` with no refusal code.
- A write whose artifact id is longer than the cap, such as a directive `PUT` whose path id is 65 characters (the
  `ArtifactId` path parameter allows 128), is refused with `422 validation_failed` and stores nothing. Before, the
  write was accepted and the pack then answered `500 pack_unreadable` on every read.
- Core's seam: nothing; core reads no pack file through this contract.
- No real pack is affected: the longest id in spec-kitty-open-packs at the pin `22c14b2` is 55 characters
  (`refactoring-replace-magic-number-with-symbolic-constant`).
