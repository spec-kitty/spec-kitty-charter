# Research: Closure symmetry

## What #554 already delivered (checked at 75d5e30)

- Validate mode (`PackCheck`) reads through `FileSystemPackInspector`, whose verdict is
  `StorageRulesets.verdict`: the read faults, `registration()` (= `holding()` + `PackRulesets.content()`), the store
  half's own faults (`AssemblyRules.held`, the assembly's store-only list), then the manifest gate.
- The assembly (`PackAssembly.assembly`) hands the store-only list every value-object breach and every closure fault
  it records. So validate mode already states a guard, lifecycle or mission type that does not close, and the
  `Executable` and `Lifecycle` invariant breaches, as `artifact_schema_error` at the file, in the load's words
  (`StoreHalfFaultsTest.aReferenceThatDoesNotCloseIsStated`).
- What is still missing: the rule lives in `packs.infra.storage` (`GuardClosure`, `MissionTemplates`), outside the PACK
  ruleset; validate mode states it as a store-only fault, not a rule finding; the per-file write path (commit and
  remove) never judges it; and no standing test holds the four paths together.

## Decisions

### D1. The rule's home: `packs.domain.rules.ReferenceClosure`, a member of `content()` through `ReferenceRules`

- **Decision**: one pure class decides the closure over a subject (candidate assets, guards, lifecycles, mission types;
  payload paths; namable template ids) and a `ConfigSchemas` port. `ReferenceRules.RULES` lifts it onto
  `PackContents` through a new fact, `ReferenceFacts`, which the store half fills from the candidates it built.
- **Rationale**: #355's ruling "one rule, one place" names #554's PACK ruleset. A content rule over the read's facts is
  what every surface runs by name.
- **Alternatives**: a `ReferenceClosure` beside the ruleset, called by each path (rejected by #355's re-scope); judging the
  assembled pack (misses exactly the artifacts the load leaves out).

### D2. The code stays `artifact_schema_error`, moved to `PackCodes`

- **Decision**: `ARTIFACT_SCHEMA_ERROR` moves from `StorageCodes` to `PackCodes` (one home, no alias). Every reader,
  binding and the assembly name it there.
- **Rationale**: a domain rule cannot name an infra code, and `StorageCodesTest` keeps the two vocabularies disjoint. The
  load must stay unchanged (FR-002), and validate mode must name what the load records (FR-003).
- **Alternatives**: a new `reference` code (changes the load's code and the whole-pack registration's wire answer);
  an alias in `StorageCodes` (two names for one code; C-001).

### D3. Schema compilation is a port

- **Decision**: `packs.domain.ConfigSchemas` (`schemaProblems`, `valueProblems`); `EmbeddedSchemas` implements it as a
  singleton. The rule is given it through `ReferenceFacts` (validate, registration) or as an argument (load, writes).
- **Rationale**: #355 ("`EmbeddedSchemas` stays an infra port that the rule is given"); ADR-0006.

### D4. The per-file judge sits at the commit store's choke point

- **Decision**: `CommitStorePackWriteStore.commit` judges `change.revalidated()`; `.remove` judges the pack after the
  removal, which `Removal` now carries (`ArtifactRemovalService` builds it with `Pack.without`). A finding refuses with
  `REGISTRATION_CONFLICT` and the load's reason, under the registry lock and `PackWriteLocks`, before the impact check
  and before anything is staged.
- **Rationale**: every per-file write goes through these two methods ("the one choke point", IC-15). Item 13's
  registrations inherit the check without opting in.
- **Alternatives**: in each registration service (a new service could leave it out).

### D5. Domain invariants on the write paths

- **Decision**: the symmetry table's invariant rows assert, on the two write paths, that the domain value cannot be
  built (`ConstraintBreachException`), so no write can carry it.
- **Rationale**: the aggregate holds only built values; there is nothing for a write path to judge.

### D6. #593

- **Decision** (as amended by plan review finding 2): each write records the time from the barrier's release to the end
  of its call, held to 4 s, under the rig's 5 s registry lock; a breach fails as
  `write P7 waited 4.2 s in round 63 (bound 4.0 s)`. A write the lock gave up on, or one still running at the round's
  15 s deadline, is named with its round. The method's `@Timeout(30)` becomes `@Timeout(120)`, the hang guard only.
- **Rationale**: the issue's proposal. A round's 17 writes take a few hundred milliseconds together on a loaded runner,
  so a write that waits 4 s waited behind something, not behind a slow runner.
- **Alternatives**: raising the 30 s timeout alone (rejected by #593).

## Plan review

One independent reviewer read the plan against the code (charter: an adversarial squad at the plan point). Logged as
an open review item: a full squad was not run. Its eleven findings, and what the implementation did with each:

| # | Severity | Finding | Disposition |
|---|----------|---------|-------------|
| 1 | CRITICAL | No `DELETE` reaches an asset: `ArtifactRemovalService.REMOVABLE` has no asset, guard or lifecycle (404 first) | Accepted. The delete row is proven at the store's `remove`, the choke point every removal goes through; the PR says so. Opening asset removal needs a payload removal rule and is out of scope. |
| 2 | CRITICAL | #593's 10 s bound can never fire: the rig's registry lock gives up at 5 s; cancellations are unnamed | Accepted. Bound 4 s, under the lock; a `RegistryBusyException` and a cancelled write are named with the round; a second case starves one round on purpose. |
| 3 | HIGH | `WholePackStore.write` is not what `PUT /packs` calls | Accepted. The table asserts registration's verdict (the read's rationale, which `PackRegistrationService` refuses whenever it fails) as its own column, and keeps the whole-pack write. |
| 4 | HIGH | Moving the closure into `content()` reorders `PUT /packs` faults (effects before causes) | Accepted. `StorageRulesets.verdict` states the closure findings among the store's own faults, in the order the load records them (`inLoadOrder`). |
| 5 | HIGH | `Removal.after` supplied by the caller can be left out; first-by-id edge; 422 before 409 on removal | Accepted the first: the store reads the pack itself and takes the removed artifacts out (`Pack.without`). The first-by-id edge (two files of one guard id) is recorded as a known limit: a repeated id is already a `duplicate_artifact_id` fault. On a removal the impact check (422) runs before the store's judge (409); recorded. |
| 6 | MEDIUM | Facts must follow the shaped read and the refusal; avoid the 17 `PackContents` call sites | Accepted. `ReferenceFacts` lives on `Assembly.Assembled`, built from the shaped read; `Refused` and `None` carry none. |
| 7 | MEDIUM | A port instance inside a domain fact record | Kept, with reason: the rule stays a static member of `content()` by name, which #355 asks for; the port is a stateless capability, and `ReferenceFacts` is never compared. |
| 8 | MEDIUM | The `guard-lifecycle` fixture is not visible to `:pack-validation` | Accepted. The table runs over the built-in example pack from `:packs`' test fixtures. |
| 9 | MEDIUM | The code move's knock-ons, and a name clash with the pack-set `CLOSURE` | Accepted. The code is in `PackCodes.CONTENT`, gone from `StorageCodes`; the classes are named `ReferenceClosure`, `ReferenceRules`, `ReferenceFacts`. |
| 10 | LOW | The mission-type wording differs between the write paths | Accepted. Both write paths answer `Finding.refusal()`, which names the type. |
| 11 | LOW | The judge recompiles every schema on every directive write | Accepted. The commit judge runs only for a write or removal of an asset, guard, lifecycle, mission type or template. |

