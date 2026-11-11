# Implement Action - Governance Guidelines

These guidelines govern the quality and correctness standards for work package implementation in the software-dev mission. They are injected at runtime via the charter context bootstrap.

---

## Working Directory Discipline

- After running `spec-kitty implement WP##`, change into the checkout shown in the output.
- ALL file operations (Read, Write, Edit) must target files inside that checkout.
- Write deliverables only in the checkout `spec-kitty implement` printed. For lane topologies that is the lane worktree, never the repository root checkout; for `single_branch` it is the write checkout, which may be the repository root checkout. Writing anywhere else causes review failures and consolidation conflicts.

---

## Pre-Read Checks

Before any file operation, run:

```bash
pwd
ls -la
test -f kitty-specs/<mission>/tasks/<wp-file>.md && echo "wp prompt exists"
```

If a file or path is uncertain, verify with `ls` or `test -f` before reading it.

---

## Commit Discipline

- Commit all implementation changes before moving to `for_review`.
- Uncommitted changes will block the `move-task` command.
- Commit message format: `feat(WP##): <describe your implementation>`

---

## Supply-Chain Security Check (dependency changes)

- Any dependency add/upgrade/removal must run the `supply-chain-install-safety` tactic's checklist before the quality gate, per `DIRECTIVE_051` (see the matching ecosystem toolguide for exact commands).
- Record the check results - not just intent - in the commit message or WP notes. An unexamined default is a gap, not a pass. Advisory in v1: no new fail-closed gate is added.
