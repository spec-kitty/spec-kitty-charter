# Research: Charter example packs replace the open-packs corpus

## R-1 — Where the example packs live

- **Decision**: a `java-test-fixtures` source set of `:packs`, consumed by `:deployable` (operator, DM-01M74GFCT9ZC6V4HEAQZM6MA3C).
- **Rationale**: one copy for both modules, no path property, the files sit beside the schemas they exercise.
- **Alternatives considered**: a repository directory passed as a system property (keeps the `openPacksDir` plumbing).

## R-2 — The seam

- **Decision**: replace `OpenPacksCorpus` (`root`, `packsDirectory`, `packs`, `assertPinned`) with `ExamplePacks`
  (`root`, `packsDirectory`, `packs`); `assertPinned` has no equivalent and is dropped.
- **Rationale**: every corpus test reads through it; the re-point is mechanical where a test asserts a property of
  all packs, and a rewrite where it asserts real content.
- **Alternatives considered**: copying open-packs content into the repository (a second vendored corpus; rejected by
  ADR-0070).

## R-3 — Tests that pin real content

| Test | Pins | Becomes |
| --- | --- | --- |
| `PackDigestBaselineTest` | digests of six real packs | digests of the example packs, recorded once |
| `PackManifestParityTest` | the real built-in manifest's hashes | the example built-in manifest's hashes |
| `MissionTypeCorpusTest`, `OpenPacksComposeTest` | real pack names and mission types | example pack names (`built-in`, `org-base`, `project`) |
| `ProfileCorpusTest`, `ProfileExamplesEdgeTest` | `java-jenny`, `qa` profiles and the contract examples | example profiles; the contract examples are aligned with them |
| `WholePackOpenPacksRoundTripTest` | per-pack known failures | none |

## R-4 — Supply chain

No dependency changes. `java-test-fixtures` is a core Gradle plugin; dependency verification is unchanged.
