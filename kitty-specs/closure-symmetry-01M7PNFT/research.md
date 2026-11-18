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

### D1. The rule's home: `packs.domain.rules.PackClosure`, a member of `content()` through `ClosureRules`

- **Decision**: one pure class decides the closure over a subject (candidate assets, guards, lifecycles, mission types;
  payload paths; namable template ids) and a `ConfigSchemas` port. `ClosureRules.RULES` lifts it onto
  `PackContents` through a new fact, `ClosureFacts`, which the store half fills from the candidates it built.
- **Rationale**: #355's ruling "one rule, one place" names #554's PACK ruleset. A content rule over the read's facts is
  what every surface runs by name.
- **Alternatives**: a `PackClosure` beside the ruleset, called by each path (rejected by #355's re-scope); judging the
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
  singleton. The rule is given it through `ClosureFacts` (validate, registration) or as an argument (load, writes).
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

- **Decision**: each write records the time from the barrier's release to the end of its call. After each round, the
  slowest write is compared to a 10 s bound; a breach fails as `write P7 waited 12.3 s in round 63 (bound 10 s)`. The
  class `@Timeout(30)` on that case becomes `@Timeout(120)`, documented as the hang guard only.
- **Rationale**: the issue's proposal. 10 s per write is about 50 times the slowest write a quiet runner shows (17 writes
  in a round, each a few ms to a few hundred ms), so a breach is starvation, not a slow runner.
- **Alternatives**: raising the 30 s timeout alone (rejected by #593).

## Plan review

One independent reviewer read the plan against the code (charter: an adversarial squad at the plan point). Its findings
and their dispositions are recorded below when it returns. Logged as an open review item: a full squad was not run.
