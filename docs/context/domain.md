---
title: Domain
description: The charter vocabulary the service uses, taken from the core glossary and core ADRs 2026-10-06-2 and 2026-10-11-2.
doc_status: active
updated: 2026-10-13
type: Context
kind: Glossary
tags: [glossary]
related:
  - architecture/resolution-model
---

# Domain

These terms are defined in the core repository (`docs/context/charter.md`,
`docs/context/governance.md`, core ADR `2026-10-06-2` and core ADR `2026-10-11-2`). They are
repeated here because the Java domain model must use them unchanged. If a definition here
and the core glossary disagree, the core glossary wins until this repository's contract
becomes the source of record for that term; flag the drift.

This page is the short orientation. The [Glossary](/glossary/) holds the full entries,
imported from the core glossary.

## The charter

The word "charter" is used for several things. In the Java domain model it names only the
first.

| Term | Meaning |
|---|---|
| **charter** | The governance document of a project (core glossary, `docs/context/charter.md`). In the Java domain model, `charter` alone names only this. |
| **charter service** | The Java service this repository will build. Use this, not bare "the charter", for the running process. |
| **Python charter package** | Today's `src/charter/` in core, reached through `charter.offering.api`. |
| **the charter** (unit) | Shorthand, in prose only, for the governance unit of Brain Kitty that core ADR `2026-10-12-2` extracts. Do not use it as a type or module name. |

## The offering and the active charter

The charter has two sides, matching core's package split `src/charter/offering/` and
`src/charter/activation/` (core ADR `2026-10-06-2`).

| Term | Meaning |
|---|---|
| **Charter offering** | Everything offered to a project: the Charter Packs it can draw from (the built-in pack and each configured org pack) plus the project layer. |
| **Charter Pack** | A distributable bundle of interconnected charter components (artifacts and their DRG edges) together with optional activation presets (`presets/<name>.yaml`). An org pack may also enforce activations (`required_<kind>` in `org-charter.yaml`). |
| **Activation preset** | A named set of activations a Charter Pack ships, applied with `spec-kitty charter activate --pack <pack> --preset <preset>`. The built-in pack ships `default` (no per-kind restriction, so it cannot drift) and `minimal`. Presets are pack data; there is no code registry. |
| **Active charter** | What a project has activated: its `activated_<kind>` keys, `activated_kinds` and `mission_type_activations`. |
| **Charter Bundle** | The materialized `.kittify/charter/` tree. |

"Active" still describes one artifact's state ("an active directive"); **active charter**
names the project's activated set as a whole.

## Packs and levels

| Term | Meaning |
|---|---|
| **Pack level** | `built-in`, `org` or `project`, declared as `pack_level` in every pack's `pack.yaml` (core ADR `2026-10-11-2`). Loading refuses a pack whose declared level disagrees with where it is configured (`PACK_LEVEL_MISMATCH`) or that lacks the field (`PACK_LEVEL_MISSING`). In core today only org packs are refused at load; see [Resolution model](/architecture/resolution-model/). |
| **Built-in pack** | The pack that ships with Spec Kitty. Its designated source home is `spec-kitty-open-packs`, to be vendored into consumer installs byte-identical (core ADR `2026-08-16-2`; the vendoring pipeline, core #3504, is not built yet). |
| **Org pack** | A pack listed under `charter_packs.org.packs` in `.kittify/config.yaml`. It carries `org-charter.yaml` and a single `drg/fragment.yaml`. |
| **Project pack** (core glossary: *Project layer*) | The project's own charter components under `.kittify/charter-packs/`, `pack_level: project`. It ships no presets. |
| **`parent_pack`** | A pack's declared parent. A pack with no parent is a *root pack*, which is why the level is not called "root". |

A pack has a *level*; the merged graph has *layers*, one per level. Do not say "tier" for
either.

Implementations must read the configured inputs explicitly. They never infer a pack's level,
or the internal pack (core's in-house org pack, `packs/internal/`, never shipped to
consumers), from directory presence (core ADR `2026-10-03-1`, "Cross-language
contract").

## Artifacts and the graph

| Term | Meaning |
|---|---|
| **Artifact kind** | One of the kinds the core `ArtifactKind` enum defines: `directive`, `tactic`, `styleguide`, `toolguide`, `paradigm`, `procedure`, `agent_profile`, `mission_step_contract`, `glossary_pack`, `skill`, `anti_pattern`, plus `template` and `asset`, which are not charter-activatable. The enum also carries each kind's plural, whether it is activatable, org-requirable and selection-overlayable, and what is in force when its activation key is absent. It is the single authority; the Java domain derives from the same facts and must not restate them. |
| **DRG** | The Doctrine Reference Graph: artifacts are nodes, typed relations are edges (`requires`, `suggests`, `refines`, `specializes_from`, `delegates_to`, `enhances`, `overrides`, and others). An edge endpoint is a URN `<kind>:<id>` or a bare id the fragment's own nodes declare; anything else is refused at merge time (`unresolved_edge_endpoint`). |
| **`specializes_from`** | Profile lineage, declared as a DRG edge between two `agent_profile` nodes, never as a per-profile field. |
| **`enhances` / `overrides`** | `enhances` merges fields and keeps the action sequence and step inputs and outputs; `overrides` replaces in full. Silently dropping steps is refused. |
| **Cascade** | Activating an artifact follows its DRG `requires` and `suggests` edges to the artifacts it needs. Deactivation skips artifacts another active artifact still references. |
| **Layer merge** | Built-in, org and project layers merge into one active graph under the semantics of core ADR `2026-05-16-1` (doctrine layer merge semantics). |

## Operations

| Term | Meaning |
|---|---|
| **Plan / commit activation** | Activation is planned (validated, non-mutating) and then committed. Config is never written when the plan fails. |
| **Action-scoped context** | The guidance for one action of one mission type, as `spec-kitty charter context --action <name>` returns it today. Action nodes in the DRG are `action:<mission-type>/<action>`; the CLI's bootstrap set is specify, plan, implement and review. |
| **Profile resolution** | `(profile_id, action)` in, the resolved profile with its `specializes_from` lineage and `enhances` / `overrides` edges applied plus compact context out (core `docs/architecture/profile-load-reliability.md` §4.2). Fail loud: an unresolved profile is an error, never an unprofiled agent. |
| **Resolution** | Computing an answer from the active graph: action-scoped context, a profile, or a graph query. Not the core glossary's *Governance Resolution*, which is the existence and compatibility check of the active charter against the offering. |

## Neighboring terms

| Term | Meaning |
|---|---|
| **Event ledger** | The append-only event logs the core runtime writes (today `kitty-specs/<mission>/status.events.jsonl`). Core's glossary calls each one an event log. The charter service never writes to it. |
| **Charter contract** | The OpenAPI document plus the YAML document schemas, semantic rules, identifiers, fixtures and expected results listed in [Contract and DTOs](/architecture/contract-and-dtos/). |

## Words to avoid

- **"Doctrine pack"** and **"Pack Default Charter"** are retired names (core ADR `2026-10-06-2`).
  The governance *content* of a directive may still be called doctrine.
- **"Primary"** for the Java-authoritative strangler stage: core ADR `2026-10-03-1` avoids it
  because the word already has four senses. Say "Java authoritative".
- **"Feature"** for a Mission: Spec Kitty's terminology canon prohibits it.
