# R3 design: where the pack rules live (2026-11-16)

## Question and method

The operator, on R3: "unsure. I was thinking we would leave much of the business logic rules (and validation)
inside the packs domain. Dispatch an architecture squad to come with a coherent design."

Three design lenses worked independently from one brief: domain model, modules and hexagonal architecture, and
consumers and migration. All three reached the same placement. This note merges them. Where they split, the
choice is marked **[chosen]** with its reason. A challenge pass follows (§7).

## New fact: #555 and slice G merged on 2026-11-15

`main` is now `97985727a`, and this branch is rebased onto it locally. PR #578 (#555):

- added `testImplementation(project(":pack-validation"))` to `:packs`, as the round-trip oracle;
- moved the whole-pack gate and the drift test to `:pack-validation` (`wholePackRoundTrip`, `driftTest`);
- grew `pack-validation` from 1 mutant to the round-trip verdict. #578 reports about 350 mutants, 88% killed against
  floor 85. The build file still says "margin 0 until #554", so the figure is measured again here.

## 1. Placement

| Element | Home | Why |
| --- | --- | --- |
| `PackCodes`: kernel `ReasonCode` constants with token, severity and wording | `packs.domain` | Every surface names them. R2(b) needs one table, and ruling 4 makes each code own its severity. |
| Per-kind rules (`Rule`s over the smallest domain fact: descriptor, graph, mission type, step, artifact, asset, fragment, prompt slot, glossary, manifest, reserved shard, not held) | `packs.domain.rules` (new), carved from `PackPolicy`, `StepEnhancementRules` and `RelationshipEndpointRule` | The load, the compose read, the resolver and the offering call these rules today, as static calls inside `:packs`. Keeping them there needs no port and no no-op judge (A-M2, M-1). |
| `PACK` with a surface profile (`VALIDATE`, `REGISTER`, `COMPOSE_READ`) | `packs.domain.rules.PackRulesets` | R1(a). A profile chooses which rules are composed, and no rule carries a flag. The manifest gate is a plain sequence (A-D1). |
| `CLOSURE`: collect-all composition checks over `PackUnderResolution`, as functions plus a ruleset | `packs.domain` **[chosen]** | The A-M4 fold. The offering takes the first refusal in today's interleaved order, with its `RefusalPack` list. The ruleset form serves pack-check. One lens put the ruleset in `pack-validation`; it is a thin wrap, and keeping it beside its functions avoids two homes. |
| Mappers `PackVerdicts` and `PackRefusals` | `packs.domain` | They shape and order findings only. Under R2(b) the wording comes from `PackCodes`. |
| `PackPolicy` residue (`TENSION`, `joinsItself`, `bodyPathEscape`, `assetPathEscape`, `registeredGraph`) and the descriptor function | `packs.domain` | The A-M5 fold. |
| Spring-free assembly and manifest derivation | `packs.domain` (`PackAssembly`, `PackManifest`) | The A-B3 fold. `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` stays green. |
| Inspector facts (P1, the A-M1 checklist) | `PackContents`, filled by `FileSystemPackInspector` | Ruling 1 and P1. |
| Judging port `PackRules` | **Dropped** | Nothing in `:packs` reaches outward for rules any more. |
| #556 ports (`ClosureWalk`, `ImpactCheck`, `MutabilityCheck`) | `packs.domain`, implemented in `packs.application` | As the ADR amendment already says. |
| Round-trip flow and verdict | Flow in `packs.infra.storage`, verdict in `packvalidation.roundtrip` | Merged in #578, unchanged. |
| Pack-set check for pack-check (`PackSetCheck`: load a set, `PACK(VALIDATE)` per pack, `CLOSURE`, name the checks that ran) | `pack-validation` | **Deferred to #478 [chosen].** Two lenses proposed it now. One objected that it would be speculative code with only tests as callers. #478 owns its inputs (`--config-root`, `--pack dir:level`) and its output. |

**What `pack-validation` is after #554:** the Spring-free check library that pack-check runs. Today it holds
`PackLoad` and the round-trip verdict, and #478 adds the set check. It is no longer "every pack check": the service's
rules are one set of classes, in `packs.domain`, and pack-check reaches them through `:packs`.

## 2. Flows

- **Validate mode.** `PackValidation.validate` = `PackVerdicts.of(PackRulesets.pack(VALIDATE).check(inspector.inspect(root, FULL)))`.
  The bean at `CharterWiring:87` is unchanged.
- **Registration [chosen: judged in `packs.application`].**
  - `PackRegistrationService.register` and `PackSourceStagers` take a `PackInspector` bean. They inspect the staged
    tree as `PackRoot(packId, empty)` (C-11), run `pack(REGISTER)`, and refuse through `PackRefusals.of`.
  - Only then does `WholePackStore.loadStaged` read and build the aggregate. It judges nothing, and returns the
    reader's faults.
  - **Fail-closed guard.** If the reader recorded any fault or build breach while `PACK` passed, the registration is
    refused as `validation_failed` in today's aggregate shape.
  - One lens judged inside `loadStaged` instead, which keeps the 63 `new WholePackStore(` test sites judging. Two
    lenses and A-M2 chose the application layer, which keeps the store a reader. The store tests that assert refusals
    move to `PackRegistrationService` tests and rule tests.
- **Compose read.** `DomainRepositoryService` keeps its reader and calls the same per-kind rules it calls today, so
  the pack is not read again (A-M3). It applies `COMPOSE_READ`, and the first failure, in reader order, answers
  `pack_unreadable` with the endpoint prefix kept. The `judged()` withholding stays, as aggregate shaping.
- **Offering.** `OfferingReadService` calls the resolver's collect-all `CLOSURE` functions and takes the first
  refusal in today's order. The descriptor check is one `packs.domain.rules` function, which the offering and the
  resolver precondition both call.
- **pack-check (#478, later).** Its main calls `PackSetCheck(new PackLoad(new FileSystemPackInspector()))`. That runs
  `PACK(VALIDATE)` and `CLOSURE`, and the round trip when asked. The output names the checks that ran (ruling 3).

## 3. R2(b): which token survives [chosen]

Each fault keeps its **registration token**, and validate mode changes:

- Registration is the contract wire.
- open-packs' expected-code fixtures pin the registration tokens.
- #478 asks for "the same codes as the API".
- No main code injects `PackValidation` (`CharterWiring:87`).

Validate mode's change gets a `### Breaking` fragment. `planted/*/expected.json` and `scripts/packs/core_verdicts.py`
follow. Where registration has no token for a fault (it does not run that rule today), validate mode's token stays.

## 4. Tests

- **The question goes away.** Rule, `PACK` composition (M-7), mapper and surface-parity tests (SC-001) all live in
  `:packs` test and run the real rules.
- **The golden snapshot** (M-2) and the planted-pack test (M-8) run in `:packs` through the real inspector, rules and
  mappers.
- **#578's test edge** from `:packs` to `:pack-validation` stays, for the round-trip oracle only. No judging test
  uses it.
- **No no-op judge** exists anywhere.

## 5. Mutation floors and the mission's "margin widened" criterion

- **The rules' mutants stay in `:packs`** (`domain+application`, floor 85), with their tests, as M-9 asks. A narrowed
  PIT run is reported per work package.
- **`pack-validation`.** If #578's figure holds (about 88% against 85), its margin is already above 0, but #578
  widened it, not #554.
- **#554 adds no new main code to `pack-validation` under this design.** So the criterion "the pack-validation
  mutation margin is widened by #554" can only be met by:
  - (i) the nightly measurement of #578's code, which re-sets the documented margin (ADR-0062 row 105, still 0); or
  - (ii) bringing the set check forward from #478.

  This is operator question **R3-b**.

## 6. ADR-0075 amendment (2026-11-16, R3)

- **The rules stay in the packs domain.** The pack rules, `PACK` with its surface profiles, `CLOSURE` and `PackCodes`
  live in `packs.domain` (`packs.domain.rules`), in the kernel's rule vocabulary over the canonical domain objects.
  Decision 3's "every pack check in pack-validation" is replaced.
- **No judging port.** Each surface of the packs slice runs `PACK` directly. Decision 4's port, and the 2026-11-13
  "port that `packs.domain` declares", are withdrawn.
- **`pack-validation` is the Spring-free check library that pack-check runs.** It holds `PackLoad`, the round-trip
  verdict and, from #478, the pack-set check that names the checks that ran. It names only `packs.domain`. `deployable`
  keeps it on the test classpath only.
- **Converters.** They go because every surface emits kernel `Reason`s. One fault has one code and one wording. Validate
  mode adopts the registration tokens, under `### Breaking`.
- **#556.** The rules that call the external-check ports also live in `packs.domain`. `packs.application` composes them
  for the service, and pack-check does not compose them.
- **Mutation floors** stay with their subjects: the rules under `:packs` `domain+application`.

**ArchUnit:**
- Single-owner rules A to C are re-pointed to `LevelRules` and `PackCodes`, and their bite tests stay.
- New `PACK_CODES_HAVE_ONE_OWNER`: only `PackCodes` builds a pack `ReasonCode`.
- New `PACK_RULES_READ_NO_FILE`: `packs.domain.rules` names no `java.nio.file` or `java.io` file and stream types
  (NFR-004).
- The other rules and every allowlist are unchanged.

## 7. Challenge pass

See [design-r3-challenge.md](design-r3-challenge.md). Verdict: sound with folds. The folds below change §1 to §6.

- **F1 (MAJOR, to the operator).** The service no longer uses `pack-validation`, and ruling 1's "through a port"
  sentence is withdrawn. That reverses ADR-0075's founding steer: "a module the service itself uses, then repackaged
  as a CLI". The operator confirms or rejects this as R3 (`spec.md` FR-006, C-003, the key entity, and ADR-0075's
  title and description follow).
- **F2 (folded).** `PackAssembly` and `PackManifest` cannot live in `packs.domain`. They consume `infra.storage` types
  (`PackRead`, `KindBindings`, `TemplateBinding`, `GuardClosure`, `PackManifestDso`). So they become Spring-free,
  non-bean classes in `packs.infra.storage`. `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` is their acceptance check, and
  the inspector names them within its own package.
- **F3 (folded; replaces §3's blanket rule).** The token is chosen per fault, in a table: **the specific code wins**.
  - Registration's catch-alls give way to the specific code, because registration changes there:
    - `domain`, `reference` and the `invalid` fallback;
    - the aggregate `{pack, validation_failed, message}`;
    - the load refusal at pointer `pack`;
    - `schema` reported one per violation.
  - Where registration already answers a specific code (`retired`, `manifest_membership`, `reserved_shard`, `asset`,
    `prompt_fragment_invalid`, `relationship_endpoint_not_allowed`), validate mode adopts it.
  - Each surface that changes gets a `### Breaking` fragment: one for registration and one for validate mode.
  - WP01's golden snapshot lists every row before any code changes.
- **F4 (folded).** Judging on the inspector must keep registration's shape for a root it cannot read.
  - An I/O fault, or a root that is not a directory, answers 422 `validation_failed` at `pack`, as `loadStaged` does
    today. It does not answer a bare `pack_unreadable`.
  - The inspector does not follow symlinks on the registration path, as the store does not.
  - A reverse subset test is added: `PACK(REGISTER)`'s faults ⊆ today's reader faults, over every planted, example
    and scenario pack. Together with the forward subset test, this pins R1(a).
- **F5 (folded; brings part of pack-check's need forward).** `CLOSURE` needs `PackUnderResolution`, which holds the
  defined and changed artifacts and the held terms. Today only `DomainRepositoryService`, a `@Service`, builds it.
  - #554 adds a Spring-free function that builds `PackUnderResolution` from `PackContents`.
  - A parity test checks it against `DomainRepositoryService`'s facts over the example packs.
  - This avoids `CLOSURE` passing on empty facts in pack-check, and avoids a second derivation.
  - Decision 7's wording, "it needs every `pack.yaml`, never the graph", is amended: it needs every pack's contents,
    never the closure walk.
- **F6 to F10 (MEDIUM and MINOR, folded into the plan).**
  - The guard answers today's aggregate shape at `VIOLATION`.
  - The compose read's second fact source is recorded as the one remaining reader-side judgement, covered by both
    subset tests.
  - Registration judges once, in `PackRegistrationService`; `PackSourceStagers` calls through it.
  - #578's mutation figure is unverified in the repo (R3-b).
  - The ArchUnit rule wording and SC-001 are aligned.
  - The guard runs before the gate and the impact check, which both read the reader's contents.

## 8. Superseded by the shaping squad (2026-11-16)

After WP01 and WP02, open case 6 (validate mode judges files registration does not hold) led the operator to direct a
shaping squad ([squad-shaping.md](squad-shaping.md)) and to rule S1 to S3, then S6 ("Port, no rule change"). §1 to §7
stand as the record of R3; the elements below are changed, and the replacement is canonical. The plan, the data model
and the work packages follow the replacement. The compositions are written out, typed against the kernel, in
data-model.md ("The compositions").

| design-r3 element (where) | Replaced by | Ruling |
| --- | --- | --- |
| `PACK` with a surface profile `Surface {VALIDATE, REGISTER, COMPOSE_READ}` in `packs.domain.rules.PackRulesets` (§1, §2) | Named compositions with `ModularRuleset.must`, no enum and no mode flag: `PackRulesets.content()` and `closure()` in `packs.domain.rules`; `StorageRulesets.holding()` and `registration() = holding().must(content().on(TreeRead::contents))` in `packs.infra.storage.rules`. The manifest gate is a plain function: `StorageRulesets.verdict(read)` joins the read faults and `registration().check(read)` with `Rationale.and`, then, only when that `Rationale` passes, `.and(new Rationale(manifestMembership(read)))`. Validate mode is `PackCheck.validate = read.rationale().and(placement().check(new Placement(read, root.configuredLevel())))`. The compose read runs `content()` on its read's contents. | S1 to S3, S6 |
| Manifest, reserved-shard and not-held rules as per-kind rules in `packs.domain.rules` (§1) | Storage rules in `packs.infra.storage.rules`, over the `TreeRead` and its `TreeCensus`, with their tokens in `StorageCodes`. Nothing outside `packs.infra.storage` and `deployable` names that package; registration and the validation edge see these rules only through the `Rationale` of `PackReading`'s readout. | S3, S6 |
| `PackCodes` as the one owner of every pack `ReasonCode` (§1, §6 `PACK_CODES_HAVE_ONE_OWNER`) | Each token is built exactly once: domain tokens in `PackCodes`, storage and read-fault tokens in `StorageCodes`, the placement tokens in `pack-validation`'s `PlacementCodes`. The ArchUnit rule is reworded. | S3, Q7 |
| Mappers `PackVerdicts` and `PackRefusals` in `packs.domain` (§1) | `PackRefusals` in `packs.application` (API edge); `PackVerdicts` in `pack-validation` (validation edge). Neither names a storage code. `PackVerdicts` sorts by `Precedence.comparator(PlacementCodes' set)`, a `Comparator<Reason>` over kernel types, and `PackVerdict.findings` is a `List<Reason>` (WP09). | S1 to S3 (P5), S6 |
| `PackValidation` in `packs.domain`, and validate mode's flow `PackVerdicts.of(PackRulesets.pack(VALIDATE).check(inspector.inspect(root, FULL)))` with the bean at `CharterWiring:87` unchanged (§2) | `PackCheck` in `pack-validation`, the validation edge, constructed with the `PackReading` its caller supplies: `PackVerdicts.of(read.files(), read.rationale().and(placement().check(new Placement(read, root.configuredLevel()))))`. The bean goes, because `deployable` keeps `pack-validation` test-only. | operator direction, S2 (A6, Pr2), S6 |
| "`pack-validation` is the Spring-free check library that pack-check runs ... #554 adds no new main code to it" (§1, §5) | `pack-validation` is the validation edge: it reaches the core and storage rules through `PackReading`'s readout, and adds the round trip, placement and, from #478, the pack set and the CLI. #554 adds `PackCheck`, `PackVerdicts` and the placement rules, so its mutation margin is measured over #554's own code too. | operator direction, S2, S6 |
| Level, org-fragment, descriptor-format, asset-mime and repeated-asset-id rules validate-only in `PACK(VALIDATE)` (§1, R1 table) | Repeated asset id: a domain rule in `content()` (registration gains it). Descriptor version format and asset mime format: prechecks of the one reader (registration gains them, one `### Breaking` fragment). Level and org fragment: `pack-validation`'s placement extension, validate only. | S2 |
| Registration flow: inspect the staged tree, judge, then `loadStaged` reads again and builds; the fail-closed guard refuses a reader-only fault (§2, §7 F4, F6, F10) | One read produces `PackContents` and `TreeCensus`; registration takes the `Rationale` of `PackReading.read` (`StorageRulesets.verdict`), refuses through `PackRefusals`, then `PackAssembly` builds the aggregate from the same read's `PackContents` and the store stamps it. No second read and no guard. | S1, S6 |
| `PackAssembly` and `PackManifest` in `packs.domain` (§1; already folded to `packs.infra.storage` by §7 F2) | `PackAssembly` and `PackManifestDerivation` in `packs.infra.storage`, building from the one read, judging nothing. The reader calls `PackManifestDerivation` to put the derived manifest set in the `TreeCensus`, so the storage rules compare facts only. | S1 (A5) |
| A Spring-free composition-facts builder beside `DomainRepositoryService`'s own facts, held equal by a parity test (§7 F5) | One builder over the one read; `DomainRepositoryService` delegates to it. | S1 (P7) |
| Two owners of "held": the inspector's `PackFileKind` classification and the store's `modelledFiles` (implicit in §1, §2) | One `TreeCensus` in the storage edge, built from the narrowed `file-kinds.yaml` (`**/charter.yaml` and nested `**/*.graph.yaml` narrowed, a packs `### Breaking` line), with an explicit not-held kind. Validate mode warns `file_not_held` and judges nothing in a file not held. | S1, S3; open case 6 |
| The compose read applying `COMPOSE_READ` with its reader's second fact source recorded as the one remaining reader-side judgement (§2, §7 F6 to F10) | The compose read applies `content()` to the one reader's read, keeping its first-`pack_unreadable` mapping and the `judged()` withholding. | S1 |
| Serialization knowledge in `PackPolicy` and `PackContents` (schema wording, YAML vocabulary) (§1) | Neutral read faults in `PackContents`, with the schema wording owned by the storage-edge reader. | S1 to S3 (A4) |
| The `PackInspector` port of the load (§2, and the 2026-11-13 amendment's "port that `packs.domain` declares") with its `Scope {LOAD_PATH, FULL}` | One domain port of the read, `PackReading` (`PackReadout read(PackRoot root)`) in `packs.domain`. Its readout carries the `PackContents`, the classified files, the not-held files (`NotHeldFile`) and the storage edge's verdict as one kernel `Rationale`. `FileSystemPackInspector` implements it; its `TreeRead readTree(PackRoot)` is package-private. `PackInspector` and `Scope` are deleted (WP09). No `TreeReader` port is built. | S6 |
| ArchUnit (§6): `PACK_VALIDATION_IS_FRAMEWORK_FREE` unchanged, "every allowlist unchanged" | No rule is widened, and no allowlist entry is added: `PACK_VALIDATION_IS_FRAMEWORK_FREE`, the onion rules, `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK`, `STORAGE_RETURNS_DOMAIN` and `PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES` stand. Added: `STORAGE_RULES_ARE_FRAMEWORK_FREE` for `packs.infra.storage.rules`, and `PACK_RULES_READ_NO_FILE`. Rules A to C point at `pack-validation`'s `LevelRules` and `PlacementCodes`; `PACK_CODES_HAVE_ONE_OWNER` reads "each token is built once". | S3 (Pr4), S6, Q7 |
| The plan's single atomic switch work package (WP09 of 2026-11-16, ~80 files) | WP09 the validation edge, WP12 registration and the compose read on the one read, WP13 the deletions and ArchUnit; WP11 settles open case 6 first. | Pr7 |

What stands: the rules live in the packs domain. The core's rules are plain rulesets with no judging port; S6 adds one
reading port (`PackReading`) whose readout carries the storage edge's verdict. `CLOSURE` stays beside its functions in
`packs.domain`; R2's per-fault token table and F3's "the specific code wins"; the offering's first refusal in today's
order; the #556 ports; the round-trip flow; the mutation floors staying with their subjects; and the ADR-0075
amendment's rule-placement core, now extended by S1 to S3 and S6 in WP10.
