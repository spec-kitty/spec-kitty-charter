# Mission steps: no step declares its preconditions

Filed as spec-kitty/spec-kitty-open-packs#99. Refs spec-kitty/spec-kitty-charter#194, charter ADR-0044 (ruling Q2).

A step's expected artifacts close it as precondition, step, postcondition: `expected_input_artifacts` are what it needs
before it runs, `expected_output_artifacts` what it must leave behind. Every pinned `required_by_step` entry is a
postcondition (each, the five `artifact_class: input` specs included, is required at the step that produces it), so
the migration wrote no inputs.

Ask: author the inputs, for example `plan` requiring `spec.md`, `tasks` requiring `plan.md`.

Also: qa's type-level `output.defect-log.qa` moved to the `implement` step, which carries its template
(`templates/defect-log-template.md`); check that a non-blocking step output is the intended meaning.
