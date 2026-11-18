// The charter service: a hexagonal Java service built against contracts/charter/openapi.yaml.
// Module roles and the dependency rule: docs/architecture/hexagonal-service.md.
// Repositories (ADR-0079). Maven Central rate-limits by address (HTTP 429), and the CI runners share addresses, so
// Google's mirror of Central comes first everywhere; it serves Central's bytes, and the strict SHA-256 dependency
// verification (gradle/verification-metadata.xml) checks every artifact whichever repository served it. Gradle moves to
// the next repository only on a 404, never on an error. Plugins: the mirror, then the plugin portal, for the markers and
// plugins only it hosts (the portal hands anything else to Central). Libraries: the mirror, then Central, for an
// artifact the mirror does not hold yet. build-logic/settings.gradle.kts declares the same orders.
pluginManagement {
    includeBuild("build-logic")
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        mavenCentral()
    }
}

rootProject.name = "spec-kitty-charter"

listOf(
    "kernel",
    "packs",
    "profiles",
    // The edge rings of the profiles slice (application, infra.api, infra.packs, infra.activations), apart from the
    // domain because packs depends on profiles (ADR-0037, amendment of 2026-11-09).
    "profiles-edge",
    // The routing vertical slice (ADR-0080): the routing catalog's domain and the ranker, plain Java. packs depends on it,
    // because packs.infra.storage binds the catalog (ADR-0037, amendment of 2026-11-18).
    "routing",
    // The edge rings of the routing slice (application, infra.api, infra.activations), apart from the domain because
    // packs depends on routing (ADR-0080; ADR-0037, amendment of 2026-11-18).
    "routing-edge",
    "missions",
    // The edge rings of the missions slice (application, infra.api, infra.packs, infra.activations), apart from the
    // domain because packs depends on missions (ADR-0037, amendment of 2026-11-09).
    "missions-edge",
    // The edge rings of the asset, skill and references reads (application, infra.api, infra.packs, infra.activations),
    // with no domain module: :activations names packs only from its own adapter (ADR-0037, amendment of 2026-11-13).
    "artifacts-edge",
    "activations",
    // Every pack check as a kernel ruleset, without Spring, which the packs slice and pack-check run (ADR-0075).
    "pack-validation",
    // The standalone pack-check CLI over pack-validation, its own jar on the service-v* release (ADR-0075).
    "pack-check",
    "charter-api",
    // The service-wide HTTP transport shared by every slice's inbound adapter (problems, refusals, body guard).
    "web-transport",
    "deployable",
    // Aggregation only, no sources: one Javadoc site and one coverage report over the hand-written service modules.
    "charter-reports",
).forEach { name ->
    include(name)
    project(":$name").projectDir = file("service/$name")
}

// The charter contract's own checks: lint, planted fixtures, bundle and breaking-change check
// (contracts/build.gradle.kts), and the Java closure checker (contracts/src). Part of verifyAll through its check task.
include("contracts")

// The Java client generated from the published contract bundle (#218): client-java/, built by charter.client-java.
include("client-java")
