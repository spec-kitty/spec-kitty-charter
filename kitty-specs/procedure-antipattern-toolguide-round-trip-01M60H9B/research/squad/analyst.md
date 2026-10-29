# Analyst (analyst-annie) - empirical content inventory, charter #194

Profile applied: analyst-annie (role analyst; verbs elicit/formalize/translate-requirements; modes analysis-mode, precision-pass; directives 003, 047, 032). Boundaries honoured: no implementation/architecture decisions, no schema design (field-level sanitized shape is left to the datamodel delegate); every claim is traceable to a file or script. Charter context (research action, 1.4.0): directives 001/003/010/025/030/032/034/037/041/051, DDD + specification-by-example paradigms. READ-ONLY.
Pack checkout: /home/user/spec-kitty-open-packs @ 01edb4e (verified, untouched). Scripts/raw output: scratchpad/squad/{inv.py,inv.txt,val.py,ap.py,d1.py}.

## 0. Headline
1. All 41 procedures, 34 styleguides, 137 tactics, 28 step-contracts and 14 graph shards ALREADY validate against the merged charter schemas (val.py: 0 invalid). Divergence is therefore NOT in the kinds with schemas; it is in (a) anti_pattern (no file kind, no schema, two inline shapes + graph-only nodes), (b) every mission-type-tier file (no schema at all: file-kinds.yaml puts them in `exclusions`), (c) profile-vs-role (ADR-0032) leakage.
2. anti_pattern graph nodes and inline anti-patterns do not overlap at all (0 of 228 inline names match any of the 13 graph labels).
3. docs/artefacts (open-packs) states "Anti-pattern: inside other artefacts / the DRG; not a standalone file."

## 1. File inventory (packs/*, 516 yaml, 155 md, 11 py, 3 otf, 2 csv, 2 template, 2 gitkeep, 1 each png/html/feature)
### Procedures (41 files, all `*.procedure.yaml`, all YAML) - located ONLY under `packs/<pack>/procedures/`
built-in 23 | internal 13 | qa 5 | event-driven/cave-kitty 0. No procedure file elsewhere; `procedure.graph.yaml` (built-in, 23 nodes/110 edges) covers only the 23 built-in. No nested dirs.
### anti_pattern content
- Inline `anti_patterns:` key: 54 files = 28 styleguides (102 entries) + 26 procedures (126 entries) = 228 entries (225 unique names). NO other kind carries the key (tactics use `failure_modes`, see 3.5; paradigm/agent files only mention the word in prose).
- Graph: `packs/built-in/anti_pattern.graph.yaml` (13 nodes, `edges: []`). No other pack has an anti_pattern graph or fragment node. No standalone anti-pattern file, directory, extension or schema exists (file-kinds.yaml has no rule; `anti_pattern` is in node_kinds/artifact_kinds/fragment_kinds only).
### Mission types / missions / mission steps
| Location | Files | Types |
|---|---|---|
| built-in/missions/mission_types/ | 4 | yaml (documentation, plan, research, software-dev) |
| qa/mission_types/ , cave-kitty/mission_types/ | 2 + 2 | `<t>.yaml` + `<t>/governance-profile.yaml` (NOTE: different from built-in, see D-9) |
| built-in/missions/{documentation,plan,research,software-dev}/ | 27+13+21+16=77 | per mission: mission.yaml, mission-runtime.yaml, expected-artifacts.yaml, governance-profile.yaml (4 each... plan/research/doc/sd), actions/*/index.yaml (8+4+6+6=24 total), templates/ (md; plus 2 `.template`, 2 csv, `.gitkeep` in plan) |
| built-in/missions/mission-steps/<type>/<step>/ | 77 (30 step.yaml, 47 md) | step.yaml + prompt.md (+ guidelines.md in 17 of 30) |
| built-in/missions/built_in_step_contracts/ | 22 | 21 `*.step-contract.yaml` + EMPTY `__init__.py` (0 bytes, a .py inside a data pack) |
| qa/missions/qa/ | 22 | yaml(4: mission, mission-runtime, expected-artifacts + templates/plan-field-declaration.yaml), 17 md (command-templates + templates), 1 `.feature` |
| qa/mission-steps/qa/<step>/step.yaml | 7 | yaml only (no prompt.md) - sits at pack root, NOT under missions/ |
| qa/mission_step_contracts/ | 7 | `*.step-contract.yaml` (dir name differs from built-in `built_in_step_contracts`) |
| cave-kitty/missions/cave-dev/ | 11 | 3 yaml + 8 md `command-templates/`; NO actions/, NO mission-steps, NO step-contracts |
Totals of in-scope structured yaml parsed: mission_type 6, gov-profile (type-tier) 2, mission.yaml 6, mission-runtime 6, expected-artifacts 6, gov-profile (mission-tier) 4, action index 24, step.yaml 37, step-contract 28 (21 built-in + 7 qa), plan-field-declaration 1.
Mission-step dirs exist for 4 types x steps: documentation 8, plan 4, research 6, software-dev 12 (4 more than the 8 runtime steps), qa 7, cave-dev 0.

## 2. Field inventory (counts = files having the key; full tables in inv.txt)
### 2.1 procedure (41) - clean vs schema
Top: schema_version 41 (str "1.0"), id 41, name 41, purpose 41, entry_condition 41, exit_condition 41, steps 41, references 31 (list of {type,id,reason?}; reason in 27), anti_patterns 26 ({name,description} only), notes 13, applies_to_languages 1 (value `[]`, tracker-organisation-workflow: empty and pointless). Steps (274): title, description, actor (agent 246 / human 28; `system` never used), on_failure 25 files, on_success 3 files.
Anomalies: id==filename slug in 41/41 (good). Inline `references` duplicates the procedure.graph edges: for built-in, 103 of 103 inline refs are also graph edges (graph has 6 more) - duplicated information (schema permits both). `on_success` is used in only 3 files.
### 2.2 styleguide (34) anti_patterns[]: {name, description, bad_example, good_example} all 4 required (102 entries). Compare with procedure anti_patterns {name, description}: same key, two shapes.
### 2.3 mission_type (6) keys: schema_version (INT 1 - every other kind uses string "1.0"), id, display_name, requires_substantive_spec (bool, present in only 4/6: absent from qa and cave-dev).
### 2.4 type-tier governance-profile (2: qa, cave-dev): id, mission_type, template_set (null), selected_{directives,agent_profiles,tactics,styleguides,toolguides,procedures,paradigms,mission_step_contracts}, available_tools, activations (all `[]` except a few). Mission-tier governance-profile (4 built-in) has the identical key set. No schema_version at all. `id == mission_type` is an INVARIANT stated only in a YAML comment.
### 2.5 mission.yaml (6) - three different shapes
- built-in 4 + cave-dev: name, description, version (str), domain, workflow.phases[{name,description}], artifacts{required,optional}, validation{checks,custom_validators}, agent_context(4), commands{specify,plan,...: {prompt}}(4), paths(4), mcp_tools(3), task_metadata(3).
- cave-dev lacks agent_context/commands/paths; qa additionally carries `mission`, `initial`, `states[{name,display_name}]`, `transitions[{from,to}]` marked in a comment "NEVER READ BY THE RUNTIME".
- No `id`/`key`/`schema_version`. Human name field `name` ("Software Dev Kitty") is NOT the mission-type `display_name` ("Software Development").
### 2.6 mission-runtime.yaml (6): mission{key,name,version(+title,description,runtime{loop_type,prompt_template_dir,step_transition,terminal_step},steps[] only in plan)}, steps[]{id,title,description,depends_on,prompt_template(5/6),agent-profile(4/6)}. `mission.key` duplicates mission_type.id; `mission.name`, `mission.version` duplicate mission.yaml name/version (versions DISAGREE: documentation 1.0.0 vs 2.0.0, research 1.0.0 vs 2.0.0, software-dev 1.0.0 vs 2.2.0, plan 2.0.0 vs 1.0.0). plan has a mirrored `mission.steps` (id,name,description,order,depends_on) explicitly "for legacy readers" and a `runtime` block no other file has.
### 2.7 expected-artifacts (6): schema_version "1.0" AND manifest_version "1" (two version fields), mission_type, required_always, optional_always[{artifact_class,artifact_key,blocking,path_pattern}], required_by_step{<step-id>: [...]}. Step-key spelling: `tasks_finalize`, `tasks_outline`, `tasks_packages` (underscore) vs step ids `tasks-finalize` etc. (hyphen). Keys `done`, `analyze`, `discovery` appear with no matching step in that mission.
### 2.8 action index (24): action, directives, tactics, styleguides, toolguides, procedures (all 24), paradigms (5), agent_profiles (7 files key present, 3 non-empty: all `retrospective-facilitator`). No schema_version/id.
### 2.9 step.yaml (37): id, display_name, step_type (always "agent"), prompt_template, agent_profile (str in 7 [all qa], null in 30), guidance (null in 37/37), delegates_to ([] in 37/37), depends_on, in_action_sequence (bool), sequence_index (int 28 / null 7 / ABSENT in 2: the two `retrospect` steps), template{artifact_key,template_file} (13). No schema_version.
### 2.10 step-contract (28): id, schema_version "1.0", action, mission, steps[]{id,description,command(46 steps),delegates_to{kind,candidates}(101 of 147 steps; absent in 46),guidance(10)}, gates (1 file). Matches schema's "LEGACY shape" note.
### 2.11 anti_pattern.graph nodes (13): urn, kind, label, tags. Other graph kinds have no `tags` on any node.

## 3. Anomalies and comparison to merged-schema conventions
Cross-kind convention in merged schemas: snake_case keys, `id` + `schema_version` (string) on every artifact, display text under `title` (directive, styleguide, toolguide, asset, skill) or `name` (tactic, paradigm, procedure, agent-profile), kebab-case only in agent-profile (profile-id, directive-references...). Relations are expressed as DRG edges, not inline (ProcedureStep docstring, Daphne profile).
3.1 Naming drift (same concept, different key): label of a thing = `name` (procedure/tactic/mission.yaml/mission-runtime.mission/state), `title` (steps in mission-runtime, mission-runtime plan mission), `display_name` (mission_type, step.yaml, states), `label` (graph nodes). Identity = `id` (most), `key` (mission-runtime.mission.key), `mission_type` (governance-profile, expected-artifacts), `mission` (step-contract.mission, qa mission.yaml.mission), `action` (index/step-contract). Profile ref = `agent_profile` (step.yaml) / `agent-profile` (mission-runtime, kebab!) / `agent_profiles` (action index) / `selected_agent_profiles`.
3.2 Plural/singular: `anti_patterns` (inline) vs `anti_pattern` kind token; `mission-steps/` vs `mission_types/` vs `mission_step_contracts/` vs `built_in_step_contracts/` directory spelling (hyphen vs underscore vs prefix).
3.3 schema_version: int `1` (mission_type), str "1.0" (everything schema-backed, expected-artifacts), absent (mission.yaml, mission-runtime, governance-profile, action index, step.yaml). Also `version` (mission.yaml, mission-runtime) and `manifest_version`.
3.4 Dead/deprecated-looking: step.yaml `guidance: null` x37, `delegates_to: []` x37, `step_type: agent` x37 (constant), `template_set: null` x6, `domain` (mixed values other/software/research), mission-runtime plan `mission.steps` "legacy readers" mirror + `order`, qa mission.yaml `states/transitions` ("NEVER READ BY THE RUNTIME"), `custom_validators` bool, empty `__init__.py`, `applies_to_languages: []`.
3.5 Same concept, different home: tactics carry `failure_modes` (90 tactic files, list of plain str, 367 strings) which is the tactic-tier analogue of `anti_patterns` ({name,description}); semantics are not unified.
3.6 Directive id forms split: action-index `directives` entries are FILENAME SLUGS ("010-specification-fidelity-requirement", 60/60), step-contract `delegates_to.candidates` and most governance-profile entries are IDS ("DIRECTIVE_010"; 86/86 and 7/9); governance-profile mixes (2 slugs in built-in documentation/plan). Tactic/styleguide/procedure refs are consistent ids everywhere.
3.7 ADR-0032 (steps name roles, a mission type does not name profiles): violations: mission-runtime `agent-profile` holds PROFILE ids in 27 steps (documentation, research, cave-dev, qa); step.yaml `agent_profile` in 7 qa steps (qa-reporter-rhea...); governance-profile `selected_agent_profiles` (qa: 5 profiles); action-index `agent_profiles: [retrospective-facilitator]` (3 files); built-in mission README documents "agent-profile assignments". 0 uses of a role-typed field anywhere. Note: ADR states "QA pack pins a profile id on every step".
3.8 Step-id drift between files of one mission: runtime ids vs step dirs vs actions vs contracts disagree (research: runtime has `accept`, steps have `retrospect`; software-dev: runtime `discovery`, steps `research`+`charter`+`tasks-*`, 6 actions, 5 contracts; documentation: contracts lack `retrospect`).
3.9 Mission-tier files exist in two layouts: built-in governance-profile at missions/<t>/, qa/cave-dev at mission_types/<t>/; step.yaml at missions/mission-steps/ (built-in) vs pack-root mission-steps/ (qa); step-contracts in missions/built_in_step_contracts vs mission_step_contracts.

## 4. anti_pattern.graph.yaml structure
`schema_version '1.0'`, `generated_at: STATIC`, `generated_by: drg-migration-v1`, 13 nodes {urn `anti_pattern:<slug>`, kind `anti_pattern`, label (title case), tags}, `edges: []`. The node carries NO body (no description/bad_example). Tags: 8 x `smell`, 5 x `anti-pattern` (a hyphenated, kind-name-collision tag; the only sub-classification, and it is a tag not a field). Only the label is human text; slug==kebab(label) in 13/13.
Linking is inbound only, from OTHER shards, via relation `rejects` with `reason`: tactic.graph (7 edges: refactoring-* tactics -> unencapsulated-record, global-data, implicit-concept, misplaced-field, feature-envy, repeated-switches-on-state, big-bang-rewrite) and paradigm.graph (8 edges: brownfield-onboarding, c4-incremental-detail-modeling, domain-driven-design). All 15 reasons present. No procedure or styleguide has any edge to an anti_pattern node. Orphans: none (all 13 nodes are the target of at least one `rejects` edge; verified by script across tactic/paradigm/procedure/styleguide/directive shards). Both inline shapes are unlinked to the node kind, there is no id/slug on inline anti-patterns, and 3 names repeat across files ("CRUD / affordance echo", "Command-named steps", "Defect framing").
Round-trip implication: the graph shard already round-trips (graph-shard schema + `anti_pattern` node kind). What lacks a file/schema is the inline entries (nested in procedure/styleguide, which already have schemas) - they are covered by the existing procedure/styleguide round trip, if those DSOs model them. #194 "anti_pattern content" therefore is: inline lists (two shapes) + the graph shard.

## 5. Candidate divergence table (confidence H/M/L; sanitized shape belongs to the datamodel delegate)
| # | File(s) | Field/issue | Observed | Likely sanitized expectation | Conf |
|---|---|---|---|---|---|
| D1 | 28 styleguides vs 26 procedures | `anti_patterns` entry shape | styleguide {name,description,bad_example,good_example}; procedure {name,description} | one shape (or explicit documented per-kind variants) with explicit types | H |
| D2 | 90 tactics | `failure_modes` list[str] vs `anti_patterns` list[obj] | different key, string type | align or document as distinct; singular concept naming | M |
| D3 | anti_pattern.graph.yaml | node `tags` smell vs anti-pattern | only kind with tags; hyphen value equals kind name | explicit enum field or drop tags; one spelling | M |
| D4 | anti_pattern.graph.yaml vs inline | no overlap; inline entries have no id | 0/228 names match labels | single identity (slug) for an anti-pattern | M |
| D5 | built-in/missions/mission_types/*.yaml | `schema_version: 1` int | all other kinds "1.0" str | string "1.0" | H |
| D6 | qa.yaml, cave-dev.yaml | `requires_substantive_spec` absent | present in 4/6 | required bool with explicit value (or documented default) | H |
| D7 | mission-runtime.yaml (27 steps), step.yaml (7), governance-profile (qa), action index retrospect (3) | profile ids where a role is expected | `agent-profile`, `agent_profile`, `selected_agent_profiles`, `agent_profiles` | single `role` field naming a role; drop profile pins (ADR-0032) | H |
| D8 | mission-runtime.yaml | `agent-profile` kebab key | only kebab key outside agent-profile schema | snake_case | H |
| D9 | built-in vs qa/cave-dev | governance-profile location and step/step-contract dir layout | 4 layouts | one layout per kind | M |
| D10 | mission.yaml / mission-runtime.yaml | duplicated name+version+key vs mission_type id/display_name; versions disagree in 4/6 | 3 files carry the type's name | one authority for name/version | H |
| D11 | mission-runtime (plan) | `mission.steps` legacy mirror, `order`, `title`, `runtime` | only in plan | remove mirror; consistent step fields | H |
| D12 | mission.yaml (qa) | `mission/initial/states/transitions` marked never read | 1 file | drop or schema it | M |
| D13 | mission.yaml | 3 shapes; `domain` free text; `agent_context/paths/mcp_tools/task_metadata` partial | see 2.5 | uniform required set; optional blocks typed | M |
| D14 | step.yaml x37 | `guidance: null`, `delegates_to: []`, `step_type` constant, `sequence_index` null/absent, `in_action_sequence` | dead/duplicate | drop dead; sequence_index required-or-absent consistently | H (dead), M (rest) |
| D15 | expected-artifacts.yaml | `schema_version` + `manifest_version`; `tasks_finalize` vs `tasks-finalize`; `done` key w/o step | | one version; step keys = step ids | H |
| D16 | action index / governance-profile / step-contract | directive reference forms: filename-slug vs `DIRECTIVE_NNN` id | 60 slug / 95 id | id form everywhere | H |
| D17 | action index, gov-profile, mission.yaml, mission-runtime | no `schema_version` | absent | add (string) | H |
| D18 | procedures | inline `references` duplicate procedure.graph edges (103/103) | duplicated | one authority (edge) | M (schema allows it) |
| D19 | tracker-organisation-workflow.procedure.yaml | `applies_to_languages: []` | empty | omit | L |
| D20 | procedures | `actor: system` allowed, never used; `on_success` 3 files | | none (note only) | L |
| D21 | built_in_step_contracts/__init__.py | empty python file in a data pack | 0 bytes | remove | M |
| D22 | step ids across runtime/steps/actions/contracts | disagree (see 3.8) | | consistent id set per type | M |
| D23 | 3 anti-pattern names duplicated across files | CRUD/affordance echo, Command-named steps, Defect framing | | confirm intent | L |

## 6. Where my lens does not apply / limits
- I do not propose DSO field names (datamodel delegate). Confidence flags above are about the divergence being real, not about the fix.
- I did not execute the Java DSO round trip; "validates against schema" is jsonschema 4.26 Draft 2020-12 over the YAML only. Mission-tier files have no schema, so "divergence" for them means inconsistency among siblings, not violation of a contract.
- event-driven pack has no in-scope files; internal pack has procedures only.

## Verdict
The packs are schema-clean where schemas exist; the sanitization burden concentrates on (1) the anti_pattern concept (two inline shapes, a third `failure_modes`, a tag-only graph kind with no linkage to the inline entries), and (2) the whole mission-type tier, which has no schema, three to four file layouts, key-case and version-type drift, duplicated identity/version data and profile pins contradicting ADR-0032. Expect roughly 15-20 open-packs issues (D5-D17 clustered by file kind) rather than tolerance code; procedures need almost none.
