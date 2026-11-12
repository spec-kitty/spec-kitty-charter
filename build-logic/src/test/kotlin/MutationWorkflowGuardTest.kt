import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

/**
 * Mutation testing never runs on a pull request or in the required check (ADR-0060, #387): `mutation-nightly.yml` has
 * only the schedule and workflow_dispatch triggers, no other workflow calls it or waits for it, and no other workflow
 * runs PIT or the floor check. That `verifyAll` and `check` never depend on pitest is the build's own rule
 * ([MutationIsolation], applied to every task graph by the root build). The nightly also takes its module list from the
 * build (#388). The workflows are parsed as YAML, so comments and path filters that name the nightly do not count; each
 * rule has a negative control planted on the parsed structure of the real workflows.
 */
class MutationWorkflowGuardTest {

    private val workflows: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, ".github/workflows") }
        .first { File(it, "ci.yml").isFile }

    private val nightlyFile = "mutation-nightly.yml"
    private val nightly get() = File(workflows, nightlyFile).readText()
    private val ci get() = File(workflows, "ci.yml").readText()
    private val service get() = File(workflows, "service.yml").readText()

    /** Every workflow but the nightly, by file name. */
    private val others: Map<String, String>
        get() = workflows.listFiles { f -> f.name.endsWith(".yml") && f.name != nightlyFile }.orEmpty()
            .sortedBy { it.name }
            .associate { it.name to it.readText() }

    @Test
    fun theNightlyRunsOnTheScheduleAndOnDispatchOnly() {
        assertThat(MutationGuard.triggers(nightly)).isEmpty()
        for (trigger in listOf("pull_request", "push", "merge_group", "workflow_call", "workflow_run")) {
            assertThat(MutationGuard.triggers(nightly.mutate { sub("on")[trigger] = null })).`as`(trigger).isNotEmpty()
        }
        assertThat(MutationGuard.triggers(nightly.mutate { sub("on").remove("schedule") })).`as`("no schedule").isNotEmpty()
    }

    @Test
    fun noOtherWorkflowCallsOrWaitsForTheNightly() {
        val name = MutationGuard.parse(nightly)["name"].toString()
        others.forEach { (file, text) -> assertThat(MutationGuard.callers(text, name)).`as`(file).isEmpty() }
        val planted = mapOf(
            "a job that calls it" to ci.mutate { sub("jobs")["mutation"] = mutableMapOf("uses" to "./.github/workflows/$nightlyFile") },
            "a workflow_run on it" to service.mutate {
                put("on", mutableMapOf("workflow_run" to mutableMapOf("workflows" to listOf(name), "types" to listOf("completed"))))
            },
        )
        planted.forEach { (label, text) -> assertThat(MutationGuard.callers(text, name)).`as`(label).isNotEmpty() }
    }

    @Test
    fun noOtherWorkflowRunsPitOrTheFloorCheck() {
        others.forEach { (name, text) -> assertThat(MutationGuard.runsMutation(text)).`as`(name).isEmpty() }
        val planted = mapOf(
            "pitest in a step" to service.mutate { firstJob().steps().add(mutableMapOf("run" to "./gradlew :packs:pitest")) },
            "the floor check in a step" to ci.mutate {
                firstJob().steps().add(mutableMapOf("run" to "./gradlew build checkMutationFloors"))
            },
        )
        planted.forEach { (label, text) -> assertThat(MutationGuard.runsMutation(text)).`as`(label).isNotEmpty() }
    }

    @Test
    fun theNightlyTakesItsModulesFromTheBuildAndKeepsPitsOutput() {
        assertThat(MutationGuard.moduleList(nightly)).isEmpty()
        val planted = mapOf(
            "no mutationModules" to nightly.mutate {
                step("Mutation testing")["run"] = "./gradlew pitest --continue -PrequireSymlinks=true"
            },
            "no pipefail" to nightly.mutate {
                step("Mutation testing").let { it["run"] = it["run"].toString().replace("set -o pipefail\n", "") }
            },
            "PIT output not kept" to nightly.mutate {
                step("Mutation testing").let { it["run"] = it["run"].toString().replace(Regex("""\|\s*tee\s+\S+"""), "") }
            },
            "summary without the log" to nightly.mutate {
                step("Summarise the mutation floors").let {
                    it["run"] = it["run"].toString().replace(Regex("""--log\s+\S+"""), "")
                }
            },
            "summary without the list" to nightly.mutate {
                step("Summarise the mutation floors").let {
                    it["run"] = it["run"].toString().replace(Regex("""--modules\s+\S+"""), "")
                }
            },
        )
        planted.forEach { (label, text) -> assertThat(MutationGuard.moduleList(text)).`as`(label).isNotEmpty() }
    }
}

private typealias Node = MutableMap<String, Any?>

@Suppress("UNCHECKED_CAST")
private fun Node.sub(key: String): Node = this[key] as Node

@Suppress("UNCHECKED_CAST")
private fun Node.firstJob(): Node = sub("jobs").values.first() as Node

@Suppress("UNCHECKED_CAST")
private fun Node.steps(): MutableList<Node> = this["steps"] as MutableList<Node>

/** The nightly's step named [name], in whichever job holds it: the PIT shards and the summary are separate jobs. */
@Suppress("UNCHECKED_CAST")
private fun Node.step(name: String): Node =
    sub("jobs").values.flatMap { (it as Node).steps() }.first { it["name"] == name }

/** The workflow parsed, changed and written back, so a planted variant does not depend on the file's layout. */
@Suppress("UNCHECKED_CAST")
private fun String.mutate(change: Node.() -> Unit): String {
    val doc = MutationGuard.parse(this) as Node
    doc.change()
    return Yaml(DumperOptions().apply { defaultFlowStyle = DumperOptions.FlowStyle.BLOCK; width = Int.MAX_VALUE }).dump(doc)
}

/** Rules over a parsed workflow; each returns its violations. */
private object MutationGuard {

    private const val NIGHTLY = "mutation-nightly.yml"
    private const val MODULES = "build/reports/mutation-floors/modules.txt"
    private const val LOG = "build/reports/mutation-floors/pitest.log"
    private val mutationTask = Regex("""(?<![\w-])(pitest|checkMutationFloors)\b""")

    /** Snakeyaml (YAML 1.1) reads the bare `on` key as boolean true. */
    fun parse(text: String): Map<*, *> {
        val doc = Yaml(LoaderOptions().apply { isAllowDuplicateKeys = false }).load<Any?>(text) as Map<*, *>
        return doc.entries.associate { (if (it.key == true) "on" else it.key) to it.value }
    }

    private fun Any?.map(): Map<*, *> = this as? Map<*, *> ?: emptyMap<Any?, Any?>()
    private fun Any?.list(): List<*> = this as? List<*> ?: listOfNotNull(this)

    private fun jobs(doc: Map<*, *>) = doc["jobs"].map().entries.associate { it.key.toString() to it.value.map() }
    private fun steps(job: Map<*, *>) = (job["steps"] as? List<*>).orEmpty().map { it.map() }

    private fun on(doc: Map<*, *>): List<String> = when (val on = doc["on"]) {
        is Map<*, *> -> on.keys.map { it.toString() }
        else -> on.list().map { it.toString() }
    }

    fun triggers(text: String): List<String> {
        val found = on(parse(text))
        return found.filterNot { it in setOf("schedule", "workflow_dispatch") }.map { "unexpected trigger $it" } +
            listOf("schedule", "workflow_dispatch").filterNot { it in found }.map { "missing trigger $it" }
    }

    /** Jobs or steps that use the nightly, and a `workflow_run` trigger on it by its [name]. */
    fun callers(text: String, name: String): List<String> {
        val doc = parse(text)
        val uses = jobs(doc).flatMap { (job, body) ->
            (listOfNotNull(body["uses"]) + steps(body).mapNotNull { it["uses"] })
                .filter { NIGHTLY in it.toString() }
                .map { "$job uses $it" }
        }
        val waits = doc["on"].map()["workflow_run"].map()["workflows"].list()
            .filter { it.toString() == name }
            .map { "a workflow_run trigger on $it" }
        return uses + waits
    }

    /** Steps whose script runs PIT or the floor check. */
    fun runsMutation(text: String): List<String> = jobs(parse(text)).flatMap { (job, body) ->
        steps(body).mapNotNull { step ->
            val run = step["run"]?.toString() ?: return@mapNotNull null
            if (mutationTask.containsMatchIn(run)) "$job runs ${mutationTask.find(run)!!.value}" else null
        }
    }

    /**
     * The nightly writes the module list and keeps PIT's output in its mutation step, and the summary reads both
     * (#388, #461).
     */
    fun moduleList(text: String): List<String> {
        val steps = jobs(parse(text)).values.flatMap(::steps)
        val mutation = steps.firstOrNull { "pitest" in it["run"].toString() }?.get("run").toString()
        val summary = steps.firstOrNull { "mutation_summary.py" in it["run"].toString() }?.get("run").toString()
        return listOfNotNull(
            if (!Regex("""\bmutationModules\b""").containsMatchIn(mutation)) "the PIT step does not run mutationModules" else null,
            if (!Regex("""--modules\s+${Regex.escape(MODULES)}""").containsMatchIn(summary))
                "the summary does not read $MODULES" else null,
            if (!Regex("""\btee\s+${Regex.escape(LOG)}""").containsMatchIn(mutation)) "the PIT step does not keep $LOG" else null,
            // GitHub's default shell is bash -e without pipefail: tee's 0 would turn a red PIT run green.
            if (!Regex("""(?m)^\s*set\s+-[a-z]*o\s+pipefail\b|^\s*set\s+-o\s+pipefail\b""").containsMatchIn(mutation))
                "the PIT step pipes through tee without set -o pipefail" else null,
            if (!Regex("""--log\s+${Regex.escape(LOG)}""").containsMatchIn(summary)) "the summary does not read $LOG" else null,
        )
    }
}
