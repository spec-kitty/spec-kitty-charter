import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the modules under the nightly mutation floor, one directory per line relative to the repository root (#388):
 * every project that applies `charter.mutation-conventions`, as the build sees it. The nightly passes the file to
 * `scripts/ci/mutation_summary.py`, so a module whose PIT run fails before it writes `floors.json` still gets a
 * "no report" row, without a list kept by hand beside the build.
 */
@DisableCachingByDefault(because = "Writes one short file from configuration-time facts")
abstract class WriteMutationModules : DefaultTask() {

    /** The module directories, relative to the repository root, with `/` separators. */
    @get:Input
    abstract val modules: ListProperty<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun write() {
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(modules.get().sorted().joinToString("") { "$it\n" })
    }
}
