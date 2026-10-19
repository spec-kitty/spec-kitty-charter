---
title: "ADR-0005: The charter offering service registers and distributes packs; built-in is its first catfooding consumer"
description: The service registers and distributes packs of every level through its OpenAPI API from four source kinds; built-in rides that path as the first catfooding consumer.
doc_status: draft
updated: 2026-10-19
type: ADR
kind: ADR
related:
  - adr/0001-java-service-with-openapi-contract
  - adr/0004-public-api-returns-generated-dtos
  - adr/0006-onion-vertical-slices-archunit
  - adr/0007-rest-hateoas-contract-of-record
  - architecture/resolution-model
  - architecture/descriptors
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for how packs are registered, sourced, verified and distributed by the service.
  - profile: core-maintainer
    guidance_text: Read this to see how core, as a generated client, obtains packs instead of vendoring them.
  - profile: evaluator
    guidance_text: Read this for the pack sourcing and distribution model and the supply-chain stance behind it.
---

# ADR-0005: The charter offering service registers and distributes packs; built-in is its first catfooding consumer

## Status

**Proposed** — 2026-10-18. Decider: Stijn Dejongh (operator). This record sets the pack sourcing and
distribution model for the charter offering service and supersedes vendoring as this repository's
end-state. It builds on [ADR-0001](/adr/0001-java-service-with-openapi-contract/) (the charter is a Java
service behind an OpenAPI contract) and [ADR-0004](/adr/0004-public-api-returns-generated-dtos/) (the
public API returns contract-generated DTOs). It is the design-mission realization of thirteen resolved
Decision Moments; the cited DM ids are the authoritative record of each choice.

**Amendment 2026-10-19** (operator ruling): decision 9 is amended. `requires_core` stays in
`pack.yaml` as an optional PEP 440 range of supported Spec Kitty core versions. The point is
annotated where it stands.

## Context

The charter offering service (this repository; ADR-0001) manages charter offerings, and a charter is built
from **packs**. The service must not only resolve the **built-in** and **org** levels — it must **store and
distribute consumer-created and non-open (private) packs**. A pack **registration + distribution** capability
is therefore a day-one requirement of the service, not an optional convenience.

Today packs are **vendored**: this repository carries `packs/internal` and `packs/qa` as hand-synced mirrors,
and core carries `packs/built-in`. Vendoring duplicates the source of truth and must be reconciled by manual
provenance tracking and drift gates.

Core treats built-in as a committed, pinned, drift-gated tree (core ADR
[`2026-08-16-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-08-16-2-open-packs-is-source-of-truth-for-built-in-doctrine.md)
Option B, amended by core #6045; implemented by #3504 / PR #6363). **For the purposes of this design that
work is prior art — it inspires but does not constrain the solution space here.**

Interaction with core is fixed by ADR-0001/0002/0004: an **OpenAPI contract**, a **Swagger/docsite** contract
description, and a **generated Python client**. Whatever this service does with packs, core consumes it **as a
client of that contract** — it does not re-implement resolution or vendor packs in the end-state.

## Decision

1. **The service owns pack registration and distribution, exposed in the OpenAPI contract.** A registered
   pack declares a **source** of one of **four kinds** (DM `01M577HPH8R7T9NQGHZJ46WASF`): a **git URL**
   (pinned to a commit rev), a **filesystem location**, a **service-stored** pack (uploaded/held — the
   mechanism for consumer and private packs), and a **published-artifact** source identified by a
   **purl-style locator `pkg:charter/<id>@<version>`** (a versioned package the service pulls from a
   registry; DM `01M578WH1TV7FYJFBNHX7BXN5F`). The `source` kind is a versionable discriminator; `bundled`
   is a resolve-time materialization outcome, not a registration input.
2. **One canonical path for every pack** (DIRECTIVE_044). Built-in, org, consumer and private packs all
   register and resolve through this one API; the requester tier is **derived from the authenticated
   identity, never a client-set field** (DM `01M578W9FK24NNSFRYT7C5ZZZ1`; fail-closed trust, with authn/authz
   a separate downstream concern), and it selects behavior as a parameter, never a separate code path.
   **Built-in is the first catfooding consumer** (DM `01M577HR0FSCR6574VXB9K814C`): resolved through the
   service from its versioned, tagged, published open-packs ref, not vendored as a special case. The one pack
   we most control is the one we most want continuously exercising the distribution path every consumer
   depends on. Committed production trees (`packs/internal`, `packs/qa` here; built-in wherever this model
   reaches core) are retired in the end-state.
3. **The committed artifact is the pin, not the content, and the lockfile is two-tier** (DM
   `01M577WZG8726DCYH7ECDQWWZ1`). A **config-level lockfile** carries one entry per registered pack —
   `{source, rev, snapshot_sha256}` — as the single reproducibility fact; **per-constituent `content_hash`**
   stays in each pack's generated `pack-manifest.yaml` for file-level verification. The
   `spec-kitty-snapshot-sha256` recipe is reused verbatim; no competing hash is introduced.
4. **Storage is file-based content-addressed behind a storage-agnostic domain port** (DM
   `01M577X0ZJJW170A5RM1PP9AV2`). The domain declares repository/gateway ports returning plain domain models;
   a file-based content-addressed adapter is the shipped default. A database or object-store adapter can sit
   behind the same ports later **without touching the domain, the application ring, inbound adapters or the
   core seam**. Content-addressing dovetails with the integrity model in decision 3 and dedupes identical
   constituents.
5. **The built-in migration is staged, and the installed distribution bundles built-in** (DM
   `01M577HSFD75NHZHVMP9RGNEK7`). The swap is sequenced: **resolver-behind-loader → integrity + lockfile →
   fetch-at-release → de-vendor**. A built-in pack **bundles into the installed distribution** so cold and
   offline installs resolve without a network. Retiring any committed tree is a later, sequenced step gated
   on the resolver, cache and lockfile existing and being proven — not a free swap.
6. **Test determinism via `src/test/resources`.** The service's own tests read committed fixtures — the
   subset a test exercises — so tests stay deterministic and offline without vendoring whole production trees.
7. **Core is a generated client.** Core obtains packs through the generated Python client against this
   contract (ADR-0004); it does not fetch, merge or vendor in the end-state. Core's current vendoring is a
   transitional state and prior art, not a constraint on this contract.
8. **Supply-chain controls: explicit allowlist on a fail-closed floor** (DM `01M577HTZ0YTYYJRFCMDQN0PRN`;
   recorded per DIRECTIVE_051, DIRECTIVE_003). Every non-stored source pins a **rev, never a floating ref**,
   and every materialized constituent is **integrity-verified** against its `content_hash` / tree digest. A
   consumer/private **git** source sits behind an **explicit allowlist in config**; unlisted or unreachable
   sources **fail closed**, never silent fetch-on-use. The allowlist has **mixed per-entry granularity** (DM
   `01M578WJG7ZQ9AAX4WWP1H555M`): each entry may be scoped to a **host**, an **org**, or an **exact repo**,
   the exact-repo scope being least privilege. A sketch of the config shape:

   ```yaml
   trust:
     allowlist:
       - scope: host         # broadest
         match: github.com
       - scope: org          # one owner/org on a host
         match: github.com/spec-kitty
       - scope: repo         # exact repo — least privilege
         match: github.com/spec-kitty/spec-kitty-open-packs
   ```

   An **offline cache** lets a populated service resolve with no network. **Trust tiers:** built-in and open
   packs are a high-trust tier above the allowlist; **signing is reserved as a higher tier** to adopt when
   core's verified-distribution scheme lands.
9. **Engine/compatibility is gated at resolve time** (DM `01M5781BVDQ4NTHSKA95Z60XPY`). A `requires_core`-style
   range lives on the **registration/source descriptor and the lockfile entry** — **not** in `pack.yaml`,
   whose `PackDescriptor` schema forbids extra fields. *(Amended 2026-10-19: the operator keeps
   `requires_core` in `pack.yaml` as an optional PEP 440 range — a defined field, not an extra one,
   so the "forbids extra fields" rationale above no longer applies — as core and open-packs already
   use it; the registration descriptor and the lockfile carry it too. See
   [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/).)* It is enforced at **resolve time** with a canonical
   refusal code sibling to `PACK_LEVEL_MISMATCH` (e.g. `PACK_ENGINE_INCOMPATIBLE`; the exact string is a
   contract-naming detail ratified with the contract), failing closed before any merge work.
10. **Resolver / merge boundary** (DIRECTIVE_001, 031). The *resolver* (fetch / materialize / verify / store)
    is a separate concern from *merge and activation*; it hands downstream a verified local materialization
    plus its manifest. The domain never sees a URL — only a `PackSource` value and a verified local
    materialization. The OpenAPI contract is the single translation layer between service and client.

## Consequences

### Positive

- One canonical distribution path, dogfooded by our own flagship pack — the path consumers depend on is
  continuously exercised by us.
- Consumer-created and non-open packs are first-class, not bolted on later; the published-artifact kind keeps
  the north-star data-dependency path open without reshaping existing responses.
- No hand-reconciled vendored trees and no content-drift gates in the end-state; the two-tier lockfile is the
  single committed fact, with file-level verification deferred to each pack's manifest.
- Core is decoupled to a generated client of the contract; pack mechanics live behind one seam, and the
  storage backend can change without reaching the domain.

### Negative

- A cold, no-network, non-test environment needs one bootstrap fetch to populate built-in, mitigated by the
  offline cache, by `src/test/resources` fixtures for tests, and by the installed distribution bundling
  built-in at release (decision 5).
- Built-in becomes a release-time dependency on its published open-packs ref — a broken or unpinned ref
  breaks the release (the pin + integrity verify is what contains this).
- The registration API, resolver, cache and lockfile must exist and be proven before any vendored tree is
  retired — staged sequencing, not a free swap.
- Four source kinds plus a published-artifact pull path widen the conformance surface over a three-kind v1.

### Risks

- **Supply chain:** fetching doctrine over git is an execution-of-third-party-content surface. The v1 floor is
  **tamper-evidence** (content hash) plus an **allowlist**, not tamper-proof signing; signing is a reserved
  higher tier. Contained by pin-to-rev + integrity verify + allowlist + fail-closed (decision 8).
- **Version/engine skew:** a fetched pack may not match the engine — gated at resolve time (decision 9).
- **Source conflict:** two sources claiming the same pack id, or a constituent slug that collides across
  packs, are **refused** — **fail closed** (DIRECTIVE_043), never last-write-wins. The **constituent-slug
  namespace is global** (DM `01M578WCEQERGSG02RPN29KRZF`): a slug collision across packs is a refusal, not a
  per-pack shadow; the choice is reversible to per-pack scope if it proves too strict.
- **Deferred storage policy (flagged, not resolved here):** the private-pack tenancy/privacy boundary (DM
  `01M5781ER9V60RN0KVBJH0EQRM`, deferred — baseline: ownership/visibility enforced at the storage port and
  API, never by directory presence) and the retention/GC policy (DM `01M5781HN6JHTV970D3V2N7FEF`, deferred —
  baseline: retain anything a lockfile pins, GC the rest) are operator-owned, set at storage-design time.

## Alternatives considered

### Option A: Vendor every pack (status quo; mirrors core's Option B)

Keep committed trees reconciled by drift gates. Rejected: duplicates the source of truth, needs manual
provenance and drift machinery, and — decisively — leaves the service's core function (pack distribution)
un-dogfooded by our most-controlled pack, so distribution bugs surface in consumers rather than in us.

### Option B: A published data-dependency package per pack as the primary mechanism (core's Option C north star)

Ship each pack only as a versioned published artifact a client depends on. Not chosen as the *primary*
mechanism: it does not provide the **runtime registration** of consumer and private packs the service must
offer, and the service's distribution API subsumes it. It is adopted instead as **one of the four source
kinds** (decision 1), keeping the north-star path open without making it the only path.

### Option C: Vendor built-in only; register the rest

Fetch/register org, consumer and private packs but keep built-in vendored. Rejected: it special-cases the one
pack we most control onto a non-canonical surface — the exact DIRECTIVE_044 inversion this ADR removes.

### Option D: Trust-on-first-use or signing-only trust in v1

Record the pin on first fetch (TOFU), or require a signed provenance chain, instead of an allowlist. TOFU is
hostile to headless/CI runs and weak to record-poisoning; signing-only is not buildable on existing primitives
(core's verified-distribution scheme #2539/#2543 is unstarted). Rejected for v1: the allowlist on a fail-closed
floor is the headless-friendly default, with signing reserved as a higher tier (decision 8).

## References

- This repo: ADR-0001 (Java service + OpenAPI), ADR-0002 (contract source of record), ADR-0004 (generated
  DTOs), [ADR-0006](/adr/0006-onion-vertical-slices-archunit/) (layering that houses this model),
  [ADR-0007](/adr/0007-rest-hateoas-contract-of-record/) (the API surface it is exposed through);
  `architecture/resolution-model`, `architecture/descriptors`.
- Decision Moments: `01M577HPH8R7T9NQGHZJ46WASF` (four source kinds), `01M577HR0FSCR6574VXB9K814C` (built-in
  end-state), `01M577HSFD75NHZHVMP9RGNEK7` (staged migration + bundle), `01M577HTZ0YTYYJRFCMDQN0PRN` (trust /
  allowlist), `01M577WZG8726DCYH7ECDQWWZ1` (two-tier lockfile), `01M577X0ZJJW170A5RM1PP9AV2` (storage port),
  `01M5781BVDQ4NTHSKA95Z60XPY` (engine-compat); `01M578WH1TV7FYJFBNHX7BXN5F` (purl published-artifact
  locator), `01M578WJG7ZQ9AAX4WWP1H555M` (mixed-granularity allowlist), `01M578W9FK24NNSFRYT7C5ZZZ1`
  (requester tier from auth), `01M578WCEQERGSG02RPN29KRZF` (global constituent-slug namespace); deferred
  `01M5781ER9V60RN0KVBJH0EQRM` (tenancy), `01M5781HN6JHTV970D3V2N7FEF` (retention).
- Core prior art (reference, not binding): ADR `2026-08-16-2` + its 2026-10-11 amendment, core #6045, #3504 /
  PR #6363; ADR `2026-05-16-1` (layer merge); ADR `2026-10-11-2` (`pack_level`).
- open-packs: #23 (checksum/verification/trust model), #31/#32 (built-in seed + `snapshot_sha256` +
  `requires_core`).
- Directives: DIRECTIVE_044 (canonical source), DIRECTIVE_051 (supply-chain install safety), DIRECTIVE_043
  (close defect classes by construction), DIRECTIVE_001 / 031 (boundaries / bounded context), DIRECTIVE_003
  (decision documentation).
</content>

</invoke>
