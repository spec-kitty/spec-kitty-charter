# Mission Specification: Gate-A read surface: API root, offering, generic artifact routes and links

**Mission Branch**: `issue-126-offering-reads`
**Created**: 2026-10-31
**Status**: Draft
**Input**: Orchestrator brief for spec-kitty-charter #126 (re-scoped 2026-10-24), with #175 and
#176 folded in. Research and operator rulings:
`kitty-ops/research/issue-126-offering-reads.md` (sections 6 and 7; rulings of 2026-10-31).

## Intent Summary

- **Primary actor:** a client of the charter API. Core's future generated client is the main one
  (#13, ADR-0021). It discovers the API from one entry point and reads a project's governance
  artifacts by following links, not by hard-coding one route per kind.
- **Trigger:** the client starts against a running charter service and reads the offering, or one
  pack's artifacts of any kind.
- **Desired outcome:** the API answers from a single, kind-generic resource tree (ADR-0019 D2, ADR-0020
  D1):
  - an API root;
  - the offering: the packs in force, in merge order;
  - one route family for artifacts of every served kind, writes included;
  - a `_links` object with `self` on every success answer.

  Refusals and the source-fingerprint header say exactly what the service does (#175, #176).
- **Invariants:**
  - No link points to a reserved or unbuilt operation.
  - Every link `href` carries the `/api/v1` prefix.
  - The removed per-kind routes have no alias.
  - Every verified read's 200 carries the source fingerprint, and nothing else does.
  - The change lands inside the unreleased `0.1.0-SNAPSHOT`, under its `### Breaking` subsection
    (ADR-0022; operator ruling 6a).
- **Source:** the brief was comprehensive (scope, ADRs, acceptance criteria), so it went through brief
  intake. The research squad's six open points were ruled on by the operator on 2026-10-31 and
  relayed by the orchestrator. Decision `DM-01M691NVZG50WH0PWHD5SCX0SN` records the confirmation.

## Bulk-edit target

This mission moves an API surface. The per-kind route families
`/packs/{packId}/<kindPlural>` and `/packs/{packId}/<kindPlural>/{id}`, for the plurals `tactics`,
`directives`, `styleguides`, `paradigms` and `toolguides`, become `/packs/{packId}/artifacts`
(reads, with `?kind=`) and `/packs/{packId}/artifacts/<kind>[/{id}]`, keyed by the singular kind
token. The per-kind operationIds `listPack<Kind>s` and `getPack<Kind>` are replaced by
`listPackArtifacts` and `getPackArtifact`. Each `registerPack<Kind>` and `putPack<Kind>` keeps its
operationId at its new path. The rules for each category of occurrence are in `occurrence_map.yaml`
(drafted at plan). Pack content, storage paths on disk (for example a pack's `tactics/` directory)
and pack-file keys are not renamed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Discover the API from its root (Priority: P1)

A client knows only the service address. It reads `GET /` and finds links to:

- the service information;
- the offering;
- a pack's artifacts, as a URI template;
- the contract document.

It then follows those links without knowing any other path.

**Why this priority**: the root is the entry point ADR-0019 asks core's seam to start from. Without
it, `_links` cannot be followed from a known place.

**Independent Test**: a contract scenario reads `/`, then follows each link it returns and gets a 200.

**Acceptance Scenarios**:

1. **Given** a running service, **When** a client reads `/` with a valid token, **Then** the answer
   is 200 with a `_links` object. The object holds `self`, `info`, `offering`, `artifacts`
   (templated) and `service-desc`, every `href` starts with `/api/v1`, and every link resolves to a
   built operation.
2. **Given** a running service, **When** a client reads `/` without a token, **Then** the answer is
   401, as for every operation.
3. **Given** the root answer, **When** it is checked against the relation table in the
   hypermedia guide, **Then** every relation key it uses is listed there.

---

### User Story 2 - Read a pack's artifacts of any kind through one route family (Priority: P1)

A client lists a pack's artifacts, optionally filtered by kind and paged, then reads one artifact by
kind and id. The answer has the same shape for every kind: a common envelope that names the kind
and id, links to itself, and holds the kind's own representation.

**Why this priority**: this replaces five route families with one (ADR-0019 D2). It is the larger
part of the breaking change.

**Independent Test**: contract scenarios list `/packs/{packId}/artifacts`, with and without `kind`
and across pages, and read `/packs/{packId}/artifacts/{kind}/{artifactId}` for each served kind.

**Acceptance Scenarios**:

1. **Given** a pack with tactics and directives, **When** the client reads
   `/packs/{packId}/artifacts?kind=tactic`, **Then** the answer is 200. It lists only tactics, each
   with a `self` link to its item, and carries paging information and the source fingerprint.
2. **Given** the same pack, **When** the client reads `/packs/{packId}/artifacts` with no `kind`,
   **Then** it lists the artifacts of every served kind, in a stable documented order.
3. **Given** more artifacts than the page size, **When** the client follows `next` until it is
   absent, **Then** it sees every artifact exactly once. The last page has no `next` link.
4. **Given** a served kind and an existing id, **When** the client reads
   `/packs/{packId}/artifacts/{kind}/{artifactId}`, **Then** the answer is 200 with the envelope,
   holding exactly one kind-named member. It carries the fingerprint, and the ETag for a kind that
   can be written.
5. **Given** an id that is not in the pack, **When** the client reads the item, **Then** the answer
   is a 404 with that kind's existing not-found code (for example `tactic_not_found`).
6. **Given** a path kind the service does not serve, **When** the client reads an item under it,
   **Then** the answer is a 404 `kind_not_found` charter refusal.
7. **Given** an unknown `?kind=` value, **When** the client lists, **Then** the answer is a 400
   Problem.
8. **Given** an unknown pack, **When** the client lists or reads, **Then** the answer is a 404
   `pack_not_found`.
9. **Given** a pack whose files cannot be read, **When** the client reads a tactic item, **Then**
   the answer is a 500 `pack_unreadable` without a fingerprint (#176).
10. **Given** any former per-kind read path (for example `/packs/{packId}/tactics`), **When** a
    client reads it, **Then** the answer is a 404 Problem without a refusal code.

---

### User Story 3 - Register and replace artifacts inside the same tree (Priority: P1)

A pack author registers a directive, styleguide, paradigm or toolguide with
`POST /packs/{packId}/artifacts/{kind}`, and replaces one with
`PUT /packs/{packId}/artifacts/{kind}/{artifactId}`. Each kind keeps its own typed request body,
validation, conflict and precondition rules. Only the paths move, and the answer is the same
envelope a read returns.

**Why this priority**: removing the per-kind routes without an alias would orphan the eight write
operations (ADR-0026 D1.2). The operator ruled that they move (ruling 2).

**Independent Test**: the existing registration scenarios run against the new paths and still pass,
with each answer being the envelope with its links.

**Acceptance Scenarios**:

1. **Given** a writable pack, **When** an author POSTs a valid directive registration to
   `/packs/{packId}/artifacts/directive`, **Then** the answer is 201. Its `Location` and `self`
   link are `/api/v1/packs/{packId}/artifacts/directive/{id}`, and a GET of that link returns the
   same envelope.
2. **Given** an existing directive and its ETag, **When** the author PUTs a replacement with a
   matching `If-Match`, **Then** the answer is 200 with the envelope. A stale `If-Match` is a 412,
   as today.
3. **Given** every refusal the per-kind writes answer today (409 `registration_conflict`, 422
   `validation_failed`, 404, 412, 428 where it applies), **When** the same request is sent to the
   new path, **Then** the same refusal and code are answered.
4. **Given** a former per-kind write path (for example `POST /packs/{packId}/directives`), **When**
   it is called, **Then** the answer is a 404 Problem without a refusal code.

---

### User Story 4 - Read the offering (Priority: P2)

A client reads `GET /offering`. It gets the configured packs in force, in merge order, each with its
identity, name, version, level and a link to its artifacts.

**Why this priority**: the offering is ADR-0020 D1's third top-level resource. The smallest Gate-A
set is its pack list (ruling 3). The merged reads (the action context, the graph, mission types)
wait for merge and activation (#10, #11).

**Independent Test**: a contract scenario configures a built-in, an org and a project pack, reads
`/offering` and checks the order, the entries and the links.

**Acceptance Scenarios**:

1. **Given** a built-in, an org and a project pack, **When** the client reads `/offering`, **Then**
   the answer is 200. The packs are listed built-in, then org, then project, and by pack id within
   a level. Each pack has an `artifacts` link, and the answer carries `self` and the source
   fingerprint.
2. **Given** a configured pack whose directory is missing, **When** the client reads `/offering`,
   **Then** the answer is a 500 `pack_unreadable` without a fingerprint.
3. **Given** the offering answer, **When** its links are followed, **Then** each one resolves to a
   built operation. None points to an unbuilt pack, graph, kind or mission-type read.

---

### User Story 5 - Headers and refusals say what the service does (Priority: P2)

A client and the contract checks can rely on the declared headers and examples.

- `Charter-Source-Fingerprint` is required on every verified read's 200 and declared on no other
  answer (#175).
- The 500 refusal examples name only codes the service raises (#176).

**Why this priority**: today the fingerprint is declared on refusals (which never carry it) and
optional on reads (which always carry it), and the 500 examples name codes that are never raised.

**Independent Test**: the lint rules fail planted fixtures for each direction, and pass the real
contract.

**Acceptance Scenarios**:

1. **Given** the contract, **When** it is linted, **Then** every verified GET 200 (`getServiceInfo`,
   `getOffering`, `listPackArtifacts`, `getPackArtifact`) declares the fingerprint required, and no
   other response declares it.
2. **Given** a planted fixture with a fingerprint on a refusal, or a verified read whose 200 lacks
   a required fingerprint, **When** it is linted, **Then** the matching rule fails.
3. **Given** the `OfferingUnreadable` response, **When** its examples are read, **Then** they name
   `pack_unreadable` and `stale_input`, both raised codes.

### Edge Cases

- **An artifact kind the service knows but does not serve** (for example `procedure`) is a 404
  `kind_not_found` on an item path. As a `?kind=` value it is a 400. Only the five kinds with a
  contract representation are served: tactic, directive, styleguide, paradigm and toolguide.
- **Tactics can be read, not written.** No POST or PUT exists for `tactic`, so no `edit` link is
  offered on a tactic.
- **A GET on a literal write path** (for example `GET /packs/{packId}/artifacts/directive`) matches
  no read operation. It answers the framework's Problem without a refusal code, never a 200.
- **An empty pack, or an empty kind** lists an empty page: `hasNextPage` is false and there is no
  `next` link.
- **A page cursor the service did not issue, and a `pageSize` outside 1-200** are a 400 Problem.
- **Inputs that change during a read** are a 500 `stale_input` without a fingerprint, as today, on
  the list, the item and the offering.
- **The relationships write** (`PUT /packs/{packId}/relationships`) stays where it is. Its answer
  gains `_links` like every success answer.
- **The contract document** (`GET /openapi.yaml`) answers YAML, not a JSON schema. It carries no
  `_links` and no fingerprint. A 304 has no body.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | API root | As a client, I want `GET /` to answer the API root with links to `info`, `offering`, `artifacts` (templated) and `service-desc`, plus `self`, so that I can start from one known place (ADR-0019 D2, D4). | High | Open | [build] | no — the path answers 404 today |
| FR-002 | Offering pack list | As a client, I want `GET /offering` to answer the configured packs in force, ordered built-in, org, project and then by pack id, each with id, name, version, level and an `artifacts` link, from the canonical pack model (ADR-0020 D1). | High | Open | [build] | no — the path answers 404 today |
| FR-003 | Generic artifact list | As a client, I want `GET /packs/{packId}/artifacts`, filtered by an optional `kind` and paged with the shared page information and `first`/`next` links, to list the pack's artifacts of the served kinds as summaries that each link to their item (ADR-0019 D2). | High | Open | [build] | no — the path answers 404 today |
| FR-004 | Generic artifact item | As a client, I want `GET /packs/{packId}/artifacts/{kind}/{artifactId}` to answer one artifact in a closed common envelope (kind, id, pack id, `_links`, and exactly one kind-named member holding that kind's representation) (ruling 1). | High | Open | [build] | no — positive control: each served kind is read on the same fixture |
| FR-005 | Writes move into the tree | As a pack author, I want the eight registration and replacement operations at `POST /packs/{packId}/artifacts/{kind}` and `PUT /packs/{packId}/artifacts/{kind}/{artifactId}`, one typed operation per kind, keeping their bodies and refusals and answering the envelope (ruling 2, ADR-0026 D1.2 amended). | High | Open | [build] | no — the old-path scenarios are moved, and a no-op fails them |
| FR-006 | Per-kind routes removed without an alias | As the contract owner, I want every `/packs/{packId}/{tactics,directives,styleguides,paradigms,toolguides}[/{id}]` operation removed, so that each such path answers a 404 Problem without a refusal code. | High | Open | [build] | no — today they answer 200 |
| FR-007 | Kind refusals | As a client, I want an unserved or unknown path kind to be a 404 `kind_not_found` charter refusal (a new raised code), and an unknown `?kind=` to be a 400 Problem (ruling 6b, ADR-0019 D5). | High | Open | [build] | no — positive control: a served kind on the same fixture answers 200 |
| FR-008 | Links component and required `_links` | As a client, I want one `Links` component (open relation keys, closed link object) and a required `_links` with `self` on every JSON success schema, so that every answer can be navigated (ADR-0019 D3). | High | Open | [build] | no — schema conformance fails an answer without `_links` |
| FR-009 | Links point only to built operations | As a client, I want every link to carry `/api/v1` and resolve to a built operation, and no link to name a reserved or unbuilt operation. | High | Open | [build] | no — scenarios follow each link and expect a 200 |
| FR-010 | Closure exemption for `_links` | As the contract owner, I want the closure checker to accept `Links` as its one open map, pinned by a fixture, while every other map is still reported. | High | Open | [build] | no — paired fixture: another open map on the same fixture still fails |
| FR-011 | Relation keys linted | As the contract owner, I want a lint rule that fails a relation key missing from the relation table in the hypermedia guide, with a planted fixture. The table gains `service-desc`, `first` and `next`. | High | Open | [build] | no — the planted fixture fails, and `clean.yaml` passes |
| FR-012 | Fingerprint only on verified reads | As a client, I want `Charter-Source-Fingerprint` declared required on the 200 of `getServiceInfo`, `getOffering`, `listPackArtifacts` and `getPackArtifact`, and declared on no other response (refusals, the shared Problem, writes, the root, the contract document) (#175, ruling 4). | High | Open | [build] | no — today it is optional on reads and declared on refusals |
| FR-013 | Fingerprint lint rules | As the contract owner, I want the always-on headers rule narrowed to the two always-on headers, one rule requiring the fingerprint on verified GET 200s and one forbidding it elsewhere, each with planted fixtures (#175). | High | Open | [build] | no — each planted fixture fails its rule |
| FR-014 | Refusal examples name raised codes | As a client, I want the 500 `OfferingUnreadable` examples to be `pack_unreadable` and `stale_input`, and its description to name only raised codes (#176). | Medium | Open | [build] | no — the examples are absent today |
| FR-015 | `pack_unreadable` pinned on an item read | As the contract owner, I want a scenario that pins `pack_unreadable` on a tactic item read through the generic route (#176). | Medium | Open | [build] | no — no such scenario exists today |
| FR-016 | `service-desc` hand-over | As a client, I want `ServiceInfo` and the root to carry a `service-desc` link to `/api/v1/openapi.yaml`, with the `Link` header kept on the info response only (the #208 hand-over). | Medium | Open | [build] | no — absent today |
| FR-017 | Breaking changelog entry | As core's maintainer, I want the `0.1.0-SNAPSHOT` `### Breaking` subsection to name every removed route and operation, the new required `_links`, the fingerprint and refusal changes, the new code and the seam change for core, with a scenario line for each scenario change (ADR-0022). | High | Open | [build] | no — the release-gate and docs checks read it |
| FR-018 | Records stay true | As a reader, I want the hypermedia guide, the service-contract doc, ADR-0019 (implementation note), ADR-0026 (a D1.2 amendment note on paths only), the scenarios README, the lint README and `VENDORED.md` to match the new surface. | Medium | Open | [build] | no — each named record still describes the per-kind routes today |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Full build green | `./gradlew verifyAll` passes on Java 25 with strict dependency verification: 0 failing tests, 0 lint or closure findings, 0 Checkstyle, javadoc or NullAway errors. | Reliability | High | Open |
| NFR-002 | No new suppressions | 0 new Checkstyle suppressions, 0 new `@SuppressWarnings`, 0 new lint or closure exemptions other than the one pinned `_links` exemption, and 0 new known-failure entries. | Maintainability | High | Open |
| NFR-003 | Bounded complexity | Every method added or changed has cyclomatic complexity ≤ 15. | Maintainability | High | Open |
| NFR-004 | Scenario coverage does not shrink | Every behaviour the removed per-kind scenarios pin today is pinned again on the new routes. The count of `@contract` scenarios after the change is ≥ the count before. | Reliability | High | Open |
| NFR-005 | Bounded pages | No list answer holds more than 200 items, and the default page holds 50. | Performance | Medium | Open |
| NFR-006 | Docs checks green | `check_docs.py all`, markdownlint-cli2 and cspell report 0 new findings in changed files. | Maintainability | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | No new ADR | ADR-0019, ADR-0020 D1 and ADR-0022 settle the direction. The only ADR edits are an amendment note on ADR-0026 D1.2 (paths only) and ADR-0019's implementation note. | Process | High | Open |
| C-002 | Inside `0.1.0-SNAPSHOT` | `info.version` stays `0.1.0-SNAPSHOT`, and all entries go under its `### Breaking` subsection. #125 lands in the same version; whoever lands second rebases (ruling 6a). | Process | High | Open |
| C-003 | Smallest Gate-A offering set | No action-context, graph, mission-type, kinds or `GET /packs/{packId}` operation is added; merge, activation and cascade (#10, #11) are not built here (ruling 3). | Business | High | Open |
| C-004 | No activation operations | Nothing from #125 (activation schemas, reserved 501 operations) is built, and no link names them. | Process | High | Open |
| C-005 | No kind-registry refactor | The per-kind application read and registration services are not folded together (#169). The generic route dispatches to them. | Technical | Medium | Open |
| C-006 | No provisional markings | No `x-provisional` or `x-stability-level` marking is added (#46, ruling 5). | Technical | Medium | Open |
| C-007 | Red first | Contract scenarios and lint or closure fixtures are written and seen failing before the contract or the service changes them to green (ATDD). | Process | High | Open |
| C-008 | Onion boundaries kept | The architecture tests stay green unchanged: the API adapter names no profile or mission domain types and reaches no storage. | Technical | High | Open |
| C-009 | Generator-typable shapes | No response or request relies on a generated `oneOf` for its type. An exactly-one rule is documented in the schema and enforced by the service. | Technical | High | Open |

### Key Entities

- **API root**: the entry resource. It carries only links.
- **Offering**: the packs in force, in merge order. Each entry has the pack's id, name, version, level
  and an `artifacts` link. In this minor the order is declared, not merged; merging comes with #10 and
  #11.
- **Artifact (envelope)**: one artifact of a served kind. It carries its kind token, id, pack id,
  `_links`, and exactly one kind-named member with that kind's representation (tactic, directive,
  styleguide, paradigm, toolguide).
- **Artifact summary / artifact page**: the list form. A summary has kind, id, an optional title and a
  `self` link. A page holds summaries, the shared page information and `self`, `first` and `next`
  links.
- **Links / link**: an open map from a relation key to a closed link object (`href` required;
  `templated`, `method` and `title` optional).
- **Served kind**: an artifact kind with a contract representation. In this minor, the five above.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A client that knows only the service address reaches every built read through links,
  with 0 hard-coded paths besides `/` — [build] · no-op passable: no
- **SC-002**: 0 operations remain on a per-kind path, and 0 artifact reads or writes are lost: all 8
  writes and 2 generic reads answer on the new tree — [build] · no-op passable: no
- **SC-003**: 100% of JSON success answers carry `_links.self`, and 100% of the link hrefs start with
  `/api/v1` and resolve to a built operation — [build] · no-op passable: no
- **SC-004**: The fingerprint is declared on exactly 4 responses (the verified GET 200s), and the
  `OfferingUnreadable` examples name 0 unraised codes — [build] · no-op passable: no
- **SC-005**: The full build and the docs checks pass with 0 new suppressions — [ratchet] · no-op
  passable: yes — paired with SC-002, which a no-op fails

## Assumptions

- Core does not consume the HTTP contract today (its seam is in-process), so the seam change for core
  is stated for its future generated client (#13). No core code changes.
- Within a level, packs are ordered by pack id. ADR-0020's "a later pack wins" holds once merge
  exists. Until then the order is stated, and the description says that merge is not yet applied.
- A list with no `kind` orders summaries by the served-kind order (tactic, directive, styleguide,
  paradigm, toolguide), then by the order the pack's storage lists them.
- The page cursor is opaque. It stays valid across a change of unrelated artifacts, but makes no
  promise of a consistent snapshot across pages. Each page is verified against the fingerprint on its
  own.
- The answer to `PUT /packs/{packId}/relationships` links `self` to the relationships resource with
  `method: PUT`, because no GET exists there. The plan confirms the exact relation against the
  relation table.

## Dependencies and Coordination

- **#125** (the activation set) also edits `openapi.yaml`, the CHANGELOG, the lint rules and the
  scenarios, inside the same `0.1.0-SNAPSHOT`. Whoever lands second rebases.
- **#289 and #288**: storage and schema only; they touch the CHANGELOG (#288). Whoever lands second
  rebases.
- **#209** (the contract document) has landed (PR #267), so the `service-desc` link targets a built
  operation.
