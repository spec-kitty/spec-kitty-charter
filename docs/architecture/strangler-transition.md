---
title: Strangler transition
description: The stages that move charter reads, then writes, from core Python to the Java service, with the evidence for each and the point of no return.
doc_status: draft
updated: 2026-10-20T00:00:00Z
type: Architecture
kind: Explanation
related:
  - architecture/overview
  - architecture/contract-and-dtos
  - plans/roadmap
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
audience:
  - profile: core-maintainer
    guidance_text: Read this for the stages, the evidence each needs and the point of no return.
  - profile: charter-service-contributor
    guidance_text: Read this for what may be built in parallel and what waits for the seam.
---

# Strangler transition

The stages come from core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) ("Staged ownership"), accepted in core on 2026-10-18. The Java service is a rewrite that
replaces the Python charter, which stays in core as the oracle until its reads and writes
retire ([ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/)), so the
seam and the shadow comparison run in core and the service runs from this repository.

> **Status:** design only; this repository is at stage 0. The cutover scope, the merged stages 3
> and 4 and the corpus-only gate are provisional decisions of 2026-10-19
> ([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)). [Problem](/context/problem/) describes core's seam today. [What the labels mean](/#status).
>
> **Proposed** ([ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/)):
> core adopts a client generated from the published contract (0.1.0 first, [ADR-0022](/adr/0022-contract-versions-start-at-0-x/)) now, behind a proxy that routes
> each operation to core's Python, a shadow or the service. Stages 2 to 4 then happen one
> operation at a time, each switch fail closed.

## Stages

| Stage | Production reads | Production writes | Evidence to enter |
|---|---|---|---|
| 0. Current | Python | Python | — |
| 1. Callers on the seam | Python, behind the seam | Python, behind the seam | Callers outside `charter` import no charter internals; a ratchet holds the count at zero |
| 2. Read shadow | Python; Java compared out of band | Python | Contract and fixture equivalence |
| 3. Java authoritative | Java; loud, observable Python fallback | Python | Conformance is a blocking gate; freshness and failure behavior proven |
| 4. Java reads complete | Java only | Python | Fallback-retirement criteria met |
| **3 + 4, as decided** | **Java only; the seam fails closed** | Python | **The conformance corpus passes as a blocking gate** |
| 5. Write migration | Java | Python or Java, per operation | Codec identity, mapping identity, confined mutation, negative-fixture equivalence |
| 6. End state | Java | Java | Every write operation migrated |

"Java authoritative" means Java gives the production answer and Python answers only as a
fallback. The fallback must never silently choose a second answer.

**Stages 3 and 4 ship together** (provisional,
[ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)). There is no period in which Python
answers as a fallback: when the service is down or slow, the seam fails closed with a named
error ([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/)). The rows for
stages 3 and 4 stay above as the shape of the original plan.

## The 4.x cutover

The 4.x cutover is stage 4, with stage 3 folded into it. It is done when:

- every Charter Pack and offering read goes through this repository's API, called from
  core's seam;
- that includes the reads `charter.activation` makes of offering data, while activation's own
  state logic stays in core until its post-GA rework (core ADR
  [`2026-10-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-16-1-extract-charter-offering-first-activation-rework-post-ga.md));
- the Python offering read code in core is removed or inactive.

**Compile is the one exemption.** It keeps a Python read path, held by a ratchet, until it
becomes the first write operation migrated in stage 5. Core's ADR `2026-10-03-1` is to be
amended to govern only the seam routing; the offering internals, including the Charter Pack
structure, are decided in this repository.

## Retiring the fallback

Python reads retire only when all of these hold:

- the supported conformance corpus passes, as a blocking gate in core's CI;
- the behavior of a stale or unavailable service is proven by tests;
- cross-OS packaging is supported.

The original plan also asked for an observation period. That was dropped on 2026-10-19
(provisional, [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)): the corpus is the only
evidence for the switch-over, so every behavior that matters must be a corpus case.

## Stepping back

Stages 1 to 3 can each step back one stage. After stage 4, only a single write operation
can step back:

- **Callers on the seam:** move a caller back to its direct import.
- **Read shadow:** turn the comparison off; Python already answers.
- **Java authoritative:** switch the seam adapter back to Python reads. With stages 3 and 4
  shipped together, this step exists only until the release that removes the Python reads.
- **Write migration:** send one write operation back to Python while its writer exists.

**The point of no return is stage 4**, retiring the Python reads. Since stages 3 and 4 ship
together, the switch-over itself is that point. After it, going back means
restoring deleted code from a release. Retiring each Python writer repeats that choice for
its operation.

## Writes, one at a time

There is no single write adapter in core today; there are three write paths (activation
commit, charter document save, compile). The seam must front all three before any of them
moves. During migration one side holds the single write lock: the Python seam, until the
last operation migrates. Java mutates a file only through a handoff the seam grants for that
operation.

A write operation moves to Java only after three gates pass for it:

1. **Codec identity.** Lossless YAML load and emit is byte-identical, including comments,
   ordering, scalar styles, anchors, document markers and explicit null spellings.
2. **Mapping identity.** YAML maps into the domain and back into the same document without a
   business change, byte-identical.
3. **Confined mutation.** A deliberate domain change produces only its expected local diff.

Codec identity alone does not prove the domain took part, and mapping identity alone could
be faked by a document copy. Confined mutation is the witness that the semantic mapping
participated.

## Relation to the 4.0.0 gate

Stage 1 is the import-move precondition of core ADR [`2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) (D-2), and it gates 4.0.0
GA. It is done: spec-kitty#6188 moved the `specify_cli → charter` imports onto the offering
seam, as core ADR [`2026-10-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-16-1-extract-charter-offering-first-activation-rework-post-ga.md) (Accepted) records. That ADR's step P4
(spec-kitty#6288) still plans to move the Python offering into this repository; under
[ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/) it does not move, so
core is asked to amend P4 ([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)). Stages 2 onward do not gate GA: core
ADR `2026-10-03-1` places them in 4.x evolution. This repository can build the contract, the
domain and the conformance corpus in parallel with stage 1, but nothing here answers a
production read before stage 1 is done.

The tracker's **4.x cutover** milestone ends at stage 4: the Python offering reads are gone
from core, and every read goes through this repository's API from the seam. See
[Milestones](/plans/milestones/).
