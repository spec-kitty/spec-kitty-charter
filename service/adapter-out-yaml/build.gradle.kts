plugins {
    id("charter.java-conventions")
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
}

tasks.test { useJUnitPlatform { excludeTags("open-packs") } }
tasks.check { dependsOn(openPacksRoundTrip) }
