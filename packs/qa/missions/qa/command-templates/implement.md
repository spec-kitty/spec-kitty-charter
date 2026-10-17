# Test Execution

## Role in the QA lifecycle

This step is the `implement.md`-equivalent stage: it executes the designed test suite and
records defects found during execution.

## Bound agent-profile

`qa-test-executor-elena`.

## Directive citations

- `QA_002` (defect-report-quality) — defect report quality applies during execution.
- `QA_006` (exploratory-charters-timeboxed) — exploratory-charter execution also happens during
  this step.

## Canonical artifact & template

- Canonical artifact: `defect-log.md`.
- Template file: `defect-log-template.md`.

## Evidence Commit Discipline

Commit every `evidence/results/*.xml` and `evidence/sessions/*.session.yaml` file this step
produces before the step completes. Do not leave any of them uncommitted.

This matters because any uncommitted evidence file blocks `accept` — untracked and
tracked-and-modified alike, with no asymmetry between the two. The gate is
`acceptance/__init__.py::_accept_dirty_gate`, fed by a bare `git status --porcelain`
(`task_utils/support.py::git_status_lines` — no `-uno`, no status-code filter). Its only
exclusions are accept-owned writes, self-bookkeeping churn, and coordination residue, and none of
those three is keyed on status code, so an **untracked** (`??`) evidence file is excluded by none
of them and blocks exactly like a **tracked-and-modified** (` M`) one. (A `??`-skipping filter
does exist — `accept.py`'s `_dirty_paths_with_prefix` — but it feeds a separate,
`kitty-specs/<mission>/`-scoped cleanup step and never inspects `evidence/` paths; it has no
bearing on whether `accept` blocks.)

Committing every evidence file before the step completes is the only path that keeps the mission
acceptable: uncommitted evidence does not merely risk being lost at merge, it makes `accept` fail
outright until the file is committed.
