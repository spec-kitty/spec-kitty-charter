import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The licence files of the pack-doctor jar (#478, WP06 T025). Each library of the jar's classpath ships its licence and
 * notice files at `META-INF/LICENSE*`, `META-INF/NOTICE*`, a root `LICENSE` or `NOTICE`, or a third-party
 * `META-INF/<name>-LICENSE` (jackson-core's `FastDoubleParser-*` and `Schubfach-LICENSE`), or a `THIRD-PARTY` note (itu's). Merged as they are, the
 * libraries' files would collide, so this task moves them under `META-INF/licenses/<group>-<module>/` and the jar leaves
 * the originals out ([isLicence]). A library that ships none takes the text checked in at
 * `service/pack-doctor/licenses/<group>-<module>/` (its `LICENSE`, and a `SOURCE` naming where it was taken from).
 *
 * The task fails when a library ends up with no licence, and when a checked-in directory names no library of the
 * classpath, so a stale text never ships. Charter's own projects carry the project's `LICENSE`, written as
 * `META-INF/LICENSE`. It also writes `META-INF/pack-doctor/libraries.txt`, one `group:module:version` line per library,
 * which the jar test compares with `runtimeClasspath` (J5).
 *
 * Configuration-cache safe: it reads the configuration's resolved artifacts, never the configuration. The file also holds
 * the two small types the jar convention needs beside it, [ModuleVersions] and [PackDoctorJarTestArguments].
 */
@CacheableTask
abstract class PackDoctorLicences : DefaultTask() {

    /** The jar classpath's resolved artifacts, from `incoming.artifacts.resolvedArtifacts`. */
    @get:Internal
    abstract val artifacts: SetProperty<ResolvedArtifactResult>

    /** The same artifacts' files, so a changed library reruns the task. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val artifactFiles: ConfigurableFileCollection

    /** The ids of the artifacts, so a changed version with the same file name reruns the task. */
    @get:Input
    val artifactIds: List<String>
        get() = artifacts.get().map { it.id.componentIdentifier.displayName }.sorted()

    /** `service/pack-doctor/licenses`: the checked-in texts of the libraries that ship none. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val checkedIn: DirectoryProperty

    /** The repository's own `LICENSE`. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val projectLicence: RegularFileProperty

    /** Where the files are written, laid out as they sit in the jar. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun relocate() {
        val libraries = artifacts.get().sortedBy { it.id.componentIdentifier.displayName }.mapNotNull { artifact ->
            when (val id = artifact.id.componentIdentifier) {
                is ProjectComponentIdentifier -> null
                is ModuleComponentIdentifier -> Library(id.group, id.module, id.version, artifact.file)
                else -> throw GradleException("pack-doctor jar: cannot name the licence of ${id.displayName} (#478)")
            }
        }
        relocate(libraries, checkedIn.get().asFile, projectLicence.get().asFile, outputDirectory.get().asFile)
    }

    companion object {
        /** Where the libraries' licence directories sit in the jar. */
        const val LICENCES = "META-INF/licenses"

        /** The jar's list of the libraries it holds, `group:module:version` per line. */
        const val LIBRARIES = "META-INF/pack-doctor/libraries.txt"

        private val LICENCE = Regex(
            "(META-INF/)?(LICENSE|NOTICE|THIRD-PARTY)([.-][A-Za-z0-9.]+)?|META-INF/[A-Za-z0-9_.]+(-[A-Za-z0-9_.]+)*-LICENSE([.][A-Za-z]+)?",
        )

        /** Whether a library entry is a licence or notice file this task moves. */
        fun isLicence(path: String): Boolean = LICENCE.matches(path)

        /** The directory of one library under [LICENCES]. */
        fun directoryName(group: String, module: String): String = "$group-$module"
    }

    /** One library of the jar's classpath: its coordinates and its jar. */
    data class Library(val group: String, val module: String, val version: String, val file: File)
}

/**
 * Moves each library's licence files under [PackDoctorLicences.LICENCES] in [out], adds the checked-in texts of
 * [checkedInRoot] and the project's licence, and writes the library list; fails on a library with no licence and on a
 * checked-in directory that names no library. The task's action, apart from Gradle, so a unit test reaches its failure
 * branches.
 */
internal fun relocate(libraries: List<PackDoctorLicences.Library>, checkedInRoot: File, projectLicence: File, out: File) {
    out.deleteRecursively()
    val licences = File(out, PackDoctorLicences.LICENCES)
    licences.mkdirs()
    projectLicence.copyTo(File(out, "META-INF/LICENSE"))

    val available = checkedInRoot.listFiles { file -> file.isDirectory }.orEmpty().associateBy { it.name }
    val used = mutableSetOf<String>()
    val unlicensed = mutableListOf<String>()
    val listed = mutableListOf<String>()

    for (library in libraries) {
        val name = PackDoctorLicences.directoryName(library.group, library.module)
        val coordinates = "${library.group}:${library.module}:${library.version}"
        listed.add(coordinates)
        val target = File(licences, name)
        copyShipped(library.file, target)
        val texts = available[name]
        if (texts != null) {
            used.add(name)
            texts.listFiles { file -> file.isFile }.orEmpty().forEach { it.copyTo(File(target, it.name)) }
        }
        // A notice or a third-party note is not a licence: one file named LICENSE* must be there.
        if (target.listFiles().orEmpty().none { it.name.contains("LICENSE") }) {
            unlicensed.add(coordinates)
        }
    }

    val stale = available.keys - used
    if (unlicensed.isNotEmpty() || stale.isNotEmpty()) {
        throw GradleException(
            buildList {
                if (unlicensed.isNotEmpty()) {
                    add(
                        "pack-doctor jar: no licence file for ${unlicensed.sorted()}; check one in at " +
                            "service/pack-doctor/licenses/<group>-<module>/LICENSE with its SOURCE (#478)",
                    )
                }
                if (stale.isNotEmpty()) {
                    add("pack-doctor jar: service/pack-doctor/licenses holds ${stale.sorted()}, no library of the jar (#478)")
                }
            }.joinToString("; "),
        )
    }
    File(out, PackDoctorLicences.LIBRARIES).apply {
        parentFile.mkdirs()
        writeText(listed.sorted().joinToString("\n", postfix = "\n"))
    }
}

/** Copies the licence entries of one jar into [target], by their file names. */
private fun copyShipped(jar: File, target: File) {
    ZipFile(jar).use { zip ->
        for (entry in zip.entries().asSequence().filter { !it.isDirectory && PackDoctorLicences.isLicence(it.name) }.sortedBy { it.name }) {
            target.mkdirs()
            val file = File(target, entry.name.substringAfterLast('/'))
            if (file.exists()) {
                throw GradleException("pack-doctor jar: $jar holds two licence files named ${file.name} (#478)")
            }
            zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
    }
}


/**
 * The `group:module:version` of every library of a resolved classpath, one per line and sorted (#478, WP06 T023):
 * `runtimeClasspath`'s, which the jar test compares with the jar's own list, so an exclude that shifts a version fails
 * it.
 */
@CacheableTask
abstract class ModuleVersions : DefaultTask() {

    /** The classpath's resolved artifacts. */
    @get:Internal
    abstract val artifacts: SetProperty<ResolvedArtifactResult>

    /** The libraries, as the lines the task writes. */
    @get:Input
    val libraries: List<String>
        get() = artifacts.get().mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
            .map { "${it.group}:${it.module}:${it.version}" }
            .distinct()
            .sorted()

    /** The file written. */
    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun write() {
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(libraries.joinToString("\n", postfix = "\n"))
        }
    }
}

/**
 * The system properties `jarTest` passes to PackDoctorJarTest (#478, WP06 T026): the jar, its second build, the
 * runtimeClasspath list, the toolchain's `java` and the symbolic-link switch. Resolved when the test runs, never at
 * configuration. The paths are internal: the jars and the list are the task's file inputs, and the launcher is the
 * task's own toolchain.
 */
class PackDoctorJarTestArguments(
    @get:Internal val jar: org.gradle.api.provider.Provider<String>,
    @get:Internal val again: org.gradle.api.provider.Provider<String>,
    @get:Internal val runtimeVersions: org.gradle.api.provider.Provider<String>,
    @get:Internal val java: org.gradle.api.provider.Provider<String>,
    @get:Input val requireSymlinks: org.gradle.api.provider.Provider<String>,
) : org.gradle.process.CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf(
        "-Dpackdoctor.jar=${jar.get()}",
        "-Dpackdoctor.jarAgain=${again.get()}",
        "-Dpackdoctor.runtimeVersions=${runtimeVersions.get()}",
        "-Dpackdoctor.java=${java.get()}",
        "-Dcharter.requireSymlinks=${requireSymlinks.get()}",
    )
}
