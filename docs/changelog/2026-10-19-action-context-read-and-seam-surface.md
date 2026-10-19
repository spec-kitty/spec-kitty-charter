---
title: The action-context read walks the offering, and the seam surface is reshaped
description: getActionContext answers the offering's full walk with depth as suggests steps; ServiceInfo, the fingerprint, stale_input, the token and packs refresh are shaped before 1.0.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - api/service-contract
  - conformance/divergence-register
audience:
  - profile: core-maintainer
    guidance_text: Read this for what the seam reads, how depth maps, and what the launcher must set.
  - profile: charter-service-contributor
    guidance_text: Read this for what changed in the contract and the service before 1.0.
---

# 2026-10-19 — The action-context read walks the offering, and the seam surface is reshaped

Contract 1.0 is not tagged yet, so its closed shapes can still change. This change settles
the action-context read (#95) and the reserved seam surface (#113) before the tag. The
decisions are in [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/).

## What changed

- **The action-context read is the offering's full walk.** In order:
  1. scope, from the action node;
  2. `requires`, transitively;
  3. `suggests`, up to `depth` steps;
  4. the `requires` of what was suggested;
  5. `vocabulary`.

  Each entry says how it was reached (`relation`, `via`, `hops`), and the answer carries the
  offering's load `diagnostics`. Activation, selection roots and tensions come from later
  operations.
- **`depth` counts `suggests` steps,** from 0 to 2, default 2. The seam maps a first load to 2
  and a later load to 1.
- **`ServiceInfo.serviceVersion`** names the build.
- **The source fingerprint** also covers `config.yaml` and `charter.yaml`. Only a 200 carries
  it, the digest that read verified.
- **`stale_input` is raised:** a read whose inputs change while it is answered is refused,
  and may be retried. Every refusal code is pinned by a scenario. A machine-readable
  `x-refusal` map gives each code's status and retry class.
- **The per-worktree token is required:**
  - Every operation takes a bearer token.
  - A missing or wrong token is a 401; a browser request or a non-loopback host is a 403.
  - The service will not start without a token, or with one passed on the command line.
- **`POST /packs/refresh`** re-reads the packs and the project configuration and answers the
  refreshed listing.

Core's code is evidence of intent, not a specification. Each deliberate difference is in the
[divergence register](/conformance/divergence-register/). The contract `CHANGELOG.md` and the
[service contract](/api/service-contract/) record the detail.
