# Mission Specification: Whole-pack load and write through the Pack aggregate

**Mission Branch**: `feat/189-whole-pack-seam`
**Created**: 2026-10-27
**Status**: Draft
**Input**: Issue [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189), part of epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186) (Wave 0, alongside #187 and #188).

> These mission documents were written by hand. The session could not install the Spec Kitty CLI
> (permission denied), so there is no CLI-generated `meta.json`, status log or lane file.

## Intent Summary

The **primary actor** is the whole-pack round-trip gate (#187) and the kind missions (#190 to
#195) that turn it green. The **trigger** is a pack directory to load, or an aggregate to write.
The **outcome**: one call loads a whole pack (descriptor, every bound kind, the DRG documents)
into the `Pack` aggregate, and one call writes that aggregate to a fresh directory; a new kind
joins both by registering itself. The **invariant**: no code path enumerates the kinds by hand,
so a kind the code does not name cannot be dropped.

**Confirmation basis.** The operator's brief for this mission asks for the kind-keyed
restructure first, as a narrow slice of #169, recorded in an ADR. The grounding and the open
questions are on [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189#issuecomment-6435708786);
the mission proceeds on the defaults stated there. ADR-0037 is **Proposed** and the PR must not
merge until the operator accepts it. The #188 fidelity record has landed as ADR-0036 (Proposed); this
mission aligns with it (ADR-0037 decision 7).

## Grounding (main 0b2503b)

- `Pack` holds five typed lists and ten per-kind invariants. `FilesystemPackStore`,
  `SerializationStrategy`, `DomainRepositoryService` and `PackFileSet.LoadedKind` are wired per
  kind.
- Four registration services rebuild a pack by copying each kind by hand, and
  `RelationshipRegistrationService` lists the kinds to collect URNs. A new kind would be dropped on
  commit.
- `PackContents` (the inspector's facts) is already kind-generic and is not changed.
- Real packs place artifacts in category subdirectories, and 69 of 300 artifact files have a name
  other than their id, so a write must keep each file's path.
- The DRG reader drops `tags`, `provenance`, `generated_by` and fragment `body_path`; there is no
  fragment writer.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A new kind registers instead of editing the aggregate (Priority: P1)

A kind mission adds its entity, DSO, YAML format and schema and registers a binding. The aggregate,
the store, the strategy and the registration services need no edit.

**Independent Test**: A test lists the registered kinds and shows that each one loads, writes, and
survives a registration of another kind; the per-kind fields and methods are gone from `Pack`,
`FilesystemPackStore`, `SerializationStrategy` and `DomainRepositoryService`.

**Acceptance Scenarios**:

1. **Given** the five bound kinds, **When** a pack is loaded, **Then** every artifact of every kind is in the aggregate, as before.
2. **Given** a pack with artifacts of several kinds, **When** a directive is registered, **Then** the committed pack still holds every artifact of every other kind.
3. **Given** a binding without a YAML format, **When** the registry is checked, **Then** a test fails.

### User Story 2 - The gate loads a whole pack and writes it back (Priority: P1)

The round-trip gate loads a pack directory and writes the aggregate to a fresh directory.

**Independent Test**: A test loads each fixture pack and the open-packs packs, writes them, reloads
the written copy and compares the aggregates by meaning.

**Acceptance Scenarios**:

1. **Given** a pack directory, **When** it is loaded, **Then** the aggregate holds the descriptor and every artifact of every bound kind, and beside it come the layout, every DRG document with every schema field and the list of unmodelled files.
2. **Given** a loaded pack and its layout, **When** it is written to a directory that does not exist, **Then** each artifact lands at its source path, the DRG documents at theirs, and reloading gives an equal aggregate.
3. **Given** a target that exists, **When** a write is attempted, **Then** it is refused and the target is unchanged.
4. **Given** a pack with read violations, **When** a write is attempted, **Then** it is refused and nothing is created.
5. **Given** a failure while writing, **When** the write aborts, **Then** no target and no staging directory remain.

### Edge Cases

- A pack with no `pack.yaml` is refused on load (as today); `packs/qa` is a pack fix, raised on #189.
- A new artifact with no recorded path is written at `<plural>/<id>.<kind>.yaml`.
- Edges compare in canonical form (order and duplicates do not count), per the operator's ruling.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| FR-001 | The `Pack` aggregate keys its artifacts by `ArtifactKind`, with generic belongs-to-pack and unique-id invariants. | Draft |
| FR-002 | `Pack.with(artifact)` adds or replaces one artifact and revalidates; registration uses it. | Draft |
| FR-003 | Storage binds each kind once (`KindBinding`); the registry is the single list of loaded and fingerprinted kinds. | Draft |
| FR-004 | `SerializationStrategy` reads and writes any bound kind through one kind-parameterized pair. | Draft |
| FR-005 | A whole pack loads from a directory into the aggregate, with the layout, both DRG document shapes with every schema field, and the unmodelled files beside it. | Draft |
| FR-006 | A whole pack writes to a target that does not exist, through a staging directory and a rename, refusing a pack with violations or undeclared descriptor keys. | Draft |
| FR-007 | The five existing kinds behave as before: every existing test passes and the HTTP contract is unchanged. | Draft |

### Non-Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| NFR-001 | ArchUnit rules hold: `packs.domain` names no storage type; the kernel is unchanged. | Draft |
| NFR-002 | A failed whole-pack write leaves no target and no staging directory. | Draft |

### Constraints

| ID | Constraint | Status |
|----|------------|--------|
| C-001 | No change to the HTTP contract or to the #187 gate files. | Draft |
| C-002 | Unknown fields stay refused (the schemas forbid them); no special logic for pack inconsistencies. | Draft |

## Success Criteria *(mandatory)*

- **SC-001**: Adding a kind touches no file of the aggregate, the store, the strategy or the registration services.
- **SC-002**: Every open-packs and fixture pack loads, writes and reloads to an equal aggregate for the bound kinds and DRG documents.
- **SC-003**: `./gradlew verifyAll` is green on Java 25.

## Out of scope

- New kinds (#190 to #194) and pack-level files (#195).
- The fidelity policy (#188) and the gate itself (#187).
- A contribution seam for kinds in other slices (ADR-0037 decision 6).
