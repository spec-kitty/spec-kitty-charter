# Mission steps: one-line descriptions, detailed prompt descriptions, and the titles that were dropped

Filed as spec-kitty/spec-kitty-open-packs#96. Refs spec-kitty/spec-kitty-charter#194, charter ADR-0044 (ruling Q1,
as refined on 2026-10-30).

A step's `description` in the mission-type file is its one-line, human-facing summary, at most 160 characters (the
schema's `maxLength`). A prompt may keep its frontmatter `description` as a longer, detailed description; the
migration keeps every prompt byte for byte, frontmatter included. The prompt itself is what the agent is sent.

No pinned step description is over the bound: the longest is 119 characters (`documentation/discover`), the median about 72.
No description was shortened.

## Steps whose description comes only from the prompt frontmatter

These on-demand steps have no runtime description, so the migration copied the prompt's frontmatter `description` into
the step's `description`. The prompt keeps it too, so the same text stands in both places.

| software-dev step | description (from the prompt frontmatter) |
|---|---|
| `charter` | "Interview and compile a project charter" |
| `tasks-finalize` | "Validate dependencies, finalize WP metadata, and commit all task artifacts." |
| `tasks-outline` | "Create a work package manifest" |
| `tasks-packages` | "Materialize work package files" |

Ask: write a one-line summary for each, distinct from the prompt's detailed description, or keep the copy.

## Step titles that were dropped

Where the runtime gave a step a `title` and its `step.yaml` a different `display_name`, the migration keeps the
runtime title and drops the display name. There are 17 such steps:

| type | step | kept title | dropped `display_name` |
|---|---|---|---|
| documentation | `discover` | Documentation Discovery | Discovery |
| documentation | `audit` | Documentation Audit | Audit |
| documentation | `design` | Documentation Design | Design |
| documentation | `generate` | Documentation Generation | Generation |
| documentation | `validate` | Documentation Validation | Validation |
| documentation | `publish` | Documentation Publication | Publication |
| plan | `specify` | Specify | Specification |
| plan | `plan` | Plan | Planning |
| research | `scoping` | Research Scoping | Scoping |
| research | `methodology` | Methodology Design | Methodology |
| research | `gathering` | Source Gathering | Gathering |
| research | `synthesis` | Findings Synthesis | Synthesis |
| research | `output` | Publication Output | Output |
| software-dev | `discovery` | Discovery & Research | Research |
| software-dev | `plan` | Implementation Planning | Implementation Plan |
| software-dev | `tasks` | Tasks | Task Breakdown |
| software-dev | `review` | Code Review | Review |

Ask: confirm the kept titles, or set the better one in the mission-type file.

Each line is also in the charter mission's `research/migration/migration-report.md`.
