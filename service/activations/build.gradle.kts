// The activations vertical slice (ADR-0006, ADR-0020): the activation register under ai.speckitty.charter.activations,
// with its domain, application, infra.api and infra.packs rings. It sits over the packs slice and asks it only through
// its own application port (AvailablePacks), which only infra.packs implements (docs/architecture/hexagonal-service.md).
plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
    // The nightly mutation floor (#239, ADR-0062 as amended 2026-11-06).
    id("charter.mutation-conventions")
}

dependencies {
    api(project(":kernel"))
    // infra.packs: the one adapter that names the packs slice, and only its application ring.
    implementation(project(":packs"))

    // infra.api: the inbound REST adapter implements the generated activation contract interface.
    api(project(":charter-api"))
    // The service-wide transport: problem rendering, refusal mapping and the JSON body guard.
    implementation(project(":web-transport"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))
    implementation(catalog.lib("jackson-databind"))

    testImplementation(catalog.lib("spring-boot-starter-test"))
    // The standalone MockMvc tests of infra.api validate the generated interface's constraints, as the service does.
    testImplementation(catalog.lib("spring-boot-starter-validation"))
    testImplementation(catalog.lib("archunit"))
    // ActivationWireTokenParityTest reads the contract enums that no generated model carries (#430).
    testImplementation(catalog.lib("snakeyaml-engine"))
}

// The CPU-time budgets (tests tagged `timing`, #316, ADR-0057), report-only and never part of `test` or `check`, as in
// :packs: ActivationSelectionBudgetTest prints the p95 of a cascading select over five hundred artifacts (NFR-006).
// Run it in an invocation of its own:
//
//   ./gradlew :activations:timingBudgets
tasks.test {
    useJUnitPlatform { excludeTags("timing") }
    // ActivationWireTokenParityTest reads the contract's CascadeKind enum, which no generated model carries (#430).
    val contractSchemas = rootProject.layout.projectDirectory.dir("contracts/charter/schemas")
    inputs.dir(contractSchemas).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("contractSchemas")
    systemProperty("charter.contractSchemas", contractSchemas.asFile.absolutePath)
}

tasks.register<Test>("timingBudgets") {
    group = "verification"
    description = "The CPU-time budgets (tests tagged timing), run alone and report-only; not part of check (#316)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("timing") }
    maxParallelForks = 1
    setForkEvery(1)
    mustRunAfter(tasks.test)
    doNotTrackState("a budget measured on another run says nothing about this one (#316)")
    testLogging { showStandardStreams = true }
}

// The nightly mutation floor (#239): one group for the whole module, ruled at 85 by the operator on 2026-11-06
// (a trial run scored 91.4 %); raised by PR once the score clears it by 5.
mutationFloors {
    group("activations") {
        packages.add("ai.speckitty.charter.activations")
        floor = 85
    }
}

// PIT runs the unit tests as `test` does: the timing budgets above stay out of it.
// ActivationWireTokenParityTest reads the contract schemas through a system property that `tasks.test` sets above; PIT's
// minion JVM does not inherit it, so without it the mutation run (mutation-testing.yml, then nightly) failed on a green suite.
pitest {
    excludedGroups = setOf("timing")
    jvmArgs.add(
        rootProject.layout.projectDirectory.dir("contracts/charter/schemas").asFile.absolutePath
            .let { "-Dcharter.contractSchemas=$it" },
    )
}

// The schemas are an input of the mutation run as they are of `test`, so a changed enum re-runs it.
tasks.named("pitest") {
    inputs.dir(rootProject.layout.projectDirectory.dir("contracts/charter/schemas"))
        .withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("contractSchemas")
}
