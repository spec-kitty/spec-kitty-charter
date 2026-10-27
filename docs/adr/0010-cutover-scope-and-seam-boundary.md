---
title: "ADR-0010: The 4.x cutover retires every Python offering read in one switch-over; core governs only the seam routing"
description: All offering reads, including activation's, go through the API at once, gated by the conformance corpus; offering internals and pack structure are decided here.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - architecture/strangler-transition
  - plans/roadmap
audience:
  - profile: core-maintainer
    guidance_text: Read this for what the cutover removes from core, what stays, and the amendments asked of core.
  - profile: charter-service-contributor
    guidance_text: Read this for what must be true before the switch-over and what this repository now decides alone.
  - profile: governance-lead
    guidance_text: Read this for when the Python charter stops answering and what evidence allows it.
---

# ADR-0010: The 4.x cutover retires every Python offering read in one switch-over; core governs only the seam routing

## Status

**Accepted** — 2026-10-27 (provisional since 2026-10-19). Decider: Stijn Dejongh (operator).
This decision is ratified as of 2026-10-27; it is still revisited as the system evolves, and a later record
supersedes it rather than editing it.

## Context

The operator defined the **4.x cutover** on 2026-10-19: the charter offering's Python code in
core is removed or inactive, and every Charter Pack and offering read goes through this
repository's API, called from core's seam (`charter.offering.api`, the anti-corruption layer
at core's entry point).

Four facts shape what that means:

- **Two records disagree on where the Python goes.** This repository's
  [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/) (Accepted
  2026-10-18) says the Java service is a rewrite and the Python charter stays in core until
  it retires. Core ADR
  [`2026-10-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-16-1-extract-charter-offering-first-activation-rework-post-ga.md)
  (Accepted 2026-10-16) lists phase P4, core issue #6288: move `src/charter/offering/` into
  this repository, version-locked, for 4.0.0 (`2026-10-16-1`, lines 88-99).
- **The most-used reads are in activation.** Action-scoped context, the active charter and
  profile resolution live in `charter.activation`, which stays in core until its post-GA
  rework (`2026-10-16-1` D-2, lines 82-87). About 59 activation files import
  `charter.offering`.
- **Core's Python writers read the offering in-process.** Compile loads the CRG and presets
  (`src/charter/activation/compiler.py`, lines 54-56 and 1758-1761), and `pack_manager`
  reads the mission repositories (`src/charter/activation/pack_manager.py`, lines 95-102).
- **The import move is done.** Core issue #6188 is closed, so stage 1 of the
  [strangler](/architecture/strangler-transition/) is reached. Core ADR
  [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md)
  still frames the later stages, including a stage 3 in which Java answers with a Python
  fallback, and an observation period before stage 4.

## Decision

1. **ADR-0008 stands.** Nothing Python moves to this repository. The Python charter stays in
   core behind the seam until its reads are retired. Core is asked to amend P4 (below).
2. **Cutover scope.** At the cutover, every read of Charter Pack or offering data goes
   through this repository's API:
   - the offering's own read paths;
   - the reads that `charter.activation` makes of offering data. Activation's own state
     logic stays in core.
3. **Compile is the one exemption.** Compile keeps a Python read path for its inputs. A
   ratchet test in core stops any other caller from using it. It retires when compile
   becomes the first write migrated to Java (stage 5).
4. **Stages 3 and 4 ship together.** There is no period in which Java answers with Python
   behind it. The release that routes reads to Java also stops calling Python for reads
   ([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/): fail closed).
5. **The conformance corpus alone gates the switch-over.** A divergence on the supported
   corpus fails core's CI (#72). There is no observation period (#75 is dropped).
6. **The split of authority.** Core ADR `2026-10-03-1` governs only the seam routing: the
   stages, the switch at the seam, and what the seam does on failure. Everything inside the
   offering is decided here: the domain model, resolution and merge rules, the contract, and
   the Charter Pack structure (`pack.yaml`, file layout, descriptors).

## Consequences

### Positive

- One switch-over and one rule: after it, core answers no offering read in Python.
- The Python offering can be deleted, apart from compile's ratcheted inputs.
- Pack structure changes no longer need a core ADR. They are decided where the contract and
  its oracle are.

### Negative

- The corpus is the only evidence. Behavior that no corpus case pins can change at the
  switch-over unnoticed.
- No soft landing: a service defect after the switch-over is a user-facing outage until it is
  fixed or the release is rolled back.
- The strangler spans two repositories: the seam and the ratchet live in core, the service
  here.

### Risks

- Corpus coverage gaps. Mitigation: the corpus covers every contract operation and every
  refusal code before the gate turns blocking, and acceptance scenarios (#31) run against
  both implementations.
- Rollback after the switch-over means a release downgrade, or restoring deleted code.
  Mitigation: delete the Python read code in the release after the switch-over, not the same
  one.
- Compile's exemption outlives its purpose. Mitigation: the ratchet counts the callers, and
  stage 5 starts with compile.

## Alternatives considered

### Follow core's P4: move the Python offering here first

Not chosen: it packages Python whose only future is retirement, and it needs `kernel` from
somewhere. ADR-0008 already weighed it.

### Retire only `src/charter/offering/` read paths

Not chosen: activation and the writers would keep reading the Python offering, so the code
could never be deleted, and two implementations would answer "what exists".

### Everything through the API, writers included

Not chosen for now: every write path changes before stage 5, and writes fail whenever the
service is down.

### A stage 3 with a Python fallback and an observation period

Not chosen: it keeps two answers alive and delays retirement. The operator prefers one
switch-over, gated by the corpus.

## Revisit when

- A corpus gap causes a divergence that users see after the switch-over.
- Core's activation rework (post-GA) starts. The line between activation state and offering
  data is then redrawn.
- Compile migrates to Java (stage 5). The exemption then ends.
- A hosted or shared service (#24, #44) changes who runs the service.

## Requests to core

- **Amend `2026-10-16-1` D-3.** Replace P4 (#6288, "extract `src/charter/offering/` to
  `spec-kitty-charter`") with: "The offering stays in core behind `charter.offering.api` and
  is replaced in place by the spec-kitty-charter service (spec-kitty-charter ADR-0008). The
  4.0.0 leg is a frozen seam and a green conformance corpus, not an extraction."
- **Amend `2026-10-03-1`.** Proposed text: "This record governs the seam routing only: the
  stages, the switch at `charter.offering.api`, and the seam's failure behavior. The
  offering's internals, its contract and the Charter Pack structure are decided in
  spec-kitty-charter. Stage 1 is reached (#6188). Stages 3 and 4 ship together, with no
  Python read fallback, and the conformance corpus is the only gate. Compile keeps a ratcheted
  Python read path until it is the first write migrated."
- **A ratchet** that fails when any caller other than compile reads the Python offering after
  the switch-over.

## References

- [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/): rewrite and
  replace
- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/): what contract
  1.0 holds
- [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/): the seam at runtime
- [Strangler transition](/architecture/strangler-transition/), [Roadmap](/plans/roadmap/)
- Epic #69 and issues #72 and #76
