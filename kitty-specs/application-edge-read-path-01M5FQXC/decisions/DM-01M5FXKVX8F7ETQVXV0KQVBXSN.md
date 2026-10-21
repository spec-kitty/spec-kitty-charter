# Decision Moment `01M5FXKVX8F7ETQVXV0KQVBXSN`

- **Mission:** `application-edge-read-path-01M5FQXC`
- **Origin flow:** `plan`
- **Slot key:** `plan.structure.deployable-package`
- **Input key:** `deployable_package`
- **Status:** `resolved`
- **Created:** `2026-10-21T19:04:40.616563+00:00`
- **Resolved:** `2026-10-21T19:08:38.990197+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How should the deployable composition root relate to the existing bootstrap package?

## Options

- Rename bootstrap -> deployable (redistribute its wiring into per-layer configs)
- New deployable package alongside; delete bootstrap
- Other

## Final answer

Rename bootstrap -> deployable. deployable holds @SpringBootApplication + an aggregate DeployableConfiguration that @Imports each layer's own @Configuration. The current bootstrap wiring (CharterWiring/GuardWiring/PackPersistenceWiring) is redistributed into the per-layer configs. Retarget ONION_ONLY_BOOTSTRAP_COMPOSES_ADAPTERS and the NullMarked/other gate references from bootstrap to deployable.

## Rationale

_(none)_

## Change log

- `2026-10-21T19:04:40.616563+00:00` — opened
- `2026-10-21T19:08:38.990197+00:00` — resolved (final_answer="Rename bootstrap -> deployable. deployable holds @SpringBootApplication + an aggregate DeployableConfiguration that @Imports each layer's own @Configuration. The current bootstrap wiring (CharterWiring/GuardWiring/PackPersistenceWiring) is redistributed into the per-layer configs. Retarget ONION_ONLY_BOOTSTRAP_COMPOSES_ADAPTERS and the NullMarked/other gate references from bootstrap to deployable.")
