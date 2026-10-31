# Mission steps: retrospect prompts are empty, one retrospect action index has no step, research accept has no prompt

Filed as spec-kitty/spec-kitty-open-packs#98. Refs spec-kitty/spec-kitty-charter#194, charter ADR-0044.

- `missions/mission-steps/documentation/retrospect/prompt.md` and `.../research/retrospect/prompt.md` are empty: the
  `retrospect` on-demand steps were migrated without a prompt.
- `missions/software-dev/actions/retrospect/index.yaml` belongs to no step (software-dev has no retrospect step): its
  references were not carried.
- research's runtime `accept` step has no step directory and no prompt anywhere: migrated with no prompt.

Ask: author the missing prompts, and decide whether software-dev gets a `retrospect` on-demand step carrying those
references.
