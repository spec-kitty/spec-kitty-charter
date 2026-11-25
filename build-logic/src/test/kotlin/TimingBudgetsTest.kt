import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The timing classes are read from the test sources, in the one form the build can see (#316, #644 triage). */
class TimingBudgetsTest {

    @TempDir
    lateinit var sources: File

    private fun source(path: String, text: String) {
        File(sources, path).apply { parentFile.mkdirs() }.writeText(text)
    }

    @Test
    fun theLiteralTagNamesTheClassAloneOrGroupedAndAnUntaggedClassIsLeftOut() {
        source("a/b/PlainBudgetTest.java", "package a.b;\n@Tag(\"timing\")\npublic class PlainBudgetTest {}")
        source("a/ValueBudgetTest.java", "package a;\n@Tag( value = \"timing\" )\nclass ValueBudgetTest {}")
        source("a/GroupedBudgetTest.java", "package a;\n@Tags({@Tag(\"timing\"), @Tag(\"slow\")})\nclass GroupedBudgetTest {}")
        source("a/b/UnitTest.java", "package a.b;\n@Tag(\"stress\")\nclass UnitTest {}")

        assertThat(TimingBudgets.classes(sources))
            .containsExactly("a.GroupedBudgetTest", "a.ValueBudgetTest", "a.b.PlainBudgetTest")
    }

    @Test
    fun theClassNameComesFromThePackageDeclarationNotThePath() {
        source("src/PackagedBudgetTest.java", "/* package x.y; */\npackage real.pkg ;\n@Tag(\"timing\")\nclass PackagedBudgetTest {}")

        assertThat(TimingBudgets.classes(sources)).containsExactly("real.pkg.PackagedBudgetTest")
    }

    @Test
    fun aTagTheBuildCannotSeeIsRefusedNamingItsFile() {
        source("a/ConstantBudgetTest.java", "package a;\nstatic final String T = \"timing\";\n@Tag(T)\nclass ConstantBudgetTest {}")
        source("a/PlainBudgetTest.java", "package a;\n@Tag(\"timing\")\nclass PlainBudgetTest {}")

        assertThat(refusedFiles()).containsExactly("a/ConstantBudgetTest.java")
    }

    @Test
    fun aTagThatDoesNotNameATopLevelConcreteClassOfAPackagedJavaSourceIsRefused() {
        source("a/AbstractBase.java", "package a;\n@Tag(\"timing\")\nabstract class AbstractBase {}")
        source("a/Outer.java", "package a;\nclass Outer {\n  @Tag(\"timing\")\n  static class Inner {}\n}")
        source("a/OnMethod.java", "package a;\nclass OnMethod {\n  @Tag(\"timing\") @Test void budget() {}\n}")
        source("NoPackage.java", "@Tag(\"timing\")\nclass NoPackage {}")
        source("a/Garbled.java", "package a b;\n@Tag(\"timing\")\nclass Garbled {}")
        source("a/KotlinBudget.kt", "package a\n@Tag(\"timing\")\nclass KotlinBudget")
        source("a/Fine.java", "package a;\nclass Fine { String s = \"{\"; /* { */ }\n@Tag(\"timing\")\nclass Tagged {}")

        assertThat(refusedFiles()).containsExactly(
            "NoPackage.java", "a/AbstractBase.java", "a/Garbled.java", "a/KotlinBudget.kt", "a/OnMethod.java", "a/Outer.java",
        )
    }

    private fun refusedFiles(): List<String> {
        val message = catchThrowable { TimingBudgets.classes(sources) }
        assertThat(message).isInstanceOf(IllegalStateException::class.java)
        return message.message!!.substringAfterLast(": ").split(", ")
    }

    @Test
    fun aBudgetClassWithNoResultOrNoTestRunIsReportedAsNotRun() {
        source("TEST-a.Ran.xml", "<testsuite name=\"a.Ran\" tests=\"2\" skipped=\"0\">")
        source("TEST-a.Empty.xml", "<testsuite name=\"a.Empty\" tests=\"0\" skipped=\"0\">")

        assertThat(TimingBudgets.withoutResults(listOf("a.Ran", "a.Empty", "a.Absent"), sources))
            .containsExactly("a.Empty", "a.Absent")
    }

    @Test
    fun theModulesWriteTheTagInTheSupportedFormAndHoldBudgets() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "service").isDirectory }

        for (module in listOf("packs", "activations")) {
            assertThat(TimingBudgets.classes(File(root, "service/$module/src/test/java"))).`as`(module).isNotEmpty()
        }
    }
}
