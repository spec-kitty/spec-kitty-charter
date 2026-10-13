---
title: Domain
description: The charter vocabulary the service owns, taken from the core glossary and ADRs 2026-10-06-2 and 2026-10-11-2.
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
`docs/context/governance.md`, ADR `2026-10-06-2` and ADR `2026-10-11-2`). They are repeated
here because the Java domain model must use them unchanged. If a definition here and the
core glossary disagree, the core glossary wins until this repository's contract becomes the
source of record for that term; flag the drift.

## The offering and the active charter

The charter has two sides, matching core's package split `src/charter/offering/` and
`src/charter/activation/` (ADR `2026-10-06-2`).

| Term | Meaning |
|---|---|
| **Charter offering** | Everything offered to a project: the Charter Packs it can draw from. |
| **Charter Pack** | A distributable bundle of interconnected charter components (artifacts and their DRG edges) together with a set of activation presets. An org pack may also enforce activations (`required_<kind>` in `org-charter.yaml`). |
| **Activation preset** | A named set of activations a Charter Pack ships, applied with `spec-kitty charter activate --pack <pack> --preset <preset>`. The built-in pack ships `default` (no per-kind restriction, so it cannot drift) and `minimal`. Presets are pack data; there is no code registry. |
| **Active charter** | What a project has activated: its `activated_<kind>` keys and `mission_type_activations`. |
| **Charter Bundle** | The materialised `.kittify/charter/` tree. |

"Active" still describes one artifact's state ("an active directive"); **active charter**
names the project's activated set as a whole.

## Packs and levels

| Term | Meaning |
|---|---|
| **Pack level** | `built-in`, `org` or `project`, declared as `pack_level` in every pack's `pack.yaml` (ADR `2026-10-11-2`). Loading refuses a pack whose declared level disagrees with where it is configured (`PACK_LEVEL_MISMATCH`) or that lacks the field (`PACK_LEVEL_MISSING`). In core today only org packs are refused at load; see [Resolution model](/architecture/resolution-model/). |
| **Built-in pack** | The pack that ships with Spec Kitty. Its designated source home is `spec-kitty-open-packs`, to be vendored into consumer installs byte-identical (core ADR `2026-08-16-2`; the vendoring pipeline, core #3504, is not built yet). |
| **Org pack** | A pack listed under `charter_packs.org.packs` in `.kittify/config.yaml`. It carries `org-charter.yaml` and a single `drg/fragment.yaml`. |
| **Project pack** | The pack under `.kittify/charter-packs/`. |
| **`parent_pack`** | A pack's declared parent. A pack with no parent is a *root pack*, which is why the level is not called "root". |

Implementations must read the configured inputs explicitly. They never infer a pack's level,
or the internal pack, from directory presence (core ADR `2026-10-03-1`, "Cross-language
contract").

## Artifacts and the graph

| Term | Meaning |
|---|---|
| **Artifact kind** | One of the kinds the core `ArtifactKind` enum defines: `directive`, `tactic`, `styleguide`, `toolguide`, `paradigm`, `procedure`, `agent_profile`, `mission_step_contract`, `glossary_pack`, `skill`, `anti_pattern`, plus `template` and `asset`, which are not charter-activatable. The enum also carries each kind's plural, whether it is activatable, org-requirable and selection-overlayable, and what is in force when its activation key is absent. It is the single authority; the Java domain derives from the same facts and must not restate them. |
| **DRG** | The doctrine reference graph: artifacts are nodes, typed relations are edges (`requires`, `suggests`, `refines`, `specializes_from`, `delegates_to`, `enhances`, `overrides`, and others). An edge endpoint is a URN `<kind>:<id>` or a bare id the fragment's own nodes declare; anything else is refused at merge time (`unresolved_edge_endpoint`). |
| **`specializes_from`** | Profile lineage, declared as a DRG edge between two `agent_profile` nodes, never as a per-profile field. |
| **`enhances` / `overrides`** | `enhances` merges fields and keeps the action sequence and step inputs and outputs; `overrides` replaces in full. Silently dropping steps is refused. |
| **Cascade** | Activating an artifact follows its DRG `requires` and `suggests` edges to the artifacts it needs. Deactivation skips artifacts another active artifact still references. |
| **Layer merge** | Built-in, org and project layers merge into one active graph under the semantics of core ADR `2026-05-16-1` (doctrine layer merge semantics). |

## Operations

| Term | Meaning |
|---|---|
| **Plan / commit activation** | Activation is planned (validated, non-mutating) and then committed. Config is never written when the plan fails. |
| **Action-scoped context** | The guidance for one action of one mission type, as `spec-kitty charter context --action <name>` returns it today. Action nodes in the DRG are `action:<mission-type>/<action>`; the CLI's bootstrap set is specify, plan, implement and review. |
| **Profile resolution** | `(profile_id, action)` in, the resolved profile with its lineage and overlays applied plus compact context out (core `docs/architecture/profile-load-reliability.md` §4.2). Fail loud: an unresolved profile is an error, never an unprofiled agent. |

## Words to avoid

- **"Doctrine pack"** and **"Pack Default Charter"** are retired names (ADR `2026-10-06-2`).
  The governance *content* of a directive may still be called doctrine.
- **"Primary"** for the Java-authoritative strangler stage: core ADR `2026-10-03-1` avoids it
  because the word already has four senses. Say "Java authoritative".
- **"Feature"** for a Mission: Spec Kitty's terminology canon prohibits it.
