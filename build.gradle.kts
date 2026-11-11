// Root project: aggregation only. Module builds live under service/, conventions under build-logic/.
plugins {
    // checkServiceReleaseTag, the publishing gate of a service release (ADR-0046).
    id("charter.service-release")
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs every module's check, including the contract validation and the example-pack round-trip."
    dependsOn(subprojects.map { "${it.path}:check" })
    // The convention plugins' own tests, the contract normalizer's among them.
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}

// Syncs packs/internal and packs/qa from the spec-kitty-open-packs commit pinned by openPacksRef (gradle.properties) and
// verifies them against packs/open-packs.lock.yaml. They are not committed: run it once per worktree. The work is
// scripts/packs/sync_packs.py, which CI runs through this task as well. Source: -PopenPacksDir (absolute, or relative to
// the repository root), else the sibling ../spec-kitty-open-packs, read at the pin and never from its working tree.
//   ./gradlew syncPacks                           sync and verify
//   ./gradlew syncPacks -PupdatePacksLock=true    after a pin bump: also rewrite the lockfile
//   ./gradlew syncPacks -PcheckPacks=true         only verify the synced trees against the lockfile
tasks.register<Exec>("syncPacks") {
    group = "verification"
    description = "Syncs packs/internal and packs/qa from the pinned open-packs commit and verifies their digests."
    val script = layout.projectDirectory.file("scripts/packs/sync_packs.py")
    // A bare `-Pflag` is Gradle's usual way to say yes; `=false` says no; anything else is a typo, not a silent no.
    fun booleanProperty(name: String) = providers.gradleProperty(name)
        .map { raw ->
            when (raw.trim().lowercase()) {
                "", "true" -> true
                "false" -> false
                else -> throw GradleException("-$name takes true or false (or is given bare), but is '$raw'")
            }
        }
        .orElse(false)
    val checkOnly = booleanProperty("checkPacks")
    val updateLock = booleanProperty("updatePacksLock")
    val openPacksDir = providers.gradleProperty("openPacksDir")
    workingDir = layout.projectDirectory.asFile
    environment.remove("PYTHONPATH")
    executable = "python3"
    argumentProviders.add(CommandLineArgumentProvider {
        buildList {
            add(script.asFile.path)
            if (checkOnly.get()) add("--check")
            if (updateLock.get()) add("--update-lock")
            openPacksDir.orNull?.let { addAll(listOf("--open-packs-dir", it)) }
        }
    })
}
