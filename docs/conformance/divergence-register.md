---
title: Divergence register
description: The single register of every deliberate difference between the charter service's answers and core's, each with a stable id and the corpus case that pins it.
doc_status: draft
updated: 2026-10-19T00:00:00Z
type: API
kind: Reference
related:
  - adr/0016-conformance-corpus-harness-over-core-cli
  - adr/0018-action-context-read-and-seam-access-surface
  - api/service-contract
audience:
  - profile: contract-consumer
    guidance_text: Read this to know where the service answers differently from core on purpose, and how the seam maps each difference.
  - profile: charter-service-contributor
    guidance_text: Read this before changing a walk rule, and add an entry here when an answer differs from core on purpose.
---

# Divergence register

This is the single register that [ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/)
decision 4 means (operator ruling R10, 2026-10-19). It lists where the service answers
differently from core's Python charter on purpose, and why. Every such difference is reviewed
and recorded here, never hidden in a hand-edited expected answer. The conformance gate (#72)
consults this register. Its format stays #72's. Every entry has a stable id, and ids are never
reused.

Entries are grouped by block, one block per mission, so that two missions never share an id:

- **Context read (DIV-101..110, ADR-0018)** is below.
- **Pack schemas (DIV-001..019)** is added by the pack-schema mission (runner B).
- The machine-readable `divergences.yaml` comes with that mission.

The first three entries of the context-read block lived in the provisional section of
[`contracts/charter/CHANGELOG.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md).
That section still links them. They were decided in [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/).

## Context read (DIV-101..110, ADR-0018)

| Id | The charter answers | Core answers | Corpus case |
|---|---|---|---|
| DIV-101 | 404 `action_not_found` for an action that is not a graph node | its non-bootstrap context | `action-not-a-node` |
| DIV-102 | a scope target the graph does not declare, with a null label | skips it (and R9's filter drops such edges) | `undeclared-scope-target` |
| DIV-103 | a load error refused (500, pack code) | an empty answer with a warning | `unreadable-pack` |
| DIV-104 | withdrawn, see below | | |
| DIV-105 | an ordered answer with `relation`, `via`, `hops` and `diagnostics` | unordered sets | every case (mapping) |
| DIV-106 | vocabulary targets as entries with relation `vocabulary`; the seam drops them from delivery | computes `glossary_scopes` and discards it | `vocabulary-walk` |
| DIV-107 | the offering's walk only; activation, selection roots, the language filter and the directive allowlist are the effective-context operation's (R9, #103) | one filtered, selected answer | `activated-project`, `selected-roots`, `language-filtered`, `directive-allowlist` |
| DIV-108 | no tension annotation until the creed set (#16, #77) | an annotation over `in_tension_with` pairs | `tension-pair` |
| DIV-109 | every walked kind, including paradigm, template and agent profile | delivery slots exclude those kinds (seam-side) | `undelivered-kind` |
| DIV-110 | the hard dependencies (`requires`, transitive) of suggested artifacts are listed | a suggested artifact without its `requires` | `suggested-artifact-requires` |

**DIV-104 (withdrawn).** It registered walking `scope` from the mission-type node. The plan-point
squad found that core documents those edges as the entry point of the activation cascade, not of
this walk. Walking them adds 30 to 49 artifacts on `plan`, `documentation` and `research`
actions against core's 0 to 14. The service therefore walks scope from the action node only, and
no divergence remains.

**DIV-110 and why it is kept.** Core's own docstring calls `requires` "hard dependencies", and its
selection closure follows them, yet its walk lists a suggested artifact without them. The
operator ruled on 2026-10-19 to keep the difference. Suggestions of required artifacts are
**not** followed, as in core. The walk evidence (72 cells over open-packs) is 65 identical and 7
differing only by DIV-110; core minus charter is empty in every cell.

## Corpus cases named for the harness (#9)

- **Every mission type and action** at depths 0, 1 and 2, against `charter context --json` with and
  without `--mark-loaded`, through the seam's mapping. Cases without activation keys match core
  exactly.
- **Walk cases:** `requires-chain`, `requires-cycle`, `suggests-two-hops`, `suggests-diamond` (a
  node reachable at 1 and at 2 hops whose child must appear at depth 2),
  `suggested-artifact-requires` (listed, DIV-110), `required-artifact-suggests` (not listed, as in
  core), `other-action-owned-via-requires` (core includes it) and `plan-actions-empty` (`plan/*`,
  which core answers empty).
- **The public bundle read:** `public-bundle-depth-0`.
- **The register cases** in the table above.

## Activated cases are compared two ways

Without activation keys the filter is the identity, so those cases must match core exactly and
need no entry here. For an activated case the harness asserts both sides (ADR-0018 D5):

1. charter = core run on the same fixture without activation keys;
2. core run on the activated fixture is a subset of the charter's answer.

The seam does not switch its context read to the service until R9's effective-context operation
exists (cutover stage 3).

## Adding an entry

Give it the next free id in its block, state both answers, name the corpus case, and cite the ruling. Link the
entry from the contract scenario that pins it.
