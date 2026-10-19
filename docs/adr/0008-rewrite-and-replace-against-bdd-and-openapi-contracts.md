---
title: "ADR-0008: The Java charter is a rewrite that replaces the Python charter, built against BDD and OpenAPI contracts"
description: The Python charter stays in core and is replaced stage by stage; the Java service is written against BDD and OpenAPI contracts and must round-trip open-packs.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0001-java-service-with-openapi-contract
  - adr/0002-contract-source-of-record
  - plans/open-questions
  - plans/roadmap
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what the service is built against and the round-trip it must pass.
  - profile: core-maintainer
    guidance_text: Read this for why the Python charter does not move here and stays the oracle in core.
---

# ADR-0008: The Java charter is a rewrite that replaces the Python charter, built against BDD and OpenAPI contracts

## Status

**Accepted** — 2026-10-18. Decider: Stijn Dejongh (operator). Answers
[open question 2](/plans/open-questions/).

Clarification 2026-10-19 (terminology; the decision is unchanged): "BDD" names the
practice. In Decision 2, "BDD scenario files" and "the Gherkin scenarios" are the **contract
scenarios** in `contracts/charter/scenarios/`; the copied acceptance suite in
`tests/scenarios/charter/` is the **acceptance scenarios**
([Behavior and acceptance scenarios](/architecture/behavior-and-scenarios/#where-they-live)).
The title keeps its wording because it names the accepted record.

## Context

Two routes were open for getting from the Python charter in core to a Java service here:
move the Python charter into this repository first and strangle it here, or write the Java
service fresh and replace the Python charter in core, so the Python code never moves. The
first route packages Python that will later retire; the second runs the strangler across two
repositories.

## Decision

1. **Rewrite and replace.** The Java service is written fresh in this repository. The Python
   charter stays in core, behind `charter.offering.api`, and is replaced one stage at a time
   (core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md), Accepted 2026-10-18). No Python
   charter package is published from this repository.
2. **Built against contracts.** The service is built against two contract halves that live
   together under `contracts/charter/`: the OpenAPI document (shapes, codes) and BDD scenario
   files (behavior, `contracts/charter/scenarios/`). The service runs every scenario file in
   its build. The conformance corpus (roadmap step 3) adds expected results produced by the
   Python charter; it complements the Gherkin scenarios, it does not replace them.
3. **Open-packs round-trip.** Every YAML file of `spec-kitty-open-packs` (`built-in/` and
   `packs/`) must round-trip through the file-based infrastructure layer
   (`adapter-out-yaml`): the document model renders it byte for byte, rewriting every scalar
   through the model's writer loads to the same data, and every pack loads and its graph
   survives a write and re-read. CI runs it against a pinned open-packs commit, and it never
   skips.

## Consequences

### Positive

- No interim Python package, no second home for code that will retire.
- The Python charter keeps answering in core while it is the oracle, so the corpus is
  produced where the reference implementation already runs.
- The round-trip ties the infrastructure layer to real content from the first commit.

### Negative

- The strangler spans two repositories: the seam and the shadow comparison live in core, the
  service here.
- The extraction of `src/charter/` from core (core ADR `2026-10-12-2`, epic #6099) no longer
  ends in this repository; core has to record where the Python charter goes once it retires.

### Risks

- A rewrite can drift from Python behavior that no scenario file or corpus case pins. The
  corpus is the guard; until it exists (roadmap step 3) the service answers no production
  read.

## Alternatives considered

### Move the Python charter here first, then strangle it with Java here

One repository for both implementations. Not chosen: it publishes and maintains a Python
package whose only future is retirement, and needs `kernel` to ship first (open question 4).

## References

- [Roadmap](/plans/roadmap/), steps 1 to 7
- Core ADR `4.x/2026-10-03-1`, Amendment 2026-10-18
