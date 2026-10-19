// The charter service: a hexagonal Java service built against contracts/charter/openapi.yaml.
// Module roles and the dependency rule: docs/architecture/hexagonal-service.md.
pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "spec-kitty-charter"

listOf(
    "charter-domain",
    "charter-read-app",
    "charter-api",
    "adapter-in-rest",
    "adapter-out-yaml",
    "charter-service",
).forEach { name ->
    include(name)
    project(":$name").projectDir = file("service/$name")
}

// The charter contract's own checks: lint, planted fixtures, bundle and breaking-change check
// (contracts/build.gradle.kts), and the Java closure checker (contracts/src). Part of verifyAll through its check task.
include("contracts")
