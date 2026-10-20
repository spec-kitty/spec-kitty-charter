plugins {
    id("charter.java-base")
    id("charter.hand-written")
}

dependencies {
    api(project(":charter-domain"))
    implementation(catalog.lib("snakeyaml-engine"))
    // networknt json-schema-validator validates pack files against contracts/packs/schemas (ADR-0017).
    // The Spring Boot BOM is used here only to pin Jackson 3 and slf4j to the versions the service runs;
    // networknt's own Jackson (3.2.x) and slf4j pins are excluded so the service classpath does not move.
    implementation(platform(catalog.lib("spring-boot-bom")))
    implementation(catalog.lib("json-schema-validator")) {
        exclude(group = "tools.jackson.core")
        exclude(group = "tools.jackson.dataformat", module = "jackson-dataformat-yaml")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(catalog.lib("jackson-databind"))
    implementation(catalog.lib("slf4j-api"))
}

// The pack schemas and the file-kind table are bundled as main resources under packs/, so that
// classpath:packs/schemas/<kind>.schema.json and classpath:packs/file-kinds.yaml resolve offline. The copy
// is a declared input of processResources, so a schema change re-runs the tests.
val packContracts = rootProject.layout.projectDirectory.dir("contracts/packs")
tasks.processResources {
    from(packContracts) {
        include("schemas/*.schema.json", "file-kinds.yaml")
        into("packs")
    }
}

// The open-packs round-trip: every YAML file of spec-kitty-open-packs must survive the file-based
// adapter unchanged (operator decision, 2026-10-18). Point -PopenPacksDir at a checkout; by default
// the sibling directory ../spec-kitty-open-packs. A missing checkout fails the task, it never skips.
val openPacksDir = providers.gradleProperty("openPacksDir")
    .orElse(rootProject.layout.projectDirectory.dir("../spec-kitty-open-packs").asFile.absolutePath)

// The pin: gradle.properties (openPacksRef) is its single source of truth, read here and by CI. It is validated
// when the task runs, never at configuration time, and is a task input so a new pin is never answered from the cache.
val openPacksRef = providers.gradleProperty("openPacksRef")

val testSourceSet = sourceSets.test.get()
val openPacksRoundTrip = tasks.register<Test>("openPacksRoundTrip") {
    group = "verification"
    description = "Round-trips every spec-kitty-open-packs YAML file through the file-based adapter."
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("open-packs") }
    val dir = openPacksDir
    inputs.dir(dir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("openPacks")
    systemProperty("charter.openPacksDir", dir.get())
    val ref = openPacksRef
    inputs.property("openPacksRef", ref.orElse(""))
    doFirst {
        val pin = ref.orNull
        check(pin != null && Regex("[0-9a-f]{40}").matches(pin)) {
            "openPacksRef in gradle.properties must be 40 lowercase hex characters, but is '${pin ?: ""}'"
        }
        systemProperty("charter.openPacksRef", pin)
    }
}

tasks.test {
    useJUnitPlatform { excludeTags("open-packs") }
    // PackSchemasTest checks that VENDORED.md names every bundled schema, reading it from the repository.
    val vendored = packContracts.file("VENDORED.md")
    inputs.file(vendored).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("vendoredMd")
    systemProperty("charter.vendoredMd", vendored.asFile.absolutePath)
}
tasks.check { dependsOn(openPacksRoundTrip) }
