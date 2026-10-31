# Research: Gate-A read surface

Discovery research, the squad findings and the operator rulings are in
`kitty-ops/research/issue-126-offering-reads.md`. This file records the design decisions taken at
plan time. Each gives the decision, the rationale and the alternatives. No decision here reopens an
operator ruling.

## R-01 One `Links` schema, `self` enforced on answers, not in the schema

- **Decision.** `Links` is an open map from a relation key to a closed `Link`
  (`additionalProperties: {$ref: Link}`). It requires no key. Every JSON success schema requires
  `_links: {$ref: Links}`. That every success answer holds `self` is enforced in two places:
  - a lint rule over the bundle: every 2xx JSON schema requires `_links`;
  - the scenario harness: every 2xx JSON body holds `_links.self` with an `href` under `/api/v1`.
- **Rationale.** ADR-0019 D3 and the hypermedia guide ask for one `Links` component with open
  keys. Some embedded entries are not resources of their own and have no `self`:
  - an offering pack entry, since `GET /packs/{packId}` is deferred (ruling 3);
  - an artifact summary has its own item, so it does carry `self`.

  A `required: [self]` on `Links` would force a fake `self` on those entries, or a second link
  schema.
- **Alternatives.**
  - `Links` requiring `self`, plus a second `EmbeddedLinks`. Two components go against D3.
  - `allOf` plus a sibling `required` on each `_links` property. This adds composition the closure
    checker and the generator handle poorly.

## R-02 The closure exemption is by component identity

- **Decision.** The ClosureChecker treats a schema titled `Links` whose `additionalProperties` is
  exactly `{$ref: Link}` as closed. Any other map stays open and is reported. Two fixtures pin this:
  - `links-map-exempt.yaml` passes;
  - `links-lookalike-map.yaml`, an open map with another title or another value schema, is reported.
- **Rationale.** It is the one exemption the hypermedia guide names, and it cannot be widened by
  accident.

## R-03 The relation-key lint reads the guide's table through a generated list

- **Decision.** vacuum has core functions only. The relation table's keys are written into the
  ruleset as an `enumeration` the rule checks every key of a `_links` example against.
  - Examples are the only place keys appear in an OpenAPI document, since `Links` is an open map.
  - The scenario harness also checks every key of every answer against the table.
  - A `contracts` unit test parses the table in `docs/development/hypermedia-hateoas.md` and fails
    when it and the ruleset's list differ. The guide stays the source of truth.
- **Rationale.** There is no custom-function runtime in the lint setup, and keys live in answers.
  Checking both the examples (lint) and the answers (scenarios) covers what can be seen statically
  and what is seen at run time.
- **Alternatives.** A custom vacuum function. That needs a JS runtime the build does not verify
  (supply chain), so it was rejected.

## R-04 Artifact envelope and summary shapes

- **Decision.**
  - `Artifact`: `kind` (`ArtifactKindToken`), `id`, `packId`, `_links`, and the five optional
    members `tactic`, `directive`, `styleguide`, `paradigm` and `toolguide`, each the existing
    per-kind schema unchanged. `additionalProperties: false`, and a `oneOf` of five
    `required: [<member>]` branches documents the exactly-one rule. The service always fills
    exactly the member that `kind` names.
  - `ArtifactSummary`: `kind`, `id`, `title` (a tactic's or paradigm's `name`; the `title` of the
    other three) and `_links.self`.
  - `ArtifactPage`: `items`, `page` (the shared `PageInfo`) and `_links` (`self`, `first`, `next`
    when there is a next page).
- **Rationale.** This is ruling 1. The generator ignores `oneOf` and types the members as optional
  fields; the `ToolguideRegistration` precedent is in CHANGELOG `0.1.0-SNAPSHOT` `### Breaking`.
- **Item links.**
  - `self`;
  - `collection`: `/api/v1/packs/{packId}/artifacts?kind=<kind>`;
  - `edit`: method `PUT` on the same href, for the four writable kinds only.

## R-05 Kind path parameter, kind filter and refusals

- **Decision.**
  - The `{kind}` path parameter is a slug string. The service answers `kind_not_found` (404, a new
    raised code) for any token outside the served five.
  - The `kind` query parameter is the `ArtifactKindToken` enum. An unknown value fails conversion,
    and the existing handler answers it as a 400 Problem.
  - An unknown id answers its kind's existing code.
  - An unknown pack answers `pack_not_found`. The pack is checked before the kind, so an unknown
    pack with an unknown kind is `pack_not_found`.
- **Rationale.** This is ruling 6b, with ADR-0019 D5 for the path and the query convention in
  `rest-resource-conventions.md` for the filter.

## R-06 Paging and cursor

- **Decision.**
  - Order: by served-kind order (tactic, directive, styleguide, paradigm, toolguide), then by id in
    code-point order.
  - The cursor is opaque: base64url of `<kind>/<id>` of the last item served. The next page holds
    the items strictly after that position.
  - A cursor that does not decode to a served kind and an id, or that names a kind other than the
    `kind` filter, is a 400 Problem.
  - `pageSize` is 1-200 with default 50, from the shared parameters.
  - Each page is one verified read and carries its own fingerprint.
- **Rationale.** A position cursor survives the addition or removal of other artifacts, and a
  deleted anchor still pages correctly. Storage order is not stable enough for a cursor, so the spec
  assumption is made explicit as code-point id order.
- **Alternatives.** An offset cursor. It skips or repeats items when an earlier artifact is added
  or removed, so it was rejected.

## R-07 Offering order and source

- **Decision.**
  - A new domain port `PackCatalog.configured()` returns the configured pack identifiers.
    `DomainRepositoryService` implements it over `StorageRootLocations.all()`.
  - `OfferingReadService` loads each pack's descriptor inside one `ConsistentRead` and orders by
    `PackLevel` ordinal (built-in, org, project), then by pack id.
  - The level is the declared `pack_level`, default `org`, which is the storage default.
  - A configured pack that cannot be read refuses the whole read with `pack_unreadable`.
- **Rationale.** Ruling 3, and ADR-0020 D1/D4 ("a later pack wins"). There is no port for listing
  packs today, so one is needed.

## R-08 Fingerprint policy and harness

- **Decision.**
  - The fingerprint is declared `required: true` on the 200 of `getServiceInfo`, `getOffering`,
    `listPackArtifacts` and `getPackArtifact`, and nowhere else.
  - The lint rule `charter-headers-on-every-response` is narrowed to `Charter-Contract-Version` and
    `Charter-Worktree`.
  - New rule `fingerprint-on-verified-reads`: a GET 200 of a path other than `/` and
    `/openapi.yaml` declares the fingerprint `required: true`.
  - New rule `fingerprint-only-on-verified-reads`: any other response, and every shared component
    response, declares no fingerprint.
  - The harness's `@After` hook takes "this GET 200 must carry the fingerprint" from the contract,
    not from "every GET 200". The root's 200 must carry none.
- **Rationale.** Ruling 4 (#175). The hook already exempts writes, and it now needs to exempt the
  root, which reads no pack.

## R-09 `service-desc`

- **Decision.**
  - `ServiceInfo._links` and the root's `_links` carry `service-desc` with href
    `/api/v1/openapi.yaml`.
  - The info 200 declares and sends `Link: </api/v1/openapi.yaml>; rel="service-desc"`, a new header
    file. No other response sends it.
  - The relation table gains `service-desc` (RFC 8631), `first` and `next` (IANA).
- **Rationale.** The #208 hand-over in the #126 comment. #209 has landed.

## R-10 Service structure: dispatch, not a registry refactor

- **Decision.**
  - The generic reads live in `ArtifactController` (generated `ArtifactsApi`, tag `Artifacts`).
  - The list goes through a new `ArtifactCatalogReadService`, one verified read of the pack's
    served kinds that projects `(kind, id, title)`.
  - The item goes through a package-private `ArtifactKinds` dispatch from the kind token to the
    existing per-kind read service and DTO mapper.
  - The writes stay in the per-kind controllers (generated `<Kind>sApi`, now holding only
    `register*`/`put*`) and answer the envelope.
  - `RelationshipController` answers `RelationshipRegistration` with `_links`.
- **Rationale.**
  - C-005 keeps #169's registry refactor out of scope.
  - ArchUnit forbids `infra.api` from naming profile or mission domain types; the five served kinds
    are all in `packs.domain`.
  - One verified read for the list keeps a page consistent with one fingerprint.
- **Alternatives.** One generic controller for the writes too. Its typed bodies would need
  `oneOf`, which the generator cannot type. Rejected (C-009).

## R-11 Root and offering placement

- **Decision.**
  - `getApiRoot` is tagged `Service` and served by `InfoController`.
  - `getOffering` is tagged `Offering` and served by a new `OfferingController`.
- **Rationale.** The root is about the service itself, like `/info`. The offering is the second
  top-level resource of ADR-0020 D1.

## R-12 Work-package slicing and lanes

- **Decision.** One lane of five sequential work packages. Each WP leaves `verifyAll` green:
  - WP01: headers, refusals, links infrastructure;
  - WP02: root and offering;
  - WP03: generic reads;
  - WP04: writes moved and every answer linked;
  - WP05: records.
- **Rationale.** Every WP edits `openapi.yaml`, the CHANGELOG and the scenarios, so parallel lanes
  would only conflict. Ruling 6c is one PR.

## Supply chain

No dependency is added, upgraded or removed. `DIRECTIVE_051` has no control to apply, and
`gradle/verification-metadata.xml` is unchanged.

## Plan-point adversarial squad (2026-10-31)

Three profile-loaded delegates reviewed the plan: architect-alphonso (contract/API), qa-test-designer-tessa
(test integrity) and reviewer-renata (correctness and scope). All three returned **ship-with-fixes**. Every
finding is dispositioned below. "Accepted" means the planning artifacts changed. Decisions R-13 to R-24
below supersede the earlier R-items where they say so.

| # | Lens | Sev | Finding | Disposition | Where |
|---|---|---|---|---|---|
| 1 | alphonso | BLOCKER | `RelationshipRegistration` is also the request body and a nested item, so a required `_links` breaks them; `self` with `PUT` has no GET | accepted | R-13 |
| 2 | alphonso, tessa | BLOCKER | `ContractConformance` accepts only `additionalProperties: false` and vets every schema file, so `Links` turns the suite red | accepted | R-14, WP01 |
| 3 | tessa | MAJOR | Conformance matches the first path template and then `break`s, so a literal PUT path collides with the templated GET | accepted | R-14, WP01 |
| 4 | alphonso | MAJOR | `Link.method` as an upper-case enum fails `enum-snake-case` | accepted | R-15 |
| 5 | alphonso | MAJOR | `PageInfo` enters the bundle without an `x-source`/`x-derived` citation; spike the generator's typing of `nextPageCursor` | accepted | R-16, WP02 |
| 6 | alphonso | MAJOR | A generated enum for `?kind=` breaks Spring's `Enum.valueOf` conversion of `tactic` | accepted | R-17, WP02 |
| 7 | alphonso, renata | MAJOR | The R-02 exemption keys on a `$ref`, but the checker reads the inlined bundle | accepted | R-02 revised |
| 8 | renata, tessa | MAJOR | The offering level comes from the declared `pack_level`, but storage uses the configured location level; a pack without `pack.yaml` would 500 the offering | accepted | R-18 |
| 9 | renata | MAJOR | The new list path must rewrite `PACK_UNREADABLE` of an unconfigured pack to `PACK_NOT_FOUND`, as the per-kind reads do | accepted | R-19 |
| 10 | renata, tessa | MAJOR | WP order: the WP02 links target WP03 operations; WP03 removes GETs while the writes and journeys still use them | accepted | R-12 revised |
| 11 | tessa | MAJOR | The hook checks only that `self` sits under `/api/v1`; links are not resolved; `Location` is unpinned | accepted | R-20 |
| 12 | tessa | MAJOR | Operations no scenario exercises escape the `self` check | accepted | R-20 (operation coverage) |
| 13 | tessa, alphonso | MAJOR/MINOR | The relation-key lint is examples-only and `enumeration` cannot check keys | accepted | R-03 revised |
| 14 | tessa | MAJOR | A fingerprint hook that reads the contract is circular | accepted | R-08 revised |
| 15 | tessa | MAJOR | Paging edge cases are unnamed, and no steps exist for JSON-pointer fields or for following `next` | accepted | R-21, WP02 |
| 16 | tessa | MAJOR | FR-014's no-op mark is dishonest: nothing reads the refusal examples | accepted | R-22, WP01; spec FR-014 |
| 17 | tessa | MINOR | FR-017 and FR-018 are no-op passable | accepted | spec marks set to `yes — review-checked` |
| 18 | tessa | MINOR | NFR-004 is not measurable | accepted | R-23 |
| 19 | tessa | MINOR | The removed-route check covers one alias; GET on a literal write path is a 405 | accepted | R-21, WP03 |
| 20 | tessa, renata | MINOR | The narrowed headers rule orphans the `.missing-fingerprint` fixture; `_links` changes ripple into every fixture | accepted | WP01, WP05 |
| 21 | alphonso | MINOR | The pack-before-kind check belongs in an application service, not the adapter dispatch | accepted | R-19 |
| 22 | alphonso | MINOR | Literal write paths are sound; pin the 405s, the param name and registration not being discoverable | accepted | R-21; PUT param renamed `{artifactId}`; CHANGELOG |
| 23 | alphonso, renata | MINOR | The hypermedia table's "On" column is stale for `collection`, `edit` and `artifacts` | accepted | WP05 |
| 24 | alphonso | MINOR | `PackCatalog` escapes the ArchUnit port-suffix rules | accepted | R-18 (`PackCatalogRepository`) |
| 25 | renata | MINOR | An `edit` link on a read-only linked pack contradicts D3 ("only when available") | changed: no `edit` link in this minor | R-24 |
| 26 | renata | MINOR | `KIND_NOT_FOUND` must also be in `RefusalCode.notFound()` | accepted | WP01 |
| 27 | renata | MINOR | The occurrence map's `infra/storage/**` do-not-change contradicts the change to `DomainRepositoryService` | accepted | `occurrence_map.yaml` |
| 28 | renata | MINOR | Stale records: architectural-patterns-applied.md:102, packs-resource-model.md:31, roadmap.md:60; the #126 issue body | accepted | WP05; closeout task |
| 29 | renata | NIT | PUT answers 201 when it creates | accepted | spec US3 |
| 30 | renata, tessa | NIT | `Location` is absolute and `self` is a path | accepted | spec US3: "same path" |
| 31 | renata | NIT | Operation counts are off | accepted | plan Scale/Scope: 21 today, 15 after |
| 32 | alphonso, tessa, renata | NIT | The root is `/api/v1/`; `/api/v1` is unpinned | accepted | R-21 |
| 33 | alphonso | NIT | `TacticNotFound.yaml` is orphaned; the `OfferingPack.level` should `$ref` `PackLevel`; the advice is "re-targeted" for nothing | accepted | WP03; data model; plan |
| 34 | renata | MINOR | The #176 item scenario must use the missing-directory state, and the 400s must be raised from the adapter | accepted | WP03 (#176); R-17 |

### R-02 (revised) Closure exemption, structural

The checker exempts a schema position reached as the property `_links` whose `additionalProperties` is a
schema titled `Link` that the checker itself proves closed (ADR-0019 D3: "a property named `_links` whose
values reference the link object"). Every other map is still reported. The fixtures are:

- one exemption that passes;
- lookalikes that are reported: the same map under another property name, a value schema with another
  title, and a `Link` that is not closed.

### R-03 (revised) Relation keys

- **One source.** The guide's relation table in `docs/development/hypermedia-hateoas.md` is the only source.
- **Lint rule `link-relations-are-known`.** It uses `function: schema` with
  `propertyNames: {enum: [...]}`, and its `given` is scoped to example values (`$..examples..value.._links`
  and `$..example.._links`).
- **Required examples.** A companion rule, `success-responses-have-link-examples`, requires every 2xx JSON
  response to carry an example with `_links`, so the key check is never vacuous.
- **Parity tests.** A `contracts` unit test parses the guide's table and fails when it differs from the
  ruleset's list. The service builds every relation from one `Relation` constant set, and a deployable test
  checks that set against the same table, which is copied into the test resources by `processTestResources`.
- **Run time.** The scenario hook checks every key of every 2xx answer against the copied table.

### R-08 (revised) Fingerprint: two independent checks

- **The hook keeps its own rule.**
  - A GET 200 with a JSON body, on any path except `/`, carries exactly one fingerprint, equal to the one
    `/info` gave.
  - Every other answer carries none, except that writes stay exempt (ADR-0026).
  - `/openapi.yaml` answers YAML, so it falls outside the JSON-body condition.
- **The lint rules separately check the contract.** The two are not derived from each other.

### R-12 (revised) Work-package order

One lane. Each WP leaves `verifyAll` green.

| WP | Delivers |
|---|---|
| WP01 | #175 headers and lint; #176 examples plus the raised-code check; `kind_not_found`; `Links`/`Link`; the closure exemption; `ContractConformance` maps and method-aware path matching |
| WP02 | The generic reads, **added** beside the per-kind routes: the list (paging, filter, cursor), the item envelope, `kind_not_found`, the kind converter, `PageInfo` citation |
| WP03 | The writes moved into the tree; **every** per-kind route removed (reads and writes); the kind features and journeys migrated with a removed-to-new scenario map; the removed-route outline; the #176 item scenario |
| WP04 | `GET /` and `GET /offering`; `ServiceInfo._links` and the `service-desc` link and header; the offering port. Every link target now exists. |
| WP05 | Every answer linked and checked: `success-schemas-carry-links`, `link-relations-are-known` and the example rule with fixtures; the harness `self` and link-resolution hooks; the operation-coverage check; the relationships answer; the guide table; the CHANGELOG `### Breaking`; ADR-0019 and ADR-0026 notes; docs and records |

### R-13 Relationships answer

- `PUT /packs/{packId}/relationships` answers a new response-only schema, `RelationshipRegistered`:
  - `relationship` holds the registered `RelationshipRegistration`, unchanged;
  - `_links` holds `artifacts`, the pack's artifact list.
- There is no `self`: the URI has no GET. `RelationshipRegistration` itself, the request body and the
  nested item, gets no `_links`.
- The harness's `self` rule is: a 2xx JSON answer carries `self` when its request URI has a GET operation
  in the contract.

### R-14 Conformance harness

`ContractConformance` changes in two ways:

- It validates a schema-valued `additionalProperties` by checking each value against that schema. Red
  fixtures: a link without `href`, and a link with an extra member.
- It matches a response on path template **and** method.

`ContractConformanceTest` pins both.

### R-15 `Link.method`

`method` is `{type: string, pattern: '^(GET|POST|PUT)$'}`, with the methods named in its description. No
enum, so `enum-snake-case` does not apply.

### R-16 `PageInfo`

- It gets an `x-derived` citation, and a line in the `VENDORED.md` change list.
- WP02 spikes the generator's typing of `nextPageCursor` (`oneOf [PageCursor, null]`). If the generator
  wraps it, WP02 narrows the vendored copy to a nullable string, recorded in `VENDORED.md`. C-009 forbids
  relying on a generated `oneOf`.

### R-17 `?kind=` conversion and the 400s

- An `infra.api` converter maps the query value through the generated enum's `fromValue`. A positive
  scenario (`?kind=tactic`) comes before the 400 case.
- The adapter answers the 400s directly, as a plain Problem. They cover an unknown filter, a malformed
  cursor, a cursor for another kind, and an out-of-range `pageSize`, which bean validation already
  handles.
- The cursor is decoded in the adapter, so `application` sees only a typed position.

### R-18 Offering source

- **The port.** `PackCatalogRepository.configured()` (in `packs.domain`, implemented in `infra.storage`)
  returns `ConfiguredPack(PackIdentifier id, PackLevel level)`. The level is the configured location
  level, the one storage already serves by.
- **The fields.**
  - `packId` is the configured identifier, which is also the sort key.
  - `name` and `version` come from `pack.yaml` and are **optional**: absent when the pack has no
    `pack.yaml`.
  - A `pack.yaml` that is present but unreadable or invalid is a 500 `pack_unreadable`.
- **The order.** By level ordinal, then by `packId` in code-point order.
- **Scenarios pin:**
  - a pack without `pack_level`;
  - declared and configured levels that differ: the configured level wins;
  - two packs per level, configured out of order;
  - `Zeta` before `alpha`;
  - a pack without `pack.yaml`.

### R-19 Generic reads in the application layer

- `ArtifactReadService` (in `application`) holds the generic reads:
  - `list(pack, kindFilter, position, size)`: one verified read over `PackRepository.load`;
  - `item(pack, kindToken, id)`.
- The order inside a read is: resolve the pack (rewriting `PACK_UNREADABLE` to `PACK_NOT_FOUND` as the
  per-kind services do); resolve the kind (`KIND_NOT_FOUND`); delegate the item to the existing per-kind
  read service.
- The adapter maps the typed result to the envelope. Unit tests cover the rewrite, and a list scenario
  covers an unknown pack.

### R-20 Hooks that cannot be faked

The `@After` hook asserts, for every 2xx JSON answer:

- `_links.self.href` equals the base path plus the request path, and for a list page the query as well,
  whenever the URI has a GET;
- every link's `href`, with its template expanded or stripped, matches a contract path template whose
  method (`method`, default `GET`) exists. This is a static resolution: no extra requests.
- on a 201, the path of `Location` equals `_links.self.href`.

An `@AfterAll` coverage check fails when a contract (operation, 2xx status) pair is exercised by no
scenario.

### R-21 Paging and route edge cases to pin

**Paging** (WP02):

- exactly `pageSize` items: no trailing empty page, and `hasNextPage` false;
- `pageSize=1` across the tactic-to-directive boundary, with no filter;
- `next` keeps `kind` and `pageSize`;
- a deleted anchor between pages;
- bad cursors: one for another kind, one that is not base64, `procedure/x`, and one with no slash;
- `pageSize` of 0, 201 and `abc`;
- an empty pack and an empty kind.

New steps go into the scenarios README:

- a JSON-pointer field step;
- "following `next`, the `items[].id` are …";
- "the answer has no link X";
- "the problem has no code".

**Routes** (WP03):

- an outline over all 10 removed read paths and the 8 removed write method-and-path pairs: 404, no code;
- GET on a literal write path: 405;
- PUT on `/artifacts/tactic/x`: 405, documented.

**Root** (WP04): `/api/v1/` answers the root, and `/api/v1` without the slash is pinned as it answers today.

### R-22 Refusal examples name raised codes

A `contracts` unit test fails when a refusal example's `code` is not `raised: true` in
`CharterRefusalCode`'s `x-refusal` map. It has a planted fixture. This makes FR-014 non-vacuous.

### R-23 Measuring NFR-004

- **Baseline.** 111 scenario headers, 6 of them outlines. The measure is the JUnit test count of
  `CharterContractSuite` on `main` at `e2f206f`, recorded in WP03.
- **WP03** adds a mapping table in `tasks/WP03` from each removed scenario to its replacement.
- **ArtifactControllerTest** replaces the per-kind read controller tests.

### R-24 No `edit` link in this minor

An artifact item carries `self` and `collection` only. ADR-0019 D3 allows a link only when its action is
available to this caller in this state. A pack linked from outside the config root is read-only, and
gating `edit` on that state would widen the read model. The CHANGELOG says registration is not yet
advertised by a link. R-04's `edit` is withdrawn.
