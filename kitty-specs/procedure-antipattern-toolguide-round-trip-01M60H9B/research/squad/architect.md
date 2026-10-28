# #194 pre-spec squad: architect lens (domain model and sanitized shape)

Delegate: **architect-alphonso**, loaded via `spec-kitty agent profile show architect-alphonso`.
Charter context: `spec-kitty charter context --action plan --json` (bootstrap).

**Applied from the profile:** primary focus (architecture, decisions, component boundaries);
avoidance boundary (I write no implementation code, and I stayed read-only); canonical verbs
(model, evaluate, decide, specify); directives 001 (architectural integrity), 003 (decision
documentation: new decisions are routed to an ADR), 031 (context-aware design), 032 (conceptual
alignment: terms are checked against `docs/context/`), 044 (canonical sources and unification:
one authority per fact).
**Applied from the charter:** PROJECT_002 / ADR-0036 (equality of meaning, unknown fields
refused, inconsistencies fixed in the pack). "Never break the OpenAPI contract." DDD in core's
vocabulary.
**Binding operator decision (coordinator, mid-task):** the sanitized shape wins over #194's
"round-trip as-is" wording. The shapes below are designed on purpose, not fitted to the content
at `01edb4e`. Every place where the content diverges is costed as an open-packs issue plus a
known-failure entry.

Evidence base: charter `f790273`; open-packs at `01edb4e`; core at `/home/user/spec-kitty` (src).

---

## 1. Domain model

### 1.1 Concepts, identity, files

| Concept | Identity (URN) | Aggregate / slice | Today's file(s) at 01edb4e | CharterArtifact? | Own stored kind (KindBinding) |
|---|---|---|---|---|---|
| **Procedure** | `procedure:<id>` | `packs` | `procedures/<id>.procedure.yaml` (23) | yes (`ArtifactKind.PROCEDURE` exists) | **yes**, `procedure` (schema `procedure`, token `procedure`) |
| **MissionType** | `mission_type:<id>` | new `missions` slice (ADR-0032 D6, ADR-0035 D4, plan D9) | `missions/mission_types/<id>.yaml` (4); partly duplicated in `missions/<id>/mission.yaml`, `mission-runtime.yaml`, `governance-profile.yaml`, `expected-artifacts.yaml` | yes (ADR-0035 D4 says "mission types included"), but this **needs `MISSION_TYPE` in `ArtifactKind`** (see F-1) | yes, `mission_type` |
| **MissionStep** (the "mission step" of the glossary: a runtime DAG node with its prompt) | local `<type>/<step>`; no NodeKind today | entity of the MissionType aggregate | `missions/mission-steps/<type>/<step>/step.yaml` (30), plus companions `prompt.md` and `guidelines.md` | **no** (not separately activatable; core keeps no ArtifactKind for it) | yes, as a **part binding**, which the registry has no seam for yet (F-2) |
| **Action** | `action:<type>/<action>` (NodeKind.ACTION) | entity of the MissionType aggregate: a MissionStep that is in the action sequence | `missions/<type>/actions/<action>/index.yaml` (24) duplicates the `action.graph.yaml` `scope` edges | no | **no**: the action is a CRG node; `index.yaml` is a duplicate (F-6) |
| **MissionStepContract** ("step contract": the detailed version of an action) | `mission_step_contract:<id>` | `packs` (already green, #191) | `missions/built_in_step_contracts/*.step-contract.yaml` | yes | yes (exists) |
| **Mission template** | path under `missions/<type>/templates/` | MissionType content (ADR-0040 keeps it out of top-level `template`) | `missions/<type>/templates/**` (md, csv, `.template`) | no | **no**: a `bytes` payload kind (`mission-template`) through a `payloads` rule (ADR-0040 mechanism) |
| **AntiPattern** | `anti_pattern:<id>` (NodeKind.ANTI_PATTERN) | CRG node, re-kinded inside a graph document | `anti_pattern.graph.yaml` (13 nodes: label + tags, 0 edges); targets of `rejects` edges in `paradigm.graph.yaml` and `tactic.graph.yaml`; org fragment form `{id, kind: anti_patterns, title, body_path}` | **no file-backed artifact.** `ArtifactKind.ANTI_PATTERN` exists (activatable), but no PackFileKind and no KindBinding (ADR-0037 D4 bullet 2) | **no** |
| **Inline anti-pattern entry** | none (value object of its owner) | inside Styleguide / Procedure | `anti_patterns:` lists in 10 styleguides and 16 procedures | no | no (rides inside its owner's DSO) |

### 1.2 Relations (CRG edges, canonical form per ADR-0036 D4)

- `mission_type:T --requires--> action:T/A`: the action set (59 edges in `mission_type.graph.yaml`). Order cannot come from here: edges are a **set** (ADR-0036 per-kind table). The sequence needs one file authority (§2.4).
- `mission_type:T --scope--> <artifact>`: type-wide governance, the "recipe ingredients" (logical-model §Mission types, line 251).
- `action:T/A --scope--> directive|tactic|paradigm|...`: action-scoped context. `action:T/A --instantiates--> template:...`.
- `procedure:P --requires|suggests--> tactic|directive|...` (110 edges in `procedure.graph.yaml`).
- `paradigm|tactic --rejects--> anti_pattern:X` (15 edges). `rejects` goes from a good artifact to an anti_pattern node (logical-model line 274).
- MissionStep → MissionStepContract: **not an edge today.** It is joined by key `(mission, action) == (type, step.id)` when `in_action_sequence`. Make it explicit through derived identity (§2.4), not a new field that duplicates the key.
- MissionStep → role: the step **names a role, never a profile** (ADR-0032 D1, settled). Procedure step → role: the conceptual model (line 196) already says a procedure may name the `operator` role. Its representation is new (§3).

### 1.3 How `mission-steps/` relates to the green `mission_step_contract` kind

They are **two concepts, joined 0..1**. A MissionStep is the runtime node (prompt, DAG `depends_on`, template output, role). A MissionStepContract is the sub-step breakdown of one *action*, with delegation candidates. Every contract corresponds to exactly one action step. Not every step has a contract (`analyze` has `in_action_sequence: false`; `charter`, `research` and others have none).
Core conflates them in the DRG (`org_pack_loader.py:73-78`: plural `mission_steps` is an alias of `mission_step_contracts`). **Do not copy that conflation.** It is a divergence-register entry, not a model choice.

---

## 2. Sanitized-shape proposal

### 2.1 Conventions found in the merged schemas (evidence) and the rule chosen

| # | Concern | What the 16 merged schemas do | Rule (proposed) |
|---|---|---|---|
| R1 | Key case | snake_case everywhere **except** `agent-profile.schema.json` (kebab: `profile-id` :256, `schema-version` :269, `directive-references` :385, mixed with snake `applies_to_languages` :378) | **snake_case**, always |
| R2 | Format version | `schema_version` string `^1\.0$` (directive, tactic, paradigm, styleguide, toolguide, procedure, graph-shard); `const "1.0"` (skill); bare string (mission-step-contract); optional (agent-profile); semver (charter); **integer** (org-charter); absent (asset, glossary-pack). Content: `mission_types/*.yaml:1` `schema_version: 1` (int) | `schema_version`: **required**, string, `const: "1.0"` for every new DSO |
| R3 | Identity | `id` (most), `profile-id` (agent), `pack_id` (descriptor); patterns: kebab `^[a-z][a-z0-9-]*$` (most), UPPER `^[A-Z][A-Z0-9_-]*$` (directive), none (asset, glossary-pack, step-contract) | `id`: required, kebab pattern `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` (skill's stricter form), **equal to the file stem** (or the directory name for a step) |
| R4 | Display label | `name` (tactic, paradigm, procedure, agent, pack); `title` (directive, styleguide, toolguide, skill, asset, org-fragment node); `label` (graph node); `display_name` (core MissionType/MissionStep) | **`title`**, required. Rationale: the newest schemas designed in this repo use it (skill, asset, fragment node), and `name` is already taken for the identity of inline value objects (pattern, anti-pattern and parameter `name`). Maps to `CharterArtifact.label()` |
| R5 | Summary prose | `intent` (directive), `summary` (paradigm, toolguide), `purpose` (tactic, procedure, agent), `description` (skill, glossary, agent: **both** `purpose` and `description` on one profile) | **`description`**, the one "what is this" field. Maps to `CharterArtifact.description()`. A field with genuinely distinct semantics keeps its own name (for example `entry_condition`) |
| R6 | Pointer to a pack file | `body_path` (skill, fragment node), `guide_path` (toolguide), `path` (asset); core step uses `prompt_template`, `template_file`; `guidelines.md` is named by **no** field | `<role>_path` (or `path` on a pointer object), relative to the artifact's file, and **every** companion named by a field (ADR-0039 D1: a binding names its companions) |
| R7 | Cross-artifact reference | five shapes: `{type,id,when?,reason?}` directive; `{name,type,id,when}` all required, tactic; `{type,id}` paradigm; `{type,id,reason?}` procedure; `List<String>` styleguide/toolguide; per-kind lists `directive-references`/`tactic-references` agent; bare `directive_refs` paradigm, `procedures` directive | **one `$def Reference {type: ArtifactKind, id, when?, reason?}`** wherever a reference is kept. Whether references are kept at all is #189 (§3, D-3) |
| R8 | Inline anti-pattern | styleguide `{name,description,bad_example,good_example}` all required; procedure `{name,description}` | **one `$def AntiPatternEntry {name*, description*, bad_example?, good_example?}`** |
| R9 | Step | tactic `{title*,description?,examples,references}`; procedure `{title*,description?,actor,on_success?,on_failure?}`; step contract `{id*,description*,command?,...}` | Step items: `title*`, `description?`; `id` **only when something refers to the step** (contract steps, mission steps) |
| R10 | Who performs | procedure `actor` enum `human|agent|system`; mission step `agent_profile` (profile id) and `step_type` | **`role`**: an open role token (ADR-0032 D1/D2/D5); `operator` is how a human is asked for |
| R11 | Relationships | inline lists and the CRG both carry them (see F-6, F-7) | **one authority per fact**: a fact the CRG carries is not repeated in a DSO (pending #189 for the *kept* inline `references`) |
| R12 | Closed | all closed except `pack-descriptor` (`additionalProperties: true` :4) | `additionalProperties: false` at every level; only declared open maps |
| R13 | Optional lists | absent = null = `[]` (ADR-0036 D2) | lists default `[]`; never `null`-required |

### 2.2 `procedure` (rewrite `contracts/packs/schemas/procedure.schema.json` in place; it is canonical here, R20)

| Field | Type | Req | Change vs today's schema / content |
|---|---|---|---|
| `schema_version` | `const "1.0"` | yes | same |
| `id` | kebab, = stem | yes | same |
| `title` | string, minLength 1 | yes | **rename** from `name` (23 files) |
| `description` | string | yes | **rename** from `purpose` (23 files) |
| `entry_condition` | string | yes | same |
| `exit_condition` | string | yes | same |
| `steps` | `ProcedureStep[]` minItems 1 | yes | see below |
| `anti_patterns` | `AntiPatternEntry[]` | no | shape unified (content is a subset, so it passes) |
| `applies_to_languages` | string[] | no | same (no content uses it) |
| `notes` | string | no | same |
| `references` | `Reference[]` | no | **D-3**: keep it with the canonical shape (content `{type,id,reason}` passes), or drop it if #189 makes the CRG authoritative (21 files fail) |

`ProcedureStep {title*, description?, role?, on_success?, on_failure?}`. **`actor` (147 uses: 130
`agent`, 17 `human`) is replaced by `role`.** `human` maps to `operator`, and `agent`/absent maps
to "any role". `system` is not used. All 23 files are touched by the rename, so they fail until
open-packs migrates.

```json
{"$schema":"https://json-schema.org/draft/2020-12/schema","title":"Procedure","type":"object","additionalProperties":false,
 "required":["schema_version","id","title","description","entry_condition","exit_condition","steps"],
 "properties":{
  "schema_version":{"const":"1.0"},
  "id":{"$ref":"#/$defs/Slug"},
  "title":{"type":"string","minLength":1},
  "description":{"type":"string","minLength":1},
  "entry_condition":{"type":"string","minLength":1},
  "exit_condition":{"type":"string","minLength":1},
  "steps":{"type":"array","minItems":1,"items":{"$ref":"#/$defs/ProcedureStep"}},
  "anti_patterns":{"type":"array","items":{"$ref":"#/$defs/AntiPatternEntry"}},
  "applies_to_languages":{"type":"array","items":{"type":"string"}},
  "notes":{"type":"string"},
  "references":{"type":"array","items":{"$ref":"#/$defs/Reference"}}},
 "$defs":{
  "Slug":{"type":"string","pattern":"^[a-z][a-z0-9]*(-[a-z0-9]+)*$"},
  "RoleToken":{"type":"string","pattern":"^[a-z][a-z0-9]*(-[a-z0-9]+)*$"},
  "ProcedureStep":{"type":"object","additionalProperties":false,"required":["title"],
   "properties":{"title":{"type":"string","minLength":1},"description":{"type":"string"},
    "role":{"$ref":"#/$defs/RoleToken"},"on_success":{"type":"string"},"on_failure":{"type":"string"}}},
  "AntiPatternEntry":{"type":"object","additionalProperties":false,"required":["name","description"],
   "properties":{"name":{"type":"string","minLength":1},"description":{"type":"string","minLength":1},
    "bad_example":{"type":"string"},"good_example":{"type":"string"}}},
  "Reference":{"type":"object","additionalProperties":false,"required":["type","id"],
   "properties":{"type":{"$ref":"#/$defs/ArtifactKind"},"id":{"type":"string","minLength":1},
    "when":{"type":"string"},"reason":{"type":"string"}}},
  "ArtifactKind":{"enum":["directive","tactic","styleguide","toolguide","paradigm","procedure","agent_profile","mission_step_contract","glossary_pack","skill","anti_pattern","asset","template","mission_type"]}}}
```

### 2.3 `mission_type` (new `contracts/packs/schemas/mission-type.schema.json`)

One DSO per mission type, consolidating the fields that are today spread over five files. File
location is D-4. The logical-model candidate is `missions/<type>/mission.yaml`; until the ADR
rules, it stays at `missions/mission_types/<id>.yaml`.

| Field | Type | Req | Source today / disposition |
|---|---|---|---|
| `schema_version` | `const "1.0"` | yes | content has int `1`: **known failure ×4** |
| `id` | Slug = stem | yes | `id` |
| `title` | string | yes | **rename** of `display_name` (×4) |
| `description` | string | no | `mission.yaml` `description` (retired there) |
| `steps` | `Slug[]`, unique, minItems 1 | yes | **the single ordered sequence authority** (D-5). It replaces core's `action_sequence`, step `sequence_index` + `in_action_sequence`, `mission-runtime.yaml` `steps`, and `mission.yaml` `workflow.phases` |
| `path_conventions` | object, closed keys `workspace|tests|deliverables|documentation` (core `VALID_PATH_KEYS`) | no | `mission.yaml` `paths` (retired there) |
| `requires_substantive_spec` | boolean | no | same |
| `required_artifacts` | `ExpectedArtifact[]` | no | `expected-artifacts.yaml` `required_always` (D-6) |

Not in the schema, on purpose: `extends` (it is the `specializes_from` CRG edge; R11);
`template_set` (core retired it, governance-profile `:20`); the governance-profile `selected_*`
lists (they are `mission_type --scope-->` edges; R11); `mission.yaml` `version`, `domain`,
`validation`, `mcp_tools` and `artifacts` (no consumer in the charter model; operator to confirm in
D-4).

### 2.4 `mission_step` (new `mission-step.schema.json`; part of the MissionType aggregate)

| Field | Type | Req | Disposition |
|---|---|---|---|
| `schema_version` | `const "1.0"` | yes | **added** (content has none: ×30) |
| `id` | Slug = directory name | yes | same |
| `title` | string | yes | **rename** of `display_name` (×30) |
| `step_type` | enum `agent|integration` | yes | `human_in_loop` overlaps `role: operator` (ADR-0032 D5). Proposal: drop the value, a human step is `role: operator` (D-7) |
| `role` | RoleToken | no | **replaces `agent_profile`** (30/30 carry `agent_profile: null`, `step.yaml:5`). ADR-0032 D1 settles this. An unknown key is refused even when null, so ×30 fail |
| `prompt_path` | path | yes | **rename** of `prompt_template` (R6) |
| `guidelines_path` | path | no | **new**: names `guidelines.md`, an unnamed companion today (R6, ADR-0039 D1) |
| `action` | boolean | no, default false | **replaces `in_action_sequence`**. Ordering moves to `mission_type.steps` |
| `depends_on` | Slug[] | no | same |
| `delegates_to` | Slug[] | no | same (always `[]` in content); a reference to *what*? Undocumented, D-7 |
| `guidance` | string | no | same (always null) |
| `output` | `{artifact_key: Slug, path}` | no | **rename** of `template{artifact_key, template_file}` (R6); `path` is relative to `missions/<type>/templates/` |
| `expected_artifacts` | `ExpectedArtifact[]` | no | from `expected-artifacts.yaml` `required_by_step.<step>` (D-6) |
| `recommended_model_tier` | string | no | core field; content none. Include only if the operator wants it |

`ExpectedArtifact {key*: string, class*: enum input|output|..., path_pattern*: string, blocking?: boolean}`.
It is core's `artifact_key`/`artifact_class` with the redundant `artifact_` prefix dropped (or kept;
the rule is "no stutter inside a value object").

Retired with no DSO: `missions/<type>/mission-runtime.yaml` (core: "transitional", "legacy DAG";
everything in it duplicates the type + steps); `missions/<type>/actions/*/index.yaml` (duplicates
`action.graph.yaml` scope edges with a *different id spelling*: `index.yaml:2`
`010-specification-fidelity-requirement` vs edge target `directive:DIRECTIVE_010`); `governance-profile.yaml`
(`id` and `mission_type` duplicate each other, `:18-19`); `expected-artifacts.yaml` (folded in);
`missions/<type>/mission.yaml` (folded in, or *becomes* the type DSO under D-4).
Mission templates become a `payloads` rule `*/missions/*/templates/**/*` → kind `mission-template`,
`bytes`. Add hidden-file and `README.md` exclusions for `missions/**` (for example
`missions/plan/templates/.gitkeep`, `missions/README.md`).

### 2.5 AntiPattern

- **The node is the identity.** In a root shard it is a `DRGNode {urn: anti_pattern:<slug>, kind: anti_pattern, label, tags, provenance?}`; in an org fragment it is `{id, kind: anti_patterns, title, body_path?}`. It needs **no new schema**: `graph-shard` and `org-fragment` already admit it. Round-trip is blocked only by node `tags` being dropped (known red **#236**, `expected-red.yaml` `anti_pattern.graph.yaml`). **#194's anti_pattern scope is mostly #236.** Say so in the spec, or the slice duplicates work.
- **Inline entries are value objects**, not nodes: `AntiPatternEntry` inside styleguide and procedure (R8). They carry no URN and never re-kind. The 13 graph nodes and the 26 inline lists are **not linked today** (no inline `name` matches a node label, for example `anemic-domain-model` appears in no styleguide). Linking them would be a new decision (D-8). The proposal does not invent a link.
- **Not stored by a KindBinding, and no PackFileKind** (ADR-0037 D4 bullet 2). If the domain is to expose anti-patterns as `CharterArtifact`s (the kind is activatable), they are *derived from CRG documents*. That collides with ADR-0037 D5, under which the CRG documents stay outside the aggregate (D-8).

---

## 3. Decisions: settled vs new (new → **ADR-0042**; 0041 is earmarked for #227; `docs/adr/` ends at 0040)

**Already settled (cite, don't re-decide):**
- Steps name roles, not profiles; mission types name no profiles: ADR-0032 D1/D6.
- Mission types are their own concept and domain model, activated through the same lever: plan D9, ADR-0032 D6, conceptual model §Mission types.
- They implement `CharterArtifact` from their own slice: ADR-0035 D4. The pack store binds a kind of another slice: ADR-0037 amendment (#190) reverses D6.
- A binding names its schema, file token and id key: ADR-0037 amendment (#191).
- Unknown fields refused, equality of meaning, edges canonical, inconsistencies fixed upstream with a known-failure entry: ADR-0036 D1, D3, D4, D5.
- Prompt and guidelines as text companions named by the artifact; a companion is never `.yaml`: ADR-0039 D1, D3.
- Content form by rule; a new `bytes` payload kind is a rule plus a `BYTES_KINDS` entry: ADR-0040.
- Anti-patterns are not files and have no binding: ADR-0037 D4.

**Genuinely new (ADR-0042, "Mission types, procedures and anti-patterns in the pack store; the sanitized DSO rule"):**
- **D-1 The sanitized-shape rule itself** (R1–R13) as a standing rule for every DSO and schema, with a register of the merged schemas that break it (§4). Today nothing records it. ADR-0036 only says "unknown fields refused".
- **D-2 `MISSION_TYPE` joins `ArtifactKind`** (kernel enum, `file-kinds.yaml` `artifact_kinds`, `VocabularyEqualityTest`). It needs the flags `activatable`/`orgRequirable`/`selectionOverlayable`, and the `ArtifactKind` `$def` must be added to schemas. This diverges from core (`MissionTypeNotAnArtifactKind`): a divergence-register entry. ADR-0037 D6's sentence "Mission types are not an `ArtifactKind` at all" (`0037:153`) was reversed only for the registry, so this is not yet decided.
- **D-3 Inline `references` on a new DSO**: keep them (canonical R7 shape) or drop them for CRG authority. This **pre-empts #189** (edge authority, open per ADR-0037 D5/D7). The evidence cuts both ways. `ArtifactEdges.java:59` mints every typed ref as `SUGGESTS`, while `procedure.graph.yaml` has `adversarial-squad-deployment → code-review-incremental` as `requires` with the identical reason text, so binding procedure refs **mints a conflicting duplicate edge**. Yet core's directive schema calls frontmatter refs the "single canonical authority" (directive.schema.json :27). Recommendation: keep `references` in the DSO with the canonical shape, but **do not mint relationships from procedure refs** until #189 rules.
- **D-4 Mission-type file layout**: one DSO file (candidate `missions/<type>/mission.yaml`, logical-model) vs today's `mission_types/<id>.yaml`.
- **D-5 Sequence authority**: an ordered `mission_type.steps` list vs per-step `sequence_index` (core's current projection).
- **D-6 Expected artifacts**: folded into type and step vs their own DSO.
- **D-7 Step semantics**: `step_type: human_in_loop` vs `role: operator`; meaning of `delegates_to` on a mission step.
- **D-8 Anti-pattern as a node-derived artifact**: whether the aggregate exposes them, given ADR-0037 D5; whether inline entries link to nodes.
- **D-9 A "part binding"**: a stored kind that is *not* an `ArtifactKind` (MissionStep, many files per aggregate). `KindBinding.kind()` returns `ArtifactKind`, and ADR-0039 D3 forbids `.yaml` companions, so step files can be neither their own artifact kind (by the model) nor companions (by ADR-0039). Either amend ADR-0037 (a binding keyed by a part kind, owned by the MissionType binding) or make MissionStep an ArtifactKind (contradicts the model). This is the structural crux of #194.
- **D-10 Procedure step `role`** replacing `actor`. It extends ADR-0032 D1 to procedure steps; the conceptual model already implies it (line 196).

---

## 4. Merged DSOs and schemas that VIOLATE the sanitized rule (follow-ups for epic #186; not changed in #194)

- [HIGH] contracts/packs/schemas/agent-profile.schema.json:256,269,385 vs :378 - kebab keys (`profile-id`, `schema-version`, `*-references`) mixed with snake `applies_to_languages`; `AgentProfileDso.java:47` needs `idKey()` special-casing - migrate to snake_case `id`/`schema_version` (R1, R3).
- [HIGH] AgentProfileDso.java:58 + :49 - both `purpose` and `description` on one artifact - one summary field (R5).
- [HIGH] agent-profile, directive, tactic, paradigm, styleguide, toolguide, procedure schemas - five reference shapes (TacticReference requires a stray `name` and `when`; styleguide/toolguide plain strings; agent has per-kind lists; paradigm `directive_refs` + `references`; directive `procedures` bare list) - one `Reference` `$def` (R7), after #189.
- [MEDIUM] Display label: `name` (tactic, paradigm, procedure, agent) vs `title` (directive, styleguide, toolguide, skill, asset) vs graph `label` vs fragment `title` (graph-shard :90 vs org-fragment `_OrgDRGNode`) - `title` (R4).
- [MEDIUM] Summary: `intent` (directive), `summary` (paradigm, toolguide), `purpose` (tactic) - `description` (R5).
- [MEDIUM] `schema_version`: int in org-charter; semver in charter; optional in agent; absent in asset and glossary-pack; unpatterned in mission-step-contract - `const "1.0"` (R2).
- [MEDIUM] Id patterns: directive UPPER_SNAKE, asset/glossary/step-contract unpatterned - one slug rule (R3); directive ids are a known core coupling, so treat them as a register entry.
- [MEDIUM] styleguide.schema.json `AntiPattern` (all four required) vs procedure `ProcedureAntiPattern` - one `AntiPatternEntry` (R8).
- [MEDIUM] mission-step-contract ids are ad hoc: bare `specify` for software-dev, prefixed `plan-specify`/`documentation-design` for others, while `mission` + `action` already hold the key - derive the id as `<mission>/<action>` (matches the action URN) or drop the redundant fields.
- [MEDIUM] charter.schema.json:750 `mission_type_activations` vs `activated_<plural>` everywhere else; org-charter has `required_*` for 12 kinds but no mission types - align when D-2 lands.
- [MEDIUM] org-fragment `_OrgDRGEdge` lacks `when`, which `DRGEdge` has; node `urn`/`label` vs `id`/`title` - one node and edge vocabulary.
- [LOW] pack-descriptor.schema.json:4 `additionalProperties: true` - ADR-0036 D3 already orders it closed; still open.
- [LOW] Pointers: `guide_path`/`body_path`/`path` are consistent; core step `prompt_template`/`template_file` are not (they enter with #194, so fix them there).
- [LOW] open-packs `missions/built_in_step_contracts/__init__.py` - a Python package marker in a content pack; no classification rule; upstream issue.

---

## 5. Risks

- [BLOCKER] core coupling (not HTTP) - `contracts/packs/schemas` is **consumed by core** (README; ADR-0015). Core's Pydantic models are `extra="forbid"` (`missions/models.py` MissionType `schema_version: int`, `display_name`; Procedure `name`/`purpose`/`actor`). A sanitized schema plus migrated open-packs content **breaks core's loader** unless core changes in lockstep - the spec must name a three-repo sequence (charter schema → core model → open-packs content and re-pin), or a dual-read window in **core** (never in the charter: ADR-0036 D1.3 bans tolerance here). This touches "Divergence from the oracle" (charter.md).
- [HIGH] known-failure volume - by the shapes above: procedures 23/23 (rename), mission_types 4/4, step.yaml 30/30, plus every retired file (index.yaml ×24, mission-runtime ×4, governance-profile ×4, expected-artifacts ×4, mission.yaml ×4). `known-failures.yaml` lists files of a *modelled* kind. Retired-file classes need a different entry type (an exclusion with a reason plus an open-packs issue, or a new "retired" class) - decide in the spec. Without that, the gate degenerates into a long allowlist.
- [HIGH] D-9 (part binding) is unresolved; implementation will otherwise improvise tolerance (for example a `.yaml` companion exception to ADR-0039 D3).
- [MEDIUM] HTTP contract - **no change needed** if #194 stays storage-only. `contracts/charter/schemas/NodeKind.yaml` already enumerates `anti_pattern`, `action` and `mission_type`; only its *description* ("every artifact kind plus action and mission_type") becomes inaccurate once D-2 lands. That is a doc fix with a CHANGELOG line, not an enum change. `CharterRefusalCode` `mission_type_not_found` stays `raised: false`. Adding procedure or mission-type endpoints is out of scope (ADR-0032 D7, C-003).
- [MEDIUM] D-3 - binding procedure `references` through `ArtifactEdges` mints `SUGGESTS` edges that contradict the CRG's `requires`; the gate compares files, so it will not catch it, but resolved context changes. Do not mint until #189 rules.
- [MEDIUM] anti_pattern - #194 cannot go green for `anti_pattern.graph.yaml` before #236 (node tags). Sequence it or depend on it explicitly.
- [LOW] digest order - new bindings must be **appended** to `KindBindings.ALL` (fingerprint stability, KindBindings Javadoc).

## Concessions (outside my lens)

- Whether core can take a lockstep schema change, and on what schedule, is a core-maintainer / planner call, not architecture.
- Byte-level hazards of mission templates (CRLF, `.template` files) belong to the implementer/QA lens. ADR-0040 already covers the mechanism.
- Whether `mission.yaml`'s `validation` and `mcp_tools` matter to anyone is a product question. I only flag that the model has no home for them.

## Verdict

**Proceed to spec, with conditions.** The domain model is clear and mostly settled by ADRs
0032/0035/0036/0037/0039/0040 and D9. The sanitized shapes for `procedure`, `mission_type` and
`mission_step` are well defined (§2), and anti_pattern needs no new schema (its round-trip is
#236). Before the plan, the spec must (1) commission **ADR-0042** covering D-1, D-2, D-9 (the
part binding, which is the structural crux), D-3/#189 and D-4–D-8; (2) state the **three-repo
lockstep** for core-consumed schemas; (3) choose how retired files are recorded in the gate.
The 14 merged-schema violations go to #186 as follow-ups, not into #194.
