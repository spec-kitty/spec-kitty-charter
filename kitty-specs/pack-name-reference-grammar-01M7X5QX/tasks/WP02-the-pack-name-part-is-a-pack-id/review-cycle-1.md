---
affected_files: []
cycle_number: 1
mission_slug: pack-name-reference-grammar-01M7X5QX
reproduction_command:
reviewed_at: '2026-11-20T21:57:59Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP02
---

# WP02 review (claude::reviewer-renata), head 5ecff25

Verdict: changes requested. One MEDIUM finding blocks approval: FR-011 / plan D8 is not met. The rest is sound.

## MEDIUM-1: the strict load does not refuse a 64-character qualifier (FR-011, D8, T014)

FR-011 says a graph edge whose qualifier has 64 characters, which the schema accepts, is refused at load with the
pack-id detail. D8 asks for a `:packs` load test. T014's `create_intent` names
`service/packs/src/test/java/.../infra/storage/QualifierLoadTest.java`. That file was not created. It was replaced by
`GraphBindingTest.aQualifierOverTheLongestPackIdIsAFindingThatNamesThePackIdGrammar`, which tests GraphBinding and not
the load.

Reviewer probe (scratch test, not committed): `WholePackStoreTest.richPack`, then the `tactic.graph.yaml` edge target
rewritten to `<n p's>::directive:DIRECTIVE_043`:

- n = 64: `WholePackStore.load(pack)` **loads the pack** with the edge intact. `StagedReads.contents` (loadStaged) refuses
  it with `VALIDATION_FAILED [tactic.graph.yaml drg_schema_error ... A pack id must be a kebab slug of at most 63
  characters ...]`.
- n = 63: both load.

So registration staging refuses the pack, and the strict load, which the read services use through the store, does
not. `loadStrict` and `loadStored` refuse only on `StoreRead.graphRefusal()`, and that is schema-only. Before #582 the
gap could not occur, because the schema qualifier and the kernel's `PACK_NAME` were the same language with no cap. #582
opened it on purpose (the documented exception) but closed it only in GraphBinding and staging.

Consequences:

- The contract text is inaccurate. The charter fragment says "a pack that writes one is refused at load", and the packs
  CHANGELOG says "the service refuses the pack at load". That is true only of registration staging.
- PLAUSIBLE: `ArtifactReadService.resolved` (l.~542) still says "The schemas refuse a malformed endpoint when a pack is
  read, so the grammar's parser cannot refuse one here", and calls `PackQualifiedReference.parse(edge.target())`
  unguarded. A stored pack that holds a 64-character qualifier now makes that throw `IllegalArgumentException` on an
  artifact read, where it used to resolve as an unresolved pack. The old service accepted 64 characters (the old
  `RelationshipWireTest` pinned `"p".repeat(64)` as accepted), so a pack stored before this change can hold one.

Fix, at the root:

- Make the strict store read refuse an endpoint `PackQualifiedReference.parse` refuses, as `drg_schema_error` naming the
  pack-id detail, on the path `loadStrict` and `loadStored` use. The cheapest route is to fold the GraphBinding finding,
  or the same parse check, into `StoreRead.graphRefusal`.
- Add the planned `QualifierLoadTest`, or the same cases in `WholePackStoreTest`: 64 `p`s refused by `load(...)` with
  the pack-id detail, and 63 `p`s loaded, with a positive control on the same fixture.
- Fix or remove the `ArtifactReadService.resolved` comment once the invariant holds again.
- Keep the fragment and CHANGELOG sentence true.

## LOW (fold, no issue)

- L1: The URN part `[^:\s]` also excludes white space after the kind's colon. The colon is needed for #582, the white
  space is not. The kernel still parses `directive: D1`, so this opens a small divergence in the other direction. It is
  declared under Breaking, so keep it or drop the `\s`; either way say why in the fragment. Harmless.
- L2: Stale wording. `build-logic/src/test/kotlin/RegexRecursionGuardTest.kt:81` gives an allow-list reason that names
  the removed `PackQualifiedReference.PACK_NAME_GRAMMAR`, and the comment at `RelationshipEndpointRule.java:174` says
  "breaks the pack-name grammar".
- L3: `Urn.yaml` (`^[a-z_]+:.+$`) still accepts `a_b::directive:D1`, but no reference slips through it. Kernel `Urn`
  refuses any id that holds `::`. The canonical `a-b::...` fails `Urn.yaml` (no `-` in the kind), so no Urn-typed field
  carries a reference. Pre-existing and outside #582. No action needed.
- NIT: `GuardReference.parse` does not check the pack part before the match, as D5 says. It is bounded by the total cap
  of 135 and refused by the constructor, so NFR-001 holds.

## Verified OK

- Red first: 0dfe1cf is test-only, and its body records the red run (20 tests, 11 failures).
- All 15 qualifier sites (13 + guard `implemented_by` + lifecycle `ref`) write `[a-z0-9]+(-[a-z0-9]+)*::`. A grep of
  `contracts/` for `::` in `pattern` finds exactly these. The Java qualifier paths (KindRef, Relationship,
  SubstepCandidate, GuardReference, GuardImplementation, GraphBinding, RelationshipWire) all decide through
  `PackQualifiedReference.isPackName`. `PACK_NAME_GRAMMAR` is gone from main code.
- C-004 is untouched: `fallback_profile`, `role_pins`, `Slug.yaml`, `Artifact*.kind` and `MissionSubstepDelegation`.
- The caps are consistent: pack id 63, the guard ref 63 + 8 + 64 = 135, matching `lifecycle.schema.json` `maxLength`
  135. Echoed text is cut at 64 characters.
- Breaking is declared: the charter fragment has one `### Breaking` block that names core's seam, and the packs
  CHANGELOG has a `### Breaking` block under 0.1.0-SNAPSHOT. `:contracts:check` reports "1 changelog fragment is well
  formed" and "PACKS CHANGELOG passes the heading, order and Breaking rules". `-PcontractBaseline=none` skips oasdiff,
  so CI is the first compatibility check.
- Open-packs main b426378, which is newer than the 3bedeef census: all 24 `pack.yaml` names are pack ids, and every
  `::` qualifier in a reference field is a pack id (architecture, foundation, planning-analysis, refactoring, testing,
  writing). The non-conforming `X::` hits are Rust, PHP and Python code inside prose.

## Commands run (head 5ecff25)

- `./gradlew :kernel:check :missions:check :packs:test :pack-validation:test :contracts:check -PcontractBaseline=none`:
  BUILD SUCCESSFUL.
- Planted break: `_` in the graph-shard edge `target` qualifier. QualifierParityTest fails 3 of 5 and names
  `graph-shard.schema.json#/$defs/DRGEdge/properties/target`. Restored.
- Planted break: kernel `isPackName` maps `_` to `-`. PackQualifiedReferenceTest fails 3 of 20. Restored.
- `:pack-validation:driftTest -PopenPacksDir=<open-packs main b426378>`: 1 test, 0 failures.
- `pack-check --config-root <open-packs main>` (the packCheckJar build): 19 packs, 0 problems, exit 0.
- Load probe (scratch, removed): see MEDIUM-1.
