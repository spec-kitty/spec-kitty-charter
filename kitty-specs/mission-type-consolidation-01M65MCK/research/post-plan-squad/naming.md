# #194 PR-B: naming and sanitized-shape review of the mission-type DSO (lexical-larry)

This is a read-only adversarial review. I loaded the profile with `spec-kitty agent profile show lexical-larry`.

Inputs checked: ADR-0044 decision 3, `spec.md`, `data-model.md`, `research/squad/divergence.md` §2/§4/§5, S1–S16 (ADR-0042 and ADR-0043), the glossary, `docs/context/domain.md`, `docs/_meta/terminology.yaml`, DIV-011, the procedure, toolguide, toolguide-guide, preset and mission-step-contract schemas, the kernel `ArtifactKind`, and the pinned data in `spec-kitty-open-packs` at `5df92a5`.

I did not relitigate the operator rulings (two kinds, one file, `steps`/`on_demand_steps`, folding, roles, references kept, `:missions`).

## Verified clean (no finding)

- **References.** All 187 reference ids in the 24 action indexes and 6 governance profiles resolve to an artifact id. The only stems are the 62 directive stems, and every one maps to an existing `DIRECTIVE_NNN`.
- **Roles.** Every profile→role mapping produces a token that matches `^[a-z][a-z0-9-]*$`. Each primary role was checked against `roles[0]`: researcher, architect, implementer, reviewer, analyst, planner, qa-strategist, test-designer, test-executor, qa-reporter, facilitator. The grammar matches the procedure schema.
- **Companions.** No prompt, command-template or guidelines file has an H1 or H2 outside a fence beyond its title. Prompt frontmatter carries only `description` (28 files), and 2 prompts are empty, as the ADR says.
- **Terminology gate.** `check_docs.py terms` passes. `on_demand_steps` does not trip the `\bmission_steps\b` avoid rule.

## Findings (ranked)

### 1. HIGH: the expected-artifact `id` has no grammar, and the pinned keys fit no existing id grammar

- **Evidence.**
  - ADR-0044 and the data model give expected-artifact `id` no pattern. The only `id` patterns in the schemas are kebab: `^[a-z0-9]+(-[a-z0-9]+)*$` in procedure and the step `id`.
  - The pinned keys are dotted and mix kebab with snake: `output.tasks.per_wp`, `runtime.charter-lint.decay`, `output.qa-coverage.qa.optional`.
  - An implementer who reuses the step or type id pattern refuses all 44 entries.
- **Fix.** Declare a grammar explicitly in the ADR and the schema. Either:
  - **(a)** keep the keys verbatim, with `^[a-z][a-z0-9-]*(\.[a-z0-9_-]+)+$`, and note in the ADR why `_` is admitted (`per_wp`); or
  - **(b)** have the harness rewrite them to one grammar.

  Pick one and record it.

### 2. HIGH: the expected-artifact `id` is not an identity, and the pinned data breaks it in both directions (S1, S6)

- **Evidence.** The data model makes ids unique "within the type" and "within the step" separately, so one file can carry two entries with the same `id` and different meanings:
  - documentation `evidence.audit-report` is blocking at step `validate` and non-blocking in `optional_always`;
  - software-dev `output.tasks.per_wp` appears at both `tasks-packages` and `tasks-finalize`.

  The opposite also happens. qa gives one file two ids: `qa-coverage.json` is both `output.qa-coverage.qa` and `output.qa-coverage.qa.optional`, and `qa-traceability.yaml` is both `output.qa-traceability.review.qa` and `output.qa-traceability.qa`.
- **Fix.**
  - State the uniqueness scope as the whole mission-type file: the type list plus every step list.
  - Make the harness collapse each collision deliberately: one id per artifact, with step entries carrying the blocking fact.
  - Or say plainly that `id` is a label, not an identity, and rename it. S1 reserves `id` for identity.
  - Add a scenario to User Story 3.

### 3. HIGH: `mission.yaml` `artifacts` and other pinned fields are neither mapped nor dropped (breaks SC-004)

- **Evidence.**
  - `artifacts.required`/`artifacts.optional` exist in 6 of 6 `mission.yaml` files. They hold bare names and directories (`contracts/`, `checklists/`, `divio-templates/`, `generator-configs/`, `sources/`, `analysis/`), plus `tasks.md` and `findings.md` as required.
  - Most of these have no `expected-artifacts.yaml` counterpart and no class or key, so the `{id, artifact_class, path_pattern}` entry cannot hold them without invented values.
  - The divergence research §4 says "folded"; ADR-0044's From column and drop list say nothing.
  - Also missing from the drop list:
    - qa `mission.yaml` `mission`/`initial`/`states`/`transitions` (the retired v1 FSM block);
    - plan `mission-runtime.yaml` `mission.description`;
    - `validation.custom_validators`. It is only implied by `validation`, so name it.
- **Fix.** Add `mission.yaml artifacts` to the drop list with its reason: it duplicates the expected-artifact manifest, and core's acceptance check hardcodes spec/plan/tasks. Also add the FSM block and the runtime `mission.description`. Otherwise define the folding rule (the invented id and class), but a drop is the honest choice.

### 4. HIGH, terminology: the glossary's "Step template" is the prompt, but the schema's step `template` is the output-document template

- **Evidence.**
  - The glossary says: "Step template: The prompt fed to the agent when a mission step runs."
  - `docs/context/conceptual-model.md:217` says the same.
  - ADR-0044's step `template` is `{artifact_key, template_path}`, pointing at `templates/spec-template.md`, the skeleton of the produced artifact. The prompt is `prompt_path`.
  - A reader of the glossary will look for the prompt under `template`.
- **Fix.** Ask curator-carla to do one of these:
  - **(a)** Retarget the glossary: "Step prompt" for the prompt companion, and "Step template" kept but redefined as the document template a step fills in.
  - **(b)** Rename the field to `artifact_template`. This is preferred if finding 5's fix (a) does not land.

  Update `conceptual-model.md` in the same change.

### 5. MEDIUM-HIGH: `template.artifact_key` keeps the retired identity name, with a second grammar in the same object (S1, S3)

- **Evidence.**
  - Decision 3 renames the expected-artifact `artifact_key` to `id` (S1), but keeps `artifact_key` inside `template`.
  - The values are a different vocabulary: `spec`, `plan`, `tasks`, `test-report`, `defect-log`, against `input.spec.main` and `workflow.plan.qa`.
  - S3 says one identity is keyed the same way everywhere in an object.
  - `template.template_path` also stutters.
  - Data-model says the path is "relative, inside the pack, `templates/...`". The ADR says it is relative to the mission-type file.
- **Fix.**
  - **(a)** Preferred: fold the template onto the expected artifact it produces, as `expected_artifacts[].template_path`. Checked against the pins, 12 of the 13 templated steps have exactly one matching step entry.
    - specify↔`*.spec.*`, plan↔`*.plan.*`, qa tasks↔`workflow.tasks.qa`, qa review↔`output.test-report.review.qa`.
    - The exception is qa `implement` (`defect-log`), whose step list is deliberately empty. It needs a non-blocking step entry, or the template is dropped with an open-packs issue.
  - **(b)** Otherwise rename `artifact_key` to a non-identity name that core's template-slot meaning supports, and record it as a divergence.

  Either way, fix one relative-path wording.

### 6. MEDIUM, terminology: the glossary's "Guideline" is a top-precedence charter rule; `guidelines_path` and `mission-step-guidelines` are a step's advisory notes

- **Evidence.** The glossary says: "Guideline: A high-level charter rule expressing non-negotiable or strongly preferred governance behavior. Guidelines sit at the highest precedence…". ADR-0044 brings this word into the model, the schema name and the `StepDocument` field for the pinned `guidelines.md` (17 files).
- **Fix.** Either:
  - add a glossary entry, "Step guidelines (companion)", that disambiguates explicitly and links to Guideline; or
  - rename the field and companion to `notes_path` and `<id>.notes.md`.

  The file name is migrated anyway, so renaming costs nothing now and is expensive later.

### 7. MEDIUM, terminology: "Step contract" and "Mission step" now both own the role and the outputs

- **Evidence.**
  - The glossary says: "Step contract: The detailed version of a mission step: what the step does, its inputs and outputs, and the role it asks for."
  - ADR-0044 puts `role` and `expected_artifacts` (the outputs) on the mission step, and leaves `mission_step_contract` "as it is". That schema has no role but has `inputs`, and identifies the step by `action` plus `mission`.
  - So there are two homes for the outputs, a definition claiming a third home for the role, and a third vocabulary (`action`/`mission`) for the step key.
- **Fix.** Update the Step contract definition to "the structural sub-steps of one mission step and what each delegates to". Add a note in ADR-0044 that the role and expected artifacts live on the mission step only. List the contract's `action`/`mission` keys as an epic #186 follow-up, since they should become `mission_type`/step `id`.

### 8. MEDIUM: the reference `type` enum and the domain `ReferenceType` fork the one reference shape (S14, S3)

- **Evidence.**
  - S14 says `type` is an artifact-kind token. The procedure schema uses a shared `$defs/ArtifactKind` with 13 tokens.
  - `ProcedureReference` uses the kernel's `ArtifactKind` (`ProcedureReference.java:18`).
  - ADR-0044 closes the enum to 6 tokens, and the data model adds a new `ReferenceType` enum (`DIRECTIVE`, …) for the same concept.
  - The result is a second enum and a reference shape that differs by schema.
- **Fix.** Use `ArtifactKind` in `MissionReference`, or better, one shared kernel reference value. Express the restriction as an invariant with a reason: no `agent_profile`, because roles replace profiles under ADR-0032. In the schema, reuse the same `$defs/ArtifactKind` and add a `not`/enum restriction, or justify the subset in the ADR. Note that `template` (the graph `instantiates`) is then intentionally not expressible.

### 9. MEDIUM: the type-level `references` are the glossary's "Recipe ingredients", but the ADR declares them inert

- **Evidence.**
  - The glossary says: "Recipe ingredients: the guidance a mission type names by id … while a mission of that type runs, these artifacts are added to the plate; activating the mission type is the consent". The canonical term replaces Mission-Type Profile.
  - ADR-0044 maps `governance-profile.yaml` `selected_*` to `references` and says they "mint no relationship".
  - So the same field name and shape mean "inert citation" on a procedure and "activation set" on a mission type.
- **Fix.** Say in ADR-0044 that the type-level `references` are the recipe ingredients, and that until #189 their activation meaning is carried by the graph `scope` edges (which equal `selected_*` exactly, divergence §2.10). Say whether step-level references are action-scoped context. Then update the glossary entry's "today in `governance-profile.yaml`" to the new field.

### 10. MEDIUM: qa's `selected_agent_profiles` is dropped, but "activation is the preset's" has no destination

- **Evidence.**
  - The qa pack has no `presets/` directory; only built-in and event-driven do.
  - `qa-defect-triager-dmitri` (role `defect-triager`) is named by no step, so after migration nothing in qa refers to that profile.
  - The divergence research said "moves to preset `activated_agent_profiles` (qa)". The ADR drop list says only "activation is the preset's".
- **Fix.** Either state the drop as an accepted loss with an open-packs issue that tells the pack to add a preset, or make the harness emit `qa/presets/qa.yaml` with `activated_agent_profiles`. Pick one in the ADR; FR-010's issue list should name it.

### 11. MEDIUM: `requires_substantive_spec: true`-only loses core's three-state meaning

- **Evidence.**
  - Divergence §3 says that in core, None means the built-in default map.
  - The pinned values are true (software-dev, research), explicit `false` (documentation, plan), and absent (cave-dev, qa).
  - The ADR maps both `false` and absent to "absent = not required". If core's default map says true for documentation or plan, meaning changes silently when core incorporates the shape.
- **Fix.** Check core's default map. If it is false for documentation and plan, record that in ADR-0044 decision 3. If not, add a divergence entry. Either way, make the schema description say "absent = not required, never 'use a default'".

### 12. MEDIUM: `artifact_class` repeats, and sometimes contradicts, the id prefix (S5, S6); the enum also silently narrows core's

- **Evidence.**
  - Every pinned key starts with its class (`input.spec.main` / `input`). S5 forbids a field that repeats another.
  - Three qa entries contradict their prefix: `output.qa-coverage.qa`, `output.qa-traceability.review.qa` and `output.evidence.qa` are `artifact_class: evidence`.
  - Keys also repeat the owner type (`.qa`, `.cave-dev`, `.documentation`) and the blocking flag (`.optional`).
  - Core's enum includes `policy`; the ADR's omits it. No pinned value uses it.
- **Fix.** If the ids are rewritten (finding 1b), drop the class and owner segments from the id and keep `artifact_class` as the one home. If they are kept verbatim, state that the id is opaque and the prefix carries no meaning. Record the `policy` omission as a divergence. Also align the `id`/`artifact_class` family under S3: within one entry, either both are unprefixed (`id`, `class`) or the ADR says why `artifact_` stays.

### 13. LOW-MEDIUM: `mission_type` as an ArtifactKind leaves sibling enums stale

- **Evidence.**
  - FR-004 adds `ArtifactKind.MISSION_TYPE`, with the plural `mission_types`.
  - The procedure schema's `$defs/ArtifactKind` (13 tokens, described as "the kernel artifact-kind tokens") and the preset's `activated_kinds` enum do not gain `mission_type`/`mission_types`. Yet the preset has `activated_mission_types`.
  - `domain.md:111` says "Avoid the singular `mission_type` as a kind name". That is fragment-scoped, but it reads as contradicting the new artifact-kind token.
- **Fix.** List these enums in FR-004 and FR-012 (update them or justify the omission). Reword `domain.md:111` to "in a fragment the kind is `mission_types`; the artifact-kind token is `mission_type`".

### 14. LOW: "on-demand step" and "action" need glossary entries and a redirect

- **Evidence.**
  - `on_demand_steps` introduces a concept the glossary does not have.
  - `domain.md:109` still defines "Action" as "One step of a mission type's workflow", which is now the Mission step. ADR-0044 admits that the two name the same thing until #189.
  - The ADR calls `on_demand_steps` an "unordered set". The data model says `Set` (insertion-ordered) and the YAML is a list, so the order's round-trip meaning is unstated.
- **Fix.**
  - Add a glossary entry, "On-demand step: a mission step invoked by explicit command, outside the type's sequence".
  - Mark Action in `domain.md` as "the graph node of a mission step (`action:<type>/<step>`), pending #189".
  - Say that the order of `on_demand_steps` is authored and preserved (as a list, like `references`), or canonicalize it. Do not call it a set.

### 15. LOW: grammar and doc details

- **Evidence and fixes.**
  - **(a)** The mission-type id pattern `^[a-z0-9]+(-[a-z0-9]+)*$` (data model) differs from the preset's and toolguide's `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`. Pick the letter-first form, since it is also the role grammar's first-character rule.
  - **(b)** `path_pattern` and `path_conventions` values are project-workspace globs and directories, not pack files. Say so, so nobody applies S13, and give a grammar: relative, no `..`, no leading `/`.
  - **(c)** `content` (the old `agent_context`) is standing agent instructions. Give the schema a description saying so, since the per-step prompts are companions, a different form.
  - **(d)** The ADR's YAML example shows research `scoping` with `output.spec.main`/`output`. The pin is `input.spec.research`/`input`. Use the real value.
  - **(e)** Define what `blocking: true` means on a type-level entry (required at every step? at accept?). `required_always` is empty everywhere, so the case is unexercised.
