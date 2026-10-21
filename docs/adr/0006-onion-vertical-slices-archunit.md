---
title: "ADR-0006: Onion layering cut by vertical concern slices, enforced by ArchUnit"
description: The service is onion-layered and cut into vertical concern slices, one Gradle module per slice with layers as packages; ArchUnit enforces inward dependencies and a pure domain.
doc_status: draft
updated: 2026-10-21T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0001-java-service-with-openapi-contract
  - adr/0005-service-registers-and-distributes-packs
  - adr/0007-rest-hateoas-contract-of-record
  - architecture/hexagonal-service
  - architecture/resolution-model
  - architecture/architectural-patterns-applied
  - development/how-to-ddd
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the module layout, where each layer lives, and the ArchUnit rules your code must pass.
  - profile: core-maintainer
    guidance_text: Read this to see how the service's hexagonal shape is organized and gated.
  - profile: evaluator
    guidance_text: Read this for the layering invariants and how they are enforced as a build gate.
---

# ADR-0006: Onion layering cut by vertical concern slices, enforced by ArchUnit

## Status

**Accepted** — 2026-10-21 (proposed 2026-10-18), realized by the pack repositories and validation
mission. Decider: Stijn Dejongh (operator). This record fixes the internal structure of
the charter offering service. It realizes the operator architecture brief and
[ADR-0001](/adr/0001-java-service-with-openapi-contract/)'s hexagonal mandate, and is the home that houses
the pack registration and distribution model of [ADR-0005](/adr/0005-service-registers-and-distributes-packs/).
It resolves three design-mission Decision Moments (module layout, domain-service implementation placement,
Gradle granularity).

### Amendment 2026-10-21 — realized

The pack repositories and validation mission (`pack-repositories-validation-01M5EET9`) built this
decision. The realized module set is:

- **`kernel`** — `kernel.domain` (the shared Pack and DRG vocabulary) and `kernel.validation` (a
  pure guard library, `java` and `jspecify` only);
- **`packs`** — the first slice, with the layer packages `domain`, `application`, `infra.storage`
  (the outbound adapter) and `infra.api` (the inbound adapter);
- **`bootstrap`** — the composition root;
- `charter-api` and `charter-reports`, unchanged.

The prior flat, role-named modules (`charter-domain`, `charter-read-app`, `adapter-in-rest`,
`adapter-out-yaml`, `charter-service`) are superseded by this layout. The layering is now a
blocking ArchUnit gate: `HexagonalArchitectureTest` in `bootstrap` enforces the onion direction,
domain and kernel purity, port placement, `STORAGE_RETURNS_DOMAIN`, slice isolation, transport
confinement and a pure validation library, each with a known-bad control. The rule names in the
table below are the original design names; the realized rules keep their intent. See
[Architectural patterns applied](/architecture/architectural-patterns-applied/) and
[How to DDD here](/development/how-to-ddd/).

### Revision 2026-10-21 — the application layer is an outer edge ring

The application-edge read path mission (`application-edge-read-path-01M5FQXC`) reclassifies the
`application` layer. It is an **outer edge adapter, peer to persistence**, not a pure inner ring.
Only `domain` and `kernel` are pure. `application`, `infra.storage` and `infra.api` are peer edges
around that pure core. Application use cases are Spring `@Service` beans that orchestrate domain
ports; they may use the framework. Status stays Accepted.

- **New rule `APPLICATION_IS_EDGE`** replaces `APPLICATION_IS_PLAIN_JAVA`. `..application..` may
  depend on `..domain..`, `..kernel..` and the framework. It may not depend on another slice's
  `infra`, on another slice's `domain`, or on the `deployable` composition root.
- **Unchanged:** `DOMAIN_IS_PURE`, `NO_INFRA_ANNOTATIONS_IN_DOMAIN`, the kernel purity rules and the
  pure validation library rule. Domain and kernel stay framework-free: the `kernel` is backed by the
  build-time dependency rejection (the `charter.domain-conventions` plugin) as well as ArchUnit,
  while each slice's `domain` (for example `packs.domain`) is held pure by ArchUnit.
- **Composition root renamed.** The `bootstrap` module becomes `deployable`. Each edge layer owns a
  `@Configuration` in a `config` sub-package, and an aggregate `DeployableConfiguration`
  component-scans their `config` sub-packages.
- The decision list item 2 (`entry-point infra → application → domain`) and item 3 (the
  application ring as a place for plain orchestration) are read with this revision: the direction
  stays inward, but the application ring is an edge, not part of the pure core.

## Context

The service is a hexagonal, plain-Java domain behind a generated OpenAPI contract (ADR-0001). The existing
hexagonal docs (`architecture/hexagonal-service`, `overview`) organized modules by **use-case side** —
`charter-read-app`, `charter-write-app`, `adapter-in-rest`, `adapter-in-write` — a horizontal read/write
(CQRS) split at the top level.

The architecture brief sets two orthogonal axes instead: onion layers (dependency direction, inward only)
and a partition by **vertical concern slice** (subdomain). It also fixes two hard rules — no infra annotations
in any domain package, and a storage layer that returns plain domain models — and names ArchUnit as the
enforcement. Three choices were genuinely open: which axis is the top-level module boundary, where
domain-service *implementations* live (pure domain vs application ring), and the Gradle module granularity.

## Decision

1. **Vertical concern slices are the top-level partition; read/write is a sub-split inside each slice's
   application ring** (DM `01M5759JC2JMQT0HDE9A4HRKNG`). This supersedes the horizontal read/write top-level
   shape in `hexagonal-service`/`overview`. The slices are **pack registration & validation**, **mission
   types**, **profiles**, and **other charter artifacts**, over a cross-cutting **shared kernel** slice that
   carries the pack-and-DRG primitives every slice reads. Each slice cuts through every onion layer. The
   read/write distinction survives as a sub-split *within* each slice's application ring, not as a top-level
   module boundary — the two axes are orthogonal and compose.
2. **Onion dependency direction: arrows point inward only.** `entry-point infra → application → domain ←
   storage infra`. The domain is the innermost ring and depends on nothing outward; both infra rings depend
   inward on the domain. Repository and gateway **ports live in the domain**; their implementations live in
   the storage infra ring.
3. **Hybrid domain-service placement** (DM `01M576E578836JG8XG90D3PDD8`). Pure domain services hold domain
   logic and live in the domain ring (plain Java, no annotations). The application ring handles read/write
   use-case orchestration plus port and transaction wiring. Use cases are the contract-neutral seam every
   inbound adapter shares; a transport type never crosses into the application or domain ring.
4. **One Gradle module per concern slice; layers are packages within the module** (DM
   `01M576E6NQGGSE8ESV765NGH7X`). Package naming is `ai.speckitty.charter.<concern>.<layer>`. A
   `bootstrap` module is the only composition root that knows every slice. ArchUnit, not module boundaries,
   enforces the layering *inside* each slice; the slice boundary is the module boundary.
5. **ArchUnit is the enforcement gate.** The following named rules make the two axes a build gate, not a
   habit. They are the acceptance gate for the eventual implementation mission:

   | Rule | Statement |
   |---|---|
   | `DOMAIN_IS_PURE` | classes in `..domain..` depend only on `java..`, `..kernel.domain..`, and their own slice's `..domain..` |
   | `NO_INFRA_ANNOTATIONS_IN_DOMAIN` | no class/field/method in `..domain..` carries a Spring, Jakarta, Jackson/YAML, JPA or GraphQL annotation |
   | `ONION_DEPENDS_INWARD` | `..infra..` → `{..application.., ..domain..}`; `..application..` → `..domain..`; `..domain..` → nothing outward |
   | `ENTRYPOINT_OVER_APPLICATION` | `..infra.api.rest..` / `..infra.api.graphql..` depend on `..application..` (and generated API/GraphQL types) but **not** on `..infra.storage..` |
   | `STORAGE_RETURNS_DOMAIN` | storage-adapter port implementations return only `..domain..`/`..kernel.domain..` (or JDK) types |
   | `PORTS_LIVE_IN_DOMAIN` | repository/gateway interfaces reside in `..domain..`; implementations in `..infra.storage..` |
   | `SLICES_DO_NOT_CROSS` | a slice's `..domain..` depends on no other slice except `..kernel..` |
   | `TRANSPORT_STAYS_IN_ADAPTER` | generated OpenAPI DTOs / GraphQL types are referenced only in `..infra.api..` |
   | `RESOLVER_OWNS_NETWORK` | only the storage ring (resolver adapter) references git/HTTP/cache client libraries |
   | `GENERATED_API_NOT_IN_DOMAIN` | `..domain..` imports nothing from the generated `charter-api` package |

## Consequences

### Positive

- A new concern is one new module with the same four layers — slices scale by addition, not by reshaping.
- The no-infra-in-domain and inward-dependency rules are a failing build, not a review comment.
- The storage backend and the inbound surfaces can each change behind their ports/adapters without reaching
  the domain — the property ADR-0005 (storage port) and ADR-0007 (API adapters) both rely on.
- One `Pack`/`DRG` vocabulary in the shared kernel keeps slices from minting divergent shapes.

### Negative

- One module per slice is coarser than one module per `(slice × layer)`, so the layering inside a slice is
  gated only by ArchUnit, not by the compiler — a weaker guarantee that depends on the rules being run.
- The top-level shape diverges from the existing `hexagonal-service`/`overview` read/write module names,
  which must be updated to match (flagged in `architecture/hexagonal-service`).
- Cross-slice reuse must route through the kernel, which can pull vocabulary into the kernel over time.

### Risks

- If ArchUnit rules are skipped or weakened in CI, the pure-domain and inward-dependency invariants silently
  erode; the rule set must be a blocking gate.
- A read/write sub-split living inside the application ring (rather than as separate modules) relies on
  discipline to keep read and write orchestration from entangling.

## Alternatives considered

### Option A: Keep the horizontal read/write (CQRS) top-level split

Organize modules by use-case side (`charter-read-app`, `charter-write-app`, `adapter-in-*`) as the existing
docs do. Not chosen: it scatters a single concern across read/write modules and makes a vertical subdomain
hard to reason about or extract. Read/write is retained as a sub-split inside each slice instead.

### Option B: One Gradle module per (slice × layer)

Maximal compiler-enforced layering (a domain module cannot even see an infra module). Not chosen: it
multiplies module count fourfold for little gain over ArchUnit, which already gates the same invariants; the
slice is the boundary that matters for ownership and extraction.

### Option C: Domain-service implementations in the application ring (pure-hexagonal)

Put all service implementations in the application ring and keep the domain a data-only model. Not chosen: it
hollows out the domain and pushes domain logic outward. The hybrid keeps domain logic in the domain and
reserves the application ring for orchestration and wiring.

## References

- This repo: [ADR-0001](/adr/0001-java-service-with-openapi-contract/) (hexagonal Java service),
  [ADR-0005](/adr/0005-service-registers-and-distributes-packs/) (the model this structure houses),
  [ADR-0007](/adr/0007-rest-hateoas-contract-of-record/) (the inbound adapters over the application ring);
  `architecture/hexagonal-service` (to be updated to this top-level shape), `architecture/resolution-model`.
- Decision Moments: `01M5759JC2JMQT0HDE9A4HRKNG` (vertical slices top-level), `01M576E578836JG8XG90D3PDD8`
  (hybrid domain-service placement), `01M576E6NQGGSE8ESV765NGH7X` (one module per slice, ArchUnit layering).
- Directives: DIRECTIVE_001 / 031 (boundaries / bounded context).
</content>
