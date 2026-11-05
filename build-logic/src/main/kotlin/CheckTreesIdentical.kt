import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Fails unless two directory trees hold the same files with the same bytes. `charter.client-java` uses it to prove
 * that generating the client twice from one bundle gives identical sources (#218).
 */
@CacheableTask
abstract class CheckTreesIdentical : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val first: DirectoryProperty

    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val second: DirectoryProperty

    @get:OutputFile abstract val marker: RegularFileProperty

    @TaskAction
    fun compare() {
        val a = first.get().asFile
        val b = second.get().asFile
        val differing = differingFiles(a, b)
        if (differing.isNotEmpty()) {
            throw GradleException(
                "Two generations differ in ${differing.size} file(s) under $a and $b:\n" +
                    differing.take(20).joinToString("\n") { "  $it" },
            )
        }
        marker.get().asFile.writeText("${tree(a).size} files identical\n")
    }

    companion object {
        /** The relative paths that are in one tree only, or in both with different bytes, sorted. */
        fun differingFiles(a: File, b: File): List<String> {
            val left = tree(a)
            val right = tree(b)
            return (left.keys + right.keys).sorted().filter { path ->
                val x = left[path]
                val y = right[path]
                x == null || y == null || !x.readBytes().contentEquals(y.readBytes())
            }
        }

        private fun tree(root: File): Map<String, File> =
            root.walkTopDown().filter { it.isFile }.associateBy { it.relativeTo(root).invariantSeparatorsPath }
    }
}
