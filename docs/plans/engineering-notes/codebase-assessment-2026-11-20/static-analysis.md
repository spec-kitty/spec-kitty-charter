---
title: "Static analysis, 2026-11-20"
description: What the charter's static-analysis gates and CI evidence show at 25e24c37, including a silenced timing nightly, an unexplained Sonar gate failure and suppressions.
doc_status: draft
updated: 2026-11-20T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/codebase-assessment-2026-11-20
  - plans/engineering-notes/code-and-test-quality-assessment-2026-11-12
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the broken timing nightly, the Sonar scope gaps, the suppression inventory and the dependency hygiene, each with its fix.
---

# Static analysis, 2026-11-20

Lens L4 of the [codebase assessment of 2026-11-20](/plans/engineering-notes/codebase-assessment-2026-11-20/), pinned
at `25e24c37`. It reads the static-analysis gates and their CI evidence: Error Prone and NullAway, Checkstyle,
duplication, dependency hygiene and SonarCloud. It changes no code.

Baseline: `25e24c372bae838d95a9328be57ee2063f6c05d1`. Read-only. No PIT re-run and no verifyAll. CPD was run locally (PMD 7.28.0, resolved through the [ADR-0079](/adr/0079-dependencies-resolve-through-a-maven-central-mirror/) mirror). All file:line references are at the baseline.

## 0. Defects first

No production correctness, data or security defect was found by this lens. The most serious item is a **test defect that silences a monitor**:

### F1. The CPU-time budget nightly is dead, and nobody is told (HIGH, S)

- Run 44673807037 (2026-11-20, at 25e24c37) was **cancelled** by the 20-minute job timeout (`timing-nightly.yml:33`) during the step "Check the CPU-time budgets". Run 44457638021 (2026-11-19, 8ba750f8) was cancelled too. The last success was 44236574406 (2026-11-18). The green runs of 11-17 and 11-18 took 19m33s and 19m45s, so the 20-minute timeout has no headroom; the 11-19 run at `8ba750f8` predates both fixture breaks below (`git merge-base --is-ancestor` refuses both), so the timeout is the first cause and the fixtures a second one from 11-20.
- Two budget tests fail while building their fixture, before they measure anything. Both are stale fixtures that `verifyAll` never runs, because the `timing` tag is excluded (`service/packs/build.gradle.kts:175`, `service/activations/build.gradle.kts:39`):
  - `ReachabilityBudgetTest > aReachOverFiveHundredArtifactsReportsItsP95` fails with `IllegalArgumentException: a scope edge may not connect tactic to tactic` at `Relationship.of(Relationship.java:206)`, through `ReachabilityBudgetTest.fixture(ReachabilityBudgetTest.java:100)`. The cause is the scope rule that #602 (`0e8ffd9a`, tension as a relation) added. The fixture loop is at `ReachabilityBudgetTest.java:95-110`.
  - `ResolverCostBoundTest > aFiftyPackTwoHundredEdgeComposeResolvesWithinTheP95Bound` fails with `ConstraintBreachException: A pack id must be a kebab slug...` at `PackIdentifier.<init>(PackIdentifier.java:42)`, through `ResolverCostBoundTest.java:114`. The cause is `ResolverCostBoundTest.java:140`: `String.format("01CNZST0000000000000000000%020d", index)` gives 46 upper-case characters. #583's bulk edit (`8a44dfc3`) rewrote the old ULID-shaped id to this, so the comment at `:139` ("Twenty-six characters") is false too.
  - `PackFileSetBudgetTest.theDigestChecksAreWithinBudget` measured **10.68 s against a 10.00 s budget** (`PackFileSetBudgetTest.java:78`).
- Each budget runs in a fresh JVM (`setForkEvery(1)`), and `charter-api:validateContract` takes about 2 minutes twice in the job. The job therefore runs past 20 minutes. The job is cancelled rather than failed, so the "Report a CPU-time budget over its limit" step (`timing-nightly.yml:84-89`, `if: steps.timing.outcome == 'failure'`) is **skipped**. A cancelled scheduled run does not notify the way a failure does.
- **Remedy:** fix the two fixtures (use a slug pack id, e.g. `"cost-%04d"`, and use a relation type the scope rule allows between tactics). Raise `timeout-minutes` or give the timing step its own `timeout-minutes` below the job's. Also add `if: always() && steps.timing.outcome != 'success'` to the report step. Optionally compile the `timing`-tagged classes and construct their fixtures in `check`, so stale fixtures fail on the PR. Size S.

## 1. CI evidence

| Workflow | Run id | Commit | Conclusion | What the log prints |
|---|---|---|---|---|
| `service.yml` (push to main) | 44611792378 | 25e24c37 | success | `verify`: `verifyAll` "BUILD SUCCESSFUL in 13m 18s", 235 tasks. `:charter-reports:testCodeCoverageReport` ran. **No coverage percentage is printed**: JaCoCo has no threshold (`charter.hand-written.gradle.kts:126`, "no threshold (FR-008)"). Jobs `jar` (5m44s build), `git-source` and `release-rehearsal` also ran. |
| `ci.yml` (CI gate) | 44681758074 (PR #615) | b37cec8b | success | ci.yml has only `pull_request` and `merge_group` triggers (`ci.yml:30-34`), so no main run exists. Runs 811-813 on PR #608 were cancelled; 44661945292 was green after the daemon heap was raised to 2 GiB. |
| `sonar.yml` | 44674623934 | 25e24c37 | success (reported) | Scan OK; **"✖ Quality Gate has FAILED."**, condition not named; see section 6. |
| `mutation-testing.yml` (weekly, Mon 02:43) | 43794141037 | 97985727 (2026-11-16) | success | Every group is above its floor (table below). It is run #1 of workflow id 416325827; the next run is 2026-11-23. |
| `timing-nightly.yml` | 44673807037 | 25e24c37 | **cancelled** | See F1. Report-only p95 values: ActivationSelection 21.3 ms (budget 200), EffectiveRead 110.1 ms (budget 300), NFR-008 202-after-last-byte 243 ms (budget 1000), NFR-001 tree generation 0.55 s and whole-pack loads 3.74 s (budget 10 s each), digest checks **10.68 s (budget 10 s, over)**. |

Artifacts: `coverage` 14572958427 (5.6 MB), `javadoc` 14573043261, `contract-bundle` 14574141511 and `service-jar` 14573083825 exist. **They could not be downloaded:** the redirect goes to `productionresultssa*.blob.core.windows.net`, which the egress proxy refuses (403). Full job logs come only through the MCP `get_job_logs` content path, for the same reason. So the aggregate JaCoCo percentage is **not available** from here.

Mutation floors (log of run 43794141037; score / strength / floor):

| Group | Mutants | Score | Floor | Margin |
|---|---:|---:|---:|---:|
| :packs domain+application | 4141 | 90.3 | 85 | +5.3 |
| :packs infra.fetch | 693 | 78.2 | 70 | +8.2 |
| :packs infra.archive | 429 | 82.9 | 80 | **+2.9** |
| :packs infra.storage | 3241 | 90.4 | 85 | +5.4 |
| :activations | 1586 | 91.8 | 85 | +6.8 |
| :artifacts-edge | 354 | 91.5 | 85 | +6.5 |
| :kernel | 846 | 83.6 | 75 | +8.6 |
| :missions | 174 | 78.7 | 70 | +8.7 |
| :missions-edge | 171 | 91.2 | 85 | +6.2 |
| :pack-validation | 350 | 88.0 | 85 | +3.0 |
| :profiles | 261 | 91.1 | 90 | **+1.1** |
| :profiles-edge | 167 | 98.8 | 85 | +13.8 |

- TIMED_OUT mutants: 12 in others, 54 in packs-core and 13 in packs-storage, 79 in all (the sum of PIT's per-mutator `TIMED_OUT` counts; 87, 65 and 23 log lines mention the word). The packs shards took 44m01s and 40m29s.
- `:routing` and `:routing-edge` have floors (90 and 85) but were added after this run, so they have not been measured yet.
- #461 ("closes when a nightly is green") is still open, although 43794141037 is green. It is a closure candidate.

## 2. Error Prone and NullAway

Configuration: `build-logic/src/main/kotlin/charter.hand-written.gradle.kts`.

| Item | Setting | Evidence |
|---|---|---|
| javac | `-Xlint:all -Werror` on **every** JavaCompile (main, test, testFixtures, driftTest) | `:28-31` |
| Error Prone | 2.50.0, default check set, no check raised or lowered by name, no `-XepAllErrorsAsWarnings`. With `-Werror`, every default WARNING-level finding fails the compile. | `:83`, `libs.versions.toml:29` |
| NullAway | 0.14.2, **ERROR on compileJava only**, with `OnlyNullMarked=true` and `JSpecifyMode=true` | `:89-95` |
| NullAway off | compileTestJava (`:97-101`), compileTestFixturesJava (`:104-108`), pack-validation driftTest (`service/pack-validation/build.gradle.kts:120-122`) | operator ruling 2026-10-18 |
| Error Prone fully off | client-java `generated` source set (`charter.client-java.gradle.kts:97-98`) and the native AOT sets (`charter.native-image.gradle.kts:40-41`). charter-api does not apply the plugin. | generated code |
| @NullMarked guard | ArchUnit `everyHandWrittenMainPackageIsNullMarked` (`HexagonalArchitectureTest.java:2349-2367`) | imports service classes only |
| Javadoc | `-Xdoclint:all -Werror` on main | `:115-124` |

@NullMarked coverage per module (packages with code under `src/main/java`, against `package-info.java` carrying `@NullMarked`):

| Module | Packages | @NullMarked |
|---|---:|---:|
| activations | 8 | 8 |
| artifacts-edge | 8 | 8 |
| deployable | 1 | 1 |
| kernel | 6 | 6 |
| missions / missions-edge | 1 / 8 | 1 / 8 |
| pack-check | 0 (only package-info at HEAD) | – |
| pack-validation | 3 | 3 |
| packs | 16 | 16 |
| profiles / profiles-edge | 3 / 8 | 3 / 8 |
| routing / routing-edge | 2 / 6 | 2 / 6 |
| web-transport | 2 | 2 |
| contracts | 4 | 4 |
| **client-java** | 1 | **0**: `ai.speckitty.charter.client` (hand-written `ProblemDetails.java`) |

Service coverage is 100%. client-java is the one gap: under `OnlyNullMarked`, NullAway silently skips its hand-written class, although `charter.client-java.gradle.kts:4` says the hand-written sources "keep every gate". The ArchUnit guard does not import client-java. See **F6**.

### Suppression inventory

Totals: **main 7 (NOSONAR 2, @SuppressWarnings 5)**, the same as 11-12 (2 / 5) and the same set. Java test code has 48 `@SuppressWarnings`. Kotlin build code has 10 `@Suppress("UNCHECKED_CAST")`. There are no `CHECKSTYLE:OFF`, `NOPMD` or `@SuppressFBWarnings`.

Main code:

| file:line | Suppresses | Justification written? | Valid? |
|---|---|---|---|
| `service/kernel/.../kernel/domain/Relationship.java:138` | NOSONAR java:S2583 (null check under @NullMarked) | Yes, `:134-136` and inline | Yes: callers from parsed YAML; RelationshipTest pins the IAE |
| `service/packs/.../infra/api/ProblemErrorController.java:45` | NOSONAR java:S3752 + S6856 (`@RequestMapping` on all methods with a placeholder) | Yes, `:41-43` | Yes: a container error forward of any method |
| `service/packs/.../infra/storage/PackDescriptorDso.java:81` | `"checkstyle:ParameterNumber"` | Yes, "one parameter per descriptor key" | **Dead.** checkstyle.xml has no `ParameterNumber` module and no `SuppressWarningsHolder`/`SuppressWarningsFilter`, so it suppresses nothing. It is also an exception outside the register (F5). |
| `service/packs/.../infra/storage/PackFileKind.java:648` | `unchecked` (`javac -Xlint`) | **No comment** | Plausible: `instanceof Map<?,?>` then cast to `Map<String,Object>` of a bundled YAML; the reason is unwritten |
| `service/packs/.../infra/storage/YamlFormats.java:47` | `unchecked` | Yes, "DSO type checked before the cast" | Yes |
| `service/profiles-edge/.../infra/api/ProfileJson.java:75` | Error Prone `UnusedMethod` (Jackson mix-in) | Yes, Javadoc `:70-73` | Yes |
| `service/profiles-edge/.../infra/api/ProfileJson.java:97` | Error Prone `UnusedMethod` (mix-in) | Yes, Javadoc `:96` | Yes |

Test code (48 Java), grouped:

| Group | Count | Where | Justification | Valid? |
|---|---:|---|---|---|
| `unused` on ArchUnit bad-fixture fields | 13 | `service/deployable/src/test/java/.../badfixture/*.java:9`, plus `DomainReachesOutward.java:10,14` | No comment; the fixtures exist only to hold a forbidden dependency | Yes by construction; a one-line class comment would do |
| `unused` on test-only types | 5 | `ActivationProblemsTest.java:127,159`, `EndpointTableAgreementTest.java:219,236`, `RefusalHandlerTest.java:208` | None | Probably valid (reflection or record shapes); unwritten |
| `unchecked` on YAML/JSON read-back with a comment | 6 | `GlossaryYamlTest.java:375,382`, `GuardAndLifecycleYamlTest.java:154`, `ProcedureYamlTest.java:167,172`, `SkillYamlTest.java:214` | Yes ("a YAML mapping read back from text this test wrote") | Yes |
| `unchecked` without a comment | 24 | `ProfileCorpusTest.java:105,156,162,212,260,282,299,311,504` (9), `PackGraphEdges.java:38,70`, `ProfileExamplesEdgeTest.java:122,127`, `EffectiveGlossaryReadsTest.java:234`, `WholePackCensusAgreementTest.java:113`, `YamlNullFormsTest.java:12`, `contracts/.../ClosureCheckerTest.java:533`, `EffectiveArtifactCopiesParityTest.java:152,157,162`, `TermCopiesParityTest.java:69,74,79` | No | Valid in kind (casting untyped YAML), but 9 in one file shows a missing typed helper |

Kotlin (build-logic tests and `contracts/build.gradle.kts:483`): 10 `@Suppress("UNCHECKED_CAST")` on snakeyaml maps in the workflow guard tests. Acceptable.

## 3. Checkstyle and Spotless

| Item | State at 25e24c37 | Evidence |
|---|---|---|
| Checkstyle version | 14.3.0 (latest 14.4.0) | `libs.versions.toml:27` |
| Failure policy | `isIgnoreFailures=false`, `maxWarnings=0`, `maxErrors=0`, Checker `severity=error` | `charter.hand-written.gradle.kts:50-56`, `checkstyle.xml:13` |
| Source sets | Every source set: checkstyleMain (16), Test (16), TestFixtures (packs), DriftTest (pack-validation). client-java `checkstyleGenerated` is disabled. | verify log of 44611792378; `charter.client-java.gradle.kts:100` |
| **Complexity gate** | **ON since 2026-11-12** (`6451dc59`, #371): CyclomaticComplexity 15, NPathComplexity 1000, MethodLength 60 (countEmpty=false), NestedIfDepth 1. They apply to test code too. | `config/checkstyle/checkstyle.xml:74-87`; ADR-0058 `:84-91` |
| Parameter count | **Off**: no `ParameterNumber` module, and ADR-0058 does not list it. No ClassFanOut, JavaNCSS or ExecutableStatementCount. | `checkstyle.xml` |
| Exception register | `config/checkstyle/suppressions.xml`: **0 entries** ("Size: 0 entries"), checked by `checkCheckstyleRegister` in `check` | `suppressions.xml:15-18`; `charter.hand-written.gradle.kts:60-77` |
| Spotless | 8.10.3 with palantir-java-format 2.101.0 and `formatAnnotations()`. Targets main, test, testFixtures and driftTest. `spotlessCheck` is part of `check`. | `charter.hand-written.gradle.kts:36-47` |
| Javadoc | doclint all, warnings are errors | `:115-124` |

Since 11-12 the gate is strictly stronger: the complexity limits are on, with no grandfathering. SonarCloud's profile is logged as **"Sonar way core"** (sonar log 04:43:24), which matches 11-12's observation that S3776, S138, S1541 and S107 never fire. Checkstyle is the only complexity gate ([ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/)).

## 4. Duplication (PMD CPD)

[ADR-0079](/adr/0079-dependencies-resolve-through-a-maven-central-mirror/) names Google's mirror, which is reachable. `pmd-dist` has no `-bin.zip` on Central (HTTP 404), so pmd-cli and pmd-java 7.28.0 were resolved with Maven through the mirror into a scratch directory and run with `PmdCli cpd`. Scope: `service/*/src/main/java`, 948 files, 121,302 physical lines.

| min-tokens | Blocks | Duplicated lines | Rate (of physical lines) |
|---:|---:|---:|---:|
| 100 (PMD default) | 6 | 204 | 0.17% |
| 75 | 25 | 782 | 0.64% |
| 50 | 111 | 3,090 | 2.55% |

The six blocks at 100 tokens:

| Lines | Tokens | Location A | Location B |
|---:|---:|---|---|
| 29 | 107 | `packs/infra/api/PackRemovalController.java:87` | `packs/infra/api/PackUninstallController.java:53`: **new since 11-12 (#517)** |
| 26 | 101 | `packs/infra/api/ArchiveBodyAdvice.java:145` | `packs/infra/api/PackSourceBodyAdvice.java:69`: the `LimitedStream` copy that 11-12 flagged; **still there** |
| 13 | 136 | `profiles/infra/api/EffectiveProfileDtos.java:140` | same file `:180` |
| 13 | 101 | `packs/domain/InForceResolution.java:122` | `packs/domain/rules/Precedence.java:104` (precedence logic in two places) |
| 11 | 121 | `packs/application/ActionContextWalk.java:368` | same file `:436` |
| 10 | 116 | `packs/infra/storage/GraphDocumentReader.java:46` | `packs/infra/storage/GraphDocumentYaml.java:37` |

Lizard 1.17.13 `-Eduplicate` reported a 49.79% "duplicate rate". That figure counts identifier-normalized structural similarity (enums, records) and is **not comparable to CPD or Sonar**, so it is discarded.

## 5. Dependency hygiene

| Item | State | Evidence |
|---|---|---|
| Version catalog | `gradle/libs.versions.toml`: every `[libraries]` alias and every version key is referenced (static scan: 0 unused) | script in "Reproduce" |
| Verification | `gradle/verification-metadata.xml`: `verify-metadata=true`, **`verify-signatures=false`**, 593 components, SHA-256 only, 0 PGP keys | `:3-6` |
| Dependency locking | **None** (no `dependencyLocking`, no `*.lockfile`). Versions are pinned by catalog and BOM; verification catches substitution but not a silent BOM-driven change. | grep |
| Repositories | Mirror first, then Central (ADR-0079); `FAIL_ON_PROJECT_REPOS` | `settings.gradle.kts:9-23` |
| Gradle wrapper | 9.8.1, `validateDistributionUrl=true`, **no `distributionSha256Sum`**, no wrapper-validation action in any workflow | `gradle/wrapper/gradle-wrapper.properties` |
| Update bot | **No Dependabot, no Renovate** (`.github/` holds only CODEOWNERS and workflows). Actions are SHA-pinned (good), so nothing proposes their updates either. | `ls .github` |
| Mockito kept with no test using it | Documented in `charter.hand-written.gradle.kts:143-147` (#373) | |

Currency against the mirror's `maven-metadata.xml` on 2026-11-20:

| Library | Pinned | Latest | Note |
|---|---|---|---|
| Spring Boot BOM | 4.1.1 | **4.1.3** (patch), 4.2.0 | two patch releases behind; also drives Jackson, Tomcat and Mockito |
| Error Prone | 2.50.0 | 2.53.0 | |
| NullAway | 0.14.2 | 0.14.4 | |
| Checkstyle | 14.3.0 | 14.4.0 | |
| JaCoCo | 0.8.15 | 0.8.16 | |
| PIT | 1.30.0 | 1.30.2 | |
| Spotless plugin / palantir | 8.10.3 / 2.101.0 | 8.11.0 / 2.103.0 | |
| openapi-generator | 7.26.0 | 7.27.0 | |
| networknt json-schema-validator | 3.0.8 | 3.0.9 | |
| cucumber BOM | 8.0.4 | 8.1.0 | |
| okhttp / gson (client) | 4.12.0 / 2.11.0 | 5.5.1 / 2.15.0 | pinned on purpose to the generator's okhttp-gson template (`libs.versions.toml:38-40`) |
| junit, archunit, snakeyaml-engine, commons-compress | 6.1.3, 1.5.1, 3.2, 1.28.0 | current (junit's next is 6.2.0-M1) | |

## 6. SonarCloud

`sonar.yml` facts:

- Nightly at 04:23; report-only; every step `continue-on-error`.
- The quality gate step uses `sonarqube-quality-gate-action` v1.2.1 (`:173-180`). Its `quality-gate-status` output goes to the job summary only (`:192`, `:203`).

Nightly 44674623934 (job 136083394156), at 25e24c37, everything the log exposes:

- SonarScanner CLI 8.1.0.6389 on a Java 21 scanner JRE, with `sonar.java.source=25` and preview off. The quality profile is **"Sonar way core"**. The project binding is BOUND; branch `main` (long).
- 1,824 files were indexed. Main: 947 parsed, **945 analyzed**. Test: 873.
- **WARN "Unresolved imports/types have been detected"**. A likely cause: `service/packs/src/testFixtures/java` (8 files) is neither in `sonar.tests` nor on `sonar.java.test.libraries` (`sonar.yml:161,165`). Five modules' tests use `testFixtures(project(":packs"))`.
- **WARN `Use of preview features have been detected`** while `sonar.java.enablePreview=false`.
- Coverage: the JaCoCo XML imported 1 report. The CPD executor reported 609 files with blocks and 338 with none. Taint analysis ran 29 rules over 3,187 UCFGs. No dashboard numbers are printed.
- CE task: `AaH3RZ3h9d8Rc-CgklMs`.
- Gate step: **"✖ Quality Gate has FAILED."**, exit 1, `outcome=failure`. **No condition is named.**
- Scope gap: `sonar.sources` and `sonar.tests` list 13 modules (`sonar.yml:160-165`), but **`service/pack-check` is missing**, although `service/charter-reports` aggregates it for coverage (`service/charter-reports/build.gradle.kts:42`). The comment at `sonar.yml:143` ("The five hand-written service modules") is stale.

Issues:

- **#463** is open (P2). Its code findings are fixed on main (c9d388b8a, cfea446b6, a3bcac6e6, e65a9b646). What remains is **marking 5 false positives** in the UI: S2095 `RegistrationWiring.java:185`, S6416 ×3 (`ActivationFileCodec.java:167,181`; `PackFileKind.java:527`) and S9428 `Fingerprint.java:17`. The ready-to-paste reasons are in comment 6806498128. A 2026-11-17 comment records the same "gate FAILED, condition unknown".
- **#494** (parent) is open with sub-issues 4/8 done: #461 open, #463 open, #530 open, #531 open; #465, #387, #388 and #389 closed.

**The numbers are not guessed.** The operator should pull them (anonymous for a public project; otherwise add `-u $SONAR_TOKEN:`):

```bash
B=https://sonarcloud.io/api
P=spec-kitty_spec-kitty-charter
# 1. Which gate condition fails (the missing fact)
curl "$B/qualitygates/project_status?projectKey=$P&branch=main"
curl "$B/qualitygates/get_by_project?project=$P&organization=spec-kitty"
curl "$B/ce/task?id=AaH3RZ3h9d8Rc-CgklMs"                      # the analysis of 25e24c37
# 2. Ratings, size, coverage, duplication (overall and new code)
curl "$B/measures/component?component=$P&branch=main&metricKeys=alert_status,quality_gate_details,reliability_rating,security_rating,sqale_rating,security_review_rating,security_hotspots_reviewed,bugs,vulnerabilities,code_smells,coverage,line_coverage,branch_coverage,duplicated_lines_density,duplicated_blocks,ncloc,files,complexity,cognitive_complexity,sqale_index,new_reliability_rating,new_security_rating,new_maintainability_rating,new_coverage,new_duplicated_lines_density,new_security_hotspots_reviewed,new_violations"
# 3. Open issues by rule, type and severity
curl "$B/issues/search?componentKeys=$P&branch=main&resolved=false&facets=rules,types,severities,impactSoftwareQualities,impactSeverities&ps=500"
# 4. Hotspots
curl "$B/hotspots/search?projectKey=$P&branch=main&status=TO_REVIEW&ps=500"
# 5. Quality profile actually bound (expected "Sonar way core")
curl "$B/qualityprofiles/search?project=$P&organization=spec-kitty"
# 6. Per-file duplication and coverage, to compare with CPD above
curl "$B/measures/component_tree?component=$P&branch=main&metricKeys=duplicated_lines_density,coverage&qualifiers=FIL&s=metric&metricSort=duplicated_lines_density&asc=false&ps=50"
```

Expected (not verified): if the gate is "Sonar way", its conditions are on new code (coverage ≥ 80%, duplication ≤ 3%, ratings A, hotspots reviewed 100%). `quality_gate_details` names the failing one.

## 7. Findings

| # | Finding | Severity | Remedy | Size |
|---|---|---|---|---|
| F1 | The CPU-time budget nightly is cancelled two nights running (44457638021, 44673807037); two fixtures are broken by #583 and #602; the report step is skipped on cancel; the digest-check budget is over (10.68/10 s) | HIGH | Fix `ResolverCostBoundTest.java:140` and `ReachabilityBudgetTest.java:95-110`; set a timing-step `timeout-minutes` below the job's, and report on `!= success`; compile and construct the timing fixtures in `check` | S |
| F2 | The SonarCloud gate has failed every night with no named condition; the workflow summary prints only the status | MEDIUM | Pull the export list in section 6. Add a step that curls `api/qualitygates/project_status` with the token and writes the failing conditions to `$GITHUB_STEP_SUMMARY`. Mark #463's 5 FPs. | S |
| F3 | Sonar scope gaps: `service/pack-check` is missing from `sonar.sources/tests/binaries`; packs testFixtures are missing from tests and test libraries (likely source of the "unresolved types" WARN); the comment at `sonar.yml:143` is stale | MEDIUM | Add `pack-check` and `service/packs/src/testFixtures/java` (+ `build/classes/java/testFixtures`). Better: derive the list from the `serviceModules` map in charter-reports, so a new module cannot be missed (the guard test could assert parity). | S |
| F4 | No dependency update automation; Spring Boot two patches behind (4.1.1 → 4.1.3), and every quality tool one or more minors behind; no wrapper checksum; no PGP signature verification | MEDIUM | Add Renovate or Dependabot (gradle + github-actions, grouped, weekly), with the verification-metadata regeneration step in its PR template; set `distributionSha256Sum`; consider `verify-signatures` for new components | M |
| F5 | The register says "Size: 0 entries" and "those gates are never relaxed in source" (`suppressions.xml:5-8`), but main code has 5 in-source `@SuppressWarnings` (2 Error Prone `UnusedMethod`, 2 javac `unchecked`, 1 dead `checkstyle:ParameterNumber`) and 2 NOSONAR. `PackFileKind.java:648` has no written reason. | LOW | Either record the 7 as comment entries in the register (as its header asks) or change the header to allow commented in-source suppressions; delete the dead `checkstyle:ParameterNumber`; add a reason at `PackFileKind.java:648` | S |
| F6 | client-java's hand-written `ProblemDetails` is in a package without `@NullMarked`, so NullAway (OnlyNullMarked) skips it while the plugin claims every gate applies | LOW | Add `package-info.java` with `@NullMarked` to `client-java/src/main/java/ai/speckitty/charter/client/` (the generated sources are in a separate source set) | S |
| F7 | Duplication: 0.17% (CPD, 100 tokens). 11-12's `LimitedStream` copy (ArchiveBodyAdvice/PackSourceBodyAdvice) is unchanged, and a new 29-line copy sits between PackRemovalController and PackUninstallController (#517) | LOW | Extract one `LimitedStream` (limit as a parameter) and a shared removal-response helper | S |
| F8 | Test `@SuppressWarnings("unchecked")`: 24 have no reason, 9 of them in `ProfileCorpusTest.java` | LOW (fold) | A typed YAML read helper in test fixtures removes most of them | S |
| F9 | Mutation margins are thin: `:profiles` +1.1, `:packs infra.archive` +2.9 and `:pack-validation` +3.0; 79 TIMED_OUT mutants; #461 is closable on 43794141037 | LOW | Close #461 with run 43794141037 as evidence; watch the three thin groups (#530 and #531 cover the timeouts) | S |
| F10 | `verifyAll` takes 13m18s against a 20-minute CI step budget (PR #608 timed out twice before the 2 GiB heap); `charter-api:validateContract` takes about 2m10s per invocation | LOW | Cache or skip `validateContract` when its input is unchanged (it has no declared outputs to be up-to-date against); track the margin | M |

Since 11-12:

- **Improved:** the complexity gate is on, the mutation nightly is green, and the #463 code findings are fixed.
- **Unchanged:** the suppression set (2/5), the LimitedStream duplication, and the 5 unmarked FPs.
- **New:** the timing nightly is broken (F1), pack-check is outside Sonar (F3), and the Removal/Uninstall controller copy.

## 8. Reproduce

```bash
git rev-parse HEAD   # 25e24c37..., from the repository root
# Suppressions
git grep -n -E '@SuppressWarnings|NOSONAR|@Suppress\(' -- '*.java' '*.kt' '*.kts'
# @NullMarked coverage
for m in service/*/ client-java contracts; do d=${m%/}/src/main/java; [ -d $d ] || continue
  for p in $(find $d -name '*.java' ! -name package-info.java -printf '%h\n'|sort -u); do
    grep -qs '@NullMarked' $p/package-info.java || echo "UNMARKED $p"; done; done
# Checkstyle modules and register
grep -n '<module name=' config/checkstyle/checkstyle.xml; grep -c '<suppress ' config/checkstyle/suppressions.xml
# CPD (PMD 7.28.0 via the ADR-0079 mirror). The scratch pom that resolved it is not kept: it declares
# net.sourceforge.pmd:pmd-cli and pmd-java 7.28.0, and settings.xml points Maven at the mirror. Shape:
mvn -q -s settings.xml -Dmaven.repo.local=./m2 dependency:copy-dependencies -DoutputDirectory=./lib
java -cp "lib/*" net.sourceforge.pmd.cli.PmdCli cpd --language java --minimum-tokens 100 --format csv \
  --no-fail-on-violation $(for d in service/*/src/main/java; do echo -d $d; done)
# Version currency
curl -s https://maven-central.storage-download.googleapis.com/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml | grep -o '<release>[^<]*'
# CI logs (GitHub MCP): get_job_logs job_id=136083394156 (sonar), 136080736191 (timing),
#   135870042667 (service verify), 133058994609/133058994356/133058994610 (PIT shards)
```

Raw outputs (the CI log extracts for sonar, verify, timing and the PIT shards; the CPD CSVs at 100, 75 and 50 tokens;
the raw suppression grep; the `@NullMarked` list) are not committed; regenerate them with the commands above.
