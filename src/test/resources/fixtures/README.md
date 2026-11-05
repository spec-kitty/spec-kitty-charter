# Pack registration and distribution fixtures

The files the pack registration and distribution scenarios of #43 name: the operator configuration,
the lockfile and its stale copy, and the packs. They stay here, outside the contract tree, because a
YAML file under `contracts/charter/` is read as part of the contract. Their hashes and digests are
illustrative.

- `operator-config/charter-offering.config.yaml`: allowlist, engine version and trust floor.
- `lockfile/charter.lock.yaml`: the configuration-level two-tier lockfile, one entry per pack.
- `lockfile/charter.lock.stale.yaml`: the same with a mismatched snapshot digest (integrity failure).
- `packs/<id>/pack.yaml` and `pack-manifest.yaml`: a pack per source kind and tier.
- `packs/acme-review-pack/acme-review-pack.source.yaml`: a per-pack source descriptor (where
  `requires_core` and the pin live, outside `pack.yaml`).
- `packs/acme-review-pack-tampered/`: a constituent altered after pinning (integrity failure).

## The scenarios that use them

The scenarios moved from `src/test/resources/features/` (#374) and now live next to the contract, one
folder per cluster, tagged `@WIP` and not `@contract`:

| Scenarios | Cluster | Surface they need |
|---|---|---|
| `contracts/charter/scenarios/registration/register-pack.feature` | Pin and record a pack from each of the four source kinds; fail closed on an id or slug conflict. | `POST /packs` |
| `contracts/charter/scenarios/trust/source-trust.feature` | The operator allowlist admits or refuses git sources; built-in is high-trust; the pin-to-rev floor. | `POST /packs` |
| `contracts/charter/scenarios/integrity/pin-and-verify.feature` | Two-tier integrity checked when a pack is distributed; fail closed on a mismatch. | `POST /packs/{packId}/distributions`, `GET /packs/{packId}/resolution` |
| `contracts/charter/scenarios/distribution/resolve-and-distribute.feature` | One canonical path for every pack; engine gate; offline cache; unreachable source. | `POST /packs/{packId}/distributions`, `GET /packs/{packId}/resolution` |

These scenarios are not executed, by decision (#374). They specify the pack registration and
distribution surface of ADR-0005 and ADR-0019, which is decided but not built
(`docs/api/service-contract.md`, "Decided, not built") and outside the cutover scope (ADR-0055 D1).
`CharterContractSuite` runs only `@contract` and excludes `@WIP`, so their steps are not bound.

What already holds for configured packs is covered by contract scenarios:

- REGISTER-003 by `composition.feature` "Two configured packs declaring one pack_id refuse the compose".
- REGISTER-004, at one level, by `composition.feature` "Two siblings defining one artifact, unsettled,
  refuse the compose". Across levels the same id is a layer override (ADR-0053 decision 6).
- DISTRIBUTE-002's read side, built-in with no shortcut, by the ordering scenarios of `offering.feature`
  and `composition.feature` "The single built-in tree still loads".

DISTRIBUTE-006 is deferred (decision `01M5781ER9V60RN0KVBJH0EQRM`).

When the surface is scheduled, each cluster is bound where it now lives (open question 16): its refusal
codes take the contract's snake_case names, its tiers become levels, and a slug collision is refused at
one level only. ADR-0048 has core fetch the built-in pack itself, against ADR-0005 decisions 2 and 7;
that tension affects TRUST-003 and DISTRIBUTE-002 and is noted, not ruled.
