# Research — Application-edge read path on the Pack model

## Resolved decisions (Decision Moments)

| DM | Decision | Rationale | Alternatives rejected |
|----|----------|-----------|-----------------------|
| scope.read-write-boundary | Read-path only; new reads are **tactics only** | Descriptor/DRG/other artefacts' read models not yet ruled; keep scope honest | Read+write (bigger surface, not needed); all-artefacts (models unruled) |
| scope.charterpack-reconciliation | Delete `CharterPack*` outright **and** retire pack-listing + action-context endpoints | One canonical model; their Pack-model read models aren't ruled, so they can't be rebuilt now | Keep CharterPack as interim (ships two roots); migrate-all-now (needs unruled models) |
| api.tactic-endpoint-shape | `GET /packs/{packId}/tactics` + `GET /packs/{packId}/tactics/{tacticId}` | Covers list + point-read; the single route exercises NFR-001 directly | Collection-only / single-only (narrower than the read story needs) |
| gate.application-edge-rule | Replace `APPLICATION_IS_PLAIN_JAVA` with positive `APPLICATION_IS_EDGE` | Keeps a biting guard after the doctrine flip (application may use framework but not cross to other-slice infra/deployable) | Delete the rule (loses the cross-boundary guard) |
| structure.deployable-package | Rename `bootstrap` → `deployable`; aggregate `DeployableConfiguration` @Imports layer configs | One composition root named for what it is; cleaner diff than delete+add | New package + delete bootstrap (harder-to-read diff) |
| contract.save-semantics | `TacticRepository.save` is **upsert** (create-or-update) | Operator ruling; the port's first copyable contract should be unambiguous and complete | Replace-existing-only (narrower; operator chose upsert) |

## Second-opinion squad findings — disposition

Three opus lenses ran over the pre-redesign integrated diff. Dispositions:

- **MAJOR — two aggregate roots** (`Pack` wired but live path on `CharterPack`): **resolved by deletion** (IC-05, FR-004). No coexistence ships.
- **MAJOR — duplicate violations seam** (`PackInspectionQuery.partialReads` duplicates `Pack.violations()`): **fold** — collapse to a single owner on the aggregate (IC-06, FR-010).
- **MAJOR — `save` create-vs-update undefined**: **fold** — pinned as upsert with a both-arm port contract test (IC-06, FR-009).
- **MINOR — child-entity write ports bypass the root**: fold — enforce invariants on the save path (IC-06).
- **MINOR — `SerializationStrategy` only swappable in-package**: fold — make it an injected bean (IC-06, FR-008-adjacent).
- **MINOR — point lookups fan out to full-pack reads**: fold — descriptor-only identity resolution (IC-03/IC-06, NFR-001).
- **MINOR — `STORAGE_RETURNS_DOMAIN` gates return types not parameters**: fold — add the parameter half to the gate (IC-06).
- **NOTE — `PackDescriptor` wide positional ctor**: descriptor is out of this mission's read scope (C-002); carry as a follow-up, do not grow it.
- **NOTE — write-side `RefusalCode.PACK_UNREADABLE`→500**: not HTTP-reachable (C-006, read-only surface); with upsert, unknown-id is now a create not a refusal — revisit when the write endpoint is modelled.
- **Correctness/fail-closed lens: CLEAN** — path/symlink escape fail-closed, atomic write proven, guards unbypassable. **Preserve** these on the upsert create arm (atomic, pack-root-contained).
- **Purity gate: genuinely double-gated** — **preserve** both the build-time rejection and the ArchUnit purity rules (NFR-003); only the *application* purity rule changes.

No contested finding is silently dropped (adversarial-evidence contract).

## Dependency / supply-chain posture

**No dependency delta.** The mission reuses Spring Boot, SnakeYAML, networknt json-schema, jspecify, ArchUnit, JUnit 5, AssertJ — all already on the classpath. `DIRECTIVE_051` / `supply-chain-install-safety` has no new install to vet. If implementation surfaces a new dependency, run the install-safety controls before adding it.

## Open follow-ups (explicitly out of scope)

- Read models for pack **descriptor**, **DRG**, and **offering/listing** + the **action-context** capability — a follow-up mission once ruled.
- Write **endpoints** + write-specific refusal codes — deferred (C-006).
- `PackDescriptor` builder-convention cleanup — follow-up (descriptor out of scope).
