import net.ltgt.gradle.errorprone.errorprone

// The pack vertical slice: domain, application, infra.storage, infra.api under ai.speckitty.charter.packs.
// WP05 activated the infra.storage dependencies; WP06 activated the infra.api ones.
plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
    // The nightly mutation floor (#239). Packs cannot use charter.domain-conventions, which refuses external
    // dependencies, so it applies the mutation conventions itself; the PIT scope and its floors are below.
    id("charter.mutation-conventions")
    // The example packs every pack-reading test reads (ADR-0070): a test-fixtures source set :deployable consumes.
    `java-test-fixtures`
}

dependencies {
    // The test fixtures (ADR-0070) resolve versions through the same BOM as main and test.
    testFixturesImplementation(platform(catalog.lib("spring-boot-bom")))
    api(project(":kernel"))
    // The profiles slice's domain: the pack store binds the agent-profile kind (ADR-0037, amendment of 2026-10-27).
    api(project(":profiles"))
    // The missions slice's domain: the pack store binds the mission-type kind (ADR-0044).
    api(project(":missions"))

    // infra.storage: YAML codec and pack-file schema validation (confined to this module, ADR-0017).
    implementation(catalog.lib("snakeyaml-engine"))
    // networknt json-schema-validator validates pack files against contracts/packs/schemas (ADR-0017).
    // The Spring Boot BOM is used here only to pin Jackson 3 and slf4j to the versions the service runs;
    // networknt's own Jackson (3.2.x) and slf4j pins are excluded so the service classpath does not move.
    implementation(platform(catalog.lib("spring-boot-bom")))
    implementation(catalog.lib("json-schema-validator")) {
        exclude(group = "tools.jackson.core")
        exclude(group = "tools.jackson.dataformat", module = "jackson-dataformat-yaml")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    // re2j runs the patterns a pack writes into a config_schema in linear time (no backtracking).
    implementation(catalog.lib("re2j"))
    implementation(catalog.lib("jackson-databind"))
    // infra.archive: the tar and zip readers of the bounded pack archive extractor (IC-03, DIRECTIVE_051). Only the
    // JDK-backed STORED and DEFLATED paths run; the optional codecs are not on the classpath.
    implementation(catalog.lib("commons-compress"))
    implementation(catalog.lib("slf4j-api"))

    // infra.api: the inbound REST adapter implements the generated contract interfaces.
    api(project(":charter-api"))
    // The service-wide transport: problem rendering, refusal mapping and the JSON body guard.
    implementation(project(":web-transport"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))

    testImplementation(catalog.lib("spring-boot-starter-test"))
    // NetworkCodeBoundaryTest: the inner rings name no java.net type but URI (WP08; the version deployable pins).
    testImplementation(catalog.lib("archunit"))
    // OwnerOnlyTest: a file system without POSIX views, to run the ACL path of the owner-only helper on Linux (#510).
    testImplementation(catalog.lib("jimfs"))
}

// The contract version the service reports (ContractVersion): the info.version of contracts/charter/openapi.yaml,
// written as a resource so the service never parses the contract at run time.
val contractVersion = tasks.register("contractVersionResource") {
    description = "Writes the contract's info.version into contract-version.properties."
    val spec = rootProject.layout.projectDirectory.file("contracts/charter/openapi.yaml")
    val out = layout.buildDirectory.dir("generated/contract-version")
    inputs.file(spec).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("contract")
    outputs.dir(out).withPropertyName("resources")
    doLast {
        val version = Regex("""(?m)^info:\n(?:[ ]+.*\n)*?[ ]{2}version:[ ]*(\S+)""")
            .find(spec.asFile.readText())
            ?.groupValues?.get(1)
            ?: throw GradleException("contracts/charter/openapi.yaml has no info.version")
        val file = out.get().file("ai/speckitty/charter/packs/infra/api/contract-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}
sourceSets.main { resources.srcDir(contractVersion) }

// The service's own version (ServiceVersion): the root project's `version`, from gradle.properties, written as a
// resource. It is independent of the contract version above. The value is read at configuration time, so the task
// holds no Project at execution and is configuration-cache safe.
val serviceVersion = tasks.register("serviceVersionResource") {
    description = "Writes the root project's version into service-version.properties."
    val value = rootProject.version.toString()
    val out = layout.buildDirectory.dir("generated/service-version")
    inputs.property("version", value)
    outputs.dir(out).withPropertyName("resources")
    doLast {
        val file = out.get().file("ai/speckitty/charter/packs/infra/api/service-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$value\n")
    }
}
sourceSets.main { resources.srcDir(serviceVersion) }

// ServiceVersionTest checks the written version against the root project's own, which can equal the contract version
// (both are 0.1.0-SNAPSHOT today), so the test cannot tell the two sources apart by their values alone.
tasks.named<Test>("test") {
    val projectVersion = rootProject.version.toString()
    inputs.property("projectVersion", projectVersion)
    systemProperty("charter.projectVersion", projectVersion)
}

// The pack schemas and the file-kind table are bundled as main resources under packs/, so that
// classpath:packs/schemas/<kind>.schema.json and classpath:packs/file-kinds.yaml resolve offline. The copy
// is a declared input of processResources, so a schema change re-runs the tests.
val packContracts = rootProject.layout.projectDirectory.dir("contracts/packs")
tasks.processResources {
    from(packContracts) {
        include("schemas/*.schema.json", "file-kinds.yaml")
        into("packs")
    }
}

val repositoryRoot: File = rootProject.layout.projectDirectory.asFile
val testSourceSet = sourceSets.test.get()

// The whole-pack round-trip gate (#187): every file of every charter example pack (ADR-0070, #547) is read into the
// domain, written back, read again and compared with its source by meaning, against the checked-in expected-red
// baseline (src/test/resources/whole-pack). It writes one report per pack, reports/whole-pack/<pack>.md. It is not part
// of check; CI requires it through `CI gate / gate`, which calls .github/workflows/whole-pack.yml for the `wholepack`
// group (epic #186, operator ruling of 2026-11-05).
val wholePackReports = layout.buildDirectory.dir("reports/whole-pack")
tasks.register<Test>("wholePackRoundTrip") {
    group = "verification"
    description = "The whole-pack round-trip gate over every charter example pack (#187, #547); not part of check."
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform {
        includeTags("whole-pack")
        excludeTags("timing")
    }
    val reports = wholePackReports
    outputs.dir(reports).withPropertyName("wholePackReports")
    systemProperty("charter.wholePackReportDir", reports.get().asFile.absolutePath)
}

// The real-git tests of the git source (WP18, T093, T152): bare repositories served over https by `git http-backend`
// behind a test HTTPS server, fetched with the host git, which must be 2.50.1 or later (C-008, NFR-011). They carry the
// `git-host` tag, which `test`, `check` and PIT leave out (unitTestExcludedTags, below). Locally, with an older git or none,
// they are skipped and reported as skipped; the `git-source` job of .github/workflows/service.yml runs this task with
// -PrequireGitHost=true, so there a git that is too old fails them instead of skipping them.
val requireGitHost = providers.gradleProperty("requireGitHost").orElse("false")
tasks.register<Test>("gitHostTest") {
    group = "verification"
    description = "The real-git tests of the git source (tag git-host); need git 2.50.1 or later; not part of check (WP18)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("git-host") }
    systemProperty("charter.requireGitHost", requireGitHost.get())
    inputs.property("requireGitHost", requireGitHost)
    // The tests start a server and run git: never cached as a pass for another host.
    doNotTrackState("a result measured with another git says nothing about this one (WP18)")
    testLogging { showStandardStreams = true }
}

// The CPU-time budgets (#316): tests tagged `timing` hold a piece of work to a CPU-time budget, which only means
// something on a quiet machine. Under verifyAll every module's tests run at once, and that load pushed the generated-tree
// checks, then in PackFileSetExactnessTest, past their budget (13.1 s of a 10 s budget on a merge-queue runner) although
// nothing in them had changed. So they are not part of `test` or `check`: this task runs them in one test JVM, and the
// nightly CI workflow runs the packs and activations budgets with --no-parallel. The correctness of the same work stays
// in `test` (PackFileSetExactnessTest). Run it in an invocation of its own, as the only task named:
//
//   ./gradlew :packs:timingBudgets
tasks.register<Test>("timingBudgets") {
    group = "verification"
    description = "The CPU-time budgets (tests tagged timing), run alone; not part of check (#316)."
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("timing") }
    maxParallelForks = 1
    // Each budget measures in a fresh JVM: a timing class that ran earlier in the same JVM leaves heap and JIT state
    // that costs the next one's measured thread about 10% (ResolverCostBoundTest before PackFileSetBudgetTest).
    setForkEvery(1)
    // Never beside this module's other test tasks, when they are named in the same invocation.
    mustRunAfter(tasks.test)
    doNotTrackState("a budget measured on another run says nothing about this one (#316)")
    // The generated trees plant symbolic links. A budget that skips is never meaningful, so a file system without them
    // fails here, whatever -PrequireSymlinks says.
    systemProperty("charter.requireSymlinks", "true")
    // The budget tests that report a measured time (PackFileSetBudgetTest, ResolverCostBoundTest) print it, so the margin
    // shows in every run.
    testLogging { showStandardStreams = true }
}

// What the unit tests need from the repository, shared by `test` and `pitest` (below), which runs the same tests in its
// own JVMs: the tags they leave out, the system properties they read and the repository files they read, declared as
// inputs so a cached result never outlives them.
val repositoryDir = rootProject.layout.projectDirectory
// `git-host` (WP18's real-git tests) needs the host git binary and runs in its own CI job, so neither `test` nor `check`
// runs it. `stress` (the 100-run concurrency tests of WP12, WP14 and WP19) still runs in `test` and `check`, and only PIT
// leaves it out (pitest, below): a mutant would multiply its cost by the mutant count.
val unitTestExcludedTags = listOf("whole-pack", "timing", "git-host")
// The link tests skip where the file system cannot make a symbolic link; -PrequireSymlinks=true, which CI passes
// (service.yml), makes them fail there instead, so a runner without links cannot stay green by skipping (#286).
val requireSymlinks = providers.gradleProperty("requireSymlinks").orElse("false")
val unitTestSystemProperties = mapOf(
    // PackSchemasTest checks that every schema file in the repository is bundled and listed in file-kinds.yaml;
    // PlantedFilesTest reads the repository's conformance files. Both find the repository by this property.
    "charter.repositoryRoot" to repositoryDir.asFile.absolutePath,
    // VocabularyEqualityTest checks that every load-path wire code is in the contract's code enums.
    "charter.contractSchemas" to repositoryDir.dir("contracts/charter/schemas").asFile.absolutePath,
    // ActivationConfigSchemaParityTest validates the ActivationConfig corpus against the canonical JSON Schema and its
    // OpenAPI mirror (#125).
    "charter.offering" to repositoryDir.dir("contracts/offering").asFile.absolutePath,
    "charter.requireSymlinks" to requireSymlinks.get(),
)
fun Task.readsTheUnitTestInputs() {
    mapOf(
        "packSchemaFiles" to "contracts/packs/schemas",
        // PlantedFilesTest and DivergenceRegisterTest read the register in both forms.
        "conformanceDocs" to "docs/conformance",
        "contractSchemas" to "contracts/charter/schemas",
        "offering" to "contracts/offering",
    ).forEach { (name, dir) ->
        inputs.dir(repositoryDir.dir(dir)).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName(name)
    }
    // ProjectCharterYamlSchemaTest validates the project's own charter.yaml against the charter schema (#131).
    inputs.file(repositoryDir.file(".kittify/charter/charter.yaml")).withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("projectCharterYaml")
    inputs.property("requireSymlinks", requireSymlinks)
    // SymbolicLinksTest fails a GitHub Actions run without -PrequireSymlinks=true; GITHUB_ACTIONS is an input, so a
    // cached result from a run without it never stands in for one with it.
    inputs.property("githubActions", providers.environmentVariable("GITHUB_ACTIONS").orElse(""))
}

tasks.test {
    useJUnitPlatform { excludeTags(*unitTestExcludedTags.toTypedArray()) }
    systemProperties(unitTestSystemProperties)
    readsTheUnitTestInputs()
}

// The nightly open-packs drift watch (ADR-0070, .github/workflows/open-packs-drift.yml): the driftTest source set runs
// every pack of a spec-kitty-open-packs checkout through the whole-pack round trip the gate uses (its helpers come from
// the test source set). `check` (so verifyAll) compiles and lints the source set but never runs it, and no required job
// runs the driftTest task; -PopenPacksDir points at the checkout (default: the sibling ../spec-kitty-open-packs).
val driftTestSourceSet = sourceSets.create("driftTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
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
    description = "Round-trips every pack of a spec-kitty-open-packs checkout by meaning (nightly drift watch); not run by check."
    testClassesDirs = driftTestSourceSet.output.classesDirs
    classpath = driftTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    systemProperty("charter.openPacksDir", openPacksDir.get())
    doNotTrackState("open-packs main moves; a cached pass says nothing about today's tree")
}

// The nightly mutation floor (#239; operator rulings of 2026-11-05). PIT mutates domain, application, infra.storage
// (infra.storage.config included), infra.fetch, and since WP13 infra.archive (the archive extractor, which carries the upload's
// safety rules; floor group infra.archive), but never the Spring wiring (application.config, infra.fetch.config) nor the
// inbound REST adapter (infra.api), and runs only the unit tests of those packages: the Spring-wired tests
// (*WiredTest, the only @SpringBootTest among them) and the tagged suites that `test` leaves out (
// whole-pack, timing, git-host, and stress for PIT alone) stay out of PIT too. pit-targets.init.gradle narrows this scope further for a run of
// one work package's classes.
val packsPackage = "ai.speckitty.charter.packs"
val mutatedPackages =
    listOf("domain", "application", "infra.storage", "infra.fetch", "infra.archive")
        .map { "$packsPackage.$it.*" }
        .toSet()
pitest {
    targetClasses = mutatedPackages
    excludedClasses = setOf(
        "$packsPackage.application.config.*",
        "$packsPackage.infra.fetch.config.*",
        "$packsPackage.infra.api.*",
    )
    targetTests = mutatedPackages
    excludedTestClasses = setOf("*WiredTest")
    // charter.mutation-conventions gives PIT's minion the tags `test` leaves out and its system properties (#461);
    // the inputs are declared below. PIT alone also leaves out `stress`: the 100-run concurrency tests run in `test`
    // but are too slow to repeat per mutant.
    excludedGroups = setOf("stress")
}
tasks.named("pitest") { readsTheUnitTestInputs() }

// Three floor groups, as the operator ruled them on 2026-11-05 (C-001): each at or below floor5(score - 3) of its first
// measured run. domain+application (92.7 %) and infra.storage (90.7 %) take 85; infra.fetch (77.3 %) takes 70. WP13 adds
// infra.archive on the same rule: 87.5 % (239 of 273) on its first narrowed run takes 80. infra.api is not mutated (it is
// excluded above and left out of mutatedPackages), so it has no floor.
mutationFloors {
    group("domain+application") {
        packages.addAll("$packsPackage.domain", "$packsPackage.application")
        excludes.add("$packsPackage.application.config")
        floor = 85
    }
    group("infra.storage") {
        packages.add("$packsPackage.infra.storage")
        floor = 85
    }
    group("infra.archive") {
        packages.add("$packsPackage.infra.archive")
        floor = 80
    }
    group("infra.fetch") {
        packages.add("$packsPackage.infra.fetch")
        excludes.add("$packsPackage.infra.fetch.config")
        floor = 70
    }
}
