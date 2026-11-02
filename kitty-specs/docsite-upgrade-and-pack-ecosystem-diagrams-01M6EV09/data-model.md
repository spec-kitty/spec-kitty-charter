# Data model: Docsite on DocKitty 0.6.0 and pack-ecosystem diagrams

This mission adds no runtime data. The entities below are the docs artifacts it produces and the rules each one must satisfy.

| Entity | Where | Invariants |
| --- | --- | --- |
| Toolkit pin | `site/package.json`, `site/package-lock.json` | Exact `0.6.0`; every new dependency is exact; lockfile integrity comes from the registry, never from a hand edit |
| Contract bundle | CI artifact `charter-contract`, staged at `site/openapi/charter-openapi.yaml` | Produced by `:contracts:bundleContract` only; never committed; its published copy and `.sha256` verify with `sha256sum -c` |
| API reference set | `/reference/api/main/` | Label `main`, `released: false`; built from the bundle; reachable from `docs/api/service-contract.md` |
| Staged schema copy | `site/schemas/*.json` (git-ignored) | A byte copy of `contracts/packs/schemas/*.json`, refreshed before every validate and build |
| Schema page | `docs/architecture/pack-schemas/<stem>.md` | `kind: Schematic`; `schematic_path: schemas/<stem>.schema.json`; repo frontmatter; linked from the hub; the built page carries a rendered diagram |
| Architecture diagram | `docs/architecture/diagrams/<stem>.puml` + `.svg` | `@startuml <stem>`; the SVG is rendered with the pinned repository jar and is current; it cites its ADRs; every open-question element is «pending» with ADR and OQ number |
| Pending marker | `_style.iuml` | One stereotype and one field suffix, used consistently, with a legend line on each diagram that uses them |
