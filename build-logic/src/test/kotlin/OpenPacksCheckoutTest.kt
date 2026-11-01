import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Where the build looks for the spec-kitty-open-packs checkout (-PopenPacksDir, #322). */
class OpenPacksCheckoutTest {

    @TempDir
    lateinit var workspace: File

    private val repositoryRoot get() = File(workspace, "spec-kitty-charter")

    @Test
    fun withoutThePropertyTheCheckoutIsTheSiblingOfTheRepositoryRoot() {
        assertThat(OpenPacksCheckout.resolve(repositoryRoot, null))
            .isEqualTo(File(workspace, "spec-kitty-open-packs").absoluteFile)
    }

    @Test
    fun aRelativePathResolvesAgainstTheRepositoryRootNotTheProjectDirectory() {
        assertThat(OpenPacksCheckout.resolve(repositoryRoot, "../spec-kitty-open-packs"))
            .isEqualTo(File(workspace, "spec-kitty-open-packs").absoluteFile)
    }

    @Test
    fun aRelativePathBelowTheRootStaysBelowIt() {
        assertThat(OpenPacksCheckout.resolve(repositoryRoot, ".open-packs"))
            .isEqualTo(File(repositoryRoot, ".open-packs").absoluteFile)
    }

    @Test
    fun anAbsolutePathIsKeptAsGiven() {
        val elsewhere = File(workspace, "elsewhere/open-packs").absoluteFile
        assertThat(OpenPacksCheckout.resolve(repositoryRoot, elsewhere.path)).isEqualTo(elsewhere)
    }

    @Test
    fun aBlankPathIsRefusedRatherThanNamingTheRepositoryRoot() {
        for (blank in listOf("", " ")) {
            assertThatThrownBy { OpenPacksCheckout.resolve(repositoryRoot, blank) }
                .`as`("'$blank'")
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("-PopenPacksDir is empty")
        }
    }

    @Test
    fun theResultIsAlwaysAbsoluteAndNormalized() {
        for (given in listOf(null, "../spec-kitty-open-packs", "./a/../b", File(workspace, "x/../y").absolutePath)) {
            val resolved = OpenPacksCheckout.resolve(repositoryRoot, given)
            assertThat(resolved.isAbsolute).`as`(given ?: "<absent>").isTrue()
            assertThat(resolved.toPath()).`as`(given ?: "<absent>").isEqualTo(resolved.toPath().normalize())
        }
    }
}
