<p align="center"><img src="site/src/assets/brain-kitty.png" alt="Brain Kitty" width="160"></p>

# spec-kitty-charter

The **charter** of Spec Kitty: the governance authority of the Brain Kitty product line.
It resolves Charter Packs (built-in, org and project) and a project's active charter into
the guidance that Spec Kitty's runtime acts on at each step of a Mission.

This repository is where the charter becomes a **Java service built against an OpenAPI
contract**, released and versioned on its own.

## Status: bootstrap, first code

The charter still runs as Python inside the core CLI
([`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty), `src/charter/`), and
stays there as the reference until the Java service replaces it. This repository holds the
direction, the design, the decisions, the contract and the first slice of the service.

| Part | What it is | State |
|---|---|---|
| [`contracts/charter/`](contracts/charter/openapi.yaml) | OpenAPI 3.1 contract, the single source of every charter data transfer object (DTO), and its [BDD scenarios](contracts/charter/scenarios/) | `0.1.0-SNAPSHOT`: two read paths |
| [`service/`](service/) | Hexagonal Java 25 / Spring Boot 4 service, one per worktree, loopback only; Gradle convention plugins in [`build-logic/`](build-logic/) | skeleton: domain, read app, REST in, YAML out |
| Conformance corpus | Fixtures the Python and Java implementations must both pass | next ([roadmap](docs/plans/roadmap.md) step 3) |
| Python charter | Stays in core behind `charter.offering.api` as the oracle while Java replaces it ([ADR-0008](docs/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts.md)) | not moved here |
| [`docs/`](docs/README.md) | Context, architecture, decisions and plans | bootstrapped |

## How it fits

- **Contract first.** The service generates its server interfaces from the contract. The
  core ingests a pinned release of the same contract and generates the DTOs its CLI and its
  `next` state machine use. Neither side imports the other's code.
- **Staged behind one seam.** The Python seam `charter.offering.api` becomes the only entry
  point for CLI callers. Behind it, reads move to the Java service first (shadow, then
  authoritative with a loud fallback), and writes move later, one operation at a time. The
  Java service is a rewrite built against BDD and OpenAPI contracts; the Python charter does
  not move here.
- **Not an event producer.** Events come from the core CLI's one canonical event writer,
  which the runtime calls as Missions move through their lifecycle, and follow the
  [`spec-kitty-events`](https://github.com/spec-kitty/spec-kitty-events) contracts. The
  charter service answers questions; it does not write to the ledger.

## Governing decisions

- Core ADR `2026-10-12-2`: the charter becomes an external contract package in its own
  repository. Its precondition, moving the charter-internal imports of about 130 files onto
  `charter.offering.api`, gates 4.0.0 general availability (GA). It was tracked as
  [spec-kitty#645](https://github.com/spec-kitty/spec-kitty/issues/645) until that issue was
  closed as not planned on 2026-10-13; the import move is now carried by epic
  [spec-kitty#6099](https://github.com/spec-kitty/spec-kitty/issues/6099).
- Core ADR `2026-10-03-1` (Accepted, Amendment 2026-10-18): a staged Java service
  strangler for charter reads and writes.
- [ADR-0001](docs/adr/0001-java-service-with-openapi-contract.md): the charter is a Java
  service built against an OpenAPI contract.
- [ADR-0002](docs/adr/0002-contract-source-of-record.md): this repository is the source of
  record for that contract.
- [ADR-0008](docs/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts.md): the
  Java service is a rewrite that replaces the Python charter; the open-packs content must
  round-trip through its file-based layer.

## Building the service

```bash
./gradlew verifyAll   # contract validation, unit, architecture, BDD and open-packs round-trip
```

It needs Java 25 and a `spec-kitty-open-packs` checkout beside this one (or
`-PopenPacksDir=<path>`). Details: [Building the service](docs/guides/service-build.md).

## Documentation

The [`docs/`](docs/README.md) tree follows the
[doc-kitty](https://github.com/spec-kitty/doc-kitty) convention and is published from
`main` to <https://spec-kitty.github.io/spec-kitty-charter/> (build setup:
[Building the docsite](docs/guides/docsite.md)):

- [Context](docs/context/) — why, what the charter owns, and the systems around it.
- [Architecture](docs/architecture/) — the target design.
- [Decision Records](docs/adr/) — decisions here, and the core ADRs they build on.
- [Plans](docs/plans/) — the bootstrap roadmap and open questions.
- [API](docs/api/) — the planned contract surface.
- [Guides](docs/guides/) — contributing and building the docsite.
- [Changelog](docs/changelog/) — notable changes.

## License

[MIT](LICENSE) © 2026 Spec Kitty, Inc.
