# Mission Specification: Slice A2: preset, asset and skill reads

**Mission Branch**: `kitty/mission-slice-a2-cutover-reads-01M78RJD` (lands on `slice-a2-536-cutover-reads`, then `main` by pull request)
**Created**: 2026-11-12
**Status**: Draft
**Input**: Slice A2 (#536), the reads [ADR-0074](../../docs/adr/0074-the-remaining-cutover-reads-and-their-paths.md) added, without #106, which the operator split into its own mission on 2026-11-12.

## Intent Summary (confirmed by the operator, 2026-11-12)

A client of the charter contract reads an offering's presets, assets and skills, in a pack and under an activation, and follows links from the root to an activation and from there to its writes and reads. Core's generated client after the cutover is one such client; a runner that executes a guard (`asset:<id>`) is another. Today the service holds this content but answers no read for it, so the client must read pack files or build URLs. This mission serves those reads by ADR-0074's paths (decisions 9 to 14), each with `@contract` scenarios that pass the ADR-0059 D1 switch gate.

Decision Moments resolved at specify:

- `DM-01M78RKM6Q23PA9QAC80QJKQFF`: an activation links its writes, `offering`, and each effective read already built.
- `DM-01M78RKPH6W3YT9YRPB98DP102`: an asset or skill that is not held answers `404 artifact_not_found`; no new refusal codes.
- `DM-01M78S3RNPZ5ZFV5AR6H1X6HV1`: an asset is in force under an activation when a pack of the activation's closure holds it. Across levels the same id is a layer override (project over org over built-in). No selection, deny or cascade applies to assets.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read a pack's presets (Priority: P1)

A client lists a pack's presets, and reads one by id, to show what applying it would put in force before it applies it.

**Why this priority**: presets are P1 in epic #94 (#98). Today a preset can be applied but not read.

**Independent Test**: register a pack with two presets, list them, then read one.

**Acceptance Scenarios**:

1. **Given** a pack that holds presets `team` and `minimal`, **When** the client lists the pack's presets, **Then** it gets both, each with its id, description and kind lists.
2. **Given** the same pack, **When** the client reads preset `team`, **Then** it gets the preset's id, description and each kind's list of items, and each item says whether it is must-have.
3. **Given** a pack that holds no preset `ghost`, **When** the client reads `ghost`, **Then** it is refused with `404 preset_not_found`.
4. **Given** no pack `nope`, **When** the client lists its presets, **Then** it is refused with `404 pack_not_found`.

### User Story 2 - Read an asset and its payload (Priority: P1)

A guard runner holds the reference `asset:<id>`. It reads the asset's descriptor (its media type, and its executable block if it has one), then the asset's bytes, in the pack or under the activation in force.

**Why this priority**: a guard names its implementation as an asset (ADR-0054). Without this read, no client can run a guard.

**Independent Test**: register a pack with an executable asset and a plain one, list assets, read each item and its payload, then read the same assets under an activation that holds the pack.

**Acceptance Scenarios**:

1. **Given** a pack with asset `run` (`text/x-python`, executable) and asset `logo` (`image/png`), **When** the client lists the pack's assets, **Then** it gets both, each with id, media type and whether it is executable.
2. **Given** that pack, **When** the client reads asset `run`, **Then** it gets the descriptor with its media type, path and executable block.
3. **Given** that pack, **When** the client reads the payload of `run`, **Then** it gets the file's exact bytes with `Content-Type: text/x-python`.
4. **Given** an asset whose media type is not a well-formed `type/subtype`, **When** the client reads its payload, **Then** the bytes are served as `application/octet-stream`, and the descriptor still shows the media type as written.
5. **Given** an activation whose closure holds a built-in pack and a project pack that both carry asset `run`, **When** the client reads `run` and its payload under the activation, **Then** it gets the project pack's version.
6. **Given** a pack that holds no asset `ghost`, **When** the client reads it in the pack or under an activation, **Then** it is refused with `404 artifact_not_found`.
7. **Given** a pack whose asset id is not a lowercase kebab-case single segment (for example `a/payload` or `Run`), **When** the pack is registered, **Then** it is refused as invalid content.

### User Story 3 - Read a skill and its body (Priority: P2)

A client lists a pack's skills and reads one, with its body, to install it into a harness, in a pack or under an activation.

**Why this priority**: a cutover read (#107). Skill bodies are held, but no operation serves them.

**Independent Test**: register a pack with a prompt skill and a wrapper skill, list and read both, then read the prompt skill under an activation that selected it.

**Acceptance Scenarios**:

1. **Given** a pack with prompt skill `land-pr` and wrapper skill `wrap`, **When** the client lists skills, **Then** it gets both, each with id, title, description and form.
2. **Given** that pack, **When** the client reads `land-pr`, **Then** the answer carries the skill's fields and its body text exactly as the body file holds it.
3. **Given** that pack, **When** the client reads `wrap`, **Then** the answer carries no body.
4. **Given** an activation whose closure holds `land-pr`, **When** the client reads it under the activation, **Then** it gets the winning version, with its state and reason (in force or not), as every effective item read answers. A skill no pack of the closure holds answers `404 artifact_not_found`.

### User Story 4 - Read an in-force artifact's references (Priority: P2)

A client reading an artifact in force under an activation reads what that artifact references, without going back to a pack (#532).

**Independent Test**: an activation with a directive in force that references a tactic. Read the directive's references under the activation.

**Acceptance Scenarios**:

1. **Given** an activation with directive `D` in force, which `requires` tactic `T`, **When** the client reads `D`'s references under the activation, **Then** it gets `T` with the relation `requires`.
2. **Given** an activation whose closure holds no `D`, **When** the client reads `D`'s references, **Then** it is refused with `404 artifact_not_found`. A `D` the closure holds but that is not in force answers the references of its winning version, as every effective item read answers whatever the state.

### User Story 5 - Reach the activation surface by links (Priority: P2)

Starting from the root, a client creates an activation and selects an artifact by following links only (#409).

**Acceptance Scenarios**:

1. **Given** the root, **When** the client follows `activations` to create an activation, **Then** the activation answers `self`, `offering`, `edit`, `delete`, `select`, `exclude`, `apply-preset`, and a link to each built effective read (artifacts, graph, skipped report, packs).
2. **Given** that activation, **When** the client expands the `select` link with a kind and id and sends it, **Then** the artifact is selected.
3. **Given** any answer, **When** its links are checked, **Then** none points at an operation the contract reserves (`501`).

### Edge Cases

- An asset sidecar whose payload file is missing is already refused at registration. The read never answers a descriptor with no bytes.
- A templated item read (`…/artifacts/{kind}/{id}`) asked for `asset` or `skill` is refused with `kind_not_found`, and names the literal path, as for an agent profile.
- A conditional read with a current `If-None-Match` answers `304` on each effective (activation) twin, as the existing effective reads do.
- A preset is read only in its pack. Under an activation, `…/presets/{presetId}` stays the apply write.
- A preset under the generic `?kind=` list does not exist: a preset is not an artifact.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
| --- | --- | --- | --- | --- |
| FR-001 | A client can list a pack's presets, each with id, description, and per-kind item lists that mark must-have items (#98). | Approved | [build] | no |
| FR-002 | A client can read one preset of a pack by id. A preset the pack does not hold is refused `404 preset_not_found`, and that code's description covers a pack as well as an activation. | Approved | [build] | no |
| FR-003 | A client can list a pack's assets by the asset collection path, each with id, media type and an executable flag (#105). | Approved | [build] | no |
| FR-004 | A client can read an asset's descriptor by its literal item path: media type as written, path, title if any, and executable block if any. | Approved | [build] | no |
| FR-005 | A client can read an asset's payload. Its exact bytes are served with the media type as `Content-Type` when it is well-formed `type/subtype`, and `application/octet-stream` otherwise. | Approved | [build] | no |
| FR-006 | An asset id is one lowercase kebab-case segment of at most 64 characters. A pack with an asset id outside that pattern is refused as invalid content. | Approved | [build] | no |
| FR-007 | A client can list a pack's skills by the skill collection path, each with id, title, description and form (#107). | Approved | [build] | no |
| FR-008 | A client can read a skill by its literal item path. A prompt skill's answer carries its body exactly as held; a wrapper skill carries none. | Approved | [build] | no |
| FR-009 | Every asset and skill read of FR-003 to FR-008 has an activation twin at the same suffix, answering the version in force. For an asset, that is the closure's holder after layer override. For a skill, it is the activation's in-force resolution. | Approved | [build] | no |
| FR-010 | An asset or skill that no pack (in a pack read) or no pack of the closure (under an activation) holds is refused `404 artifact_not_found`; an effective read answers a held artifact whatever its state, as `getEffectiveArtifact` does. A missing pack is `pack_not_found`, and a missing activation is `activation_not_found`. | Approved | [build] | no |
| FR-011 | The templated item read does not render `asset` or `skill`; it refuses with `kind_not_found` and names the literal path. `?kind=asset` and `?kind=skill` list them in both trees. | Approved | [build] | no |
| FR-012 | A client can read an in-force artifact's references under an activation, at the same suffix as the pack read (#532). | Approved | [build] | no |
| FR-013 | An activation's answer links `self`, `offering`, the write relations `edit`, `delete`, `select`, `exclude` and `apply-preset` (templated by kind and id where the path needs them), and each built effective read (#409). | Approved | [build] | no |
| FR-014 | No answer links an operation the contract reserves. Every relation an answer carries is in the relation table, marked "In the contract". | Approved | [ratchet] | no |
| FR-015 | Each new operation has `@contract` scenarios for its declared answers, a `ready` entry in the switch-gate register, and its rows in the every-operation and unexpected-failure tables. | Approved | [build] | no |

### Non-Functional Requirements

| ID | Requirement | Status |
| --- | --- | --- |
| NFR-001 | `./gradlew verifyAll` passes on Java 25, with strict dependency verification and the whole-pack round trip, with 0 failures and 0 skipped contract scenarios. | Approved |
| NFR-002 | The switch-gate register reports 100% of the new operations `ready`, with every declared status asserted by at least one scenario. | Approved |
| NFR-003 | A payload read returns the stored bytes unchanged: a byte-for-byte equality check over a binary fixture (an image) passes. | Approved |
| NFR-004 | No new dependency enters the build. | Approved |

### Constraints

| ID | Constraint | Status |
| --- | --- | --- |
| C-001 | Paths, names and shapes follow ADR-0074 decisions 9 to 14 and the contract's existing conventions (camel case, one tag per controller). | Approved |
| C-002 | The charter's model is canonical: no legacy reader, and tests and examples use only the canonical shape. ADR-0019's `/packs/{packId}/assets/{path}` rows are not built. | Approved |
| C-003 | The change is additive to the 0.x contract and ships `### Added` and `### Changed` fragments under `contracts/charter/changes/`. | Approved |
| C-004 | Slice C's names are not used or changed here: the `prompt` path and link relation, its four schemas, and `mission_type_not_in_force`. This mission rebases onto slice C where they overlap. | Approved |
| C-005 | #106 (glossary), #533 (step-contract twin) and #108 (routing catalog) are out of scope. | Approved |

## Success Criteria *(mandatory)*

- SC-001: A client reads every preset, asset (with its bytes) and skill of a registered pack through the contract alone, with no pack-file access. All 15 functional requirements are proven by passing contract scenarios. — [build] · no-op passable: no
- SC-002: A guard runner holding `asset:<id>` gets the in-force bytes under an activation in one read. — [build] · no-op passable: no
- SC-003: From the root, a client creates an activation and selects an artifact by following links only, with zero hand-built URLs. — [build] · no-op passable: no
- SC-004: #98, #105, #107, #532 and #409 are closed by the pull request, and #536 is ticked. — [build] · no-op passable: no

## Key Entities

- **Preset**: a pack-level file, not an artifact. It holds an id, a description, and per kind a list of items, each optionally must-have.
- **Asset**: an artifact with a descriptor (id, media type, path, optional title, optional executable block) and a payload of exact bytes.
- **Skill**: an artifact with a title, description, form (prompt or wrapper), triggers, parameters and, for a prompt skill, a body text.
- **Activation link set**: the relations an activation answers, for its writes and its effective reads.

## Domain Language

- "In force" (not "active" or "enabled") for what an activation answers.
- "Preset" (not "profile" or "bundle"); "payload" (not "content" or "blob") for an asset's bytes; "body" for a skill's text.

## Assumptions

- Amended at plan (2026-11-12): the effective skill and references twins answer a held artifact whatever its state, following the existing effective item reads (`getEffectiveArtifact`, `getEffectiveAgentProfile`); the spec first said "not in force is 404", which would have made these two reads the only ones to judge state. Recorded in research.md R1.

- The existing effective-read machinery resolves a skill's in-force version (skill is an activatable kind).
- Scenario packs are written inline per scenario. Every asset id in charter's example packs and fixtures already matches FR-006's pattern (checked 2026-11-12), so no example pack changes. A new planted fixture proves the refusal.
