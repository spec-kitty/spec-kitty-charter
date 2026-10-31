# Research: the Gate-A read surface (#126, with #175 and #176)

Pre-specify research for one Spec Kitty mission on branch `issue-126-offering-reads`. A three-lens
read-only squad ran on 2026-10-31: contract and gates, service ports, and the core seam with the ADRs
and sibling issues. Main was at `e2f206f`. This note records the findings and the decisions the
operator rules on before specify.

## 1. Where the contract stands

- `info.version` is `0.1.0-SNAPSHOT`, and no `contract-charter-v*` tag exists. 0.1.0 is built but
  held (`docs/plans/milestones.md:75-78`). The release gate has no baseline (decision NONE).
  - Breaking changes have landed inside the unreleased SNAPSHOT under its `### Breaking`
    subsection. The precedent is #194 (`CHANGELOG.md:18`).
- The contract has 22 operations. `/info` and `/openapi.yaml` are under the Service tag. The rest
  sit on per-kind routes for five kinds:
  - tactics: list and get only;
  - directives, styleguides, paradigms and toolguides: list, `register*` (POST), get and `put*`
    (PUT, `If-Match`/ETag);
  - `putPackRelationship` (PUT `/packs/{packId}/relationships`).
- **The ADRs conflict on writes.**
  - ADR-0019 D2 maps only reads under `/artifacts`.
  - ADR-0026 D1.2 puts registration on `POST /packs/{packId}/{kindPlural}` and
    `PUT /packs/{packId}/{kindPlural}/{id}`, "one family per kind".
  - Removing the per-kind routes with no alias therefore needs a ruling on the 8 write operations
    (decision 2).
- Nothing in `contracts/charter` uses `_links`, `PageInfo`, `x-provisional` or
  `x-stability-level` today.
  - The shared `PageInfo`, `PageCursor` (`pageCursor`) and `PageSize` (`pageSize`, 1-200, default
    50) are vendored but unused.
  - `docs/development/hypermedia-hateoas.md` sets the shape (`:50-66`): a required `_links`
    referencing one `Links`, open keys and a closed link object (`href` required; `templated`,
    `method` and `title` optional), and a single ClosureChecker exemption pinned by a fixture.
  - The relation table (`:97-124`) has no `service-desc`, and no `first`/`next` rows.

## 2. Gates the mission must keep green

- `:contracts:check` runs `bundleContract`, `lintContract` (vacuum, Spectral-format ruleset, core
  functions only; the loaded rule count must match), `lintFixtures` (one planted fixture per rule,
  inline copy of `clean.yaml`), `checkClosedResponseObjects` (ClosureChecker, about 75 pinned
  fixtures in `contracts/src/test/resources/closure/`), `compatibilityFixtures` and
  `checkContractCompatibility` (ReleaseGate: the top CHANGELOG `##` equals `info.version`;
  `### Breaking` on a line bump).
- The contract scenarios run in `:deployable:test` through `CharterContractSuite`. Steps address
  operations by path below `/api/v1`, and some compare exact bodies.
  - `ContractConformance` validates every response against the contract schema, so a required
    `_links` is enforced on every scenario response automatically.
  - It also checks that a 200 carries exactly one fingerprint and no other answer carries one.
- The `resource-property-cited` lint rule needs an `x-source` or `x-derived` citation on every
  property of a titled object, `_links` included.
- ArchUnit: `infra.api` may not name `profiles.domain` or `missions.domain`. A generic controller
  therefore maps through the kernel `CharterArtifact`/`ArtifactKind`, or a kind-keyed mapper in the
  packs slice.

## 3. Service findings

- Five per-kind controllers (`TacticController` … `ToolguideController`, about 980 lines) implement
  the generated `<Kind>sApi` interfaces (`useTags=true`). Item reads of the four writable kinds set
  an ETag from the pack revision (`Revised<T>`).
- `ConsistentRead.read(detail, Supplier<T>)` verifies any supplier against the source fingerprint
  (`pack_unreadable` on the first digest, `stale_input` on a change). It can be reused unchanged
  for the generic artifact reads and for `/offering`.
- `Pack` holds `Map<ArtifactKind, List<CharterArtifact>>`, and `ArtifactKind` has 14 kinds with
  `token()`, `plural()`, `fromToken` and `fromPlural`. Only the five routed kinds have contract
  schemas.
- **No domain port lists packs.** `StorageRootLocations.all()` (in `infra/storage`) lists
  `<config-root>/packs/*`, sorted built-in first, then by name. Org and project are not ordered
  against each other. `/offering` needs a new domain port plus a defined order.
- **No merge, activation or cascade exists** (#10, #11). `MissionStep` has no action field, so no
  action index is modelled.
- `RefusalHandler` maps one `*_not_found` code per kind. A generic route has to settle what an
  unknown or unserved kind answers.
- Blast radius: about 46 files reference the per-kind surface directly.
  - main: 6 files;
  - tests: 10 files;
  - contract: about 30 files, including 13 feature files.
  - Folding the nine per-kind application services is #169 and stays out of scope.

## 4. The core seam

Core does not consume the HTTP contract. Its seam is the in-process `charter.offering.api`
(`/opt/spec-kitty/src/charter/offering/api.py:37-39`). The generated client (#13, ADR-0021) is
future work and "stays 0.x until `_links` lands".

The `### Breaking` seam line for core is therefore forward-looking:

- no core code consumes the removed routes;
- the generated client must be built from this version, type `Links`, start at `GET /` and
  follow relations (the ADR-0019 requests to core).

## 5. Sibling and release coordination

- **#125 (activation minor).** The operator's numbering makes it 0.2.0. No #125 branch or PR is on
  GitHub yet.
  - This mission lands inside `0.1.0-SNAPSHOT` under `### Breaking`, following the #194
    precedent, unless the operator rules otherwise.
  - Whoever lands second rebases and renumbers.
- **#289 and #288** are storage-layer and schema-only, with no HTTP contract change. They do not
  conflict here beyond the CHANGELOG (#288).
- **#169** stays out of scope. This mission adds one generic read path; it does not fold the
  per-kind services.
- **#208 hand-over.** A `service-desc` link to `/api/v1/openapi.yaml` goes on `ServiceInfo` and
  the root, with a relation-table row. #209 has landed (PR #267), so the link points to a built
  operation.

## 6. Decisions for the operator (sent to the orchestrator as one batch)

1. **Artifact response shape.** Recommended: a closed common envelope `Artifact`:
   - `kind`, `id`, `packId` and `_links`;
   - exactly one kind-named member (`tactic`, `directive`, …) that holds today's per-kind schema
     unchanged;
   - the schema documents the exactly-one rule with a `oneOf` of `required` branches, and the
     service enforces it (the `ToolguideRegistration` precedent: the generator ignores `oneOf`).

   The list returns `ArtifactPage`:
   - `items`: an `ArtifactSummary` per artifact, with `kind`, `id`, an optional `title` and
     `_links.self`;
   - `page`: the shared `PageInfo`;
   - `_links`: `self` and `first`, plus `next` when there is a next page.

   Not chosen:
   - a bare `oneOf` of the per-kind schemas, because the generator cannot type it (ADR-0021, ADR-0043);
   - an open `body` map, because ClosureChecker refuses it.
2. **The 8 per-kind writes.** Recommended:
   - keep one typed operation per kind (ADR-0026 D1.2's "one family per kind");
   - move the paths into the generic tree: `POST /packs/{packId}/artifacts/{kind}` and
     `PUT /packs/{packId}/artifacts/{kind}/{id}`, as literal per-kind paths
     (`/artifacts/directive`, …);
   - GET and PUT then share one item URI, and the `edit`/`register` relations point into the
     tree;
   - this needs an amendment note on ADR-0026 D1.2 (paths only), not a new ADR.

   Not chosen: leaving the writes at the old paths. GET on `/packs/{p}/directives` would then
   answer 405 and the resource tree would be split.

   `PUT /packs/{packId}/relationships` stays.
3. **Offering reads in this minor.** Recommended smallest Gate-A set:
   - `GET /` (the root);
   - `GET /offering`: the configured packs in force, ordered by level (built-in, org, project),
     then by pack id. Each entry has `packId`, `name`, `version`, `level` and an `artifacts`
     link. The description says merge is not yet applied (#10, #11).
   - The generic artifact reads.
   - Deferred:
     - the action-context walk (R1);
     - the merged graph (R3);
     - mission types and the action index (R5), which need merge and a modelled action index;
     - `/offering/kinds` (R18).
   - No `GET /packs/{packId}` either. Offering entries link to the pack's `artifacts`, so no link
     points to an unbuilt operation.
4. **#175 fingerprint policy.** Recommended:
   - `Charter-Source-Fingerprint` is declared `required: true` on the 200 of every verified read:
     `getServiceInfo`, `getOffering` and the two artifact reads;
   - it is declared on no other response: refusals, the shared `Problem` (a `VENDORED.md`
     change-list update), writes, `getApiRoot` (it reads no pack) and `getOpenApiDocument`;
   - the lint rule `charter-headers-on-every-response` is narrowed to the two always-on headers;
   - one new rule forbids the fingerprint outside a GET 200, and one requires it on GET 200s,
     except the root and the contract document, which the rule's `given` excludes;
   - each rule gets planted fixtures.
5. **#46.** It does not affect this PR. The mission adds no `x-provisional` or `x-stability-level`
   marking: the 0.x minor rule already allows the break. The reserved activation operations are
   #125's.
6. **Also open.**
   - (a) **Version.** Recommended: land inside `0.1.0-SNAPSHOT` (`### Breaking`). If 0.1.0 is cut
     first, the first breaking minor to reach main takes 0.2.0 and the second renumbers.
   - (b) **Unknown or unserved kind.**
     - The `{kind}` path parameter is an enum of the five served tokens (`tactic`, `directive`,
       `styleguide`, `paradigm`, `toolguide`).
     - Any other kind in the path is a 404 with one new raised code, `kind_not_found` (ADR-0019 D5:
       an unknown kind is a 404 charter refusal).
     - An unknown `?kind=` value is a 400 Problem (the query convention).
     - An unknown artifact keeps its per-kind code (`tactic_not_found`, …), so no raised code is
       retired.
   - (c) **Size.** One PR in about five work packages:
     - the contract (`Links`, root, offering, artifacts, #175, #176);
     - lint and closure;
     - the service read side;
     - the moved writes;
     - the scenarios and docs.

     If decision 2 is "move" and tasks-outline shows the diff is too large to review, the writes'
     move becomes a stacked second PR.

## 7. Operator rulings (2026-10-31, relayed by the orchestrator)

1. Artifact shape: the closed `Artifact` envelope, plus `ArtifactPage` and `ArtifactSummary`, as
   recommended.
2. Writes move into the generic tree, one typed operation per kind:
   `POST /packs/{packId}/artifacts/{kind}` and `PUT /packs/{packId}/artifacts/{kind}/{id}`. ADR-0026
   D1.2 gets an amendment note (paths only). If tasks-outline shows the diff is too large, a stacked
   second PR is allowed; tell the orchestrator.
3. Offering reads: the smallest Gate-A set, as recommended. The deferrals stand, and no link points
   to an unbuilt operation.
4. #175: the fingerprint is required on verified GET 200s only, as recommended, with the lint
   changes and planted fixtures.
5. #46: no effect.
6. Also ruled:
   - (a) Version: #126 and #125 both land inside `0.1.0-SNAPSHOT`, under its `### Breaking`
     subsection. There is no 0.2.0.
   - (b) An unknown path kind is a 404 with `kind_not_found`; a bad `?kind=` is a 400.
   - (c) One PR, of about five work packages.
