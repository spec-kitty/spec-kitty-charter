# open-packs#175 head e8a24d6 (git read; API comments unreadable without push attach, which the dispatch forbids)

## packs/spdd/missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml
```
schema_version: "1.0"
id: spdd-plan
prompt_fragments:
  - slot: spdd:reasons-block
    path: spdd-plan.prompt-fragment.md
```
## packs/spdd/missions/software-dev/enhancements/spdd-plan.prompt-fragment.md
```
### REASONS Guidance - Plan

While composing the plan, fill or update:

- **Approach** - chosen strategy and rejected alternatives with rationale.
- **Structure** - code surfaces, components, dependencies, ownership boundaries.

Link to source artifacts (spec, contracts) instead of duplicating them.
```
## packs/spdd/presets/spdd.yaml
```
schema_version: "1.0"
id: spdd
description: >-
  Put Structured-Prompt-Driven Development and the REASONS canvas workflow in
  force, including the five software-dev prompt enhancements. One preset is the
  only activation gate for the doctrine and its prompt fragments (ADR-0066 d4).
activated_paradigms:
  - id: structured-prompt-driven-development
activated_directives:
  - id: DIRECTIVE_038
activated_tactics:
  - id: reasons-canvas-fill
  - id: reasons-canvas-review
activated_styleguides:
  - id: reasons-canvas-writing
activated_step_enhancements:
  - id: spdd-specify
  - id: spdd-plan
  - id: spdd-tasks
  - id: spdd-implement
  - id: spdd-review
```
## packs/spdd/pack.yaml
```
# Authored pack descriptor for the opt-in SPDD/REASONS (org-tier) Charter Pack.
# schema_version 1.0 includes the ADR-0066 prompt-slot and fragment rules.
schema_version: "1.0"
pack_id: 01M6Y9ACNAAD96XPNQS0XWP2FF
pack_version: 1.0.0
parent_pack: null
name: spdd
pack_level: org
active_charter: org-charter.yaml
pack_drg: drg/fragment.yaml
depends_on:
  - pack_id: 01ARWG13C000000000000000FG
    range: ">=1.0,<2"
```
## packs/spdd/org-charter.yaml
```
# Deliberately activates nothing on its own. A consumer opts in per project
# with the `spdd` preset or explicit artifact activation.
schema_version: 2
org_name: spec-kitty-spdd
```
## contracts/packs/file-kinds.local.yaml
```
# Open-packs additions to the pinned charter contract (charter ADR-0066).
# These rules are loaded beside, never instead of, the charter's file-kinds.yaml.
fragment_kinds:
  step_enhancements: step_enhancement

files:
  # A step enhancement lives beside the mission type it augments. Its target is
  # authored only by an `enhances` edge in this pack's graph documents.
  - match: "*/missions/*/enhancements/*.step-enhancement.yaml"
    schema: step-enhancement
  # Markdown prompt-fragment companions live beside their enhancement.
  - match: "*/missions/*/enhancements/*.prompt-fragment.md"
    schema: prompt-fragment
    content_form: markdown
```
## base prompt slot sample (plan.prompt.md diff)
```diff
diff --git a/packs/built-in/missions/software-dev/steps/plan.prompt.md b/packs/built-in/missions/software-dev/steps/plan.prompt.md
index e9e84ab..95d9b65 100644
--- a/packs/built-in/missions/software-dev/steps/plan.prompt.md
+++ b/packs/built-in/missions/software-dev/steps/plan.prompt.md
@@ -6,19 +6,9 @@ description: Create an implementation plan
 **Version**: 0.11.0+
 
 <!-- spdd:reasons-block:start -->
-
-### REASONS Guidance — Plan
-
-While composing the plan, fill or update:
-
-- **Approach** — chosen strategy and rejected alternatives with rationale.
-- **Structure** — code surfaces, components, dependencies, ownership boundaries.
-
-Link to source artifacts (spec, contracts) instead of duplicating them.
-
 <!-- spdd:reasons-block:end -->
 
-## 📍 WORKING DIRECTORY: Stay in the repository root checkout
+## WORKING DIRECTORY: Stay in the repository root checkout
 
 **IMPORTANT**: Plan works in the repository root checkout. NO worktrees created.
 
@@ -27,9 +17,10 @@ Link to source artifacts (spec, contracts) instead of duplicating them.
 # You should already be here if you just ran /spec-kitty.specify
 
 # Creates:
-# - kitty-specs/<mission_slug>/plan.md → In repository root checkout
+# - kitty-specs/<mission_slug>/plan.md -> In repository root checkout
 #   (the NNN- prefix in the directory listing is display-only metadata)
-# - Commits to target branch
+# - Commits to the Mission's planning branch (the Mission topic branch when the
+#   Mission was created without --topology, else the target branch)
 # - NO worktrees created
 ```
 
@@ -45,7 +36,7 @@ when the CLI needs a mission selector.
 - Prefer `mission_id` or `mid8` when the repo has multiple similarly named
   missions.
 - The resolver disambiguates by `mission_id` and returns a structured
-  `MISSION_AMBIGUOUS_SELECTOR` error on ambiguity — there is no silent fallback.
+  `MISSION_AMBIGUOUS_SELECTOR` error on ambiguity - there is no silent fallback.
 
 ## User Input
 
@@ -62,27 +53,27 @@ gates pass:
 
 1. **Entry gate.** `spec.md` must already be both **committed** (tracked +
    present at HEAD) and **substantive** (at least one populated `FR-###`
-   row — not just template placeholders). If either check fails, the CLI
+   row - not just template placeholders). If either check fails, the CLI
    returns `phase_complete=false` with a `blocked_reason` naming "committed
    AND substantive" and does **not** create or commit `plan.md`.
 
 2. **Exit gate.** `plan.md` is only auto-committed when its Technical Context
    section contains a real `Language/Version` value (and at least one peer
-   field) — not the `[e.g., …]` / `[NEEDS CLARIFICATION …]` placeholders.
+   field) - not the `[e.g., ...]` / `[NEEDS CLARIFICATION ...]` placeholders.
 
    - The FIRST `setup-plan` call after the entry gate passes scaffolds
      `plan.md` from the template. This is a **non-error** state: the CLI
      returns `result: "success"` with `scaffold_only: true` and
-     `phase_complete: false` — it means "plan.md is ready for you to
+     `phase_complete: false` - it means "plan.md is ready for you to
      populate", not a failure. Do **not** treat this call as blocked; proceed
      to fill in the Technical Context.
    - Once `plan.md` has been edited but its Technical Context is still not
      substantive, the CLI returns `result: "blocked"` with
      `phase_complete: false` and a populated-but-insufficient
-     `blocked_reason` naming the missing field(s) — populate the section and
+     `blocked_reason` naming the missing field(s) - populate the section and
      re-run.
 
-Section presence is the only signal — adding arbitrary prose without the
+Section presence is the only signal - adding arbitrary prose without the
 required structural rows does **not** count as substantive (no byte-length
 escape hatch).
 
@@ -97,7 +88,7 @@ reference will later require an issue-matrix row before its owning work package
 approved. A context-only citation (e.g. `Follow-up:`, `see #`, `parent`, `epic`) or a
 PR/commit reference (`PR #NNNN`, a `/pull/NNNN` URL) is non-gating and needs no row; if
 an issue genuinely owes the mission no work, it can later be recorded with the
-`not-applicable` verdict. This is informational only — it does not gate
+`not-applicable` verdict. This is informational only - it does not gate
 `/spec-kitty.plan`.
 
 ## Branch Strategy Confirmation (MANDATORY)
@@ -141,7 +132,7 @@ This command runs in the **repository root checkout**, not in a worktree.
   - Use `current_branch`, `target_branch` / `base_branch`, and `planning_base_branch` / `merge_target_branch` (plus uppercase aliases) from that payload
   - Use `branch_matches_target` from that payload to detect branch mismatch; do not probe branch state manually inside the prompt
 - Planning artifacts live in `kitty-specs/<mission_slug>/` (the `NNN-` prefix is display-only metadata)
-- The plan template is committed to the target branch after generation
+- The plan template is committed to the Mission's planning branch after generation. Without an explicit `--topology` at create, planning artifacts (spec, plan, tasks, `meta.json`) are committed to the Mission topic branch, not the target branch; consolidation lands them on the target.
 
 **Path reference rule:** When you mention directories or files, provide either the absolute path or a path relative to the project root (for example, `kitty-specs/<mission>/tasks/`). Never refer to a folder by name alone.
 
@@ -203,7 +194,7 @@ Before asking **any** clarifying question during plan elaboration, you MUST:
 - `--input-key` is the snake_case programmatic key (e.g., `db_choice`).
 - The `decision_id` on the wire is a plain ULID (26 chars). The `DM-` prefix
   appears only in artifact filenames, not in CLI arguments.
-- The verifier cross-checks `[NEEDS CLARIFICATION: …] <!-- decision_id: <id> -->`
+- The verifier cross-checks `[NEEDS CLARIFICATION: ...] <!-- decision_id: <id> -->`
   sentinels in `plan.md` against the decisions index and exits non-zero on drift.
 - Widening is represented by the CLI/SaaS widen flow; if that flow returns
   canonical thread metadata, it must be recorded as `DecisionPointWidened`.
@@ -247,9 +238,9 @@ Planning requirements (scale to complexity):
 
 ## Bulk-Edit Check (if applicable)
 
-If this mission is marked `change_mode: bulk_edit` in `meta.json` — or if the
+If this mission is marked `change_mode: bulk_edit` in `meta.json` - or if the
 spec describes renaming the same string (identifier, path, key, label, term)
-across many files — load the `spk-practice-bulk-edit` skill and
+across many files - load the `spk-practice-bulk-edit` skill and
 follow it. You will produce `kitty-specs/<mission>/occurrence_map.yaml`
 alongside the other planning artifacts. Every one of the 8 standard categories
 (code_symbols, import_paths, filesystem_paths, serialized_keys, cli_commands,
@@ -263,8 +254,8 @@ If the mission is not a bulk edit, skip this step.
 
 Apply this section whenever the plan adds, upgrades, or removes a dependency, in any ecosystem (npm/yarn/pnpm, pip/uv, Maven/Gradle, etc.).
 
-- **Security checks**: Reference `DIRECTIVE_051` and the `supply-chain-install-safety` tactic. Planning output (Technical Context and/or `research.md`) must surface a result for every control the directive names for any dependency decision — this mirrors the `supply_chain_security_check` step already present in the `plan` step contract. Use the matching ecosystem toolguide for the exact commands.
-- **Advisory posture**: This is advisory in v1 — it does not add a new blocking gate to the Commit Boundary gates above — but an unexamined default is a gap in the plan, not a pass. Silence is not compliance.
+- **Security checks**: Reference `DIRECTIVE_051` and the `supply-chain-install-safety` tactic. Planning output (Technical Context and/or `research.md`) must surface a result for every control the directive names for any dependency decision - this mirrors the `supply_chain_security_check` step already present in the `plan` step contract. Use the matching ecosystem toolguide for the exact commands.
+- **Advisory posture**: This is advisory in v1 - it does not add a new blocking gate to the Commit Boundary gates above - but an unexamined default is a gap in the plan, not a pass. Silence is not compliance.
 - **Adversarial evidence (mandatory for plan/research)**: When a security-impacting dependency decision is made, run (or explicitly document deferral of) an adversarial-squad challenge pass before claiming plan readiness, per the `adversarial-squad-deployment` procedure. Record each contested finding's disposition in `research.md` per that procedure's findings-disposition contract (load it with `spec-kitty charter context --include procedure:adversarial-squad-deployment`). No contested finding may be silently dropped.
 
 ## Outline
@@ -275,13 +266,13 @@ Apply this section whenever the plan adds, upgrades, or removes a dependency, in
 
 2. **Resolve mission context deterministically** (CRITICAL - prevents wrong mission selection):
    - Prefer an explicit mission slug from user direction or from the current directory path (`kitty-specs/<mission-slug>/...`)
-   - The resolver requires `--mission` — always pass an explicit handle. Running `setup-plan` without `--mission` returns `PLAN_CONTEXT_UNRESOLVED` even when exactly one mission is present.
+   - The resolver requires `--mission` - always pass an explicit handle. Running `setup-plan` without `--mission` returns `PLAN_CONTEXT_UNRESOLVED` even when exactly one mission is present.
    - Resolve the handle first: `spec-kitty agent context resolve --action plan --mission <handle> --json`, then pass the resolved slug to `setup-plan`
    - If the context resolve call returns an ambiguity error with `available_missions`, stop and pick one explicit mission slug before continuing
 
 3. **Setup**: Run `spec-kitty agent mission setup-plan --mission <mission-slug> --json` from the repository root and parse JSON for:
    - `result`: "success" or error message
-   - `scaffold_only`: `true` only on the first happy-path scaffold write (plan.md freshly copied from the template, untouched). This is `result: "success"` and NOT an error — populate the Technical Context and re-run `setup-plan` to commit. `phase_complete` stays `false` until then.
+   - `scaffold_only`: `true` only on the first happy-path scaffold write (plan.md freshly copied from the template, untouched). This is `result: "success"` and NOT an error - populate the Technical Context and re-run `setup-plan` to commit. `phase_complete` stays `false` until then.
    - `mission_slug`: Resolved mission slug
    - `spec_file`: Absolute path to resolved spec.md
    - `plan_file`: Absolute path to the created plan.md
@@ -307,31 +298,31 @@ Apply this section whenever the plan adds, upgrades, or removes a dependency, in
    spec-kitty agent mission setup-plan --mission <handle> --json
    ```
 
-   **Error handling**: If the command fails with "Cannot detect mission", "Multiple missions found", or `MISSION_AMBIGUOUS_SELECTOR`, pass an unambiguous handle — the `mission_id` or its 8-char prefix `mid8` always disambiguates.
+   **Error handling**: If the command fails with "Cannot detect mission", "Multiple missions found", or `MISSION_AMBIGUOUS_SELECTOR`, pass an unambiguous handle - the `mission_id` or its 8-char prefix `mid8` always disambiguates.
 
 4. **Load context**: Read `spec_file` from setup-plan JSON output and `.kittify/charter/charter.md` if it exists. If the charter file is missing, skip Charter Check and note that it is absent. Load IMPL_PLAN template (already copied).
 
 5. **Execute plan workflow**: Follow the structure in IMPL_PLAN template, using the validated planning answers as ground truth:
-   - Update Technical Context with explicit statements from the user or discovery research; mark `[NEEDS CLARIFICATION: …] <!-- decision_id: <id> -->` only when the user deliberately postpones a decision (call `decision defer` before writing each such marker)
+   - Update Technical Context with explicit statements from the user or discovery research; mark `[NEEDS CLARIFICATION: ...] <!-- decision_id: <id> -->` only when the user deliberately postpones a decision (call `decision defer` before writing each such marker)
    - If a charter exists, fill Charter Check section from it and challenge any conflicts directly with the user. If no charter exists, mark the section as skipped.
    - Evaluate gates (ERROR if violations unjustified or questions remain unanswered)
-   - Phase 0: Generate research.md — extend it if the discovery step already created one; do not replace existing content (commission research to resolve every outstanding clarification, prioritizing unresolved domain rules, lifecycle questions, and event/integration behavior before generic tech comparisons)
+   - Phase 0: Generate research.md - extend it if the discovery step already created one; do not replace existing content (commission research to resolve every outstanding clarification, prioritizing unresolved domain rules, lifecycle questions, and event/integration behavior before generic tech comparisons)
    - Phase 1: Generate data-model.md, contracts/, quickstart.md based on confirmed intent; when applicable, capture entities/value objects, invariants, state transitions, and externally visible events in the design artifacts
    - Re-evaluate Charter Check post-design, asking the user to resolve new gaps before proceeding
 
 6. **STOP and report**: This command ends after Phase 1 planning. Report branch, IMPL_PLAN path, and generated artifacts (including the Implementation Concern Map if present).
 
-   **⚠️ CRITICAL: DO NOT proceed to task generation!** The user must explicitly run `/spec-kitty.tasks` to translate implementation concerns from `plan.md` into executable work packages. Your job is COMPLETE after reporting the planning artifacts.
+   **CRITICAL: DO NOT proceed to task generation!** The user must explicitly run `/spec-kitty.tasks` to translate implementation concerns from `plan.md` into executable work packages. Your job is COMPLETE after reporting the planning artifacts.
 
 ## Phases
 
 ### Phase 0: Outline & Research
 
 1. **Extract unknowns from Technical Context** above:
-   - For each NEEDS CLARIFICATION → research task
-   - For each unresolved rule, invariant, lifecycle edge, or event/integration ambiguity → domain research task
-   - For each dependency → best practices task
-   - For each integration → patterns task
+   - For each NEEDS CLARIFICATION -> research task
+   - For each unresolved rule, invariant, lifecycle edge, or event/integration ambiguity -> domain research task
+   - For each dependency -> best practices task
+   - For each integration -> patterns task
 
 2. **Generate and dispatch research agents**:
    ```
@@ -354,15 +345,15 @@ Apply this section whenever the plan adds, upgrades, or removes a dependency, in
 
 **Prerequisites:** `research.md` complete
 
-1. **Extract entities from mission spec** → `data-model.md`:
+1. **Extract entities from mission spec** -> `data-model.md`:
    - Entity name, fields, relationships
    - Validation rules from requirements
    - Invariants or atomicity boundaries if applicable
    - State transitions if applicable
 
 2. **Generate API contracts** from functional requirements:
-   - For each user action → endpoint
-   - For each externally visible event, webhook, or integration callback → contract or payload shape when applicable
+   - For each user action -> endpoint
+   - For each externally visible event, webhook, or integration callback -> contract or payload shape when applicable
    - Use standard REST/GraphQL patterns
    - Output OpenAPI/GraphQL schema to `/contracts/`
    - If the Mission defines no interfaces (test remediation, refactor), do not
@@ -379,7 +370,7 @@ Apply this section whenever the plan adds, upgrades, or removes a dependency, in
 
 ---
 
-## ⛔ MANDATORY STOP POINT
+## MANDATORY STOP POINT
 
 **This command is COMPLETE after generating planning artifacts.**
 
@@ -392,10 +383,10 @@ After reporting:
 **YOU MUST STOP HERE.**
 
 Do NOT:
-- ❌ Generate `tasks.md`
-- ❌ Create work package (WP) files
-- ❌ Create `tasks/` subdirectories
-- ❌ Proceed to implementation
+- Generate `tasks.md`
+- Create work package (WP) files
+- Create `tasks/` subdirectories
+- Proceed to implementation
 
 `/spec-kitty.tasks` translates implementation concerns from `plan.md` into executable work packages. The user will run it when they are ready.
 
```
## step-enhancement schema diff vs charter contracts
```diff
5c5
<   "description": "Additions to a mission step. The target is not a field: the enhancing pack's graph documents carry its enhances edge (charter ADR-0053 decision 22 and ADR-0064).",
---
>   "description": "The additions a step enhancement contributes (missions/<type>/enhancements/<id>.step-enhancement.yaml). Its target is not a field: the enhancing pack's graph documents carry the only enhances edge, to action:<type>/<step> (ADR-0053 decision 22; ADR-0064). prompt_fragments names one Markdown companion per slot of that step's prompt (ADR-0066). A semantic check beside this schema requires the id to equal the file stem, each slot to occur at most once, and each named companion to exist beside the enhancement; violations are prompt_fragment_invalid. The guard and emit additions of ADR-0053 decision 22 are not in this shape yet (#428).",
7c7,10
<   "required": ["schema_version", "id"],
---
>   "required": [
>     "schema_version",
>     "id"
>   ],
11c14,17
<       "const": "1.0"
---
>       "const": "1.0",
>       "description": "The step-enhancement shape's version, the string \"1.0\".",
>       "title": "Schema Version",
>       "type": "string"
14,15c20,23
<       "type": "string",
<       "pattern": "^[a-z][a-z0-9]*(-[a-z0-9]+)*$"
---
>       "description": "The enhancement's stable identity; equal to its file stem.",
>       "pattern": "^[a-z][a-z0-9]*(-[a-z0-9]+)*$",
>       "title": "Id",
>       "type": "string"
18c26
<       "description": "Markdown companions to insert into named slots of the enhanced step's prompt. No priority or position field exists; charter ADR-0066 decision 5 defines ordering.",
---
>       "description": "Markdown companions to insert into named slots of the enhanced step's prompt. Absent when the enhancement contributes none; never empty. Each slot id occurs at most once. No target, range, anchor, position or priority field exists (ADR-0066 decisions 3, 5 and 7).",
23c31,34
<         "required": ["slot", "path"],
---
>         "required": [
>           "slot",
>           "path"
>         ],
27,28c38,41
<             "type": "string",
<             "pattern": "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$"
---
>             "description": "The slot declared by the prompt in force for the enhanced step: <namespace>:<name>, each part lower-case and starting with a letter.",
>             "pattern": "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$",
>             "title": "Slot",
>             "type": "string"
31,33c44,47
<             "description": "The Markdown companion beside this enhancement file.",
<             "type": "string",
<             "pattern": "^[a-z][a-z0-9-]*\\.prompt-fragment\\.md$"
---
>             "description": "The <id>.prompt-fragment.md companion beside this enhancement file; no subdirectory or other file form.",
>             "pattern": "^[a-z][a-z0-9-]*\\.prompt-fragment\\.md$",
>             "title": "Path",
>             "type": "string"
```
