---
title: "ADR-0001: The charter is a Java service built against an OpenAPI contract"
description: The charter's new implementation is a Java service; an OpenAPI contract is the single source of the DTOs that the service and the core's runtime (CLI, next state machine) both use.
doc_status: active
updated: 2026-10-17
type: ADR
kind: ADR
related:
  - adr/0002-contract-source-of-record
  - architecture/contract-and-dtos
  - architecture/strangler-transition
audience:
  - profile: core-maintainer
    guidance_text: "Read this to see what core is asked to ratify: a Java target behind the existing Python seam."
  - profile: charter-service-contributor
    guidance_text: Read this for the stack, the contract-first rule and the Python-first question that bound your work.
---

# ADR-0001: The charter is a Java service built against an OpenAPI contract

## Status

**Accepted** — 2026-10-13. Decider: Stijn Dejongh (operator), in the project discussion of
how the charter, the event router and the product direction fit together.

**Amended 2026-10-17 (clarification; the decision is unchanged).** The behaviour-first
bootstrap recorded in [the overhaul approach](/plans/overhaul-approach/) sequences the work so
that the charter offering's role and boundary are specified as acceptance scenarios, then
published as the OpenAPI contract, *before* an implementation is built. This does not reopen the
decision below: the target implementation remains a Java service. What stays open is only the
*migration sequencing* of decision 3 — whether the Python charter first moves into this
repository as an interim package or is replaced in place
([open questions](/plans/open-questions/), question 2). Epic
[#29](https://github.com/spec-kitty/spec-kitty-charter/issues/29) tracks the behaviour-first
phases that precede and constrain the Java implementation tracked by epic
[#6](https://github.com/spec-kitty/spec-kitty-charter/issues/6).

## Context

Core ADR `2026-10-12-2` moves the charter into this repository as an external contract
package, consumed only through `charter.offering.api`. It does not say what language the
extracted charter is in, and this repository's first README described a Python package.
D-4 of that ADR explicitly scopes it to packaging and repository topology and keeps the
runtime service a separate track. This record joins the two tracks in one repository, which
core must ratify.

Core ADR `2026-10-03-1` (still Proposed in core) describes a different end state: a
per-worktree Java service that takes over charter reads, then writes, behind a Python seam.
Read together, the two records left open whether this repository is the permanent Python
home of the charter or the home of its Java replacement.

The operator settled it: the charter's target implementation is Java, with an OpenAPI spec ingested in the
core, used to generate DTOs that runtime execution (the CLI, the state machine) uses.

## Decision

1. **The charter's target implementation is a Java service**, built in this repository. It
   follows core ADR `2026-10-03-1`: hexagonal, a plain-Java domain, separate read and write
   applications, REST and later MCP adapters, lossless YAML adapters, one service per
   worktree, loopback only. Stack as the Mission Status service: Java 25, Spring Boot 4,
   Gradle.
2. **Contract first.** An OpenAPI 3.1 contract is the single source of every charter
   transport DTO. The service generates its server interfaces from it; the core ingests it
   and generates the DTOs its seam, CLI and runtime use. No charter response shape is
   hand-written on either side.
3. **Python remains the seam and, for now, the implementation.** `charter.offering.api`
   becomes the only entry point for CLI callers. The Python charter keeps answering until
   the strangler stages move each read, then each write, to Java. Whether that Python code
   first moves into this repository as a package (the likely path) or stays in core until
   Java replaces it is not decided by this record
   ([open questions](/plans/open-questions/), question 2).
4. **This repository adopts core ADR `2026-10-03-1` as its plan** for stages, gates and
   service boundary, and records any divergence from it as its own ADR (the first is
   [ADR-0002](/adr/0002-contract-source-of-record/)).

## Consequences

### Positive

- One reading of the new repository: it is the Java charter and its contract, not a
  permanent Python package.
- The same contract serves the CLI, agent harnesses (MCP) and any future consumer.
- Two languages are held to one answer by a contract and a conformance corpus instead of
  shared code.

### Negative

- Two implementations coexist until the Python reads, then writes, retire. Conformance must
  be a blocking gate, not a report.
- A per-OS service artifact adds release, signing and cross-OS CI cost.
- Lossless YAML writes in Java are materially harder than semantic parsing.

### Risks

- If the Python charter moves here first, this repository carries two implementations
  for the whole strangler and needs a Python build beside the Gradle one. If instead it is
  rewritten and replaced, core ADR `2026-10-12-2` D-3 (the charter *package* moves here)
  needs an amendment so the two records agree.
- Core ADR `2026-10-03-1` is still Proposed. If it changes before acceptance, this record's
  point 4 follows it.

## Alternatives considered

### Option A: Python package as the permanent end state

Release the existing Python code from this repository as a wheel, and stop there. Rejected
as the end state only, by the operator's direction: it gives the charter a repository but
keeps the per-process cold read and offers no path to a shared REST/MCP authority.
Releasing the Python charter from this repository as an interim step remains the likely
path (decision 3) and is what core ADR `2026-10-12-2` D-3 and epic spec-kitty#6099 require
for 4.0.0 GA.

### Option B: Python compiler, Java projection server

Python keeps compiling charter meaning and Java only serves the projection. Rejected in core
ADR `2026-10-03-1`: Java would depend on Python forever and the final distribution would
carry two runtimes.

## References

- Core ADR `4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md`
- Core ADR `4.x/2026-10-03-1-charter-read-write-service-strangler.md`
- Core ADR `4.x/2026-10-01-2-mission-status-read-api-and-dashboard-extraction.md`
- spec-kitty#6099, and spec-kitty#645 (closed as not planned on 2026-10-13; it tracked the
  import-move precondition)
