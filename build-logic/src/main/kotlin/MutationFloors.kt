import java.io.Serializable
import java.util.Locale
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * The mutation floor (#239): the pure reading and scoring of PIT's `mutations.xml`, kept free of Gradle so
 * that it is unit-tested in build-logic. [CheckMutationFloors] is the task around it.
 *
 * **Score.** The score is PIT's own "mutation score" (`MutationStatistics.getPercentageDetected`): the mutants PIT
 * counts as *detected* over **all** mutants, uncovered ones included. PIT's detected statuses
 * (`DetectionStatus.isDetected`) are KILLED, TIMED_OUT, MEMORY_ERROR, RUN_ERROR, NON_VIABLE and EQUIVALENT; SURVIVED,
 * NO_COVERAGE, NOT_STARTED and STARTED are not. **Test strength** is detected over the mutants that some test covers
 * (all minus NO_COVERAGE), as PIT reports it. A group passes when its unrounded score is at or above its floor: the
 * comparison is exact (`detected * 100 >= floor * mutants`), not PIT's rounded integer percentage. Printed scores
 * and strengths are truncated, never rounded, to one decimal, so a failing score never reads as its floor
 * (64.95 % prints as 64.9 %).
 *
 * **Lenient reading.** `mutations.xml` is not parsed as XML: PIT writes mutator descriptions with a raw `<` in them
 * (#239), which a strict parser refuses. Each `<mutation ...>` element is found by a regex scan, and only its
 * `status` attribute and its `<mutatedClass>` are read; anything else in the element is ignored. An element in which
 * either cannot be found is counted as unreadable, and a report with any unreadable element is refused rather than
 * scored on fewer mutants.
 *
 * **Margin.** How close a group is to its floor (#389): `marginMutants` is how many detected mutants it can lose and
 * still pass (negative: how many it must gain), and `marginPoints` is its printed score minus its floor, in points.
 * `timedOut` counts the TIMED_OUT mutants, which count as detected but are often noise from a loaded runner.
 *
 * **Summary.** [evaluate] gives the summary, `build/reports/mutation-floors/floors.json` in a module (its own output
 * directory, outside PIT's report directory): one entry per group with `mutants`, `detected`, `survived`,
 * `noCoverage`, `timedOut`, `score`, `strength`, `floor`, `marginMutants`, `marginPoints`, `passed` and the
 * [TOP_CLASSES] classes with the most surviving and uncovered mutants.
 */
object MutationFloors {

    /** PIT's detected statuses (`DetectionStatus.isDetected()` is true), the numerator of the score. */
    val DETECTED: Set<String> = setOf("KILLED", "TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR", "NON_VIABLE", "EQUIVALENT")

    private const val SURVIVED = "SURVIVED"
    private const val NO_COVERAGE = "NO_COVERAGE"
    private const val TIMED_OUT = "TIMED_OUT"

    /** Where the floor and its raise rule are recorded, quoted in every failure. */
    private const val REFERENCE = "(#239, ADR-0062)"

    /** How many classes the summary lists per group. */
    const val TOP_CLASSES = 5

    private val MUTATION_START = Regex("""<mutation\s""")
    private val STATUS = Regex("""\bstatus\s*=\s*['"]([A-Z_]+)['"]""")
    private val MUTATED_CLASS = Regex("""<mutatedClass>\s*([^<\s]+)\s*</mutatedClass>""")

    /** One mutant as the floor check needs it: the mutated class (binary name) and PIT's status. */
    data class Mutant(val mutatedClass: String, val status: String) {
        /** The top-level class, without any `$Nested` suffix. */
        val topLevelClass: String get() = mutatedClass.substringBefore('$')
    }

    /**
     * A floor group: the mutants whose class lies in one of [packages] and in none of [excludes], held to [floor]
     * percent. A package matches itself and its sub-packages.
     */
    data class Group(
        val name: String,
        val packages: List<String>,
        val excludes: List<String>,
        val floor: Int,
    ) : Serializable {
        init {
            require(name.isNotBlank()) { "A mutation floor group needs a name" }
            require(packages.isNotEmpty()) { "Mutation floor group '$name' names no package" }
            require(floor in 0..100) { "Mutation floor group '$name' has floor $floor, which is not a percentage" }
        }

        fun contains(mutatedClass: String): Boolean =
            packages.any { inPackage(mutatedClass, it) } && excludes.none { inPackage(mutatedClass, it) }

        private fun inPackage(mutatedClass: String, pkg: String): Boolean {
            val prefix = pkg.removeSuffix(".*").removeSuffix(".")
            return mutatedClass.startsWith("$prefix.") || mutatedClass.substringBefore('$') == prefix
        }

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /** The mutants of one class within a group that no test killed. */
    data class ClassGaps(val className: String, val survived: Int, val noCoverage: Int)

    /** The mutants of a report, and how many `<mutation` elements lacked a status or a mutated class. */
    data class Report(val mutants: List<Mutant>, val unreadable: Int)

    /**
     * What a check concludes: the lines to log, the summary to write (null when the report could not be scored) and
     * the failures (empty when every group passes).
     */
    data class Outcome(val lines: List<String>, val summary: String?, val failures: List<String>)

    /** The figures of one group. */
    data class GroupResult(
        val group: Group,
        val mutants: Int,
        val detected: Int,
        val survived: Int,
        val noCoverage: Int,
        val timedOut: Int,
        val topClasses: List<ClassGaps>,
    ) {
        /** Detected over all mutants, in percent; 0 for a group without mutants. */
        val score: Double get() = if (mutants == 0) 0.0 else detected * 100.0 / mutants

        /** Detected over covered mutants (all minus NO_COVERAGE), in percent; 0 when nothing is covered. */
        val strength: Double
            get() = (mutants - noCoverage).let { covered -> if (covered == 0) 0.0 else detected * 100.0 / covered }

        /**
         * The detected mutants the group can lose and still pass: [detected] minus the fewest that pass, which is
         * `ceil(floor * mutants / 100)`. Negative when it fails: the mutants it must gain.
         */
        val marginMutants: Int
            get() = (detected - (group.floor.toLong() * mutants + 99) / 100).toInt()

        /**
         * The printed score minus the floor, in points to one decimal: the score is truncated as [percent] prints it,
         * so a group below its floor never reads as 0.0.
         */
        val marginPoints: String
            get() = points(tenths(detected, mutants) - group.floor * 10L)

        /** Whether the group has mutants and its exact score is at or above its floor. */
        val passes: Boolean get() = mutants > 0 && detected.toLong() * 100 >= group.floor.toLong() * mutants

        /** The failure line for [projectPath], or null when the group passes. */
        fun failure(projectPath: String): String? = when {
            mutants == 0 ->
                "$projectPath ${group.name}: no mutant lies in its packages ${group.packages}, so its floor" +
                    " ${group.floor} % cannot be checked $REFERENCE"
            !passes ->
                "$projectPath ${group.name}: mutation score ${percent(detected, mutants)} % is below its floor" +
                    " ${group.floor} % $REFERENCE"
            else -> null
        }

        /** The log line of the group. */
        fun line(projectPath: String): String =
            "$projectPath ${group.name}: $mutants mutants, score ${percent(detected, mutants)} %," +
                " strength ${percent(detected, mutants - noCoverage)} %, floor ${group.floor} %"
    }

    /** Reads every mutant of a `mutations.xml` text leniently; a text without mutants gives an empty report. */
    fun parse(xml: String): Report {
        val starts = MUTATION_START.findAll(xml).map { it.range.first }.toList()
        val mutants = starts.mapIndexedNotNull { index, start ->
            val end = if (index + 1 < starts.size) starts[index + 1] else xml.length
            val element = xml.substring(start, end)
            val openTag = element.substringBefore('>')
            val status = STATUS.find(openTag)?.groupValues?.get(1)
            val mutatedClass = MUTATED_CLASS.find(element)?.groupValues?.get(1)
            if (status == null || mutatedClass == null) null else Mutant(mutatedClass, status)
        }
        return Report(mutants, starts.size - mutants.size)
    }

    /**
     * Checks a module's report against its groups.
     *
     * @param projectPath the module's Gradle path, which every line names
     * @param reportLocation where the report is, for the message when it is missing
     * @param reportText the text of `mutations.xml`, or null when there is none
     * @param groups the module's `mutationFloors` groups
     */
    fun evaluate(projectPath: String, reportLocation: String, reportText: String?, groups: List<Group>): Outcome {
        if (groups.isEmpty()) {
            return refused("$projectPath applies charter.mutation-conventions but declares no mutationFloors group")
        }
        if (reportText == null) {
            return refused("$projectPath: there is no PIT report at $reportLocation, so no floor can be checked")
        }
        val report = parse(reportText)
        if (report.unreadable > 0) {
            return refused(
                "$projectPath: ${report.unreadable} <mutation> element(s) in $reportLocation lack a status or a" +
                    " mutatedClass, so the report cannot be scored $REFERENCE",
            )
        }
        val results = score(report.mutants, groups)
        return Outcome(
            lines = results.map { it.line(projectPath) },
            summary = toJson(projectPath, report.mutants.size, results),
            failures = failures(projectPath, results),
        )
    }

    private fun refused(failure: String) = Outcome(emptyList(), null, listOf(failure))

    /**
     * A shard of a module's PIT run (#461): the floor groups that `-PmutationGroups` names, in the module's own order.
     * The shard mutates exactly those groups' classes ([targetClasses] less [excludedClasses]) and is scored on exactly
     * those groups, so each group's score is the one a whole-module run gives: PIT runs a mutant's covering tests either
     * way. [name] names the shard's report directory and floors file, so two shards of one module never overwrite
     * each other.
     */
    data class Shard(val name: String, val groups: List<Group>) {
        val targetClasses: Set<String> get() = groups.flatMap { it.packages }.map { "$it.*" }.toSet()
        val excludedClasses: Set<String> get() = groups.flatMap { it.excludes }.map { "$it.*" }.toSet()
    }

    /**
     * The shard that [requested] (`-PmutationGroups`, comma-separated group names) selects from a module's [groups].
     * Every name must be one of the module's groups, and at least one must be given: a misspelt name never widens a
     * shard back to the whole module, nor narrows it to nothing.
     */
    fun shard(projectPath: String, requested: String, groups: List<Group>): Shard {
        val names = requested.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (names.isEmpty()) {
            throw GradleException("$projectPath: -PmutationGroups names no floor group")
        }
        val unknown = names - groups.map { it.name }.toSet()
        if (unknown.isNotEmpty()) {
            throw GradleException(
                "$projectPath: -PmutationGroups names ${unknown.sorted().joinToString()}, which are not among its" +
                    " floor groups (${groups.joinToString { it.name }})",
            )
        }
        val selected = groups.filter { it.name in names }
        return Shard(selected.joinToString("_") { it.name.replace(Regex("[^A-Za-z0-9.-]"), "-") }, selected)
    }

    /** Scores [mutants] per group, in the order of [groups]. */
    fun score(mutants: List<Mutant>, groups: List<Group>): List<GroupResult> = groups.map { group ->
        val mine = mutants.filter { group.contains(it.mutatedClass) }
        val topClasses = mine
            .groupBy { it.topLevelClass }
            .map { (className, ofClass) ->
                ClassGaps(
                    className,
                    survived = ofClass.count { it.status == SURVIVED },
                    noCoverage = ofClass.count { it.status == NO_COVERAGE },
                )
            }
            .filter { it.survived + it.noCoverage > 0 }
            .sortedWith(compareByDescending<ClassGaps> { it.survived + it.noCoverage }.thenBy { it.className })
            .take(TOP_CLASSES)
        GroupResult(
            group = group,
            mutants = mine.size,
            detected = mine.count { it.status in DETECTED },
            survived = mine.count { it.status == SURVIVED },
            noCoverage = mine.count { it.status == NO_COVERAGE },
            timedOut = mine.count { it.status == TIMED_OUT },
            topClasses = topClasses,
        )
    }

    /** The failure lines of [results] for [projectPath]; empty when every group passes. */
    fun failures(projectPath: String, results: List<GroupResult>): List<String> =
        results.mapNotNull { it.failure(projectPath) }

    /** The machine-readable summary (`floors.json`) the mutation workflow reads. */
    fun toJson(projectPath: String, totalMutants: Int, results: List<GroupResult>): String = buildString {
        append("{\n")
        append("  \"project\": ").append(quote(projectPath)).append(",\n")
        append("  \"mutants\": ").append(totalMutants).append(",\n")
        append("  \"groups\": [")
        results.forEachIndexed { index, result ->
            append(if (index == 0) "\n" else ",\n")
            append("    {\n")
            append("      \"group\": ").append(quote(result.group.name)).append(",\n")
            append("      \"packages\": ").append(result.group.packages.joinToString(", ", "[", "]") { quote(it) })
                .append(",\n")
            append("      \"excludes\": ").append(result.group.excludes.joinToString(", ", "[", "]") { quote(it) })
                .append(",\n")
            append("      \"mutants\": ").append(result.mutants).append(",\n")
            append("      \"detected\": ").append(result.detected).append(",\n")
            append("      \"survived\": ").append(result.survived).append(",\n")
            append("      \"noCoverage\": ").append(result.noCoverage).append(",\n")
            append("      \"timedOut\": ").append(result.timedOut).append(",\n")
            append("      \"score\": ").append(percent(result.detected, result.mutants)).append(",\n")
            append("      \"strength\": ")
                .append(percent(result.detected, result.mutants - result.noCoverage)).append(",\n")
            append("      \"floor\": ").append(result.group.floor).append(",\n")
            append("      \"marginMutants\": ").append(result.marginMutants).append(",\n")
            append("      \"marginPoints\": ").append(result.marginPoints).append(",\n")
            append("      \"passed\": ").append(result.passes).append(",\n")
            append("      \"topClasses\": [")
            result.topClasses.forEachIndexed { classIndex, gaps ->
                append(if (classIndex == 0) "\n" else ",\n")
                append("        {\"class\": ").append(quote(gaps.className))
                append(", \"survived\": ").append(gaps.survived)
                append(", \"noCoverage\": ").append(gaps.noCoverage).append("}")
            }
            append(if (result.topClasses.isEmpty()) "]\n" else "\n      ]\n")
            append("    }")
        }
        append(if (results.isEmpty()) "]\n" else "\n  ]\n")
        append("}\n")
    }

    /**
     * [numerator] over [denominator] in percent, truncated (never rounded) to one decimal, as the failure line, the
     * log and the summary print it; 0.0 when [denominator] is 0. Integer arithmetic, so 65 % is never 64.9 %.
     */
    fun percent(numerator: Int, denominator: Int): String = points(tenths(numerator, denominator))

    /** [numerator] over [denominator] in tenths of a percent, truncated; 0 when [denominator] is 0. */
    private fun tenths(numerator: Int, denominator: Int): Long =
        if (denominator <= 0) 0 else numerator.toLong() * 1000 / denominator

    /** Tenths written as a decimal with one digit after the point, a minus sign when negative. */
    private fun points(tenths: Long): String {
        val magnitude = kotlin.math.abs(tenths)
        return "${if (tenths < 0) "-" else ""}${magnitude / 10}.${magnitude % 10}"
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (ch in text) {
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch < ' ' -> append(String.format(Locale.ROOT, "\\u%04x", ch.code))
                else -> append(ch)
            }
        }
        append('"')
    }
}

/** One group of the `mutationFloors` extension: package prefixes, optional excludes and a floor in percent. */
abstract class MutationFloorGroupSpec @Inject constructor(private val groupName: String) : Named {
    override fun getName(): String = groupName

    /** The packages whose classes the group holds (each with its sub-packages). */
    abstract val packages: SetProperty<String>

    /** Packages left out of the group, though inside [packages]. */
    abstract val excludes: SetProperty<String>

    /** The floor, in percent of PIT's mutation score. */
    abstract val floor: Property<Int>

    internal fun toGroup(): MutationFloors.Group = MutationFloors.Group(
        name = groupName,
        packages = packages.get().sorted(),
        excludes = excludes.getOrElse(emptySet()).sorted(),
        floor = floor.orNull ?: throw GradleException("Mutation floor group '$groupName' declares no floor"),
    )
}

/** The `mutationFloors { group("name") { ... } }` extension of `charter.mutation-conventions`. */
abstract class MutationFloorsExtension @Inject constructor(objects: ObjectFactory) {
    /** The groups, each held to its own floor. */
    val groups: NamedDomainObjectContainer<MutationFloorGroupSpec> =
        objects.domainObjectContainer(MutationFloorGroupSpec::class.java)

    /** Declares (or configures) the group [name]. */
    fun group(name: String, action: Action<in MutationFloorGroupSpec>) {
        if (name in groups.names) groups.named(name, action) else groups.register(name, action)
    }
}

/**
 * Checks a module's PIT run against its floors: reads `mutations.xml` leniently, writes `floors.json`, and fails
 * naming the module, the group, the score and the floor when a group is below it. Configuration-cache compatible:
 * every value is a declared input, and nothing reaches the project at execution time.
 */
@DisableCachingByDefault(because = "Reads one small report: restoring its summary from the cache would save nothing")
abstract class CheckMutationFloors : DefaultTask() {

    /** PIT's `mutations.xml`; a missing report fails the check. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val report: RegularFileProperty

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val groups: ListProperty<MutationFloors.Group>

    @get:OutputFile
    abstract val summary: RegularFileProperty

    @TaskAction
    fun check() {
        // A summary from an earlier run must never outlive a check that cannot write a new one.
        val summaryFile = summary.get().asFile
        summaryFile.delete()
        val reportFile = report.get().asFile
        val outcome = MutationFloors.evaluate(
            projectPath.get(),
            reportFile.path,
            if (reportFile.isFile) reportFile.readText() else null,
            groups.get(),
        )
        outcome.summary?.let {
            summaryFile.parentFile.mkdirs()
            summaryFile.writeText(it)
        }
        outcome.lines.forEach { logger.lifecycle(it) }
        if (outcome.failures.isNotEmpty()) {
            throw GradleException(outcome.failures.joinToString("\n"))
        }
    }
}
