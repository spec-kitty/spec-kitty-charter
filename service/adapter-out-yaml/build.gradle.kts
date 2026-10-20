plugins {
    id("charter.java-base")
    id("charter.hand-written")
}

dependencies {
    api(project(":charter-domain"))
    implementation(catalog.lib("snakeyaml-engine"))
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

tasks.test { useJUnitPlatform { excludeTags("open-packs") } }
tasks.check { dependsOn(openPacksRoundTrip) }
