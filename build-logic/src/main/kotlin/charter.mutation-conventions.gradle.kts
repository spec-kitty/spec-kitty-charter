// Mutation testing with PIT and its floor (spec FR-009, #239). `./gradlew pitest` is its own task, run by
// mutation-testing.yml; it is never part of `check`, so pull requests do not run it. A module declares its floors:
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
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions

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
fun declaredGroups() = mutationFloors.groups.map { it.toGroup() }

// A shard of the module's PIT run (#461): -PmutationGroups=g1,g2 mutates and scores only those floor groups, and
// writes build/reports/pitest-<shard> and build/reports/mutation-floors/floors-<shard>.json, so the mutation workflow can run one
// module's groups on several runners. Each group's score is the one a whole-module run gives. Unknown names fail the
// run (MutationFloors.shard). Without the property, the module runs whole.
val requestedGroups = providers.gradleProperty("mutationGroups")
// The module's own PIT scope, taken before a shard narrows it (below).
var moduleTargets: Set<String> = emptySet()
// Resolved only for a module whose task runs: -PmutationGroups reaches every project's configuration, and a module
// the run does not shard must not be refused. A package PIT mutates that no group holds would be dropped by the shard.
val shard = requestedGroups.map {
    val groups = declaredGroups()
    MutationFloors.requireTargetsHeld(project.path, moduleTargets, groups)
    MutationFloors.shard(project.path, it, groups)
}

val checkMutationFloors = tasks.register<CheckMutationFloors>("checkMutationFloors") {
    group = "verification"
    description = "Fails when a mutationFloors group's PIT mutation score is below its floor (#239)."
    report = pitest.reportDir.file("mutations.xml")
    summary = layout.buildDirectory.file(
        shard.map { "reports/mutation-floors/floors-${it.name}.json" }.orElse("reports/mutation-floors/floors.json"),
    )
    projectPath = project.path
    groups = shard.map { it.groups }.orElse(provider { declaredGroups() })
    // The report must come from this run's pitest when both are requested, never from an older one.
    mustRunAfter(tasks.named("pitest"))
}

// After the module's own pitest block, which sets its scope: a shard narrows that scope to its groups.
afterEvaluate {
    // The module's own scope, before a shard narrows it: every package PIT mutates must sit in a floor group, or a shard
    // would drop it and no floor would score it (checkMutationFloors refuses a mutant no group holds on any run).
    moduleTargets = pitest.targetClasses.get().toSet()
    // PIT runs the module's unit tests in its own minion JVMs, so they get what `test` gives them: its system
    // properties and the JUnit tags it leaves out. A module never copies them by hand, so it cannot forget one; a test
    // that `test` passes and PIT fails unmutated leaves the module with no report (#461). A module may still add tags
    // PIT alone leaves out (`stress` in :packs). After the module's own pitest block, so an assignment there cannot
    // replace them. Read through `provider {}`, never `unitTests.map {}`: a mapped task provider carries `test` as its
    // producer, so `pitest` would depend on `test` and run the whole unit suite first, and a red `test` would leave the
    // module with no report again.
    val unitTests = tasks.named<Test>("test")
    pitest.jvmArgs.addAll(provider { MutationFloors.minionSystemProperties(project.path, unitTests.get().systemProperties) })
    pitest.excludedGroups.addAll(
        provider { (unitTests.get().options as? JUnitPlatformOptions)?.excludeTags.orEmpty() },
    )
    if (requestedGroups.isPresent) {
        pitest.targetClasses.set(shard.map { it.targetClasses })
        pitest.excludedClasses.addAll(shard.map { it.excludedClasses })
        pitest.reportDir.set(layout.buildDirectory.dir(shard.map { "reports/pitest-${it.name}" }))
    }
}

tasks.named("pitest") {
    finalizedBy(checkMutationFloors)
    // A pitest run that fails before writing its report must not leave the last run's report to be scored.
    val mutationsXml = pitest.reportDir.file("mutations.xml")
    doFirst { mutationsXml.get().asFile.delete() }
}
