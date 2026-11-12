---
okf_version: "0.2"
title: "Charter"
description: "Glossary terms for the Charter context."
kind: Glossary
glossary_context: "Charter"
doc_status: active
generated:
  by: "doc-kitty-glossary/0.7.0"
  at: "2026-11-12T09:07:53Z"
  content_hash: "sha256:f84722b84d87"
---

# Charter

The governing term `charter` and the charter domain model: Charter Packs, artifact kinds, activation, selection and the CRG relations.

{#all-declaration-convention}

## `__all__` Declaration Convention

The project convention that every Python module with a public API must declare an `__all__` list enumerating its exported symbols. The `test_no_dead_symbols.py` architectural gate uses `__all__` as the canonical public surface; symbols absent from `__all__` are not counted as dead even if unreferenced, and symbols present in `__all__` but never imported externally are flagged as candidates for removal.

{#activated-kind}

## `activated_<kind>`

The `.kittify/config.yaml`-resolved, three-state per-kind activation authority for a charter artifact kind (`activated_paradigms`, `activated_directives`, `activated_tactics`, `activated_styleguides`, `activated_toolguides`, `activated_procedures`, `activated_agent_profiles`, `activated_mission_step_contracts`, `activated_glossary_packs`). Resolved via `PackContext.from_config`'s INV-2 two-file pointer chase: read `.kittify/config.yaml` directly, unless it carries a string `charter:` pointer, in which case follow it to the pointed `.kittify/charter/charter.yaml` and read the keys there instead. Three states per kind, applied independently: an ABSENT key resolves to `None` ("all built-ins available"); an explicit empty list (`[]`) is an explicit opt-out (nothing of that kind activated); a non-empty list is the explicit activated set. This is the field the compiler (`compile_charter`) and — after mission `spdd-reasons-activation-split-brain-01M1K6VN` — `is_spdd_reasons_active`, `_load_action_doctrine_bundle`, and `resolve_project_governance` all resolve activation from: the actual mechanism behind Charter-Mediated Selection, distinct from the charter-authored selected_&lt;kind&gt; / required_&lt;kind&gt; declaration surface.

{#activation-context}

## Activation Context

What the client is doing right now, sent with a resolution request: for example project <id>, a mission run of type <type>, step <X>, working on a Python implementation. The service resolves the project's one active charter against it and returns the artifacts that apply to that step (directives, profile, prompts and so on). It may also carry a temporary addition for this run. Core's narrower form is a two-field key (`mission_type`, `action`, with `generic` as wildcard) that scopes an Activation Registry entry.

**Meta:**

- **status:** `canonical`

{#activation-preset}

## Activation preset

A named selection that a Charter Pack ships (`presets/<id>.yaml`, its `id` equal to the file stem) and a project applies to its active charter. A preset has two jobs: as a governor it states what must be on the plate (its must-have items are the requirement), and as a convenience it offers an author's sane defaults to a project that does not want to customize. For the convenience job, applying a preset is a one-time write to the active charter; a governing preset keeps holding. Its activation lists hold preset items, and a preset with at least one must-have item is a governing preset (ADR-0031, ADR-0042). The built-in pack ships `default` and `minimal`.

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

{#anti-pattern}

## Anti-pattern

A practice to avoid, authored as its own file with a severity of `smell` (worth a remark), `severe` (should block review) or `breaking` (must block), and referenced from guidance through `rejects`. Decided in ADR-0052; until it is built an anti-pattern is only a graph node or an inline entry.

**Meta:**

- **status:** `canonical`

{#cat-7-grandfathered-orphan}

## Cat-7 Grandfathered Orphan

A dead-module or dead-symbol violation that has been explicitly classified as category 7 ("deferred — grandfathered") in the remediation tracking spreadsheet. Cat-7 items are excluded from the active failure count tracked by the ratchet baseline but must not be added to the allowlist via side-effect imports (the C2 anti-pattern). Each cat-7 record must carry a deferral reason and a target WP for cleanup.

{#catalog-miss}

## Catalog Miss

A charter artifact ID referenced by a charter selection (e.g. `selected_directives: [foo]`) that does not resolve to any known artifact in the shipped pack, any configured org pack, or the project-layer charter tree. Catalog misses are reported as errors by `spec-kitty doctor charter-packs` and by the `test_no_dead_symbols.py` gate when the referencing code reaches into the charter catalog.

{#charter}

## charter

The governance document synthesizing a project's purpose, constraints, team norms, and the body of project-specific governance artifacts (directives, tactics, and styleguides) into a durable reference artifact, produced by the charter interview workflow. This is the canonical governing term (glossary authority 1/2/3: `.kittify/glossaries/spec_kitty_core.yaml`, the built-in glossary pack, this entry).

{#charter-bundle}

## Charter Bundle

The materialized `.kittify/charter/` tree as a unit: `charter.md`, the synthesis manifest, the CRG cache and provenance. Produced by `spec-kitty charter generate` and `spec-kitty charter synthesize`; validated by `spec-kitty charter bundle validate`.

{#charter-facade}

## Charter Facade

A `src/charter/<facade>.py` module that re-exports (or thinly wraps) a charter surface so runtime callers can consume it as `from charter.<facade> import X` instead of `from charter.offering.<x> import X`. Examples: `charter.profiles`, `charter.mission_steps`, `charter.drg`, `charter.primitives`, `charter.resolution`, `charter.versioning`. The set of facades is the runtime → charter → offering boundary's public surface.

{#charter-offering}

## Charter offering

Everything a project could adopt: the buffet. It is made of Charter Packs, mostly a foundation to build on rather than a set of rules, where a derived pack builds on a broader one (org on built-in) and customizes it. The offer side of the charter; the choice side is the Active charter. The service may hold several versions of a pack side by side.

**Meta:**

- **status:** `canonical`

{#charter-pack}

## Charter Pack

A distributable bundle of charter components (artifacts and their CRG edges) together with optional activation presets shipped as `presets/<name>.yaml`. An org pack may also enforce activations through a governing preset, whose must-have items are the required floor of its projects, and lock artifacts through `policy.yaml`; `org-charter.yaml` and its `required_<kind>` lists are retired in the service (#431). A pack has two identities. Its configured pack id is the `charter_pack_id` it is configured under (`built-in`, an org pack's configured name, or `project`); org packs are configured under `charter_packs.org.packs` in `.kittify/config.yaml`. Its descriptor declares a pack id (a ULID) and a pack name (an identifier that qualifies cross-pack references). Pack ids and pack names are unique without exception. Every pack now declares its level in `pack.yaml` via the `pack_level` field — one of `built-in`, `org` or `project` (ADR 2026-10-11-2). The level names have one authority, `kernel.pack_tiers`; the tier token and the provenance/layer value now share a single spelling, `built-in` (the former no-hyphen `builtin` provenance spelling is folded away). Loading refuses a pack whose declared level disagrees with where it is configured (`PACK_LEVEL_MISMATCH`), and a `pack.yaml` missing the field (`PACK_LEVEL_MISSING`); for one release an org pack without a `pack.yaml` loads with a deprecation warning before that refusal takes effect. Validated with `spec-kitty charter pack validate`, listed with `spec-kitty charter pack list`. ADR-0048 composes packs through `depends_on`, and this part is built: the resolver orders the compose and the reads refuse a broken one. Still decided and not yet built: the named-packs split that makes `built-in` a distribution pack over the foundation, specialist, topical and stack packs.

{#charter-mediated-selection}

## Charter-Mediated Selection

Architectural pattern in which the project / org charter is the sole authority that decides which charter artifacts apply to a given mission run. The offering is the knowledge store; charter is the selector; runtime asks charter for the activated set rather than reaching into the offering directly. Enforced via the runtime → charter → offering boundary. This framing is correct at the architectural level; the MECHANISM by which the charter's activation decision is actually resolved today is activated_&lt;kind&gt; — not `selected_<kind>` alone. `selected_<kind>` is the authoring record of what an interview/pack once selected; `activated_<kind>` is what `PackContext.from_config` resolves via the INV-2 two-file pointer chase.

{#charterscope}

## CharterScope

A resolution context (`charter.scope::CharterScope`) that maps a filesystem path to the appropriate charter when multiple charters coexist in a monorepo. Single-project repositories use `CharterScope.default(repo_root)` which returns the root-scoped charter. Monorepos with multiple sub-project charters configure `charter_scopes:` in `.kittify/config.yaml`; `CharterScope.resolve(repo_root, feature_dir)` then finds the nearest-enclosing charter for any given feature directory. The resolved `scope.root` is forwarded to `build_charter_context` (or via `build_with_scope`) so the correct per-package charter governs prompt generation. See ADR-8 (`docs/adr/3.x/2026-05-18-1-monorepo-charter-scope.md`).

{#closure}

## Closure

The set of packs reachable from one pack through `depends_on`, each pack id once. A qualified reference must name a pack in the closure of the pack that writes it, not merely in the compose; otherwise it is refused, naming the missing pack (ADR-0048, ADR-0053 decision 7).

**Meta:**

- **status:** `canonical`

{#compose}

## Compose

The closure rooted at one level's configured packs, including dependencies at levels nearer built-in. Today every configured pack is a root; roots from the activation set come with #360. It is ordered dependencies first, each level contiguous in merge order. A pack id and a pack name are each unique across it without exception, and an artifact id is unique within a level except where an `overrides` edge settles it (ADR-0048 decisions 8 and 10, ADR-0053 decision 6).

**Meta:**

- **status:** `canonical`

{#composed-read}

## Composed read

A read that resolves the compose before it answers: `GET /offering` and `GET /packs/{packId}/artifacts/{kind}/{artifactId}/references`. A broken compose refuses it whole with the composition code, and any unreadable file of a configured pack refuses it as `pack_unreadable`, because it names every configured pack. Compare Pack-scoped read.

**Meta:**

- **status:** `canonical`

{#dependency-edge}

## Dependency Edge

One `depends_on` entry a pack declares: the depended-on pack's `pack_id` and a PEP 440 version range the held version must satisfy. The item is closed, and a pack that declares one must also declare `pack_level` (ADR-0048 decision 1, ADR-0053 decision 4).

**Meta:**

- **status:** `canonical`

{#activated-pack}

## Activated pack

A pack whose artifacts the active charter puts in force. The effective reads answer what an activation puts in force (issues #125, #103), and `listActivationPacks` answers its closure (#360), the roots of a compose. Compare Configured pack and Available pack.

**Meta:**

- **status:** `canonical`

{#available-pack}

## Available pack

A pack the catalog offers and the service may hold, whether or not a project configures or activates it. Compare Configured pack and Activated pack.

**Meta:**

- **status:** `canonical`

{#configured-pack}

## Configured pack

A pack named in the configuration, built-in, an org pack under `charter_packs.org.packs`, or the project layer. Every configured pack is a root of the compose today. Compare Available pack and Activated pack.

**Meta:**

- **status:** `canonical`

{#configured-pack-id}

## Configured pack id

The identifier a pack is configured under: `built-in`, an org pack's configured name, or `project` (the `charter_pack_id`). It is `OfferingPack.packId` on the wire. Not the descriptor's pack id, which is a ULID and is `OfferingPack.descriptorPackId`.

**Meta:**

- **status:** `canonical`

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

{#delegation-delegates-to-crg-relation}

## Delegation — `delegates_to` (CRG Relation)

A directional Three-layer CRG relation expressing a *runtime* handoff: one agent profile hands work to another at execution time. It is kept deliberately separate from Lineage so a static "derives from" relationship never gets conflated with a live work handoff. No built-in artifact emits a `delegates_to` edge today — delegation is currently expressed in profile `collaboration.handoff_to` prose rather than as a graph edge, so treat this relation as intended-but-dormant. Canonical source: `RELATION_DESCRIPTIONS[Relation.DELEGATES_TO]` in `src/charter/offering/drg/models.py`.

{#derived-pack}

## Derived pack

A Charter Pack that builds on a broader one (an org pack on built-in) and customizes it for its context. It may add artifacts, change existing ones within their integrity level, suggest a selection through presets, remove artifacts, lock or remove roles, pin the profile for a role and declare a fallback profile.

**Meta:**

- **status:** `canonical`

{#directive}

## Directive

A constraint-oriented governance rule that applies across flows or phases. Directives encode required or advisory expectations and can reference lower-level tactics for execution.

{#distribution-pack}

## Distribution pack

A pack that holds no artifacts of its own and aggregates the packs it depends on. `built-in` becomes one, over a slimmed foundation pack (ADR-0048).

**Meta:**

- **status:** `canonical`

{#charter-domain}

## Charter Domain

The domain model that structures reusable governance knowledge in Spec Kitty. It organizes behavior and constraints into composable artifacts (paradigms, directives, tactics, templates, styleguides, and toolguides).

{#event-registry}

## Event registry

A pack artifact that lists event types, each with a reverse-DNS namespace, a type and a name, so the charter can check that an `emit` names a declared event. It holds no schemas, envelopes or payloads, and the charter service still publishes no events (ADR-0051).

**Meta:**

- **status:** `canonical`

{#example-mapping-workshop-procedure}

## Example Mapping Workshop (Procedure)

Procedure `example-mapping-workshop`. Turns a behavior request into concrete rules, canonical examples, and open questions that stakeholders and implementers share as the current source of truth for the behavior.

{#foundation-pack}

## Foundation pack

The slimmed base pack the other packs build on: the cross-cutting directives, paradigms, glossary, base templates and assets, the shipped mission types with their step contracts, and the base agent profiles. `built-in` aggregates it (ADR-0048).

**Meta:**

- **status:** `canonical`

{#gate}

## Gate

Superseded by Guard (ADR-0054, 2026-11-02). Was proposed as a declared kind with a severity and a config schema (ADR-0050 decision 14); the operator ruled instead that guards are enforced checks implemented by executable assets.

**Meta:**

- **status:** `deprecated`

{#global-selection}

## Global Selection

Selection mode in which an artifact is in force for every step of every mission, whatever the action or mission type: it is on the project's active charter. What must be selected is stated by a requirement, authored as a preset; what a mission type adds while it runs is its recipe ingredients. Core expresses this today with `selected_<kind>` / `required_<kind>` lists (deprecated here), unioned onto the activated_&lt;kind&gt; base.

**Meta:**

- **status:** `canonical`

{#glossary-context}

## Glossary context

A ubiquitous-language bounded context authored as a pack artifact of kind `glossary`, which replaces `glossary_pack`. It holds terms, which are addressed as `term:<context>/<term>` (ADR-0049).

**Meta:**

- **status:** `canonical`

{#glossary-vocabulary-vocabulary-crg-relation}

## Glossary Vocabulary — `vocabulary` (CRG Relation)

A directional Three-layer CRG relation from a resolved charter artifact to a glossary-scope node, meant to surface which glossary sections are relevant once governance context has been resolved for an action. The traversal step exists and is exercised by tests, but no built-in or org-pack artifact currently emits a `vocabulary` edge — treat it as intended-but-dormant rather than actively exercised. Canonical source: `RELATION_DESCRIPTIONS[Relation.VOCABULARY]` in `src/charter/offering/drg/models.py`. Decided, not built yet: ADR-0049 retargets it at `glossary` and `term` instead of `glossary_pack`.

{#governance-scope-scope-crg-relation}

## Governance Scope — `scope` (CRG Relation)

A directional Three-layer CRG relation from a mission-step action node to the directives and tactics that govern performing that action. It is the entry point of governance-context resolution: the resolver first walks `scope` edges from the action node, then expands through Hard Dependency and Soft Recommendation edges from what it found. It is one of the most heavily emitted relations tied to action nodes in the built-in graph. Distinct from Workflow Application — see that entry for the contrast. Canonical source: `RELATION_DESCRIPTIONS[Relation.SCOPE]` in `src/charter/offering/drg/models.py`.

{#guard}

## Guard

An enforced check that must pass before a mission-type or lifecycle transition is taken. A guard is an artifact (`guards/<id>.guard.yaml`) `implemented_by` an executable asset or a closed built-in, run by the client to the guard/1 interface (context on stdin; exit 0 pass, 1 fail, 2 could not evaluate) and failing closed. A transition names it as `guard: [{ref, config?, blocking?}]`. The charter declares it and the client runs it (ADR-0050, ADR-0054). A `builtin:` guard runs in the client's own process and returns the same verdict shape; only an asset-backed guard speaks guard/1 (ADR-0054 decision 6).

**Meta:**

- **status:** `canonical`

{#guideline}

## Guideline

A high-level charter rule expressing non-negotiable or strongly preferred governance behavior. Guidelines sit at the highest precedence and bound what project-level charter may customize.

{#hard-dependency-requires-crg-relation}

## Hard Dependency — `requires` (CRG Relation)

A directional Three-layer CRG relation marking a mandatory prerequisite: the source artifact cannot be meaningfully resolved without the target also being present and considered. Governance-context resolution for a mission-step action walks `requires` edges transitively (no hop limit) once it has collected the action's scoped artifacts, and the charter activation cascade follows the same edge to pull in artifacts that must also be active. It is the most heavily used relation in the built-in graph. Canonical source: `RELATION_DESCRIPTIONS[Relation.REQUIRES]` in `src/charter/offering/drg/models.py`.

{#holding-pack}

## Holding pack

The pack of the compose that holds the artifact a reference names: the referring pack itself for a bare reference it can resolve there, the pack the qualifier names for a qualified one. It is answered as `HoldingPack` (`packId`, `descriptorPackId`, `name`) on a resolved reference and is absent when no pack holds the target.

**Meta:**

- **status:** `canonical`

{#import-candidate}

## Import Candidate

A pull-based curation record for an external governance idea. Captures source provenance, target classification, adaptation notes, and adoption status before canonization.

{#integrity-level}

## Integrity level

How much an artifact may be altered after it is introduced: `open`, `extendable` or `locked`. Set by the artifact's author; anyone below may tighten it, never loosen it. Independent of requirement (required-but-extendable is valid). A derived pack may remove an `open` or `extendable` artifact; a `locked` one is fully protected.

**Meta:**

- **status:** `canonical`

{#lifecycle}

## Lifecycle

A reusable state machine authored as its own pack artifact, such as the work-package lanes. A step of a mission type can reference one, and it then runs once per work package (ADR-0050).

**Meta:**

- **status:** `canonical`

{#lineage-specializes-from-crg-relation}

## Lineage — `specializes_from` (CRG Relation)

A directional, static Three-layer CRG relation: a profile or artifact derives from a parent, narrowing or extending it. In the built-in graph this appears only between `agent_profile` nodes (e.g. a language-specialist implementer profile specializing from the generic implementer profile), and is resolved through `AgentProfileRepository.resolve_profile` graph traversal at composition time. Deliberately distinct from Delegation so inheritance never leaks into runtime handoff traversal. Canonical source: `RELATION_DESCRIPTIONS[Relation.SPECIALIZES_FROM]` in `src/charter/offering/drg/models.py`.

{#living-documentation-sync-directive}

## Living Documentation Sync (Directive)

Directive DIRECTIVE_037. Requires behavior-describing artifacts to evolve together: when observable behavior changes, the canonical examples, acceptance checks, narrative specs, user docs, glossary entries, code-level docs, and architecture records are updated in the same change.

{#mission-step}

## Mission step

One step of a mission type's recipe. A step names the role it needs, never a profile. It is a sub-item of its mission type's file, in `steps` (the run order) or `on_demand_steps`, with a one-line description of at most 160 characters, its expected input artifacts (preconditions) and output artifacts (postconditions), its action-scoped guidance as graph edges from its `action:<mission-type>/<step>` node (ADR-0064) and, as Markdown companions, its prompt and guidelines. A domain type, not an artifact kind: there is no `mission_steps` kind. The graph names it as an action, `action:<mission-type>/<step>` (ADR-0044).

**Meta:**

- **status:** `canonical`

{#mission-type}

## Mission type

The recipe of the work: shipped in a pack and activated like any other artifact; once it is on the active charter, missions of that type can be run. Its steps say how to make the dish and ask for roles, never profiles. Its recipe ingredients are the guidance it names by id, added to the plate while a mission of that type runs. Modelled as its own concept, with its own domain model; it has no HTTP operations of its own yet (#194 out of scope). Stored as one file per type, `missions/<id>/<id>.mission-type.yaml`, its steps as sub-items; an artifact kind in the service, `mission_type`, where core keeps it a mission-tier concept outside its artifact kinds (ADR-0044). Decided, not built yet: ADR-0050 declares it as a state machine with transitions and guards, and ADR-0048 renames its `missions/` directory to `mission_types/`.

**Meta:**

- **status:** `canonical`

{#mission-type-profile}

## Mission-Type Profile

Superseded by the recipe's ingredients of a Mission type. Today's shipped `governance-profile.yaml` per mission type, read by core as "while a mission of this type runs, these artifacts are in scope". In the new model a mission type names the guidance it calls for by id, activating the mission type is the consent, and it names no profiles (steps ask for roles; `selected_agent_profiles` is dropped). Former definition, kept for history: Shipped governance profile per mission type (`software-dev`, `documentation`, `research`, `plan`) at `packs/built-in/missions/<type>/governance-profile.yaml`. Declares default selections and default activations for that mission type. The charter resolver reads `meta.json mission_type`, picks the matching profile, and unions its declarations into the project + org selections. No `software-dev-default` fallback for non-software missions — the resolver hard-fails if a mission's `mission_type` has no matching profile and the project has not declared its own.

**Meta:**

- **status:** `deprecated`

{#on-demand-step}

## On-demand step

A mission step invoked by an explicit command, outside its mission type's run order, listed in the type's `on_demand_steps`; for example software-dev's `charter` and `tasks-outline`. Its authored order is kept, but it is not a run order (ADR-0044).

**Meta:**

- **status:** `canonical`

{#organization-tier}

## Organization Tier

The middle layer of the three-layer CRG model, contributed by one or more org Charter Packs configured under `charter_packs.org.packs` in `.kittify/config.yaml`. Each pack ships an optional `drg/fragment.yaml` (CRG extension nodes and edges), and may require artifacts of its projects through a governing preset's must-have items and lock them through `policy.yaml`. In core, an org pack also ships an `org-charter.yaml` (governance policies and required artifact selections), propagated to consumer projects via `apply_org_charter_to_interview`; the service retired that file (#431).

{#overlay-enhancement-enhances-crg-relation}

## Overlay Enhancement — `enhances` (CRG Relation)

A directional org-pack overlay relation: a pack artifact field-merges additional content into a built-in artifact, preserving the built-in's existing action sequence and step I/O rather than discarding them. No built-in artifact emits this edge — by design, `enhances` only ever originates from an org- or project-tier pack fragment layered on top of a shipped artifact, never between two built-in nodes. Distinct from Overlay Override, which replaces rather than merges. Canonical source: `RELATION_DESCRIPTIONS[Relation.ENHANCES]` in `src/charter/offering/drg/models.py`.

{#overlay-override-overrides-crg-relation}

## Overlay Override — `overrides` (CRG Relation)

A directional org-pack overlay relation: a pack artifact declares a full replacement of a built-in artifact's content rather than a field-merge. Like Overlay Enhancement, no built-in artifact emits this edge by design — it only ever originates from an org- or project-tier overlay. Silently dropping steps or stripping step input/output when applying an `overrides` edge is rejected rather than tolerated. Canonical source: `RELATION_DESCRIPTIONS[Relation.OVERRIDES]` in `src/charter/offering/drg/models.py`.

{#pack-scoped-read}

## Pack-scoped read

A read that judges only the pack and the artifact its path names, such as `GET /packs/{packId}/artifacts/{kind}/{artifactId}`. It does not resolve the compose, so it still answers while the compose is broken or while another file of the pack is unreadable. Compare Composed read.

**Meta:**

- **status:** `canonical`

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

A playbook: a bounded, step-by-step way to handle one situation, with entry and exit conditions. It is used inside a step; nothing runs or tracks it, and it has no state, deliverables, gates or role slots, so it sits below a mission type. A tactic is one technique; a procedure is an episode that combines techniques. A rule-shaped procedure is a directive. The `operator` role marks where a procedure asks the human for input or action. Decided, not built yet: ADR-0052 tightens this definition and moves its inline anti-patterns to authored anti-pattern files.

**Meta:**

- **status:** `canonical`

{#held-step}

## Held step

A step `action:M/s` that a pack of the closure holds: some pack defines `mission_type:M` and declares the action, and a layer override of the step counts. One predicate decides which steps the write cascade follows, which ids `action_not_found` refuses and where the effective action context starts (rules W20 and E16, DIV-243). A step is a node of the effective graph, never an artifact in force.

**Meta:**

- **status:** `canonical`

{#nodereference}

## NodeReference

A node of the graph named by `{kind, id}` where it may be a step (kind `action`, id `<missionType>/<step>`) and not an activatable artifact. The enhancer targets of the skipped report, the effective graph's nodes and the context's `enhancements` carry it. See Held step.

**Meta:**

- **status:** `canonical`

{#layer-override}

## Layer override

The record that one version of an artifact, from a pack at a higher level (built-in, org, project), won over another version of the same artifact from a lower one, so the merged closure reads the winner whole. The effective graph lists one record per losing version of a node of the graph in `layerOverrides`. Not the Overrides edge, which settles a sibling conflict, nor Overlay Override, a CRG relation.

**Meta:**

- **status:** `canonical`

{#effective-graph}

## Effective graph

The graph of an activation (`getEffectiveGraph`): the nodes in force (the artifacts in force and the held steps of the mission types in force), the merged edges among them resolved through the layer overrides, and the layer overrides applied. Not the offering's graph filtered by the activation.

**Meta:**

- **status:** `canonical`

{#effective-action-context}

## Effective action context

What applies to one action under an activation (`getEffectiveActionContext`): what ADR-0018's walk reaches from a held step over the artifacts in force, and, by the ownership rule, the directives, tactics and paradigms in force that the action's mission type scopes and the project-wide artifacts, each with what it requires and suggests; each item says which in `appliesThrough` (`action`, `mission_type` or `project`). It carries the suggestions, the `enhancements` of the nodes it names and the skipped report, and links the effective step prompt of the same action. Compare Effective graph.

**Meta:**

- **status:** `canonical`

{#effective-step-prompt}

## Effective step prompt

The prompt one step of a mission type gets under an activation (`getEffectiveStepPrompt`): the prompt in force of the step, as it is stored, and, for every slot it declares, the fragments of the step enhancements in force that fill it, ordered by pack level, then pack name, then enhancement id, each with its pack and enhancement (ADR-0066 decision 9). The charter serves it and does not render it. A mission type the activation does not put in force has none (`409 mission_type_not_in_force`). Not the rendered prompt.

**Meta:**

- **status:** `canonical`

{#project-wide-artifact}

## Project-wide artifact

A directive, tactic or paradigm in force that no action and no mission type scopes: it has no scope owner. The effective action context lists it, with what it requires and suggests, on every action of a mission type in force, with `appliesThrough: project` (the ownership rule, ADR-0018 D5 as amended 2026-11-12). Compare Scope owner.

**Meta:**

- **status:** `canonical`

{#scope-owner}

## Scope owner

A node with an authored `scope` edge to an artifact: an action or a mission type, held or not. Under the ownership rule an artifact in force applies to an action that owns it, to every action of a mission type that owns it, and, when it has no owner at all, to every action (a project-wide artifact). Ownership is judged on the authored edges of the merged closure, whatever is in force.

**Meta:**

- **status:** `canonical`

{#overrides-edge}

## Overrides edge

An `overrides: [{artifact, member}]` entry on a pack descriptor that settles a sibling conflict on `artifact` by naming `member`, the descriptor pack id of a contesting pack (ADR-0053 decision 5). Until #358 any pack of the compose may declare it. Settling puts the named member's version in force (#357).

**Meta:**

- **status:** `canonical`

{#pack-id-descriptor}

## Pack id (descriptor)

The ULID a pack declares as `pack_id` in `pack.yaml`; `parent_pack`, `depends_on[].pack_id` and `overrides[].member` take the same shape. It is `OfferingPack.descriptorPackId` and `dependsOn[].descriptorPackId` on the wire, and the identity a dependency edge names. Unique across a compose without exception (`pack_duplicate_id`). Not the configured pack id.

**Meta:**

- **status:** `canonical`

{#pack-name}

## Pack name

The `name` in `pack.yaml`, matching `^[a-z0-9][a-z0-9_-]*$`. It is the `<pack-name>` qualifier of a cross-pack reference, not an identity: unique across a compose without exception (`pack_name_conflict`), and distinct from both the configured pack id and the descriptor pack id.

**Meta:**

- **status:** `canonical`

{#pep-440-range}

## PEP 440 range

The grammar of `depends_on[].range`: a PEP 440 version-specifier set such as `>=1.2,<2` or `~=1.4`, the grammar `requires_core` already uses (ADR-0053 decision 4). A malformed range, or two ranges on one pack that share no version, is refused as `pack_dependency_out_of_range`.

**Meta:**

- **status:** `canonical`

{#project-layer}

## Project layer

The project's own charter components, under the one flat root `.kittify/charter-packs/` (`charter_pack_id` `project`). It is part of the Charter offering, ships no presets, and is listed by `spec-kitty charter pack list` as the `project` row. `spec-kitty charter synthesize` writes here.

{#qualified-reference}

## Qualified reference

A cross-pack reference, an edge end in a graph document, written `<pack-name>::<urn>`; the URN part stays opaque (ADR-0053 decision 7). A reference inside the same pack stays bare. The pack is provenance on the relationship, not identity: the artifact's URN stays bare. A bare reference is the referring pack's own artifact first. A bare reference that would only resolve in another pack is refused, naming the holding pack (`pack_reference_unqualified`); one that names a pack outside the referring pack's own `depends_on` closure is refused, naming the missing pack (`pack_reference_pack_missing`); one whose pack does not hold the artifact is refused too (`pack_reference_artifact_missing`).

**Aliases:** pack-qualified reference

**Meta:**

- **status:** `canonical`

{#ratchet-baseline}

## Ratchet Baseline

A snapshot of a quality metric (failure count, symbol count, dead-module count) recorded in `tests/architectural/ratchet-baseline-*.md` and enforced by the corresponding architectural gate. A ratchet baseline only moves in the decreasing direction during normal development; any increase fails CI. Org packs may declare additional ratchet metrics via governance policies, but cannot lower an existing shipped baseline.

{#recipe-ingredients}

## Recipe ingredients

The guidance a mission type names: the targets of its `scope` edges in the pack's graph documents (core reads them from `governance-profile.yaml`, which the service retires). The mission-type file has no `references` field; a relationship is authored only in the graph documents (ADR-0064). While a mission of that type runs, these artifacts are added to the plate; activating the mission type is the consent. The ingredients name no profiles: roles stay the only link to profiles. Replaces Mission-Type Profile.

**Meta:**

- **status:** `canonical`

{#reconciliation-reconciles-tension-crg-relation}

## Reconciliation — `reconciles_tension` (CRG Relation)

A directional Three-layer CRG relation linking an active reconciliation artifact to one side of a declared Tension pair. A tension pair is treated as resolved only when an active artifact carries this edge to both sides of the pair — an edge to just one side leaves the pair half-reconciled and still flagged. It is authored explicitly and is never inferred from an `in_tension_with` edge. Canonical source: `RELATION_DESCRIPTIONS[Relation.RECONCILES_TENSION]` in `src/charter/offering/drg/models.py`; human-readable mirror in `docs/architecture/charter-relationships.md` ("Tension vocabulary").

{#refinement-refines-crg-relation}

## Refinement — `refines` (CRG Relation)

A directional Three-layer CRG relation: an artifact narrows or sharpens the applicability or meaning of a parent or built-in target without replacing it. It is a first-class, traversable relation in its own right — never a stand-in for Workflow Application or Lineage. No built-in artifact currently emits a `refines` edge, so treat it as intended-but-dormant; an earlier version of the org-to-CRG bridge silently downgraded authored `refines` edges to `applies`, but that lossy downgrade has since been removed. Canonical source: `RELATION_DESCRIPTIONS[Relation.REFINES]` in `src/charter/offering/drg/models.py`.

{#rejection-rejects-crg-relation}

## Rejection — `rejects` (CRG Relation)

A directional Three-layer CRG relation from a good artifact to a marked anti-pattern or smell node (`NodeKind.ANTI_PATTERN`), expressing rejection of a named bad practice. It is distinct from Tension — the target is not a competing equal, it is a bad practice — and from `replaces`/supersession, since the target was never a valid rule to begin with. Canonical source: `RELATION_DESCRIPTIONS[Relation.REJECTS]` in `src/charter/offering/drg/models.py`; human-readable mirror in `docs/architecture/charter-relationships.md` ("Tension vocabulary"). Decided, not built yet: ADR-0052 adds a `rejects` list on guidance whose targets are authored anti-patterns.

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

{#schema-charter-artifact}

## Schema (Charter Artifact)

A machine-validated contract that defines allowed structure and fields for charter artifacts. Used in CI/tests to fail fast when invalid charter files are introduced.

{#selected-kind-required-kind}

## `selected_<kind>` / `required_<kind>`

Superseded. In the new model a mission type's guidance is named by id as its recipe ingredients, and a requirement is authored as a preset whose must-have items are the required set; the service derives the required list from the preset (ADR-0031). The old `required_<kind>` lists are not read: `org-charter.yaml`, which held them, is retired and refused by name (#431). Former definition, kept for history: The field-naming convention for Global Selection entries. On the project charter (`GovernanceCharterConfig`), each artifact kind gets a `selected_<kind>: [<id>, ...]` field — `selected_directives`, `selected_styleguides`, `selected_toolguides`, `selected_paradigms`, `selected_tactics`, `selected_procedures`, `selected_agent_profiles`, `selected_mission_step_contracts`. On the org charter (`OrgCharterPolicy`), the mirror is `required_<kind>: [<id>, ...]`. `apply_org_charter_to_interview` unions the org `required_<kind>` into the project `selected_<kind>` non-destructively. The architectural test `test_artifact_selection_completeness.py` enforces parity — every `ActiveCharterService` artifact kind has both a `selected_*` and a `required_*` field. These fields are the CHARTER-AUTHORED record of a selection, not an independent activation source: they are unioned as an additive project-local layer over the resolved activated_&lt;kind&gt; base (`PackContext.from_config`) at the surfaces that resolve activation today (e.g. `_resolve_directive_base` in `resolver.py`).

**Meta:**

- **status:** `deprecated`

{#soft-recommendation-suggests-crg-relation}

## Soft Recommendation — `suggests` (CRG Relation)

A directional Three-layer CRG relation pointing at content that is relevant but optional. Unlike Hard Dependency, which is walked transitively with no depth limit, governance-context resolution only follows `suggests` edges a bounded number of hops, and the charter activation cascade treats a `suggests` target as something an operator may accept or decline. It is emitted more often than any other relation in the built-in graph, but that volume is incidental — the depth-bounded walk, not the count, is what separates it from `requires`. Canonical source: `RELATION_DESCRIPTIONS[Relation.SUGGESTS]` in `src/charter/offering/drg/models.py`.

{#specialist-pack}

## Specialist pack

A pack that builds on the packs below it and only adds artifacts and graph edges. Two sibling packs that change the same artifact are refused unless the distribution pack settles it (ADR-0048).

**Meta:**

- **status:** `canonical`

{#stack-pack}

## Stack pack

An opt-in pack for one technology stack, such as `java`, `python` or `drupal`; it is not a member of `built-in` (ADR-0048 decision 14).

**Meta:**

- **status:** `canonical`

{#specification-by-example-paradigm}

## Specification by Example (Paradigm)

A paradigm that builds shared understanding of behavior through concrete, business-readable examples which become the canonical source of truth for requirements, acceptance checks, and living documentation.

{#step-contract}

## Step contract

The structural sub-steps of one mission step and what each delegates to (the `mission_step_contract` kind). The step's role and expected artifacts live on the mission step, not on its contract (ADR-0044).

**Meta:**

- **status:** `canonical`

{#step-enhancement}

## Step enhancement

An add-only extension that a pack attaches to one step of a mission type. It contributes prompt fragments into named, empty slots of its target step's prompt; its target is its one `enhances` edge, to an `action:<type>/<step>`, and it carries no `overrides` edge (ADR-0066). Guard and emit fields are planned (#434). It cannot remove or reorder steps; `overrides` replaces a step whole (ADR-0050).

**Meta:**

- **status:** `canonical`

{#prompt-fragment}

## Prompt fragment

A Markdown file beside a step enhancement, `<name>.prompt-fragment.md`, named by one of its `prompt_fragments` entries. Its text is added into the slot that entry names, in the prompt of the enhancement's target step. It has no frontmatter, is not blank, and holds no slot marker or open comment (ADR-0066).

**Meta:**

- **status:** `canonical`

{#prompt-slot}

## Prompt slot

A named, empty place in a step's prompt where enhancements add fragments, written as a pair of HTML comments, `<!-- <slot id>:start -->` and `<!-- <slot id>:end -->`. Its slot id has the form `namespace:name`, for example `spdd:reasons-block` (ADR-0066).

**Meta:**

- **status:** `canonical`

{#step-guidelines}

## Step guidelines

A mission step's working notes for the agent: the Markdown companion `steps/<id>.guidelines.md`, named by `guidelines_path`, with no frontmatter. Not the charter Guideline, which is a governance rule (ADR-0044).

**Meta:**

- **status:** `canonical`

{#step-template}

## Step template

The prompt fed to the agent when a mission step runs: the step's Markdown companion `steps/<id>.prompt.md`, named by `prompt_path`. Its frontmatter holds only `description`, a detailed description longer than the step's one-line one. Not a template payload: a template is a file under `templates/` (ADR-0044).

**Meta:**

- **status:** `canonical`

{#styleguide}

## Styleguide

A charter artifact defining cross-cutting quality and consistency conventions (for example coding, documentation, or testing style) that apply across missions and templates.

{#supersession-replaces-crg-relation}

## Supersession — `replaces` (CRG Relation)

A directional Three-layer CRG relation asserting that the source artifact fully supersedes the target, which stops applying once the source is active. It is retained for backward compatibility with older, hand-authored fragments; no built-in artifact emits it today, since current practice either deactivates the superseded artifact directly or, for pack overlays, expresses supersession through Overlay Override. Distinct from Tension, which never implies either side is deprecated or wrong. Canonical source: `RELATION_DESCRIPTIONS[Relation.REPLACES]` in `src/charter/offering/drg/models.py`.

{#supports-supports-crg-relation}

## Supports — `supports` (CRG Relation)

A relationship from a resource to a rule it helps check, for example a companion asset that lets an agent verify its own compliance with a directive deterministically. Distinct from `suggests`, which points at optional relevant content.

**Meta:**

- **status:** `canonical`

{#symbol-level-dead-code}

## Symbol-level Dead Code

A public symbol (function, class, constant) exported by a module but not referenced by any other module or test in the codebase, as detected by `tests/architectural/test_no_dead_symbols.py`. Distinguished from module-level dead code (an entire module with no importers). Symbol-level findings are reported per-file and contribute to the dead-symbol ratchet baseline.

{#tactic}

## Tactic

A reusable behavioral execution pattern that defines how work is performed. Tactics are operational and agent-consumable, and can be selected by directives and mission context.

{#template-instantiation-instantiates-crg-relation}

## Template Instantiation — `instantiates` (CRG Relation)

A directional Three-layer CRG relation from a mission-step action node to the template it produces as its concrete output. It appears only between `action` and `template` nodes in the built-in graph, in modest numbers, and is distinct from Governance Scope: `scope` links an action to content it must follow, `instantiates` links it to content it produces. Canonical source: `RELATION_DESCRIPTIONS[Relation.INSTANTIATES]` in `src/charter/offering/drg/models.py`.

{#template-set}

## Template Set

A structured set of charter templates that shape output artifacts and interaction contracts for mission actions and procedures. Template sets allow consistent behavior across mission types while remaining configurable through charter selections.

{#sibling-conflict}

## Sibling conflict

Two packs at the same level that both change, or both define, the same artifact id, of any kind; a definer and an enhancer do not conflict. It is refused as `pack_sibling_conflict` unless an overrides edge naming a contesting member settles it (ADR-0048 decision 9, ADR-0053 decision 5). Across levels the same id is a legitimate layer override, not a conflict.

**Meta:**

- **status:** `canonical`

{#tension-in-tension-with-crg-relation}

## Tension — `in_tension_with` (CRG Relation)

Superseded by Tension — `tension` (CRG Relation) (ADR-0028). The former name of the authored relationship between two co-valid rules that compete on the same decision; core's CRG still uses it. In this repository the relationship is named `tension`, and `impacts` now names an artifact's per-value list (value impact), not a relation. Former definition, kept for history: A symmetric, non-transitive Three-layer CRG relation marking two co-valid, co-activatable artifacts that compete on the same decision. Stored as a single canonical edge (lexicographically-smaller URN as source) and queryable from either endpoint; it does not imply that either side is deprecated, superseded, or wrong — both remain valid rules until an operator deactivates one side or activates a reconciler (Reconciliation). Canonical source: `RELATION_DESCRIPTIONS[Relation.IN_TENSION_WITH]` in `src/charter/offering/drg/models.py`; human-readable mirror and worked examples in `docs/architecture/charter-relationships.md` ("Tension vocabulary").

**Meta:**

- **status:** `deprecated`

{#tension-tension-crg-relation}

## Tension — `tension` (CRG Relation)

An authored relationship between two rules that compete on the same decision; both stay valid. It is never derived from value impacts, and it may sit beside one structural relationship on the same pair (two rules can require each other and still be in tension). Replaces `in_tension_with` (ADR-0028).

**Aliases:** in_tension_with

**Meta:**

- **status:** `canonical`

{#term-glossary}

## Term (glossary)

One entry of a glossary context, held as an item inside it and not as an artifact of its own, with a name, a definition and optional aliases and `avoid` forms. Its URN is `term:<context-id>/<term-id>` (ADR-0049).

**Meta:**

- **status:** `canonical`

{#three-layer-crg}

## Three-layer CRG

The three-tier Charter Reference Graph composed of: (1) the shipped built-in layer (the per-kind `*.graph.yaml` fragments under `packs/built-in/`), (2) zero or more org-tier extension fragments (`drg/fragment.yaml` inside each configured org pack), and (3) optional project-tier annotations declared in the project charter. Each tier is additive; org and project tiers may only extend or annotate shipped nodes — they cannot remove or reclassify them. Resolved at runtime by `charter.drg.merge_three_layers`.

{#toolguide}

## Toolguide

A charter artifact defining tool-specific operational guidance, syntax, and constraints (for example PowerShell usage conventions) used by agents and contributors during execution.

{#topical-pack}

## Topical pack

A full top-level pack in spec-kitty-open-packs for one subject, for example `foundation`, `writing`, `java` or `drupal`; none is nested under `built-in/` (ADR-0048 decision 11).

**Meta:**

- **status:** `canonical`

{#trigger-registry}

## Trigger Registry

Canonical frozenset of agent-action tokens (e.g. `write_comment`, `write_docstring`, `rename_identifier`, `add_dependency`) the prompt builder knows how to emit "when you <token>, …" stanzas for. Any `triggers:` value declared on a shipped charter artifact must be a member of this set; the architectural test `test_trigger_registry_coverage.py` enforces no dead triggers. Mission B's WP05 populates the initial set.

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

{#workflow-application-applies-crg-relation}

## Workflow Application — `applies` (CRG Relation)

A directional Three-layer CRG relation from an agent profile to the concrete procedure or tactic it executes as its operating workflow. The built-in graph contains zero `applies` edges and authoring one into the shipped tree is refused by `tests/architectural/test_no_authored_applies_edge.py`: nothing traverses `applies` — not governance-context resolution, not the charter cascade, not the reference walk — so an authored edge names a relationship no reader follows. Use `requires` when the profile must actually reach the artifact. The relation itself is not retired: project-tier charter synthesis still emits it. Distinct from Governance Scope: `applies` names what a profile *does*, `scope` names what an action is *governed by*; the two edge-roles are never interchangeable even though both link an actor-adjacent node to guidance content. Canonical source: `RELATION_DESCRIPTIONS[Relation.APPLIES]` in `src/charter/offering/drg/models.py`.

{#workflow-id}

## Workflow ID

The stable, kebab-case identifier of a Workflow Sequence (e.g. `software-dev-default`). Used as the lookup key in the workflow schema registry and in the `meta.json` `workflow_id` field to record which sequence drove a given mission run. Org packs must not reuse shipped workflow IDs without explicit override semantics.

{#workflow-sequence}

## Workflow Sequence

A named, ordered list of mission-action steps with declared entry conditions, exit conditions, and optional per-step runtime hooks. Stored as a `WorkflowSequence` Pydantic model in `spec-kitty next`'s internal runtime schema registry. Org packs may contribute custom workflow sequences that extend or override the shipped set when activated via the Activation Registry.

{#asset}

## Asset

A pack resource described by a sidecar manifest (`*.asset.yaml`: `id`, `mime`, `path`, optional `title`) and carrying one asset payload, the file its `path` names relative to the pack's `assets/` directory. Not activatable. A sidecar whose payload does not exist is refused. Its `mime` is metadata and never decides how the payload is read (ADR-0040). Decided, not built yet: an optional `executable` block makes the asset able to implement a guard (ADR-0054 decision 5).

**Meta:**

- **status:** `canonical`

{#asset-payload}

## Asset payload

A file under a pack's `assets/` directory, held as its exact bytes and written back byte for byte: the file an asset names, or one that no sidecar names (such as a tool configuration shipped beside the assets). The service holds each one as an `AssetFile` of the pack, not as an artifact, so it needs no id. A `README.md` under `assets/` is not one (ADR-0040).

**Meta:**

- **status:** `canonical`

{#content-form}

## Content form

What a pack file-kind rule declares about how its files are read, held, written and compared: `yaml` (the default), `markdown`, `text` or `bytes` (`content_form` in `contracts/packs/file-kinds.yaml`). Never chosen by a file's extension or an asset's `mime`. Distinct from `YamlFormat`, the codec of one YAML kind, from `mime`, an asset's declared media type, and from `SerializationStrategy`, the store's YAML read and write. `yaml`, `markdown` and `bytes` are built; `text` is declared and not built (ADR-0040).

**Meta:**

- **status:** `canonical`

{#markdown-content-form}

## Markdown content form

The `markdown` content form: a file read into a model of optional frontmatter, an optional H1 title, a preamble and the H2 sections in order, each a title and its content. Two files mean the same when their models are equal; a file is written back in one canonical form, and ambiguous Markdown (an unclosed fence, a carriage return, an indented or underlined heading) is refused naming the line. A `files` rule or a companion that declares it names a structure schema that validates the model (ADR-0040, ADR-0039).

**Meta:**

- **status:** `canonical`

{#guide-toolguide}

## Guide (toolguide)

A toolguide's guide: the Markdown companion `<id>.guide.md` beside its toolguide, named by `guide_path`. It holds fixed blocks under canonical H2 headings (`Overview`, required, then `When to use`, `Setup`, `Usage`, `Cautions`, `Known limitations`, `References`) and freeform sections between `Usage` and `Cautions`. Its H1 is written from the toolguide's title, which, with the summary and commands, lives only in the toolguide. Over HTTP it travels as content or a fetched URI, never as a path (ADR-0043).

**Meta:**

- **status:** `canonical`

{#sanitized-shape}

## Sanitized shape

The deliberately designed storage shape of a pack object, as opposed to one mirrored from the files a pack happens to hold. It follows the sanitized-shape rules: S1 to S8 for every storage object (ADR-0042) and S9 to S16 for artifact kinds (ADR-0043), such as `title` for the label, `description` for the summary, `content` for a body and `role` for who acts. Pack content in another shape is fixed in the pack, never tolerated by the service.

**Meta:**

- **status:** `canonical`

{#template-pack}

## Template (pack)

A file under a pack's top-level `templates/` directory, read as an artifact whose local id is its path under that directory (`writing/meeting-minutes.md`, URN `template:writing/meeting-minutes.md`) and held as its exact bytes, frontmatter included. Not activatable. A mission template, a file under `missions/<type>/templates/`, is a template too, with the id `<type>/<path>` (ADR-0040, ADR-0044).

**Meta:**

- **status:** `canonical`

{#preset-item}

## Preset item

One entry of an activation preset's list: the `id` of an artifact or a mission type, as its own file declares it (never a file stem), optionally carrying the must-have mark. Written `{id: <id>}` or `{id: <id>, must_have: true}`. One list names an id once (ADR-0042).

**Meta:**

- **status:** `canonical`

{#must-have-mark}

## Must-have mark

The per-item flag `must_have: true` on a preset item. It makes the item part of the preset's required set, which the service derives from the marks and never reads from a second list. An item without the mark is not must-have; there is no `false` spelling. Not to be confused with the `required_<plural>` lists of core's retired `org-charter.yaml` (#431) or with JSON Schema's `required` (ADR-0031, ADR-0042).

**Meta:**

- **status:** `canonical`

{#governing-preset}

## Governing preset

An activation preset with at least one must-have item: it states what every project that takes it must have. A preset with none is a convenience preset, which only offers defaults. Derived from the items, never declared: a preset carries no flag of its own (ADR-0031, ADR-0042).

**Meta:**

- **status:** `canonical`

{#pack-manifest}

## Pack manifest

A pack's `pack-manifest.yaml`: the machine-written inventory of its constituent artifacts (`kind`, `id`, `path`, `content_hash`) and its presets (`id`, `path`, `content_hash`), with a `manifest_hash` over the whole. Only the authored-pack variant is a pack file; the fetched-pack and charter-bundle fields are refused. Its hashes are derived fields. Its lists are derived from the pack on every write, and a registration refuses an uploaded manifest whose lists differ (`manifest_membership`; ADR-0042 amendment of 2026-11-12, #110). The service holds it beside the pack aggregate, not in it (ADR-0042).

**Meta:**

- **status:** `canonical`

{#pack-document}

## Pack document

A pack's `README.md`, at the pack root: its hand-authored Markdown description, with a `## Provenance` section where `PROVENANCE.md` used to be. The service holds it in the pack aggregate as the pack's document (optional frontmatter, an optional title, a preamble and the H2 sections), read and written through the `markdown` content form and validated by the `pack-document` structure schema, and serves it at `GET /packs/{packId}/doc`. Its title is independent of the descriptor's `name`. `pack.md` and `PROVENANCE.md` are retired and refused by name (ADR-0042 as amended 2026-11-11, #260, #451).

**Meta:**

- **status:** `canonical`

{#derived-field}

## Derived field

A field of a storage object computed from other content, such as a pack manifest's `content_hash` (SHA-256 of the file's bytes with line endings normalized to LF) and `manifest_hash` (SHA-256 of the RFC 8785 canonical JSON of the manifest without it). Declared per kind in a decision record. The writer recomputes it from the bytes it writes, the reader takes it as written, and the round-trip gate verifies it on each side instead of comparing it (ADR-0042).

**Meta:**

- **status:** `canonical`
