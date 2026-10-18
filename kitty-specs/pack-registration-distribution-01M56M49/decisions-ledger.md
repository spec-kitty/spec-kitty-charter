# Interview results — resolved decision ledger

Pack registration & distribution design mission (`pack-registration-distribution-01M56M49`).
Decisions were taken as spec-kitty Decision Moments (records in `decisions/DM-*.md`);
this ledger is the consolidated, human-readable summary. Date: 2026-10-18.

## Architecture (reconciled with accepted ADRs)

| # | Decision | Choice | DM |
|---|----------|--------|----|
| 1 | Module layout | **Vertical concern slices top-level**; read/write is a sub-split inside each slice's application ring. Supersedes the horizontal read/write shape in `hexagonal-service.md`/`overview.md`. | `01M5759JC2JMQT0HDE9A4HRKNG` |
| 2 | GraphQL vs ADR-0002 | **OpenAPI stays the sole source of record**; the GraphQL schema is derived/generated from it. ADR-0002 preserved. | `01M575AAJW9R833MV6ZDA4G64J` |
| 3 | HATEOAS vs ADR-0004 | **Reusable `Links` schema component** referenced per response; DTOs stay closed (`additionalProperties:false`). ADR-0004 preserved. | `01M575AC288BKPRCCTJKT33H3G` |
| 4 | MCP adapter | **Deferred** as a later third entry-point adapter; use-case layer stays adapter-ready. Not built here. | `01M575ADHN04ZSRK007Y2S0CRF` |
| 5 | Domain-service impls | **Hybrid**: pure domain services hold domain logic; the application ring handles read/write use-case orchestration + port/transaction wiring. | `01M576E578836JG8XG90D3PDD8` |
| 6 | Gradle granularity | **One module per concern slice**; layers as packages within; ArchUnit enforces layering + no-infra-in-domain. | `01M576E6NQGGSE8ESV765NGH7X` |

## Pack sourcing & distribution

| # | Decision | Choice | DM |
|---|----------|--------|----|
| 7 | Source kinds | **Four in v1**: git URL, filesystem, service-stored, **published-artifact**. | `01M577HPH8R7T9NQGHZJ46WASF` |
| 8 | Built-in end-state | **Service-resolved** as the first catfooding consumer; committed trees retired (ADR-0005 end-state). | `01M577HR0FSCR6574VXB9K814C` |
| 9 | Built-in migration | **Staged** (resolver-behind-loader → integrity+lockfile → fetch-at-release → de-vendor); built-in **bundles into the installed distribution** for cold/offline installs. | `01M577HSFD75NHZHVMP9RGNEK7` |
| 10 | Trust & consent | **Explicit allowlist in config (v1)** on the floor: pin-to-rev + content-hash verify + offline cache + fail-closed. Built-in/open = high-trust; signing reserved as a higher tier. | `01M577HTZ0YTYYJRFCMDQN0PRN` |
| 11 | Lockfile granularity | **Two-tier**: a config-level lockfile of per-pack `{source, rev, snapshot_sha256}` + per-constituent `content_hash` in each `pack-manifest.yaml`. Reuse the snapshot_sha256 recipe. | `01M577WZG8726DCYH7ECDQWWZ1` |
| 12 | Storage backend | **File-based content-addressed** behind a storage-agnostic domain port; DB/object-store later without touching the domain. | `01M577X0ZJJW170A5RM1PP9AV2` |
| 13 | REST/GraphQL parity | **REST (OpenAPI-HATEOAS) ships in v1** as the source of record; **GraphQL is planned-later** — designed-for (use-case layer adapter-ready) but not built in v1. | `01M577X2EMGY9R5E2JC8PCJVTD` |

## Recorded default (not an operator choice)

- **Engine/compatibility gating**: a `requires_core`-style range on the registration/source descriptor + lockfile entry (NOT `pack.yaml`, which forbids extra fields); enforced at resolve time with a canonical refusal code sibling to `PACK_LEVEL_MISMATCH` (e.g. `PACK_ENGINE_INCOMPATIBLE`; exact string is a contract-naming detail). DM `01M5781BVDQ4NTHSKA95Z60XPY`.

## Deferred (policy — operator to set at storage-design time)

- **Private-pack tenancy/privacy boundary** — baseline: ownership/visibility enforced at the storage port + API (not directory presence). DM `01M5781ER9V60RN0KVBJH0EQRM` (deferred).
- **Retention / GC policy** — baseline: retain anything a lockfile pins, GC the rest. DM `01M5781HN6JHTV970D3V2N7FEF` (deferred).

## Still open (from research, not yet decided)

- Nothing blocking: the seven researched questions are resolved or deferred above; the two deferred items are storage-phase policy, not design blockers.
