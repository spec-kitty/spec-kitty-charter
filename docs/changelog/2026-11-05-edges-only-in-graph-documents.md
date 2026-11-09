---
okf_version: "0.2"
title: Relationships are authored only in graph documents
description: The artifact fields that named other artifacts are dropped, and the registration and read shapes lose their relationships arrays. An edge lives only in a pack's graph documents.
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0064-graph-documents-are-the-only-source-of-relationships
  - guides/compose-packs
  - architecture/descriptors
audience:
  - profile: pack-author
    guidance_text: Read this for the fields your pack files may no longer carry and where their edges go.
  - profile: charter-service-contributor
    guidance_text: Read this if you call the registration operations or read an artifact's references.
---

# 2026-11-05 — Relationships are authored only in graph documents

**A relationship between two artifacts is authored only in a pack's graph documents**
([#240](https://github.com/spec-kitty/spec-kitty-charter/issues/240),
[ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/)). These are the `*.graph.yaml` shards
in built-in and `drg/fragment.yaml` in an org or project pack. Over the API, `PUT /packs/{packId}/relationships`
(`putPackRelationship`) is the only write of an edge.

For pack authors:

- The pack schemas drop `references` from directive, paradigm, tactic, tactic step, procedure, mission type, mission
  step, styleguide and toolguide, a paradigm's `directive_refs` and a directive's `procedures`. A file that still
  carries one is refused as an unknown field. Write the edge in a graph document instead.
- A directive's prose steps are procedure artifacts, which the directive `requires` through a graph edge.
- A styleguide or toolguide citation that names no artifact moves into the artifact's text.
- A mission type's ingredients are its graph `scope` edges; a step's guidance is the graph edges from its
  `action:<mission-type>/<step>` node.
- A reference across packs is an edge end, still written `<pack-name>::<kind>:<id>`.
- Fields that name other artifacts as data stay, among them an agent profile's `*-references`, a guard's
  `implemented_by` and a skill's `enhances` and `overrides`.

For API clients:

- The registration bodies carry no `relationships` array, and the directive, paradigm and styleguide read shapes
  lose theirs. A body with a member its shape does not declare, `relationships`, `references` or `procedures`
  among them, is refused with 422 `validation_failed` naming the member.
- `listArtifactReferences` answers the edges in the pack's graph documents whose source is the artifact, each with
  its relation, then its derived relations (only a guard's `implemented_by`).

The contract change is recorded as a `### Breaking` fragment on the unreleased `0.2.0-SNAPSHOT` line
([ADR-0022](/adr/0022-contract-versions-start-at-0-x/)). [Compose packs](/guides/compose-packs/) now describes
cross-pack references as edge ends only.
