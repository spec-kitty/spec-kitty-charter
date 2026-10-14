---
title: Strangler transition
description: The stages that move charter reads, then writes, from core Python to the Java service, with the evidence for each and the point of no return.
doc_status: draft
updated: 2026-10-14
type: Architecture
kind: Explanation
related:
  - architecture/overview
  - architecture/contract-and-dtos
  - plans/roadmap
audience:
  - profile: core-maintainer
    guidance_text: Read this for the stages, the evidence each needs and the point of no return.
  - profile: charter-service-contributor
    guidance_text: Read this for what may be built in parallel and what waits for the seam.
---

# Strangler transition

The stages come from core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) ("Staged ownership"). It is still **Proposed**
in core; this repository treats it as the plan, and [open questions](/plans/open-questions/)
tracks its acceptance. The stages are written for either answer to
[question 2](/plans/open-questions/).

> **Status:** design only; this repository is at stage 0. [Problem](/context/problem/) describes core's seam today. [What the labels mean](/#status).

## Stages

| Stage | Production reads | Production writes | Evidence to enter |
|---|---|---|---|
| 0. Current | Python | Python | — |
| 1. Callers on the seam | Python, behind the seam | Python, behind the seam | Callers outside `charter` import no charter internals; a ratchet holds the count at zero |
| 2. Read shadow | Python; Java compared out of band | Python | Contract and fixture equivalence |
| 3. Java authoritative | Java; loud, observable Python fallback | Python | Conformance is a blocking gate; freshness and failure behavior proven |
| 4. Java reads complete | Java only | Python | Fallback-retirement criteria met |
| 5. Write migration | Java | Python or Java, per operation | Codec identity, mapping identity, confined mutation, negative-fixture equivalence |
| 6. End state | Java | Java | Every write operation migrated |

"Java authoritative" means Java gives the production answer and Python answers only as a
fallback. The fallback must never silently choose a second answer.

## Retiring the fallback

Python reads retire only when all of these hold:

- the supported conformance corpus passes;
- the behavior of a stale or unavailable service is proven by tests;
- cross-OS packaging is supported;
- an agreed observation period finds no unresolved semantic divergence.

## Stepping back

Stages 1 to 3 can each step back one stage. After stage 4, only a single write operation
can step back:

- **Callers on the seam:** move a caller back to its direct import.
- **Read shadow:** turn the comparison off; Python already answers.
- **Java authoritative:** switch the seam adapter back to Python reads.
- **Write migration:** send one write operation back to Python while its writer exists.

**The point of no return is stage 4**, retiring the Python reads. After it, going back means
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
GA. It was tracked as spec-kitty#645 until that issue was closed as not planned on
2026-10-13; the import move is now in the scope of epic spec-kitty#6099, which still names spec-kitty#645
as its precondition, so core has to re-home it. Stages 2 onward do not gate GA: core
ADR `2026-10-03-1` places them in 4.x evolution. This repository can build the contract, the
domain and the conformance corpus in parallel with stage 1, but nothing here answers a
production read before stage 1 is done.
