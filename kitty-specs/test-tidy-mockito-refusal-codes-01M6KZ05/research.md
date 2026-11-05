# Research: Test-suite tidy (Phase 0)

Re-verification of the assessment's findings on `origin/main` at `4b52bcb` (2026-11-04, JDK 25.0.5),
for the whole follow-up programme (this mission and its sibling missions for #370/#374, #320 and #239),
with the decisions for this mission.

## R-01 Findings re-verified

| Finding | Check | Result |
|---|---|---|
| #373: no test uses Mockito | grep `org.mockito` over `service/` and `contracts/` sources | 0 hits. CONFIRMED |
| #373: declared and attached | `build-logic/src/main/kotlin/charter.hand-written.gradle.kts:104-128`, `gradle/libs.versions.toml:29,53-54` | CONFIRMED |
| #369: 15 type-only `CharterRefusal` checks | script over whole `assertThatThrownBy` statements ending in `.isInstanceOf(CharterRefusal.class)` | exactly 15, at the lines #369 lists. CONFIRMED |
| #320: 8 deadline checks in 5 files | grep `assertTimeoutPreemptively` and `isLessThan(Duration…)` | all 8 present at the comment's lines. CONFIRMED |
| #370: classes with no own-module test | grep `Constrained`, `Rationale` (kernel), `SelfReviewStep`, `ModeDefault` (profiles), `CappedBodySubscriber` (packs) in each module's tests | 0 hits each. CONFIRMED |

**PIT baseline** (`./gradlew pitest --continue`, 2 min 30 s; score = killed incl. timed-out ÷ all):

| Module | Mutants | Killed | Survived | No coverage | Score | Strength | Floor `floor5(score − 3)` |
|---|---:|---:|---:|---:|---:|---:|---:|
| kernel | 378 | 274 | 28 | 76 | 72.5 % | 90.7 % | 65 |
| missions | 144 | 108 | 5 | 31 | 75.0 % | 95.6 % | 70 |
| profiles | 66 | 35 | 2 | 29 | 53.0 % | 94.6 % | 50 |

Kernel grew from 196 mutants (assessment, `d2e1670`) to 378; the floors are unchanged from #239's comment.

## R-02 Overlaps

- **Draft PR #368** (`ci-sonar-nightly-checkstyle-complexity`, #371/#372) edits
  `build-logic/src/main/kotlin/charter.hand-written.gradle.kts`, `config/checkstyle/*`,
  `docs/guides/service-build.md` and adds a changelog file. This mission touches the `.gradle.kts`
  only in the Mockito comment. Its body still describes a 14-entry shrink-only suppression register,
  against the operator's no-grandfathering ruling of 2026-11-04; #367 is unowned. The operator ruled
  (2026-11-04) to leave #367 to #368; flagged to the operator, not acted on here.
- **PR #375** is docs only (cutover read R17): no overlap.
- Within the programme: #369 and #320 both edit `InterruptedCommitRepairTest` (different lines);
  #239 regenerates `verification-metadata.xml` again for the pitest plugin on packs, so it lands
  after this mission or rebases onto it.

## R-03 Decision: keep Mockito, at Spring's version (#373)

- **Decision**: keep the explicit `mockito-core` / `mockito-junit-jupiter` test dependencies and the
  Java agent; set the catalog version to **5.23.0**, the version `spring-boot-dependencies` 4.1.1
  manages (`<mockito.version>5.23.0`) and `spring-boot-starter-test` 4.1.1 requires; record the reason.
  Operator ruling, decision `01M6KZNKE4QW577JQ5MFXVVPPQ`.
- **Rationale**: `spring-boot-starter-test` depends on Mockito in `:deployable` and `:packs`, so a
  removal leaves it on those classpaths unless excluded, which the operator judged not worth it. Kept,
  the agent avoids the JDK's dynamic self-attach refusal the day a test uses it. Today the catalog's
  5.24.0 overrides Spring's 5.23.0 (`mockito-core:5.23.0 -> 5.24.0`), so two versions are verified.
- **Alternatives considered**: exclude `org.mockito` from the starter (removes it fully; rejected by
  the operator); drop only our own declaration (leaves a transitive Mockito without its agent).
- **Supply chain (`DIRECTIVE_051`)**: no new artifact. 5.23.0 is already resolved by the Spring
  stack and its SHA-256 is already in the verified metadata; the regen re-records it from the same
  repository. Not a security-impacting dependency decision, so no adversarial challenge at plan; the
  brief's pre-PR squad covers the diff.

## R-04 Decision: regenerate the verified metadata from scratch

- **Decision**: empty `<components>` and regenerate with `--write-verification-metadata sha256`
  over every task family, keeping `<configuration>` verbatim; review the diff. Decision
  `01M6KZNNTG47M33595KY54671S`.
- **Rationale**: Gradle only adds entries; it never prunes (the stale `mockito-core` 5.23.0/5.24.0
  pair and `byte-buddy` 1.17.7/1.18.3/1.18.11 show it). The build guide forbids hand edits.
- **Alternatives considered**: additive regen (leaves 5.24.0 stale).
- **Risk**: an entry needed only by a task outside the regen run is dropped. Mitigated by the task
  list in `plan.md` IC-01 and a strict `verifyAll` and `pitest` run on a fresh Gradle user home.

## R-05 Decision: assertion shape for #369

- **Decision**: `.isInstanceOfSatisfying(CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.X))`,
  as `ConsistentReadTest` does; keep every existing post-condition assertion.
- **Rationale**: one statement, the code named in the failure message, no new test method.
- **Alternatives considered**: `.extracting(...)` (loses the type check), a shared helper (adds
  indirection for 15 sites; rejected as friction).
