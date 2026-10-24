---
title: Service lifecycle
description: How the charter service runs — a daemon per worktree, loopback only, a capability credential, source fingerprints for freshness, and failing closed.
doc_status: draft
updated: 2026-10-24T00:00:00Z
type: Architecture
kind: Explanation
related:
  - adr/0018-action-context-read-and-seam-access-surface
  - architecture/overview
  - architecture/strangler-transition
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for scope, binding, freshness and fallback of the running service.
  - profile: contract-consumer
    guidance_text: Read this to know how the service is reached and what happens when it is stale.
---

# Service lifecycle

How the service runs: one instance per worktree, bound to loopback, guarded by a capability credential, kept fresh by source fingerprints, and failing closed when it cannot answer.

> **Status:** design only, from core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) ("Service boundary") and the sibling model in core ADR [`2026-10-01-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-01-2-mission-status-read-api-and-dashboard-extraction.md) (the Mission Status Read API). The launcher, failure, handshake and freshness rules below are provisional decisions of 2026-10-19 ([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/)). [What the labels mean](/#status).

## One service per worktree

Each worktree can carry different packs and a different active charter, so a shared process
would answer from the wrong inputs. A service instance, its endpoint, process identity,
credential, source fingerprints and compiled state belong to one worktree and are never
shared implicitly.

## Who starts it

The core's `specify_cli` layer launches, locates and stops the service, and hands the seam
an endpoint and a credential. The seam never manages the process.

The service always runs as a **daemon**, in CI too: started on first use in a worktree, stopped
after an idle period and when the worktree is removed. Because the seam receives only an
endpoint and a credential, the launcher can change later without touching the seam. A daemon
started by the CLI reopens the trust question core settled for the dashboard; the binding
rules below are how this one is scoped to its worktree.

## Binding and access

- Binds to `127.0.0.1` only. A request with an `Origin`, `Sec-Fetch-Site` or `Sec-Fetch-Mode`
  header, or a `Host` whose name is not `localhost`, `127.0.0.1` or `[::1]` (the port is not
  judged), is refused with 403. The `Origin` check alone would not stop DNS rebinding.
  Loopback-only HTTP is intended; do not change it to HTTPS in response to a static-analysis
  finding.
- Every request carries the per-worktree bearer token, on every operation including `/info`. A
  missing, malformed or wrong token is a 401 with `WWW-Authenticate: Bearer`. The guard runs
  first, before any file is read, so an unknown path is a 401 and not a 404.
- The service refuses to start without a token of at least 32 characters, unless
  `charter.insecure-no-token=true` is set (development only; it warns at every start). It refuses
  a token that comes from argv or a system property, including through `spring.application.json`
  given on argv or with `-D`. The environment (`CHARTER_WORKTREE_TOKEN`) is the source.
- The token proves the client to the service, not the service to the client. The launcher's
  process identity is what identifies the server. Residual risks, for the launcher's security
  review (#70): a process that squats the port receives the token, and a derived worktree id is
  guessable. A `${x}` placeholder in the environment with its value on argv is not caught
  either (hardening: #121).
- No secrets in responses.
- **Version handshake.** An info endpoint reports the contract version, the service's build
  version (`serviceVersion`, from `gradle.properties`), the worktree and the current input fingerprint.
  Every answer carries `Charter-Contract-Version` and `Charter-Worktree`; a 200 also carries
  `Charter-Source-Fingerprint`, once, and no refusal does. The seam refuses a service whose
  `major.minor` does not match the contract it was built against.
- The MCP adapter does not ship until a decision defines who may call it and how; the
  expected answer reuses this model (loopback, Host check, worktree-scoped token).

## Freshness

- The fingerprint covers, per configured pack in order, `pack.yaml`, every file of a loaded kind
  (today tactic, directive, styleguide and paradigm files, at any depth) and the declared graph inputs (root
  `*.graph.yaml` shards, then `drg/fragment.yaml`), each as its path under the pack's configured
  name and its bytes; and, when configured, `config.yaml` and `charter.yaml` under the project
  configuration directory, with presence.
  The project configuration directory is `charter.project-config-dir`; the launcher sets it to
  the worktree's `.kittify`. An absent file and an empty one fingerprint differently. A configured directory that does not exist fails
  startup. The graph inputs are digested although no served read loads them yet (#73).
- At start, and after any write made outside it, the service fingerprints its source files
  and builds the active graph atomically. It never serves a graph built before a write it
  knows of.
- **Fingerprint and rebuild on read.** Each read checks the fingerprint of its inputs, so hand
  edits and branch switches are caught.
- **Push after write.** Core's writers notify the service after each write through the
  `kernel.atomic.observe_writes` hook, so the CLI sees its own write at once.
- Pack locations are read from `.kittify/config.yaml`, not fixed when the service starts.
- Every read verifies its inputs twice. It digests them before it loads and again after, and it
  answers 200 with the digest it verified, once, as `Charter-Source-Fingerprint`. If the two
  digests differ, the service refuses with `stale_input`, including when the load itself refused
  and the inputs changed meanwhile. A change after the second digest, or one reverted between the
  two, goes unseen. There is no Python fallback.
- The first digest covers every configured pack, so a configured pack whose directory is missing,
  or a pack holding a file the fingerprint covers that cannot be read or a directory that cannot be
  listed, refuses every read with 500 `pack_unreadable`, a read of a healthy pack included. A file
  that vanishes while a pack is walked, as in an atomic save, is not an error.
- Refresh is not served in this version. Reads verify their inputs on every call, and mutation
  operations will rebuild the graph atomically.
- Ordinary reads make no network calls.

## Failure: the seam fails closed

Stages 3 and 4 of the [strangler transition](/architecture/strangler-transition/) ship
together, so there is no Python fallback at any point.

| Situation | Behavior |
|---|---|
| Service not running, unreachable or too slow | The command fails with a named error and a remedy, and exits non-zero |
| Service answers for another worktree | Refused: identity is checked on every call |
| Contract version does not match | Refused by the seam, naming both versions |
| Inputs changed since the graph was built | Rebuild, or refuse with `stale_input`; never answer from the old graph |
| Missing or wrong token (401), or a browser request (403) | A transport failure, not a refusal |
| The service refuses (for example `pack_level_mismatch`) | That is the answer, passed through; it is not a failure |

## Release

The Mission Status service ships as a separate, per-OS release artifact outside the Python
wheel, so the wheel stays server-free. The charter service follows the same model. A
separate artifact means its own signing and a cross-OS test matrix, which is new CI cost.
