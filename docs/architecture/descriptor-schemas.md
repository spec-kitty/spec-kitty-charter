---
title: Descriptor schemas
description: PlantUML views of the charter descriptor kinds, their fields, enums and CRG edges, with the pack JSON Schemas as the structural authority.
doc_status: draft
updated: 2026-10-28T00:00:00Z
type: Architecture
kind: Reference
related:
  - architecture/overview
  - architecture/contract-and-dtos
  - architecture/resolution-model
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
audience:
  - profile: pack-author
    guidance_text: Read this for entity diagrams of the descriptor schemas and their edges.
---

# Descriptor schemas

The structural authority for every descriptor file is the set of JSON Schemas in
[`contracts/packs/schemas/`](https://github.com/spec-kitty/spec-kitty-charter/tree/main/contracts/packs/schemas)
(18 schemas), canonical here and edited in place under
[ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) and
[ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/) (first imported from core's models;
the import's changes are tabled in the
[mission research](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md#import-changes-c1-c16)). The service's YAML adapter validates against them. The authored
files and how they nest are drawn in the [pack write model](#pack-write-model). The other diagrams on this
page were drawn from core's models and sources (cited in each section) before the schemas
landed; where one differs from a schema, the schema wins. Notation in the field diagrams: `+` required, `-`
optional, `~` regex pattern, `[1..]` non-empty list, `= x` default.

All `src/charter/...` and `packs/...` paths on this page are in core,
[`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty).

These diagrams are explained by [Descriptors](/architecture/descriptors/). Where a diagram
and a schema disagree about structure, the schema wins; where this page and
[Descriptors](/architecture/descriptors/) disagree about meaning, that page wins. Known older
details in the core-derived diagrams: `pack_version` is required at every level, not only for
`built-in` (DIV-004); `pack_level` is optional; edge endpoints are URNs only (DIV-007); the
org fragment has no header fields (DIV-008); and the node-kind names follow the
[pack write model](#pack-write-model). See the
[Divergence register](/conformance/divergence-register/).

## Pack write model

![Pack write model](diagrams/pack-write-model.svg)

The authored pack files at C4 code level: the pack descriptor, graph shards, the org fragment,
nodes, edges, artifact files, the asset manifest and the org charter, with their
multiplicities, drawn from the schemas and the domain records. Source:
`diagrams/pack-write-model.puml`. A node of a fragment names its kind with one canonical
plural (`mission_step_contracts`, not `mission_steps`); see [Domain](/context/domain/). An artifact
file's identity is `id`, or `profile-id` in a `*.agent.yaml`. A shard node may carry an optional
`provenance`; an edge may not. `charter.schema.json` is not drawn: it validates
the project's `charter.yaml`, which is not a pack file (`file-kinds.yaml`). The pack-level
`preset` and `pack-manifest` schemas are not drawn yet; they are described in
[Pack-level schemas](#pack-level-schemas).

## Kinds, containment and edges

![Charter descriptor kinds](diagrams/descriptor-kinds-overview.svg)

A Charter Pack (`pack.yaml`) ships descriptor files, an optional CRG fragment and an
optional org charter. CRG nodes refer to descriptors by URN `<kind>:<id>`. An org
charter's `required_<plural>` lists overlay the project charter's `selected_<plural>`
lists; which kinds do is in the [Artifact kinds table](/architecture/logical-model/#artifact-kinds). The edge counts come from
`packs/built-in/*.graph.yaml`.

Sources: `src/charter/offering/artifact_kinds.py`, `src/charter/offering/drg/models.py`,
`src/charter/offering/packs/pack_descriptor.py`, `src/charter/activation/org_charter.py`,
`src/charter/activation/schemas.py`, `packs/built-in/*.graph.yaml`.

## Charter kinds

![Charter descriptor schemas](diagrams/descriptor-doctrine-kinds.svg)

Sources: `src/charter/offering/schemas/{directive,tactic,paradigm,styleguide,toolguide,procedure}.schema.yaml`.

Example instance (`packs/built-in/directives/018-doctrine-versioning-requirement.directive.yaml`, folded text shortened):

![Directive example](diagrams/descriptor-directive-example.svg)

## Agent profile

![Agent profile schema](diagrams/descriptor-agent-profile.svg)

Source: `src/charter/offering/schemas/agent-profile.schema.yaml` (schema-version 2).

## Mission-step contract, pack skill, glossary pack

![Mission-step contract, skill and glossary pack schemas](diagrams/descriptor-mission-skill-glossary.svg)

Sources: `src/charter/offering/missions/step_contracts.py`,
`src/charter/offering/schemas/skill.schema.yaml` with `src/charter/offering/pack_skills/models.py`,
`src/charter/offering/glossary_packs/models.py`. In core, the mission-step contract and the
glossary pack have no JSON Schema file; their Pydantic models are the schema. This repository
holds a closed JSON Schema for each (`contracts/packs/schemas/`).

## Pack, CRG and charter configuration

![Pack, CRG and charter configuration schemas](diagrams/descriptor-governance.svg)

Sources: `src/charter/offering/packs/pack_descriptor.py`, `src/charter/offering/drg/models.py`,
`src/charter/activation/org_charter.py`, `src/charter/activation/schemas.py`.

## Pack-level schemas

Three schemas cover the files that describe a pack as a whole. All three are closed
(`additionalProperties: false` at every level), so an unknown key is refused
([ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/), proposed). The
`preset` and `pack-manifest` schemas were designed here, not imported (the descriptor schema was
imported from core, ADR-0017): core's preset and manifest files have other shapes, and the
pinned built-in ones are refused until `spec-kitty-open-packs` adopts these.

**`pack-descriptor`** (`pack.yaml`). The fields are unchanged. The schema is closed now: a key it
does not declare, a retired one such as `accompanies_doctrine_pack` included, makes `pack.yaml`
unreadable. Its description says that `pack_level` is optional and `pack_version` is required at
every level.

**`preset`** (`presets/<id>.yaml`):

- `+ schema_version`: `"1.0"`
- `+ id`: `~ ^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, at most 64 characters, equal to the file stem
- `+ description`: not empty
- `- activated_kinds`: `[1..]` unique kind plurals, from the 13 kinds
- `- activated_directives`, `activated_tactics`, `activated_styleguides`, `activated_toolguides`,
  `activated_paradigms`, `activated_procedures`, `activated_agent_profiles`,
  `activated_mission_step_contracts`, `activated_anti_patterns`, `activated_mission_types`: each
  `[1..]` items
- an item: `+ id` (not empty), `- must_have` (only `true`: present means must-have)

**`pack-manifest`** (`pack-manifest.yaml`):

- `+ schema_version`: `"1.0"`
- `+ manifest_hash`: `~ ^[0-9a-f]{64}$`, derived
- `+ constituents`: a list, possibly empty, of `{+ kind, + id, + path, + content_hash}`; `kind` is
  an artifact kind token, `content_hash` is 64 lowercase hex characters, derived
- `- presets`: `[1..]` of `{+ id, + path, + content_hash}`; `path` matches `presets/<id>.yaml`
  for an id of the preset grammar, and a storage rule beyond the schema requires it to be the
  entry's own `presets/<id>.yaml`

A derived field is not authored: the service recomputes it on every whole-pack write.
`content_hash` is SHA-256 of the file's bytes with CRLF and lone CR replaced by LF; `manifest_hash`
is SHA-256 of the RFC 8785 canonical JSON of the manifest without `manifest_hash`. The fetched-pack
and charter-bundle fields of core's manifest (`charter`, `fetched_at`, `source_url`, `source_type`,
`generated_at`, `generated_by`, `provenance_path`) are not in the schema.

## Regenerating the diagrams

The `.puml` files in `docs/architecture/diagrams/` are the sources; the committed `.svg`
files are rendered from them because the docs site renders diagrams client-side and has
no PlantUML step. After editing a source, run:

```bash
cd docs/architecture/diagrams && plantuml -tsvg descriptor-*.puml pack-write-model.puml
```

`_style.iuml` holds the shared styling and is included by every diagram.
