---
title: "ADR-0033: The project's local activation file is the source of truth for its active charter"
description: Local activation.yaml is written through to the service, wins visibly on a cold-start divergence, names pack ids and versions; upgrades are opt-in per project.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0013-persistent-deactivation
  - adr/0020-offering-and-activations
  - adr/0034-local-and-hosted-deployment-options
  - context/conceptual-model
audience:
  - profile: individual-developer
    guidance_text: Read this for where your project's active charter lives and what happens when a pack you use gets a new version.
  - profile: governance-lead
    guidance_text: Read this for why a hosted service never changes a project's charter on its own, and how upgrades are acknowledged.
  - profile: core-maintainer
    guidance_text: Read this for the write-through, the cold-start check and the upgrade notice core shows.
---

# ADR-0033: The project's local activation file is the source of truth for its active charter

## Status

**Accepted** — 2026-10-26. Decider: Stijn Dejongh (operator), concept session of 2026-10-26
("Where the active charter lives", "One active charter, resolved per activation context" and
"Upgrades" in the [conceptual model working notes](/context/conceptual-model/)).

Supersedes in part:

- [ADR-0013](/adr/0013-persistent-deactivation/) decision 1: the "one activation ledger is the
  single authority" wording. The local file is the source of truth, and the service holds a
  written-through copy.
- [ADR-0020](/adr/0020-offering-and-activations/) decision 5 ("the service wins" on a divergence).

## Context

ADR-0013 made one activation ledger file in the project the single authority. ADR-0020 then moved
the authority to the service once activation routes there: on a divergence the service wins and
core fetches again; and an empty document puts every kind at its default, so the whole offering is
active.

The concept session reversed both points. The active charter is the project's plate: what it
chose. The project owns it, and a hosted service must never change it on its own.

## Decision

1. **The local `activation.yaml` is the source of truth.** It acts as a write-through cache: a
   change to it results in a call to the service. The service-side copy is what the activation and
   resolution answers come from.
2. **Cold start.** On a cold start the local file is checked against the service's copy, or used
   to register it.
3. **Local wins, visibly.** When the two disagree on a cold start, the CLI shows the difference and
   warns before it re-registers the local file. The hosted side never changes a project's charter
   on its own: an admin proposes, and the project's update is the acknowledgement.
4. **One active charter per project.** There is no separate per-Mission charter. "For this
   Mission, also include X" is done by the client, in the active charter it registered or in the
   activation context it passes; the service resolves the active charter against that context.
5. **The active charter is the plate, not the buffet.** It holds what the project chose plus what
   is required ([ADR-0031](/adr/0031-requirement-is-authored-as-a-preset/)). What an *empty*
   active charter means is **not decided here**: ADR-0020 decision 6 (each kind at its default)
   stands until the built-in pack is split down to a minimal offering (epic
   [#145](https://github.com/spec-kitty/spec-kitty-charter/issues/145)), when the operator revisits
   it. See Open questions.
6. **It names the offering packs it builds on, by id and version.**
7. **Upgrades are opt-in per project.** The service holds several versions of a pack side by side.
   A project moves when it re-registers or updates its active charter, and that update is the
   acknowledgement. At that moment newly required or locked items apply, items that no longer
   exist are reported as skipped and never dropped silently, and deactivations persist. An upgrade
   never writes a project's active charter.
8. **Outdated packs get a notice.** A project on an outdated pack version gets a notice with every
   activation answer that a newer version is available.
9. **Representation is a candidate only.** The register and resolution operations, and where the
   notice appears in an answer, are left to later records. No contract path changes (C-003).

## Open questions

- **Empty active charter.** Whether an empty active charter keeps ADR-0020 decision 6 (each kind at
  its default) or holds only what is required. Deferred by the operator to the built-in split
  (#145).

## Consequences

### Positive

- The project owns its choices, and a change it did not make cannot appear in it.
- Offline work keeps a usable charter.
- An admin can see which projects are out of date without changing them.

### Negative

- Two copies exist, and the cold-start check must be built in core and the service.
- Projects that never update stay on old pack versions; the notice is the only push.

### Risks

- A project re-registers a stale local file over an admin's proposal. Mitigation: the visible
  difference and the warning before re-registering.

## Alternatives considered

### The service is the authority (ADR-0020 D5)

Not chosen: a hosted service could then change a project's charter without the project acting.

### Upgrades apply automatically

Not chosen: a project would get new required or locked items without acknowledging them.

## References

- [Conceptual model working notes](/context/conceptual-model/)
- [ADR-0013](/adr/0013-persistent-deactivation/), [ADR-0020](/adr/0020-offering-and-activations/),
  [ADR-0031](/adr/0031-requirement-is-authored-as-a-preset/),
  [ADR-0034](/adr/0034-local-and-hosted-deployment-options/)
