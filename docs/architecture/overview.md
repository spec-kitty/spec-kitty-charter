---
title: Charter service overview
description: Containers and main flows of the charter service — the Python seam in core, the Java service, its adapters and the pack inputs.
doc_status: draft
updated: 2026-10-14
type: Architecture
kind: Explanation
related:
  - context/system-context
  - architecture/hexagonal-service
  - architecture/contract-and-dtos
audience:
  - profile: charter-service-contributor
    guidance_text: Read this first for the containers and the main flows of a governed read.
  - profile: core-maintainer
    guidance_text: Read this to see where the seam and the launcher sit in core.
---

# Overview

The containers of the charter service and the main flows between them: the Python seam in core, the Java service, its adapters and the pack inputs. Read this first, then the page for the part you are building. The state of `charter.offering.api` in core today is described in [Problem](/context/problem/).

> **Status:** design only; nothing in the diagram exists in this repository yet. [What the labels mean](/#status).

## Containers

The diagram draws the seam in core; where the Python charter ends up is [open question 2](/plans/open-questions/).

```mermaid
flowchart TB
  subgraph core["spec-kitty core (Python)"]
    direction TB
    cli["CLI commands<br/>and runtime next state machine"]
    seam["charter seam<br/>charter.offering.api"]
    dtos["generated DTOs<br/>from contracts/charter"]
    pyimpl["Python charter<br/>read + write paths<br/><i>exists; retires in stages</i>"]
    life["service launcher<br/>specify_cli"]
    cli --> seam
    seam --- dtos
    seam -->|until cut-over| pyimpl
    life -->|endpoint + credential| seam
  end

  subgraph svc["charter service (Java, this repository)"]
    direction TB
    rest["adapter-in-rest"]
    mcp["adapter-in-mcp<br/><i>after authz decision</i>"]
    read["read application"]
    write["write application<br/><i>later</i>"]
    domain["domain<br/>models + ports, plain Java"]
    yaml["adapter-out-yaml<br/>lossless documents"]
    rest --> read
    mcp --> read
    read --> domain
    write --> domain
    yaml --> domain
  end

  packs[("Charter Packs and project config<br/>pack.yaml, *.graph.yaml, drg/fragment.yaml,<br/>org-charter.yaml, .kittify/config.yaml, charter.yaml")]

  seam -->|loopback REST| rest
  life -->|start / stop per worktree| svc
  yaml -->|read| packs
  pyimpl -->|read / write| packs
```

## Main flows

**A governed read (Java authoritative stage).** A CLI command or the `next` state machine
asks the seam for action-scoped context, passing the mission type and the load depth. The
seam holds an endpoint and a credential the launcher gave it, calls `GET /api/v1/...` on the
worktree's service, and receives a response
that deserializes into the generated DTO. The service answers from a warm active graph it
built from source fingerprints. If the service is stale or unreachable, the seam falls back
to the Python implementation **loudly**: the fallback is visible in diagnostics and
telemetry and never silently picks a second answer.

**A read shadow.** Before Java is authoritative, Python answers and the Java answer is
compared out of band against the same inputs. Differences are reported, not served.

**A write (late stages).** Python holds the single write lock until a write operation
migrates. A migrated operation runs in the Java write application only through a handoff the
seam grants. After any write made outside the service, the service rebuilds its active graph
before it answers again.

## What the seam owns, and what it does not

- The seam is code under `src/charter/` in core (where it sits is
  [open question 2](/plans/open-questions/)). Its only service-facing code is a transport client.
- It does **not** start, stop or locate the service. Launch and lifecycle live in
  `specify_cli`, which hands the seam an endpoint and a credential, so `charter` gains no
  outbound edge to `specify_cli` and core's enforced import chain
  (`kernel <- charter <- {glossary, runtime, mission_runtime} <- specify_cli`) stays intact.
- No caller bypasses it. REST and MCP may never implement charter resolution independently;
  they are transports into the same read application.
