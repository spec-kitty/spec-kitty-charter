# spec-kitty-charter

The **charter offering module** in the Brain Kitty product line. It provides
retrieval of charter packs to the Build Kitty surfaces, exposed through the
module's public API `charter.offering.api`.

## Status: early extraction — under active redesign

This repository is the canonical landing zone for the charter module, which is
being **extracted and re-designed out of the Spec Kitty core CLI** so it can be
released and versioned on its own, exactly as `spec-kitty-events` and
`spec-kitty-tracker` already are. Today the charter still ships inside the core
CLI wheel; this repository is where its independent form is taking shape.

The direction is recorded in the core repository's ADR
`2026-10-12-2` (charter-extraction-to-its-own-repository), which extends the
charter-wheel assessment (`2026-08-02-1`) and the shared-package-boundary
decision (`2026-04-25-1`).

Because the module is mid-extraction, treat everything here as provisional:
the public surface is `charter.offering.api`, but version numbers, install
instructions, and the final package shape are not settled yet and are
deliberately not published in this README.

## What this module does

- Holds the charter offering — governance and doctrine packs — for the Brain
  Kitty product line.
- Retrieves charter packs and serves them to the Build Kitty surfaces that
  consume governance.
- Is consumed only across its public contract boundary (`charter.offering.api`),
  never by reaching into its internals.

## Documentation

Project documentation lives under [`docs/`](docs/index.md) and follows the
Spec Kitty common-docs conventions. Start at the
[documentation landing page](docs/index.md), then branch into:

- [Architecture](docs/architecture/index.md) — why the module exists and how it
  is structured.
- [Guides](docs/guides/index.md) — task-oriented how-tos for consumers.
- [Development](docs/development/index.md) — contributor and maintainer how-tos
  and reference policy.
- [API reference](docs/api/index.md) — the `charter.offering.api` public
  surface.
- [Decision records](docs/adr/index.md) — architecture decision records for
  this module.

## License

[MIT](LICENSE) © 2026 Spec Kitty, Inc.
