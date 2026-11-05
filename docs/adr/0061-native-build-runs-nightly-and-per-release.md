---
title: "ADR-0061: The native executables are built nightly and per release, not on each pull request"
description: "Operator ruling of 2026-11-05: the slow native build leaves CI gate / gate and runs nightly on main, on each service release and on demand."
doc_status: active
updated: 2026-11-05T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-05'
type: ADR
kind: ADR
related:
  - adr/0023-ci-gate-required-check
  - adr/0046-service-release-line-and-native-packaging
  - adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you change the service-native workflow, or when a native-only failure appears after your change merged.
  - profile: core-maintainer
    guidance_text: Read this for when the four native executables are built and checked before a release is cut.
---

# ADR-0061: The native executables are built nightly and per release, not on each pull request

## Status

**Accepted**, 2026-11-05. Decider: Stijn Dejongh (operator). The operator ruled: "the native executable build is very
slow on each PR. let's move it to a nightly and per release execution."

This record supersedes, in part, [ADR-0046](/adr/0046-service-release-line-and-native-packaging/) decision 5 (the
build on every change) and the `native` concern group that
[ADR-0023](/adr/0023-ci-gate-required-check/) gained from it.

## Context

ADR-0046 decision 5 had CI build and start all four native executables on every change that can affect one, because a
missing native reachability hint shows up only in a native build. Four runners each compile with GraalVM, so the build
is the slowest part of a pull request, and it is paid again on each merge queue entry.

## Decision

1. **The pull request no longer builds the executables.** `ci.yml` has no `native` concern group, and
   `CI gate / gate` does not wait for `service-native.yml`. The required check is unchanged.
2. **`service-native.yml` runs three ways:**
   - **nightly** on `main`, at 03:11 UTC, by `schedule`;
   - **per release**, called by `service-release.yml`, as before: a failed target still blocks the release;
   - **on demand**, by `workflow_dispatch` on any branch.
3. **A red night is a red run of the workflow.** It is reported as
   [ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/) and the mutation nightly are,
   with no other notification.

## Consequences

### Positive

- Pull requests and merge queue entries no longer wait for four GraalVM builds.
- The release keeps its full check: every executable is still built and started before it is attached.

### Negative

- **A native-only failure surfaces later, and on `main`.**
  - A missing native reachability hint, or a dependency change that breaks only the
    `nativeImageTestClasspath` verification, is found up to a day after the merge, not on the pull request.
  - Until the fix lands, `main` can hold a change that does not build natively.
  - A release can still catch it, because the release builds all four targets.
- **Nobody is told.** A red night shows as a red run; someone has to look.

### Mitigation

- For a change that risks the native build (a new reflective path, a resource or service loader, a dependency bump),
  run `service-native.yml` on the branch with `workflow_dispatch` before merging.
- The service's own hints stay unit-tested (`CharterRuntimeHintsTest`) on every pull request.

## Revisit when

- A native-only failure reaches a release, or `main` stays red natively for more than a day.
- The native build gets fast enough to run on each pull request again.
