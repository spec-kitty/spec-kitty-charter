---
title: Service lifecycle
description: How the charter service runs — one per worktree, loopback only, a capability credential, source fingerprints for freshness, and a loud fallback.
doc_status: draft
updated: 2026-10-13
type: Architecture
kind: Explanation
related:
  - architecture/overview
  - architecture/strangler-transition
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for scope, binding, freshness and fallback of the running service.
  - profile: contract-consumer
    guidance_text: Read this to know how the service is reached and what happens when it is stale.
---

# Service lifecycle

How the service runs: one instance per worktree, bound to loopback, guarded by a capability credential, kept fresh by source fingerprints and backed by a loud fallback.

> **Status:** design only, from core ADR `2026-10-03-1` ("Service boundary") and the sibling model in core ADR `2026-10-01-2`. [What the labels mean](/#status).

## One service per worktree

Each worktree can carry different packs and a different active charter, so a shared process
would answer from the wrong inputs. A service instance, its endpoint, process identity,
credential, source fingerprints and compiled state belong to one worktree and are never
shared implicitly.

## Who starts it

The core's `specify_cli` layer launches, locates and stops the service, and hands the seam
an endpoint and a credential. The seam never manages the process. The launcher's design (a
daemon started on first use, or one per command) is a deferred decision.

## Binding and access

- Binds to `127.0.0.1` only and checks the `Host` and `Origin` headers, as the Mission
  Status service does. Loopback-only HTTP is intended; do not change it to HTTPS in response
  to a static-analysis finding.
- Every request carries a capability credential scoped to the worktree.
- No secrets in responses.
- The MCP adapter does not ship until a decision defines who may call it and how; the
  expected answer reuses this model (loopback, Host check, worktree-scoped token).

## Freshness

- At start, and after any write made outside it, the service fingerprints its source files
  and builds the active graph atomically. It never serves a graph built before a write it
  knows of.
- Ordinary reads make no network calls.
- A stale input causes a refusal, or a loud fallback to Python while the fallback exists.
  Explicit refresh and mutation operations rebuild the graph atomically.

## Failure and fallback

| Situation | Behavior |
|---|---|
| Service not running or unreachable | The seam falls back to Python (until Python reads retire), and says so in diagnostics and telemetry |
| Service answers for another worktree | Refused: identity is checked on every call |
| Inputs changed since the graph was built | Rebuild, or refuse; never answer from the old graph |
| After Python reads retire | No fallback: the command fails with a named error |

## Release

The Mission Status service ships as a separate, per-OS release artifact outside the Python
wheel, so the wheel stays server-free. The charter service follows the same model. A
separate artifact means its own signing and a cross-OS test matrix, which is new CI cost.
