---
title: Resolution model
description: What the charter domain computes (layer merge, activation, cascade, action-scoped context, profile resolution) and the fail-loud rules it inherits.
doc_status: draft
updated: 2026-10-26T00:00:00Z
type: Architecture
kind: Explanation
related:
  - adr/0018-action-context-read-and-seam-access-surface
  - context/domain
  - architecture/contract-and-dtos
  - adr/0012-pack-mutability-policy
  - adr/0013-persistent-deactivation
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the behavior the domain must reproduce, step by step.
  - profile: pack-author
    guidance_text: Read this to predict how your packs load, merge and activate.
---

# Resolution model

What the charter domain computes from its inputs: layer merge, activation, cascade, action-scoped context and profile resolution, and the fail-loud rules it keeps.

> **Status:** exists in core. The Java domain must reproduce it fixture for fixture before it answers in production; a wrong core behavior is fixed in Python first, with a fixture. Mutability levels, persistent exclusions and the `impacts` relation are provisional decisions of 2026-10-19, not yet built in core; since 2026-10-26 the relation is `tension` and `impacts` is a per-value list ([ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/)), removal is distinct from deactivation ([ADR-0030](/adr/0030-removal-is-distinct-from-deactivation/)) and requirement is authored as a preset ([ADR-0031](/adr/0031-requirement-is-authored-as-a-preset/)). [What the labels mean](/#status).

## Inputs

The service reads only configured inputs, never directory presence:

| Input | Where | Notes |
|---|---|---|
| Built-in pack | the pack root the CLI resolves for `built-in` | sharded `*.graph.yaml` plus a generated `pack-manifest.yaml`; `pack_level: built-in` |
| Org packs | each directory under `charter_packs.org.packs` in `.kittify/config.yaml` | `pack.yaml` (`pack_level: org`), `org-charter.yaml`, one `drg/fragment.yaml` |
| Project pack | `.kittify/charter-packs/` | `pack_level: project` |
| Active charter | `.kittify/config.yaml`, or `charter.yaml` for a migrated project | `activated_<kind>` keys, `activated_kinds` and `mission_type_activations` |
| Mission type | the request (`--mission-type` in the CLI) or the mission's `meta.json` | keys the action grain: action nodes are `action:<mission-type>/<action>` |
| `depth` | the request | the number of `suggests` hops of the action-context walk, 0 to 2, default 2; the seam maps a first load to 2, a recorded load to 1 and the public bundle read to 0 |

The retired `doctrine.org.packs` key and the `.kittify/doctrine/` root are not read: core's
charter-pack cutover removes every read-side fallback after a one-time upgrade migration
(core ADR [`2026-10-06-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-06-2-charter-offering-active-charter-and-activation-presets.md) §7). The Java service starts on the cut-over vocabulary.

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
   is configured (core ADR [`2026-10-11-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-11-2-a-pack-declares-its-level-in-pack-yaml.md), a pack declares its level). A mismatch refuses with
   `pack_level_mismatch`. A missing level does not stop the load: it is the diagnostic
   `pack_level_missing`, and [`DiagnosticCode`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/schemas/DiagnosticCode.yaml) in the contract states when it applies.
2. **Merge.** The three layers merge into one graph under core ADR [`2026-05-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-05-16-1-doctrine-layer-merge-semantics.md) (how doctrine layers merge). Edge
   endpoints are URNs `<kind>:<id>` everywhere, so there is no endpoint resolution step and
   `unresolved_edge_endpoint` is no longer raised (DIV-007); a value that is not a URN never
   gets this far, because the load refuses it as `drg_schema_error`. Edges from an org pack's `drg/fragment.yaml`
   land in the same graph that cascade reads; they are not kept on a separate,
   diagnostic-only path (a defect the charter-resolution program fixed). Each artifact's
   mutability level is checked against what later packs do to it: a change the level does not
   allow is the refusal `governance_policy_violation`, a reserved code that nothing raises yet
   ([ADR-0012](/adr/0012-pack-mutability-policy/)). When org packs declare the same URN, the
   later-declared pack wins for every URN and every restated edge (core `59d190a7`, #87).
3. **Activate.** A per-kind activation key that is absent means what the kind's
   `effective_when_absent` says (`all` for most kinds; `required` for `skill`). A present key
   is an allowlist. A `deactivated_<kind>` deny-list is subtracted from either
   ([ADR-0013](/adr/0013-persistent-deactivation/)). Org `required_<kind>` lists apply where
   the kind is org-requirable, and union into the charter's `selected_<kind>` list where it is
   selection-overlayable.
4. **Answer.** Action-scoped context, profile resolution with `specializes_from` lineage and
   `enhances` / `overrides` applied, graph queries and listings. Profile selection for a role
   follows the order of [ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/):
   role fit, specialization, declared fallback, then a not-found refusal.

### The action-context walk

`getActionContext` answers the offering's walk, never a filtered one. Five breadth-first steps,
each artifact listed once by the first step that reaches it:

1. `scope` from the action node, one hop;
2. `requires` from the scoped artifacts, transitively;
3. `suggests` from the scoped artifacts, up to `depth` hops;
4. `requires` of the suggested artifacts, transitively (the one registered difference in the
   walk, DIV-110);
5. `vocabulary`, one hop from everything the first four steps list.

Each entry says which relation listed it (`relation`), its parent (`via`) and its distance from
the action (`hops`). Following `via` always reaches the action in exactly `hops` edges. `depth`
counts suggests hops only; `hops` is the full path length. The mission-type node is not walked.
The walk goes through a node the graph does not declare: edges are facts, so the declared
artifacts behind it are listed with `via` set to that node, and the node itself has a null label.
What applies in one project (activation, exclusions, selection roots) is a later operation, R9
(#103). The rules and the reasons are in
[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/); the differences from core are
in the [divergence register](/conformance/divergence-register/).

## Writes

Activation is a **plan, then commit**: planning validates and never mutates; committing
writes only after the plan succeeds. Applying a preset over customized lists is refused
without `--force`, and the applied preset name is not persisted. Three YAML write paths
exist in core today, and each is a separate operation to migrate
([Strangler transition](/architecture/strangler-transition/)):

- activation commit (`commit_plan`, which picks `.kittify/config.yaml` or `charter.yaml`);
- the charter document save (`charter_yaml_io`, preserving authored sections);
- compile (`write_compiled_charter`, refreshing only the derived `catalog` and `metadata`
  sections).

A fourth, non-YAML write exists: the context read itself records first-load state in
`.kittify/charter/context-state.json` (`--mark-loaded`), and its answer differs between a
first load and a later one. That state stays in core's seam, and the service takes a
`depth` integer request parameter that counts `suggests` hops (0 to 2), echoed in the response
([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/) and
[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/), provisional).

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
