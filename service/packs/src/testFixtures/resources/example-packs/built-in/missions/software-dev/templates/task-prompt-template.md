---
work_package_id: "WPxx"
subtasks:
  - "Txxx"
title: "Replace with work package title"
task_type: "implement"  # implement | review | plan | specify | research - drives the role suggestion
phase: "Phase N - Replace with phase name"
execution_mode: "code_change"  # code_change | planning_artifact - code_change WPs may never own kitty-specs/ paths; planning_artifact WPs must confine every owned_files entry to kitty-specs/ or docs/
owned_files:  # Repo-root-relative paths/globs this WP owns (e.g. src/..., tests/...) - never host-absolute or worktree-prefixed; a code_change WP must never list a kitty-specs/ path (finalize-tasks rejects it with INVALID_WP_OWNED_FILES_KITTY_SPECS); per-WP design notes / kitty-specs deliverables go in a separate planning_artifact WP confined to kitty-specs/ or docs/
  - "src/replace/with/owned/surface.py"
  - "tests/replace/with/owned/test_surface.py"
authoritative_surface: "src/replace/with/primary/surface/"  # Repo-root-relative prefix; must prefix at least one owned_files entry
create_intent:  # Repo-root-relative paths this WP will CREATE (suppresses literal-path zero-match at finalize)
  - "tests/replace/with/new/test_surface.py"
role: ""           # The role this WP asks for (e.g., "implementer", "reviewer"); the profile is selected from it
agent_profile: ""  # Optional override; leave empty unless the operator pins a profile for this WP
agent: ""          # CLI agent/tool identifier (claude, codex, copilot, etc.)
model: ""          # Model identifier (e.g., claude-sonnet-4-6) - optional
assignee: ""       # Optional friendly name when claimed/in_progress
shell_pid: ""     # PID captured when the task was claimed
history:
  - at: "{{TIMESTAMP}}"
    actor: "system"
    action: "Prompt generated via /spec-kitty.tasks"
---

# Work Package Prompt: {{work_package_id}} - {{title}}

## Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load an agent profile for the role below, and behave according to its guidance before parsing the rest of this prompt.

- **Role**: `{{role}}`
- **Profile override**: `{{agent_profile}}` (empty means: select from the role)
- **Agent/tool**: `{{agent}}`

If no profile override is set, use the profile the active preset activates for the role; otherwise run `spec-kitty agent profile list` and load a profile whose roles include it, preferring one whose specialization context matches this work package's languages and `authoritative_surface`.

---

## IMPORTANT: Review Feedback

**Read this first if you are implementing this task!**

- **Has review feedback?**: Check the `review_ref` field in the event log (via `spec-kitty agent tasks status`).
- **You must address all feedback** before your work is complete. Feedback items are your implementation TODO list.
- **Report progress**: As you address each feedback item, record what you changed with `spec-kitty agent tasks add-history <WPID> --note "..."` (stored in the status event log).

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Markdown Formatting

Wrap HTML/XML tags in backticks: `` `<div>` ``, `` `<script>` ``
Use language identifiers in code blocks: ````python`,````bash`

---

## Objectives & Success Criteria

- Summarize the exact outcomes that mark this work package complete.
- Call out key acceptance criteria or success metrics for the bundle.

## Context & Constraints

- Reference prerequisite work and related documents.
- Link to supporting specs: `.kittify/charter/charter.md`, `kitty-specs/.../plan.md`, `kitty-specs/.../tasks.md`, data model, contracts, research, quickstart.
- Highlight architectural decisions, constraints, or trade-offs to honor.

## Branch Strategy

- **Strategy**: {{branch_strategy}}
- **Planning base branch**: {{planning_base_branch}}
- **Merge target branch**: {{merge_target_branch}}

> These fields are populated automatically by `spec-kitty agent mission finalize-tasks`.
> Do NOT change them manually unless you are certain the branch topology has changed.

## Subtasks & Detailed Guidance

### Subtask TXXX - Replace with summary

- **Purpose**: Explain why this subtask exists.
- **Steps**: Detailed, actionable instructions.
- **Files**: Canonical paths to update or create.
- **Parallel?**: Note if this can run alongside others.
- **Notes**: Edge cases, dependencies, or data requirements.

### Subtask TYYY - Replace with summary

- Repeat the structure above for every included `Txxx` entry.

## Test Strategy (include only when tests are required)

- Specify mandatory tests and where they live.
- Provide commands or scripts to run.
- Describe fixtures or data seeding expectations.
- If this WP owns typed sources (including tests), include the project's
  configured compiler/typecheck command, matching CI. A passing test runner
  does not replace compiler diagnostics; a failed compiler check fails the gate.

## Risks & Mitigations

- List known pitfalls, performance considerations, or failure modes.
- Provide mitigation strategies or monitoring notes.

## Review Guidance

- Key acceptance checkpoints for `/spec-kitty.review`.
- Any context reviewers should revisit before approving.
- If typed sources changed, confirm the implementer ran the configured compiler
  check in addition to the test runner and that compiler diagnostics passed.

## Progress & Status

Progress, history, and status all live in the status event log
(`status.events.jsonl`) - never in this prompt file. Do **not** hand-edit a
history section here.

- **Record a progress note**: `spec-kitty agent tasks add-history <WPID> --note "<what you did>"`
- **Change WP status**: `spec-kitty agent tasks move-task <WPID> --to <status>`
- **View history and status**: `spec-kitty agent tasks status`

**Read status only through the CLI** (`spec-kitty agent tasks status --mission <handle>`),
which reads the Mission's resolved status surface. Never read `status.events.jsonl` or
`status.json` from the files of a lane worktree, a coordination worktree or any other
checkout: only the CLI's read counts.

### Optional Phase Subdirectories

For large missions, organize prompts under `tasks/` to keep bundles grouped while maintaining lexical ordering.
