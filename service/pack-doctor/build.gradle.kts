// The pack-doctor CLI (ADR-0075 decision 9, #478): a thin main over pack-validation, with no Spring and no CLI framework,
// that checks a set of packs and writes the contract's problem JSON. Its main, PackDoctorMain, is the composition root:
// it plugs the packs slice's file-system reader (FileSystemPackInspector) and the production round trip (PackRoundTrip)
// into pack-validation's pack-set check, so it names :packs too. The jar that ships on the service-v* release, its
// licences and its jar test come from the build-logic convention charter.pack-doctor-jar (#478, WP06), not from here.
//
// It may depend on pack-validation, :packs and a JSON writer only, pinned below. HexagonalArchitectureTest's
// PACK_VALIDATION_IS_FRAMEWORK_FREE pins the classes it uses: no Spring, HTTP, commit store or lock, and only
// PackDoctorMain may name the two adapters.
plugins {
    id("charter.java-base")
    id("charter.hand-written")
    // The weekly mutation floor (#239), as pack-validation's.
    id("charter.mutation-conventions")
    // The runnable Spring-free jar, its licences and its jar test (#478, WP06).
    id("charter.pack-doctor-jar")
}

ModuleDependencyAllowlist(
    projects = setOf(":pack-validation", ":packs"),
    modules = setOf(
        // The JSON writer of the report.
        "tools.jackson.core:jackson-databind",
    ),
).enforceOn(project)

dependencies {
    implementation(project(":pack-validation"))
    // The file-system reader and the production round trip the main plugs in (ADR-0075 as amended 2026-11-13, #553).
    implementation(project(":packs"))
    // Its version comes from the BOM pack-validation exports, as :packs' does.
    implementation(catalog.lib("jackson-databind"))

    // The charter-owned example packs (ADR-0070) and the planted packs the CLI's tests run over.
    testImplementation(testFixtures(project(":packs")))
}

// The weekly mutation floor (#239; ADR-0062, amendment of 2026-11-19): one group for the module, the CLI's arguments,
// config root, problems, report and main. Measured on #478's WP06 base (WP04 approved): 139 of 149 mutants detected
// (93.2 %, 5 survived, 5 uncovered), so floor5(score - 3) is 90, ADR-0062 decision 3's rule, and the margin is 4 mutants.
// jarTest's tests (tag `jar`) never run under PIT. Raised by PR once the score clears the floor by 5.
mutationFloors {
    group("pack-doctor") {
        packages.add("ai.speckitty.charter.packdoctor")
        floor = 90
    }
}

// The link plants skip where the file system cannot make a symbolic link; -PrequireSymlinks=true, which CI passes, makes
// them fail there instead (#286), as in :packs and :pack-validation.
val requireSymlinks = providers.gradleProperty("requireSymlinks").orElse("false")

tasks.test {
    inputs.property("requireSymlinks", requireSymlinks)
    systemProperty("charter.requireSymlinks", requireSymlinks.get())
    // PlantedParityTest reads the two golden files of its neighbours by path (NFR-003), so a changed golden file reruns
    // it rather than leaving a stale pass in the build cache (WP06 review).
    inputs.file(layout.projectDirectory.file("../packs/src/test/resources/golden/surfaces.json"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("surfacesGolden")
    inputs.file(layout.projectDirectory.file("../pack-validation/src/test/resources/golden/validate.json"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("validateGolden")
}

/**
 * Hands the repository root to NoPackCheckSurfaceTest's scan, which reads the tracked files for the old tool's name
 * (FR-007, plan D11). The candidate lines (every tracked line that holds a spelling of it, history left out) are an
 * input, so a new or changed mention reruns the scan rather than leaving a stale pass in the build cache.
 */
abstract class SurfaceScanArguments : CommandLineArgumentProvider {
    @get:Internal abstract val root: DirectoryProperty

    /** The tracked paths and the candidate lines: content, not the index's stat data. */
    @get:Input abstract val candidates: Property<String>

    override fun asArguments(): List<String> = listOf("-Dcharter.test.repo-root=${root.get().asFile.absolutePath}")
}

// The scan (tag `surface`) reads the repository through git, so it runs in surfaceTest alone, inside `check` and so
// `verifyAll`, never in `test` or PIT: PIT's minions take `test`'s excluded tags and its system properties, not its
// argument providers, so the scan would fail there unmutated and leave the module with no report (#461).
tasks.test {
    useJUnitPlatform { excludeTags("surface") }
}

val surfaceTestSourceSet = sourceSets.test.get()
val surfaceTest = tasks.register<Test>("surfaceTest") {
    group = "verification"
    description = "Scans the tracked files for the checker's old name (tag surface; FR-007)."
    testClassesDirs = surfaceTestSourceSet.output.classesDirs
    classpath = surfaceTestSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("surface") }
    shouldRunAfter(tasks.test)
    val repository = rootProject.layout.projectDirectory
    val git = listOf("git", "-C", repository.asFile.absolutePath)
    jvmArgumentProviders.add(objects.newInstance<SurfaceScanArguments>().apply {
        root.set(repository)
        candidates.set(
            providers.exec {
                commandLine(git + listOf("ls-files"))
                isIgnoreExitValue = true
            }.standardOutput.asText.zip(
                providers.exec {
                    commandLine(
                        git + listOf("grep", "-n", "-I", "-i", "-E", "pack.?check", "--", ".", ":!kitty-specs", ":!kitty-ops"),
                    )
                    isIgnoreExitValue = true
                }.standardOutput.asText,
            ) { files, lines -> files + lines },
        )
    })
}

tasks.check { dependsOn(surfaceTest) }
