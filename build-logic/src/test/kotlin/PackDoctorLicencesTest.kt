import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The licence files of the pack-doctor jar (#478, WP06 T025, squad J8): the task's work and its two refusals. */
class PackDoctorLicencesTest {

    @TempDir lateinit var dir: File

    private val projectLicence: File by lazy { dir.resolve("LICENSE").apply { writeText("MIT License\n") } }
    private val checkedIn: File by lazy { dir.resolve("licenses").apply { mkdirs() } }
    private val out: File by lazy { dir.resolve("out") }

    /** A library jar holding [entries], each a path and its text. */
    private fun library(module: String, vararg entries: Pair<String, String>): PackDoctorLicences.Library {
        val jar = dir.resolve("$module.jar")
        ZipOutputStream(jar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("org/example/$module/A.class"))
            zip.write(byteArrayOf(0))
            for ((path, text) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
            }
        }
        return PackDoctorLicences.Library("org.example", module, "1.0", jar)
    }

    private fun checkIn(directory: String) {
        checkedIn.resolve(directory).apply { mkdirs() }.resolve("LICENSE").writeText("Apache License\n")
    }

    @Test
    fun eachLibrarysLicenceMovesUnderItsOwnDirectory() {
        val shipped = library("shipped", "META-INF/LICENSE.txt" to "BSD\n", "META-INF/NOTICE" to "notice\n")
        val bare = library("bare")
        checkIn("org.example-bare")

        relocate(listOf(shipped, bare), checkedIn, projectLicence, out)

        assertThat(out.resolve("META-INF/LICENSE")).hasContent("MIT License")
        assertThat(out.resolve("META-INF/licenses/org.example-shipped").list()).containsExactlyInAnyOrder("LICENSE.txt", "NOTICE")
        assertThat(out.resolve("META-INF/licenses/org.example-bare/LICENSE")).hasContent("Apache License")
        assertThat(out.resolve(PackDoctorLicences.LIBRARIES))
            .hasContent("org.example:bare:1.0\norg.example:shipped:1.0")
    }

    @Test
    fun aLibraryWithNoLicenceFails() {
        // Error handling: a notice alone is no licence, and nothing is checked in for the library.
        val noticeOnly = library("notice-only", "META-INF/NOTICE" to "notice\n")

        assertThatThrownBy { relocate(listOf(noticeOnly), checkedIn, projectLicence, out) }
            .isInstanceOf(GradleException::class.java)
            .hasMessage(
                "pack-doctor jar: no licence file for [org.example:notice-only:1.0]; check one in at " +
                    "service/pack-doctor/licenses/<group>-<module>/LICENSE with its SOURCE (#478)",
            )
    }

    @Test
    fun aCheckedInDirectoryThatNamesNoLibraryFails() {
        // Error handling: a checked-in text whose library left the classpath is stale and would ship.
        val shipped = library("shipped", "META-INF/LICENSE" to "BSD\n")
        checkIn("org.example-gone")

        assertThatThrownBy { relocate(listOf(shipped), checkedIn, projectLicence, out) }
            .isInstanceOf(GradleException::class.java)
            .hasMessage("pack-doctor jar: service/pack-doctor/licenses holds [org.example-gone], no library of the jar (#478)")
    }

    @Test
    fun bothFaultsAreNamedAtOnce() {
        val bare = library("bare")
        checkIn("org.example-gone")

        assertThatThrownBy { relocate(listOf(bare), checkedIn, projectLicence, out) }
            .hasMessageContaining("no licence file for [org.example:bare:1.0]")
            .hasMessageContaining("holds [org.example-gone], no library of the jar")
    }
}
