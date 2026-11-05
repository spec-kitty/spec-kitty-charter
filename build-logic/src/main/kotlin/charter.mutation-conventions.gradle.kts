// Mutation testing with PIT and its nightly floor (spec FR-009, #239). `./gradlew pitest` is its own task, run by the
// nightly workflow; it is never part of `check`, so pull requests do not run it. A module declares its floors:
//
//   mutationFloors {
//       group("kernel") {
//           packages.add("ai.speckitty.charter.kernel")
//           floor = 65
//       }
//   }
//
// `pitest` is finalized by `checkMutationFloors`, which reads build/reports/pitest/mutations.xml, writes
// build/reports/mutation-floors/floors.json (its own directory, so PIT's report directory stays pitest's alone and
// pitest stays cacheable) and fails when a group's mutation score is below its floor (MutationFloors.kt has the
// score's definition).
// The finalizer runs even when another module fails, so `./gradlew pitest --continue` reports every module.
plugins {
    java
    id("info.solidsoft.pitest")
}

// Only the module's own classes are mutated, with PIT's default mutators. Threads: a GitHub-hosted ubuntu runner has
// four cores; a larger machine still runs at most four PIT workers per module, as modules run in parallel too.
pitest {
    pitestVersion = catalog.version("pitest")
    junit5PluginVersion = catalog.version("pitest-junit5")
    targetClasses = setOf("ai.speckitty.charter.*")
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
    threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
}

val mutationFloors = extensions.create<MutationFloorsExtension>("mutationFloors")

val checkMutationFloors = tasks.register<CheckMutationFloors>("checkMutationFloors") {
    group = "verification"
    description = "Fails when a mutationFloors group's PIT mutation score is below its floor (#239)."
    report = pitest.reportDir.file("mutations.xml")
    summary = layout.buildDirectory.file("reports/mutation-floors/floors.json")
    projectPath = project.path
    groups = provider { mutationFloors.groups.map { it.toGroup() } }
    // The report must come from this run's pitest when both are requested, never from an older one.
    mustRunAfter(tasks.named("pitest"))
}

tasks.named("pitest") {
    finalizedBy(checkMutationFloors)
    // A pitest run that fails before writing its report must not leave the last run's report to be scored.
    val mutationsXml = pitest.reportDir.file("mutations.xml")
    doFirst { mutationsXml.get().asFile.delete() }
}
