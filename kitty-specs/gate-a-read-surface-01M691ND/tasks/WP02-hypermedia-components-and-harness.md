---
work_package_id: WP02
title: Hypermedia components and conformance harness
dependencies:
- WP01
requirement_refs:
- FR-008
- FR-010
- C-009
- NFR-002
- NFR-003
planning_base_branch: issue-126-offering-reads
merge_target_branch: issue-126-offering-reads
branch_strategy: Planning artifacts for this mission were generated on issue-126-offering-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-126-offering-reads unless the human explicitly redirects the landing branch.
subtasks:
- T006
- T007
- T008
- T009
- T010
phase: Phase 1 - Foundation
history:
- at: '2026-10-31T14:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent: claude
agent_profile: java-jenny
authoritative_surface: contracts/charter/schemas/
create_intent:
- contracts/charter/schemas/Link.yaml
- contracts/charter/schemas/Links.yaml
- contracts/src/test/resources/closure/links-map-exempt.yaml
- contracts/src/test/resources/closure/links-map-other-property.yaml
- contracts/src/test/resources/closure/links-map-other-value-title.yaml
- contracts/src/test/resources/closure/links-map-open-link.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/Relation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/Hypermedia.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/HypermediaTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/schemas/Link.yaml
- contracts/charter/schemas/Links.yaml
- contracts/charter/schemas/_index.yaml
- contracts/src/main/java/ai/speckitty/charter/contracts/closure/**
- contracts/src/test/java/ai/speckitty/charter/contracts/closure/**
- contracts/src/test/resources/closure/**
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractConformance.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractConformanceTest.java
- service/deployable/src/test/resources/conformance/**
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/Relation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/Hypermedia.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/HypermediaTest.java
- contracts/charter/CHANGELOG.md
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – Hypermedia components and conformance harness

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and follow
its guidance before you read the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Lay the hypermedia foundation the later WPs build on:

- the shared `Link` and `Links` schemas;
- the closure checker's single structural exemption for `_links`;
- a scenario harness that validates map values and matches responses on path **and** method;
- one service-side link builder.

No operation uses `_links` yet. WP03 is the first.

## Context

- Spec FR-008 and FR-010; research R-01, R-02 (revised), R-14, R-15; squad findings 2, 3, 4 and 7.
- **Coordination with #125** (relayed by the orchestrator on 2026-10-31). #125 adds `Links.yaml`, `Link.yaml`
  and the closure exemption with **this exact shape**. Whoever lands second keeps one copy, so match the
  shape below exactly.
- ADR-0019 D3 and `docs/development/hypermedia-hateoas.md:50-66`: open relation keys, a closed link object,
  and a single exemption described as "a property named `_links` whose values reference the link object".
- **ClosureChecker** (`contracts/src/main/java/ai/speckitty/charter/contracts/closure/ClosureChecker.java`):
  - its javadoc (:11-110) is the authority;
  - it reads the bundle with every `$ref` inlined, and a leftover `$ref` is a violation (:17-23);
  - rule 5 (:56-62): a schema-valued `additionalProperties` is an open map and is reported;
  - fixtures live in `contracts/src/test/resources/closure/`, one `@Test` per fixture in
    `ClosureCheckerTest` (`assertReports(file, pointer, reason)`).
- **ContractConformance** (`service/deployable/src/test/java/.../bdd/ContractConformance.java`):
  - `vetEverySchema` (:87-109) vets **every** file under `schemas/`;
  - it throws on any `additionalProperties` other than `false` (:170-175, :313-316);
  - `response()` (:231-248) takes the first matching path template and then `break`s.

  Adding `Links.yaml` without T008 turns the whole `CharterContractSuite` red, so T006 and T008 land in
  the same commit.

## Subtasks

### T006 — `Link` and `Links` schemas

`contracts/charter/schemas/Link.yaml`:

```yaml
title: Link
description: >-
  One hypermedia link (ADR-0019 D3). href is an absolute path under /api/v1 or an RFC 6570 template
  (templated true). method is GET, POST or PUT and defaults to GET; ADR-0026 D2 rules out DELETE and PATCH.
type: object
additionalProperties: false
required: [href]
properties:
  href:      {type: string, pattern: '^/api/v1(/|$|\{)', description: ..., x-derived: ...}
  templated: {type: boolean, description: ..., x-derived: ...}
  method:    {type: string, pattern: '^(GET|POST|PUT)$', description: ..., x-derived: ...}
  title:     {type: string, description: ..., x-derived: ...}
```

`contracts/charter/schemas/Links.yaml`:

```yaml
title: Links
description: >-
  The links of a resource: an open map from a relation key in the relation table of
  docs/development/hypermedia-hateoas.md to a Link. Clients must tolerate a key they do not know.
type: object
additionalProperties:
  $ref: Link.yaml
```

- **No enum on `method`.** `enum-snake-case` (ruleset :138-145) refuses upper-case values (R-15).
- **Citations.** Every property needs `x-derived` (or `x-source`) and a description, per the
  `resource-property-cited` and `property-described` rules. Use the `x-derived` shape that `x-derived-shape`
  accepts; look at an existing schema for the exact keys.
- Add both files to `schemas/_index.yaml`.
- Neither file is referenced yet, so it is not in the bundle. The lint and closure checks see it only when
  WP03 references it.

### T007 — ClosureChecker: structural `_links` exemption

1. **Red first.** Add the fixtures to `contracts/src/test/resources/closure/`, each with a first-line
   comment, and add one `@Test` per fixture to `ClosureCheckerTest`. Run them and see the passing fixture
   fail (it is reported today).
   - `links-map-exempt.yaml` must pass. A 200 response schema has a property `_links` whose
     `additionalProperties` is the inlined closed object titled `Link`.
   - `links-map-other-property.yaml` must report. The same map under a property named `links`.
   - `links-map-other-value-title.yaml` must report. A `_links` map whose value schema is titled `Href`.
   - `links-map-open-link.yaml` must report. A `_links` map whose `Link` value has no
     `additionalProperties: false`. Report at the inner open object, or at the map, whichever the checker's
     pointer convention makes precise. Document which.
2. **Then** implement the exemption in `ClosureChecker`. A position reached through the property name
   `_links` whose `additionalProperties` is a schema with `title: Link` counts as closed, and only when the
   checker's own closure walk proves that `Link` value closed. Keep the walk recursive into the value
   schema.
3. Update the javadoc's rule list with the exemption and its reason (ADR-0019 D3).

### T008 — ContractConformance validates map values

1. **Red first.** In `ContractConformanceTest`, using resources under
   `service/deployable/src/test/resources/conformance/` or inline JSON as the existing tests do:
   - a body whose `_links.self` lacks `href` must fail;
   - a body whose `_links.self` has an extra member `rel` must fail;
   - a body with valid links must pass.

   Also check that the constructor no longer throws for `Links.yaml`.
2. **Then** change the vet and the validation:
   - `additionalProperties` may be `false`, or a schema;
   - for a schema, every property not in `properties` is validated against that schema.

   Keep `true` and absent refused: the contract stays closed everywhere else.

### T009 — Path and method matching

1. **Red first.** Add a test fixture contract (or a test-only document) with:
   - `/packs/{packId}/artifacts/{kind}/{artifactId}` (`get`);
   - `/packs/{packId}/artifacts/directive/{artifactId}` (`put`).

   Assert that `PUT /packs/qa/artifacts/directive/x` is checked against the `put` response and
   `GET /packs/qa/artifacts/directive/x` against the `get` one.
2. **Then** `response()` collects every template that matches the path. Among those that define the
   request's method, it prefers the concrete one (fewest template segments), following OpenAPI's rule.
   When no matching template defines the method, keep today's behaviour: the request is checked against
   the shared Problem, or excluded on a 405, as at :41-44.

### T010 — `Relation` constants and `Hypermedia` builder

1. `Relation` is a package-private enum in `packs.infra.api`. It holds the relation keys this mission
   emits, each with its wire key:

   | Constant | Wire key |
   |---|---|
   | `SELF` | `self` |
   | `COLLECTION` | `collection` |
   | `INFO` | `info` |
   | `OFFERING` | `offering` |
   | `ARTIFACTS` | `artifacts` |
   | `SERVICE_DESC` | `service-desc` |
   | `FIRST` | `first` |
   | `NEXT` | `next` |

   WP06 tests this set against the guide's table.
2. `Hypermedia` builds generated `Link` DTOs and a `Map<String, Link>`. Note that the generator types
   `Links` as a `Map<String, Link>`; check the generated model once WP03 references it.
   - Hrefs are the configured base path (`charter.api.base-path`, default `/api/v1`) plus a path,
     URL-encoding each path segment.
   - It also offers a templated link (`templated: true`) and a `method`.
3. Unit tests in `HypermediaTest`:
   - the base path is applied;
   - segments are encoded (an id with a space or a `/`);
   - a templated link carries `templated: true`;
   - `method` is omitted when it is GET.

   The generated `Link` DTO exists only once a schema references `Link` (WP03). Until then, build against
   a small package-private record and map it to the DTO in WP03, or reference `Links` from a schema that is
   already generated. Prefer the record: no contract change in this WP.

## Definition of Done

- [ ] `./gradlew :contracts:test :contracts:check :deployable:test :packs:test` is green.
- [ ] The four closure fixtures behave as stated. The exempt fixture failed before the change; record this
  in the activity log.
- [ ] `ContractConformanceTest` pins the map-value checks and the method-aware match. Both were red first.
- [ ] `Link`/`Links` match the shape above exactly (#125 coordination).
- [ ] No new suppressions. Complexity ≤ 15 in every changed method.

## Risks and notes

- `ClosureChecker` complexity: put the exemption in its own small method.
- Do not loosen ContractConformance for any map other than a schema-valued `additionalProperties`.

## Reviewer guidance

- Try the lookalikes yourself: rename `_links` in the exempt fixture and confirm that it reports.
- Confirm that `Link.yaml` and `Links.yaml` are byte-for-byte in the agreed shape, so the #125 rebase stays
  trivial.

## Branch strategy

The planning base and the merge target are both `issue-126-offering-reads`. The lane comes from
`lanes.json`. Implement with:
`spec-kitty agent action implement WP02 --agent claude --mission gate-a-read-surface-01M691ND`.
