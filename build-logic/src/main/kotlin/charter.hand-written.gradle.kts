// Every hand-written Java module: the quality gates. Applied beside a base plugin (`charter.java-base` or one
// built on it) in each hand-written module, and never in charter-api, whose sources are generated (spec C-005).
// Aggregations select projects by `plugins.hasPlugin("charter.hand-written")`, never by a module list.
//
// The one register of exceptions to these gates is config/checkstyle/suppressions.xml (rule, file, reason,
// sunset); no gate is relaxed anywhere else.
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    checkstyle
    jacoco
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

tasks.withType<JavaCompile>().configureEach {
    // Warnings are errors; processing and serial are off because no hand-written code has a processor or a
    // serialized form, and their warnings would only name generated or framework types.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial", "-Werror"))
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

// Error Prone on main and test. NullAway runs on main only (operator ruling 2026-10-18) and only on code under
// @NullMarked (OnlyNullMarked), so a package that is not annotated, such as the generated contract, is outside
// its scope by construction.
dependencies {
    errorprone(catalog.lib("errorprone-core"))
    errorprone(catalog.lib("nullaway"))
    compileOnly(catalog.lib("jspecify"))
    testCompileOnly(catalog.lib("jspecify"))
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode = true
    }
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
