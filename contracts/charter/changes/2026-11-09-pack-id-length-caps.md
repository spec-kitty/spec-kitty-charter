### Breaking

- The pack schemas cap the length of every id they give a grammar (#440; operator decisions
  `DM-01M706DW5XHBA4781BYHVDFNT5`, `DM-01M706DYNJRHY13DDN5C269JTT` and `DM-01M706E15NPBQMX2AGC88F65NF`). A pack file
  whose id is longer is a schema error, so the pack is refused as `500 pack_unreadable` on read, as for any other
  schema error. The caps:
  - **64 characters**: the `id` of a guard, lifecycle, procedure, toolguide, mission type, mission step, step
    enhancement, styleguide, tactic, paradigm and directive; a lifecycle state; and the pack descriptor's `name`. A
    preset id and a skill id were already capped at 64.
  - **136 characters**: a lifecycle guard entry's `ref` (`<pack>::guard:<id>`): two 64-character parts and the
    `::guard:` separator, the same ceiling the domain's guard reference applies.
  - **255 characters**: an executable's `entry` and an expected input or output artifact's `artifact_key`.
- The domain applies the same caps before it matches an id. Every pattern it matches with is possessive, so an id
  of any length is judged without overflowing the stack. Before this change, an id of about 10 000 characters threw
  `StackOverflowError`, which answered a bare `500` with no refusal code.
- No real pack is affected: the longest id in spec-kitty-open-packs at the pin `22c14b2` is 55 characters
  (`refactoring-replace-magic-number-with-symbolic-constant`).
