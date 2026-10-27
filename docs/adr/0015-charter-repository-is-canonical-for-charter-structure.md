---
title: "ADR-0015: This repository is canonical for the charter structure, responses, contracts and pack schemas"
description: Vendored charter vocabulary changed here is canonical; consumers, core included, adapt. Stay close to the source, deviate for good reason.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0002-contract-source-of-record
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0010-cutover-scope-and-seam-boundary
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for when you may change a file that came from core, and how to record it.
  - profile: core-maintainer
    guidance_text: Read this for why core adapts to this repository's charter shapes rather than the reverse.
  - profile: pack-author
    guidance_text: Read this for which repository owns the Charter Pack schema you write against.
---

# ADR-0015: This repository is canonical for the charter structure, responses, contracts and pack schemas

## Status

**Accepted** — 2026-10-27 (provisional since 2026-10-19). Decider: Stijn Dejongh (operator). Taken on
2026-10-19 and ratified on 2026-10-27, this decision is still revisited as the system evolves, on the
triggers under [Revisit when](#revisit-when).

## Context

Several files here started as copies from core. `contracts/_shared/` is vendored byte for byte,
and its `VENDORED.md` said never to edit it here. The charter's structure, response shapes and
Charter Pack schemas were taken from core's Python charter, which ADR-0008 replaces by rewrite.

That rule surfaced as a conflict on issue #90. The shared `default` `Problem` response, which
every charter operation returns, could not declare the `Charter-*` headers the service sends on
it without editing a vendored file. Keeping parity with core meant keeping a gap.

[ADR-0002](/adr/0002-contract-source-of-record/) already makes this repository the source of
record for the charter contract, but its Decision 2 reused core's shapes by vendoring a pinned
copy, not redefining them, and the old `VENDORED.md` turned that into "never edit these files
here; change them in core." It did not say what happens when a vendored file must change, nor
who owns the Charter Pack schemas. This decision supersedes that "vendor, do not redefine"
posture for the vendored vocabulary and settles both questions.

## Decision

1. **This repository is the canonical owner of the charter structure, its responses, its
   contracts and the Charter Pack schemas.** That includes the vocabulary vendored from core
   (`contracts/_shared/`). A change made here is canonical, even to a file that came from core.
   For the charter contract this restates [ADR-0002](/adr/0002-contract-source-of-record/)
   Decision 1; it *adds* the vendored `_shared/` vocabulary and the Charter Pack schemas, and
   supersedes ADR-0002 Decision 2's "vendor, do not redefine" posture.
2. **Consumers adapt.** Core and any other consumer align to this repository's shapes. They do not
   send changes back as a condition of making them here.
3. **Stay close to the source, deviate for good reason.** A vendored file is kept as it came
   unless there is a reason to change it. When there is, change it here rather than keep parity
   with a shape this repository would have to change later anyway.
4. **Record every change from an origin.** A vendored directory keeps a `VENDORED.md` that names
   its origin and commit and lists each file changed from it, why and how. A refresh from the
   origin never overwrites a listed change.
5. **Pack content is not covered.** The content of Charter Packs stays with its owners
   (spec-kitty-open-packs for the built-in and internal packs). This repository owns the schema
   that content is written against — the same structure-versus-content boundary
   [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/) Decision 6 draws.

## Consequences

### Positive

- A contract or schema gap is fixed where it is found, in one change, as #90 is.
- The charter's shapes stop inheriting decisions from the Python charter that ADR-0008 retires.

### Negative

- Core's copies drift from this repository's. Core has to adapt, and the integration seam carries
  the difference until it does.
- A file that looks vendored may not match its origin; `VENDORED.md` is the only record of why.

### Risks

- A consumer adapts late and breaks. Mitigation: changes reach consumers through the released
  contract and its CHANGELOG, and the contract's breaking-change check still applies.
- A vendored *schema* change is classified like any contract change under
  [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/): adding response
  headers to a closed response (as #90 does) is additive and lands cleanly, but adding a property
  to a closed response schema is breaking and costs a contract major. Editing a vendored shape
  here does not exempt it from that check.
- A change to a Charter Pack *schema* is guarded by
  [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/) Decision 3's
  open-packs round-trip, run in CI against a pinned open-packs commit: it catches a schema change
  that would break existing pack content.

## Alternatives considered

### Keep vendored files identical to core, change them only in core

The previous rule. Rejected: it blocks a fix here behind a change in core, and core's shapes are
the ones this repository is replacing.

### Fork every vendored file into charter-owned names

Rejected: it duplicates vocabulary that has not changed and hides which files did change.

## Revisit when

- Another consumer than core starts vendoring the charter contract, and the cost of adapting to
  changes here becomes visible.
- Core takes ownership of a shared vocabulary for several services, and a common origin is worth
  more than local changes.

## Requests to core

- Treat spec-kitty-charter's contract and its `contracts/_shared/` as the source for charter
  shapes. Adapt core's copy, or its integration seam, when a released contract changes them.

## References

- Issue #90 and its mission `problem-response-charter-headers`.
- [ADR-0002](/adr/0002-contract-source-of-record/), [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/), [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/), [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/).
