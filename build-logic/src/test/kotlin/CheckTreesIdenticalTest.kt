import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CheckTreesIdenticalTest {

    @TempDir lateinit var dir: File

    private fun tree(name: String, vararg files: Pair<String, String>): File =
        dir.resolve(name).apply { files.forEach { (path, text) -> resolve(path).apply { parentFile.mkdirs() }.writeText(text) } }

    @Test
    fun identicalTreesDifferNowhere() {
        val a = tree("a", "x/A.java" to "class A {}", "B.java" to "class B {}")
        val b = tree("b", "x/A.java" to "class A {}", "B.java" to "class B {}")
        assertThat(CheckTreesIdentical.differingFiles(a, b)).isEmpty()
    }

    @Test
    fun oneDifferentByteIsReported() {
        val a = tree("a", "x/A.java" to "class A {}")
        val b = tree("b", "x/A.java" to "class A { }")
        assertThat(CheckTreesIdentical.differingFiles(a, b)).containsExactly("x/A.java")
    }

    @Test
    fun aFileInOneTreeOnlyIsReported() {
        val a = tree("a", "A.java" to "class A {}", "Extra.java" to "")
        val b = tree("b", "A.java" to "class A {}", "Other.java" to "")
        assertThat(CheckTreesIdentical.differingFiles(a, b)).containsExactly("Extra.java", "Other.java")
    }
}
