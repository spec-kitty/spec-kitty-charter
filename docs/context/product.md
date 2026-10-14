---
title: What the charter service is for
description: What the charter service is for, where it sits in the Brain, Build and Team Kitty product lines, and what this repository will hold.
doc_status: active
updated: 2026-10-14
type: Context
kind: Explanation
related:
  - context/problem
  - context/system-context
  - adr/0001-java-service-with-openapi-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to learn what the charter is for and what this repository will hold.
  - profile: core-maintainer
    guidance_text: Read this for where the charter sits among the product lines.
  - profile: governance-lead
    guidance_text: Read this to learn what the charter does for a team and where it sits.
  - profile: individual-developer
    guidance_text: Read this to learn what the charter does for your own projects.
  - profile: non-engineer-developer
    guidance_text: Start here; this page explains the charter without engineering detail.
---

# Product

## What the charter is for

Spec Kitty runs Missions from specification through review. At every step the agent doing
the work needs to know which rules apply: the directives, tactics, styleguides, procedures,
agent profiles and glossary that govern this project. The **charter** answers that
question. It takes the Charter Packs a project draws from (the built-in pack, its
organization's packs and its own project pack), applies what the project has activated, and
returns the guidance for one action.

Without a single answer to "what governs this step?", authored governance drops silently
somewhere between the pack and the agent. Spec Kitty's charter-resolution program
(`docs/plans/charter-resolution/` in the core repository) documents exactly that class of
defect. The charter exists so that there is one answer, derived from one source, and a failure to
produce it is reported.

## Where it sits

Spec Kitty Inc. names three product lines (core `docs/context/product-lines.md`):

| Line | Side | What it is |
|---|---|---|
| **Build Kitty** | open-core producer | The free CLI, its event ledger, and the Mission Status Read API |
| **Brain Kitty** | open-core producer | Governance and doctrine: **the charter** (this repository), the open Charter Packs, doc-kitty |
| **Team Kitty** | consumer ("Multiplayer Mode") | UI surfaces (mission-ui, the pack composer) that read the Mission Status Read API across its public contract. The hosted product behind the name is not actively supported; nothing in this repository builds toward it |

The charter is a **producer**. Consumers reach it only across its published contract,
never through its internals.

## What this repository will hold

| Part | Purpose | State |
|---|---|---|
| `contracts/charter/` | The OpenAPI 3.1 contract: the source of record for every charter DTO (pending ADR-0002) | planned |
| The Java service | A hexagonal Java 25 service: pure domain, read and write applications, REST and MCP adapters, YAML adapters | planned |
| Conformance corpus | Fixtures that the Python implementation and the Java service must both pass | planned |
| The Python charter package | Today's `src/charter/offering/` or all of `src/charter/` from core (boundary open: spec-kitty#3101, spec-kitty#6099), consumed through `charter.offering.api`, as the conformance oracle while Java takes over | open; see [question 2](/plans/open-questions/) |
| `docs/` | This documentation | bootstrapped |

CLI callers reach the charter through the Python seam `charter.offering.api` while the
strangler runs. That seam becomes the single entry point for CLI callers. Behind it, the answer moves from the
Python implementation to this service, stage by stage
([Strangler transition](/architecture/strangler-transition/)).

## Who uses it

- **The Spec Kitty runtime** (the CLI commands and the `next` state machine) asks the
  charter for action-scoped context, through DTOs generated from the contract.
- **Agent harnesses** may read guidance directly through the MCP adapter, without starting
  a CLI process per question (planned; its authorization is an open decision).
- **Pack authors** validate and assemble Charter Packs against the same rules the
  service applies.

## What it is not

- **Not an event producer.** The charter service does not write to the event ledger. Events come
  from the runtime's `next` transitions; see [System context](/context/system-context/).
- **Not the pack content.** The doctrine itself (the built-in pack and open org packs) is
  authored in `spec-kitty-open-packs`. The charter service loads and resolves packs; it does not
  own their text.
- **Not hosted.** The service is local and scoped to one worktree. A remote deployment is
  not a goal of this repository. Core's `docs/architecture/charter-backend-service-future.md`
  still lists the whole runtime service as backlog and keeps in-process resolution as the
  permanent fallback; [ADR-0001](/adr/0001-java-service-with-openapi-contract/) supersedes
  that for this repository, and core needs to reconcile the page.
