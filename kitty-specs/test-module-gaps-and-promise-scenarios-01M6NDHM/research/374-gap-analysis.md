# #374 Phase 0: coverage-gap analysis of the 27 promise-first scenarios

Mission `test-module-gaps-and-promise-scenarios-01M6NDHM`, branch `test-module-gaps-and-promise-scenarios`.
Researcher: `researcher-robbie`. This was a read-only analysis: no Gradle run, no commits. Every claim cites a file and line.

## Method: `qa-coverage-gap-analysis` (packs/qa/procedures/qa-coverage-gap-analysis.procedure.yaml)

`spec-kitty charter context --include procedure:...` printed only pydantic warnings about an unrelated org procedure. I
read the procedure file directly and applied it like this:

| Step | Applied how |
|---|---|
| 1. List the coverage items the test basis requires | The test basis is the 27 `@promise` scenarios: 18 in `src/test/resources/features/` and 9 in `tests/scenarios/charter/`. Each promise is one coverage item. I also used the READMEs, `PROMISES.md`, the decisions ledger and the ADRs. |
| 2. Map each item to the tests that exercise it | I compared each item with the executed suite and with unit tests. The executed suite is `CharterContractSuite` (`service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/CharterContractSuite.java:18-25`: classpath `scenarios`, `@IncludeTags("contract")`, `@ExcludeTags("WIP")`). It runs `contracts/charter/scenarios/**`, which `service/deployable/build.gradle.kts:80-82` copies onto the test classpath. I also searched the main code for the behaviour each promise describes. |
| 3. Record every item with no exercising test as a candidate gap | Every item that is not fully mapped is recorded below with a verdict: MAP-PARTIAL, WIRE, RETIRE or GAP. None is left out. |
| 4. Schedule each gap for new tests or accept it as a documented risk (operator step) | **Not done here; this step is the operator's.** Each GAP row and each partial remainder has a recommendation for the operator to rule on (spec FR-006). |
| 5. Do not close while any gap is undecided | The analysis stays open until the operator rules on the two GAP rows and the planned-later cluster (see "Operator decisions needed"). |

Facts used throughout:

- **No runner executes these files.** Neither home is on any classpath. The root project has no source set: `settings.gradle.kts` includes only `service/*` and `contracts`. Only `contracts/charter/scenarios/**` is copied (`service/deployable/build.gradle.kts:80-82`).
- **The pack registration and distribution surface is decided but not built.**
  - The contract's paths are `openapi.yaml:47-95`. None of them is `POST /packs`, `/packs/{id}/distributions` or `/packs/{id}/resolution`.
  - `docs/api/service-contract.md:200,214-216` lists those three operations under "Decided, not built", with no inventory issue.
  - They are not in the cutover scope of `docs/adr/0055-cutover-sequencing-the-java-service-first.md:62-67`, D1: layer merge, activation, the org and project layers, effective reads and the activation register.
  - ADR-0005 still calls registration "a day-one requirement" (`docs/adr/0005-service-registers-and-distributes-packs.md:46`).
  - None of the refusal codes the scenarios use (`SOURCE_CONFLICT`, `SOURCE_NOT_ALLOWLISTED`, `INTEGRITY_FAILURE`, `PACK_ENGINE_INCOMPATIBLE`, `SOURCE_UNREACHABLE`) is in `contracts/charter/schemas/CharterRefusalCode.yaml`, whose codes are snake_case (`:125-268`).
- **GraphQL is planned-later.** No scenario covers it (`src/test/resources/features/README.md:12`; `docs/adr/0007-rest-hateoas-contract-of-record.md:71-75`), so it affects no row.
- **The `tests/scenarios/charter` Acts drive core's Python CLI** (`doctor charter-packs`, `charter synthesize`; `tests/scenarios/charter/PROMISES.md:43-47`). Since ADR-0055 D3, core's CLI is no longer the oracle (`docs/plans/triage-2026-11-02-rescopes.md:879-891`). Binding these Acts as written would need a second runner against core's Python surface, which C-004 rules out. So a WIRE verdict here means restating the promise in the contract vocabulary (`contracts/charter/scenarios/README.md`, "Step vocabulary"), not binding the CLI steps.
- **The service never reads the active charter.** It digests `config.yaml` and `charter.yaml` "and never reads them" (`contracts/charter/scenarios/README.md:53`). The activation set is reserved: there are 501 pins in `contracts/charter/scenarios/activations.feature:11-100`, and `activation_invalid` is a declaration pin (`CharterRefusalCode.yaml:45-46`).

How I used RETIRE here: the behaviour was decided against or superseded, **or** it belongs to a decided surface that is not built and not scheduled. For the second case I name the decision record and the placeholder, if the contract has one (a reserved pin or a `@WIP` scenario).

## Verdict table: `src/test/resources/features/` (18)

| # | Scenario (file:line) | Promise (one line) | Verdict | Evidence |
|---|---|---|---|---|
| 1 | `registration/register-pack.feature:29` "Registering a pack from each supported source kind pins and records it" (outline, 4 kinds) | Each of git, filesystem, service-stored and published-artifact is pinned to a revision and gets a lockfile entry | **RETIRE** (planned-later) | ADR-0005 d1/d3 (`0005...md:63-83`), DM-7/DM-11/DM-17 (`decisions-ledger.md:22,26,46`). `POST /packs` is not built (`service-contract.md:214`). No source fetcher or lockfile exists in main code: `snapshot`/`lockfile` occur nowhere in `service/*/src/main`. Placeholder: `journeys/journey-06-publish-a-pack.feature` `@WIP` "A published pack version can be registered by others". |
| 2 | `registration/register-pack.feature:43` "A git source given as a moving branch is pinned to the commit it points at" | A branch is resolved to a commit; the lockfile never holds the branch name | **RETIRE** (planned-later) | ADR-0005 d8, "a rev, never a floating ref" (`0005...md:101`). Not built, as for row 1. |
| 3 | `registration/register-pack.feature:49` "Two sources claiming the same pack id are refused, not last-write-wins" | A second pack claiming an existing pack id is refused, and the first stays | **MAP-PARTIAL** | **Covered:** `contracts/charter/scenarios/composition.feature:541` "Two configured packs declaring one pack_id refuse the compose" (500 `pack_duplicate_id`, both packs named), with its control at `:572`. **Not covered:** the refusal at registration time, with `SOURCE_CONFLICT`, while the original keeps resolving. Today the whole compose is refused instead. That remainder is planned-later, as for row 1. |
| 4 | `registration/register-pack.feature:56` "A pack whose constituent slug collides with one already registered is refused" | One artifact id contributed by two packs is refused, never silently overwritten | **MAP-PARTIAL** (part superseded) | **Covered:** `composition.feature:731` "Two siblings defining one artifact, unsettled, refuse the compose" (500 `pack_sibling_conflict`). **Superseded:** DM-15's global namespace (`decisions-ledger.md:44`). Across levels the same id is now a legitimate layer override (`docs/adr/0053...md:76-78`, d6 as amended), pinned by `composition.feature:778`. An `overrides` edge can also settle a same-level contest (`:662`). **Not covered:** refusal at registration; planned-later. |
| 5 | `integrity/pin-and-verify.feature:34` "A pack whose content matches the lockfile resolves, both tiers verified" | Distribution verifies the snapshot digest and every constituent hash, and the projection reports both verified | **RETIRE** (planned-later) | ADR-0005 d3, ADR-0007 d7 (`0007...md:89-93`). `distributions` and `resolution` are not built (`service-contract.md:215-216`). The constituent check exists only as a library function, `PackManifests.stale` (`service/packs/.../infra/storage/PackManifests.java:100-135`), called only from tests (`PackManifestRoundTripTest.java:64`, `WholePackRoundTripTest.java:723`). There is no snapshot or lockfile. |
| 6 | `integrity/pin-and-verify.feature:42` "A constituent altered after pinning fails closed" | A tampered constituent gives `INTEGRITY_FAILURE` naming the file, and nothing is handed on | **RETIRE** (planned-later) | As row 5. `PackManifests.stale` would name `constituents{<path>}.content_hash` (`PackManifests.java:120`), but no operation calls it. |
| 7 | `integrity/pin-and-verify.feature:51` "A materialized tree that does not match the pinned snapshot fails closed" | A snapshot-digest mismatch fails closed and names both digests | **RETIRE** (planned-later) | As row 5; no snapshot digest exists in the service. |
| 8 | `trust/source-trust.feature:26` "A git source whose host is not on the allowlist is refused" | An unlisted git host gives `SOURCE_NOT_ALLOWLISTED`, with nothing fetched | **RETIRE** (planned-later) | ADR-0005 d8 (`0005...md:100-117`), DM-10/18. The nearest built behaviour is the **guide-fetch** allowlist (`toolguides.feature:184`, `HOST_NOT_ALLOWED`; `ToolguideRegistration.yaml:76-77`). That is a different surface, a toolguide `guideUri` rather than a pack source, so it does not cover this promise. |
| 9 | `trust/source-trust.feature:33` "An allowlisted git source proceeds to registration" | A listed source is accepted and pinned before anything is materialized | **RETIRE** (planned-later) | As row 8. |
| 10 | `trust/source-trust.feature:40` "Built-in, as a high-trust tier, is not gated behind the operator allowlist" | Built-in bypasses the allowlist but is still pinned and recorded | **RETIRE** (planned-later) | ADR-0005 d8, "Trust tiers" (`0005...md:119-121`). Not built. Note: ADR-0048 has core pin and fetch `built-in` itself (`docs/adr/0048...md:195-197`). |
| 11 | `trust/source-trust.feature:47` "A git source that cannot be pinned to a revision is refused on the trust floor" | A floating ref is refused on the pin-to-rev floor | **RETIRE** (planned-later) | ADR-0005 d8. Not built. |
| 12 | `trust/source-trust.feature:54` "An exact-repo allowlist entry admits only that repository, not its whole host" | An exact-repo entry admits only that repository | **RETIRE** (planned-later) | DM-18 (`decisions-ledger.md:47`), ADR-0005 d8. Not built. |
| 13 | `distribution/resolve-and-distribute.feature:32` "A registered pack is distributed to a consumer through the one canonical path" (outline, 4 tiers) | Every tier is distributed through one resolve-and-verify path, reporting the pinned revision | **RETIRE** (planned-later; tier vocabulary superseded) | ADR-0005 d2. `distributePack` is not built (`service-contract.md:216`). The tiers `consumer`/`private` are not pack levels: levels are built-in/org/project and no level was added (`0053...md:64-65`, d1). A requester tier taken from the caller's identity has no basis yet: the service has one worktree token (`security.feature`), and hosted login is `@WIP` (`journey-10-host-centrally.feature:19-20`). |
| 14 | `distribution/resolve-and-distribute.feature:46` "Built-in resolves through the same registration and distribution path as any other pack" | Built-in has no shortcut and is the first catfooding consumer | **MAP-PARTIAL** | **Covered, read side:** built-in packs load through the same configured-pack compose as org and project packs (`offering.feature:13`, ordering across levels with built-in packs at `:48,:56`; `composition.feature:76` "The single built-in tree still loads"). **Not covered:** registration from a pinned source and distribution; planned-later. ADR-0048's consequence that core fetches `built-in` itself (`0048...md:195-197`) is in tension with ADR-0005 d2/d7. Operator note. |
| 15 | `distribution/resolve-and-distribute.feature:53` "A pack whose required core range excludes the engine is refused at resolve time" | A `requires_core` range that excludes the engine gives `PACK_ENGINE_INCOMPATIBLE` before any merge | **RETIRE** (planned-later) | ADR-0005 d9 (`0005...md:122-130`). The service only checks the specifier's syntax (`REQUIRES_CORE_INVALID`, `service/packs/.../domain/PackFindingCode.java:13-14`; `PackPolicy.java:252-254`). It never compares the range with an engine version. |
| 16 | `distribution/resolve-and-distribute.feature:63` "A previously resolved pack resolves from cache with no network" | A cached pack is distributed with no network, against the same pins | **RETIRE** (planned-later) | ADR-0005 d8 "offline cache". Not built; configured packs are local by construction. |
| 17 | `distribution/resolve-and-distribute.feature:71` "An uncached pack whose source is unreachable fails closed and legibly" | An unreachable, uncached source gives `SOURCE_UNREACHABLE` naming the source | **RETIRE** (planned-later) | ADR-0005 d8, ADR-0007 d7. Not built. |
| 18 | `distribution/resolve-and-distribute.feature:79` "A private pack is visible only within its owning tenant boundary" (already `@deferred`) | A private pack is not disclosed outside its tenant | **RETIRE** (deferred) | Deferred: DM `01M5781ER9V60RN0KVBJH0EQRM` (`decisions-ledger.md:34-36`; ADR-0005 Risks `0005...md:170-173`). The scenario says so itself (`:80-83`). |

## Verdict table: `tests/scenarios/charter/` (9)

| # | Scenario (file:line) | Promise | Verdict | Evidence |
|---|---|---|---|---|
| 19 | `governance-in-force/governance-in-force.feature:24` "Sharing a pack does not silently add its commands to every tool" (GOVERNANCE-001) | A skill is in force only when the charter requires or selects it | **RETIRE** (planned-later; authority rewritten) | The domain fact exists: `SKILL ... InForceWhenAbsent.REQUIRED` (`service/kernel/.../ArtifactKind.java:29-30`). The effective set is the reserved activation surface: `activations.feature:79-80` "Reading the effective artifacts of an activation is reserved" (ADR-0020 D8). The rule is rewritten by ADR-0053 d10, "only what the charter activates is in force" (`0053...md:91-98`). Placeholder: `composition.feature:819-821` `@WIP` "The compose is the closure of the activated packs" (#360). |
| 20 | `governance-in-force/governance-in-force.feature:31` "A team's org-wide requirement is honored on top of the project's own choices" (GOVERNANCE-002) | An org's required rule is in force on top of the project's selection | **RETIRE** (superseded authority; planned-later) | PROMISES cites the org `required_<plural>` overlay (`PROMISES.md:18`). ADR-0031 replaces it: requirement is authored as a preset (`docs/adr/0031...md:76-80`; `docs/architecture/descriptors.md:192-194`). Placeholder: `journeys/journey-04-org-house-rules.feature:32-36` `@WIP` "A project of the organisation gets the required preset". |
| 21 | `refuses-unsound-governance/refuses-unsound-governance.feature:29` "Governance that would silently weaken a profile is refused, not quietly accepted" (REFUSAL-001) | An `enhances` merge that drops part of a profile is refused | **GAP** (product) | The service does not resolve `enhances`: "resolving enhances, overrides or excluding() is not done here (ADR-0032)" (`service/profiles/.../domain/AgentProfile.java:23-24`). No contract scenario or `@WIP` placeholder states this refusal. ADR-0053 d22 makes **step** enhancement add-only (`0053...md:133-137`), so for steps a drop cannot happen by construction. That leaves profiles. The nearest covered behaviour is an unsettled sibling `enhances` contest refused as `pack_sibling_conflict` (`composition.feature:597`), which is a different promise. |
| 22 | `refuses-unsound-governance/refuses-unsound-governance.feature:35` "A mistyped reference between pieces of governance is named, not dropped" (REFUSAL-002) | A reference to a name that does not exist is refused and named | **MAP-PARTIAL** (remainder decided against) | **Covered:** `composition.feature:1015`, a qualified reference to an artifact the pack does not hold, refused as `pack_reference_artifact_missing` naming both packs. Also `:858` (bare cross-pack reference, `pack_reference_unqualified`), `:913` (pack outside the closure), `:1063` outline (the relationship write is refused 422 for all three), and `relationships.feature:54` (a `from` the pack does not hold, `registration_conflict`). **Not covered, by decision:** a bare `to` that no pack holds is accepted as external (`composition.feature:1129-1133` answers 201), per ADR-0026 d1.3, "`to` may be external" (`0026...md:76-80`). `unresolved_edge_endpoint`, which PROMISES cites, is raised by no operation (`errors.feature:187-188`). |
| 23 | `refuses-unsound-governance/refuses-unsound-governance.feature:41` "A broken charter fails closed instead of running half-applied" (REFUSAL-003) | A broken charter is refused rather than applied in part | **RETIRE** (planned-later) | The service never reads `charter.yaml` (`contracts/charter/scenarios/README.md:53`). The active charter is local (ADR-0033). Activation-document refusal is the reserved `activation_invalid` (`CharterRefusalCode.yaml:45-46,112`) plus the 501 pins in `activations.feature`. Nearest analogue: `errors.feature:54-66`, an unreadable project config fails closed with `pack_unreadable` on I/O only. |
| 24 | `share-governance-safely/share-governance-safely.feature:30` "A team pack carrying an unsound profile is not reported healthy" (SHARING-001) | A team pack holding an invalid profile is not reported healthy | **WIRE** (restate as `@contract`, 0 new steps) | **Behaviour exists:** every kind read on the load path is schema-validated (`YamlSerializationStrategy.java:32,59-69` gives `Unreadable`), and the offering fails closed on any unreadable file of a configured pack (`OfferingReadService.java:34`; ruling 2026-11-04 at `offering.feature:219-222`). Validate mode reports `artifact_schema_error` (`PackValidationTest.java:105`; planted `c10-agent-role-uppercase/expected.json`). **Missing:** a contract scenario with an invalid **profile**. `offering.feature:219` uses a missing asset payload instead. Steps that already exist: org pack file, `When I GET "/offering"`, status, problem code, `field "detail" ... starts with`. The `doctor charter-packs` Act has no service equivalent (#115 was closed as not planned; a validate entry point is #149, `triage-2026-11-02-rescopes.md:245-258`), so "not reported healthy" becomes the offering's 500 `pack_unreadable`. **Run it red-first to pin the detail.** |
| 25 | `share-governance-safely/share-governance-safely.feature:37` "A project's own governance wins over the team pack it adopts" (SHARING-002, already `@wip`) | The project's version of a rule is the one in force over the org pack's | **MAP-PARTIAL** | **Covered:** `composition.feature:778` "An org pack and a project pack defining one artifact compose as a layer override". It is not refused and both are served, with org first. **Not covered:** that the project's version is the effective one. This needs the layer merge and the effective reads, which are in cutover scope (ADR-0055 D1 `:62-67`, D2 `:82-93`) but not built (activations reserved). The ADR-0012 lock qualifier is placed at `journey-04-org-house-rules.feature:38-43` `@WIP`. |
| 26 | `share-governance-safely/share-governance-safely.feature:44` "A shared skill under a reserved name is not accepted as sound" (SHARING-003) | A skill under `spk-`, `spec-kitty-` or `spec-kitty.` is refused | **GAP** (product, or retire as core-owned) | The rule is documented: `docs/architecture/descriptors.md:190` reserves those prefixes for `skill_namespace`. It is not enforced. `contracts/packs/schemas/org-charter.schema.json:201-211` puts no pattern on `skill_namespace`. `skill.schema.json:19-23` accepts an id such as `spk-review-gate`. No main code checks the prefixes (searching for `spk-` and `spec-kitty.` in `service/*/src/main` finds only `GlossaryPackIdentifier.java:8` and `PackSchemas.java:56`). The rule is about **rendered** skills, which core projects (core ADR 2026-09-27-1), so the operator may rule it core-owned. |
| 27 | `share-governance-safely/share-governance-safely.feature:50` "A shared asset that reaches outside its pack is not accepted as sound" (SHARING-004) | An asset whose path leaves its pack is refused | **WIRE** (restate as `@contract`, 0 new steps) | **Behaviour exists:** on the load path, a sidecar `path` must name a payload under `assets/`; any other path "names none" and is refused (`SidecarPayloads.java:5-8,47-49`), so the offering gives 500 `pack_unreadable` (as at `offering.feature:219-248`). In validate mode, `ASSET_PATH_ESCAPE` (`PackFindingCode.java:31-32`) is unit-tested (`PackPolicyRulesTest.java:557-642`; `FileSystemPackInspectorTest.java:671-684`). **Missing:** a contract scenario with an escaping path such as `../outside.md`. Same existing steps as row 24. **Run it red-first to pin the detail.** |

## Summary counts

| Verdict | Count | Rows |
|---|---:|---|
| MAP (full) | 0 | none |
| MAP-PARTIAL | 5 | 3, 4, 14, 22, 25 |
| WIRE | 2 | 24, 27 |
| RETIRE | 18 | 1, 2, 5-13, 15-18 (15 rows from `src/test/resources/features`); 19, 20, 23 |
| GAP | 2 | 21, 26 |
| **Total** | **27** | |

Of the 18 RETIRE rows:

- 1 is deferred by decision (row 18).
- 1 has a superseded authority (row 20).
- 16 are planned-later: 14 on the not-built, unscheduled ADR-0005 registration/distribution surface, plus rows 19 and 23 on the reserved activation surface.

The partial remainders of rows 3, 4 and 14 also sit on that ADR-0005 surface.

## GAP table (for the operator's ruling)

| Row | Promise | Kind | Evidence | Recommendation |
|---|---|---|---|---|
| 21 | REFUSAL-001: an `enhances` merge that silently drops part of an agent profile is refused | Product gap: the service does not implement it | `AgentProfile.java:23-24`. No contract scenario or `@WIP`. ADR-0053 d22 closes it for steps only. | Follow-up issue: **"Refuse an agent-profile `enhances` merge that drops fields (effective profile resolution, ADR-0032/ADR-0053 d22)"**. Add a `@WIP` placeholder in `contracts/charter/scenarios/` beside the roles-and-profiles surface (README `@WIP` list). Not small: it needs the effective-profile merge. |
| 26 | SHARING-003: a shared skill or namespace under a reserved prefix is refused | Product gap, or retire if the operator rules it core-owned | Rule at `descriptors.md:190`. No pattern at `org-charter.schema.json:201-211`. No code. | Operator ruling. **If service-owned:** follow-up issue **"Refuse reserved skill namespaces (`spk-`, `spec-kitty-`, `spec-kitty.`) in org-charter `skill_namespace`"**. The schemas are imported as canonical (ADR-0017), so the pattern belongs in core's schema with a re-import, plus one `@contract` offering scenario; S/M. **If core-owned:** RETIRE, pointing to core ADR 2026-09-27-1. |

These test gaps are in scope here and small, but they are **WIRE** rows, not gaps:

- **Row 24:** one `@contract` scenario in `offering.feature`, about 25 lines of Gherkin, 0 new steps. Size S.
- **Row 27:** the same shape, 0 new steps. Size S.

Each needs a `contracts/charter/CHANGELOG.md` line (`contracts/charter/scenarios/README.md`, Rules).

## Operator decisions needed (procedure step 4)

1. **The ADR-0005 cluster:** rows 1, 2, 5-13, 15-17, and the remainders of 3, 4 and 14. The behaviour is decided ("day-one requirement", `0005...md:46`) but not built, has no tracking issue (`service-contract.md:214-216`, inventory "—") and is outside the cutover scope (ADR-0055 D1). I classified it RETIRE (planned-later). If the operator rules it a current product gap instead, one follow-up issue covers all of it: **"Build pack registration and distribution (POST /packs, distributions, resolution; ADR-0005/0019)"**. When it is built:
   - The scenarios move to `contracts/charter/scenarios/<cluster>/` (`docs/plans/open-questions.md:47`; `docs/architecture/behavior-and-scenarios.md:52`).
   - The refusal codes are renamed to snake_case contract codes.
   - REGISTER-004 is narrowed to same-level collisions (ADR-0053 d6).
   - DISTRIBUTE-001's tiers are restated as levels plus a later identity model.

   An option that touches no copy rule: move the files now as `@WIP`. The suite already excludes `@WIP` (`CharterContractSuite.java:22`), so they get a contract home without running.
2. **The ADR-0048 / ADR-0005 tension** on how built-in is obtained: core fetches it (`0048...md:195-197`), against ADR-0005 d2/d7. This affects rows 10 and 14.
3. **GAP rows 21 and 26**, above.

## What the READMEs should say once the verdicts are applied

**`src/test/resources/features/README.md`**. Replace the Status section ("Layer B — the harness (not yet wired)") with:

- These scenarios are not executed, by decision (#374). They specify the pack registration and distribution surface of ADR-0005/ADR-0019, which is decided but not built (`docs/api/service-contract.md`, "Decided, not built") and outside the cutover scope (ADR-0055 D1).
- What already holds for configured packs is covered by contract scenarios:
  - REGISTER-003 by `composition.feature` "Two configured packs declaring one pack_id refuse the compose";
  - REGISTER-004, same level, by "Two siblings defining one artifact, unsettled, refuse the compose". Across levels it is a layer override (ADR-0053 d6);
  - DISTRIBUTE-002's no-shortcut read side by `offering.feature` ordering and "The single built-in tree still loads".
- DISTRIBUTE-006 is deferred (DM `01M5781ER9V60RN0KVBJH0EQRM`).
- When the surface is scheduled, each cluster moves to `contracts/charter/scenarios/<cluster>/` (open question 16), its codes take the contract's snake_case names, and its tiers become levels.
- Keep the Fixtures section, and note that its hashes stay illustrative.

**`tests/scenarios/charter/README.md`** and **`PROMISES.md`**. Follow the re-scope of #31 ("reduce to a pointer once every promise is mapped", `triage-2026-11-02-rescopes.md:879-886`). Drop the Layer B "not yet wired" text and the core-path authority note, and state, per promise:

- **SHARING-001 and SHARING-004:** now `@contract` scenarios in `contracts/charter/scenarios/offering.feature`, run by `CharterContractSuite`.
- **REFUSAL-002:** covered by the `composition.feature` reference-refusal scenarios. A bare unknown target is external by ADR-0026.
- **SHARING-002:** partly covered by `composition.feature` "...compose as a layer override". The "project wins" read waits on the layer merge and effective reads (ADR-0055 D1).
- **GOVERNANCE-001, GOVERNANCE-002 and REFUSAL-003:** retired to the activation surface. Their placeholders are `activations.feature` (reserved), `composition.feature` `@WIP` (#360) and `journey-04` `@WIP`, under the rules of ADR-0053 d10 and ADR-0031.
- **REFUSAL-001 and SHARING-003:** tracked as follow-up issues (numbers to be filled in).

The `.feature` files are then removed, or kept only as history. Update `docs/architecture/behavior-and-scenarios.md:52-53`, the "Where they live" table, to match.
