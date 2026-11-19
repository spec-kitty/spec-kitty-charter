# Research: pack-check CLI and its service release

## R1. Does the reader run without Spring? (prototype, 2026-11-19)

- **Decision:** yes. The jar's runtime classpath is `:pack-check`'s minus the Spring, Tomcat, Jakarta, Micrometer,
  logging-backend, web-transport and charter-api modules. That leaves 20 jars, 8.8 MB.
- **Evidence:** throwaway programs on that classpath, with no Spring class loaded:
  - `new PackCheck(new FileSystemPackInspector()).validate(...)` over `example-packs/built-in` answered one
    `file_not_held` warning;
  - `CompositionFactsBuilder.underResolution` (via reflection) and `PackRulesets.closure()` answered 0 faults for the
    five example packs as a set, and 2 `pack_dependency_unknown` for `project` alone;
  - `PackRoundTrip.run` and `WholePackRoundTripRule(WholePackExpectation.none())` answered 0 reasons for built-in,
    org-base and project.
- **Rationale:** `PACK_VALIDATION_IS_FRAMEWORK_FREE`'s transitive half already proves this statically. The prototypes
  prove it at run time, which reflection or a service loader could otherwise break.
- **Alternatives:**
  - Splitting `:packs` is refused by operator ruling 2.
  - Spring Boot's `bootJar` puts `org.springframework.boot.loader` in the jar, which breaks NFR-001.

## R2. SLF4J without a backend

- **Decision:** ship `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` naming
  `org.slf4j.helpers.NOP_FallbackServiceProvider`, which is in `slf4j-api` already.
- **Evidence:**
  - Without it, SLF4J prints three warning lines to standard error.
  - With `-Dslf4j.provider=…` it still prints one info line.
  - With the service file it prints nothing (prototype).
- **Alternatives:** `slf4j-nop` is a new dependency (C-004) and gains nothing.

## R3. One runnable file

- **Decision:** a Gradle `Jar` task over the lean runtime classpath (no new plugin):
  - `Main-Class` and `Multi-Release: true` (jackson-core has `META-INF/versions/17,21,23`);
  - `module-info.class` excluded everywhere;
  - `:packs`' `META-INF/services/java.net.spi.InetAddressResolverProvider` (the DNS-rebinding guard) excluded, since
    the CLI does no network I/O;
  - each dependency's `LICENSE`/`NOTICE` moved under `META-INF/licenses/<artifact>/`;
  - `DuplicatesStrategy.FAIL` for anything else.
- **Evidence:**
  - The only service files in the 20 jars are jackson-core's `TokenStreamFactory`, jackson-databind's `ObjectMapper`
    and `:packs`' resolver: distinct names, so nothing needs merging.
  - No jar is signed.
- **Alternatives:**
  - The Shadow plugin is a new unverified plugin (C-004).
  - A zip with a `lib/` directory is not one pinnable file (ADR-0075 d9: "a jar").

## R4. Where the facts builder becomes reachable

- **Decision:**
  - A domain port, `packs.domain.PackFactsReading` (`PackUnderResolution underResolution(ConfiguredPack, PackRoot)`).
  - A public adapter, `packs.infra.storage.FileSystemPackFactsReader`, delegating to the package-private
    `CompositionFactsBuilder.underResolution` with the YAML strategy.
  - `PackSetCheck` (pack-validation) takes the port; `PackCheckMain` wires the adapter.
- **Rationale:** it mirrors `PackReading`/`FileSystemPackInspector` (S6), and #478 asks for "a public entry, as an
  adapter the CLI's main names … outside `packs.domain`". `pack-validation` keeps naming only the packs domain and the
  kernel.
- **Known limit:** set mode reads each pack twice, once through the one reader and once through the builder's
  `StoreRead`. It is accepted until the single-parse unification (ADR-0075 AA1).

## R5. The report's home in the contract (sweep of the contract checks)

- **Decision:**
  - `contracts/charter/schemas/PackCheckReport.yaml`, OpenAPI 3.1 YAML, with `problems.items: $ref
    ../../_shared/schemas/Problem.yaml`, listed in `schemas/_index.yaml`.
  - One named exemption in `contracts/.../lint/ReachableContractFilesTest` (`STANDALONE`, with the ADR-0075 d9
    rationale), which also asserts that each exempted file exists and stays unreached, so the list cannot rot.
- **Evidence:**
  - `ReachableContractFilesTest:22-44` refuses any schema that `openapi.yaml` does not reach.
  - Nothing else refuses it: lint, closure, refusal-example and oasdiff checks read only the bundle.
  - `ContractConformance.vetEverySchema` walks every file under a `schemas` directory and allows only `$ref`, `allOf`,
    `oneOf`, `properties`, `items`, `additionalProperties` and `type, required, enum, pattern, minLength, maxLength,
    minItems, maxItems, minimum, maximum`. So the schema uses no `const`, `uniqueItems`, `anyOf` or `$defs`.
- **Rationale:** ADR-0075 d9 says the contract test checks the output "against `contracts/charter/schemas`".
  `components.schemas` would bundle it, generate a DTO and pull it under oasdiff as if served.
- **Alternatives:**
  - The `offering/` JSON Schema precedent (`ActivationConfig.schema.json`) cannot `$ref` the YAML `Problem` cleanly.
  - `contracts/packs/schemas` is pack-authoring content, and `PackSchemasTest` refuses an unmapped file there.
- **Shipping the schema:** the schema is versioned with the charter contract. Adding it to `contract-release-assets.sh`
  is not done here: open-packs pins the jar, and the jar's output is pinned by the contract test.

## R6. Matching the API's problem

- **Decision:** the CLI builds problems the way `RefusalHandler` does for `getPackRegistration`:
  - **title:** the token as a sentence;
  - **status:** the `x-refusal` status of `getPackRegistration` for the code;
  - **detail:** ends with the left-out count when more than 100 violations;
  - **packs:** as `RefusalPack`.
- **The pin:** a test in `service/deployable` (pack-check is on its test classpath already) compares, for every code
  the CLI can emit, the CLI's title, status and left-out wording with `RefusalHandler`'s. It validates real CLI output
  with `ContractConformance.schemaViolations("…/PackCheckReport.yaml", …)`. This is ADR-0075 d9's "contract test keeps
  the two equal".

## R7. SHA256SUMS gains a sixth line

- **Decision:** list `pack-check-<ver>.jar` in `SHA256SUMS` and attest it.
- **Rationale:**
  - ADR-0046 d4: "Adding a target is additive"; d6 documents `sha256sum --check --ignore-missing`.
  - Core's pin verifies the asset for its host by name, so an extra line is not one of its assets.
- **Risk recorded:** core's parser is in core (`kernel/service_pin.py`), outside this repository. The ADR-0046
  amendment states the new line so core can confirm. No core issue is filed (charter, issue rule 5).

## R8. The link rule (D1)

- **Decision:** `LinkRules` over the census's `LINK` entries in `StorageRulesets.holding()`, code `pack_entry_linked`
  (`VIOLATION`), registered in `StorageCodes.ORDERED`, `PackCodesCoverageTest` (a failing and a passing fixture), the
  R2 table, and the contract's `validation_failed` description.
- **Evidence:**
  - `PackFileSet.walk` lists file and directory links below the root without following them into directories
    (`FileCollector`).
  - `CensusEntries` gives them standing `LINK`.
  - Registration and validate mode share `StorageRulesets.verdict` (#554 S6), so one rule covers both. #597's probe
    (link to a directive, to a directory, to an unmodelled text file) becomes three registration test cases.
- **Precedence:** a followed file link is still read, so any other finding at that path is dropped in favor of the
  link code (R2: the specific code wins).
- **Open-packs:** its Python classification mirror does not know the code. A draft issue goes to
  `research/issue-drafts/` with an `open-packs#TBD-pack-entry-linked` placeholder (charter, cloud-session rules).

## Squad

The charter requires an adversarial squad at the plan point, because this changes the contract and the architecture.
Its findings and their dispositions are appended below once it reports.

### Plan-point squad (2026-11-19): architect-alphonso, java-jenny, reviewer-renata

All three returned **PLAN SOUND WITH CHANGES**. Each finding is dispositioned below: **A** accepted as recommended,
**C** accepted with a change, **R** put to the orchestrator for a ruling.

| # | Lens | Sev | Finding | Disposition |
|---|---|---|---|---|
| S1 | arch | HIGH | `PackFactsReading` adds a second read; the one read's `StoreRead` already yields the facts | **A.** No new port. `PackReadout` gains `facts`: a sealed `Built(PackCompositionFacts)` or `Unreadable(code, detail)`, computed in `FileSystemPackInspector.read` by `CompositionFactsBuilder.composeFacts(id, one.store())`. The preset ids come from `facts.presets()`. R4 is superseded |
| S2 | arch + renata | HIGH | The link's target is read before it is refused, and the configured-directory load never judges links | **R (D6).** Recommend: the walk drops links from `regular` (nothing reads through a link), and the store half states `pack_entry_linked`. A jar test plants links to a marker file and asserts the marker is in neither stdout nor stderr |
| S3 | arch | MED | The CLI rebuilds `PackRefusals.of` with other wording | **A.** The Rationale→`CharterRefusal` mapping moves to `packs.domain` (it names only `PackCodes` and the kernel). `PackRefusals` and the CLI both call it. The deployable test compares the whole problem for one fixture per code family |
| S4 | arch | MED | The preset phase sits outside `PackRulesets` | **A.** `PackRulesets.closure(presetIdsOf)` = `closure().must(presets)`. `PackSetCheck` calls only that |
| S5 | arch | MED | The exemption names three adapters | **A.** `PackCheckMain` is named by FQCN. It may name `FileSystemPackInspector` and `PackRoundTrip`, with a known-good control, and the ADR-0075 note records `PackRoundTrip` |
| S6 | arch | LOW | The port signature gives the name and level twice | **A.** Folded into S1 (no port) |
| S7 | arch | LOW | #174 root link, and a link to a directory inside the pack | **A.** Both get a test |
| S8 | renata | HIGH | `CLOSURE` over a set that does not hold a pack's dependencies | **R (D5).** Recommend: always run over the set as given; US1 reworded; the docs say to pass the dependencies. An acceptance test uses a dependent pack |
| S9 | renata | MED | Stricter than the deployment | **R.** Folded into D6 |
| S10 | renata | MED | D3 under-specified (other refusal codes, `IllegalStateException` on `Assembly.None`) | **A.** `Unreadable` carries any `CharterRefusal` code. `Assembly.None` maps to `Unreadable(pack_unreadable)`. When the pack has no `PACK` problem, the refusal becomes that pack's problem; it is never duplicated. Each branch gets a test |
| S11 | renata | MED | NFR-003 is tautological | **C.** NFR-003 is reworded: every planted golden subject runs through `PackCheckMain`'s output and is compared with registration's codes (`surfaces.json`) |
| S12 | renata | MED | FR-009 has no test | **C.** It is verified by inventory: no `pack-validation` type is CLI-only. Recorded in the WP and the PR; the row is marked `folded` |
| S13 | renata | MED | FR-012 cannot be accepted inside the mission; step 5 is missing | **A.** FR-012 becomes a post-merge follow-through: the version PR, then the `service-native` dispatch at the merge commit, then the tag for the operator, then the next `-SNAPSHOT` PR. Evidence is the PR and run URLs. The rc.2 notes say the contract is unreleased |
| S14 | renata | MED | `packs[].level` can be unknown | **A.** `level` is optional in the schema: absent when not given and `pack.yaml` declares none that can be read. It gets a test |
| S15 | renata | LOW | "I/O error mid-run: exit 2" contradicts the reader | **A.** Exit 2 only for failures outside a pack read (arguments, missing directory, writing output) |
| S16 | renata | LOW | SC-004 wording | **A.** Reworded: one code for a staged tree and a local directory; real uploads are refused earlier (`ENTRY_TYPE`, `PATH_REFUSED`) |
| S17 | renata | LOW | Hard links, Windows junctions, TOCTOU | **A.** Documented on the pack-author page; out of D1's scope |
| S18 | renata | LOW | pack-check allowlist, jar-entry checks, duplicate `--pack` by `toRealPath` | **A.** Folded into IC-04 and IC-06 |
| J1 | jenny | HIGH | The SLF4J NOP service file in `main` resources breaks deployable's Logback tests | **A.** The service file is generated into a build directory and added only to the fat jar |
| J2 | jenny | HIGH | R7's claim about core's extras is unproven | **A, settled by evidence.** Core's `src/specify_cli/services/vendor.py` (spec-kitty `4890242`), `map_assets`, maps `SHA256SUMS` names by exact match. Anything else is ignored and listed back; extras are only the `*.tar.gz` stems. The ADR-0046 amendment cites it |
| J3 | jenny | HIGH | The jar test runs only the success path | **A.** `jarTest` runs the jar for valid, pack fault, composition fault, planted link, schema-invalid file and usage error, each with `--round-trip` where it applies. It asserts the exit code, the schema-valid stdout and an empty stderr (warnings are asserted where expected). It uses the toolchain's `javaLauncher`, a cleared `JAVA_TOOL_OPTIONS`/`JDK_JAVA_OPTIONS`/`CLASSPATH`, a temporary working directory, and the jar as an input |
| J4 | jenny | MED | The jar-entry deny list is incomplete | **A.** It refuses `org/springframework/`, `org/apache/tomcat/`, `jakarta/`, `ch/qos/logback/`, `ai/speckitty/charter/api/`, `ai/speckitty/charter/web/`, the resolver service and `module-info.class`, and asserts `Multi-Release: true` |
| J5 | jenny | MED | A group exclude drops the BOM and shifts versions | **A.** Exclude by module (`spring-boot-starter-webmvc`, `:web-transport`, `:charter-api`) on a jar configuration that resolves consistently with `runtimeClasspath`. A test asserts the jar's library versions equal `runtimeClasspath`'s |
| J6 | jenny | MED | The allowlist never judges a new configuration | **A.** `enforceOn` judges the jar configuration too (build-logic change), or the jar is built from `runtimeClasspath` with the excludes; the WP picks one and tests it |
| J7 | jenny | MED | The fat jar and the thin jar write the same path | **A.** The fat jar gets its own `destinationDirectory` (`build/pack-check/`) |
| J8 | jenny | MED | Licence relocation | **A.** A configuration-cache-safe task relocates each artifact's licence files. The texts of re2j, json-schema-validator, itu and snakeyaml-engine are checked in. The build fails if an artifact has no licence. Source `MANIFEST.MF` files are excluded |
| J9 | jenny | MED | No mutation floor for the CLI | **A.** `charter.mutation-conventions` is applied to `pack-check` with a floor measured on the new code; `jarTest` stays out of PIT |
| J10 | jenny | MED | `verifyAll` must run the jar test; no timing in `check` | **A.** A tagged `Test` task over the `test` source set, wired into `check`. NFR-002's 20 s is timed in the CI jar job only |
| J11 | jenny | LOW | Release scripts and notes | **A.** Folded into IC-07; a refused case without the pack-check jar; the release notes name both jars |
| J12 | jenny | LOW | Reproducibility | **A.** Gradle 9.8.1's reproducible-archive defaults are kept, and a byte-identical check on two builds goes in `jarTest` |
