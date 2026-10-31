# Mission Specification: The activation set: ActivationConfig and the reserved activation operations

**Mission Branch**: `issue-125-activation-set`
**Created**: 2026-10-31
**Status**: Draft
**Version ruling changed 2026-10-31 (operator, relayed)**: the set lands inside the unreleased `0.1.0-SNAPSHOT` under its `### Breaking` subsection; `info.version` stays `0.1.0-SNAPSHOT` (ruling 12 superseded).

**Input**: Issue [#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125) with its three comments, ADR-0020 (D3-D10), ADR-0022 D5, and the operator rulings of 2026-10-31 on the 14 pre-spec questions (all as recommended; `research/pre-spec-research.md`, decision `DM-01M691JAA6ZCQZMNH8G6ZKRVAT`).

## Intent Summary (confirmed)

- **Primary actor**: a contract consumer, first core's seam, which generates its client from the
  charter contract and maps its own activation files to and from the contract's activation document.
- **Trigger**: core's deny-list work codes against the contract now (ADR-0022 D5), so the activation
  set must be declared before it is built.
- **Outcome**: the unreleased contract `0.1.0-SNAPSHOT` (operator ruling 2026-10-31; first planned as `0.2.0-SNAPSHOT`) declares the activation document
  (`ActivationConfig`), its envelope (`Activation`), the write result (`ActivationChange`) and the
  skipped report (`SkippedReport`), plus every activation operation in full: request bodies,
  success responses, refusals and conditional headers. Every operation answers `501 Not Implemented`
  until it is built, and a contract scenario pins that.
- **Rule that always holds**: a reserved operation declares `501` and carries `x-reserved`; a built
  operation does neither. No link advertises a reserved operation.
- **Boundary**: this mission declares; it builds no activation behaviour, no storage, and no
  security scopes (ADR-0020 D12 is a later minor).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Core generates a client for the activation set (Priority: P1)

Core's seam vendors the contract bundle and generates its client. It gets typed models for the
activation document and typed methods for every activation operation, with their bodies, statuses,
refusal codes and headers, so it can code its mapping and its deny-list against them now.

**Why this priority**: it is why the set is declared early (ADR-0020 D8, ADR-0022 D5).

**Independent Test**: bundle the contract, generate the server interfaces, and find one
`Activation` interface with one method per operation and the four closed models.

**Acceptance Scenarios**:

1. **Given** the contract bundle, **When** it is validated and linted, **Then** it declares 13
   activation operations under the tag `Activation`, each with `x-reserved` and a `501`.
2. **Given** the `ActivationConfig` JSON Schema file, **When** it is compared with the contract's
   `ActivationConfig` component, **Then** they accept and refuse the same documents (parity test).

### User Story 2 - A client calling a reserved operation learns it is not built (Priority: P1)

A client sends any activation request to the running service. It gets `501` with a `Problem` body
and no refusal code, and the Charter headers, so it can fall back without guessing.

**Why this priority**: the contract's promise is only as good as what the service answers.

**Independent Test**: one `@contract @reserved` scenario per operation sends the request and checks
`501`, `application/problem+json`, no `code`.

**Acceptance Scenarios**:

1. **Given** the running service, **When** a client sends `PUT /activations/a-1` with a valid
   `ActivationConfig`, **Then** the answer is `501` Problem without `code`.
2. **Given** the running service, **When** a client sends `GET /activations/a-1/skipped`, **Then**
   the answer is `501` Problem without `code`.

### User Story 3 - A contract author cannot ship a half-reserved operation (Priority: P2)

An author who adds `x-reserved` but forgets the `501`, or builds an operation and leaves its `501`
or `x-reserved`, gets a lint error naming the rule.

**Independent Test**: one planted fixture per new rule fails exactly that rule; `clean.yaml`
passes every rule.

**Acceptance Scenarios**:

1. **Given** a reserved operation without `501`, **When** the contract is linted, **Then**
   `reserved-operation-declares-501` fails.
2. **Given** an operation with a `501` but no `x-reserved`, **When** linted, **Then**
   `built-operation-declares-no-501` fails.
3. **Given** an `x-reserved` whose `contractSet` or `issue` is missing or malformed, **When**
   linted, **Then** `x-reserved-shape` fails.
4. **Given** a `DELETE` answering `204` with no body, **When** linted, **Then**
   `error-responses-are-problems` passes, and a `204` with a body fails `no-content-has-no-body`.

### User Story 4 - Core learns why a write would be refused (Priority: P2)

Core reads the reserved refusal codes and the two new `CharterRefusal` members, so its seam can
translate a `409 artifact_required` naming every requiring pack, or a `422 activation_invalid`
with JSON Pointer violations, before any operation is built.

**Independent Test**: the contract declares six new codes with `x-refusal` and `raised: false`, a
`@reserved` declaration scenario pins each, and an architecture test proves no code raises them.

### Edge Cases

- `missionTypes` absent and `[]` both mean "none in force", but stay distinct on the wire so a
  fetched document round-trips with the same revision.
- `selected.skill` absent means only the required skills (derived from presets' must-have items,
  ADR-0031/0042), not every skill.
- A `deactivated` entry must be a non-empty list; an empty list is not canonical and is refused by
  the schema.
- `null` is never a value of any `ActivationConfig` member.
- A `501` carries no code, so the scenario harness's "every observed refusal is raised" check does
  not trip on it.
- `If-None-Match: *` on `PUT` creates only; `If-Match` on sub-resource writes is required (`428`
  without it), and `412` on a stale one.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | ActivationConfig is closed and complete | As core's seam, I want a closed `ActivationConfig` with `schemaVersion` (const 1, required), `packs` (items `{packId, version?}`, merge order, later wins), `activatedKinds`, `missionTypes`, `selected` (kind -> id list, the 11 activatable kinds), `deactivated` (kind -> non-empty id list), `languages`, in that property order, and with per-entry provenance, `contextActivations` and `appliedPresets` explicitly left out, so that I can map my files to and from it. | High | Open | [build] | no — the schema does not exist |
| FR-002 | JSON Schema 2020-12 file and OpenAPI mirror agree | As a consumer, I want `contracts/offering/schemas/ActivationConfig.schema.json` (JSON Schema 2020-12, ADR-0017 pattern) and the contract component `ActivationConfig` to accept and refuse the same documents, checked by a test that fails on divergence. | High | Open | [build] | no — paired accept/refuse corpus on both schemas |
| FR-003 | Activation envelope | As a client, I want `Activation` with `activationId`, `revision` (`sha256:` digest), `config` (`ActivationConfig`), `summary` (counts: active, inactive, excluded, required, locked, skipped) and `_links` (`self` only), closed. | High | Open | [build] | no |
| FR-004 | ActivationChange and SkippedReport | As a client, I want `ActivationChange` (`revision`, the ids applied, the exclusions cleared, `skipped`, `_links`) and `SkippedReport` (`skippedByScope`, `skippedDeactivated` entries with `viaRequires` and `requiredBy`, `unreachedBehindDeactivated`, `skippedShared` entries with the referencing active artifact), closed. | High | Open | [build] | no |
| FR-005 | Every activation operation declared in full | As core, I want the 13 operations of ADR-0020 D3/D6/D7 declared with their parameters, request bodies (`ActivationConfig`; select `{cascade, includeDeactivated}`; deactivate `{cascade}`; preset `{force}`), success statuses (`200`, `201` with `Location`/`ETag`, `204`), refusals (`404`, `409`, `412`, `415`, `422`, `428`) and `ETag`, under the tag `Activation`. | High | Open | [build] | no — none is declared today |
| FR-006 | Effective reads declared with closed schemas | As core, I want `…/artifacts{?kind,state}` (`EffectiveArtifactList`), `…/skipped` (`ActivationSkippedReport`), `…/graph` (`EffectiveGraph`) and `…/mission-types/{missionType}/actions/{action}/context{?depth}` (`EffectiveActionContext`), each carrying `revision`, `governanceFingerprint` and `_links.self`, closed and minimal. | High | Open | [build] | no |
| FR-007 | Reserved marker and 501 | As a consumer, I want each activation operation to carry `x-reserved: {contractSet: activation, issue: "#125"}` and a `501` Problem response with no code. | High | Open | [build] | no |
| FR-008 | Conditional headers | As a client, I want `If-Match` and `If-None-Match` declared where ADR-0020 D3/D6 use them and `ETag` on every success that returns a document or change, with the revision defined as `sha256:` of the RFC 8785 (JCS) serialization of the canonical config, keys in schema order. | High | Open | [build] | no |
| FR-009 | Cascade, select, deactivate and preset bodies | As core, I want `cascade` as a non-empty array of activatable kind tokens or exactly `["all"]` (absent = no cascade; `all` mixed with kinds is a semantic `422`), `includeDeactivated` boolean, `force` boolean, each closed, with the verb semantics of ADR-0020 D6 stated in the operation descriptions, and `mission_type` admitted as a `{kind}` (and cascade source) under selections only. | High | Open | [build] | no |
| FR-010 | Six reserved refusal codes | As core, I want `activation_not_found`, `artifact_not_found`, `preset_not_found` (404), `artifact_required`, `artifact_locked` (409) and `activation_invalid` (422) in `CharterRefusalCode` with `x-refusal` entries and `raised: false`, in the service's `RefusalCode`, and proven raised by no operation. | High | Open | [build] | no |
| FR-011 | CharterRefusal requiredBy and violations | As core, I want optional `requiredBy` (list of `{packId, via}`) and `violations` (list of `{pointer, code, detail}`, `pointer` an RFC 6901 JSON Pointer) on the refusal body, added to the vendored shared `Problem` and recorded in `_shared/VENDORED.md`. | High | Open | [build] | no |
| FR-012 | Lint: reserved shape and 501 pairing | As a contract author, I want lint rules `x-reserved-shape`, `reserved-operation-declares-501` and `built-operation-declares-no-501`, each with a planted fixture that fails only it. | High | Open | [build] | no — fixtures fail without the rules |
| FR-013 | Lint: 204 is a success | As a contract author, I want `error-responses-are-problems` to admit `204`, and a `204` to be held to having no body. | High | Open | [build] | no — a `204` fails the rule today |
| FR-014 | A 501 pin per operation | As a maintainer, I want one `@contract @reserved` scenario per activation operation that sends it and sees `501` Problem without code and with the Charter headers, and the scenarios README to admit this form of `@reserved`. | High | Open | [build] | no — today the route is absent (404) |
| FR-015 | The service answers 501 | As a client, I want the service to implement the generated `Activation` interface with one controller that answers `501` Problem (no code) for every operation, without touching storage. | High | Open | [build] | no |
| FR-016 | Declaration pins for the codes | As a maintainer, I want a `@contract @reserved` declaration scenario per new refusal code (status, retryability, raised by no operation). | Medium | Open | [build] | no |
| FR-017 | CHANGELOG and version | As core, I want the activation set recorded in the CHANGELOG under `## 0.1.0-SNAPSHOT`, in its `### Breaking` subsection, listing each break and what core's seam has to change, with `info.version` unchanged at `0.1.0-SNAPSHOT` (operator ruling 2026-10-31). | High | Open | [build] | no |
| FR-018 | ADR-0020 amendment | As a reader, I want ADR-0020 amended in place with the 2026-10-31 rulings: its open questions 1 and 2 ruled (left out), `appliedPresets` left out, `packs` as `{packId, version?}`, the cascade shape, the 11 kinds, the effective-read schemas, the JSON Schema mirror (D4 "references" becomes "mirrors"), the `@reserved` 501 pin, D10's "vendored `Problem` is unchanged" corrected, the validators (ETag, `governanceFingerprint` on `Activation`), the version ruling, and the build notes; plus the ADR index row. | Medium | Open | [build] | no |
| FR-019 | Architecture doc in step | As a reader, I want `docs/architecture/offering-and-activation.md` and the contract-facing docs (`docs/api/service-contract.md`, status-code conventions) to show the declared shapes, with the functional scenarios still marked proposed. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Whole build green | `./gradlew verifyAll` on Java 25 passes with 0 failures, and `:contracts:check`, `lintFixtures` and the contract scenarios pass. | Quality | High | Open |
| NFR-002 | No new suppressions | 0 new `@SuppressWarnings`, lint `ignore`, Checkstyle suppressions, NullAway exclusions or test `@Disabled`. | Quality | High | Open |
| NFR-003 | Closed shapes | 100% of the new titled object schemas are closed (`additionalProperties: false`) and pass `checkClosedResponseObjects`. | Quality | High | Open |
| NFR-004 | Every property cited | 100% of new properties carry `x-source` or `x-derived` and a description (`resource-property-cited`, `property-described`). | Quality | High | Open |
| NFR-005 | Docs checks green | `check_docs.py all`, markdownlint-cli2 and cspell report 0 errors on changed docs. | Quality | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Declare, do not build | No activation storage, repository port, use case or effective-set algebra is built; every operation answers `501`. | Scope | High | Open |
| C-002 | No security reshape | No oauth2 scheme or scopes (ADR-0020 D12 is a later minor); the activation operations use the existing security scheme. | Scope | High | Open |
| C-003 | No links to reserved operations | No `_links` relation anywhere points at a reserved operation (ADR-0019 D3); `Activation._links` carries only `self`. | Technical | High | Open |
| C-004 | Charter's own model | The vocabulary aligns with this repository's `charter.schema.json` (13 activation keys after PR #288) and ADRs, citing core for content alignment only; no core issue is a prerequisite (epic #186). | Governance | High | Open |
| C-005 | Supported keywords only | Schemas used by the OpenAPI contract use only keywords the bundler, ClosureChecker and ContractConformance accept, or those tools are extended in this mission with tests. | Technical | High | Open |
| C-006 | Sibling coordination | #126 edits the same files; whoever lands second rebases and renumbers. If #126 lands first, `Activation._links` uses its `Links` component. | Process | Medium | Open |

### Key Entities

- **ActivationConfig**: the document a client authors; the `PUT` body and the `config` of every answer.
- **Activation**: the stored resource; id, revision, config, derived summary, links.
- **ActivationChange**: the answer of one sub-resource write; the new revision and what changed.
- **SkippedReport**: what a cascade or the effective set skipped, and why.
- **EffectiveArtifactList / EffectiveGraph / EffectiveActionContext**: the effective reads under one activation.
- **Reserved operation**: declared in full, answers `501`, marked `x-reserved`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 13 of 13 activation operations are declared, reserved, and pinned by a passing `501` scenario. — [build] · no-op passable: no
- **SC-002**: 6 of 6 new refusal codes are declared `raised: false` and pinned by a declaration scenario. — [build] · no-op passable: no
- **SC-003**: The JSON Schema file and the contract component agree on 100% of a shared corpus of valid and invalid documents (at least 10 of each). — [build] · no-op passable: no
- **SC-004**: Each of the 5 new or amended lint rules (`x-reserved-shape`, `reserved-operation-declares-501`, `built-operation-declares-no-501`, `no-content-has-no-body`, amended `error-responses-are-problems`) has a planted fixture that fails exactly that rule. — [build] · no-op passable: no
- **SC-005**: Issue #125's four acceptance criteria are each satisfied and cited in the PR. — [build] · no-op passable: no

## Assumptions

- 0.1.0 stays held (no tag), so the release gate has no baseline; the set lands inside `0.1.0-SNAPSHOT` (operator ruling 2026-10-31).
- PR #288 merges before or soon after this work; the contract mirrors the 13-key vocabulary either way.
- #126 has no PR yet; this mission does not use a shared `Links` component unless #126 lands first.
