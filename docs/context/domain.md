---
title: Charter domain vocabulary
description: The charter vocabulary the service uses, taken from the core glossary and core ADRs 2026-10-06-2 and 2026-10-11-2.
doc_status: active
updated: 2026-10-30T00:00:00Z
type: Context
kind: Glossary
tags: [glossary]
related:
  - architecture/resolution-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the vocabulary the Java domain must use unchanged.
  - profile: pack-author
    guidance_text: Read this for the pack and activation terms you write against.
  - profile: governance-lead
    guidance_text: Read this for the pack, level and activation terms a team decides with.
---

# Domain

These terms are defined in the core repository (`docs/context/charter.md`,
`docs/context/governance.md`, core ADR [`2026-10-06-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-06-2-charter-offering-active-charter-and-activation-presets.md) (charter offering, active charter and presets) and core ADR [`2026-10-11-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-11-2-a-pack-declares-its-level-in-pack-yaml.md)). They are
repeated here because the Java domain model must use them unchanged. If a definition here
and the core glossary disagree, the core glossary wins until this repository's contract
becomes the source of record for that term; flag the drift. Since
[ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/) this repository is
canonical for the charter structure and the Charter Pack schemas, so the terms of those are
defined here; for every other term this repository does not own, the pointer to core stays.

This page is the short orientation. The [Glossary](/glossary/) holds the full entries; this
repository owns the charter glossary ([ADR-0029](/adr/0029-charter-glossary-is-owned-here/)). What
the concepts mean is in the [Conceptual model](/context/conceptual-model/).

## The charter

The word "charter" is used for several things. In the Java domain model it names only the
first.

| Term | Meaning |
|---|---|
| **charter** | The governance document of a project (core glossary, `docs/context/charter.md`). In the Java domain model, `charter` alone names only this. |
| **charter service** | The Java service this repository will build. Use this, not bare "the charter", for the running process. |
| **Python charter package** | Today's `src/charter/` in core, reached through `charter.offering.api`. |
| **the charter** (unit) | Shorthand, in prose only, for the governance unit of Brain Kitty that core ADR [`2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) (the charter moves to its own repository) extracts. Do not use it as a type or module name. |

## The offering and the active charter

The charter has two sides, matching core's package split `src/charter/offering/` and
`src/charter/activation/` (core ADR `2026-10-06-2`).

| Term | Meaning |
|---|---|
| **Charter offering** | [Glossary](/glossary/charter/#charter-offering). |
| **Charter Pack** | [Glossary](/glossary/charter/#charter-pack). |
| **Activation preset** | [Glossary](/glossary/charter/#activation-preset). Presets are pack data; the Java domain keeps no code registry of them. |
| **Active charter** | [Glossary](/glossary/charter/#active-charter). |
| **Charter Bundle** | [Glossary](/glossary/charter/#charter-bundle). |

"Active" still describes one artifact's state ("an active directive"); **active charter**
names the project's activated set as a whole.

## Packs and levels

| Term | Meaning |
|---|---|
| **Pack level** | [Glossary](/glossary/governance/#pack-level). Implementations check every level; core today refuses only org packs at load (see [Resolution model](/architecture/resolution-model/)). |
| **Built-in pack** | The pack that ships with Spec Kitty. Its source of truth is `spec-kitty-open-packs`; core re-vendors a **pinned, checksummed** open-packs ref into its own `packs/built-in` at release — the committed tree stays, guarded by a pin + drift gate rather than materialized at build (core ADR [`2026-08-16-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-08-16-2-open-packs-is-source-of-truth-for-built-in-doctrine.md) Option B, amended by core #6045). The content is now seeded upstream (open-packs #32); the re-vendor pipeline (core #3504) is in progress. |
| **Org pack** | A pack listed under `charter_packs.org.packs` in `.kittify/config.yaml`. It carries `org-charter.yaml` and a single `drg/fragment.yaml`. |
| **Project pack** (core glossary: *Project layer*) | The project's own charter components under `.kittify/charter-packs/`, `pack_level: project`. It ships no presets. |
| **`parent_pack`** | A pack's declared parent. A pack with no parent is a *root pack*, which is why the level is not called "root". |

A pack has a *level*; the merged graph has *layers*, one per level. Do not say "tier" for
either.

Implementations must read the configured inputs explicitly. They never infer a pack's level,
or the internal pack (core's in-house org pack, `packs/internal/`, never shipped to
consumers), from directory presence (core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler), "Cross-language
contract").

## Artifacts and the graph

| Term | Meaning |
|---|---|
| **Artifact kind** | One of the members of core's `ArtifactKind` enum, which is the single authority for each kind's facts. The kinds and their facts are tabulated in the [Logical model](/architecture/logical-model/#artifact-kinds). The Java domain derives from the same facts and does not restate them. |
| **CRG** | The Charter Reference Graph: artifacts are nodes, typed relations are edges (`requires`, `suggests`, `refines`, `specializes_from`, `delegates_to`, `enhances`, `overrides`, `tension`, and others; `tension` replaces `in_tension_with` and the signed `impacts` edge, [ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/)). An edge endpoint is a URN `<kind>:<id>`, always; anything else is refused as `drg_schema_error`. |
| **`specializes_from`** | Profile lineage, declared as a CRG edge between two `agent_profile` nodes, never as a per-profile field. |
| **`enhances` / `overrides`** | `enhances` merges fields and keeps the action sequence and step inputs and outputs; `overrides` replaces in full. Silently dropping steps is refused. |
| **Removal / deactivation** | Removal is pack-wide, by a derived pack; deactivation is one project's choice ([ADR-0030](/adr/0030-removal-is-distinct-from-deactivation/)). |
| **Cascade** | Activating an artifact follows its CRG `requires` and `suggests` edges to the artifacts it needs. Deactivation skips artifacts another active artifact still references. |
| **Layer merge** | Built-in, org and project layers merge into one active graph under the semantics of core ADR [`2026-05-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-05-16-1-doctrine-layer-merge-semantics.md) (layer merge semantics). |

## Pack findings and levels

| Term | Meaning |
|---|---|
| **Pack finding** | One judgement on a pack file, with a code, a severity (blocking or warning), the file, the place in it and a detail. A blocking finding on a load-path file becomes a refusal; a warning becomes a diagnostic. |
| **Declared level** | The `pack_level` a pack states in its own `pack.yaml`. It is optional; a pack that states none gets the diagnostic `pack_level_missing`. |
| **Configured level** | The level the operator's configuration places a pack at: built-in, `charter_packs.org.packs`, or the project layer. A declared level that differs from it refuses the pack with `pack_level_mismatch`. |

## Node kinds in an org fragment

One name per concept, with no aliases. A fragment node names its kind with the plural of an
artifact kind (`mission_types` and `anti_patterns` included), or with `actions`.

| Term | Meaning |
|---|---|
| **Mission step contract** | The structural sub-steps of one mission step and what each delegates to; the fragment kind is `mission_step_contracts`. The step's role and expected artifacts live on the mission step, not here. Avoid `mission_steps`. In core `mission_steps` is the canonical name and `mission_step_contracts` its back-compat alias; the service's canonical choice is the reverse, and it refuses `mission_steps`. |
| **Action** | A mission step as the graph names it, the node `action:<mission-type>/<step>`; the fragment kind is `actions`. Core refuses it in a fragment. The node stays until [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189) rules on edge authority; until then the action and the mission step name the same thing. |
| **Anti-pattern** | A node that good artifacts `reject`; it has no descriptor file. The fragment kind is `anti_patterns`. Core refuses it in a fragment. |
| **Mission type** | [Glossary](/glossary/charter/#mission-type). An artifact kind in the service (`mission_type`, ADR-0044), the node `mission_type:<id>`; the fragment kind is `mission_types`. Avoid the singular `mission_type` as a kind name in a fragment: it is refused. |

## Mission types and steps

One file per mission type, `missions/<id>/<id>.mission-type.yaml`
([ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/)).

| Term | Meaning |
|---|---|
| **Mission step** | [Glossary](/glossary/charter/#mission-step). A sub-item of its mission type's `steps` (in run order) or `on_demand_steps`: a role, a one-line description and the expected input and output artifacts that are its pre- and postconditions. A domain type (`MissionStep`), not an artifact kind: there is no `mission_steps` kind. |
| **On-demand step** | A mission step invoked by an explicit command, outside the run order, listed in `on_demand_steps` (software-dev's `charter`, `tasks-outline`, `tasks-packages` and `tasks-finalize`; the documentation and research `retrospect` steps). |
| **Step template** | [Glossary](/glossary/charter/#step-template). The prompt fed to the agent when a step runs, the step's prompt file `steps/<id>.prompt.md`; its frontmatter holds only its detailed description. Not a template payload. |
| **Step guidelines** | A step's working notes, `steps/<id>.guidelines.md`. Not the glossary's charter *Guideline*. |
| **Recipe ingredients** | [Glossary](/glossary/charter/#recipe-ingredients). The mission type's own `references`; a step's `references` are its action-scoped guidance. |

The authored pack files are drawn in the
[pack write model](/architecture/descriptor-schemas/#pack-write-model).

## Operations

| Term | Meaning |
|---|---|
| **Plan / commit activation** | Activation is planned (validated, non-mutating) and then committed. Config is never written when the plan fails. |
| **Action-scoped context** | The guidance for one action of one mission type, as `spec-kitty charter context --action <name>` returns it today. Action nodes in the CRG are `action:<mission-type>/<action>`; the CLI's bootstrap set is specify, plan, implement and review. |
| **Profile resolution** | `(profile_id, action)` in, the resolved profile with its `specializes_from` lineage and `enhances` / `overrides` edges applied plus compact context out (core `docs/architecture/profile-load-reliability.md` §4.2). Fail loud: an unresolved profile is an error, never an unprofiled agent. Selecting a profile for a role follows [ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/): role fit, specialization, declared fallback, then not found. |
| **Resolution** | Computing an answer from the active graph: action-scoped context, a profile, or a graph query. Not the core glossary's *Governance Resolution*, which is the existence and compatibility check of the active charter against the offering. |

## Neighboring terms

| Term | Meaning |
|---|---|
| **Event ledger** | The append-only event logs the core runtime writes (today `kitty-specs/<mission>/status.events.jsonl`). Core's glossary calls each one an event log. The charter service never writes to it. |
| **Charter contract** | The OpenAPI document plus the YAML document schemas, semantic rules, identifiers, fixtures and expected results listed in [Contract and DTOs](/architecture/contract-and-dtos/). |

## Words to avoid

- **"Doctrine"** is retired as a Spec Kitty term in this repository: the governance layer is named
  *charter* throughout (Charter Domain, Charter Reference Graph (CRG), charter artifact / kind /
  pack / catalog). **"Doctrine pack"** and **"Pack Default Charter"** are likewise retired names
  (core ADR `2026-10-06-2`). The word may appear only in its plain-English sense (a body of
  principles), never as a name for a domain object.
- **"Primary"** for the Java-authoritative strangler stage: core ADR `2026-10-03-1` avoids it
  because the word already has four senses. Say "Java authoritative".
- **"Feature"** for a Mission: Spec Kitty's terminology canon prohibits it.
