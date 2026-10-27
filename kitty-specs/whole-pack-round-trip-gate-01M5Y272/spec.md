# Mission Specification: Whole-pack round-trip gate

**Mission Branch**: `feat/whole-pack-round-trip-gate`
**Created**: 2026-10-27
**Status**: Draft
**Input**: Issue #187 (part of epic #186), narrowed by the operator brief for the Wave 0 mission: load the built-in Charter Pack at the pinned open-packs commit, write it through the existing storage, read it back and compare by meaning; report per kind and per file; own an append-only exclusion list; land red-first so each Wave 1 mission (#190–#195) flips only its own kinds green; not a required check yet. Grounded by a pre-spec squad (researcher-robbie, architect-alphonso, planner-priti); the findings and the operator questions are on #187 (comment 6435722435).

## Intent Summary

- **Primary actor**: a charter-service contributor working on a child of epic #186, and the reviewer of their pull request.
- **Trigger**: the contributor runs the whole-pack gate (locally or in CI) against the pinned built-in pack.
- **Outcome**: a report that says, for every file of the built-in pack, whether its meaning survives read → write → read-back (green), is known not to yet (expected red, with the reason and the issue that owns it), or is out of the gate's reach (excluded, with a reason). The gate passes exactly when that report matches the checked-in expectation.
- **Invariant**: nothing is silently skipped and nothing silently changes. Every file is accounted for exactly once; an unexpected red fails; an expected red that turns green also fails until the expectation is updated (two-way ratchet).
- **Boundaries**: the gate does not change how packs are read or written, does not edit the `Pack` aggregate or the filesystem pack store (owned by #189), and is not wired into the required build check.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See which parts of the built-in pack survive a round trip (Priority: P1)

A contributor runs the gate. It loads every file of the built-in pack at the pin, writes the modelled content to a fresh directory through the service's storage, reads it back, and compares each written file with its source by meaning. The contributor sees a per-kind summary (green / expected red / excluded counts) and a per-file status with the first difference for any red file.

**Why this priority**: This is the safety net epic #186 depends on. Without it no child can show that it turned its part green.

**Independent Test**: Run the gate against the pinned checkout; the report lists every built-in file once; the five modelled kinds, `pack.yaml` and the graph shards are compared; the counts per kind match the census of the pinned pack.

**Acceptance Scenarios**:

1. **Given** the pinned, clean built-in pack, **When** the gate runs, **Then** each file of a modelled kind is read, written to a fresh directory, read back, and compared with its source; a file whose meaning is unchanged is reported green.
2. **Given** a modelled file whose written form loses a field or a value present in the source, **When** the gate compares them, **Then** that file is reported red with the path of the first difference (positive control: a deliberately lossy writer in a fixture turns its file red).
3. **Given** the gate has run, **When** the contributor reads the report, **Then** every file under the built-in pack appears exactly once, as green, expected red or excluded, and the per-kind counts add up to the total file count.

---

### User Story 2 - Land red first and ratchet (Priority: P1)

The gate lands while most kinds are not yet modelled. Those kinds, and any modelled file that is known to differ, are recorded as expected red with a reason and an owning issue. The gate passes when the observed result matches that expectation, and fails when it does not, in either direction.

**Why this priority**: Without the ratchet a red-first gate either breaks the build or rots: an unexpected regression must fail, and a fix must be recorded so it cannot regress silently later.

**Independent Test**: With the checked-in expectation the gate passes; removing one expected-red entry makes it fail naming that file; adding an expected-red entry for a green file makes it fail with "unexpectedly green: remove it from the expectation".

**Acceptance Scenarios**:

1. **Given** a file is red and listed as expected red, **When** the gate runs, **Then** the gate passes and the report shows it as expected red with its reason and owning issue.
2. **Given** a file is red and not listed, **When** the gate runs, **Then** the gate fails and names the file and its first difference.
3. **Given** a file is listed as expected red but now round-trips, **When** the gate runs, **Then** the gate fails and tells the contributor to remove the entry.
4. **Given** a kind that has no reader/writer yet (for example procedure), **When** the gate runs, **Then** all of its files are reported expected red under that kind, through one kind-level entry, without failing the gate.

---

### User Story 3 - Refuse unknown fields and compare edges canonically (Priority: P2)

The comparison follows the operator rulings recorded on #188: unknown fields are refused, equality is by meaning not bytes, and DRG edges compare in canonical form where order and duplicates do not count.

**Why this priority**: It makes the gate's verdict mean what the epic needs. Without it a green could hide dropped content or a red could be noise from formatting.

**Independent Test**: A fixture file with a field outside its schema is reported red (refused), never green; a graph shard with permuted and duplicated edges is equal to its source; one changed or removed edge is not.

**Acceptance Scenarios**:

1. **Given** a fixture file of a modelled kind with a field outside its schema, **When** it is put through the gate, **Then** it is reported red as refused, naming the field and the file.
2. **Given** a graph shard whose edges are reordered and one edge is duplicated, **When** it is compared with its source, **Then** they are equal.
3. **Given** a graph shard where one edge's relation or target differs, **When** it is compared, **Then** they are unequal and the report names the edge.
4. **Given** two files that differ only in key order, quoting style, comments or whitespace, **When** they are compared, **Then** they are equal.

---

### User Story 4 - Exclude with a reason (Priority: P3)

Files the gate cannot meaningfully compare (for example a README, a `.gitkeep` or a Python helper inside an asset) are listed in an exclusion list the gate owns, each with a reason. The list only grows; an entry that no longer matches any file fails the gate so it cannot go stale.

**Why this priority**: Exclusions keep the "every file accounted for" invariant honest without pretending such files are modelled.

**Independent Test**: An excluded file is reported excluded with its reason; an exclusion that matches nothing fails the gate.

**Acceptance Scenarios**:

1. **Given** a file matched by an exclusion entry, **When** the gate runs, **Then** it is reported excluded with the entry's reason and is not compared.
2. **Given** an exclusion entry that matches no file of the pinned pack, **When** the gate runs, **Then** the gate fails naming the stale entry.
3. **Given** a file matched by both an exclusion and a modelled kind, **When** the gate runs, **Then** the gate fails (a modelled file must not be excluded).

### Edge Cases

- The open-packs checkout is missing, not at the pin, or dirty: the gate fails with the command that fixes it; it never skips.
- A modelled kind has zero files in the pinned pack (moved or renamed): the gate fails, so it cannot pass vacuously.
- A modelled file the reader refuses (schema violation, unknown field): reported red as refused, with the reader's reason.
- Explicit empty lists (`[]`) and explicit `null` versus an absent key: compared strictly (as different) until the #188 ADR rules otherwise; affected files are expected red with that reason (operator question 2 on #187).
- Graph-shard nodes compare as a set keyed by URN; edge duplicates in the source are listed in the report as information but do not make the file red.
- List order inside an artifact (steps, references) counts; only DRG edges and nodes are order-free.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | The gate loads the built-in pack from the pinned open-packs checkout and fails (never skips) when the checkout is missing, moved or dirty | As a contributor, I want the gate to run only against the pinned content so that its verdict is reproducible. | High | Open | [build] | no |
| FR-002 | Each file of a modelled kind (directive, tactic, styleguide, toolguide, paradigm), `pack.yaml` and every graph shard is read, written to a fresh directory through the existing storage, and read back | As a contributor, I want the real read and write paths exercised so that the gate tests what the service does. | High | Open | [build] | no |
| FR-003 | The written file is compared with the source file by meaning: both are parsed, and maps compare by key, scalars by value, lists in order; a missing, extra or changed key or value is a difference | As a reviewer, I want source-versus-written comparison so that a field dropped by both reader and writer cannot pass. | High | Open | [build] | no — positive control: a fixture with a deliberately lossy write is red |
| FR-004 | Graph-shard edges compare as a canonical set over every edge field (order and duplicates do not count); nodes compare as a set keyed by URN | As a reviewer, I want DRG edges compared in canonical form per the #188 ruling so that ordering noise is not a failure. | High | Open | [build] | no — paired control: permuted+duplicated edges equal; one changed edge unequal |
| FR-005 | A file the reader refuses (including a field outside its schema) is reported red as refused with the reader's reason | As a reviewer, I want unknown fields refused so that the gate enforces the #188 ruling. | High | Open | [build] | no |
| FR-006 | The gate reports per kind (green, expected red, excluded counts) and per file (status, and the first difference or reason) | As a contributor, I want per-kind and per-file reporting so that each child of #186 can see its part turn green. | High | Open | [build] | no |
| FR-007 | Every file under the built-in pack is accounted for exactly once (green, expected red or excluded) and the counts add up to the file total | As a reviewer, I want nothing silently skipped so that green means covered. | High | Open | [build] | no |
| FR-008 | A checked-in expectation lists expected-red items (whole kinds or single files), each with a reason and an owning issue; the gate passes only when observed reds equal expected reds | As a contributor, I want a red-first baseline so that the gate lands without breaking the build. | High | Open | [build] | no — paired control: an unlisted red fails; a listed green fails |
| FR-009 | An expected-red entry whose file or kind now round-trips fails the gate with an instruction to remove the entry | As a maintainer, I want a two-way ratchet so that a fix cannot regress silently later. | High | Open | [build] | no |
| FR-010 | The gate owns an append-only exclusion list, each entry with a reason; a stale entry (matching no file) or an entry that matches a modelled file fails the gate | As a maintainer, I want exclusions that cannot rot so that "excluded" stays an honest status. | Medium | Open | [build] | no |
| FR-011 | The gate's load-and-write step sits behind a seam the harness defines, so #189's whole-pack load/write can replace the per-kind adapter without changing the comparator, report or ratchet | As the #189 implementer, I want one replaceable adapter so that switching to the whole-pack seam is a small change. | Medium | Open | [build] | yes — structural; checked by review and by the #189 note |
| FR-012 | The gate runs as its own build task, separate from the required check, against the same pinned checkout | As the operator, I want the gate visible but not required until it is green. | High | Open | [build] | no — `verifyAll` does not run it; the task does |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Runtime | The gate completes over the full built-in pack in under 60 seconds on a CI runner (excluding build compilation). | Performance | Medium | Open |
| NFR-002 | Determinism | Two runs over the same pinned checkout produce byte-identical reports. | Reliability | High | Open |
| NFR-003 | Diagnosability | Every failure names the file (pack-relative path), the kind, and either the first difference path (e.g. `edges[3].relation`) or the stale/unexpected entry. | Usability | High | Open |
| NFR-004 | Isolation | The gate writes only under a per-run temporary directory; the open-packs checkout is unchanged after a run (checked by the clean-tree check). | Safety | High | Open |

### Constraints

| ID | Title | Constraint | Rationale | Status |
|----|-------|------------|-----------|--------|
| C-001 | No edits to #189-owned code | `Pack.java` and `FilesystemPackStore` are not changed; a needed seam is defined in the harness and noted on #189. | Parallel Wave 0 missions; avoid collisions. | Open |
| C-002 | Not a required check | The gate is not a dependency of `check`/`verifyAll` and its tag is excluded from the existing test tasks. | Operator brief; #187 becomes required only once green. | Open |
| C-003 | No special logic for pack inconsistencies | The comparator has no per-file special cases; an inconsistency in a pack is recorded as expected red with an upstream issue and fixed in the pack. | Operator ruling 3 on #188. | Open |
| C-004 | Built-in pack only | Internal, org and this repository's vendored packs are out of scope for this mission. | Operator brief; follow-up raised as question 1 on #187. | Open |
| C-005 | Comparator policy aligns with #188 | The comparison rules are one replaceable policy; when the #188 ADR lands, the policy is aligned with it. | #188 owns the fidelity policy. | Open |
| C-006 | Exclusion list is append-only | Entries are added, never edited or removed, except when an entry goes stale and the gate demands its removal. | Operator brief. | Open |

## Key Entities

- **Gate run**: one execution over the pinned built-in pack; produces a report.
- **File result**: one per built-in file: kind, pack-relative path, status (green, red, expected red, excluded), and the first difference or reason.
- **Kind summary**: per kind, the counts of each status.
- **Expectation (baseline)**: checked-in list of expected-red items (a kind, or a file), each with reason and owning issue.
- **Exclusion list**: checked-in list of file patterns the gate does not compare, each with a reason.
- **Meaning comparison policy**: the rules that decide whether two parsed documents are equal (map, list, scalar, DRG edge and node rules).

## Assumptions

- The pinned open-packs commit (`openPacksRef` in gradle.properties) is the corpus; bumping the pin may change the expectation and is done in its own change.
- The five modelled kinds and `pack.yaml` already refuse fields outside their schemas, except `pack.yaml`, whose schema allows extra keys (operator question 3 on #187). The built-in `pack.yaml` has none today.
- Until #188 lands, empty list / null versus absent compare strictly.
- Only `.yaml` files can be modelled today; other files of a modelled kind's directory (for example toolguide companion `.md` files) are either expected red under their owning kind or excluded with a reason, decided per file class in the plan.

## Success Criteria *(mandatory)*

- **SC-001**: 100% of the files under the built-in pack at the pin appear in the report exactly once. — [build] · no-op passable: no
- **SC-002**: Every file of the five modelled kinds whose meaning survives is green; the remaining reds in those kinds are each listed with a reason and an owning issue. — [build] · no-op passable: no
- **SC-003**: Changing one entry of the expectation in either direction makes the gate fail with a message naming that entry. — [build] · no-op passable: no
- **SC-004**: The required build check passes without running the gate; the gate's own task passes on the pinned checkout. — [build] · no-op passable: no
