# Draft issue for spec-kitty-open-packs: separate step skeletons from generic templates in mission types

Filed as spec-kitty/spec-kitty-open-packs#180.

## Why

`missions/<type>/templates/` holds two kinds of file under one name:

1. **Step skeletons**: the starting shape of a document a step produces. Only 8 of 25 are bound to their step through
   `expected_output_artifacts[].template_path` (for example `software-dev` specify → `spec-template.md`).
2. **Generic templates**: files no step produces, such as `documentation/templates/divio/*`,
   `documentation/templates/generators/*` and `documentation/templates/release-template.md`.

Some skeletons are named only in prompt prose and are not bound to a step:

- `software-dev/templates/tasks-template.md` and `task-prompt-template.md` (tasks.prompt.md);
- `research/templates/research/evidence-log.csv` and `source-register.csv` (five research prompts).

The charter service serves mission-type templates as step **skeletons** (charter mission `mission-type-reads`). A
client following a step only reaches the skeletons the step binds.

## Ask

- Bind each skeleton to the step that produces it, through `template_path` on the matching expected output artifact.
- Move the generic templates to the pack's top-level `templates/` (generic asset templates), or say why they belong to
  the mission type.
