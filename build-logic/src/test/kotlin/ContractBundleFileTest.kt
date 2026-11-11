import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Where `-PcontractBundle` points: a relative path resolves against the repository root (#324). */
class ContractBundleFileTest {

    @TempDir
    lateinit var workspace: File

    private val repositoryRoot get() = File(workspace, "spec-kitty-charter")

    @Test
    fun aRelativePathResolvesAgainstTheRepositoryRootNotTheProjectDirectory() {
        assertThat(ContractBundleFile.resolve(repositoryRoot, "build/charter-openapi.yaml"))
            .isEqualTo(File(repositoryRoot, "build/charter-openapi.yaml").absoluteFile)
    }

    @Test
    fun aRelativePathMayLeaveTheRepositoryRoot() {
        assertThat(ContractBundleFile.resolve(repositoryRoot, "../bundle/charter-openapi.yaml"))
            .isEqualTo(File(workspace, "bundle/charter-openapi.yaml").absoluteFile)
    }

    @Test
    fun anAbsolutePathIsKeptAsGiven() {
        val elsewhere = File(workspace, "elsewhere/charter-openapi.yaml").absoluteFile
        assertThat(ContractBundleFile.resolve(repositoryRoot, elsewhere.path)).isEqualTo(elsewhere)
    }

    @Test
    fun aBlankPathIsRefusedRatherThanNamingTheRepositoryRoot() {
        for (blank in listOf("", " ")) {
            assertThatThrownBy { ContractBundleFile.resolve(repositoryRoot, blank) }
                .`as`("'$blank'")
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("-PcontractBundle is empty")
        }
    }

    @Test
    fun theResultIsAlwaysAbsoluteAndNormalized() {
        for (given in listOf("build/b.yaml", "./a/../b.yaml", File(workspace, "x/../y.yaml").absolutePath)) {
            val resolved = ContractBundleFile.resolve(repositoryRoot, given)
            assertThat(resolved.isAbsolute).`as`(given).isTrue()
            assertThat(resolved.toPath()).`as`(given).isEqualTo(resolved.toPath().normalize())
        }
    }
}
