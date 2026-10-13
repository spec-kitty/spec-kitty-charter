# spec-kitty-charter

The **charter** of Spec Kitty: the governance authority of the Brain Kitty product line.
It resolves Charter Packs (built-in, org and project) and a project's active charter into
the guidance that Spec Kitty's runtime acts on at each step of a Mission.

This repository is where the charter becomes a **Java service built against an OpenAPI
contract**, released and versioned on its own.

## Status: bootstrap, no code yet

The charter still runs as Python inside the core CLI
([`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty), `src/charter/`). This
repository holds the agreed direction, the target design and the decisions; code follows.

| Part | What it is | State |
|---|---|---|
| `contracts/charter/` | OpenAPI 3.1 contract: the single source of every charter DTO | planned |
| Java service | Hexagonal Java 25 / Spring Boot 4 service, one per worktree, loopback only | planned |
| Conformance corpus | Fixtures the Python and Java implementations must both pass | planned |
| Python charter package | Today's `src/charter/` from core, behind `charter.offering.api` | likely moves here first; to be decided |
| [`docs/`](docs/README.md) | Context, architecture, decisions and plans | bootstrapped |

## How it fits

- **Contract first.** The service generates its server interfaces from the contract. The
  core ingests a pinned release of the same contract and generates the DTOs its CLI and its
  `next` state machine use. Neither side imports the other's code.
- **A strangler, not a rewrite-and-swap.** In the core, the Python seam
  `charter.offering.api` stays the only entry point for CLI callers. Behind it, reads move
  to the Java service first (shadow, then authoritative with a loud fallback), and writes
  move later, one operation at a time.
- **Not an event producer.** Events come from the runtime's `next` transitions and follow
  the [`spec-kitty-events`](https://github.com/spec-kitty/spec-kitty-events) contracts. The
  charter answers questions; it does not write to the ledger.

## Governing decisions

- Core ADR `2026-10-12-2`: the charter becomes an external contract package in its own
  repository. Its precondition, moving about 129 internal imports onto
  `charter.offering.api` ([spec-kitty#645](https://github.com/spec-kitty/spec-kitty/issues/645)),
  gates 4.0.0 GA. Epic: [spec-kitty#6099](https://github.com/spec-kitty/spec-kitty/issues/6099).
- Core ADR `2026-10-03-1` (Proposed): a staged Java service strangler for charter reads
  and writes.
- [ADR-0001](docs/adr/0001-java-service-with-openapi-contract.md): the charter is a Java
  service built against an OpenAPI contract.
- [ADR-0002](docs/adr/0002-contract-source-of-record.md) (Proposed): this repository is
  the source of record for that contract.

## Documentation

The [`docs/`](docs/README.md) tree follows the
[doc-kitty](https://github.com/spec-kitty/doc-kitty) convention:

- [Context](docs/context/) — why, what the charter owns, and the systems around it.
- [Architecture](docs/architecture/) — the target design.
- [Decision Records](docs/adr/) — decisions here, and the core ADRs they build on.
- [Plans](docs/plans/) — the bootstrap roadmap and open questions.
- [API](docs/api/) — the planned contract surface.
- [Guides](docs/guides/) — contributing.
- [Changelog](docs/changelog/) — notable changes.

## License

[MIT](LICENSE) © 2026 Spec Kitty, Inc.
