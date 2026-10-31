# Mission tier at the pin: inventory, field census, core meaning, sanitized mapping, divergences

Researcher Robbie (researcher-robbie), read-only. Issue #194 PR-B, mission `mission-type-consolidation-01M65MCK`.
Sources: `spec-kitty-open-packs` at `5df92a5` (pinned `openPacksRef` 5df92a5d); `spec-kitty-charter/packs`; core at
`/opt/spec-kitty/src` (meaning only). Census scripts: `research/scripts/inv.py`, `research/scripts/xref.py`.

Abbreviations: **MT** = target `mission_type` DSO, **MS** = target `mission_step` DSO, **DP-n** = an operator decision
point this research cannot settle. S1-S16 = ADR-0042/0043 rules.

---

## 0. Headline findings

1. **Five overlapping homes for one mission type.** `mission_types/<id>.yaml`, `missions/<id>/mission.yaml`,
   `mission-runtime.yaml`'s `mission:` block, `governance-profile.yaml`, `mission_type.graph.yaml` node. They disagree on
   the label (`Software Development` / `Software Dev Kitty` / `Planning Kitty` vs `Planning Mission`) and on the version
   (`mission.yaml` 1.0.0 vs runtime 2.2.0 / 2.0.0; plan is backwards: 2.0.0 vs 1.0.0).
2. **Two step sequences, not one.** `step.yaml`'s `sequence_index`/`in_action_sequence` encodes the *governed action
   sequence* (specify, plan, tasks, implement, review for software-dev). `mission-runtime.yaml`'s `steps[]` encodes the
   *planner DAG* (discovery → specify → plan → tasks → analyze → implement → review → accept). They differ for
   software-dev (runtime has `discovery`, `analyze`, `accept` that are `in_action_sequence: false` or have no step dir) and
   research (runtime `accept` has no step dir and no prompt anywhere).
3. **`depends_on` contradicts itself.** Every runtime DAG is a strict linear chain. 15 of 37 `step.yaml` files carry a
   `depends_on` that differs from the runtime (documentation/research/plan step.yaml say `[]`; software-dev
   `implement` says `[tasks]` where runtime says `[analyze]`; `specify` `[]` vs `[discovery]`).
4. **Step ids do not line up across families** (full matrix in §2.8): software-dev runtime `discovery` is step dir
   `research` (joined only through `prompt_template: research.md`); expected-artifacts uses snake_case
   `tasks_outline`/`tasks_packages`/`tasks_finalize` where the step dirs are kebab `tasks-outline`…; expected-artifacts
   keys `done` (software-dev, research) name no step anywhere; `retrospect` has an action index for software-dev but no
   step dir.
5. **Always-null / always-empty fields:** `guidance: null` 37/37, `delegates_to: []` 37/37, `agent_profile: null` 30/37,
   `selected_mission_step_contracts`, `available_tools`, `activations` `[]` 6/6, `required_always: []` 6/6.
6. **Profiles named where ADR-0032 says roles.** Runtime `agent-profile` (27 steps, kebab key, S9), step.yaml
   `agent_profile` (7 qa), gov-profile `selected_agent_profiles` (qa: 5), action index `agent_profiles` (3 retrospect).
7. **Directive refs by file stem.** 62 directive references in built-in action indexes and gov profiles use stems
   (`001-architectural-integrity-standard`, `042-common-docs`); the graph shards, step contracts, cave and qa use ids
   (`DIRECTIVE_001`, `CAVE_001`, `QA_001`). Same defect class as ADR-0042 D-P5.
8. **The graph shards duplicate the YAML.** `mission_type.graph.yaml` `scope` edges equal the gov-profile `selected_*`
   lists exactly (stem→id normalized); `action.graph.yaml` `scope` edges equal the action indexes except 3 files (§2.10);
   `instantiates` edges mirror `step.yaml` `template` refs (+3 C4 templates only in the graph).
9. **qa step.yaml points at prompts that do not exist** (`prompt_template: prompt.md`, no `prompt.md` beside any qa
   step; the prompts are `missions/qa/command-templates/<id>.md`). **cave-dev has no step.yaml at all** (runtime-only
   steps; prompts in `command-templates/`).
10. **The charter repo vendors `packs/qa`** byte-identical to the pin for every mission-tier file (only `PROVENANCE.md`
    and `README.md` differ). `packs/internal` has no mission-tier file. So every qa divergence below is also a divergence
    of the vendored copy.

---

## 1. Inventory (pinned packs, 5df92a5)

Only `built-in`, `cave-kitty` and `qa` hold mission-tier files. `event-driven` and `internal` hold none.

### 1.1 Counts per pack

| Family | built-in | cave-kitty | qa | Total | Path pattern |
|---|---:|---:|---:|---:|---|
| `mission_types/<id>.yaml` | 4 | 1 | 1 | 6 | built-in: `missions/mission_types/<id>.yaml`; org: `mission_types/<id>.yaml` |
| `governance-profile.yaml` | 4 | 1 | 1 | 6 | built-in: `missions/<id>/…`; org: `mission_types/<id>/…` |
| `mission.yaml` | 4 | 1 | 1 | 6 | `missions/<id>/mission.yaml` |
| `mission-runtime.yaml` | 4 | 1 | 1 | 6 | `missions/<id>/mission-runtime.yaml` |
| `expected-artifacts.yaml` | 4 | 1 | 1 | 6 | `missions/<id>/expected-artifacts.yaml` |
| `actions/<a>/index.yaml` | 24 | 0 | 0 | 24 | `missions/<id>/actions/<a>/index.yaml` |
| `mission-steps/**/step.yaml` | 30 | 0 | 7 | 37 | built-in: `missions/mission-steps/<type>/<step>/`; qa: `mission-steps/qa/<step>/` |
| `mission-steps/**/prompt.md` | 30 | 0 | 0 | 30 | 28 with `description:` frontmatter; 2 empty (0 bytes): `documentation/retrospect`, `research/retrospect` |
| `mission-steps/**/guidelines.md` | 17 | 0 | 0 | 17 | no frontmatter |
| `missions/<id>/command-templates/*.md` | 0 | 8 | 7 | 15 | the step prompts of cave and qa |
| `templates/plan-field-declaration.yaml` | 0 | 0 | 1 | 1 | `missions/qa/templates/` |
| `*.step-contract.yaml` (already a modelled kind) | 21 | 0 | 7 | 28 | built-in: `missions/built_in_step_contracts/`; qa: `mission_step_contracts/` |
| `built_in_step_contracts/__init__.py` | 1 (0 bytes) | – | – | 1 | |
| `README.md` under `missions/` | 12 | 0 | 1 | 13 | built-in: `missions/README.md`, 4× `missions/<id>/README.md`, 7× under `templates/`; qa: `missions/qa/templates/README.md` |
| mission content templates `missions/<id>/templates/**` | 33 (incl. 7 README, 1 `.gitkeep`) | 0 | 12 (incl. README, plan-field-declaration, EVIDENCE.md) | 45 | not covered by the `*/templates/**/*` payload rule (top-level only) |
| `mission_type.graph.yaml` | 1 (4 nodes, 59 edges) | – | – | 1 | 21 `requires` → action, 38 `scope`; see §2.10 |
| `action.graph.yaml` | 1 (24 nodes) | – | – | 1 | |
| DRG fragment `mission_types` nodes | – | 1 | 1 | 2 | `drg/fragment.yaml`, `body_path: mission_types/<id>/governance-profile.yaml` |
| `built-in.source.yaml` | (repo root `packs/`, not inside a pack) | | | 1 | provenance, not mission tier |

Per mission type: software-dev 1+1+1+1+1 + 6 action indexes + 12 step dirs; documentation … + 8 + 8; research … + 6 + 6;
plan … + 4 + 4; cave-dev 5 YAML + 8 command templates, 0 step dirs; qa 5 YAML + 7 step dirs (step.yaml only) + 7 command
templates + plan-field-declaration.

### 1.2 How the charter currently classifies them

`contracts/packs/file-kinds.yaml` excludes every family (`**/mission-steps/**/step.yaml`, `**/actions/*/index.yaml`,
`**/missions/*/mission.yaml`, `…/mission-runtime.yaml`, `…/expected-artifacts.yaml`, `**/governance-profile.yaml`,
`**/mission_types/*.yaml`, `built-in.source.yaml`, `**/templates/plan-field-declaration.yaml`), each "no model in core at
9554e7b2". The whole-pack gate lists `other:missions` as an expected-red kind (#194) and pins
(`WholePackRoundTripTest.missionTemplatesAreNotTemplates`) that `missions/*/templates/**` census as `other:missions`, not
`template`. `prompt.md`, `guidelines.md`, `command-templates/*.md`, READMEs and `__init__.py` match no rule
(unclassified). NodeKind already has `action` and `mission_type`; fragment kinds map `mission_types`/`actions`.

---

## 2. Field inventory per family

Types are YAML types as parsed. "n" = occurrences; "files" = files carrying the key.

### 2.1 `mission_types/<id>.yaml` (6)

| Key | Type | n | Values | Notes |
|---|---|---:|---|---|
| `schema_version` | int | 6 | `1` | int, not `"1.0"` (S2) |
| `id` | str | 6 | documentation, plan, research, software-dev, cave-dev, qa | = file stem ✓ |
| `display_name` | str | 6 | Documentation, Plan, Research, Software Development, Cave Dev, QA | S10: `title` |
| `requires_substantive_spec` | bool | 4 | true (software-dev, research), false (documentation, plan) | absent in cave-dev, qa |

Core's `MissionType` also allows `extends`, `action_sequence`, `path_conventions` — none authored.

### 2.2 `governance-profile.yaml` (6)

| Key | Type | n | empty `[]` | Values / notes |
|---|---|---:|---:|---|
| `id` | str | 6 | | = `mission_type` (documented invariant "id == mission_type", core overlay key) — duplicate (S5) |
| `mission_type` | str | 6 | | |
| `template_set` | str\|null | 6 | null 5 | only software-dev: `software-dev-default` (a charter template-set scalar, back-compat for a core test suite) |
| `selected_directives` | list | 6 | 2 | built-in by **file stem** (`042-common-docs`, `031-context-aware-design`); cave `CAVE_001`; qa `QA_001..006` |
| `selected_tactics` | list | 6 | 3 | |
| `selected_paradigms` | list | 6 | 5 | |
| `selected_styleguides` | list | 6 | 2 | |
| `selected_toolguides` | list | 6 | 5 | |
| `selected_procedures` | list | 6 | 4 | |
| `selected_agent_profiles` | list | 6 | 5 | qa: 5 profiles (incl. `qa-defect-triager-dmitri`, bound to no step) |
| `selected_mission_step_contracts` | list | 6 | **6** | always empty |
| `available_tools` | list | 6 | **6** | always empty |
| `activations` | list | 6 | **6** | always empty |

software-dev's profile is all-empty except `template_set`. Comment-only provenance in every file.

### 2.3 `missions/<id>/mission.yaml` (6) — core "MissionConfig" (Schema B)

| Key | Type | files | Values / notes |
|---|---|---:|---|
| `name` | str | 6 | "Software Dev Kitty", "Documentation Kitty", "Deep Research Kitty", "Planning Kitty", "Cave Dev", "QA" — conflicts with `display_name` |
| `description` | str | 6 | one-line summary (the only MT summary that exists for 5 of 6) |
| `version` | str | 6 | 1.0.0 ×5, plan 2.0.0 — conflicts with runtime `mission.version` |
| `domain` | str | 6 | software, research, other ×4 (README says plan is "planning") |
| `workflow.phases[]{name,description}` | list | 6 | 34 phases; **do not match step ids** (software-dev: research/design/implement/test/review; research: question/methodology/gather/analyze/synthesize/publish; qa: strategy/planning/design/execution/reporting vs 7 steps) |
| `artifacts.required` / `.optional` | list[str] | 6 / 6 (1 empty) | file names and dirs (`contracts/`, `checklists/`, `data/`), overlaps expected-artifacts |
| `paths.{workspace,tests,deliverables,documentation,data}` | map | 4 | core `VALID_PATH_KEYS`, = `MissionType.path_conventions` |
| `validation.checks` | list[str] | 6 (2 empty) | names of core-coded checks (`git_clean`, `all_tests_pass`, `kanban_complete`…) |
| `validation.custom_validators` | bool | 4 | refers to a `validators.py` no pack ships |
| `mcp_tools.{required,recommended,optional}` | lists | 3 | tool names |
| `agent_context` | str (block) | 4 | multi-paragraph agent persona — a body (S12) |
| `task_metadata.{required,optional}` | lists | 3 | WP frontmatter field names |
| `commands.<cmd>.prompt` | map | 4 | keys are **core command names** (specify/plan/tasks/implement/review/accept), not step ids — documentation and research have no step called specify/plan/tasks |
| `mission`, `initial`, `states[]{name,display_name}`, `transitions[]{from,to}` | | 1 (qa) | retired v1 FSM block, file comment says "NEVER READ BY THE RUNTIME"; core strips them (`MISSION_COMPAT_IGNORED_FIELDS`) |

### 2.4 `missions/<id>/mission-runtime.yaml` (6) — core runtime "MissionTemplate" (Schema C)

| Key | Type | n / files | Notes |
|---|---|---|---|
| `mission.key` | str | 6 | = MT id (S1 spelling `key`) |
| `mission.name` | str | 6 | "Planning **Mission**" for plan (≠ "Planning Kitty") |
| `mission.version` | str | 6 | 2.2.0 software-dev, 2.0.0 documentation/research, 1.0.0 plan/cave/qa |
| `mission.title`, `mission.description` | str | 1 (plan) | plan only; `title` repeats `name` |
| `mission.steps[]{id,name,description,order,depends_on}` | list | 1 (plan) | a **second copy** of the 4 plan steps nested in `mission:`, with `name`+`order` |
| `mission.runtime.{loop_type,step_transition,prompt_template_dir,terminal_step}` | map | 1 (plan) | `prompt_template_dir: command-templates` — plan has no `command-templates/` dir |
| `steps[].id` | str | 40 | |
| `steps[].title` | str | 40 | differs from step.yaml `display_name` in 16 steps (§2.9) |
| `steps[].description` | str | 40 | |
| `steps[].depends_on` | list | 37 (3 `[]`) | strictly linear chains in all six |
| `steps[].prompt_template` | str | 36 (5 files) | plan has none; software-dev `discovery` → `research.md` |
| `steps[]."agent-profile"` | str | 27 (4 files) | **kebab key** (S9); profile ids (ADR-0032 says roles) |

No `contract_ref`, `raci`, `expected_output`, `requires_inputs` authored (core PromptStep allows them).

### 2.5 `mission-steps/<type>/<step>/step.yaml` (37) — core `MissionStep`

| Key | Type | n | null / empty | Values |
|---|---|---:|---|---|
| `id` | str | 37 | | = dir name ✓ (37/37) |
| `display_name` | str | 37 | | S10 → `title` |
| `step_type` | str | 37 | | always `agent` (core enum agent/human_in_loop/integration) |
| `prompt_template` | str | 37 | | always `prompt.md` (qa: file absent ×7) |
| `agent_profile` | str\|null | 37 | **null 30** | only the 7 qa steps set it |
| `guidance` | null | 37 | **null 37** | never set |
| `delegates_to` | list | 37 | **[] 37** | never set |
| `depends_on` | list | 37 | [] 22 | contradicts runtime in 15 steps |
| `sequence_index` | int\|null | 35 | null 7; **absent 2** (`documentation/retrospect`, `research/retrospect`) | 0..6 |
| `in_action_sequence` | bool | 37 | | true 26, false 11 |
| `template.{artifact_key,template_file}` | map | 13 | | `spec`/`plan` (built-in), qa: `spec`,`plan`,`tasks`,`test-report`,`defect-log`; file names relative to `missions/<type>/templates/` |

`in_action_sequence: false` steps: software-dev `accept`, `analyze`, `charter`, `research`, `tasks-outline`,
`tasks-packages`, `tasks-finalize`; documentation `retrospect`; research `retrospect`. Of these `accept` and `analyze` are
nevertheless in the runtime DAG (software-dev), and `charter`/`research`/`tasks-*`/`retrospect` are in no runtime.

### 2.6 `actions/<a>/index.yaml` (24, built-in only)

| Key | Type | files | empty `[]` | Notes |
|---|---|---:|---:|---|
| `action` | str | 24 | | = dir name ✓ |
| `directives` | list[str] | 24 | 4 | file stems in all built-in (62 stem refs across index+gov) |
| `tactics` | list | 24 | 4 | |
| `styleguides` | list | 24 | 19 | |
| `toolguides` | list | 24 | 23 | |
| `procedures` | list | 24 | 20 | |
| `agent_profiles` | list | 7 | 4 | 3 retrospect actions: `retrospective-facilitator` |
| `paradigms` | list | 5 | 4 | |

Action dirs ⊂ step dirs except **software-dev `retrospect`** (action index, no step dir). software-dev `analyze`,
`accept`, `charter`, `research`, `tasks-*` have no action index.

### 2.7 `expected-artifacts.yaml` (6)

| Key | Type | Notes |
|---|---|---|
| `schema_version` | str `"1.0"` | ✓ S2 |
| `mission_type` | str | = owning MT (S5 duplicate once folded) |
| `manifest_version` | str `"1"` | a core sync-namespace key ("not a content counter") |
| `required_always` | list | **[] in 6/6** |
| `required_by_step.<step>[]` | map of lists | entries `{artifact_key, artifact_class, path_pattern, blocking}`; 13 of 41 step lists are `[]` |
| `optional_always[]` | list | 21 entries in 4 files, all `blocking: false`; [] in cave-dev, plan |

Entry fields: `artifact_key` (dotted `class.name.qualifier`), `artifact_class` ∈ input/workflow/output/evidence/policy/
runtime (core enum), `path_pattern` (glob), `blocking` (bool; `false` inside `required_by_step` once:
documentation `generate` `docs/**/*.md`). Inconsistencies: software-dev uses `output.tasks.per_wp` twice (packages and
finalize) though core calls the key "stable, unique"; qa declares `qa-coverage.json` and `qa-traceability.yaml` twice
(blocking at a step + optional_always) with distinct keys; `artifact_class` of the same spec.md is `input` in four and
`output` in plan. The `required_by_step` keys are §2.8's mismatches.

### 2.8 Step-id cross-reference matrix

| MT | runtime `steps` | step dirs | action dirs | expected-artifacts keys | mission.yaml phases |
|---|---|---|---|---|---|
| software-dev | discovery, specify, plan, tasks, analyze, implement, review, accept | accept, analyze, charter, implement, plan, research, review, specify, tasks, tasks-finalize, tasks-outline, tasks-packages | implement, plan, **retrospect**, review, specify, tasks | **discovery** ([]), specify, plan, **tasks_outline, tasks_packages, tasks_finalize**, implement ([]), review ([]), **done** ([]) | research, design, implement, test, review |
| documentation | discover, audit, design, generate, validate, publish, accept | + retrospect | = step dirs | = runtime | discover…publish (no accept) |
| research | scoping, methodology, gathering, synthesis, output, **accept** | gathering, methodology, output, retrospect, scoping, synthesis (**no accept**) | = step dirs | scoping…output, **done** ([]) | question, methodology, gather, analyze, synthesize, publish |
| plan | specify, research, plan, review | = | = | = | = |
| cave-dev | discovery … accept (8) | **none** | none | specify, plan, tasks, analyze ([]), implement ([]), review ([]), accept ([]) | discovery, specification, planning, tasks, analysis, implementation, review, acceptance |
| qa | discovery, specify, plan, tasks, implement, review, accept | = runtime | none | specify, plan, tasks, implement ([]), review, accept | strategy, planning, design, execution, reporting |

Bold = names a step no other family knows, or a missing peer. Step-contract `(mission, action)` pairs (28) each match an
existing step dir 1:1 (all in-sequence steps; none for `analyze`, `accept` (software-dev), `charter`, `research`
(software-dev), `tasks-*`, `retrospect`, `discovery`).

### 2.9 Title and prompt divergences runtime ↔ step.yaml

Title differs in 16 steps (all 6 documentation in-sequence steps "Documentation X" vs "X", all 5 research "Research
Scoping" vs "Scoping", software-dev plan "Implementation Planning"/"Implementation Plan", tasks "Tasks"/"Task Breakdown",
review "Code Review"/"Review", plan specify "Specify"/"Specification", plan plan "Plan"/"Planning"). qa matches 7/7.
Prompt summaries exist in three more places: `prompt.md` frontmatter `description` (28), `mission.yaml`
`commands.<cmd>.prompt`, `mission.yaml` `workflow.phases[].description`.

### 2.10 Graph shards (already round-tripped as `graph-shard`)

- `mission_type.graph.yaml`: nodes `{urn: mission_type:<id>, kind, label}` (label = `display_name`). Edges: 21
  `requires` → `action:<type>/<step>` (all action nodes except the 3 retrospects); 38 `scope` edges
  (directive 2, paradigm 3, procedure 6, styleguide 8, tactic 17, toolguide 2) = gov-profile `selected_*` exactly, with ids
  not stems.
- `action.graph.yaml`: 24 nodes `action:<type>/<step>`, label = step id. Edges: 163 `scope` (directive 68, tactic 66,
  procedure 11, paradigm 9, styleguide 5, agent_profile 3, toolguide 1) ≈ action indexes; 3 files differ:
  `documentation/generate` (graph adds DIRECTIVE_042), `software-dev/review` (graph adds 17 implement-ish items:
  DIRECTIVE_001/024/025/028/029/034/036, 4 paradigms, 4 procedures, 2 tactics), `software-dev/specify` (graph adds
  `paradigm:domain-driven-design`). 11 `instantiates` → `template:<type>/<file>` (8 = step.yaml `template` refs; 3 C4
  mermaid templates only in the graph, with a `reason`).
- Fragments: cave/qa `{id, kind: mission_types, title: "<X> Mission Type", body_path: mission_types/<id>/governance-profile.yaml}`
  — `body_path` points at a file PR-B retires.

---

## 3. How core consumes each family (meaning only)

| Family | Core reader(s) | What it means to core |
|---|---|---|
| `mission_types/<id>.yaml` | `charter/offering/missions/mission_type_repository.py` (`MissionType`, `extra=forbid`, id == stem; org subdir `mission_types`, project `.kittify/missions/mission_types`) | Identity, label, `requires_substantive_spec` (None ⇒ built-in default map), optional `extends`, `path_conventions`; `action_sequence` now *projected* from steps; `template_set` retired ("duplicating the step authority") |
| `step.yaml` | `mission_step_repository.py` (`MissionStep`, compound key `(mission_type_id, step_id)`, project > org > built-in), `step_projection.py` | `project_action_sequence` = steps with `in_action_sequence` sorted by `sequence_index`; `project_template_set` = `{artifact_key: template_file}` from `template`; `agent_profile` doubles as "recommended role"; `prompt_template` "always required" (blank placeholder allowed) |
| `mission-runtime.yaml` | `runtime/next/runtime_bridge_io.py` (preferred over mission.yaml), `kernel/mission_runtime_shape.py`, `resolve_runtime_shape_steps` | The planner DAG `spec-kitty next` walks. For a custom type with no step dirs (cave-dev) core *synthesizes* MissionSteps from runtime ids (display_name = id, prompt "" , `sequence_index` = position). `implement`/`review`/`specify`/`plan` ids are special-cased; software-dev splits `tasks` back into outline/packages/finalize in `runtime_bridge_composition.py` |
| `mission.yaml` | `specify_cli/mission.py` (`MissionConfig`: name, description, version semver, domain enum, workflow, artifacts, paths, validation, mcp_tools, agent_context, task_metadata, commands, task_types), `charter/activation/resolver.py` tier chain | Display/acceptance config. `artifacts.optional` used by acceptance `_missing_artifacts` (required is hard-coded spec/plan/tasks); `paths` validated against `VALID_PATH_KEYS`; `task_metadata` by `validate_tasks`; `mcp_tools`, `phases` shown by `mission_type` CLI; `validation.checks` by preflight/lint. v1 FSM keys ignored |
| `governance-profile.yaml` | `charter/activation/mission_type_profiles.py` (`MissionTypeProfile`), `mission_type_profile_repository.py`, `action_grain.py`, `offering/drg/org_pack_loader.py` | The **type grain** of governance: `selected_*` union with the **action grain** (action indexes) into `ResolvedGovernance`; a URN in both grains is `CrossGrainDoubleDeclarationError` (no overlap at the pin). `template_set` is the charter template-set scalar, a different object from the retired MT field |
| `actions/<a>/index.yaml` | `charter/offering/missions/action_index.py` (`ActionIndex`; absent ⇒ empty, malformed ⇒ error), `repository.get_action_index`, `action_grain.py`, DRG `migration/extractor.py` (graph source) | Action-grain doctrine loaded for an action (= step) |
| `guidelines.md` | `repository.get_action_guidelines` (`mission-steps/<m>/<a>/guidelines.md`, single owner since #5202) | Governance guidelines injected at runtime for that action |
| `prompt.md`, `command-templates/*.md` | `repository.get_command_template`: legacy `missions/<m>/command-templates/<name>.md` first, then `mission-steps/<m>/<name>/prompt.md` | The step prompt. This fallback is why qa's dangling `prompt_template: prompt.md` still works in core |
| `expected-artifacts.yaml` | `charter/offering/missions/expected_artifact_manifest.py`, `activation/manifest_loader.py`, `org_expected_artifacts.py`, `specify_cli/runtime/resolver.required_artifacts_for`, `runtime_bridge_cores.evaluate_guards_strict` | Step guards: blocking `path_pattern`s must exist before advancing; dossier completeness scan; `manifest_version` feeds a sync `NamespaceRef` |
| `plan-field-declaration.yaml` | `specify_cli/missions/_substantive.py` (`_pack_provided_declaration`) | Plan-substantive gate: `primary` + ≥1 `peers` field shapes (table_field/any_bold_field/…) in the plan template |
| `built-in.source.yaml` | `charter/offering/pack_paths.py`, `packs/pack_pin.py`, `kernel/pack_tiers.py` | Core's fetch pin / provenance of the built-in pack; `requires_core`. Not mission tier |
| step contracts | `charter/offering/missions/step_contracts.py`, `runtime_bridge_composition.py` | Already modelled in the charter (`MissionStepContract`, `(mission, action)` ↔ MS key) |

---

## 4. Proposed mapping into `mission_type` and `mission_step`

### 4.1 Proposed target shapes (candidate, for the spec)

```yaml
# mission_types/<id>.mission-type.yaml   (DP-1: file name / location)
schema_version: "1.0"
id: software-dev                       # = file stem (S1)
title: Software Development            # S10
description: Build high-quality software with structured workflows and TDD.   # S11
content: |                             # S12, was mission.yaml agent_context (optional)
  You are a software development agent ...
requires_substantive_spec: true        # optional (DP-5: const true vs bool)
domain: software                       # optional (DP-6: keep or drop)
steps: [discovery, specify, plan, tasks, analyze, implement, review, accept]   # ordered sequence; ids of mission_steps (DP-2)
path_conventions: {workspace: src/, tests: tests/, deliverables: contracts/, documentation: docs/}   # closed object
expected_artifacts:                    # mission-wide (was required_always + optional_always (+ mission.yaml artifacts))
  - id: evidence.research
    artifact_class: evidence
    path_pattern: research.md          # blocking omitted = not blocking (DP-7)
references:                            # type grain (was governance-profile selected_*), S14
  - {type: directive, id: DIRECTIVE_042}
  - {type: tactic, id: common-docs-find}
```

```yaml
# mission_steps/<mission_type>/<id>.mission-step.yaml   (DP-1)
schema_version: "1.0"
id: plan                               # = file stem (S1)
mission_type: software-dev             # owner; compound identity (DP-3)
title: Implementation Planning         # S10 (DP-4: which title wins)
description: Design technical architecture.    # S11
role: planner                          # S15, ADR-0032 (was agent-profile / agent_profile)
prompt_path: plan.prompt.md            # S13, markdown companion (was prompt_template / command-templates)
guidelines_path: plan.guidelines.md    # S13, optional companion
template: {artifact_key: plan, template_path: ../../missions/software-dev/templates/plan-template.md}   # DP-8
expected_artifacts:                    # was expected-artifacts required_by_step.<step>
  - {id: output.plan.main, artifact_class: output, path_pattern: plan.md, blocking: true}
references:                            # action grain (was actions/<step>/index.yaml), S14
  - {type: directive, id: DIRECTIVE_003}
```

### 4.2 Field-by-field mapping

#### `mission_types/<id>.yaml`
| Source | Target | Rationale |
|---|---|---|
| `schema_version: 1` | MT `schema_version: "1.0"` | S2 |
| `id` | MT `id` | S1 |
| `display_name` | MT `title` | S10; wins over `mission.yaml name` / runtime `mission.name` because it equals the graph label (DP-4) |
| `requires_substantive_spec` | MT `requires_substantive_spec` | kept |

#### `governance-profile.yaml` (RETIRED)
| Source | Target | Rationale |
|---|---|---|
| `id`, `mission_type` | dropped | repeat MT `id` (S5) |
| `template_set` | dropped | charter-level template-set scalar, null in 5/6; core itself retired the MT copy (DP-9 if operator wants it) |
| `selected_{directives,tactics,paradigms,styleguides,toolguides,procedures}` | MT `references[] {type, id}` | type grain, S14; directive stems normalized to ids (`042-common-docs` → `DIRECTIVE_042`) |
| `selected_agent_profiles` | dropped from MT; meaning moves to **preset** `activated_agent_profiles` (qa) | ADR-0032 decision 1: "a mission type does not name profiles" |
| `selected_mission_step_contracts`, `available_tools`, `activations` | dropped | always `[]` (S5, S16); activations belong to charter/presets |

#### `missions/<id>/mission.yaml` (RETIRED)
| Source | Target | Rationale |
|---|---|---|
| `name` | dropped (or alias of `title`) | third spelling of the label (S6) |
| `description` | MT `description` | the only summary for 5 MTs |
| `version` | dropped | conflicts with runtime version; pack `pack_version` versions content (DP-10) |
| `domain` | MT `domain` (optional enum) or dropped | DP-6 |
| `workflow.phases[]` | dropped | stale display list that matches no step ids; `steps` + MS `title`/`description` replace it |
| `artifacts.required/optional` | folded into MT/MS `expected_artifacts` | duplicate of expected-artifacts; directories (`contracts/`) become `path_pattern: contracts/**` |
| `paths.*` | MT `path_conventions` (closed object, 5 keys) | = core `MissionType.path_conventions` |
| `validation.checks` | DP-11: MT `validation_checks` or dropped | names of core-coded checks; no pack can define one |
| `validation.custom_validators` | dropped | names a `validators.py` no pack ships |
| `mcp_tools.*` | DP-11: MT `mcp_tools {required, recommended, optional}` or dropped | advisory, CLI display only |
| `agent_context` | MT `content` | S12 body |
| `task_metadata.*` | DP-11: drop (WP frontmatter is a core concern) or MT `task_metadata` | |
| `commands.<cmd>.prompt` | dropped | keyed by core command names, not step ids; each step already has `description` + prompt |
| `mission/initial/states/transitions` (qa) | dropped | self-declared dead v1 FSM block |

#### `missions/<id>/mission-runtime.yaml` (RETIRED)
| Source | Target | Rationale |
|---|---|---|
| `mission.key` | dropped | = MT `id` (S1/S5) |
| `mission.name`, `mission.title` | dropped | label conflict (DP-4) |
| `mission.version` | dropped | DP-10 |
| `mission.description` (plan) | MT `description` if mission.yaml lacks one; else dropped | |
| `mission.steps[]` (plan nested copy), `order` | dropped | duplicate of `steps[]` (S5) |
| `mission.runtime.*` (plan) | dropped | `loop_type: sequential` / `terminal_step` derivable from `steps`; `prompt_template_dir` names a dir that does not exist |
| `steps[]` order | MT `steps` (ordered id list) | the planner sequence becomes one ordered list (S6) |
| `steps[].id` | MS `id` (+ MT `steps` entry) | |
| `steps[].title` | MS `title` | DP-4: runtime title vs step.yaml `display_name` (16 differ) |
| `steps[].description` | MS `description` | |
| `steps[].depends_on` | dropped (derived from MT `steps` order, S8) | all six DAGs are linear; DP-2 if a real DAG must be expressible |
| `steps[].prompt_template` | MS `prompt_path` | S13; software-dev `discovery` → the `research` prompt (rename DP-12) |
| `steps[]."agent-profile"` | MS `role` = the profile's primary role | ADR-0032 + S15 (researcher-robbie→researcher, architect-alphonso→architect, implementer-ivan→implementer, reviewer-renata→reviewer, analyst-annie→analyst, planner-priti→planner, qa-strategist-quinn→qa-strategist, qa-test-designer-tessa→test-designer, qa-test-executor-elena→test-executor, qa-reporter-rhea→qa-reporter) |

#### `mission-steps/<type>/<step>/step.yaml` (RE-SHAPED into MS)
| Source | Target | Rationale |
|---|---|---|
| `id` | MS `id` | S1 |
| (dir `<type>`) | MS `mission_type` | compound identity made explicit (DP-3) |
| `display_name` | MS `title` | S10 / DP-4 |
| `step_type` | dropped (DP-13) | always `agent`; human steps say `role: operator` (S15) |
| `prompt_template: prompt.md` | MS `prompt_path` | S13 |
| (implicit `guidelines.md`) | MS `guidelines_path` (optional) | S13: every companion named by a field |
| `agent_profile` | MS `role` | ADR-0032; null ⇒ absent ("any role") |
| `guidance` | dropped | null 37/37 (S5) |
| `delegates_to` | dropped; meaning lives in step contracts' `delegates_to` | `[]` 37/37 (S5, S16) |
| `depends_on` | dropped | contradicts runtime in 15/37; derived from MT `steps` |
| `sequence_index` | dropped; position in MT `steps` | S6 one spelling of order |
| `in_action_sequence` | dropped; membership in MT `steps` | S6 |
| `template.artifact_key` / `.template_file` | MS `template {artifact_key, template_path}` or a ref `{type: template, id: <type>/<file>}` | S13 / DP-8 (graph URNs already `template:software-dev/plan-template.md`) |

#### `actions/<a>/index.yaml` (RETIRED)
| Source | Target | Rationale |
|---|---|---|
| `action` | dropped | = MS `id` |
| `directives/tactics/paradigms/styleguides/toolguides/procedures` | MS `references[] {type, id}` | action grain, S14; stems → ids; never minted as edges until #189 (graph `scope` edges remain the edge source) |
| `agent_profiles` (retrospective-facilitator) | MS `role: facilitator` on the retrospect step | ADR-0032 |
| empty lists | omitted | S16 |
| alternative | moves to **step-contract** | DP-14: the step contract already delegates to doctrine by kind with `candidates`; folding the index there would leave MS with no references |

#### `expected-artifacts.yaml` (RETIRED)
| Source | Target | Rationale |
|---|---|---|
| `schema_version` | (MT's own) | |
| `mission_type` | dropped | = owner (S5) |
| `manifest_version` | dropped | core sync-namespace identity, not content (S5) |
| `required_always` | MT `expected_artifacts` with `blocking: true` | `[]` 6/6, so nothing to migrate today |
| `optional_always[]` | MT `expected_artifacts` (no `blocking`) | |
| `required_by_step.<step>[]` | MS `expected_artifacts` of that step | `[]` lists omitted (S16) |
| `required_by_step.done` | dropped | always `[]`, names no step |
| `required_by_step.discovery` (software-dev) | dropped | `[]` |
| `required_by_step.tasks_outline/_packages/_finalize` | MS `expected_artifacts` of `tasks-outline`/`tasks-packages`/`tasks-finalize` | snake→kebab id fix |
| entry `artifact_key` | entry `id` | S1 (DP-7) |
| entry `artifact_class` | `artifact_class` (enum) | |
| entry `path_pattern` | `path_pattern` | |
| entry `blocking` | `blocking: true` const, absent = non-blocking | S6 (DP-7); `blocking: false` inside required_by_step (doc `generate`) becomes a non-blocking step entry |

#### Others
| Source | Disposition |
|---|---|
| `prompt.md` (30) | MS markdown companion named by `prompt_path`; frontmatter `description` (28) → dropped or MS `description` (DP-4); 2 empty files: kept as blank companion or MS without prompt (DP-15) |
| `guidelines.md` (17) | MS markdown companion named by `guidelines_path` |
| `command-templates/*.md` (cave 8, qa 7) | MS prompt companions (moved beside the MS, renamed `<id>.prompt.md`) |
| `templates/plan-field-declaration.yaml` (qa) | DP-16: moves to MS `plan` (`template.substance {primary, peers}`), or stays excluded as a template sidecar |
| `missions/<id>/templates/**` | not MT/MS: `template` payloads; needs a `*/missions/*/templates/**/*` payload rule (gate currently pins them `other:missions`) |
| `built_in_step_contracts/__init__.py` | dropped (0-byte Python packaging residue); exclusion until open-packs deletes it |
| `missions/**/README.md` | exclusion (orientation notes, as ADR-0040 R4 for `assets/**/README.md`); `missions/README.md` describes the retired layout and should be rewritten upstream |
| `built-in.source.yaml` | not mission tier; stays excluded (repo-root, outside any pack) |
| `mission_type.graph.yaml` nodes | unchanged kind; `label` must equal MT `title` |
| `action.graph.yaml` nodes `action:<type>/<step>` | DP-17: keep node kind `action` with URN = MS compound key, or rename to `mission_step` |
| fragment `mission_types` nodes | `body_path` → the new MT file |

---

## 5. Divergence table (per pinned file)

Every file below is refused by a closed MT/MS schema as pinned. "Retire" = file deleted after its meaning is migrated.

### 5.1 Mission types (6)

| ID | File(s) | Breaks | Migration |
|---|---|---|---|
| D-MT1 | all 6 `mission_types/<id>.yaml` | S2 (`schema_version: 1` int) | `"1.0"` |
| D-MT2 | all 6 | S10 (`display_name`) | → `title` |
| D-MT3 | all 6 | S11: no `description` | pull from `mission.yaml` |
| D-MT4 | built-in 4 | layout: `missions/mission_types/` vs org `mission_types/` | one location (DP-1) |
| D-MT5 | cave-dev, qa | no `requires_substantive_spec` | absent stays absent (DP-5) |

### 5.2 Governance profiles (6) — retire

| ID | File | Breaks | Migration |
|---|---|---|---|
| D-GP1 | all 6 | S5 (`id` = `mission_type`, both repeat the MT id) | drop |
| D-GP2 | all 6 | S16 (`[]` lists: 3 always-empty keys ×6, plus 26 more empty `selected_*`) | omit |
| D-GP3 | software-dev, documentation, plan, research | S5/S16 `template_set` (null ×5, `software-dev-default` ×1) | drop (DP-9) |
| D-GP4 | documentation (`042-common-docs`), plan (`031-context-aware-design`) | S1/S6 directive by file stem | `DIRECTIVE_042`, `DIRECTIVE_031` |
| D-GP5 | qa | ADR-0032 (`selected_agent_profiles` ×5) | move to qa preset `activated_agent_profiles` |
| D-GP6 | all 6 | S3/S14 (`selected_<plural>` lists of bare strings) | MT `references {type, id}` |
| D-GP7 | cave-dev, qa | layout `mission_types/<id>/governance-profile.yaml` + fragment `body_path` | repoint fragment `body_path` to the MT file |

### 5.3 `mission.yaml` (6) — retire

| ID | File | Breaks | Migration |
|---|---|---|---|
| D-MY1 | all 6 | S1/S6 `name` ≠ `display_name` (4 of 6 differ) | drop |
| D-MY2 | all 6 | S5 `version` conflicts with runtime (4 of 6 differ from the runtime; plan inverted) | drop (DP-10) |
| D-MY3 | all 6 | S5 `workflow.phases` disagree with step ids | drop |
| D-MY4 | all 6 | S6 `artifacts` duplicates expected-artifacts | fold |
| D-MY5 | 4 built-in | S5 `validation.custom_validators` (no validators.py) | drop |
| D-MY6 | 4 built-in | S12 `agent_context` | → `content` |
| D-MY7 | 4 built-in | `commands` keyed by core command names, not step ids | drop |
| D-MY8 | qa | S5 v1 FSM block (`mission/initial/states/transitions`) | drop |
| D-MY9 | 3-4 built-in | `mcp_tools`, `task_metadata`, `validation.checks`: no home | DP-11 |
| D-MY10 | cave-dev, qa | `validation.checks: []`, cave `artifacts.optional: []` | S16 omit |

### 5.4 `mission-runtime.yaml` (6) — retire

| ID | File | Breaks | Migration |
|---|---|---|---|
| D-RT1 | all 6 | S1 `mission.key` | drop (= MT id) |
| D-RT2 | 4 with profiles | S9 `agent-profile` kebab key; ADR-0032 profile not role | MS `role` |
| D-RT3 | all 6 | S5/S8 `depends_on` (linear, derivable) | MT `steps` order |
| D-RT4 | plan | S5 nested `mission.steps[]` copy + `order`, `mission.title` = `name`, `mission.runtime` | drop |
| D-RT5 | plan | `prompt_template_dir: command-templates` (dir absent), no `prompt_template` per step | MS `prompt_path` to `mission-steps/plan/<id>/prompt.md` |
| D-RT6 | software-dev | step `discovery` has no step dir; prompt via `research.md` | create MS `discovery` (prompt from `research/prompt.md`) or rename (DP-12) |
| D-RT7 | research | step `accept` has no step dir and no prompt file anywhere (`accept.md` unresolvable) | author MS `research/accept` + prompt, or drop from sequence (DP-15) |
| D-RT8 | cave-dev | 8 runtime-only steps, no step.yaml | create 8 MS from runtime + command templates |
| D-RT9 | documentation, research, software-dev, plan | 16 titles differ from step.yaml | one `title` (DP-4) |

### 5.5 `step.yaml` (37) — reshape into MS

| ID | File(s) | Breaks | Migration |
|---|---|---|---|
| D-MS1 | 37 | S10 `display_name` | → `title` |
| D-MS2 | 37 | S5 `guidance: null` | drop |
| D-MS3 | 37 | S5/S16 `delegates_to: []` | drop |
| D-MS4 | 30 built-in | S5 `agent_profile: null` | drop (no role) |
| D-MS5 | 7 qa | ADR-0032 `agent_profile` (profile id) | `role` |
| D-MS6 | 37 | S6 `sequence_index` + `in_action_sequence` (two spellings of order/membership; `sequence_index` absent ×2, null ×7) | MT `steps` |
| D-MS7 | 15 | `depends_on` contradicts runtime (documentation 6, research 4, plan 3, software-dev 2: specify, implement) | drop, derive |
| D-MS8 | 22 | S16 `depends_on: []` | drop |
| D-MS9 | 37 | S13 `prompt_template` is not `<role>_path`; guidelines companion unnamed (17) | `prompt_path`, `guidelines_path` |
| D-MS10 | 7 qa | `prompt_template: prompt.md` names a missing file (prompts in `missions/qa/command-templates/`) | move prompts, `prompt_path` |
| D-MS11 | 13 | S13 `template.template_file` is a bare name resolved by a convention | `template_path` / template ref (DP-8) |
| D-MS12 | 37 | S2 no `schema_version`; S1 compound identity only in the path | add `schema_version`, `mission_type` (DP-3) |
| D-MS13 | 37 | S5 `step_type: agent` constant | drop (DP-13) |
| D-MS14 | software-dev `charter`, `research`, `tasks-outline/-packages/-finalize`; documentation/research `retrospect` | in no runtime; reachable only by direct invocation | MS outside MT `steps` (DP-2) |

### 5.6 `actions/*/index.yaml` (24) — retire

| ID | File(s) | Breaks | Migration |
|---|---|---|---|
| D-AI1 | 24 | S1 `action` repeats dir/MS id | drop |
| D-AI2 | 20 with directives | S1/S6 directive file stems (60 refs) | ids |
| D-AI3 | 24 | S16 empty lists (78 `[]`) | omit |
| D-AI4 | 3 retrospect | ADR-0032 `agent_profiles` | MS `role: facilitator` |
| D-AI5 | software-dev `retrospect` | action index with no step | create MS `software-dev/retrospect` or drop (DP-15) |
| D-AI6 | 3 (documentation/generate, software-dev/review, software-dev/specify) | index ≠ `action.graph.yaml` scope edges | reconcile upstream; edge authority is #189 |

### 5.7 `expected-artifacts.yaml` (6) — retire

| ID | File | Breaks | Migration |
|---|---|---|---|
| D-EA1 | all 6 | S5 `mission_type`, `manifest_version` | drop |
| D-EA2 | all 6 | S16 `required_always: []`; 13 empty step lists; `optional_always: []` ×2 | omit |
| D-EA3 | software-dev | step keys `tasks_outline/_packages/_finalize` (snake) ≠ step ids (kebab); `discovery`, `done` | rename / drop |
| D-EA4 | research | `done` names no step; `accept` step has no entry | drop |
| D-EA5 | software-dev, qa | duplicate artifact keys / paths (`output.tasks.per_wp` ×2; qa-coverage.json, qa-traceability.yaml ×2) | decide identity (DP-7) |
| D-EA6 | all 6 | S1 `artifact_key`; S6 `blocking: false` vs absent | `id`; `blocking: true` const |
| D-EA7 | documentation | `blocking: false` inside `required_by_step.generate` | non-blocking step entry |

### 5.8 Other files

| ID | File | Breaks | Migration |
|---|---|---|---|
| D-X1 | qa `templates/plan-field-declaration.yaml` | no schema; S2 no `schema_version` | DP-16 |
| D-X2 | built-in `built_in_step_contracts/__init__.py` | not pack content | delete upstream; exclusion meanwhile |
| D-X3 | 13 `README.md` under `missions/` | not pack content | exclusion; rewrite `missions/README.md` |
| D-X4 | 2 empty `prompt.md` (documentation/research retrospect) | empty companion | DP-15 |
| D-X5 | 28 `prompt.md` frontmatter `description:` | Markdown model refuses frontmatter (ADR-0043 guide rules, if reused) | drop or lift to MS `description` |
| D-X6 | cave/qa fragment `mission_types` nodes | `body_path` → retired file; title "X Mission Type" ≠ MT title | repoint, align |
| D-X7 | 45 `missions/*/templates/**` | not classified as `template` | payload rule (outside MT/MS) |
| D-X8 | step contracts: software-dev ids `specify`, `plan`… unprefixed vs `plan-specify`, `research-scoping`, `qa-plan` | id scheme inconsistent (not PR-B's kind, affects MS↔contract linking) | note only |

---

## 6. Edge cases

1. **cave-kitty layout.** `mission_types/cave-dev.yaml` (3 keys) + `mission_types/cave-dev/governance-profile.yaml` (a
   directory named like the type beside its file — the core org-pack layout, `ORG_MISSION_TYPES_SUBDIR`) +
   `missions/cave-dev/{mission,mission-runtime,expected-artifacts}.yaml` + `command-templates/` (8). No step.yaml, no
   action index, no step contract. Core synthesizes its steps from the runtime ids. In the sanitized model cave-dev needs 8
   new MS files built from the runtime (`title`, `description`, `role` from profiles: researcher, analyst, planner ×2,
   reviewer ×3, implementer) with the command templates as prompt companions. Its comments state it "re-voices
   software-dev": a candidate for core's `extends: software-dev` (DP-18), but its steps carry their own titles, prompts and
   roles, so `extends` would not save content. Its MT reference set is `{directive CAVE_001, styleguide
   cave-sty-001-communication}`. qa has the same split layout plus 7 step.yaml.
2. **Step dirs without `guidelines.md`** (13): software-dev accept, analyze, charter, research, tasks-finalize,
   tasks-outline, tasks-packages; documentation retrospect; research retrospect; all 4 plan steps. qa has neither
   `prompt.md` nor `guidelines.md` (7). ⇒ `guidelines_path` optional; `prompt_path` required? (DP-15: qa's prompts exist
   elsewhere; research `accept` has none.)
3. **Steps in no runtime** (7): software-dev `charter`, `research` (reachable as the `discovery` prompt only),
   `tasks-outline`, `tasks-packages`, `tasks-finalize` (the runtime collapses them into `tasks`; core
   `runtime_bridge_composition` re-splits them; expected-artifacts gates them under snake keys); documentation and research
   `retrospect` (no `sequence_index` at all, empty prompt, action index scoped to `retrospective-facilitator`). They are
   steps the CLI calls directly (`/spec-kitty.charter`, `tasks-outline`…), so they must still exist as MS but outside MT
   `steps` (DP-2: a second list such as `auxiliary_steps`, or MS-only with no MT listing).
4. **Runtime steps without a step dir** (10): software-dev `discovery`, research `accept`, cave-dev 8.
5. **`analyze` and software-dev `accept`** are in the runtime DAG but `in_action_sequence: false` — the "action sequence"
   (governed actions with index + contract) and the planner sequence are different lists in core. The sanitized MT has
   one `steps` list (the planner's); whether a step is "governed" is then just "has references / a step contract".
6. **README.md files**: 13 under `missions/` (built-in 12, qa 1). `missions/README.md` documents the retired layout
   (mission.yaml + mission-runtime.yaml + actions). Exclude like ADR-0040 R4.
7. **`built_in_step_contracts/__init__.py`**: 0 bytes, Python package marker from core's former in-tree packaging; the
   step contracts beside it are already a modelled kind. Exclude now, delete upstream.
8. **plan mission**: its runtime has no prompts and points `prompt_template_dir` at a non-existent dir; the prompts are
   only in `mission-steps/plan/*/prompt.md` (resolved by core's fallback). Its `mission.yaml` version (2.0.0) is newer
   than its runtime (1.0.0).
9. **Charter repo `.kittify/missions/**`** holds installed copies of governance profiles, expected-artifacts and plan
   action indexes; they are project state, not packs, and outside PR-B — but they will drift from the migrated packs.
10. **`qa-defect-triager-dmitri`** is selected by qa's governance profile but bound to no step; dropping
    `selected_agent_profiles` without a preset entry loses its activation.

---

## 7. Decision points for the operator

| DP | Question | Recommendation |
|---|---|---|
| DP-1 | File names/locations: `mission_types/<id>.mission-type.yaml`, `mission_steps/<type>/<id>.mission-step.yaml`, companions beside? One layout for built-in and org packs? | One layout, suffix-typed like other kinds |
| DP-2 | Order: MT `steps` list vs MS `depends_on` DAG; how to hold out-of-sequence steps | MT `steps` (ordered); out-of-sequence MS exist without a listing; no DAG until a non-linear one exists |
| DP-3 | MS identity: `(mission_type, id)` with `id` = stem, or prefixed id (`research-scoping`) like step contracts | compound key, `mission_type` field |
| DP-4 | Which label/summary wins: runtime title vs step.yaml display_name; MT display_name vs mission.yaml name; prompt frontmatter description | MT: `display_name`; MS: runtime `title` (it is what users see in `next`), runtime `description` |
| DP-5 | `requires_substantive_spec`: bool or `const: true` | `const: true` (S6), absent = not required |
| DP-6 | Keep `domain`? | drop unless a consumer needs it |
| DP-7 | Artifact entry identity (`id` vs `artifact_key`), `blocking` spelling, duplicates | `id`, `blocking: true` const, unique per MT |
| DP-8 | Step template: `{artifact_key, template_path}` vs ref `{type: template, id}` | ref, matches graph `instantiates` URNs |
| DP-9 | Gov-profile `template_set` (software-dev only) | drop |
| DP-10 | Any `version` on MT | drop; pack version governs |
| DP-11 | `mcp_tools`, `task_metadata`, `validation.checks` | drop (core-coded or advisory), or keep as closed optional objects |
| DP-12 | software-dev `discovery` vs step dir `research` | one MS `discovery` with the research prompt; retire `research` dir |
| DP-13 | `step_type` | drop; `role: operator` for human steps |
| DP-14 | Action index → MS `references` or → step contract | MS `references` (contracts are per-step procedures, indexes are scope) |
| DP-15 | Missing/empty prompts (research accept; 2 empty retrospect prompts; qa) | `prompt_path` optional; empty prompt files dropped |
| DP-16 | plan-field-declaration | fold into MS `plan` template entry |
| DP-17 | Graph node kind `action` vs `mission_step` | keep `action` URNs until #189; map to MS key |
| DP-18 | cave-dev `extends: software-dev` | not needed; author its 8 MS |
