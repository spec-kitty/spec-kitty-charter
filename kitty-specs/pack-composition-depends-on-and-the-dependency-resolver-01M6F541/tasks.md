---
description: "Work packages for pack composition: depends_on and the dependency resolver"
---

# Work Packages: Pack composition: depends_on and the dependency resolver

**Inputs**: Design documents from `kitty-specs/pack-composition-depends-on-and-the-dependency-resolver-01M6F541/`
**Prerequisites**: plan.md (required), spec.md (user stories), research.md, data-model.md, quickstart.md

**Tests**: The spec demands BDD scenarios per refusal code (NFR-005) and pinned single-tree answers (SC-003);
testing work is therefore in scope for the packages below.

**Organization**: Fine-grained subtasks (`Txxx`) roll up into work packages (`WPxx`). Each work package is
independently deliverable and testable.

**Prompt Files**: Each work package references a matching prompt file in `tasks/` (flat, no subdirectories).

## Delivered on the branch before this step

The descriptor `depends_on` item (schema, `PackDependency`, storage, YAML writer), `Pep440Range`
(PEP 440 ordering and satisfaction), `PackCompositionResolver` (order, dedupe, cycle, unknown id,
out-of-range, parent, level rule, name conflict, duplicate id) and its wiring into the offering read
with ten contract refusal codes are **already implemented and tested on `issue-145-pack-composition`**
(commits `50b1330e`, `3fc77ea4`, `3c35227c`, `25b6d702`). The packages below verify and complete that
work — sibling refinement, the reference grammar, contract exposure, the compatibility ratchet and the
documented model — they do not re-implement it.

---

## Work Package WP01: Sibling refinement refusal and the `overrides` edge (Priority: P1)

**Goal**: Two sibling packs that change the same artifact in one compose are refused with
`pack_sibling_refinement_conflict` unless the distribution pack settles the contest with its own
`overrides` edge (FR-012, ADR-0048 open question 5 as ruled, ADR-0053 decision 5).
**Independent Test**: A two-sibling compose over one contested artifact is refused with the sibling code
naming both packs; the same compose with a settling `overrides` edge resolves; the layer merge across
levels is untouched.
**Prompt**: `tasks/WP01-sibling-refinement-refusal.md`
**Requirement Refs**: FR-012

### Included Subtasks

T001 Add the `overrides` edge to the pack-descriptor schema
T002 Carry `overrides` through the kernel descriptor and storage
T003 Refuse an unsettled sibling refinement in the compose resolution
T004 Test the sibling refusal, the settled compose and the layer merge
T005 Keep the refusal parity surfaces green

### Implementation Notes

- The schema item is closed and authored (never derived), mirroring how `depends_on` landed in
  `50b1330e`; the ULID and closed-world rules of ADR-0036 decision 3 apply.
- The refusal is already reserved: `PACK_SIBLING_REFINEMENT_CONFLICT` maps to 409 in `RefusalHandler`.
- Detection needs the artifacts each pack changes; ADR-0048 (amended) places the sibling-override
  refusal with the compose resolution.

### Parallel Opportunities

- None: this package owns the compose-resolution seam the later Java packages build on.

### Dependencies

- None (starting package; the resolver it extends is already on the branch).

### Risks & Mitigations

- Sibling detection may need artifact-relationship data the resolver does not hold → keep the check at
  the compose-resolution boundary, and record any out-of-map touch with a one-line rationale.

---

## Work Package WP02: The pack-qualified reference grammar (Priority: P2)

**Goal**: A reference may be written `<pack-name>::<urn>`; in-pack references stay bare; a bare
reference that only resolves in another pack is refused with `pack_reference_unqualified`, and a
qualified reference to a pack outside the closure with `pack_reference_pack_missing` (FR-013, FR-014,
FR-015, FR-017; ADR-0048 decision 10 as amended, ADR-0053 decision 7).
**Independent Test**: One shared fixture where `B::directive:<urn>` resolves and the same reference
written bare is refused naming B; a reference to `C::` with C outside the closure is refused naming C.
**Prompt**: `tasks/WP02-pack-qualified-reference-grammar.md`
**Requirement Refs**: FR-013, FR-014, FR-015, FR-017

### Included Subtasks

T006 Parse `<pack-name>::<urn>` with the URN part opaque
T007 Resolve a qualified reference against the compose closure
T008 Refuse a bare reference that only resolves in another pack
T009 Refuse a qualified reference to a pack outside the closure
T010 Test both grammar outcomes on one shared fixture

### Implementation Notes

- The two reference codes are already reserved in `RefusalCode` and `RefusalHandler` (422).
- C-005: no URN-parsing work — the `<urn>` part after `::` is treated as an opaque string; no kind
  token is parsed, split or re-spelled.

### Parallel Opportunities

- None: sequenced after WP01 because both packages touch the packs application composition seam.

### Dependencies

- Depends on WP01.

### Risks & Mitigations

- Load-order resolution could sneak back in → the bare-reference refusal is the guard; test it on the
  same fixture as the qualified resolution (SC-004).

---

## Work Package WP03: The contract change set — dependency facts and resolver scenarios (Priority: P1)

**Goal**: The contract carries what a compose resolved: `PackPin` and `OfferingPack` gain the resolved
dependency facts (FR-020), every refusal carries a contract code and names the offending pack (NFR-002), the
resolver's refusals are proven by BDD scenarios with negative controls (NFR-005), and the change set lands as one
recorded family with a `### Breaking` fragment and the
changelog entry (FR-019 completion, FR-023 changelog, C-002, C-006).
**Independent Test**: The contract suite runs one scenario per refusal code plus a negative control;
the offering read for a compose exposes each declared range and the version that satisfied it.
**Prompt**: `tasks/WP03-contract-change-set-dependency-facts-and-scenarios.md`
**Requirement Refs**: FR-001, FR-002, FR-003, FR-006, FR-007, FR-008, FR-009, FR-010, FR-016, FR-019,
FR-020, FR-023

### Included Subtasks

T011 Derive the resolved edges (declared range, satisfying version) in the offering read
T012 Add the dependency facts to `PackPin.yaml` and `OfferingPack.yaml`, and move `info.version`
T013 Write the `### Breaking` changelog fragment for the change set
T014 Author one BDD scenario and one negative control per refusal code
T015 Run the contract suite, parity tests and golden examples green

### Implementation Notes

- An additive response property is graded `ERR` by `contracts/compat/severity-levels.txt`, so the
  `info.version` move and the `### Breaking` fragment are part of the change, not optional (ADR-0022).
- The scenarios verify the descriptor, PEP 440 and resolver refusals already delivered on the branch —
  they are the end-to-end proof for FR-001 to FR-010 and FR-016, not new behaviour.
- The resolved edges are derived from the closure the resolver already returns
  (`ResolvedEdge` per data-model.md); prefer derivation beside the offering read over reshaping
  `PackCompositionResolver`, which WP01 owns.

### Parallel Opportunities

- None: the contract family must land alone (C-006).

### Dependencies

- Depends on WP01, WP02.

### Risks & Mitigations

- Another contract PR landing first → rebase this branch rather than interleaving two change sets
  (C-006); the `create` surface stays in `contracts/charter/`.

---

## Work Package WP04: The compatibility ratchet and resolver qualities (Priority: P2)

**Goal**: Prove the delivered resolver's qualities: today's single `built-in` tree answers byte-identical
except additive fields (FR-018, NFR-004, SC-003), resolution is deterministic across directory order and
repeated runs (NFR-001), a 50-pack/200-edge compose resolves within 250 ms at p95 (NFR-003), and one
compose is one closure at one level with the layer merge kept separate (FR-011, FR-004, FR-005).
**Independent Test**: The ratchet, determinism, performance and level-separation tests all pass in
`:service:packs:test` without touching production code.
**Prompt**: `tasks/WP04-compatibility-ratchet-and-resolver-qualities.md`
**Requirement Refs**: FR-004, FR-005, FR-011, FR-018

### Included Subtasks

T016 Pin the single-tree offering and pack answers (ratchet)
T017 Prove deterministic resolution across shuffled inputs and repeated runs
T018 Bound the resolution cost of a 50-pack, 200-edge compose
T019 Prove one closure at one level and the diamond order

### Implementation Notes

- New test files only; the ratchet tolerates exactly the additive fields WP03 introduces.
- The performance bound is a p95 smoke on the reference build, not a micro-benchmark harness.

### Parallel Opportunities

- Runs alongside WP05 (docs); owns only new test files.

### Dependencies

- Depends on WP03.

### Risks & Mitigations

- Timing assertions flake in CI → assert the p95 bound generously and quarantine rather than delete a
  flaky case (qa-flaky-test-quarantine).

---

## Work Package WP05: The documented composition model (Priority: P2)

**Goal**: The docs describe the model the code now has: the logical model and architecture pages cover
`depends_on`, the compose and the resolver's refusals (FR-021); the glossary and the contextive
definitions carry Compose, Closure, Dependency edge and Qualified reference (FR-022); the work-breakdown
items 2 and 3 are marked done (FR-023); every pack-model page states the new shape or links to the page
that does (SC-006).
**Independent Test**: `python3 scripts/docs/check_docs.py all`, markdownlint and cspell pass with zero
findings, and no page still says the resolver is not built.
**Prompt**: `tasks/WP05-documented-composition-model.md`
**Requirement Refs**: FR-021, FR-022, FR-023

### Included Subtasks

T020 Describe composition in the logical model and architecture pages
T021 Carry the four terms in the glossary and the contextive definitions
T022 Mark work-breakdown items 2 and 3 done
T023 Sweep every pack-model page and pass the docs checks

### Implementation Notes

- `logical-model.md` still says "the resolver is not built yet" (left by `50b1330e`); the glossary's
  Compose entry still defers to ADR-0048 open question 6, which the 2026-11-02 rulings settled.
- The changelog entry itself is WP03's fragment; this package verifies it reads where readers look.

### Parallel Opportunities

- Runs alongside WP04; owns docs and the contextive source only.

### Dependencies

- Depends on WP03.

### Risks & Mitigations

- Docs drift from code → cite the delivered commits and the contract change set, and keep terms in the
  glossary's canonical spellings.

---

## Dependency & Execution Summary

- **Sequence**: WP01 → WP02 → WP03 → (WP04 ∥ WP05).
- **Parallelization**: WP04 and WP05 are independent of each other once the contract set (WP03) lands.
- **MVP Scope**: WP01 plus WP02 deliver the remaining behaviour; WP03 is the contract record; WP04 and
  WP05 are the proof and the documentation. The already-landed commits are the descriptor and resolver
  core.

---

## Requirements Coverage Summary

| Requirement ID | Covered By Work Package(s) |
|----------------|----------------------------|
| FR-001 | WP03 |
| FR-002 | WP03 |
| FR-003 | WP03 |
| FR-004 | WP04 |
| FR-005 | WP04 |
| FR-006 | WP03 |
| FR-007 | WP03 |
| FR-008 | WP03 |
| FR-009 | WP03 |
| FR-010 | WP03 |
| FR-011 | WP04 |
| FR-012 | WP01 |
| FR-013 | WP02 |
| FR-014 | WP02 |
| FR-015 | WP02 |
| FR-016 | WP03 |
| FR-017 | WP02 |
| FR-018 | WP04 |
| FR-019 | WP03 |
| FR-020 | WP03 |
| FR-021 | WP05 |
| FR-022 | WP05 |
| FR-023 | WP03, WP05 |

---

## Subtask Index (Reference)

| Subtask ID | Summary | Work Package | Priority | Parallel? |
|------------|---------|--------------|----------|-----------|
| T001 | `overrides` edge in the pack-descriptor schema | WP01 | P1 | No |
| T002 | `overrides` through kernel descriptor and storage | WP01 | P1 | No |
| T003 | Refuse an unsettled sibling refinement | WP01 | P1 | No |
| T004 | Sibling refusal, settled compose, layer-merge tests | WP01 | P1 | No |
| T005 | Refusal parity surfaces green | WP01 | P1 | No |
| T006 | Parse `<pack-name>::<urn>` (URN opaque) | WP02 | P2 | No |
| T007 | Resolve a qualified reference against the closure | WP02 | P2 | No |
| T008 | Refuse a bare reference resolving only elsewhere | WP02 | P2 | No |
| T009 | Refuse a qualified reference outside the closure | WP02 | P2 | No |
| T010 | Shared-fixture grammar tests | WP02 | P2 | No |
| T011 | Derive resolved edges in the offering read | WP03 | P1 | No |
| T012 | Dependency facts in `PackPin.yaml`/`OfferingPack.yaml`, `info.version` | WP03 | P1 | No |
| T013 | `### Breaking` changelog fragment | WP03 | P1 | No |
| T014 | BDD scenario + negative control per refusal code | WP03 | P1 | No |
| T015 | Contract suite, parity and goldens green | WP03 | P1 | No |
| T016 | Single-tree ratchet | WP04 | P2 | Yes (with WP05) |
| T017 | Deterministic resolution proof | WP04 | P2 | Yes (with WP05) |
| T018 | 50-pack/200-edge cost bound | WP04 | P2 | Yes (with WP05) |
| T019 | One closure at one level, diamond order | WP04 | P2 | Yes (with WP05) |
| T020 | Composition in logical model and architecture pages | WP05 | P2 | Yes (with WP04) |
| T021 | Glossary and contextive terms | WP05 | P2 | Yes (with WP04) |
| T022 | Work-breakdown items 2 and 3 marked done | WP05 | P2 | Yes (with WP04) |
| T023 | Pack-model page sweep and docs checks | WP05 | P2 | Yes (with WP04) |
