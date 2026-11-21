import net.ltgt.gradle.errorprone.errorprone

// The pack-validation module (ADR-0075 decision 3, as amended 2026-11-13 and by #554's S2, S3 and S6): the validation
// edge. Validate mode is PackCheck: it reads a pack once through the packs domain's PackReading port, whose readout
// carries the storage edge's verdict, adds its own placement rules (the configured level and the org fragment, with
// their codes in PlacementCodes) and maps the findings (PackVerdicts). It also holds the whole-pack round trip (#578).
// The service runs none of its main code; the pack-doctor CLI will (#478).
//
// It depends on :packs and names only the packs domain and the kernel: the packs domain model stays in :packs (operator
// ruling of 2026-11-13, #553), and a composition root supplies the file-system reader. :packs brings Spring onto the
// classpath, but nothing this module reaches uses it: HexagonalArchitectureTest's PACK_VALIDATION_IS_FRAMEWORK_FREE
// pins that transitively. Leaving Spring off pack-doctor's runtime classpath is #478's, with the jar.
//
// Not charter.domain-conventions: that refuses every external dependency. The module pins its own instead, below.
plugins {
    id("charter.java-base")
    id("charter.hand-written")
    // The weekly mutation floor (#239). Never charter.domain-conventions: it refuses the external dependencies this
    // module declares.
    id("charter.mutation-conventions")
}

ModuleDependencyAllowlist(
    projects = setOf(":kernel", ":profiles", ":missions", ":packs"),
    modules = setOf(
        // The parsing libraries the codecs and the schema validation use (ADR-0017), for rules that read them directly.
        "org.snakeyaml:snakeyaml-engine",
        "com.networknt:json-schema-validator",
        "tools.jackson.core:jackson-databind",
        "com.google.re2j:re2j",
        // A BOM only: it gives :packs' versionless dependencies (charter-api's) the versions the service runs, as the
        // Spring conventions do for :packs. It brings no class.
        "org.springframework.boot:spring-boot-dependencies",
    ),
).enforceOn(project)

dependencies {
    api(project(":kernel"))
    api(project(":profiles"))
    api(project(":missions"))
    // The packs slice's Spring-free load and the domain it builds (operator ruling of 2026-11-13, #553).
    api(project(":packs"))
    // Exported, so a consumer (pack-doctor) resolves :packs' versionless dependencies at the same versions.
    api(platform(catalog.lib("spring-boot-bom")))
    // The whole-pack round trip's expected baseline lists (WholePackExpectation, ADR-0075 decision 8 as amended
    // 2026-11-15): plain YAML, not pack content, so no pack reader parses them.
    implementation(catalog.lib("snakeyaml-engine"))

    // The charter-owned example packs (ADR-0070), their expected-red baseline and the round trip's lossy controls.
    testImplementation(testFixtures(project(":packs")))
    // ValidateGoldenTest writes and reads golden/validate.json as the other golden files are written (#554).
    testImplementation(catalog.lib("jackson-databind"))
}

// The weekly mutation floor (#239; ADR-0062, amendments of 2026-11-13 and 2026-11-17): one group for the whole module,
// the validation edge (PackCheck, PackVerdicts, the placement rules and PlacementCodes, #554) and the whole-pack round
// trip's verdict (#578). Measured on #554's head: 330 of 372 mutants detected (88.7 %), so floor5(score - 3) is 85 and
// the margin is 13 mutants (it was 0 on #553's one mutant). #554's classes kill every mutant; the survivors and the
// uncovered mutants are the round trip's. Raised by PR once the score clears the floor by 5.
mutationFloors {
    group("pack-validation") {
        packages.add("ai.speckitty.charter.packvalidation")
        floor = 85
    }
}

val repositoryRoot: File = rootProject.layout.projectDirectory.asFile
val testSourceSet = sourceSets.test.get()

// The link plants of the golden subjects skip where the file system cannot make a symbolic link; -PrequireSymlinks=true,
// which CI passes, makes them fail there instead (#286), as in :packs.
val requireSymlinks = providers.gradleProperty("requireSymlinks").orElse("false")

// The whole-pack round trip's gate (`whole-pack`) runs in its own task, below, never in `test` or PIT.
tasks.test {
    useJUnitPlatform { excludeTags("whole-pack") }
    inputs.property("requireSymlinks", requireSymlinks)
    systemProperty("charter.requireSymlinks", requireSymlinks.get())
    // Validate mode's golden snapshot (#554, NFR-002): ValidateGoldenTest compares with src/test/resources/golden/,
    // and `-Dgolden.update=true` makes it rewrite validate.json instead, the only way it is written. The switch is an
    // input, so a cached comparison never stands in for a rewrite.
    val goldenUpdate = providers.systemProperty("golden.update").orElse("false")
    inputs.property("goldenUpdate", goldenUpdate)
    systemProperty("golden.update", goldenUpdate.get())
    systemProperty("charter.goldenDir", layout.projectDirectory.dir("src/test/resources/golden").asFile.absolutePath)
}

// The whole-pack round-trip gate (#187; ADR-0075 decision 8, as amended by operator ruling 2 of 2026-11-15): every file of
// every charter example pack (ADR-0070, #547) goes through the production flow (PackRoundTrip, in :packs main), and
// WholePackRoundTripRule judges it by meaning against the checked-in expected-red baseline, which sits beside the example
// packs in :packs' test fixtures (whole-pack/). It writes one report per pack, reports/whole-pack/<pack>.md. It is not part
// of check; CI requires it through `CI gate / gate`, which calls .github/workflows/whole-pack.yml for the `wholepack`
// group (epic #186, operator ruling of 2026-11-05).
val wholePackReports = layout.buildDirectory.dir("reports/whole-pack")
tasks.register<Test>("wholePackRoundTrip") {
    group = "verification"
    description = "The whole-pack round-trip gate over every charter example pack (#187, #547); not part of check."
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("whole-pack") }
    val reports = wholePackReports
    outputs.dir(reports).withPropertyName("wholePackReports")
    systemProperty("charter.wholePackReportDir", reports.get().asFile.absolutePath)
}

// The open-packs drift test (ADR-0070): spec-kitty-open-packs runs it as a gate at its charter pin, and its
// charter-track.yml against charter releases and charter main; no charter workflow runs it (#604). The driftTest
// source set runs every pack of a spec-kitty-open-packs checkout through the production round trip and the gate's
// rule, with no expectation (its helpers come from the test source set). `check` (so verifyAll) compiles and lints the source set but
// never runs it, and no required job runs the driftTest task; -PopenPacksDir points at the checkout (default: the sibling
// ../spec-kitty-open-packs).
val driftTestSourceSet = sourceSets.create("driftTest") {
    compileClasspath += sourceSets.main.get().output + testSourceSet.output
    runtimeClasspath += sourceSets.main.get().output + testSourceSet.output
}
configurations[driftTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[driftTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
configurations[driftTestSourceSet.compileOnlyConfigurationName].extendsFrom(configurations.testCompileOnly.get())
// Test code: the same rules as compileTestJava (charter.hand-written), NullAway off.
tasks.named<JavaCompile>(driftTestSourceSet.compileJavaTaskName) {
    options.errorprone { disable("NullAway") }
}
val openPacksDir = providers.gradleProperty("openPacksDir")
    .map { OpenPacksCheckout.resolve(repositoryRoot, it).path }
    .orElse(OpenPacksCheckout.resolve(repositoryRoot, null).path)
tasks.register<Test>("driftTest") {
    group = "verification"
    description = "Round-trips every pack of a spec-kitty-open-packs checkout by meaning (open-packs CI); not run by check."
    testClassesDirs = driftTestSourceSet.output.classesDirs
    classpath = driftTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    systemProperty("charter.openPacksDir", openPacksDir.get())
    doNotTrackState("open-packs main moves; a cached pass says nothing about today's tree")
}
