---
work_package_id: WP04
title: Documentation
dependencies:
- WP01
- WP02
- WP03
requirement_refs:
- FR-006
- C-005
planning_base_branch: ci-sonar-nightly-checkstyle-complexity
merge_target_branch: ci-sonar-nightly-checkstyle-complexity
branch_strategy: Planning artifacts for this mission were generated on ci-sonar-nightly-checkstyle-complexity. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into ci-sonar-nightly-checkstyle-complexity unless the human explicitly redirects the landing branch.
subtasks:
- T014
- T015
- T016
agent: claude
history: []
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-11-04-complexity-gate-and-nightly-sonarcloud.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/guides/service-build.md
- docs/changelog/2026-11-04-complexity-gate-and-nightly-sonarcloud.md
- docs/_meta/spelling-words.txt
role: documentarian
tags: []
tracker_refs: []
---

# WP04: Documentation

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Tell contributors three things: which complexity limits block a pull request, how to read and shrink the
exception register (and what the stale check says), and where the nightly SonarCloud report lives. Record
the change in the changelog. ADR-0056 stays Proposed. Do not edit it, because it lands in PR #366 and the
operator accepts it.

Implementation command:
`spec-kitty agent action implement WP04 --agent claude --mission ci-sonar-nightly-checkstyle-complexity-01M6HQ46`

## Branch Strategy

- Planning branch: `ci-sonar-nightly-checkstyle-complexity`.
- Final merge target: `ci-sonar-nightly-checkstyle-complexity`, which reaches `main` by pull request.
- This WP depends on WP01, WP02 and WP03. Describe what they actually shipped.

## Context

- **Spec:** FR-006, C-005.
- **House style:** read `docs/guides/service-build.md` first and match its voice and structure. Read a
  recent `docs/changelog/2026-11-0*` entry for the frontmatter and the H1 form `YYYY-MM-DD — Title`.
- **Docs gate:**
  - `python3 scripts/docs/check_docs.py all` must be clean.
  - The terminology gate forbids "DRG" (use CRG), "doctrine" and "feature" for the domain object.
  - cspell runs in CI. Add genuine project words to `docs/_meta/spelling-words.txt` in sorted position,
    and never re-sort the file.
- **ADR-0056 link:** link it only if PR #366 has merged into the base this WP builds on. Otherwise name
  it in plain text, "Proposed ADR-0056 (PR #366)", so the links check stays clean.

### Subtask T014: Service-build guide

Add a section, for example "Complexity limits", next to the existing quality-gate text. Cover:
- the four limits and that they apply to main and test sources;
- what a failure looks like;
- how to fix a failure: simplify the method; an entry is never added for new code;
- the register: one entry per method, by XPath, with rule, file, reason and sunset, and the Size count;
- the stale check (`checkCheckstyleRegister`) and its four findings, each with the action to take;
- the nightly report: its name in Actions, that it is reported only, that it skips green until the token
  exists, and where the quality-gate verdict appears.

Correct the PIT sentence only if it lies within the paragraph you touch. #239 owns that fix.

### Subtask T015: Changelog entry

Name the file `docs/changelog/2026-11-04-complexity-gate-and-nightly-sonarcloud.md`, keep it under
3,000 characters, and copy the frontmatter keys from a recent entry. Say what is new, what blocks and what
is only reported, and that the SonarCloud project and token come later from the operator.

### Subtask T016: Docs gate

Run `python3 scripts/docs/check_docs.py all` and fix every finding in your files. Check the new prose by
eye for words cspell will not know. US spelling.

## Definition of Done

- The guide section and the changelog entry are written; the docs gate is clean.
- Spelling words are added in sorted position only.
- ADR-0056 is untouched.

## Risks and reviewer guidance

- **The reviewer checks** every claim against what WP01 to WP03 shipped: the limits, the task names, the
  workflow name and the summary behavior.
