// The plain-Java core (domain and application modules): no framework, no YAML, no HTTP.
// The dependency rule itself is pinned by the ArchUnit test in bootstrap; this plugin
// only keeps such modules from declaring anything but project dependencies.
plugins {
    id("charter.java-base")
    id("info.solidsoft.pitest")
}

configurations.matching { it.name == "api" || it.name == "implementation" }.configureEach {
    withDependencies {
        val external = filterIsInstance<org.gradle.api.artifacts.ExternalModuleDependency>()
        check(external.isEmpty()) {
            "${project.path} is plain Java and may depend on other charter modules only, not on $external"
        }
    }
}

// Mutation testing of the core (spec FR-009): every module with these conventions is a PIT target, and only the
// module's own classes are mutated. It is its own task, `./gradlew pitest`, run by the nightly workflow; it is
// never part of `check`, so pull requests do not run it.
pitest {
    pitestVersion = catalog.version("pitest")
    junit5PluginVersion = catalog.version("pitest-junit5")
    targetClasses = setOf("ai.speckitty.charter.*")
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
}
