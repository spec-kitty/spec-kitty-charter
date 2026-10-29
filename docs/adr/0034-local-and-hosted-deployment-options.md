---
title: "ADR-0034: The charter service has a local and a hosted deployment option"
description: Local is spawned by the CLI and works offline; hosted uses a configured endpoint and a standard login token and fails closed; records the open-core boundary.
doc_status: active
updated: 2026-10-28T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - adr/0020-offering-and-activations
  - adr/0033-local-active-charter-is-source-of-truth
  - adr/0041-containerized-configuration-model
  - context/conceptual-model
audience:
  - profile: governance-lead
    guidance_text: Read this for how a team hosts the charter service centrally, and what is open-core and what is commercial.
  - profile: core-maintainer
    guidance_text: Read this for the amendment of core ADR 2026-10-03-1 that core owes, and why a hosted endpoint is not a removed hosted surface.
  - profile: individual-developer
    guidance_text: Read this for what you see when you work offline or when a hosted service is unreachable.
---

# ADR-0034: The charter service has a local and a hosted deployment option

## Status

**Accepted** — 2026-10-26. Decider: Stijn Dejongh (operator), concept session of 2026-10-26
("Deployment: local and hosted" and "Guiding principles" in the
[conceptual model working notes](/context/conceptual-model/)).

Supersedes in part [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/) decision
2: loopback is the local option, not the only one; a configured remote endpoint is allowed.

Records the amendment of core ADR `4.x/2026-10-03-1` that core owes. This record does not change
core.

Clarification 2026-10-28: decision 5 is resolved in part by [ADR-0041](/adr/0041-containerized-configuration-model/) (2026-10-29) for the configuration keys of the containerized service. The login flow and the token format stay open.

## Context

ADR-0011 bound the service to one CLI-started daemon per worktree, on loopback only. ADR-0020 D12
already shaped the contract hosted-first (bearer scopes, a local deployment profile). Core ADR
`4.x/2026-10-03-1` still describes a CLI-started, loopback, per-worktree daemon. Core also removed
its hosted surfaces in spec-kitty#6164 and asks that they not return.

A team lead wants to host the charter service centrally instead of each member running it locally.

## Decision

1. **One service, two deployment options.**
   - **Local**: the CLI spawns a local service and connects to it. It keeps working offline.
   - **Hosted**: the CLI is configured with a remote endpoint and a standard login token. When the
     host is unreachable, it fails closed with a clear error (ADR-0011 decision 1 still holds).
2. **A core client calling a self-hosted charter service is allowed.** It is a generic client for
   an open-source service, not a return of the hosted surfaces removed in spec-kitty#6164.
3. **Open-core boundary.**
   - **Open-core**: the service, the local option, and self-hosting for one team with any standard
     login provider.
   - **Commercial**: managed hosting, multiple tenants, the cross-project "who is out of date"
     admin view and org reporting. Cross-repository policing is out of scope for open-core.
4. **The amendment core owes.** Core ADR `4.x/2026-10-03-1` should be amended so that loopback is
   the local option and a configured remote endpoint is allowed. Proposed text: "Amendment: the
   charter service has two deployment options (spec-kitty-charter ADR-0034). Local: the CLI starts
   it on loopback, per worktree, as before. Hosted: the CLI calls a configured endpoint with a
   standard login token and fails closed when it is unreachable." Core makes this change in its own
   repository; this record does not.
5. **Representation is a candidate only.** Configuration keys, the login flow and the token format
   are left to later records. No contract path changes (C-003).
   *(Clarification 2026-10-28: the configuration keys are set by ADR-0041.)*

## Consequences

### Positive

- A team shares one charter service and one set of pack versions.
- The local option keeps the offline, single-developer case unchanged.
- The open-core line is written down before a hosted service is built.

### Negative

- Core's ADR and its seam disagree with this record until core amends them.
- The hosted option needs a login integration the local one does not.

### Risks

- A hosted endpoint is read as a return of core's removed hosted surfaces. Mitigation: decision 2,
  and the amendment text above.

## Alternatives considered

### Loopback only (ADR-0011)

Not chosen: it rules out a team hosting the service.

### Hosted only

Not chosen: it breaks offline work and the single-developer case.

## References

- [Conceptual model working notes](/context/conceptual-model/)
- [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/),
  [ADR-0020](/adr/0020-offering-and-activations/),
  [ADR-0033](/adr/0033-local-active-charter-is-source-of-truth/)
- Core ADR [`4.x/2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md)
