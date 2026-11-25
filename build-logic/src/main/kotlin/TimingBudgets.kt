import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.testing.Test

/**
 * The test classes of a module that carry the `timing` tag (#316), read from their sources (#644 triage).
 *
 * The supported form is the tag on the declaration of a top-level, non-abstract class of a Java source that declares
 * its package, as a literal: `@Tag("timing")` or `@Tag(value = "timing")`, alone or inside `@Tags({...})`. Every other
 * mention of the `"timing"` literal in a test source is refused, because the class would match neither this list nor
 * `test`, which leaves `timing` out, and so would run nowhere: a constant, a composed annotation, a tag on a nested
 * class or a method, on an abstract class (its subclasses inherit the tag but are not named here), in a source
 * without a package declaration, or anywhere in a Kotlin source. A concrete class that extends a tagged concrete class
 * inherits the tag the same way and is not seen: tag it directly.
 */
object TimingBudgets {

    /** The supported form: `@Tag("timing")` or `@Tag(value = "timing")`, spaces allowed. */
    private val SUPPORTED = Regex("""@Tag\(\s*(?:value\s*=\s*)?"timing"\s*\)""")

    private val PACKAGE = Regex("""(?m)^\s*package\s+([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)\s*;""")

    private val CLASS = Regex("""\bclass\b""")

    private val NOT_A_CONCRETE_CLASS = Regex("""\b(?:abstract|interface)\b""")

    private const val LITERAL = "\"timing\""

    /**
     * The fully qualified names of the classes under [testSources] whose source carries the tag, sorted.
     *
     * The name comes from the source's `package` declaration and the file name, the way the compiler names the class.
     *
     * @param testSources a module's `src/test/java`
     * @return the class names; empty when none carries the tag
     * @throws IllegalStateException when a source names the `"timing"` literal in a form the build cannot follow
     */
    fun classes(testSources: File): List<String> {
        val refused = mutableListOf<String>()
        val classes = mutableListOf<String>()
        testSources.walkTopDown().filter { it.isFile }.forEach { file ->
            val path = file.relativeTo(testSources).invariantSeparatorsPath
            when (file.extension) {
                "kt" -> if (file.readText().contains(LITERAL)) refused += path
                "java" -> {
                    val scan = scan(file.readText(), file.nameWithoutExtension)
                    if (scan.refused) refused += path
                    scan.className?.let { classes += it }
                }
            }
        }
        check(refused.isEmpty()) {
            "the timing tag is written in a form timingBudgets cannot see; use @Tag(\"timing\") on a top-level, " +
                "non-abstract class of a Java source with a package declaration: " + refused.sorted().joinToString()
        }
        return classes.sorted()
    }

    /**
     * The classes of [classes] with no test result under [results]: no `TEST-<class>.xml`, or one that ran no test.
     *
     * @param classes the tagged classes ([classes])
     * @param results a test task's JUnit XML directory
     * @return the class names that did not run, in the order given
     */
    fun withoutResults(classes: List<String>, results: File): List<String> =
        classes.filterNot { name ->
            val xml = File(results, "TEST-$name.xml")
            xml.isFile && (Regex("""\btests="(\d+)"""").find(xml.readText())?.groupValues?.get(1)?.toInt() ?: 0) > 0
        }

    private class Scan(val className: String?, val refused: Boolean)

    private fun scan(text: String, simpleName: String): Scan {
        val literals = text.split(LITERAL).size - 1
        if (literals == 0) return Scan(null, false)
        val tags = SUPPORTED.findAll(text).toList()
        val code = masked(text)
        val inPlace = tags.size == literals && tags.all { onTopLevelClass(code, it.range.last + 1) }
        val pkg = PACKAGE.find(code)?.groupValues?.get(1)
        if (!inPlace || pkg == null) return Scan(null, true)
        return Scan("$pkg.$simpleName", false)
    }

    /** True when the declaration an annotation ending at [from] belongs to is a concrete class at the top level. */
    private fun onTopLevelClass(code: String, from: Int): Boolean {
        var parens = 0
        var braces = 0
        for (i in 0 until from) {
            when (code[i]) {
                '(' -> parens++
                ')' -> parens--
                '{' -> if (parens <= 0) braces++
                '}' -> if (parens <= 0) braces--
            }
        }
        if (braces != 0) return false
        parens = 0
        for (i in from until code.length) {
            when (code[i]) {
                '(' -> parens++
                ')' -> parens--
                ';' -> if (parens <= 0) return false
                '{' -> if (parens <= 0) {
                    val header = code.substring(from, i)
                    return CLASS.containsMatchIn(header) && !NOT_A_CONCRETE_CLASS.containsMatchIn(header)
                }
            }
        }
        return false
    }

    /** [text] with comments and the insides of string and character literals blanked, so braces and keywords in them are not read. */
    private fun masked(text: String): String {
        val out = StringBuilder(text)
        fun blank(from: Int, to: Int) {
            for (k in from until minOf(to, text.length)) if (out[k] != '\n') out[k] = ' '
        }
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("//", i) -> {
                    val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    blank(i, end)
                    i = end
                }
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                    blank(i, end)
                    i = end
                }
                text.startsWith("\"\"\"", i) -> {
                    val end = text.indexOf("\"\"\"", i + 3).let { if (it < 0) text.length else it }
                    blank(i + 3, end)
                    i = end + 3
                }
                text[i] == '"' || text[i] == '\'' -> {
                    val quote = text[i]
                    var j = i + 1
                    while (j < text.length && text[j] != quote && text[j] != '\n') j += if (text[j] == '\\') 2 else 1
                    blank(i + 1, j)
                    i = j + 1
                }
                else -> i++
            }
        }
        return out.toString()
    }
}

/** Reads [TimingBudgets.classes] as a build input, so the configuration cache sees the walk and re-runs it each build. */
abstract class TimingClassesSource : ValueSource<List<String>, TimingClassesSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val testSources: Property<File>
    }

    override fun obtain(): List<String> = TimingBudgets.classes(parameters.testSources.get())
}

/**
 * Fails when a tagged budget class left no test result: a correctness check on the report-only budgets, not a timing
 * one. The nightly runs it after `timingBudgets`, outside its `continue-on-error`, so a budget that never ran (the
 * fork died, a filter matched nothing) is a red job and not a quiet gap in the report.
 */
abstract class VerifyTimingBudgetsRan : DefaultTask() {
    @get:Input
    abstract val classes: ListProperty<String>

    /** Read at run time and never an output: the verified task is not a dependency, so it is never re-run by this one. */
    @get:Internal
    abstract val results: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verify() {
        val missing = TimingBudgets.withoutResults(classes.get(), results.get().asFile)
        check(missing.isEmpty()) {
            "these timing budget classes left no test result under ${results.get().asFile}, so they did not run: " +
                missing.joinToString()
        }
    }
}

/** Registers `verifyTimingBudgetsRan` for a module whose `timingBudgets` task is narrowed by [onlyTimingClasses]. */
fun Project.registerVerifyTimingBudgetsRan(testSources: File) {
    tasks.register<VerifyTimingBudgetsRan>("verifyTimingBudgetsRan") {
        group = "verification"
        description = "Fails when a timing budget class left no result from timingBudgets; run after it, not part of check."
        classes.set(providers.of(TimingClassesSource::class.java) { parameters.testSources.set(testSources) })
        results.set(layout.buildDirectory.dir("test-results/timingBudgets"))
    }
}

/**
 * Narrows a `timingBudgets` task to the test classes that carry the `timing` tag ([TimingBudgets.classes]).
 *
 * The task forks a fresh JVM for every test class (`forkEvery = 1`), so a budget never measures beside another's heap
 * and JIT state. Gradle hands every class of the test source set to a fork, and the JUnit tag filter applies only
 * inside it: without this narrowing, `:packs:timingBudgets` started about 1,400 JVMs to run 6 tests, and 14 of its 15
 * minutes went to forks that ran nothing, so the CPU-time nightly ran out of its job time. The tag filter stays, so a
 * class named here that loses its tag still runs nothing.
 *
 * @param testSources the module's `src/test/java`
 * @throws IllegalStateException when no class carries the tag, so the task can never pass by running nothing, or when a
 *     source writes the tag in a form [TimingBudgets.classes] refuses
 */
fun Test.onlyTimingClasses(testSources: File) {
    val classes = project.providers.of(TimingClassesSource::class.java) { parameters.testSources.set(testSources) }.get()
    check(classes.isNotEmpty()) { "no test class under $testSources carries @Tag(\"timing\")" }
    classes.forEach { filter.includeTestsMatching(it) }
}
