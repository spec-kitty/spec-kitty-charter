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
