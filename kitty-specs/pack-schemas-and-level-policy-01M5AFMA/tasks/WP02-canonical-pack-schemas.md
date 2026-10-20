---
work_package_id: WP02
title: Canonical pack schemas imported with provenance
dependencies: []
requirement_refs:
- C-003
- C-004
- FR-004
- FR-005
planning_base_branch: feat/pack-schemas-and-level-policy
merge_target_branch: feat/pack-schemas-and-level-policy
branch_strategy: Planning artifacts for this mission were generated on feat/pack-schemas-and-level-policy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-schemas-and-level-policy unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-schemas-and-level-policy-01M5AFMA
base_commit: 1685deeed2a219e301525542f80f8e0e3f65e106
created_at: '2026-10-19T18:18:46.912356+00:00'
subtasks:
- T007
- T008
- T009
- T010
- T011
history:
- at: '2026-10-19T18:10:00Z'
  event: created
  by: /spec-kitty.tasks
agent_profile: python-pedro
authoritative_surface: contracts/packs/
create_intent:
- contracts/packs/schemas/pack-descriptor.schema.json
- contracts/packs/schemas/graph-shard.schema.json
- contracts/packs/schemas/org-fragment.schema.json
- contracts/packs/schemas/org-charter.schema.json
- contracts/packs/schemas/directive.schema.json
- contracts/packs/schemas/tactic.schema.json
- contracts/packs/schemas/styleguide.schema.json
- contracts/packs/schemas/toolguide.schema.json
- contracts/packs/schemas/paradigm.schema.json
- contracts/packs/schemas/procedure.schema.json
- contracts/packs/schemas/agent-profile.schema.json
- contracts/packs/schemas/mission-step-contract.schema.json
- contracts/packs/schemas/asset.schema.json
- contracts/packs/schemas/charter.schema.json
- contracts/packs/file-kinds.yaml
- contracts/packs/VENDORED.md
- contracts/packs/README.md
- scripts/packs/sync_schemas.py
execution_mode: code_change
model: sonnet
owned_files:
- contracts/packs/**
- scripts/packs/sync_schemas.py
role: implementer
tags: []
tracker_refs: []
---

# WP02 — Canonical pack schemas imported with provenance

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `python-pedro`). Then load governance context: `spec-kitty charter context --action implement`.
The charter wins over this prompt.

## Objective

Import core's 14 pydantic pack and artifact models as this repository's canonical JSON Schemas
(Draft 2020-12) under `contracts/packs/schemas/` (ADR-0015, ADR-0017; spec FR-004, FR-005; rulings
R1-R2, R4-R9, R13-R15).

- **Single source.** The committed schemas are exactly `transform(export(core@9554e7b2))`,
  produced by one script, `scripts/packs/sync_schemas.py`.
- **Recorded changes.** Every change from the raw export is listed in `VENDORED.md` with its
  ruling.
- **One data authority.** `contracts/packs/file-kinds.yaml` is the single data authority for node
  kinds, fragment plurals, file classification and the reviewed exclusions. The script reads its
  kind lists from it.

Read first:
- [spec.md](../spec.md): FR-004, FR-005 and rulings R1-R16;
- [plan.md](../plan.md): "Vocabulary kept in sync", "Schema loading" and P2, P5, P9;
- [research.md](../research.md): R1, R3 and R4;
- the research scratch is **reference only**, never an input: the raw exports in
  `/tmp/claude-0/-home-user-spec-kitty-charter/e4c21583-84af-5c92-92fe-19a07865220a/scratchpad/implementer/schemas/`
  and the URN pattern drafts in `…/scratchpad/r-core/urn-schema-defs.json`. This WP regenerates
  its own export and owns its own classifier, so nothing from the scratch is committed or
  required.

## Environment

- Core venv: `~/.venv-sk`, with core at `9554e7b2` and pydantic 2.14.1. Run with
  `~/.venv-sk/bin/python -I`.
- Pinned open-packs: the checkout at `../spec-kitty-open-packs`, at the PR #52 head `0b19a83`.
  Its layout is `packs/<pack>/`, with built-in under `packs/built-in`. WP03 pins the same commit.
- Python `jsonschema` 4.26 is in the venv. Use it only for the probe in T011, never in the build
  (C-002).

## Branch Strategy

- Planning base and merge target: `feat/pack-schemas-and-level-policy`.
- Lane worktree from `lanes.json`.
- Implement with:
  `spec-kitty agent action implement WP02 --agent claude --mission pack-schemas-and-level-policy-01M5AFMA`.

## Subtasks

### T007 — `sync_schemas.py`: export the 14 models

**Steps**:

1. Write `scripts/packs/sync_schemas.py` (stdlib plus pydantic plus core; no other dependency).
   - Import the 14 models; the module paths and classes are in research R1, table "Core models":
     `PackDescriptor`, `DRGGraph`, `OrgDRGFragment`, `Directive`, `Tactic`, `Styleguide`,
     `Toolguide`, `Paradigm`, `Procedure`, `AgentProfile`, `MissionStepContract`,
     `AssetManifest`, `OrgCharterPolicy` and `CharterYaml`.
   - Export each with `model_json_schema(mode="validation")`.
   - Write deterministic JSON: `indent=2`, keys in pydantic's order (no sort), a trailing newline
     and UTF-8.
   - Output file names follow `<kind>.schema.json`; the full list is in the frontmatter's
     `create_intent`.
2. The script verifies that the installed core's commit is the one named in `VENDORED.md`. Read it
   from the installed package's metadata, or take a `--core-commit` argument checked against the
   checkout's `git rev-parse`. On a mismatch it fails loudly.
3. Add a `--check` mode: it regenerates into a temporary directory and exits non-zero on any
   difference from `contracts/packs/schemas/`.

### T008 — `contracts/packs/file-kinds.yaml`

**Purpose**: The single data authority (plan "Vocabulary kept in sync"). It is read by the
script, the Java adapter (WP04) and the tests (WP05, WP08).

**Content** (document the shape at the top of the file):

- **`node_kinds`**: the service's 15 tokens (R15): `directive`, `tactic`, `styleguide`,
  `toolguide`, `paradigm`, `procedure`, `agent_profile`, `mission_step_contract`,
  `glossary_pack`, `skill`, `anti_pattern`, `asset`, `template`, `action` and `mission_type`.
  Check them against `service/charter-domain/.../NodeKind.java`.
- **`fragment_kinds`**: plural to node kind, with one name per concept (R14). It holds the 13
  `ArtifactKind` plurals (`ArtifactKind.java:12-36`), plus `mission_types` → `mission_type` and
  `actions` → `action`. It has no `mission_steps` and no `mission_type` alias.
- **`files`**: the rules that map a file to its kind and schema.
  - `pack.yaml` at the pack root goes to `pack-descriptor`.
  - `*.graph.yaml` goes to `graph-shard`.
  - `drg/fragment.yaml` goes to `org-fragment`.
  - `org-charter.yaml` goes to `org-charter`.
  - The nine artifact suffixes (`.directive.yaml` … `.asset.yaml`) map to their schemas.
  - `charter.yaml` goes to `charter`. It is a project file and is not expected in packs.
  - Match on the file name, plus the path where the rule needs it. No rule may match a file that
    another rule also matches.
- **`exclusions`**: every YAML class with no schema, each with a `reason` and a match rule.
  - The 107 files at the pinned commit fall into these classes: step definitions, `index.yaml`,
    `mission.yaml`, `mission-runtime.yaml`, `governance-profile.yaml`, `expected-artifacts.yaml`,
    `*.skill.yaml`, `*.glossary-pack.yaml`, `*.config.yaml`, presets, mission-type definitions,
    `pack-manifest.yaml`, `built-in.source.yaml` and `plan-field-declaration.yaml`.
  - Prefer directory-based matches (`presets/`, `mission_types/`) over bare names such as
    `plan.yaml`.
  - Derive the exact rules from the inventory, re-running the probe classifier at
    `…/scratchpad/probe/classify.py` against the pinned checkout.
  - The reasons are reviewable prose. Most say "no model in core at 9554e7b2".

### T009 — Transform: every change on import

**Purpose**: FR-005. Implement it as functions in `sync_schemas.py`, each named after its change
id, so `VENDORED.md` can cite them.

| Id | Change | Ruling |
|----|--------|--------|
| C1 | Add `"$schema": "https://json-schema.org/draft/2020-12/schema"` and `"$id": "https://schemas.spec-kitty.ai/charter/packs/<kind>.schema.json"`. Every `$ref` stays local (`#/$defs/...`) and every IRI ends in `.json` | R1, P5 |
| C2 | Close the nested sections. Every object schema with `properties` gets `additionalProperties: false`. Objects core types as free-form maps (`additionalProperties: {schema}` or `true` with no `properties`) stay as they are. List each closed `$def` per file | spec open item 1 |
| C3 | URN pattern on the shard node `urn` and on **every** edge `source` and `target` (shards and fragments): `^(<node_kinds joined>):[A-Za-z0-9_/.-]+$`, generated from `file-kinds.yaml` | R6, R15 |
| C4 | Per-kind prefix binding on shard nodes: `kind` enum = `node_kinds`, plus `allOf` of 15 generated `{"if":{"properties":{"kind":{"const":K}},"required":["kind"]},"then":{"properties":{"urn":{"pattern":"^K:"}}}}` | research R4 |
| C5 | Fragment node `kind` enum = the `fragment_kinds` keys. Fragment node `id`: `minLength: 1` and `pattern: ^[A-Za-z0-9_/.-]+$` | R14, P9 |
| C6 | Remove `pack_name`, `source_kind`, `source_ref`, `layer_index` and `provenance_marker` from the fragment's `properties` and `required`. The top level stays closed, so an authored one is refused | R7 |
| C7 | Relation vocabulary: shards and fragments both use the shard `Relation` enum (core `drg/models.py:134-150`, including the legacy `in_tension_with`), **plus `impacts`**. Core has no `impacts` member yet. ADR-0009 D1 and ADR-0014 D-2 rule that the contract leads and core aligns at the cutover, and the service's `Relation` enum has it (`Relation.java`). There is no `extends` alias. Check the final enum against `service/charter-domain/.../Relation.java` | P2, ADR-0009 D1 |
| C8 | Shard `generated_by`: removed from `required`. `generated_at` stays required | R8 |
| C9 | Pack descriptor: `pack_version` added to `required` (every level); `pack_level` removed from `required` (an enum when present); `requires_core` optional string; top-level `additionalProperties` true (unknown and retired keys are reported by the adapter, not refused) | R3, R4, R5, R13 |
| C10 | Coercions dropped. Agent `roles` items get the lowercase pattern `^[a-z][a-z0-9_-]*$`. Integer fields stay `type: integer`, so a quoted number is refused. A scalar `role` stays refused by the closed object. Org-charter `schema_version` stays `integer` (`"1"` refused). Document each, including the ones the export already gets right | R9 |
| C11 | Lost validators that a schema can express: agent `routing_priority` `minimum 0`, `maximum 100`; `max_concurrent_tasks` `minimum 1`; directive lenient adherence (`enforcement: lenient-adherence` → `required: [explicit_allowances]`) as `if/then`. Check the exact field names and enum value against core `directives/models.py:135` and `agent_profiles/profile.py:337-345` | spec FR-005f |
| C12 | Shard and fragment edges get an optional `impacts: {"type": "number"}` property. Core's `DRGEdge` has none (ADR-0009 D1). There is no range in the schema: the strength rule, including NaN, stays in the domain (plan) | ADR-0009 D1, plan |
| C16 | Edge `provenance` is removed from the authored edge schema. Core's `DRGEdge.provenance` (`drg/models.py:409-412`) is a merge-time marker core stamps, not authored data, and the service's reader refuses it today. No open-packs DRG edge carries it | P10 (R7's principle) |
| C13 | Nullable `anyOf[T, null]` is kept as exported. Document that the domain collapses it (WP05) | plan |
| C14 | Patterns use only constructs portable between ECMA-262 and Java (character classes, anchors and alternation). The optional regex engines are absent, so JDK regex applies | research R2 |
| C15 | `charter.yaml`: C1 and C2 only. Whether this repository's own `.kittify/charter/charter.yaml` validates is measured in WP08 (P7) | P7 |

C7, C12 and C16 get divergence-register entries DIV-018 (`impacts`) and DIV-019 (edge `provenance`), written by WP09.

Before writing a change, check its premise against the raw export. Example: does the export
already make `pack_level` required? (It does.) Record any premise that does not hold in
`VENDORED.md` instead of inventing a change.

### T010 — `VENDORED.md` and `README.md`

Follow the house model in `contracts/_shared/VENDORED.md`. Record:

- **Origin**: core `spec-kitty/spec-kitty` at `9554e7b2…` (full SHA from the core checkout),
  pydantic 2.14.1, and the command `~/.venv-sk/bin/python -I scripts/packs/sync_schemas.py`.
- **Every change** C1 to C16: what changed, in which files, why, and the ruling or call.
- **The rule from ADR-0015 D4**: a refresh never overwrites a listed change, and `--check`
  proves the committed files are reproducible.
- **Cross-links** to the divergence register (`docs/conformance/divergence-register.md`, created
  by WP09) and to ADR-0017.

`README.md` is short: what the schemas are for, how a pack author validates a file, and that the
schemas are canonical here (ADR-0015).

Both files must pass `npx --yes markdownlint-cli2@0.18.1 contracts/packs/*.md`. The cspell gate's
scope is `docs/**`, so `contracts/packs/` is outside it. Still keep the spelling clean: the
wordlist (`docs/_meta/spelling-words.txt`) belongs to WP09 and WP10, so report any new term to
the orchestrator rather than editing it.

### T011 — Corpus probe and reproducibility

**Steps**:

1. Run `sync_schemas.py --check` and record its clean exit.
2. Probe the **transformed** schemas against every mapped YAML file at the pinned checkout with
   Python `jsonschema` (venv). Write your own small classifier from `file-kinds.yaml`, with no
   hard-coded layout; the research `classify.py` hard-codes the old layout. Walk
   `<checkout>/packs/**/*.yaml` and load with `ruamel.yaml` `typ="safe"`, which is in the venv.
   - Expect 409 mapped files, all valid, and 107 excluded.
   - If closing nested sections (C2) or a pattern refuses a real file, **stop**. Record which file
     and which change, and report it to the orchestrator: it may need an operator ruling (K8). Do
     not loosen a change silently.
3. Probe each change with one hand-written invalid sample. Confirm that the transformed schema
   refuses it where the raw export accepted it, or the reverse for the relaxations C6, C8 and C9.
   These samples are scratch only; WP08 plants the real ones.
4. Record the probe command and output in the WP evidence.

## Definition of Done

- [ ] 14 schemas, `file-kinds.yaml`, `VENDORED.md` and `README.md` are committed.
      `sync_schemas.py --check` is clean.
- [ ] The probe at the pinned commit shows 409 mapped files valid, 107 excluded and 0
      unclassified.
- [ ] Every change C1 to C16 is either listed with its ruling or recorded as "premise did not
      hold".
- [ ] No schema contains a non-local `$ref` or an IRI that does not end in `.json`.
- [ ] Commits carry the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Risks

- **K8.** Closing nested sections might refuse real files. The probe catches it before the commit.
- **The URN alternation must equal `NodeKind`.** WP05's equality test enforces it.

## Reviewer guidance (opus, reviewer-renata)

- Re-run `sync_schemas.py --check` and the probe yourself.
- Check every `VENDORED.md` change against the actual diff between the raw export and the
  committed file.
- Check that nothing in the transform hard-codes a kind list instead of reading `file-kinds.yaml`.
