---
title: Problem
description: Why the charter leaves the core CLI for its own repository, and why its new implementation is a Java service built against an OpenAPI contract.
doc_status: active
updated: 2026-10-13
type: Context
kind: Explanation
related:
  - context/product
  - adr/0001-java-service-with-openapi-contract
  - architecture/strangler-transition
---

# Problem

## The charter is tangled into the CLI

The charter lives at `src/charter/` in the core CLI. It already has a curated public
surface, `charter.offering.api`, and it depends only on `kernel`. But the rest of the CLI
does not use that surface: about 129 files in `specify_cli`, `runtime` and `glossary` import
charter internals directly (`charter.drg`, `charter.activation.pack_context`,
`charter.bundle`, `charter.activation.charter_yaml_io`, the compiler, the resolver, and more;
core ADR `2026-10-03-1` counts them by module).

As long as that holds:

- the charter cannot be released or versioned on its own;
- open-core consumers cannot depend on governance without depending on the whole CLI;
- no other implementation can sit behind the charter, because there is nothing to sit
  behind.

Core ADR `2026-10-12-2` (Accepted) decides that the charter becomes an external contract
package in its own repository, like `spec-kitty-events` and `spec-kitty-tracker`. Its
precondition, moving those imports onto `charter.offering.api` (spec-kitty#645), is part of
the 4.0.0 GA gate. The mechanics ride with epic spec-kitty#6099.

## Every read starts from nothing

Each CLI or agent interaction loads, parses, merges, activates and resolves the charter in a
fresh Python process. That is expected to make high-frequency reads expensive. It has not
been measured end to end, and nothing in this repository asserts a speed-up until it has
(core ADR `2026-10-03-1`, "Deferred decisions").

The stronger reason is shape. Offering the same governance answer through the CLI, REST
and MCP is hard when the answer is computed inside one CLI invocation.

## Governance fails quietly

The charter-resolution program in core found that authored org and project doctrine loads,
validates and reports healthy, then reaches no agent. Its root cause is that canonical facts
(kind vocabulary, recursion policy, name versus id) are restated by hand in several places,
and every divergence fails green. A rewrite that restates the same facts a third time would
make this worse. The rewrite therefore has to be **contract-first** and **conformance-gated**:
one published contract, one fixture corpus, two implementations that must give the same
answer until the old one retires.

## Why Java, and why a contract

The operator chose Java for the charter (2026-10-13, recorded in
[ADR-0001](/adr/0001-java-service-with-openapi-contract/)). That follows the direction
core ADR `2026-10-03-1` proposed (a per-worktree Java read service, writes later) and matches
the Mission Status Read API service (core ADR `2026-10-01-2`): Java 25, Spring Boot 4, one
Gradle build, contract-first OpenAPI 3.1.

The OpenAPI contract is what keeps two languages honest. The core ingests it and generates
the DTOs its runtime (the CLI and the `next` state machine) uses; the service generates its
server interfaces from the same document. Neither side imports the other's code.
