// The activations vertical slice (ADR-0006, ADR-0020): the activation register under ai.speckitty.charter.activations,
// with its domain, application, infra.api and infra.packs rings. It sits over the packs slice and asks it only through
// its own application port (AvailablePacks), which only infra.packs implements (docs/architecture/hexagonal-service.md).
plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
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
}

// The CPU-time budgets (tests tagged `timing`, #316, ADR-0057), report-only and never part of `test` or `check`, as in
// :packs: ActivationSelectionBudgetTest prints the p95 of a cascading select over five hundred artifacts (NFR-006).
// Run it in an invocation of its own:
//
//   ./gradlew :activations:timingBudgets
tasks.test {
    useJUnitPlatform { excludeTags("timing") }
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
