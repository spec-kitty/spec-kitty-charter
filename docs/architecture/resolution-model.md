---
title: Resolution model
description: What the charter domain computes (layer merge, activation, cascade, action-scoped context, profile resolution) and the fail-loud rules it inherits.
doc_status: draft
updated: 2026-10-13
type: Architecture
kind: Explanation
related:
  - context/domain
  - architecture/contract-and-dtos
---

# Resolution model

> **State:** the behaviour below exists today in core's Python charter. The Java domain must
> reproduce it, fixture for fixture, before it answers anything in production. Where core's
> behaviour is known to be wrong, the fix lands in Python first (with a fixture), so the two
> implementations never disagree on purpose.

## Inputs

The service reads only configured inputs, never directory presence:

| Input | Where | Notes |
|---|---|---|
| Built-in pack | the pack root the CLI resolves for `built-in` | sharded `*.graph.yaml` plus a generated `pack-manifest.yaml`; `pack_level: built-in` |
| Org packs | each directory under `charter_packs.org.packs` in `.kittify/config.yaml` | `pack.yaml` (`pack_level: org`), `org-charter.yaml`, one `drg/fragment.yaml` |
| Project pack | `.kittify/charter-packs/` | `pack_level: project` |
| Active charter | `.kittify/config.yaml`, or `charter.yaml` for a migrated project | `activated_<kind>` keys and `mission_type_activations` |
| Mission type | the request (`--mission-type` in the CLI) or the mission's `meta.json` | keys the action grain: action nodes are `action:<mission-type>/<action>` |
| Load depth | the request | first load (bootstrap) or a later, compact load; see Writes |

The retired `doctrine.org.packs` key and the `.kittify/doctrine/` root are not read: core's
charter-pack cutover removes every read-side fallback after a one-time upgrade migration
(core ADR `2026-10-06-2` §7). The Java service starts on the cut-over vocabulary.

## Pipeline

```mermaid
flowchart LR
  load["load packs<br/>check pack_level"] --> merge["merge layers<br/>built-in, org, project"]
  merge --> graph["active graph<br/>validated DRG"]
  act["active charter<br/>+ org required_kind"] --> graph
  graph --> ctx["action-scoped<br/>context"]
  graph --> prof["profile<br/>resolution"]
  graph --> q["graph queries<br/>listings"]
```

1. **Load.** Each pack's `pack.yaml` is read and its `pack_level` checked against where it
   is configured. A mismatch refuses with `PACK_LEVEL_MISMATCH`, a missing field with
   `PACK_LEVEL_MISSING` (core ADR `2026-10-11-2`; in core today only org packs are checked at
   load, and the Java service should check every level).
2. **Merge.** The three layers merge into one graph under core ADR `2026-05-16-1`. Edge
   endpoints resolve to `<kind>:<id>` URNs or to bare ids the fragment declares; anything
   else is an `unresolved_edge_endpoint` conflict. Org `drg/fragment.yaml` edges reach the
   same graph cascade reads, not a separate diagnostic path (the charter-resolution
   program's M2 fix).
3. **Activate.** A per-kind activation key that is absent means what the kind's
   `effective_when_absent` says (`all` for most kinds; `required` for `skill`). A present key
   is an allowlist. Org `required_<kind>` lists apply where the kind is org-requirable, and
   union into the selection where it is selection-overlayable.
4. **Answer.** Action-scoped context, profile resolution with `specializes_from` lineage and
   `enhances` / `overrides` applied, graph queries and listings.

## Writes

Activation is a **plan, then commit**: planning validates and never mutates; committing
writes only after the plan succeeds. Applying a preset over customised lists is refused
without `--force`, and the applied preset name is not persisted. Three YAML write paths
exist in core today, and each is a separate operation to migrate
([Strangler transition](/architecture/strangler-transition/)):

- activation commit (`commit_plan`, which picks `.kittify/config.yaml` or `charter.yaml`);
- the charter document save (`charter_yaml_io`, preserving authored sections);
- compile (`write_compiled_charter`, refreshing only the derived `catalog` and `metadata`
  sections).

A fourth, non-YAML write exists: the context read itself records first-load state in
`.kittify/charter/context-state.json` (`--mark-loaded`), and its answer differs between a
first load and a later one. Default here: that state stays in the Python seam, and the
service takes the load depth as a request parameter
([open questions](/plans/open-questions/), question 12).

## Rules the domain inherits

- **Derive, do not restate.** Kind vocabulary, plurals and per-kind facts come from one
  authority. Core's single authority is the `ArtifactKind` enum. The contract publishes the
  kind table as data (a fixture or a schema extension); the Java domain's own kind type is
  checked against it by a conformance test, not imported from generated code, because the
  domain imports no generated types.
- **Fail loud.** A dropped artifact, an unresolved edge, an unresolved profile or a stale
  input is a named refusal or a visible diagnostic, never a quiet empty answer. This is the
  lesson of the charter-resolution program (core `docs/plans/charter-resolution/`).
- **One answer.** While two implementations exist, a difference between them is a defect in
  one of them, found by the shadow comparison or the conformance corpus.
