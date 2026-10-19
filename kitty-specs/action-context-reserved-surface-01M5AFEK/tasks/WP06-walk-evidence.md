---
work_package_id: WP06
title: Walk evidence against core
dependencies:
- WP01
requirement_refs:
- FR-008
- C-004
- SC-001
planning_base_branch: feat/action-context-reserved-surface
merge_target_branch: feat/action-context-reserved-surface
branch_strategy: Planning artifacts for this mission were generated on feat/action-context-reserved-surface. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/action-context-reserved-surface unless the human explicitly redirects the landing branch.
subtasks:
- T027
- T028
- T029
phase: Phase 1 - The read
history:
- at: '2026-10-19T17:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: researcher-robbie
authoritative_surface: kitty-specs/action-context-reserved-surface-01M5AFEK/evidence/
create_intent:
- kitty-specs/action-context-reserved-surface-01M5AFEK/evidence/core_walk.py
- kitty-specs/action-context-reserved-surface-01M5AFEK/evidence/walk-comparison.md
execution_mode: planning_artifact
model: sonnet
owned_files:
- kitty-specs/action-context-reserved-surface-01M5AFEK/evidence/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP06: Walk evidence against core

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `researcher-robbie`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- **Has review feedback?**: check the `review_ref` field in the event log (`spec-kitty agent tasks status`).
- Address every feedback item, and record each with `spec-kitty agent tasks add-history WP06 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

SC-001's recorded evidence. Over open-packs at the commit `verifyAll` pins (`OPEN_PACKS_REF` in
`.github/workflows/service.yml`), the run compares:

- core's bare walk (`charter.offering.drg.query.resolve_context` at core 9554e7b2, run from
  `/root/.venv-sk`);
- the service's `getActionContext` answer after WP01.

It covers **every action of every mission type** at depths 0, 1 and 2. Every difference is
classified against the divergence register (`research.md` §5), and any unregistered difference
is reported as a finding. This is evidence, not a gate: the gate is the corpus harness (#9),
over core's CLI.

**Done when** `evidence/walk-comparison.md` holds:

- the commits used;
- the method;
- a per-action table: core count, charter count, and differences by register id;
- every unregistered difference, explained, or flagged as a defect for WP01.

## Context & Constraints

**Read:**

- `research.md` §2 (core's walk, and what is *not* the walk), §3 D2 and D3, and §5;
- the plan-point squad's throwaway script at
  `/tmp/claude-0/-home-user-spec-kitty-charter/c8bb6a80-7eb0-5761-86f0-7a030cd21b49/scratchpad/walk.py`,
  if it is still there. It is a starting point, not to be trusted blindly.

**Constraints:**

- **Core is evidence.** Do not edit core or open-packs. Run Python with `-I` from a directory
  that is not inside open-packs.
- **Mapping:** core's sets are unordered and carry no provenance, so compare **URN sets**. The
  charter's extra fields (`relation`, `via`, `hops`) are DIV-005. Core's `glossary_scopes` maps
  to the charter's `relation: vocabulary` entries (DIV-006).
- **Expected registered differences:**
  - **DIV-002:** undeclared scope targets. Core's `walk_edges` may still list them; check.
  - **DIV-006:** vocabulary. Open-packs has 0 `vocabulary` edges, so expect none.
  - Anything else is a finding.

## Branch Strategy

- **Planning base branch**: `feat/action-context-reserved-surface`
- **Merge target branch**: `feat/action-context-reserved-surface`

```bash
spec-kitty agent action implement WP06 --agent claude --mission action-context-reserved-surface-01M5AFEK
```

## Subtasks & Detailed Guidance

### Subtask T027: Core's walk

`evidence/core_walk.py`:

- Load open-packs `built-in/` into core's DRG model the way core does.
  - Find core's graph loader: `src/charter/offering/drg/` (look for `load_graph` or a merge
    function).
  - If core's loader needs a project, use its built-in-only path.
- For every `action:` node, call `resolve_context(graph, action_urn, depth=d)` for d in 0, 1, 2.
- Write JSON: `{action: {depth: {"artifacts": sorted([...]), "glossary_scopes": sorted([...])}}}`.
- Print core's commit (`git -C /home/user/spec-kitty/spec-kitty rev-parse HEAD`) and open-packs'
  commit into the output.
- Run it with `/root/.venv-sk/bin/python -I evidence/core_walk.py <open-packs path> > evidence/core-walk.json`.
  Keep the JSON if it is under about 300 KB; otherwise keep only the summary.

### Subtask T028: The service's answers

Choose the simplest honest way:

- **(a) Run the service.** Start it over open-packs `built-in` (`bootRun` with
  `charter.packs[0]…`). If WP04 has landed, set `CHARTER_WORKTREE_TOKEN`; otherwise not. Then
  GET every action at depths 0, 1 and 2. Script it in `evidence/service_walk.sh` or `.py`.
- **(b) A throwaway JUnit test** (not committed) that runs `ActionContextQuery` over
  `FileSystemCharterPackRepository` on open-packs and prints JSON.

Option (a) exercises the real HTTP answer and is preferred. Record the service commit.

### Subtask T029: Classify and write up

**`evidence/walk-comparison.md`:**

- **Method:** the commits and the commands to reproduce the run.
- **A summary table:** per action and depth, core |S|, charter |S|, |core − charter|,
  |charter − core|, and register ids.
- **Every difference** with its register id, or "UNREGISTERED" and an explanation.
- **A conclusion:** does SC-001 hold?

**An unregistered difference that is a WP01 defect** (for example a depth off-by-one or a
missing BFS expansion) means:

1. stop;
2. record it with `add-history` and in the write-up;
3. leave it for the orchestrator to send back to WP01.

Do not fix service code in this WP.

## Definition of Done

- [ ] The script and its output are committed under `evidence/`, and are reproducible.
- [ ] Every difference is classified; SC-001's verdict is stated.
- [ ] No edit outside `evidence/`.
