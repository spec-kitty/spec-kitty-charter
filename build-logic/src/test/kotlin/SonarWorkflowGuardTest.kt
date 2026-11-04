import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The nightly SonarCloud workflow stays reported only (ADR-0058): it never runs on a pull request or in the merge
 * queue (ADR-0047), the CI gate never calls it (ADR-0023), every action is pinned to a commit (NFR-004) and the
 * token reaches only the steps that need it. The workflows are read as text: build-logic has no YAML library.
 */
class SonarWorkflowGuardTest {

    private val workflows: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, ".github/workflows") }
        .first { File(it, "ci.yml").isFile }

    private val sonar get() = File(workflows, "sonar.yml").readText()
    private val ci get() = File(workflows, "ci.yml").readText()

    @Test
    fun sonarRunsOnTheScheduleAndOnDispatchOnly() {
        assertThat(SonarWorkflowGuard.triggers(sonar)).containsExactlyInAnyOrder("schedule", "workflow_dispatch")
        assertThat(SonarWorkflowGuard.triggerViolations(sonar)).isEmpty()
    }

    @Test
    fun theCiGateNeverCallsOrWaitsForSonar() {
        assertThat(SonarWorkflowGuard.gateNeeds(ci)).isNotEmpty()
        assertThat(SonarWorkflowGuard.ciViolations(ci)).isEmpty()
    }

    @Test
    fun everyActionIsPinnedToACommitWithItsVersion() {
        assertThat(SonarWorkflowGuard.uses(sonar)).isNotEmpty()
        assertThat(SonarWorkflowGuard.pinViolations(sonar)).isEmpty()
    }

    @Test
    fun theTokenReachesOnlyTheProbeTheScanAndTheGate() {
        assertThat(SonarWorkflowGuard.tokenViolations(sonar)).isEmpty()
    }

    @Test
    fun everyStepAfterTheProbeIsGatedOnTheTokenAndCannotTurnTheRunRed() {
        assertThat(SonarWorkflowGuard.steps(sonar).first().id).isEqualTo("sonar_token")
        assertThat(SonarWorkflowGuard.stepViolations(sonar)).isEmpty()
    }

    // Non-vacuity: each check fails on a planted variant of the real workflow.

    @Test
    fun aPullRequestTriggerIsCaught() {
        for (trigger in listOf("pull_request", "pull_request_target", "merge_group", "push")) {
            val planted = sonar.replaceFirst("  workflow_dispatch:\n", "  workflow_dispatch:\n  $trigger:\n")
            assertThat(planted).isNotEqualTo(sonar)
            assertThat(SonarWorkflowGuard.triggerViolations(planted)).`as`(trigger).isNotEmpty()
        }
    }

    @Test
    fun aMissingScheduleIsCaught() {
        val planted = sonar.replaceFirst(Regex("  schedule:\n    - cron: \"[^\"]+\"\n"), "")
        assertThat(planted).isNotEqualTo(sonar)
        assertThat(SonarWorkflowGuard.triggerViolations(planted)).isNotEmpty()
    }

    @Test
    fun anUnpinnedActionIsCaught() {
        for (bad in listOf("uses: foo/bar@v1", "uses: foo/bar@0123456789abcdef0123456789abcdef01234567",
            "uses: foo/bar@main # v1", "uses: ./.github/actions/local")) {
            val planted = sonar.replaceFirst(
                "      - name: Set up Gradle\n",
                "      - name: Planted\n        $bad\n\n      - name: Set up Gradle\n",
            )
            assertThat(planted).isNotEqualTo(sonar)
            assertThat(SonarWorkflowGuard.pinViolations(planted)).`as`(bad).isNotEmpty()
        }
    }

    @Test
    fun theTokenAnywhereElseIsCaught() {
        val inABuildStep = sonar.replaceFirst(
            "        run: ./gradlew syncPacks",
            "        env:\n          SONAR_TOKEN: \${{ secrets.SONAR_TOKEN }}\n        run: ./gradlew syncPacks",
        )
        val atJobLevel = sonar.replaceFirst(
            "    timeout-minutes: 30\n",
            "    timeout-minutes: 30\n    env:\n      SONAR_TOKEN: \${{ secrets.SONAR_TOKEN }}\n",
        )
        val inAnArgument = sonar.replaceFirst(
            "            -Dsonar.java.source=25\n",
            "            -Dsonar.java.source=25\n            -Dsonar.token=\${{ secrets.SONAR_TOKEN }}\n",
        )
        val echoedByTheScan = sonar.replaceFirst(
            "          echo \"present=true\"",
            "          echo \"\$SONAR_TOKEN\"\n          echo \"present=true\"",
        )
        for (planted in listOf(inABuildStep, atJobLevel, inAnArgument, echoedByTheScan)) {
            assertThat(planted).isNotEqualTo(sonar)
            assertThat(SonarWorkflowGuard.tokenViolations(planted)).isNotEmpty()
        }
    }

    @Test
    fun aStepThatCanTurnTheRunRedOrRunWithoutTheTokenIsCaught() {
        val notContinued = sonar.replaceFirst(
            "        id: build\n        if: steps.sonar_token.outputs.enabled == 'true' && steps.sync.outcome == 'success'\n" +
                "        continue-on-error: true\n",
            "        id: build\n        if: steps.sonar_token.outputs.enabled == 'true' && steps.sync.outcome == 'success'\n",
        )
        val ungated = sonar.replaceFirst(
            "        id: gradle\n        if: steps.sonar_token.outputs.enabled == 'true'\n",
            "        id: gradle\n",
        )
        for (planted in listOf(notContinued, ungated)) {
            assertThat(planted).isNotEqualTo(sonar)
            assertThat(SonarWorkflowGuard.stepViolations(planted)).isNotEmpty()
        }
    }

    @Test
    fun aCiGateThatCallsOrWaitsForSonarIsCaught() {
        val calls = ci.replaceFirst(
            "  gate:\n",
            "  sonar:\n    name: Sonar\n    uses: ./.github/workflows/sonar.yml\n\n  gate:\n",
        )
        val waits = ci.replaceFirst("    needs: [changes, service, native, docs]\n", "    needs: [changes, service, native, docs, sonar]\n")
        for (planted in listOf(calls, waits)) {
            assertThat(planted).isNotEqualTo(ci)
            assertThat(SonarWorkflowGuard.ciViolations(planted)).isNotEmpty()
        }
    }
}

/** Line-based reading of the two workflows; whole-line comments are ignored, so a comment never satisfies a check. */
private object SonarWorkflowGuard {

    private val forbiddenTriggers = setOf("pull_request", "pull_request_target", "merge_group", "push")
    private val pinned = Regex("""^[^@\s]+@[0-9a-f]{40}\s+#\s*v\d\S*\s*$""")
    private val tokenEnvEntry = Regex("""^\s+SONAR_TOKEN: \$\{\{ secrets\.SONAR_TOKEN }}\s*$""")
    private val tokenSteps = setOf("sonar_token", "scan", "quality_gate")
    private const val TOKEN_GATE = "steps.sonar_token.outputs.enabled == 'true'"

    data class Step(val id: String?, val lines: List<String>)

    private fun lines(text: String) = text.lines().filterNot { it.trimStart().startsWith("#") }

    private fun indent(line: String) = line.length - line.trimStart().length

    /** The lines of the block opened by the first line matching [header], up to the next line at its indent or less. */
    private fun block(lines: List<String>, header: Regex): List<String> {
        val start = lines.indexOfFirst { header.matches(it) }
        if (start < 0) return emptyList()
        val level = indent(lines[start])
        return lines.drop(start + 1).takeWhile { it.isBlank() || indent(it) > level }
    }

    fun triggers(workflow: String): List<String> =
        block(lines(workflow), Regex("""^on:\s*$"""))
            .mapNotNull { Regex("""^ {2}([A-Za-z_]+):""").find(it)?.groupValues?.get(1) }

    fun triggerViolations(workflow: String): List<String> {
        val found = triggers(workflow)
        return found.filter { it in forbiddenTriggers }.map { "sonar.yml triggers on $it" } +
            listOf("schedule", "workflow_dispatch").filterNot { it in found }.map { "sonar.yml has no $it trigger" }
    }

    fun uses(workflow: String): List<String> =
        lines(workflow).mapNotNull { Regex("""^\s*(?:-\s+)?uses:\s*(.+)$""").find(it)?.groupValues?.get(1) }

    fun pinViolations(workflow: String): List<String> =
        uses(workflow).filterNot { pinned.matches(it) }.map { "not pinned to a commit with a # vX comment: $it" }

    fun steps(workflow: String): List<Step> {
        val stepLines = block(lines(workflow), Regex("""^\s+steps:\s*$"""))
        val steps = mutableListOf<MutableList<String>>()
        for (line in stepLines) {
            if (Regex("""^\s+- """).containsMatchIn(line) && indent(line) == indent(stepLines.first { it.isNotBlank() })) {
                steps += mutableListOf(line)
            } else {
                steps.lastOrNull()?.add(line)
            }
        }
        return steps.map { step ->
            Step(step.firstNotNullOfOrNull { Regex("""^\s+(?:- )?id:\s*(\S+)""").find(it)?.groupValues?.get(1) }, step)
        }
    }

    /**
     * `secrets.SONAR_TOKEN` only as an `env` entry of the probe, the scan and the gate (the gate action queries
     * SonarCloud with it); `SONAR_TOKEN` in a script only in the probe's.
     */
    fun tokenViolations(workflow: String): List<String> {
        val all = lines(workflow).filter { "SONAR_TOKEN" in it }
        val violations = mutableListOf<String>()
        for (step in steps(workflow)) {
            val envEntries = block(step.lines, Regex("""^\s+env:\s*$""")).filter { "SONAR_TOKEN" in it }
            for (line in step.lines.filter { "SONAR_TOKEN" in it }) {
                val inEnv = line in envEntries && tokenEnvEntry.matches(line)
                // The probe's own script tests the variable and names it in its notice; no other step's script may.
                val probeRead = step.id == "sonar_token" && "secrets." !in line
                if (!((inEnv && step.id in tokenSteps) || probeRead)) {
                    violations += "SONAR_TOKEN in step ${step.id}: ${line.trim()}"
                }
            }
        }
        val outsideSteps = all.size - steps(workflow).sumOf { s -> s.lines.count { "SONAR_TOKEN" in it } }
        if (outsideSteps > 0) violations += "SONAR_TOKEN outside the steps ($outsideSteps line(s))"
        if (tokenSteps.any { id -> steps(workflow).none { it.id == id && it.lines.any(tokenEnvEntry::matches) } }) {
            violations += "the probe, the scan or the gate does not take SONAR_TOKEN from its env"
        }
        return violations
    }

    /** Every step after the probe is gated on the probe and is continue-on-error. */
    fun stepViolations(workflow: String): List<String> =
        steps(workflow).drop(1).flatMap { step ->
            val name = step.lines.first().trim()
            listOfNotNull(
                if (step.lines.none { it.trimStart().startsWith("if:") } || step.lines.none { TOKEN_GATE in it })
                    "$name is not gated on the token probe" else null,
                if (step.lines.none { Regex("""^\s+continue-on-error:\s*true\s*$""").matches(it) })
                    "$name is not continue-on-error" else null,
            )
        }

    fun gateNeeds(ci: String): List<String> {
        val gate = block(lines(ci), Regex("""^ {2}gate:\s*$"""))
        val needs = gate.firstNotNullOfOrNull { Regex("""^ {4}needs:\s*\[(.*)]\s*$""").find(it)?.groupValues?.get(1) }
            ?: return emptyList()
        return needs.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun ciViolations(ci: String): List<String> =
        lines(ci).filter { "sonar" in it.lowercase() }.map { "ci.yml mentions sonar: ${it.trim()}" } +
            gateNeeds(ci).filter { "sonar" in it.lowercase() }.map { "the gate needs $it" }
}
