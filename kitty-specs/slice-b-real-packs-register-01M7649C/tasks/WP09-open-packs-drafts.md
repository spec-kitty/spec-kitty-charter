---
work_package_id: WP09
title: Open-packs drafts, proven against open-packs main
dependencies:
- WP04
requirement_refs:
- FR-020
- SC-005
planning_base_branch: feat/466-slice-b-real-packs-register
merge_target_branch: feat/466-slice-b-real-packs-register
branch_strategy: Planning artifacts for this mission were generated on feat/466-slice-b-real-packs-register. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/466-slice-b-real-packs-register unless the human explicitly redirects the landing branch.
subtasks:
- T041
- T042
phase: Phase 5 - The record
history:
- at: '2026-11-11T21:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: researcher-robbie
authoritative_surface: kitty-specs/slice-b-real-packs-register-01M7649C/research/
create_intent:
- kitty-specs/slice-b-real-packs-register-01M7649C/research/open-packs-dry-run.md
execution_mode: planning_artifact
model: sonnet
owned_files:
- kitty-specs/slice-b-real-packs-register-01M7649C/research/open-packs-TBD-readme-doc.md
- kitty-specs/slice-b-real-packs-register-01M7649C/research/open-packs-TBD-org-charter-preset.md
- kitty-specs/slice-b-real-packs-register-01M7649C/research/open-packs-dry-run.md
role: researcher
tags: []
tracker_refs: []
---

# Work Package Prompt: WP09 - Open-packs drafts, proven against open-packs main

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load researcher-robbie` (or read `researcher-robbie.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/a79c36318b7a506ca2ab26d74705c5e3e406004f/packs/built-in/agent_profiles/`) and
adopt it.

## Objective

Make the two drafts complete enough that, once open-packs applies them, every pack registers (SC-005). Under ADR-0070
charter tests never read open-packs content, so this is a one-off dry run recorded as research, not a test.

## Subtasks

### T041 - Dry runs

Open-packs is cloned read-only at `/home/user/spec-kitty-open-packs` (`git -C … checkout origin/main`; record the SHA).
With the WP04 head of the service built:

1. **Classification:** for every file under each of the 19 packs, run `PackFileKind.classify` (a throwaway JUnit test or
   JShell against `service/packs/build/classes`, not committed) and list each file that would be refused at registration:
   Retired, case variant, Unclassified (`unmodelled`).
2. **README codec:** read each `packs/<p>/README.md` through `PackDocumentMarkdown.read`; list each refusal with its
   reason (squad A11b: cave-kitty has `# ` lines; code fences may matter).
3. Record both lists, the SHA and the command in `research/open-packs-dry-run.md`. Separate what the two drafts fix from
   anything left.

### T042 - Finalize the drafts

- `open-packs-TBD-readme-doc.md`: add per-pack README fixes from the codec run; confirm the table against the SHA.
- `open-packs-TBD-org-charter-preset.md`: add the field mapping of the how-to (`required_<plural>` → must-have items,
  `activations` → preset, `governance_policies` → `policy.yaml`, others dropped with reasons); confirm `internal`'s
  item lists.
- Anything left that neither draft fixes: name it in `open-packs-dry-run.md` and tell the orchestrator (do not file).

## Branch Strategy

Planning base and merge target: `feat/466-slice-b-real-packs-register`. Implement with
`spec-kitty agent action implement WP09 --agent claude --mission slice-b-real-packs-register-01M7649C`.

## Definition of Done

The dry run lists zero refusals left once both drafts are applied, or names each remaining one.
