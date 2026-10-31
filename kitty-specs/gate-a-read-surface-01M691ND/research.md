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
