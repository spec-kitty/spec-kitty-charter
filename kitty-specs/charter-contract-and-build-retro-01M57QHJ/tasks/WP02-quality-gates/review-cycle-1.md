---
affected_files: []
cycle_number: 1
mission_slug: charter-contract-and-build-retro-01M57QHJ
reproduction_command:
reviewed_at: '2026-10-18T20:13:25Z'
reviewer_agent: claude
wp_id: WP02
---

# WP02 review 1 (reviewer-renata): changes requested

Reviewed b602695, 9478862, e877713, ab0f2a8, 1c0be9f and 55e4c87 (lane head 55e4c87; the later lane commits
are status merges only). Most of this WP is right, and the gates work. Every gate failed on the violation I
planted, and no gate reaches charter-api. The formatting commit is formatting only, and the supply chain
is clean. Two findings block approval. Both are unregistered suppressions, and they contradict NFR-001, the
WP's "no inline `@SuppressWarnings` without the register's fields" and its own review guidance ("No
suppressions outside the register"). The recorded claim "suppression register size 0" is therefore not
accurate. Both fixes are small.

I ran every probe in a scratch clone at 55e4c87
(`/tmp/claude-0/-home-user/fb45a2c5-8ebb-5df5-9291-5519fc2684d3/scratchpad/wp02/repo`). The harness is
`probe.sh` plus `sub.py` beside it, and the logs are in `probes/`. Each plant was reverted afterwards.

## Findings

1. **[MEDIUM] charter.hand-written.gradle.kts:21: `-Xlint:-serial` and `-Xlint:-processing` are blanket
   suppressions of javac lint categories across every hand-written module. They are not in the register,
   the build does not need them, and `-serial` hides a real defect class.**
   The comment above them says that "no hand-written code has ... a serialized form". That is false.
   `CharterRefusal` extends `RuntimeException`, declares `serialVersionUID`, and documents `code` as "part of
   the serialized form". The flags came over from the old `charter.java-conventions`, but this WP rewrote that
   line and its justification. NFR-001 asks for 0 blanket suppressions.
   Probes:
   - (a) I deleted both flags and ran `./gradlew compileJava compileTestJava`. Every module compiled green,
     so neither flag suppresses any current warning.
   - (b) In `CharterRefusal` I planted a field `private final Thread owner = Thread.currentThread();` and a
     documented accessor. With the flags as committed, `:charter-domain:compileJava` reported BUILD
     SUCCESSFUL. With the flags removed, it failed:
     `CharterRefusal.java:14: warning: [serial] non-transient instance field of a serializable class declared with a non-serializable type` /
     `error: warnings found and -Werror specified`.

   **Recommendation:** make the hand-written flags exactly `-Xlint:all -Werror`, and delete the false
   comment. If a category ever needs to be turned off, it gets a register entry (rule, scope, reason,
   sunset). The plugin's header ("no gate is relaxed anywhere else") then stays true.

2. **[MEDIUM] GraphDocumentReader.java:127: an inline `@SuppressWarnings("unchecked")` with no register
   entry and no reason.**
   It sits in `adapter-out-yaml/src/main/**`, which this WP owns. `suppressions.xml` says it records every
   exception to another gate as a comment entry, and it reports "Size: 0 entries". Under `-Xlint:all
   -Werror`, `unchecked` is a gate, so this is an unrecorded exception to that gate. NFR-001 says that "each
   targeted suppression names its rule and reason". This one names the rule but gives no reason.
   Probe:
   ```
   grep -rnE '@SuppressWarnings|CHECKSTYLE:?OFF|NOPMD|NOSONAR|spotless:off' --include=*.java service contracts
   ```
   The only hit is `GraphDocumentReader.java:127: @SuppressWarnings("unchecked")`.
   **Recommendation:** use one of these:
   - (a) Remove the suppression. Return `Map<?, ?>` and read the values through it, or copy the entries
     into a new `LinkedHashMap<String, Object>`. Either way the cast disappears, and the key check already
     proves the keys are strings.
   - (b) Keep it, add an inline reason, and add a register comment entry with rule `unchecked`, the file, a
     reason ("keys proven String by the preceding allMatch") and a sunset date. Then correct the register's
     size line.

   Either way, add a grep step to the planted-proof record so that the "0 suppressions" claim can be
   checked.

3. **[LOW] No guard that every hand-written package carries `@NullMarked`.**
   NullAway runs with `OnlyNullMarked`. A new package without a `package-info.java` would therefore leave
   NullAway's scope without any error. Today all 6 main packages are marked (I checked by listing the
   packages that have `.java` files).
   **Recommendation:** add an ArchUnit rule in `HexagonalArchitectureTest`, or a small test, asserting that
   every `ai.speckitty.charter..` main package except `ai.speckitty.charter.api..` is annotated
   `@NullMarked`. Then the boundary that exempts generated code by construction cannot also exempt
   hand-written code by accident.

4. **[LOW] CharterProperties: the absent-packs path is not covered by any test, and the history note says
   it is.**
   The history says "covered by the Spring context in the BDD suite", but the BDD suite configures packs. I
   checked the behaviour with a scratch test that bound through
   `new Binder(new MapConfigurationPropertySource(Map.of("charter.other","x"))).bindOrCreate("charter", CharterProperties.class)`.
   `packs()` was empty and the test passed. So the `@DefaultValue` change preserves behaviour, but nothing
   pins it.
   **Recommendation:** commit that Binder test, with both the absent case and the one-pack case, in
   `charter-service/src/test`.

5. **[INFO] Smaller notes. None needs a change unless you agree.**
   - PIT targets come from `charter.domain-conventions`, not from `hasPlugin("charter.hand-written")`. The
     WP text asked for the latter, but domain plus read-app is exactly FR-009's scope and no module list is
     hard-coded. Say so in the history.
   - `disableWarningsInGeneratedCode = true` is a no-op for hand-written modules, which have no `@Generated`
     code. Consider dropping it, so that no relaxation-shaped option sits in the gate plugin.
   - The `contracts/charter` input dir also holds `scenarios/` and `CHANGELOG.md`, so editing a `.feature`
     file re-runs code generation. This is harmless, and the cost is small.

## What I verified (no change needed)

- **e877713 is formatting only.** It touches 52 `.java` files and nothing else. I compared every file with
  a tokenizer that ignores whitespace and comments and treats imports as a set (`tok2.py`), and no file
  differs.
- **Exempt by construction.** `:charter-api:check --dry-run` runs only `validateContract`,
  `generateContract`, compile and test, with no Checkstyle, Spotless, Javadoc or JaCoCo task. charter-api
  has no `errorprone` configuration ("configuration 'errorprone' not found"). `-Xlint:all -Werror` live only
  in `charter.hand-written`, and the `removeAll` subtraction is gone.
- **`generateContract` inputs.** An edit to `contracts/charter/schemas/Slug.yaml` re-runs it, and so does
  an edit to `contracts/_shared/schemas/Problem.yaml`. An edit to the closure checker's `JsonPointer.java` or
  to `contracts/lint/README.md` leaves it UP-TO-DATE.
- **Scenario classpath.** `charter-service/build/resources/test` holds only `scenarios/` and
  `junit-platform.properties`.
- **Planted proofs**, each with exit 1 and the rule named:
  - Spotless: `:charter-domain:spotlessJavaCheck FAILED`.
  - Checkstyle main: `Urn.java:3:17 ... [AvoidStarImport]`.
  - Checkstyle test: `UrnTest.java:3:17 ... [AvoidStarImport]` and
    `PackListingQueryTest.java:105:5 Class Probe should be declared as final. [FinalClass]`.
  - Error Prone main: `Urn.java:74: error: [ArrayEquals]`.
  - Error Prone test: `UrnTest.java:24: error: [ArrayEquals]` and
    `YamlDocumentTest.java:53: warning: [ReferenceEquality]` with `-Werror`.
  - NullAway main, in charter-domain, adapter-in-rest and charter-service:
    `[NullAway] returning @Nullable expression from method with @NonNull return type`.
  - NullAway test: `return null` in `UrnTest` compiles (exit 0), so NullAway does not run on test.
  - doclint, a public method without Javadoc, in each of the six modules (domain, read-app, rest, yaml,
    service, contracts): `warning: no comment` / `error: warnings found and -Werror specified`.
  - doclint, a broken `{@link}`: `error: reference not found`.
- **Wiring.** `:charter-domain:check` includes checkstyleMain, checkstyleTest, spotlessCheck, javadoc and
  jacocoTestReport. `verifyAll --dry-run` contains no pitest task.
- **Suppression grep.** Besides findings 1 and 2, I found no `CHECKSTYLE:OFF`, `NOPMD`, `NOSONAR` or
  `spotless:off`, no Error Prone `disable` other than NullAway on test (per the ruling), and no severity
  downgrade. The Checkstyle ruleset has 47 semantic checks, which is a meaningful set. `-Xmaxwarns 10000`
  on javadoc is fine.
- **Supply chain.**
  - `verification-metadata.xml` only adds entries (no `-` lines against 15d4ddf).
  - The SHA-256 values match the published ones for nullaway-0.14.2.jar, checkstyle-14.3.0.jar,
    palantir-java-format-2.101.0.jar, pitest-1.30.0.jar (Maven Central `.sha256`) and
    gradle-errorprone-plugin-5.1.1.jar (Plugin Portal download).
  - Every version is at least two weeks old. The newest is palantir 2.101.0, published 2026-10-01.
  - No jqwik, no OpenRewrite, no init script and no repository override.
- **Source edits.**
  - `rest.Absent` is sound and minimal.
  - The `Node.child` and `Vocabulary.names` rewrites keep the same behaviour: `names` is reached only after
    `typeProblem` has proved the value is a String or a list.
  - The protected constructor of `CharterServiceApplication` satisfies HideUtilityClassConstructor, and
    Spring still boots, as the BDD suite shows.
  - The ArchUnit change admits exactly `org.jspecify.annotations..`, as DM 01M589DHDJG0GQAFTWCJKJ60F1
    rules.
  - The out-of-map edits are formatting, nullness and Javadoc only, and they are recorded in the history.
- **Mockito.** `PackListingQueryTest` is meaningful: it pins the layer-order mapping and the
  refusal pass-through, and checks "once and nothing else" with `verifyNoMoreInteractions`. Mockito loads
  as a `-javaagent`; the test reports carry no self-attach warning, and PIT kills 8 of 8 mutants in
  read-app with that test.
- **Build.**
  - `JAVA_HOME=…java-25 ./gradlew clean verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs
    -PcontractBaseline=none --max-workers=2 --no-build-cache` gives BUILD SUCCESSFUL in 53s. It ran 639
    tests (566 service plus 73 contracts), 0 skipped, 0 failed.
  - A second run reports "Configuration cache entry reused".
  - `./gradlew pitest` takes 16s. It reports domain 71 mutations with 42 killed, read-app 8 mutations with
    8 killed, and writes HTML and XML reports.
