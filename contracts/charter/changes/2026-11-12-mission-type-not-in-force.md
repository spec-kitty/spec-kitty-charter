### Breaking

- `CharterRefusalCode` gains the value `mission_type_not_in_force` (409, `raised: true`; operator ruling OD-5 of
  2026-11-12, Decision Moment `01M774HSKK0SSA4147QEG9ZCKR`; research R5). `getEffectiveStepPrompt` alone raises it, for
  a mission type the activation's merged closure holds and the activation does not put in force, after
  `activation_not_found`, `mission_type_not_found` and `action_not_found` and before the closure's `500` codes and any
  `304`. The action context of the same action answers `200` with no artifacts, and the two reads differ on purpose:
  the context describes what governs the action, which for a mission type not in force is nothing, while the prompt
  read hands a renderer the text of a running step, and such a mission type has none. The enum is closed, so a
  consumer that validates a refusal's `code` against the earlier enum refuses the new value. Core: nothing while it is
  not a consumer (epic #186).
