# Quickstart — validating the mission

How to verify the delivered mission end-to-end. All commands run from the repository root.

## 1. Structure & layering (IC-01/IC-02)
```bash
./gradlew :bootstrap:test --tests '*HexagonalArchitectureTest'
```
Expect: the ADR-0006 slice rules pass — `DOMAIN_IS_PURE`, `NO_INFRA_ANNOTATIONS_IN_DOMAIN`, `ONION_DEPENDS_INWARD`, `ENTRYPOINT_OVER_APPLICATION`, `STORAGE_RETURNS_DOMAIN`, `PORTS_LIVE_IN_DOMAIN`, `SLICES_DO_NOT_CROSS`, `TRANSPORT_STAYS_IN_ADAPTER`, `RESOLVER_OWNS_NETWORK`, `GENERATED_API_NOT_IN_DOMAIN`, plus the validation-purity rule — and each known-bad control reds the rule it targets.

## 2. Validation module purity (IC-03)
```bash
./gradlew :validation:test
```
Expect: `guard()` throws on the first breach (bespoke exception); `check()` accumulates all breaches; zero third-party imports (ArchUnit validation-purity rule green; known-bad would-import control reds).

## 3. Round-trip fidelity on the DSO (IC-05/IC-08)
```bash
./gradlew :packs:test --tests '*RoundTrip*' --tests '*WriteSafety*'
```
Expect: fetch→write(fresh dir)→refetch yields an **equal DSO** (order/duplicate-sensitive, incl. `active_charter`/`pack_drg`); a mutation written through the port changes what refetches (falsifiable); a failure-injected write leaves the target untouched with no stray temp; a symlink escaping the pack root is refused (with an in-root positive control).

## 4. Storage-transparent repositories through the wired bean (IC-04/IC-06)
```bash
./gradlew :bootstrap:test --tests '*Wiring*'
```
Expect: `load(PackIdentifier, TacticIdentifier)` / `save(Tactic)` resolve through the **wired** `@Service` bean; the port interfaces name only domain types (verified by the port-signature ArchUnit rule + known-bad).

## 5. Full gate
```bash
./gradlew verifyAll
```
Expect: green, except the pre-existing environmental `spec-kitty-open-packs` pin mismatch on `VendoredPacksTest` (CI uses the pinned ref; locally pass `-PopenPacksDir=<checkout-at-pin>`).

## 6. Docs (IC-07)
- `docs/development/code-style-and-conventions.md`, the "How to DDD here" guide, and `docs/architecture/architectural-patterns-applied.md` (embedding the C4 + class diagrams) exist and document the specific rules (records-vs-builders, DIP, identity-vs-fidelity, DTO vs DSO).
- `docs/glossary` defines **Domain Entity vs Persistence Entity** (+ value object, aggregate root, port/adapter, projection, DTO vs DSO, identifier VO).
- `docs/changelog` records the `validation` module, the repository reshape, and the screaming-architecture slice migration.

## Acceptance scenario map
US1 → §4; US2 → §3; US3 → §3/§4 (inspection query); US4 → §2; US5 → §1/§6.
