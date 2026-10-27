# Work Packages: Agent profiles round-trip through the domain

Hand-authored (no CLI). Each WP is test-first and lands as its own commit.

## WP01 - The `profiles` slice and the `AgentProfile` entity

- Module `:profiles` (`charter.domain-conventions`, `api(project(":kernel"))`), in
  `settings.gradle.kts` and `charter-reports`' `serviceModules`.
- `AgentProfile` and its value objects; invariants from the schema; `relationships()` empty.
- ArchUnit: `profiles` in `SLICES` and `DOMAIN_RINGS`, `PROFILES_DOMAIN_IS_PURE` with a biting
  control, the slice-coverage control moved to a non-slice owner.
- **Done when**: entity unit tests and `HexagonalArchitectureTest` pass.

## WP02 - The binding seam and the stored kind

- `KindBinding.schemaName()` and `idKey()`; `PackFileSet`, `YamlSerializationStrategy` and
  `FilesystemPackStore.locate` use them; `KindBindingsTest` name check tolerates `_`.
- `AgentProfileDso`, `AgentProfileYaml`, `AgentProfileBinding`; one line each in `KindBindings`,
  `YamlFormats`; `DomainRepositoryService.save(AgentProfile)`.
- **Done when**: a format test round-trips every pinned profile DSO-equal and pins the fidelity
  cases (quoted numbers, typed integers and booleans, trailing newlines, omitted defaults,
  `excluding` forms); a store test saves a profile in place by `profile-id`; the pinned and
  vendored packs load with no profile violations.

## WP03 - The gate turns `agent-profile` green

- `DomainRoundTrip`: `agent-profile` modelled, its file token and id key from the binding, its
  profiles saved; `WholePackRoundTripTest.REQUIRED_KINDS`; `MeaningComparator` open map
  `excluding`; the `expected-red.yaml` entry removed.
- **Done when**: `:packs:wholePackRoundTrip` passes and the report shows 26 green agent profiles.

## WP04 - Docs

- ADR-0037 amendment (decisions 4 and 6), changelog entry, #187 quickstart message fix, any
  architecture page that lists the slices or the bound kinds.
- **Done when**: `check_docs.py all`, markdownlint and cspell pass.
