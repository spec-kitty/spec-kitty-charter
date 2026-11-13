import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the modules under the mutation floor, one per line (#388): every project that applies
 * `charter.mutation-conventions`, as the build sees it. A line is the module's directory relative to the repository
 * root, then its floor group names, separated by tabs (#461). The mutation run passes the file to
 * `scripts/ci/mutation_summary.py`, so a module whose PIT run fails before it writes `floors.json`, or a group whose
 * shard wrote no report, still gets a "no report" row, without a list kept by hand beside the build.
 */
@DisableCachingByDefault(because = "Writes one short file from configuration-time facts")
abstract class WriteMutationModules : DefaultTask() {

    /** One line per module: its directory relative to the repository root, with `/` separators, then its groups. */
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
