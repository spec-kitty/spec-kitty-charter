# Decision Moment `01M65SW4F4DHZC70JE43F27RDF`

- **Mission:** `mission-type-consolidation-01M65MCK`
- **Origin flow:** `plan`
- **Slot key:** `plan.q2.inputs-outputs`
- **Input key:** `plan.q2.inputs-outputs`
- **Status:** `resolved`
- **Created:** `2026-10-30T07:02:34.724523+00:00`
- **Resolved:** `2026-10-30T07:02:37.427833+00:00`
- **Resolved by:** `Stijn Dejongh (operator), relayed by the orchestrator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

Q2: Where do a step's expected artifacts and output templates live?

## Options

- fold templates onto expected artifacts
- step-level output_template

## Final answer

Step-level expected_input_artifacts (preconditions) and expected_output_artifacts (postconditions); templates on outputs; the type keeps its own outputs. Every pinned per-step entry is a postcondition (the five class-input entries are spec.md, required at the step that produces it); no pinned pack declares a precondition.

## Rationale

Operator ruling 2026-10-30, verbatim: 'step level expected_input_artefacts and expected_output_artefacts. this gives us a rather nice precondition -> do -> postcondition closure.' Spelled artifact per the glossary.

## Change log

- `2026-10-30T07:02:34.724523+00:00` — opened
- `2026-10-30T07:02:37.427833+00:00` — resolved (final_answer="Step-level expected_input_artifacts (preconditions) and expected_output_artifacts (postconditions); templates on outputs; the type keeps its own outputs. Every pinned per-step entry is a postcondition (the five class-input entries are spec.md, required at the step that produces it); no pinned pack declares a precondition.")
