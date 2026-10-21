# Quickstart — Application-edge read path on the Pack model

## Retrieve a pack's tactics (the new read path)

```
GET  /api/v1/packs/{packId}/tactics            → 200 [ Tactic, … ]   | 404 Problem (pack absent/unreadable)
GET  /api/v1/packs/{packId}/tactics/{tacticId} → 200 Tactic          | 404 Problem (pack or tactic absent)
```

The request flows: thin controller (`infra.api`) → `TacticReadService` (`application` edge, `@Service`) → repository ports (`packs.domain`) → `DomainRepositoryService` (`infra.storage`, scan-driven). The controller holds no business logic.

## Run a modular wired test (the payoff of per-layer configs)

A `@SpringBootTest` can provision one layer slice wired to its real collaborators via the deployable aggregate:

```java
@SpringBootTest(classes = { StorageConfiguration.class, ApplicationConfiguration.class })
class TacticReadWiredTest {
    @Autowired TacticReadService tactics;   // real application bean
    // ... point a configured pack root at a fixture, then read its tactics through the production path
}
```

- Each layer's `@Configuration` (`StorageConfiguration`, `ApplicationConfiguration`, `ApiConfiguration`) composes that layer; `DeployableConfiguration` `@Import`s them for the full app.
- Exactly one bean per repository port exists (no duplicate from a widened scan) — asserted by the wiring test.

## Verify locally (targeted, not heavy — C-007 / NO_FULL_HEAVY_SUITES)

```bash
# Architecture gate (ADR-0006, revised for application-as-edge) + its known-bad controls:
./gradlew :deployable:test --tests '*HexagonalArchitectureTest'

# Domain + application + storage behaviour, incl. the upsert save contract test:
./gradlew :kernel:test :packs:test

# One modular wired slice:
./gradlew :deployable:test --tests '*TacticReadWiredTest'
```

CI owns the full suites, the docs-freshness gates, and the nightly PIT (kernel).

## What is gone (one model, no coexistence)

`CharterPack`, `CharterOffering`, `CharterPackRepository`, `FileSystemCharterPackRepository`, `ConsistentLoad`, `PackListingQuery`, `ActionContextQuery`, and the `GET /packs`, `POST /packs/refresh`, `GET /mission-types/{…}/context` endpoints. `GET /info` stays.
