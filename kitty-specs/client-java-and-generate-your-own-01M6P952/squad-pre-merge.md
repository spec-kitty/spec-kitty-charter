# Pre-merge adversarial squad: findings and dispositions

Point-cut: pre-merge, 2026-11-05. Question: is the aggregate diff correct, honestly tested, inside its boundaries, and
is the how-to accurate? Four profile-loaded, read-only delegates: `reviewer-renata` (correctness, test honesty),
`architect-alphonso` (architecture, boundaries), `java-jenny` (Gradle, supply chain), `curator-carla` (docs). All four
returned **approve with changes**; none rejected.

## Convergent finding

| # | Severity | Lenses | Finding | Disposition |
|---|---|---|---|---|
| 1 | high | renata, jenny | `generateClientAgain` has the same cache key as `generateClient` (the output path is not part of it; `org.gradle.caching=true`), so `checkClientReproducible` could compare a generation with a cache copy of itself | **accepted** — `outputs.cacheIf { false }` on the second generation (`charter.client-java.gradle.kts`); shown: after a clean, `generateClient` came FROM-CACHE and `generateClientAgain` ran. `CheckTreesIdentical` gained its own test (one byte, a file in one tree only). The same-JVM limit is stated in the comment. |

## Other findings

| # | Severity | Lens | Finding | Disposition |
|---|---|---|---|---|
| 2 | medium | renata | `ClientOutputTest` skipped a missing directory silently | **accepted** — every directory must exist and hold files; generated and main classes are checked separately; a floor of named files. It then caught a real gap: the source set's directories include an empty `src/generated/java`, so the test now receives the generator's output directory only. |
| 3 | medium | renata | FR-004 proved for two models only | **accepted** — `noGeneratedModelRefusesAMemberItDoesNotDeclare` scans every generated source for the generator's refusal text |
| 4 | low | renata | A Problem with a null `status` (or `title`) was accepted | **accepted** — `ProblemDetails` requires both non-null; two tests |
| 5 | low | renata | Content type ignored without saying so | **accepted** — documented in the Javadoc; a test with `text/html` |
| 6 | low | renata | The header's version is not checked | **accepted** — the contract version is passed in and asserted |
| 7 | medium | renata, alphonso | `HowToTest` reads the how-to, which is outside the service path filters | **accepted** — `docs/guides/generate-a-client.md` added to `ci.yml`'s `service_re` and `service.yml`'s paths |
| 8 | medium | alphonso | Coordinates conflict on #217 | **changed** — the orchestrator confirmed the later ruling (group `ai.speckitty.charter`, artifact `charter-client`); listed for the operator's confirmation in the PR hand-off; must be settled before #225 publishes |
| 9 | low | alphonso, jenny | Two different `info.version` regexes | **accepted** — one `Project.contractInfoVersion` in `CharterBuild.kt`, anchored under `info:`, used by both plugins |
| 10 | low | alphonso | The register of gate exemptions omits the client's `generated` source set | **accepted** — named in `charter.hand-written` and `charter.java-base` |
| 11 | low | alphonso | Opened objects serialize unknown members back and change `equals` | **accepted** — documented in the build guide's client section |
| 12 | low | alphonso | `implementation(generated.output)` is a file dependency, dropped from publication metadata | **deferred_with_rationale** — nothing is published; #225 must check the POM lists the `api` dependencies only (noted in the hand-off) |
| 13 | medium | jenny | Generated classes are not on `apiElements`, so an in-build consumer would not compile against them | **deferred_with_rationale** — no in-build consumer exists; the jar carries both source sets. The first consumer is #219's smoke test, which must consume the jar or add the generated classes to the `classes` variant (noted in the hand-off) |
| 14 | low | jenny | No `eol=lf` for the header template | **accepted** — `.gitattributes` |
| 15 | low | jenny | Generator support files (pom, wrapper) land in the output directory | **deferred_with_rationale** — not on any source set or in the jar; cosmetic |
| 16 | low | jenny | `inputs.dir(templates)` duplicates the plugin's own input | **deferred_with_rationale** — harmless; kept explicit beside the bundle input |
| 17 | low | jenny | A redundant regex alternative in `HowToTest` | **accepted** |
| 18 | medium | carla | The page framed core (a `core-maintainer` audience, a core line) | **accepted** — audience removed; ADR-0021 described neutrally; "no Python client is published" stated once |
| 19 | medium | carla | No source given for the token and URL | **accepted** — the service path is marked optional, the token's source linked |
| 20 | medium | carla | "Verify" overclaimed | **accepted** — a checksum beside its file proves a whole download, not its origin |
| 21 | medium | carla | The literal-path caveat misstated its source | **accepted** — generated clients are unaffected; validators and mocks may refuse |
| 22 | low | carla | The 403 caveat omitted a non-loopback `Host` | **accepted** |
| 23 | low | carla | macOS checksum commands; `unzip`; the Java prerequisite | **accepted** |
| 24 | low | carla | The lead-in named `openapi.yaml` for every source | **accepted** |
| 25 | low | carla | Snapshot version without an example | **accepted** |
| 26 | low | carla | YAML 1.1 coercion in the round trip | **deferred_with_rationale** — the page says "for client generation only"; the contract has no such scalars today |
| 27 | low | carla | The changelog repeats the build guide | **deferred_with_rationale** — kept short (under 2.2 kB); the entry records what changed, the guide stays the authority |
| 28 | low | carla | The options table repeats Steps 3 and 4 | **deferred_with_rationale** — the steps are copy-paste commands, the table is the rationale |
