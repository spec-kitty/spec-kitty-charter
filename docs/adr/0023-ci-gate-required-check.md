---
title: "ADR-0023: An always-on CI gate is the single required status check"
description: Adds an always-on orchestrator workflow that aggregates the path-filtered service and docs checks into one status context, so branch protection can gate merges on green.
doc_status: active
updated: 2026-10-31T00:00:00Z
decision_status: Accepted
decision_date: '2026-10-22'
type: ADR
kind: ADR
related:
  - development/service-architecture-orientation
  - adr/0006-onion-vertical-slices-archunit
audience:
  - profile: core-maintainer
    guidance_text: Read this for why main requires one CI gate context and how auto-merge now gates on green.
  - profile: charter-service-contributor
    guidance_text: Read this for the single check your PR must pass and why the per-area workflows no longer trigger on pull_request directly.
---

# ADR-0023: An always-on CI gate is the single required status check

## Status

**Accepted** — 2026-10-22. Decider: Stijn Dejongh (operator).

Extended 2026-10-31 by [ADR-0046](/adr/0046-service-release-line-and-native-packaging/): a third concern group,
`native`, calls `service-native.yml` (the four native executables) for changes to the contract, the service, the
build and `scripts/release/`; `CI gate / gate` counts it like the other two. The required check is unchanged.

Amended 2026-11-01 by ADR-0047 (operator ruling of 2026-11-01). `main` lands through a merge queue, and the
changes below are annotated where they stand. The required check is unchanged.

- The gate also triggers on `merge_group`, and on a queue entry it diffs the queue's base against its head.
- A `pr:pause-ci` label pauses a pull request's CI.
- `docs-site.yml` no longer builds the service on a pull request. It builds the Javadoc alone.

## Context

The `main` branch ruleset required a pull request and a linear history, but **no status check** —
zero required contexts. With nothing required, GitHub treats a PR as mergeable the moment it is
opened, so `gh pr merge --auto` degrades to an immediate merge rather than waiting for green. The
intent is the opposite: a PR should merge only once CI passes.

The obvious fix — mark the existing checks required — does not work against this repository's CI
topology. Every PR workflow is **path-filtered**:

- `service.yml` runs only on `contracts/**`, `service/**`, build files.
- `docs-cleanliness.yml` runs only on `docs/**`, `site/**`, `scripts/docs/**` and the lint configs.

GitHub does not post a check context for a workflow that a path filter skipped, and it treats a
required context that never posts as *"Expected — waiting"* indefinitely. So requiring
`service / verify` would block every docs-only PR forever, and requiring `Docs checks` would block
every service-only PR. A `workflow_run`-triggered aggregator is worse: it posts its context only on
`main`, never on a PR head, so requiring it blocks every PR (the same trap that bit the
`CI Aggregate` pattern elsewhere).

## Decision

Introduce one always-on orchestrator workflow, `.github/workflows/ci.yml` (**CI gate**), as the
single PR entry point, and require only its terminal job context, **`CI gate / gate`**.

- It triggers on `pull_request` with **no path filter**, so it always runs and always posts.
  *(Amended 2026-11-01, ADR-0047: also on `merge_group`.)*
- A `changes` job detects which concern groups the PR touched (a `git diff` against the PR base,
  mirroring the two workflows' path lists; a change to `ci.yml` itself re-runs both groups).
- It calls the real gate workflows as reusable workflows (`uses: ./.github/workflows/service.yml`,
  `./.github/workflows/docs-cleanliness.yml`) **only for the groups that changed**, preserving the
  cost saving of the path filters.
- A terminal `gate` job (`if: always()`) passes only when every triggered group succeeded or was
  legitimately skipped, and **fails closed** if change detection did not complete.

The per-area workflows lose their direct `pull_request` trigger (they keep `push` on `main`,
`workflow_call` and `workflow_dispatch`, and `docs-cleanliness` keeps its weekly `schedule`), so
nothing double-runs through two PR entry points.
`markdown-lint` is marked `continue-on-error` so it stays advisory — findings never block the gate.
`external-links` was already `continue-on-error`. `docs-site.yml` stays a separate, non-required PR
check (a site-build failure is a publishing concern, not a content regression).

With the context posting on every PR, `main`'s ruleset requires `CI gate / gate`, and
`gh pr merge --rebase --auto` now genuinely queues and merges on green.

## Consequences

### Positive

- One honest, always-present required check; auto-merge waits for green instead of merging on open.
- The path-filter cost saving is kept — a docs-only PR still skips the Java build and vice versa.
- Granular visibility remains: each called workflow's jobs still post their own (non-required)
  contexts under `CI gate / …`.

### Negative

- An orchestration indirection: the real jobs now run one level down, under `CI gate`.
- Some redundancy remains: `docs-site.yml` still calls `service.yml` for the Javadoc, so a
  service-touching PR can build the service twice (pre-existing; a later consolidation may fold
  `docs-site`'s PR build into the gate). *(Resolved 2026-11-01, ADR-0047: on a pull request
  `docs-site.yml` builds the Javadoc alone.)*

### Risks

- A change-detection miss would skip a group silently. Mitigated by failing closed on a
  detection error and by re-running both groups whenever a workflow file changes.

## Alternatives considered

### Require the path-filtered checks directly

Rejected: deadlocks the complementary PR type, which never posts the required context.

### Always run both workflows on every PR

Rejected: correct but discards the path-filter cost saving (a full Java build on every docs typo).

### Companion inverse-path jobs that post a green stand-in

Rejected: two sources for one context name is fragile and easily drifts out of sync.

## References

- `.github/workflows/ci.yml`, `service.yml`, `docs-cleanliness.yml`
- [Service architecture orientation](/development/service-architecture-orientation/)
