---
title: "ADR-0070: Charter tests its own example packs; open-packs proves its content against a pinned charter"
description: "The contract tests stop reading pinned open-packs content: charter-owned example packs carry the round trip, and open-packs validates its packs against a charter release."
doc_status: active
updated: 2026-11-11T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-11'
type: ADR
kind: ADR
related:
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0057-performance-budgets-never-block-a-pull-request
  - adr/0067-paradigms-and-styleguides-state-named-principles
  - adr/0068-a-pack-declares-its-fallback-profile
  - adr/0069-a-profile-has-one-primary-role-and-optional-alt-roles
  - plans/charter-example-packs
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding a test that reads pack content, or changing the whole-pack or open-packs CI jobs.
  - profile: pack-author
    guidance_text: Read this for where your pack is proven valid against the charter, and how a contract change reaches you.
---

# ADR-0070: Charter tests its own example packs; open-packs proves its content against a pinned charter

## Status

**Accepted**, 2026-11-11 (operator, on PR #474). Raised by Stijn Dejongh (operator) on
[PR #474](https://github.com/spec-kitty/spec-kitty-charter/pull/474), whose three breaking contract changes
(ADR-0067 to ADR-0069) could not reach green in either repository. It amends the charter's "Gates before
merge" (the whole-pack round trip, operator ruling of 2026-11-05, epic #186) and the open-packs clause of PROJECT_002,
as [Consequences](#consequences) sets out. The work is planned in
[the example-packs plan](/plans/charter-example-packs/).

## Context

- Charter's tests read a pinned spec-kitty-open-packs checkout (`openPacksRef` in `gradle.properties`): the
  `:packs:openPacksRoundTrip` and `:deployable:openPacksProfiles` tasks (tag `open-packs`, about 28 test classes) and
  the `wholePackRoundTrip` gate (`whole-pack.yml`). Two of them are required for a pull request.
- open-packs authors against charter's schemas and model, which the charter's model now defines canonically (charter
  companion, amended 2026-11-10): a changed shape replaces the old one outright.
- So the dependency is a cycle: charter's tests need open-packs content in the new shape, and open-packs cannot author
  the new shape until charter publishes it. Every breaking contract change leaves one repository red until the other
  moves, and with required checks on both sides neither can merge first without a red landing. PR #474 is the case.
- The service ships no open-packs content. `syncPacks` copies `packs/internal` and `packs/qa` into this repository only
  for its own governance (the CLI's org layer); the cycle is in the tests alone.

## Decision

1. **Charter's tests read only charter-owned example packs.** A set of hand-crafted example packs under the test
   resources replaces the pinned open-packs content for every test that reads pack content. They change in the same
   commit as the contract, so a contract change and its proof land together. They are written in the canonical shape
   only (charter companion, 2026-11-10); an invalid shape appears only in a test of error handling.
2. **The example packs cover what the corpus covered**, by design rather than by sample: every artifact kind at full
   shape, a closure of built-in, org and project levels with `depends_on`, cross-pack qualified references, a mutability
   policy with each level, presets, graph documents of both shapes, step enhancements with prompt fragments, the
   prompt slots, mission types with lifecycles and guards, companion files and binary payloads, and a declared fallback
   profile. The round-trip gate (ADR-0036: equality of meaning) runs over them and stays required.
3. **open-packs proves its content against a pinned charter release.** open-packs CI validates every pack with the
   charter's published artifacts at a pinned charter version (the pack schemas and the contract bundle, or the service
   jar of a `service-v*` release). The dependency becomes one-way: charter publishes; open-packs pins, stays green, and
   bumps the pin when it migrates.
4. **A drift watch, never a gate.** Charter may keep a nightly, non-blocking run of the round trip over open-packs
   `main` (as ADR-0057 and ADR-0060 do for other slow or external checks). It opens an issue on red and never blocks a
   pull request.
5. **The governance sync stays.** `syncPacks` keeps feeding `packs/internal` and `packs/qa` to this repository's own
   governance from the pin; it is not a test input, and its pin may lag a contract change without blocking it.

## Consequences

### Positive

- A contract change reaches green in charter on its own; open-packs follows on its own schedule against a pinned
  release.
- The proof of each shape lives beside the shape, so a reviewer sees both in one diff.
- Tests no longer depend on content they do not own: a wording change in open-packs cannot turn charter red.

### Negative

- Charter no longer proves directly that the real built-in pack loads; that proof moves to open-packs and arrives
  after charter merges.
- The example packs are new content to keep honest: they can drift towards what is easy to test. Mitigation: a
  coverage check (plan, phase 2) fails when a kind, a schema property or a refusal code has no example.

### Amends

- **Charter companion, "Gates before merge"**: the whole-pack round trip runs over charter's example packs.
- **PROJECT_002**: "spec-kitty-open-packs YAML always round-trips without loss of meaning" is proven in open-packs,
  against a pinned charter release; charter proves round-trip fidelity on its own example packs.

## Alternatives considered

### Keep the corpus tests and land each breaking change red

Not chosen: both repositories carry a red required check for the length of a migration, and a real failure hides
behind the expected one.

### Pin open-packs to a branch that already migrated

Not chosen: it needs the open-packs change authored before the contract it follows is merged, which is the cycle
again, and a branch pin is not reproducible.

### Mark the corpus checks optional

Not chosen: an optional red is ignored, and charter keeps a dependency it does not need.

## References

- [PR #474](https://github.com/spec-kitty/spec-kitty-charter/pull/474), ADR-0067, ADR-0068, ADR-0069
- [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) (round trip as equality of meaning)
- [The example-packs plan](/plans/charter-example-packs/)
- The open-packs validation job (decision 3): [spec-kitty-open-packs#188](https://github.com/spec-kitty/spec-kitty-open-packs/issues/188)
