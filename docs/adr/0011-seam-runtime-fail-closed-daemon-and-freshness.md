---
title: "ADR-0011: At runtime the seam fails closed, a daemon serves every read, and the service stays fresh by fingerprint and push"
description: No Python fallback or cache; one daemon per worktree, CI included; first-load state stays in core; the service rebuilds on a changed input fingerprint and on write.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0010-cutover-scope-and-seam-boundary
  - architecture/service-lifecycle
  - architecture/strangler-transition
  - architecture/resolution-model
audience:
  - profile: core-maintainer
    guidance_text: Read this for what the seam does when the service fails, how the launcher works, and the trust model it reopens.
  - profile: charter-service-contributor
    guidance_text: Read this for the depth parameter, the freshness rule and the stale_input refusal.
  - profile: individual-developer
    guidance_text: Read this for what you see when the charter service is down.
---

# ADR-0011: At runtime the seam fails closed, a daemon serves every read, and the service stays fresh by fingerprint and push

## Status

**Accepted (provisional)** — 2026-10-19. Decider: Stijn Dejongh (operator).
This decision is provisional: it is revisited as the system evolves, and a later record
supersedes it rather than editing it.

Amended 2026-10-19 by [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/): see
[Amendment](#amendment-2026-10-19).

Superseded in part by [ADR-0034](/adr/0034-local-and-hosted-deployment-options/) (2026-10-26): loopback is the local deployment option, and a configured remote endpoint is allowed (decision 2).

Superseded in part by [ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/) (2026-10-27): packs are discovered under one configuration root, not configured, so a missing pack directory is simply not served; the fail-closed refusal for a configured pack whose directory is missing no longer has a case (decision 4).

## Context

After the cutover every offering read crosses a process boundary
([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)). Four runtime questions follow.

- **Failure.** The seam has no remote adapter yet. Core's `charter.offering.api` is "an
  in-process Python contract" (`src/charter/offering/api.py`, lines 36-39), so the timeout,
  the failure behavior and the handshake are all still to be built. Fail-loud is already a
  domain rule ([Resolution model](/architecture/resolution-model/)). A refusal from the
  service, such as `pack_level_mismatch`, is an answer. Only a transport failure is a
  failure.
- **Launcher** (open question 8). A cold JVM start costs about 1 to 2 s, a GraalVM native
  image about 50 to 200 ms. Core's Mission Status service is native and *detached*: no
  spec-kitty command starts it. Core rejected an option for it because it "put a daemon
  *inside the CLI*" (core ADR
  [`2026-10-01-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-01-2-mission-status-read-api-and-dashboard-extraction.md),
  lines 250-254), after the dashboard daemon's PID-recycling and token-leak defects. The
  charter service cannot be detached: after the cutover every read needs it.
- **First-load state** (open question 12). The context read records a first load in
  `.kittify/charter/context-state.json` and answers deeper the first time
  (`src/charter/activation/context_state.py`, lines 40 and 89-110). The depth also caps how
  far the graph walk goes. That code lives in activation, which stays in core.
- **Freshness.** The service reloads every pack on each request, but fixes pack locations
  when it starts (`CharterProperties.java`, `CharterWiring.java`). An edit to
  `.kittify/config.yaml` goes unseen. Users also edit packs by hand and switch branches.
  Core's writers already report every write through `kernel.atomic.observe_writes`
  (`src/kernel/atomic.py`, lines 51-96).

## Decision

1. **Fail closed, always.**
   - A refusal from the service is the answer. The seam passes it on.
   - A transport failure (no service, timeout, wrong contract version, wrong worktree) is a
     named error with a remedy, and the command exits non-zero.
   - There is no Python fallback and no cached last-good answer.
2. **One daemon per worktree serves every read, CI included.**
   - The launcher in core starts it on first use and stops it after an idle period or when
     the worktree goes away.
   - It binds to loopback only, and every request carries a per-worktree token.
     *Superseded in part by ADR-0034: loopback is the local option; a hosted option uses a configured endpoint and a standard login token.*
   - The seam receives only an endpoint and a credential, so it does not depend on how the
     service was started.
   - **Trust model.** This puts a CLI-started daemon back in the trust model core rejected
     for the dashboard. It is scoped narrowly: one worktree, loopback, a token readable only
     by the user, read operations only, no browser-facing surface, and an identity check
     against the info operation before every session. The launcher records the process
     identity, not only the PID, so a recycled PID is never mistaken for the service.
     *"Read operations only" is reworded by Amendment 2026-10-19.*
3. **First-load state stays in core's seam.** The seam decides the load depth and passes it
   as the integer `depth` parameter. The service stays a pure read model: its answer is a
   function of its inputs and the depth.
4. **Freshness by fingerprint and push, from the start.**
   - The service reads pack locations from `config.yaml`, not from its launch settings.
   - It keeps a built graph and a fingerprint of its inputs: `config.yaml`, `charter.yaml`
     and every configured pack root. Each request checks the fingerprint. On a change the
     service rebuilds atomically before it answers.
     *The input list is superseded by Amendment 2026-10-19.*
   - After each write, the seam sends the written paths (from `observe_writes`), and the
     service rebuilds.
   - If inputs change during a read, or a rebuild fails, the service refuses with
     `stale_input`.
   - Every answer carries the `Charter-Contract-Version`, `Charter-Worktree` and
     `Charter-Source-Fingerprint` headers. The seam accepts only the contract `major.minor`
     it pinned.
     *"Every answer carries" the fingerprint is superseded by Amendment 2026-10-19: it is on
     200 answers only.*

## Consequences

### Positive

- One answer, always: no path gives a second, older or Python answer.
- Warm reads from the start, and hand edits and branch switches are caught by the fingerprint.
- The service stays a pure read model, which keeps the conformance comparison simple.

### Negative

- A service outage blocks every charter read until the daemon restarts.
- CI must start and stop a daemon. Its runners need the launcher too.
- Two invalidation paths (fingerprint and push) to build and test before any benchmark asks
  for them.

### Risks

- The daemon reopens a trust model core closed once. Mitigation: the scoping above, and a
  security review of the launcher before the cutover.
- The fingerprint misses a change (for example, mtime resolution on some filesystems).
  Mitigation: include size and a content hash for small files; the push after writes covers
  core's own changes.
- A native image is not ready in time (open question 5). Mitigation: a JVM daemon is slower to
  start but still serves warm reads.

## Alternatives considered

### Loud Python fallback in stage 3, with a switch

Not chosen: it keeps a second answer alive, and [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)
ships stages 3 and 4 together.

### Serve a cached last-good answer

Not chosen: it is the quiet wrong answer the fail-loud rule forbids.

### One process per command, or two launcher modes

Not chosen: per-command pays the cold start on every agent step, and two modes double the
paths to test.

### Move first-load state into the service

Not chosen: the read would no longer be pure, and the service would write a file the Python
seam also owns.

### Reload per request, or a file watcher

Reload per request was the cheapest start, but the operator chose warm reads from the start.
A file watcher is platform-specific and misses events on network filesystems.

## Revisit when

- A benchmark shows per-command native start is fast enough that the daemon is not worth its
  trust cost.
- Users report outages that a fallback would have hidden, and the cost of failing closed
  proves too high.
- A hosted or shared service (#24, #44) replaces the per-worktree daemon.
- A write path moves to Java (stage 5). The write handoff must then invalidate the graph
  explicitly.

## Requests to core

- **Launcher** (#70): a launcher in `specify_cli` with the scoping above. Proposed text for
  core ADR `2026-10-03-1`, "Service boundary": "The CLI starts one charter service per
  worktree on first use, on loopback with a per-worktree token, and stops it on idle or
  worktree removal. This is a CLI-started daemon; it is limited to read operations and has no
  browser-facing surface."
  *"Limited to read operations" is reworded by Amendment 2026-10-19.*
- **Seam failure behavior** (#73): no fallback; transport failures become named errors.
- **Write push:** after each charter write, the seam sends the paths from `observe_writes`
  to the service.

## References

- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/): the reserved
  `depth`, `stale_input`, info operation and headers
- [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/): the cutover scope
- [Service lifecycle](/architecture/service-lifecycle/),
  [Strangler transition](/architecture/strangler-transition/)
- Issues #70, #71 and #73

## Amendment (2026-10-19)

[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/) supersedes three points of
decisions 2 and 4. The original text above is unchanged.

- **Decision 2, "read operations only".** No operation changes governance or anything on disk.
  `POST /packs/refresh` only tells the service to rebuild its read model, and every read after it
  answers what the fingerprint check alone would answer (ADR-0018 D10).
- **Decision 4, "every answer carries" the three headers.** `Charter-Source-Fingerprint` is on
  200 answers only; no refusal carries it. The other two headers stay on every answer, the 401
  and 403 included (D11).
- **Decision 4, the fingerprint input list.** The inputs are every loaded pack file plus
  `config.yaml` and `charter.yaml` under the configured project directory (D8).

The token, its source and the browser refusal are specified in ADR-0018 D9. The built graph and
reading pack locations from `config.yaml` are not superseded and stay with #73.
