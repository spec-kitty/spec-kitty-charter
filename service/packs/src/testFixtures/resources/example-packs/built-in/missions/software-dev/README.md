# Software Development Mission

The primary mission type for structured software delivery. It runs an eight-step
workflow from discovery through review to acceptance.

## Runtime step order

```
discovery -> specify -> plan -> tasks -> analyze -> implement -> review -> accept
                                                    ^            |
                                                    +-- rework --+
```

The Spec Kitty runtime guards each transition: an artifact-presence check (for example
`artifact_exists("spec.md")`), a work-package status check, or, for `analyze`,
an analysis-currency check. `implement` and `review` iterate per work package;
a rejected review sends the work package back to `implement`.

`analyze` is the required analysis gate between `tasks` and `implement`: it
records a cross-artifact analysis report, and `implement` refuses to claim a
work package until a current analysis report exists.

## Steps

| Step | Purpose |
|------|---------|
| `discovery` | Research technologies and best practices |
| `specify` | Define user scenarios and acceptance criteria |
| `plan` | Design the technical architecture |
| `tasks` | Break work into finalized, dependency-ordered work packages |
| `analyze` | Cross-artifact consistency analysis, recorded as a current analysis report |
| `implement` | Execute a work package |
| `review` | Review and validate a work package |
| `accept` | Validate mission completeness before consolidation |

A step names the role it needs, never a profile; the activation preset decides which
profiles are active. A work package likewise records the role it asks for in its prompt
frontmatter (`role`); `agent_profile` there is an optional override, normally empty.

## Contents

- `software-dev.mission-type.yaml` - The mission type: its steps in run order, its
  on-demand steps (`charter`, `tasks-outline`, `tasks-packages`, `tasks-finalize`), and
  each step's prompt, guidelines, expected artifacts and references, and its role where set
- `steps/` - The step prompts (`<step>.prompt.md`) and guidelines (`<step>.guidelines.md`)
- `templates/` - Content scaffolds (spec, plan, tasks, task-prompt templates)
