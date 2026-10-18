# Pack registration & distribution — BDD feature suite

Promise-first BDD scenarios that define **what the charter offering service does for a consumer**
when it registers, verifies, resolves and distributes packs — independent of the implementation
that delivers it. They are the behavioral contract this mission specifies *before* the OpenAPI
surface is authored: the scenarios are the public-surface design input (per the mission brief and
`docs/architecture/behavior-and-scenarios.md`).

Authority: the 13 decisions in
`kitty-specs/pack-registration-distribution-01M56M49/decisions-ledger.md`. The v1 public surface is
**REST (OpenAPI-HATEOAS)**; GraphQL is planned-later (DM-13) and is **not** scenarioed here.

## Status

- **Layer A — the contract (here now):** the `.feature` files and the `fixtures/` tree. They are
  implementation-free Gherkin; they import no code and are valid as they stand.
- **Layer B — the harness (not yet wired):** no Cucumber-JVM runner or step definitions exist in
  this repository yet (no Java in this mission). Standing up the runner and binding the steps is a
  later mission. The fixtures below are the data those steps will bind to; hash/digest values are
  illustrative until the harness recomputes them.

## Layout

One feature per behavior cluster:

| Feature | Cluster | Implied REST surface (v1 source of record) |
|---------|---------|---------------------------------------------|
| `registration/register-pack.feature` | Intake: pin & record a pack from each of the four source kinds; fail closed on id/slug conflict. | `POST /api/v1/packs` |
| `trust/source-trust.feature` | Allowlist admits/refuses git sources; high-trust built-in bypasses; pin-to-rev floor. | `POST /api/v1/packs` (pre-fetch allowlist check) |
| `integrity/pin-and-verify.feature` | Two-tier integrity at resolve; fail closed on constituent-hash or snapshot-digest mismatch. | `POST /api/v1/packs/{packId}/resolutions` |
| `distribution/resolve-and-distribute.feature` | One canonical path across the four tiers; built-in catfooding; engine gate; offline cache; unreachable fail-closed; one `@deferred` tenancy placeholder. | `GET /api/v1/packs/{packId}/distribution` |

## Fixtures (`fixtures/`)

- `operator-config/charter-offering.config.yaml` — allowlist + engine version + trust floor.
- `lockfile/charter.lock.yaml` — the config-level two-tier lockfile (one entry per pack).
- `lockfile/charter.lock.stale.yaml` — variant with a mismatched snapshot digest (integrity fail).
- `packs/<id>/pack.yaml` + `pack-manifest.yaml` — a pack per source kind / tier.
- `packs/acme-review-pack/acme-review-pack.source.yaml` — per-pack source descriptor (shows where
  `requires_core`/pin live, outside `pack.yaml`).
- `packs/acme-review-pack-tampered/` — a constituent altered after pinning (integrity fail).

## Authoring

Follows the house style in `tests/scenarios/charter/` and
`docs/architecture/behavior-and-scenarios.md`: outcomes rather than output surfaces, one promise per
scenario, the consumer's language, `@promise:` tags mapped to their authority. The authority map for
this suite is `kitty-specs/pack-registration-distribution-01M56M49/bdd-scenarios.md`.
