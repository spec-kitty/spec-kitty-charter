---
okf_version: "0.2"
title: "Charter"
description: "Glossary terms for the Charter context."
kind: Glossary
glossary_context: "Charter"
doc_status: active
generated:
  by: "doc-kitty-glossary/0.5.0"
  at: "2026-10-26T21:16:31Z"
  content_hash: "sha256:e1183cd9535e"
---

# Charter

The governing term `charter` and the doctrine domain model: Charter Packs, artifact kinds, activation, selection and the DRG relations.

{#all-declaration-convention}

## `__all__` Declaration Convention

The project convention that every Python module with a public API must declare an `__all__` list enumerating its exported symbols. The `test_no_dead_symbols.py` architectural gate uses `__all__` as the canonical public surface; symbols absent from `__all__` are not counted as dead even if unreferenced, and symbols present in `__all__` but never imported externally are flagged as candidates for removal.

{#activated-kind}

## `activated_<kind>`

The `.kittify/config.yaml`-resolved, three-state per-kind activation authority for a doctrine artifact kind (`activated_paradigms`, `activated_directives`, `activated_tactics`, `activated_styleguides`, `activated_toolguides`, `activated_procedures`, `activated_agent_profiles`, `activated_mission_step_contracts`, `activated_glossary_packs`). Resolved via `PackContext.from_config`'s INV-2 two-file pointer chase: read `.kittify/config.yaml` directly, unless it carries a string `charter:` pointer, in which case follow it to the pointed `.kittify/charter/charter.yaml` and read the keys there instead. Three states per kind, applied independently: an ABSENT key resolves to `None` ("all built-ins available"); an explicit empty list (`[]`) is an explicit opt-out (nothing of that kind activated); a non-empty list is the explicit activated set. This is the field the compiler (`compile_charter`) and — after mission `spdd-reasons-activation-split-brain-01M1K6VN` — `is_spdd_reasons_active`, `_load_action_doctrine_bundle`, and `resolve_project_governance` all resolve activation from: the actual mechanism behind Charter-Mediated Selection, distinct from the charter-authored selected_&lt;kind&gt; / required_&lt;kind&gt; declaration surface.

{#activation-context}

## Activation Context

What the client is doing right now, sent with a resolution request: for example project <id>, a mission run of type <type>, step <X>, working on a Python implementation. The service resolves the project's one active charter against it and returns the artifacts that apply to that step (directives, profile, prompts and so on). It may also carry a temporary addition for this run. Core's narrower form is a two-field key (`mission_type`, `action`, with `generic` as wildcard) that scopes an Activation Registry entry.

**Meta:**

- **status:** `canonical`

{#activation-preset}

## Activation preset

A named selection that a Charter Pack ships (`presets/<name>.yaml`) and a project applies to its active charter. A preset has two jobs: as a governor it states what must be on the plate (its must-have items are the requirement), and as a convenience it offers an author's sane defaults to a project that does not want to customize. For the convenience job, applying a preset is a one-time write to the active charter; a governing preset keeps holding. How a governing preset is told apart from a convenience preset is an open question (ADR-0031). The built-in pack ships `default` and `minimal`.

**Meta:**

- **status:** `canonical`

{#activation-registry}

## Activation Registry

Charter-level list of `(activation_context, charter_pack_id, artifact_id)` tuples expressing which charter artifacts activate in which contexts (`charter_pack_id` names the Charter Pack). Lives on the charter (not on the artifact) so different projects can activate the same shared artifact in different contexts without forking it. Both project charter and org charter may declare entries; org-declared entries propagate to consumers via the standard org-charter pre-fill.

{#active-charter}

## Active charter

What a project has chosen from the charter offering: its plate, as against the buffet. One active charter per project; there is no separate per-mission charter. It names the offering packs it builds on by id and version, and its activations and deactivations. The project's local `activation.yaml` is the source of truth, written through to the service (write-through active charter); the service resolves it per activation context. "Active charter" names the project's chosen set as a whole; "active" alone still describes one artifact; the `.kittify/charter/` tree is never called "active".

**Meta:**

- **status:** `canonical`

{#cat-7-grandfathered-orphan}

## Cat-7 Grandfathered Orphan

A dead-module or dead-symbol violation that has been explicitly classified as category 7 ("deferred — grandfathered") in the remediation tracking spreadsheet. Cat-7 items are excluded from the active failure count tracked by the ratchet baseline but must not be added to the allowlist via side-effect imports (the C2 anti-pattern). Each cat-7 record must carry a deferral reason and a target WP for cleanup.

{#catalog-miss}

## Catalog Miss

A doctrine artifact ID referenced by a charter selection (e.g. `selected_directives: [foo]`) that does not resolve to any known artifact in the shipped pack, any configured org pack, or the project-layer doctrine tree. Catalog misses are reported as errors by `spec-kitty doctor charter-packs` and by the `test_no_dead_symbols.py` gate when the referencing code reaches into the doctrine catalog.

{#charter}

## charter

The governance document synthesizing a project's purpose, constraints, team norms, and the body of project-specific governance artifacts (directives, tactics, and styleguides) into a durable reference artifact, produced by the charter interview workflow. This is the canonical governing term (glossary authority 1/2/3: `.kittify/glossaries/spec_kitty_core.yaml`, the built-in glossary pack, this entry).

{#charter-bundle}

## Charter Bundle

The materialized `.kittify/charter/` tree as a unit: `charter.md`, the synthesis manifest, the DRG cache and provenance. Produced by `spec-kitty charter generate` and `spec-kitty charter synthesize`; validated by `spec-kitty charter bundle validate`.

{#charter-facade}

## Charter Facade

A `src/charter/<facade>.py` module that re-exports (or thinly wraps) a doctrine surface so runtime callers can consume it as `from charter.<facade> import X` instead of `from doctrine.<x> import X`. Examples: `charter.profiles`, `charter.mission_steps`, `charter.drg`, `charter.primitives`, `charter.resolution`, `charter.versioning`. The set of facades is the runtime → charter → doctrine boundary's public surface.

{#charter-offering}

## Charter offering

Everything a project could adopt: the buffet. It is made of Charter Packs, mostly a foundation to build on rather than a set of rules, where a derived pack builds on a broader one (org on built-in) and customizes it. The offer side of the charter; the choice side is the Active charter. The service may hold several versions of a pack side by side.

**Meta:**

- **status:** `canonical`

{#charter-pack}

## Charter Pack

A distributable bundle of charter components (artifacts and their DRG edges) together with optional activation presets shipped as `presets/<name>.yaml`. An org pack may also enforce activations through `required_<kind>` lists in its `org-charter.yaml`. A pack is identified by its `charter_pack_id` (`built-in`, an org pack's configured name, or `project`); org packs are configured under `charter_packs.org.packs` in `.kittify/config.yaml`. Every pack now declares its level in `pack.yaml` via the `pack_level` field — one of `built-in`, `org` or `project` (ADR 2026-10-11-2). The level names have one authority, `kernel.pack_tiers`; the tier token and the provenance/layer value now share a single spelling, `built-in` (the former no-hyphen `builtin` provenance spelling is folded away). Loading refuses a pack whose declared level disagrees with where it is configured (`PACK_LEVEL_MISMATCH`), and a `pack.yaml` missing the field (`PACK_LEVEL_MISSING`); for one release an org pack without a `pack.yaml` loads with a deprecation warning before that refusal takes effect. Validated with `spec-kitty charter pack validate`, listed with `spec-kitty charter pack list`.

{#charter-mediated-selection}

## Charter-Mediated Selection

Architectural pattern in which the project / org charter is the sole authority that decides which doctrine artifacts apply to a given mission run. Doctrine is the knowledge store; charter is the selector; runtime asks charter for the activated set rather than reaching into doctrine directly. Enforced via the runtime → charter → doctrine boundary. This framing is correct at the architectural level; the MECHANISM by which the charter's activation decision is actually resolved today is activated_&lt;kind&gt; — not `selected_<kind>` alone. `selected_<kind>` is the authoring record of what an interview/pack once selected; `activated_<kind>` is what `PackContext.from_config` resolves via the INV-2 two-file pointer chase.

{#charterscope}

## CharterScope

A resolution context (`charter.scope::CharterScope`) that maps a filesystem path to the appropriate charter when multiple charters coexist in a monorepo. Single-project repositories use `CharterScope.default(repo_root)` which returns the root-scoped charter. Monorepos with multiple sub-project charters configure `charter_scopes:` in `.kittify/config.yaml`; `CharterScope.resolve(repo_root, feature_dir)` then finds the nearest-enclosing charter for any given feature directory. The resolved `scope.root` is forwarded to `build_charter_context` (or via `build_with_scope`) so the correct per-package charter governs prompt generation. See ADR-8 (`docs/adr/3.x/2026-05-18-1-monorepo-charter-scope.md`).

{#context-scoped-selection}

## Context-Scoped Selection

Selection mode in which an artifact applies only in a given activation context: the service resolves the active charter against what the client is doing (project, mission run of a type, step, work context) and returns only the applicable artifacts. A client that wants "for this mission, also include X" adds X to its active charter or passes it in its activation context; there is no separate per-mission charter. Core's form is the Activation Registry.

**Meta:**

- **status:** `canonical`

{#deactivation}

## Deactivation

A per-project choice to leave an artifact off the project's active charter. It persists across upgrades, and is refused for a required artifact. Not removal, which a derived pack does for every project that uses it.

**Meta:**

- **status:** `canonical`

{#delegation-delegates-to-drg-relation}

## Delegation — `delegates_to` (DRG Relation)

A directional Three-layer DRG relation expressing a *runtime* handoff: one agent profile hands work to another at execution time. It is kept deliberately separate from Lineage so a static "derives from" relationship never gets conflated with a live work handoff. No built-in artifact emits a `delegates_to` edge today — delegation is currently expressed in profile `collaboration.handoff_to` prose rather than as a graph edge, so treat this relation as intended-but-dormant. Canonical source: `RELATION_DESCRIPTIONS[Relation.DELEGATES_TO]` in `src/charter/offering/drg/models.py`.

{#derived-pack}

## Derived pack

A Charter Pack that builds on a broader one (an org pack on built-in) and customizes it for its context. It may add artifacts, change existing ones within their integrity level, suggest a selection through presets, remove artifacts, lock or remove roles, pin the profile for a role and declare a fallback profile.

**Meta:**

- **status:** `canonical`

{#directive}

## Directive

A constraint-oriented governance rule that applies across flows or phases. Directives encode required or advisory expectations and can reference lower-level tactics for execution.

{#doctrine-domain}

## Doctrine Domain

The domain model that structures reusable governance knowledge in Spec Kitty. It organizes behavior and constraints into composable artifacts (paradigms, directives, tactics, templates, styleguides, and toolguides).

{#example-mapping-workshop-procedure}

## Example Mapping Workshop (Procedure)

Procedure `example-mapping-workshop`. Turns a behavior request into concrete rules, canonical examples, and open questions that stakeholders and implementers share as the current source of truth for the behavior.

{#global-selection}

## Global Selection

Selection mode in which an artifact is in force for every step of every mission, whatever the action or mission type: it is on the project's active charter. What must be selected is stated by a requirement, authored as a preset; what a mission type adds while it runs is its recipe ingredients. Core expresses this today with `selected_<kind>` / `required_<kind>` lists (deprecated here), unioned onto the activated_&lt;kind&gt; base.

**Meta:**

- **status:** `canonical`

{#glossary-vocabulary-vocabulary-drg-relation}

## Glossary Vocabulary — `vocabulary` (DRG Relation)

A directional Three-layer DRG relation from a resolved doctrine artifact to a glossary-scope node, meant to surface which glossary sections are relevant once governance context has been resolved for an action. The traversal step exists and is exercised by tests, but no built-in or org-pack artifact currently emits a `vocabulary` edge — treat it as intended-but-dormant rather than actively exercised. Canonical source: `RELATION_DESCRIPTIONS[Relation.VOCABULARY]` in `src/charter/offering/drg/models.py`.

{#governance-scope-scope-drg-relation}

## Governance Scope — `scope` (DRG Relation)

A directional Three-layer DRG relation from a mission-step action node to the directives and tactics that govern performing that action. It is the entry point of governance-context resolution: the resolver first walks `scope` edges from the action node, then expands through Hard Dependency and Soft Recommendation edges from what it found. It is one of the most heavily emitted relations tied to action nodes in the built-in graph. Distinct from Workflow Application — see that entry for the contrast. Canonical source: `RELATION_DESCRIPTIONS[Relation.SCOPE]` in `src/charter/offering/drg/models.py`.

{#guideline}

## Guideline

A high-level doctrine rule expressing non-negotiable or strongly preferred governance behavior. Guidelines sit at the highest precedence and bound what project-level charter may customize.

{#hard-dependency-requires-drg-relation}

## Hard Dependency — `requires` (DRG Relation)

A directional Three-layer DRG relation marking a mandatory prerequisite: the source artifact cannot be meaningfully resolved without the target also being present and considered. Governance-context resolution for a mission-step action walks `requires` edges transitively (no hop limit) once it has collected the action's scoped artifacts, and the charter activation cascade follows the same edge to pull in artifacts that must also be active. It is the most heavily used relation in the built-in graph. Canonical source: `RELATION_DESCRIPTIONS[Relation.REQUIRES]` in `src/charter/offering/drg/models.py`.

{#import-candidate}

## Import Candidate

A pull-based curation record for an external doctrine idea. Captures source provenance, target classification, adaptation notes, and adoption status before canonization.

{#integrity-level}

## Integrity level

How much an artifact may be altered after it is introduced: `open`, `extendable` or `locked`. Set by the artifact's author; anyone below may tighten it, never loosen it. Independent of requirement (required-but-extendable is valid). A derived pack may remove an `open` or `extendable` artifact; a `locked` one is fully protected.

**Meta:**

- **status:** `canonical`

{#lineage-specializes-from-drg-relation}

## Lineage — `specializes_from` (DRG Relation)

A directional, static Three-layer DRG relation: a profile or artifact derives from a parent, narrowing or extending it. In the built-in graph this appears only between `agent_profile` nodes (e.g. a language-specialist implementer profile specializing from the generic implementer profile), and is resolved through `AgentProfileRepository.resolve_profile` graph traversal at composition time. Deliberately distinct from Delegation so inheritance never leaks into runtime handoff traversal. Canonical source: `RELATION_DESCRIPTIONS[Relation.SPECIALIZES_FROM]` in `src/charter/offering/drg/models.py`.

{#living-documentation-sync-directive}

## Living Documentation Sync (Directive)

Directive DIRECTIVE_037. Requires behavior-describing artifacts to evolve together: when observable behavior changes, the canonical examples, acceptance checks, narrative specs, user docs, glossary entries, code-level docs, and architecture records are updated in the same change.

{#mission-step}

## Mission step

One step of a mission type's recipe. A step names the role it needs, never a profile.

**Meta:**

- **status:** `canonical`

{#mission-type}

## Mission type

The recipe of the work: shipped in a pack and activated like any other artifact; once it is on the active charter, missions of that type can be run. Its steps say how to make the dish and ask for roles, never profiles. Its recipe ingredients are the guidance it names by id, added to the plate while a mission of that type runs. Modelled as its own concept, with its own domain model and endpoints.

**Meta:**

- **status:** `canonical`

{#mission-type-profile}

## Mission-Type Profile

Superseded by the recipe's ingredients of a Mission type. Today's shipped `governance-profile.yaml` per mission type, read by core as "while a mission of this type runs, these artifacts are in scope". In the new model a mission type names the guidance it calls for by id, activating the mission type is the consent, and it names no profiles (steps ask for roles; `selected_agent_profiles` is dropped). Former definition, kept for history: Shipped governance profile per mission type (`software-dev`, `documentation`, `research`, `plan`) at `packs/built-in/missions/<type>/governance-profile.yaml`. Declares default selections and default activations for that mission type. The charter resolver reads `meta.json mission_type`, picks the matching profile, and unions its declarations into the project + org selections. No `software-dev-default` fallback for non-software missions — the resolver hard-fails if a mission's `mission_type` has no matching profile and the project has not declared its own.

**Meta:**

- **status:** `deprecated`

{#organization-tier}

## Organization Tier

The middle layer of the three-layer DRG model, contributed by one or more org Charter Packs configured under `charter_packs.org.packs` in `.kittify/config.yaml`. Each pack ships an `org-charter.yaml` (governance policies and required artifact selections) and an optional `drg/fragment.yaml` (DRG extension nodes and edges). Organization-tier content propagates to all consumer projects via `apply_org_charter_to_interview` and the standard charter pre-fill path.

{#overlay-enhancement-enhances-drg-relation}

## Overlay Enhancement — `enhances` (DRG Relation)

A directional org-pack overlay relation: a pack artifact field-merges additional content into a built-in artifact, preserving the built-in's existing action sequence and step I/O rather than discarding them. No built-in artifact emits this edge — by design, `enhances` only ever originates from an org- or project-tier pack fragment layered on top of a shipped artifact, never between two built-in nodes. Distinct from Overlay Override, which replaces rather than merges. Canonical source: `RELATION_DESCRIPTIONS[Relation.ENHANCES]` in `src/charter/offering/drg/models.py`.

{#overlay-override-overrides-drg-relation}

## Overlay Override — `overrides` (DRG Relation)

A directional org-pack overlay relation: a pack artifact declares a full replacement of a built-in artifact's content rather than a field-merge. Like Overlay Enhancement, no built-in artifact emits this edge by design — it only ever originates from an org- or project-tier overlay. Silently dropping steps or stripping step input/output when applying an `overrides` edge is rejected rather than tolerated. Canonical source: `RELATION_DESCRIPTIONS[Relation.OVERRIDES]` in `src/charter/offering/drg/models.py`.

{#paradigm}

## Paradigm

A named school of practice with a worldview, such as DDD, BDD or trunk-based development. It has no rules of its own: it explains why a set of guidance belongs together, and it is adopted, neither followed nor used. Adopting it brings in the directives, tactics, styleguides and procedures it points at. It states its principles and must pass the paradigm test; a single convention (Git Flow) does not. Tensions between paradigms are expressed through values and the `tension` relationship, not through a paradigm-level exclusion.

**Meta:**

- **status:** `canonical`

{#paradigm-principles}

## Paradigm principles

The short list of core ideas a paradigm states, in the style of Kent Beck's Test Desiderata.

**Meta:**

- **status:** `canonical`

{#paradigm-test}

## Paradigm test

Whether something is a paradigm: a school of practice with a worldview that brings several practices together and explains why they belong together. A single convention or workflow fails it. Which shipped paradigms fail it, and where they go instead, is left to a later reclassification pass.

**Meta:**

- **status:** `proposed`

{#procedure}

## Procedure

A playbook: a bounded, step-by-step way to handle one situation, with entry and exit conditions. It is used inside a step; nothing runs or tracks it, and it has no state, deliverables, gates or role slots, so it sits below a mission type. A tactic is one technique; a procedure is an episode that combines techniques. A rule-shaped procedure is a directive. The `operator` role marks where a procedure asks the human for input or action.

**Meta:**

- **status:** `canonical`

{#project-layer}

## Project layer

The project's own charter components, under the one flat root `.kittify/charter-packs/` (`charter_pack_id` `project`). It is part of the Charter offering, ships no presets, and is listed by `spec-kitty charter pack list` as the `project` row. `spec-kitty charter synthesize` writes here.

{#ratchet-baseline}

## Ratchet Baseline

A snapshot of a quality metric (failure count, symbol count, dead-module count) recorded in `tests/architectural/ratchet-baseline-*.md` and enforced by the corresponding architectural gate. A ratchet baseline only moves in the decreasing direction during normal development; any increase fails CI. Org packs may declare additional ratchet metrics via governance policies, but cannot lower an existing shipped baseline.

{#recipe-ingredients}

## Recipe ingredients

The guidance a mission type names by id (today in `governance-profile.yaml`). While a mission of that type runs, these artifacts are added to the plate; activating the mission type is the consent. The ingredients name no profiles: roles stay the only link to profiles. Replaces Mission-Type Profile.

**Meta:**

- **status:** `canonical`

{#reconciliation-reconciles-tension-drg-relation}

## Reconciliation — `reconciles_tension` (DRG Relation)

A directional Three-layer DRG relation linking an active reconciliation artifact to one side of a declared Tension pair. A tension pair is treated as resolved only when an active artifact carries this edge to both sides of the pair — an edge to just one side leaves the pair half-reconciled and still flagged. It is authored explicitly and is never inferred from an `in_tension_with` edge. Canonical source: `RELATION_DESCRIPTIONS[Relation.RECONCILES_TENSION]` in `src/charter/offering/drg/models.py`; human-readable mirror in `docs/architecture/doctrine-relationships.md` ("Tension vocabulary").

{#refinement-refines-drg-relation}

## Refinement — `refines` (DRG Relation)

A directional Three-layer DRG relation: an artifact narrows or sharpens the applicability or meaning of a parent or built-in target without replacing it. It is a first-class, traversable relation in its own right — never a stand-in for Workflow Application or Lineage. No built-in artifact currently emits a `refines` edge, so treat it as intended-but-dormant; an earlier version of the org-to-DRG bridge silently downgraded authored `refines` edges to `applies`, but that lossy downgrade has since been removed. Canonical source: `RELATION_DESCRIPTIONS[Relation.REFINES]` in `src/charter/offering/drg/models.py`.

{#rejection-rejects-drg-relation}

## Rejection — `rejects` (DRG Relation)

A directional Three-layer DRG relation from a good artifact to a marked anti-pattern or smell node (`NodeKind.ANTI_PATTERN`), expressing rejection of a named bad practice. It is distinct from Tension — the target is not a competing equal, it is a bad practice — and from `replaces`/supersession, since the target was never a valid rule to begin with. Canonical source: `RELATION_DESCRIPTIONS[Relation.REJECTS]` in `src/charter/offering/drg/models.py`; human-readable mirror in `docs/architecture/doctrine-relationships.md` ("Tension vocabulary").

{#removal}

## Removal

A pack-wide act by a derived pack: one named artifact (or role) is taken out of the offering for every project that uses the pack. Allowed for `open` and `extendable` artifacts, refused for `locked` ones; declared one artifact at a time, never by filter. Not deactivation, which is a per-project choice on the active charter (ADR-0030).

**Meta:**

- **status:** `canonical`

{#requirement-charter-lever}

## Requirement (charter lever)

"You must take this": a rule that prevents a project from leaving an artifact inactive; the primary way a rule is enforced, honoured locally (deactivating a required artifact is refused). It is authored as a preset: the preset's must-have items are the required set, and the service derives the required list from it. A prohibition is a requirement phrased inversely. Independent of integrity.

**Meta:**

- **status:** `canonical`

{#rules-and-resources}

## Rules and resources

Two families of charter artifacts. Rules are followed (directive, tactic, styleguide and the like); resources are used (template, asset, skill). Procedures are used inside a step. A resource can make a rule checkable, linked to it by the `supports` relationship. Paradigms are neither: they are adopted.

**Meta:**

- **status:** `canonical`

{#schema-doctrine-artifact}

## Schema (Doctrine Artifact)

A machine-validated contract that defines allowed structure and fields for doctrine artifacts. Used in CI/tests to fail fast when invalid doctrine files are introduced.

{#selected-kind-required-kind}

## `selected_<kind>` / `required_<kind>`

Superseded. In the new model a mission type's guidance is named by id as its recipe ingredients, and a requirement is authored as a preset whose must-have items are the required set; the service derives the required list from the preset (ADR-0031). How the old `required_<kind>` lists are read for compatibility is an open question. Former definition, kept for history: The field-naming convention for Global Selection entries. On the project charter (`GovernanceCharterConfig`), each artifact kind gets a `selected_<kind>: [<id>, ...]` field — `selected_directives`, `selected_styleguides`, `selected_toolguides`, `selected_paradigms`, `selected_tactics`, `selected_procedures`, `selected_agent_profiles`, `selected_mission_step_contracts`. On the org charter (`OrgCharterPolicy`), the mirror is `required_<kind>: [<id>, ...]`. `apply_org_charter_to_interview` unions the org `required_<kind>` into the project `selected_<kind>` non-destructively. The architectural test `test_artifact_selection_completeness.py` enforces parity — every `ActiveCharterService` artifact kind has both a `selected_*` and a `required_*` field. These fields are the CHARTER-AUTHORED record of a selection, not an independent activation source: they are unioned as an additive project-local layer over the resolved activated_&lt;kind&gt; base (`PackContext.from_config`) at the surfaces that resolve activation today (e.g. `_resolve_directive_base` in `resolver.py`).

**Meta:**

- **status:** `deprecated`

{#soft-recommendation-suggests-drg-relation}

## Soft Recommendation — `suggests` (DRG Relation)

A directional Three-layer DRG relation pointing at content that is relevant but optional. Unlike Hard Dependency, which is walked transitively with no depth limit, governance-context resolution only follows `suggests` edges a bounded number of hops, and the charter activation cascade treats a `suggests` target as something an operator may accept or decline. It is emitted more often than any other relation in the built-in graph, but that volume is incidental — the depth-bounded walk, not the count, is what separates it from `requires`. Canonical source: `RELATION_DESCRIPTIONS[Relation.SUGGESTS]` in `src/charter/offering/drg/models.py`.

{#specification-by-example-paradigm}

## Specification by Example (Paradigm)

A paradigm that builds shared understanding of behavior through concrete, business-readable examples which become the canonical source of truth for requirements, acceptance checks, and living documentation.

{#step-contract}

## Step contract

The detailed version of a mission step: what the step does, its inputs and outputs, and the role it asks for.

**Meta:**

- **status:** `canonical`

{#step-template}

## Step template

The prompt fed to the agent when a mission step runs.

**Meta:**

- **status:** `canonical`

{#styleguide}

## Styleguide

A doctrine artifact defining cross-cutting quality and consistency conventions (for example coding, documentation, or testing style) that apply across missions and templates.

{#supersession-replaces-drg-relation}

## Supersession — `replaces` (DRG Relation)

A directional Three-layer DRG relation asserting that the source artifact fully supersedes the target, which stops applying once the source is active. It is retained for backward compatibility with older, hand-authored fragments; no built-in artifact emits it today, since current practice either deactivates the superseded artifact directly or, for pack overlays, expresses supersession through Overlay Override. Distinct from Tension, which never implies either side is deprecated or wrong. Canonical source: `RELATION_DESCRIPTIONS[Relation.REPLACES]` in `src/charter/offering/drg/models.py`.

{#supports-supports-drg-relation}

## Supports — `supports` (DRG Relation)

A relationship from a resource to a rule it helps check, for example a companion asset that lets an agent verify its own compliance with a directive deterministically. Distinct from `suggests`, which points at optional relevant content.

**Meta:**

- **status:** `canonical`

{#symbol-level-dead-code}

## Symbol-level Dead Code

A public symbol (function, class, constant) exported by a module but not referenced by any other module or test in the codebase, as detected by `tests/architectural/test_no_dead_symbols.py`. Distinguished from module-level dead code (an entire module with no importers). Symbol-level findings are reported per-file and contribute to the dead-symbol ratchet baseline.

{#tactic}

## Tactic

A reusable behavioral execution pattern that defines how work is performed. Tactics are operational and agent-consumable, and can be selected by directives and mission context.

{#template-instantiation-instantiates-drg-relation}

## Template Instantiation — `instantiates` (DRG Relation)

A directional Three-layer DRG relation from a mission-step action node to the template it produces as its concrete output. It appears only between `action` and `template` nodes in the built-in graph, in modest numbers, and is distinct from Governance Scope: `scope` links an action to content it must follow, `instantiates` links it to content it produces. Canonical source: `RELATION_DESCRIPTIONS[Relation.INSTANTIATES]` in `src/charter/offering/drg/models.py`.

{#template-set}

## Template Set

A structured set of doctrine templates that shape output artifacts and interaction contracts for mission actions and procedures. Template sets allow consistent behavior across mission types while remaining configurable through charter selections.

{#tension-in-tension-with-drg-relation}

## Tension — `in_tension_with` (DRG Relation)

Superseded by Tension — `tension` (DRG Relation) (ADR-0028). The former name of the authored relationship between two co-valid rules that compete on the same decision; core's DRG still uses it. In this repository the relationship is named `tension`, and `impacts` now names an artifact's per-value list (value impact), not a relation. Former definition, kept for history: A symmetric, non-transitive Three-layer DRG relation marking two co-valid, co-activatable artifacts that compete on the same decision. Stored as a single canonical edge (lexicographically-smaller URN as source) and queryable from either endpoint; it does not imply that either side is deprecated, superseded, or wrong — both remain valid rules until an operator deactivates one side or activates a reconciler (Reconciliation). Canonical source: `RELATION_DESCRIPTIONS[Relation.IN_TENSION_WITH]` in `src/charter/offering/drg/models.py`; human-readable mirror and worked examples in `docs/architecture/doctrine-relationships.md` ("Tension vocabulary").

**Meta:**

- **status:** `deprecated`

{#tension-tension-drg-relation}

## Tension — `tension` (DRG Relation)

An authored relationship between two rules that compete on the same decision; both stay valid. It is never derived from value impacts, and it may sit beside one structural relationship on the same pair (two rules can require each other and still be in tension). Replaces `in_tension_with` (ADR-0028).

**Aliases:** in_tension_with

**Meta:**

- **status:** `canonical`

{#three-layer-drg}

## Three-layer DRG

The three-tier Doctrine Relationship Graph composed of: (1) the shipped built-in layer (the per-kind `*.graph.yaml` fragments under `packs/built-in/`), (2) zero or more org-tier extension fragments (`drg/fragment.yaml` inside each configured org pack), and (3) optional project-tier annotations declared in the project charter. Each tier is additive; org and project tiers may only extend or annotate shipped nodes — they cannot remove or reclassify them. Resolved at runtime by `charter.drg.merge_three_layers`.

{#toolguide}

## Toolguide

A doctrine artifact defining tool-specific operational guidance, syntax, and constraints (for example PowerShell usage conventions) used by agents and contributors during execution.

{#trigger-registry}

## Trigger Registry

Canonical frozenset of agent-action tokens (e.g. `write_comment`, `write_docstring`, `rename_identifier`, `add_dependency`) the prompt builder knows how to emit "when you <token>, …" stanzas for. Any `triggers:` value declared on a shipped doctrine artifact must be a member of this set; the architectural test `test_trigger_registry_coverage.py` enforces no dead triggers. Mission B's WP05 populates the initial set.

{#usage-examples-sync-tactic}

## Usage Examples Sync (Tactic)

Tactic `usage-examples-sync`. The step-by-step pattern for keeping canonical usage examples, acceptance checks, and the artifacts that quote them aligned during a behavior change.

{#value-charter}

## Value (charter)

One of a defined set of qualities a rule can cost or gain on. A base set ships (AMMERSE by default) and packs may extend it; making the set fully configurable is a possible later extension. Values make trade-offs visible; the agent surfaces them and never decides them silently. The numeric layer (interaction matrix, ranking) is not part of the model yet.

**Meta:**

- **status:** `canonical`

{#value-impact}

## Value impact

An artifact's `impacts` list: one entry per value, `{value, strength, rationale}`, saying what adopting the artifact costs or gains on that value. A strength is never shown without its rationale, and the rationale wins when they disagree. `impacts` names this list, not a relationship between two artifacts (ADR-0028).

**Meta:**

- **status:** `canonical`

{#workflow-application-applies-drg-relation}

## Workflow Application — `applies` (DRG Relation)

A directional Three-layer DRG relation from an agent profile to the concrete procedure or tactic it executes as its operating workflow. The built-in graph contains zero `applies` edges and authoring one into the shipped tree is refused by `tests/architectural/test_no_authored_applies_edge.py`: nothing traverses `applies` — not governance-context resolution, not the charter cascade, not the reference walk — so an authored edge names a relationship no reader follows. Use `requires` when the profile must actually reach the artifact. The relation itself is not retired: project-tier charter synthesis still emits it. Distinct from Governance Scope: `applies` names what a profile *does*, `scope` names what an action is *governed by*; the two edge-roles are never interchangeable even though both link an actor-adjacent node to guidance content. Canonical source: `RELATION_DESCRIPTIONS[Relation.APPLIES]` in `src/charter/offering/drg/models.py`.

{#workflow-id}

## Workflow ID

The stable, kebab-case identifier of a Workflow Sequence (e.g. `software-dev-default`). Used as the lookup key in the workflow schema registry and in the `meta.json` `workflow_id` field to record which sequence drove a given mission run. Org packs must not reuse shipped workflow IDs without explicit override semantics.

{#workflow-sequence}

## Workflow Sequence

A named, ordered list of mission-action steps with declared entry conditions, exit conditions, and optional per-step runtime hooks. Stored as a `WorkflowSequence` Pydantic model in `spec-kitty next`'s internal runtime schema registry. Org packs may contribute custom workflow sequences that extend or override the shipped set when activated via the Activation Registry.
