# The mission-type schema against #520

- **The schema refuses `action_sequence` and `template_set`.** `contracts/packs/schemas/mission-type.schema.json`
  sets `additionalProperties: false` on the root (line 198), on `Step` (line 6) and on every definition. A pack that
  authors `action_sequence`, `template_set`, `in_action_sequence` or `sequence_index` is refused as having an unknown key.
- **Every mission type has at least one step.** `steps` is required, with `minItems: 1`, and its description reads
  "the list order is the run order" (lines 247-255). `MissionType.java:28` adds "A mission type needs at least one step."
- **ADR-0044 already decides this:**
  - decision 2: `steps` is the only order;
  - decision 3: `depends_on`, `sequence_index` and `in_action_sequence` are dropped, and so are `template_set` and
    `step_type`;
  - decision 4: there is no non-linear order until a later decision.

  ADR-0050 decisions 8 to 11 add `transitions` later, for #434.
- **`step.yaml` is a retired file kind** (`contracts/packs/file-kinds.yaml:291-294`).
  `RetiredMissionFieldNamesTest` keeps the old field names out of the main sources.
- **No pack authors either field, and no mission type has zero steps.** This holds in charter and in open-packs:

  | Mission type | Steps | On-demand steps |
  |---|---|---|
  | cave-dev | 8 | 0 |
  | plan | 4 | 0 |
  | research | 6 | 1 |
  | software-dev | 8 | 5 |
  | qa | 7 | 0 |
  | documentation | 7 | 1 |
- **No test proves the refusal.** No invalid example pack and no `@contract` scenario shows `action_sequence` or
  `template_set` being refused.
- **"Template" still has other senses:**
  - `template_set` is required in `charter.schema.json` (lines 84, 113 and 357) and appears in the `charter.yaml`
    fixtures;
  - the javadoc of `RelationType.ENHANCES` says "action sequence";
  - template files are called "skeletons" on the wire, which ADR-0044 settled;
  - the step prompt is the glossary's "step template".
- **The graph chain is incomplete.** It runs `mission_type -requires-> action` (21 edges), then
  `action -instantiates-> template` (11 edges). The six mission types have 47 steps between them but only 21
  `requires` edges. No edge carries order.
