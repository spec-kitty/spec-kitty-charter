// The plain-Java core (domain and application modules): no framework, no YAML, no HTTP.
// The dependency rule itself is pinned by the ArchUnit test in deployable; this plugin
// only keeps such modules from declaring anything but project dependencies.
plugins {
    id("charter.java-base")
    // Mutation testing of the core (spec FR-009): every module with these conventions is a PIT target, held to the
    // floors it declares in `mutationFloors` (#239).
    id("charter.mutation-conventions")
}

configurations.matching { it.name == "api" || it.name == "implementation" }.configureEach {
    withDependencies {
        val external = filterIsInstance<org.gradle.api.artifacts.ExternalModuleDependency>()
        check(external.isEmpty()) {
            "${project.path} is plain Java and may depend on other charter modules only, not on $external"
        }
    }
}
