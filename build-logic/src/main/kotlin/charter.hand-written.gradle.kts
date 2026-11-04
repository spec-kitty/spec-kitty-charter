// Every hand-written Java module: the quality gates. Applied beside a base plugin (`charter.java-base` or one
// built on it) in each hand-written module, and never in charter-api, whose sources are generated (spec C-005).
// Not every project with this plugin is part of the service: :contracts applies it too (the contract's own
// Java checker). So the service aggregations (the Javadoc site and the coverage report in service/charter-reports)
// declare their module set by name, in `serviceModules` there. A new hand-written module under service/ must be
// added to that list; the charter-reports build fails, naming it, until it is.
//
// The one register of exceptions to these gates is config/checkstyle/suppressions.xml (rule, file, reason,
// sunset); no gate is relaxed anywhere else for hand-written code. Generated sources stay outside them by
// construction: charter-api, and the AOT source sets of charter.native-image.
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    checkstyle
    jacoco
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

// The main sources as an outgoing variant (Category=DOCUMENTATION, DocsType=SOURCES), which the aggregated
// Javadoc in service/charter-reports resolves instead of reaching into this project's source sets.
java { withSourcesJar() }

tasks.withType<JavaCompile>().configureEach {
    // Every javac lint category, and warnings are errors.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Formatting is palantir-java-format's alone: it also removes unused imports and orders them, so no second
// formatter is needed, and Checkstyle carries no layout rule that could disagree with it. spotlessCheck is
// part of `check`.
spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        palantirJavaFormat(catalog.version("palantir-java-format"))
        formatAnnotations()
    }
}

// Semantic style rules on main and test; any violation fails the build.
checkstyle {
    toolVersion = catalog.version("checkstyle")
    configDirectory = rootProject.layout.projectDirectory.dir("config/checkstyle")
    isIgnoreFailures = false
    maxWarnings = 0
    maxErrors = 0
}

// The register is shrink-only (ADR-0056). Checkstyle never reports a suppression that suppresses nothing, so a probe
// runs the same rules with the register switched off over the register's files in this module, and
// checkCheckstyleRegister (CheckstyleRegister) requires each entry here to be live: every rule it lists still fires on
// its method. It also checks each entry's comment fields, that its file exists, and the header's Size. Part of `check`.
val checkstyleConfigDir = rootProject.layout.projectDirectory.dir("config/checkstyle")
val registerFile = checkstyleConfigDir.file("suppressions.xml")
val register = CheckstyleRegister.parse(providers.fileContents(registerFile).asText.get())
val repositoryRoot = rootProject.layout.projectDirectory.asFile
val moduleDir = layout.projectDirectory.asFile
// The `files` patterns of the entries whose file lies in this module; the probe scans only the files they match.
val registerPatterns = register.entries
    .filter { entry -> entry.file?.let { repositoryRoot.resolve(it).canonicalFile.startsWith(moduleDir.canonicalFile) } == true }
    .map { it.fileRegex }

val checkstyleRegisterProbeConfig = tasks.register("checkstyleRegisterProbeConfig") {
    description = "Writes the Checkstyle configuration without its suppression filters, for the register probe."
    val source = checkstyleConfigDir.file("checkstyle.xml")
    val probe = layout.buildDirectory.file("checkstyle-register/probe.xml")
    inputs.file(source).withPropertyName("config").withPathSensitivity(PathSensitivity.NONE)
    outputs.file(probe).withPropertyName("probe")
    outputs.cacheIf { true }
    doLast {
        var text = source.asFile.readText()
        for (filter in listOf("SuppressionFilter", "SuppressionXpathFilter")) {
            val module = Regex("""\s*<module name="$filter"\s*(?:/>|>.*?</module>)""", RegexOption.DOT_MATCHES_ALL)
            val found = module.findAll(text).count()
            if (found != 1) {
                throw GradleException("config/checkstyle/checkstyle.xml holds $found $filter modules, not one: the register probe cannot switch it off")
            }
            text = module.replace(text, "")
        }
        probe.get().asFile.apply { parentFile.mkdirs() }.writeText(text)
    }
}

val probeSourceSets = listOf("main", "test")
val probeReports = probeSourceSets.map { set -> layout.buildDirectory.file("checkstyle-register/probe-$set.xml") }
val checkstyleRegisterProbes = probeSourceSets.zip(probeReports).map { (set, report) ->
    tasks.register<Checkstyle>("checkstyleRegisterProbe${set.replaceFirstChar { it.uppercase() }}") {
        description = "Runs Checkstyle on the register's $set files with the register switched off."
        config = resources.text.fromFile(checkstyleRegisterProbeConfig)
        configDirectory = checkstyleConfigDir
        val patterns = registerPatterns
        source(sourceSets.named(set).map { sourceSet ->
            sourceSet.java.matching {
                include { element -> element.isDirectory || patterns.any { Regex(it).containsMatchIn(element.file.absolutePath) } }
            }
        })
        classpath = files()
        isIgnoreFailures = true
        isShowViolations = false
        maxWarnings = Int.MAX_VALUE
        maxErrors = Int.MAX_VALUE
        reports {
            xml.required = true
            xml.outputLocation = report
            html.required = false
            sarif.required = false
        }
    }
}

val checkCheckstyleRegister = tasks.register("checkCheckstyleRegister") {
    group = "verification"
    description = "Fails on a stale entry, a missing field or file, or a wrong Size in config/checkstyle/suppressions.xml."
    val reports = probeReports
    val registeredFiles = register.entries.mapNotNull { it.file }.distinct().map { repositoryRoot.resolve(it) }
    val registerXml = registerFile
    val root = repositoryRoot
    val module = moduleDir
    val marker = layout.buildDirectory.file("checkstyle-register/verified.txt")
    inputs.file(registerFile).withPropertyName("register").withPathSensitivity(PathSensitivity.NONE)
    inputs.files(checkstyleRegisterProbes).withPropertyName("probeReports").withPathSensitivity(PathSensitivity.NONE)
    // The probed sources, whose declarations the check reads to tie each finding to its method.
    inputs.files(checkstyleRegisterProbes.map { probe -> probe.map { it.source } })
        .withPropertyName("probedSources").withPathSensitivity(PathSensitivity.RELATIVE)
    // The files the entries name, existing or not, so that a file appearing or going reruns the check.
    inputs.files(registeredFiles).withPropertyName("registeredFiles").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("moduleDir", moduleDir.relativeTo(repositoryRoot).invariantSeparatorsPath)
    outputs.file(marker).withPropertyName("verified")
    outputs.cacheIf { true }
    doLast {
        val parsed = CheckstyleRegister.parse(registerXml.asFile.readText())
        val violations = reports.map { it.get().asFile }.filter { it.isFile }
            .flatMap { CheckstyleRegister.parseReport(it.readText()) }
        val sources = mutableMapOf<File, List<String>>()
        val findings = CheckstyleRegister.verify(
            parsed, violations, { file, n -> sources.getOrPut(file) { file.readLines() }.getOrNull(n - 1) }, root, module,
        )
        if (findings.isNotEmpty()) {
            throw GradleException(
                "config/checkstyle/suppressions.xml is out of date (ADR-0056):\n" + findings.joinToString("\n") { "  $it" },
            )
        }
        val live = parsed.entries.count { entry -> entry.file?.let { root.resolve(it).canonicalFile.startsWith(module.canonicalFile) } == true }
        marker.get().asFile.apply { parentFile.mkdirs() }
            .writeText("${parsed.entries.size} entries in order, $live live in this module (${violations.size} probe findings)\n")
    }
}

tasks.named("check") {
    dependsOn(checkCheckstyleRegister)
}

// Error Prone on main and test. NullAway runs on main only (operator ruling 2026-10-18) and only on code under
// @NullMarked (OnlyNullMarked), so a package that is not annotated, such as the generated contract, is outside
// its scope by construction.
dependencies {
    errorprone(catalog.lib("errorprone-core"))
    errorprone(catalog.lib("nullaway"))
    compileOnly(catalog.lib("jspecify"))
    testCompileOnly(catalog.lib("jspecify"))
}

tasks.named<JavaCompile>("compileJava") {
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone {
        disable("NullAway")
    }
}

// Javadoc of main with every doclint group on and warnings as errors: every public type and member is
// documented. Part of `check`.
tasks.named<Javadoc>("javadoc") {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
        addBooleanOption("notimestamp", true)
        // Report every finding, not the first hundred.
        addStringOption("Xmaxwarns", "10000")
    }
}

// Coverage per module, XML for tools and HTML for readers; no threshold (FR-008 asks for a report).
jacoco {
    toolVersion = catalog.version("jacoco")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.named("check") {
    dependsOn(tasks.named("javadoc"), tasks.named("jacocoTestReport"))
}

// Mockito where a hand-written fake is not worth it. It is loaded as a Java agent rather than self-attaching,
// which the JDK warns about and will refuse.
val mockitoAgent = configurations.create("mockitoAgent") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    testImplementation(catalog.lib("mockito-core"))
    testImplementation(catalog.lib("mockito-junit-jupiter"))
    mockitoAgent(catalog.lib("mockito-core"))
}

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(JavaAgentArgument(mockitoAgent))
}

// PIT runs the tests in its own JVMs, which need the agent too.
pluginManager.withPlugin("info.solidsoft.pitest") {
    extensions.configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
        jvmArgs.add(mockitoAgent.elements.map { jars -> "-javaagent:" + jars.single().asFile.absolutePath })
    }
}
