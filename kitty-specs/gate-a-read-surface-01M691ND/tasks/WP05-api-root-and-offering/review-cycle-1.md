---
affected_files: []
cycle_number: 1
mission_slug: gate-a-read-surface-01M691ND
reproduction_command:
reviewed_at: '2026-10-31T16:16:35Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP05
---

# WP05 review feedback, cycle 1 (reviewer-renata)

Verdict: CHANGES REQUESTED. There is one blocking defect. Everything else checks out (see "Verified" at the end).

## Blocking

[HIGH] service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java:289-291 and 127-142 (`item` -> `judgeDescriptor`)

**Issue.** The item checks `pack.yaml` (`descriptors.find(pack)`) before it delegates to the kind's read, outside any
`ConsistentRead`. The per-kind read that follows (`tactics.tactic`, `directives.directiveWithRevision`, ...) reads only the
artifact's own file. Its digests come after the check, so they cannot catch a `pack.yaml` change that is already
finished when the first digest is taken. The javadoc's claim that "a change in between is caught there" is false for
that case. The 200/500 decision depends on `pack.yaml`, and the fingerprint the 200 carries does not cover the state
that decision was made on.

**Concrete failure (TOCTOU).** Configured pack `qa` has a valid `pack.yaml` and `tactics/rehearse.tactic.yaml`.
1. T0: `GET /packs/qa/artifacts/tactic/rehearse`. `judgeDescriptor` reads a valid `pack.yaml` and passes.
2. T1: another process deletes `qa/pack.yaml`, or overwrites it with invalid YAML. Invalid bytes still digest: the
   fingerprint reads raw bytes, and an absent `pack.yaml` is simply left out by `PackFileSet.digested`.
3. T2 to T4: `TacticReadService` runs `ConsistentRead.read`. Digest F1 is taken (no `pack.yaml`, or the invalid
   bytes), the tactic file is read and succeeds, and F1 is taken again. The two digests agree.
4. The answer is **200 with `Charter-Source-Fingerprint: F1`**. Under the operator ruling of 2026-10-31, the input
   state F1 must answer **500 `pack_unreadable`**, and `GET /info` would report the same F1. So a 200 is
   fingerprinted with a state that refuses: the answer is not a function of the inputs it claims to be verified
   against (ADR-0018 D11). A client that caches by fingerprint now holds a 200 for a state in which the read refuses.
   This is exactly what the double-digest read exists to rule out.

The list, the unserved-kind and malformed-id paths (`refusedAfterThePack` -> `load`), and the offering all judge
`pack.yaml` inside their verified read. Only the item of a served kind leaves the check outside.

**Required change.**
- Judge the descriptor and read the artifact between the **same** two digests, and make that digest the fingerprint
  returned. You choose how. Two options:
  - an `ArtifactReadService` item read whose single `read.read(...)` supplier runs `judgeDescriptor` and then the kind
    lookup;
  - an outer `read.read` around `judgeDescriptor` plus the kind read that refuses `stale_input` unless the outer
    first digest equals the inner verified fingerprint. Beware of A-B-A if you only compare the outer digests.
- Correct the javadoc of `judgeDescriptor` and the class javadoc to match.
- Add a deterministic red-first unit test in `ArtifactReadServiceTest`. For example, use a fake whose `find(pack)`
  answers the descriptor on its first call and then "removes" `pack.yaml` (a flag the `PackSources` stub's
  fingerprint reflects). With the current code the item answers 200. It must refuse instead: `pack_unreadable`, or
  `stale_input` if the change is seen between the digests, but never 200. An alternative is a call-order spy that
  asserts `find` runs after the first digest and before the second.

## Non-blocking (NIT)

- [NIT] kitty-specs/gate-a-read-surface-01M691ND/spec.md FR-002 still says name and version are "absent without one".
  The operator ruling supersedes it. Align the spec text, or note the ruling there, when the mission docs are next
  touched.
- [NIT] Commit 40f60bed has a stray backslash in its message. Clean it up with the planned history cleanup.

## Verified (no action)

- Root: the exact links, including `artifacts` = `/api/v1/packs/{packId}/artifacts{?kind}` with `templated: true` and
  `service-desc`. No fingerprint and no `Link` header. Every link is followed to a 200 in root.feature (the template
  is expanded with and without `kind`). `/api/v1` is pinned as a 404 with no code.
- Offering: one `ConsistentRead`. The order is level ordinal, then code-point packId. The scenario configures six
  packs so that neither storage's `Path` name order nor a case-insensitive sort gives the answer. The configured level
  wins over `pack_level`. Each pack has an `artifacts` link, and the answer has `self`, the fingerprint and
  stale_input. pack_unreadable is pinned for a missing directory, a missing `pack.yaml` and an invalid `pack.yaml`.
  name and version are required.
- `PackCatalogRepository` and `ConfiguredPack` are domain ports or records. HexagonalArchitectureTest is unchanged
  and green (43). 404 is answered only for an unconfigured id (`judged` -> `isConfigured`).
- `ServiceInfo._links` has self and service-desc. The `Link` header is sent on the info 200 only, and is pinned absent
  on the root, the offering, refusals and openapi.yaml.
- The fingerprint hook has its own rule (GET 200 JSON, path != "/", exactly one, equal to /info's) and does not use
  the contract. The YAML-as-text changes in the conformance check and ReadConsistencySteps are sound.
- `Hypermedia.templated` keeps a whole `{name}` segment and encodes every other segment. `link()` still encodes.
- No C-003 or C-004 creep.
- `:contracts:check :contracts:test :packs:test :deployable:test --rerun-tasks` is green: packs 2294/0 failed,
  deployable 462/0 failed, 186 scenarios/0 failed, contracts 181/0 failed. CyclomaticComplexity max=15 is clean on
  the changed Java files. No new suppressions. Every commit has the Stijn Co-Authored-By trailer and no model name.
