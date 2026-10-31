---
title: "ADR-0022: The contract is published as 0.x while it iterates, and 1.0.0 is minted once it is stable"
description: Proposes publishing the charter contract as 0.x with core's generated client as the feedback loop, breaking only on a 0.x minor, and minting 1.0.0 once stable.
doc_status: active
updated: 2026-10-31T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0020-offering-and-activations
  - adr/0021-contract-first-seam-generated-client-and-routing-proxy
  - api/compatibility
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the version rules while the contract is 0.x, and what the release gate checks.
  - profile: core-maintainer
    guidance_text: Read this for which 0.x release to pin, what a minor bump costs the seam, and when 1.0.0 comes.
  - profile: contract-consumer
    guidance_text: Read this before you depend on a 0.x contract.
---

# ADR-0022: The contract is published as 0.x while it iterates, and 1.0.0 is minted once it is stable

## Status

**Accepted** — 2026-10-27 (provisional since 2026-10-24), proposed 2026-10-20 and merged with #123. Decider: Stijn Dejongh (operator). Decision D1 is **(ruled)**;
the others are **(default)** and stand as decided until revisited. D1 and D2 are built (#129).
It is ratified as of 2026-10-27.

This record amends:

- the contract's `info.version`, `1.0.0-SNAPSHOT` since PR #54, which becomes `0.1.0-SNAPSHOT`;
- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/): its reservations
  target 1.0.0, which is no longer the first published version (D4);
- [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)'s "release window", which
  closes at 1.0.0 instead of the first tag (D4);
- the release gate (`ReleaseGate`, rules 7 and 8), which treats a 0.x minor as the breaking
  boundary (D2);
- [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/) decision 6: core
  waits on 0.1.0, not on 1.0.0 (D5).

## Context

ADR-0021 makes the published contract core's seam from now on: core vendors a client generated
from it and routes each operation through a proxy. That makes core's adoption the best evidence
the contract has, and the operator wants to use it:

> Given that we will be iterating on the contracts for a while, using the generated client and the
> core integration as an early feedback mechanism, our initial API should have a `0.x` version.
> The `1.x` version is minted once we are reasonably confident the contract is stable enough to
> be published.

Under the plan before this record, the first published version was 1.0.0. Every closed shape had
to be right before that tag, which is why ADR-0019 and ADR-0020 list a large reshape "before the
tag", and why ADR-0021 accepted that core waits for it. The shapes most likely to change are
exactly the ones core's integration will test first.

## Decision

### D1. The first published contract is 0.1.0, and 1.0.0 comes when the contract is stable (ruled)

The contract is published as `0.y.z` while it iterates with core. 1.0.0 is minted when the
operator judges it stable enough (D6). The unreleased version on `main` becomes `0.1.0-SNAPSHOT`,
and released tags keep the `contract-charter-v` prefix (`contract-charter-v0.1.0`).

### D2. In 0.x, a minor version may break and a patch may not (default)

SemVer allows anything in `0.y.z`. That would turn the release gate off for as long as the
contract is 0.x, which is the period it changes most. Instead:

- **`0.y.z` → `0.y.(z+1)`:** compatible changes only. The gate compares against the newest
  `0.y` release and fails on a breaking change, as it does within a major today.
- **`0.y.z` → `0.(y+1).0`:** may break. The CHANGELOG section must hold a `### Breaking`
  subsection that lists each break and what core's seam has to change. The gate still runs
  `oasdiff` against the previous minor and shows the result, but does not fail on it.

From 1.0.0 the usual rules apply: a major version is the breaking boundary. Rejected: "anything
goes in 0.x", which hides every break from core until its generated client fails to compile.

### D3. Core pins one 0.y release, and the version handshake matches the minor (default)

Core vendors one exact `0.y.z` bundle. While the contract is 0.x, the seam's version handshake
(ADR-0011, `Charter-Contract-Version`) accepts a service whose version has the same `0.y`, and
refuses any other minor. From 1.0.0 it accepts the same major. A breaking minor is therefore a
coordinated step: release `0.(y+1).0`, regenerate core's client, adapt the in-process backend, and
switch routes only once both sides speak the new minor.

### D4. "Reserve before release" means before 1.0.0 (default)

Every rule that freezes a shape at the first tag now freezes it at 1.0.0:

- the reservations of ADR-0009, ADR-0018's release window, and the pre-tag lists of ADR-0019 and
  ADR-0020;
- the closed-enum rule for refusal codes and the closed-schema rule for response fields.

In 0.x those shapes may change in any minor. They are still declared as early as they are known,
because core codes against what is declared: a reserved activation operation (ADR-0020 D8)
belongs in an early 0.x minor even though it could legally arrive later.

### D5. 0.1.0 is published from the contract as it stands, and the reshape follows in minors (default)

The first release does not wait for the reshape of ADR-0019 and ADR-0020. The order:

1. **0.1.0:** today's contract, once the duplicate-DTO defect
   ([#119](https://github.com/spec-kitty/spec-kitty-charter/issues/119)) is fixed, so core's
   generated client is clean. Core starts the proxy (ADR-0021) on it.
2. **The next minors:** the activation set and its document first, because core's deny-list work
   depends on it; then the `/offering` move, the API root and `_links`; then the hosted-first
   reshape. The exact grouping is planned in the tracker.

*Note, 2026-10-31 (operator ruling on #125 and #126):* 0.1.0 was held, so the activation set (#125) and the
offering reads, API root and `_links` (#126) land inside the unreleased `0.1.0-SNAPSHOT`, under its `### Breaking`
subsection, instead of in the minors after it. The order of step 2 holds; it now falls before the first release.
See ADR-0020's amendment of 2026-10-31.

This cuts the time core waits for its first client from "the whole reshape" to "one defect fix".
Rejected: publishing nothing until the reshape is done, which delays the feedback this record
exists for.

### D6. 1.0.0 is minted on the operator's call, against stated evidence (default)

The operator decides. The evidence the decision should rest on:

- core's seam routes every charter read and the activation operations through the generated client
  and the proxy;
- the cutover read inventory (#94) is decided, and each read is in the contract or excluded;
- the activation set (ADR-0020) and the hosted-first reshape are in the contract;
- the last two minors held no `### Breaking` subsection;
- the open questions that ADR-0019 and ADR-0020 leave for "before 1.0.0" are ruled.

**The first route switched to the service needs 1.0.0** (ADR-0021 D5). Shadow routes may run on
0.x; production answers from the service run on a stable contract. So 1.0.0 is inside the 4.x
cutover milestone.

### D7. The URL prefix stays `/api/v1` (default)

The prefix names the API generation that 1.0.0 will stabilize, not the release number, so it does
not change at 1.0.0. Rejected: `/api/v0` for 0.x, which would make 1.0.0 break every path for no
change in behavior.

## Consequences

### Positive

- Core gets a generated client from the current contract after one defect fix, instead of after
  the whole reshape.
- Shapes are tested by a real consumer before they freeze. The open questions of ADR-0019 and
  ADR-0020 can be answered with core's experience rather than ahead of it.
- The gate keeps working through 0.x: a patch that breaks still fails the build.

### Negative

- Every breaking minor costs core a regeneration and an adapter change, in step with this
  repository.
- 1.0.0 is a judgment call, so it can slip. D6's evidence list makes the call reviewable.
- The release gate needs a change for 0.x (D2), and the contract, its CHANGELOG and several
  documents change their version.

### Risks

- **0.x becomes permanent.** Mitigation: 1.0.0 is on the cutover's path (D6), so the cutover cannot
  finish without it.
- **A breaking minor surprises core.** Mitigation: the `### Breaking` subsection names the seam
  change, and the handshake refuses a mismatched minor instead of answering with a different
  shape.

## Alternatives considered

### B: publish 1.0.0 first and use additive contract sets for every change

The plan before this record (ADR-0009). Rejected: it freezes the shapes before core has used them.

### C: unpublished snapshots only, no releases until 1.0.0

Core would vendor `-SNAPSHOT` bundles. Rejected: a snapshot is not checksummed or tagged, so core
cannot pin it, and the gate has no baseline to compare against.

## Revisit when

- Core's integration shows the minor-level handshake is too strict, for example because patch
  releases need different routing.
- A second consumer arrives before 1.0.0 and cannot follow breaking minors.

## Requests to core

- Pin one `0.y.z` bundle and regenerate on every minor you adopt.
- Make the seam's handshake match the minor while the contract is 0.x.
- Report every friction with a generated shape as an issue here; that is the feedback 0.x exists
  for.

## References

- [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/),
  [ADR-0020](/adr/0020-offering-and-activations/),
  [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/),
  [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/).
- [Compatibility](/api/compatibility/).
- [SemVer 2.0.0, item 4](https://semver.org/#spec-item-4) (anything may change in `0.y.z`).
- Issues: [#45](https://github.com/spec-kitty/spec-kitty-charter/issues/45) (release publishing),
  [#119](https://github.com/spec-kitty/spec-kitty-charter/issues/119).
