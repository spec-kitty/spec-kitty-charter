import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The nightly mutation floor (#239): lenient reading of PIT's mutations.xml, scoring per group, and the verdict. */
class MutationFloorsTest {

    private val kernel = MutationFloors.Group("kernel", listOf("ai.speckitty.charter.kernel"), emptyList(), 65)

    @Test
    fun aRawLessThanInADescriptionDoesNotStopTheReading() {
        val parsed = MutationFloors.parse(report(killed = 2, survived = 1, noCoverage = 1))
        val mutants = parsed.mutants

        assertThat(parsed.unreadable).isZero()
        assertThat(mutants).hasSize(4)
        assertThat(mutants.map { it.status }).containsExactly("KILLED", "KILLED", "SURVIVED", "NO_COVERAGE")
        assertThat(mutants).allSatisfy { assertThat(it.mutatedClass).isEqualTo("ai.speckitty.charter.kernel.domain.A") }
    }

    @Test
    fun everyPitStatusIsCountedAsPitCountsIt() {
        val xml = mutations(*PIT_STATUSES.keys.map { mutation("ai.speckitty.charter.kernel.domain.A", it) }.toTypedArray())

        val result = MutationFloors.score(MutationFloors.parse(xml).mutants, listOf(kernel)).single()

        assertThat(MutationFloors.DETECTED).isEqualTo(PIT_STATUSES.filterValues { it }.keys)
        assertThat(result.mutants).isEqualTo(10)
        assertThat(result.detected).isEqualTo(6)
        assertThat(result.survived).isEqualTo(1)
        assertThat(result.noCoverage).isEqualTo(1)
        for ((status, detected) in PIT_STATUSES) {
            val one = MutationFloors.score(
                MutationFloors.parse(mutations(mutation("ai.speckitty.charter.kernel.domain.A", status))).mutants,
                listOf(kernel),
            ).single()
            assertThat(one.detected).describedAs(status).isEqualTo(if (detected) 1 else 0)
        }
    }

    @Test
    fun theScoreCountsEveryDetectedStatusOverAllMutantsAndTheStrengthOverTheCoveredOnes() {
        val xml = mutations(
            mutation("ai.speckitty.charter.kernel.domain.A", "KILLED"),
            mutation("ai.speckitty.charter.kernel.domain.A", "TIMED_OUT"),
            mutation("ai.speckitty.charter.kernel.domain.A", "MEMORY_ERROR"),
            mutation("ai.speckitty.charter.kernel.domain.A", "RUN_ERROR"),
            mutation("ai.speckitty.charter.kernel.domain.A", "SURVIVED"),
            mutation("ai.speckitty.charter.kernel.domain.A", "SURVIVED"),
            mutation("ai.speckitty.charter.kernel.domain.A", "NO_COVERAGE"),
            mutation("ai.speckitty.charter.kernel.domain.A", "NO_COVERAGE"),
        )

        val result = MutationFloors.score(MutationFloors.parse(xml).mutants, listOf(kernel)).single()

        assertThat(result.mutants).isEqualTo(8)
        assertThat(result.detected).isEqualTo(4)
        assertThat(result.survived).isEqualTo(2)
        assertThat(result.noCoverage).isEqualTo(2)
        assertThat(result.score).isEqualTo(50.0)
        assertThat(result.strength).isEqualTo(4 * 100.0 / 6)
    }

    @Test
    fun aGroupBelowItsFloorFailsNamingTheModuleTheGroupTheScoreAndTheFloor() {
        // 64 of 100 detected: 64.0 % against a floor of 65 %.
        val result = scoreKernel(killed = 64, survived = 30, noCoverage = 6)

        assertThat(result.passes).isFalse()
        assertThat(MutationFloors.failures(":kernel", listOf(result))).containsExactly(
            ":kernel kernel: mutation score 64.0 % is below its floor 65 % (#239, ADR-0062)",
        )
    }

    @Test
    fun aScoreJustUnderTheFloorFailsThoughPitWouldRoundItUp() {
        // 129 of 200 is 64.5 %, which PIT's integer percentage rounds to 65.
        val result = scoreKernel(killed = 129, survived = 71, noCoverage = 0)

        assertThat(result.failure(":kernel")).isEqualTo(
            ":kernel kernel: mutation score 64.5 % is below its floor 65 % (#239, ADR-0062)",
        )
    }

    @Test
    fun aGroupExactlyAtItsFloorPasses() {
        val result = scoreKernel(killed = 13, survived = 5, noCoverage = 2)

        assertThat(result.score).isEqualTo(65.0)
        assertThat(result.passes).isTrue()
        assertThat(result.failure(":kernel")).isNull()
    }

    @Test
    fun aGroupOverItsFloorPasses() {
        val result = scoreKernel(killed = 9, survived = 1, noCoverage = 0)

        assertThat(result.passes).isTrue()
        assertThat(MutationFloors.failures(":kernel", listOf(result))).isEmpty()
    }

    @Test
    fun groupsTakeTheirPackagesAndSubPackagesButNotTheExcludedOnesNorLookAlikes() {
        val packs = MutationFloors.Group(
            "domain+application",
            listOf("ai.speckitty.charter.packs.domain", "ai.speckitty.charter.packs.application"),
            listOf("ai.speckitty.charter.packs.application.config"),
            70,
        )
        val storage = MutationFloors.Group("infra.storage", listOf("ai.speckitty.charter.packs.infra.storage"), emptyList(), 50)
        val xml = mutations(
            mutation("ai.speckitty.charter.packs.domain.Pack", "KILLED"),
            mutation("ai.speckitty.charter.packs.domain.model.Pack\$Entry", "SURVIVED"),
            mutation("ai.speckitty.charter.packs.application.Load", "KILLED"),
            mutation("ai.speckitty.charter.packs.application.config.Wiring", "SURVIVED"),
            mutation("ai.speckitty.charter.packs.domainextra.Other", "SURVIVED"),
            mutation("ai.speckitty.charter.packs.infra.storage.FileStore", "NO_COVERAGE"),
            mutation("ai.speckitty.charter.packs.infra.web.Controller", "SURVIVED"),
        )

        val (domainApplication, infraStorage) = MutationFloors.score(MutationFloors.parse(xml).mutants, listOf(packs, storage))

        assertThat(domainApplication.mutants).isEqualTo(3)
        assertThat(domainApplication.detected).isEqualTo(2)
        assertThat(domainApplication.topClasses)
            .containsExactly(MutationFloors.ClassGaps("ai.speckitty.charter.packs.domain.model.Pack", 1, 0))
        assertThat(infraStorage.mutants).isEqualTo(1)
        assertThat(infraStorage.noCoverage).isEqualTo(1)
        assertThat(infraStorage.failure(":packs")).isEqualTo(
            ":packs infra.storage: mutation score 0.0 % is below its floor 50 % (#239, ADR-0062)",
        )
    }

    @Test
    fun theTopClassesAreTheFiveWithTheMostSurvivingAndUncoveredMutants() {
        val xml = mutations(
            *(1..7).flatMap { n ->
                List(n) { mutation("ai.speckitty.charter.kernel.domain.C$n", if (it % 2 == 0) "SURVIVED" else "NO_COVERAGE") }
            }.toTypedArray(),
            mutation("ai.speckitty.charter.kernel.domain.C9", "KILLED"),
        )

        val top = MutationFloors.score(MutationFloors.parse(xml).mutants, listOf(kernel)).single().topClasses

        assertThat(top.map { it.className.substringAfterLast('.') }).containsExactly("C7", "C6", "C5", "C4", "C3")
        assertThat(top.first()).isEqualTo(MutationFloors.ClassGaps("ai.speckitty.charter.kernel.domain.C7", 4, 3))
    }

    @Test
    fun anEmptyReportHasNoMutantsAndFailsRatherThanPassingSilently() {
        val mutants = MutationFloors.parse(EMPTY_REPORT).mutants
        val result = MutationFloors.score(mutants, listOf(kernel)).single()

        assertThat(mutants).isEmpty()
        assertThat(MutationFloors.parse("")).isEqualTo(MutationFloors.Report(emptyList(), 0))
        assertThat(result.mutants).isZero()
        assertThat(result.score).isZero()
        assertThat(result.strength).isZero()
        assertThat(result.passes).isFalse()
        assertThat(result.failure(":kernel"))
            .startsWith(":kernel kernel: no mutant lies in its packages")
            .endsWith("(#239, ADR-0062)")
    }

    @Test
    fun theSummaryHoldsOneEntryPerGroupWithItsFigures() {
        val result = scoreKernel(killed = 2, survived = 1, noCoverage = 1)

        // The module has 7 mutants, the group 4 of them: the top-level count and the group's are told apart.
        val json = MutationFloors.toJson(":kernel", 7, listOf(result))

        assertThat(json).contains("\n  \"mutants\": 7,\n  \"groups\": [\n", "\n      \"mutants\": 4,\n")
        assertThat(json).doesNotContain("\"mutants\": 4,\n  \"groups\"", "      \"mutants\": 7")
        assertThat(json).contains(
            "\"project\": \":kernel\"",
            "\"group\": \"kernel\"",
            "\"detected\": 2",
            "\"survived\": 1",
            "\"noCoverage\": 1",
            "\"score\": 50.0",
            "\"strength\": 66.6",
            "\"floor\": 65",
            "\"passed\": false",
            "{\"class\": \"ai.speckitty.charter.kernel.domain.A\", \"survived\": 1, \"noCoverage\": 1}",
        )
        assertThat(MutationFloors.toJson(":x\"y", 0, emptyList())).contains("\"project\": \":x\\\"y\"", "\"groups\": []")
    }

    @Test
    fun theMarginIsTheDetectedMutantsAGroupCanLoseAndStillPassAndItsPointsOverTheFloor() {
        // 70 of 100 against 65 %: 65 must be detected, so 5 can be lost; 70.0 - 65 = +5.0 points.
        val over = scoreKernel(killed = 70, survived = 30, noCoverage = 0)
        assertThat(over.marginMutants).isEqualTo(5)
        assertThat(over.marginPoints).isEqualTo("5.0")

        // 13 of 20 is exactly 65 %: one lost mutant fails it.
        val at = scoreKernel(killed = 13, survived = 5, noCoverage = 2)
        assertThat(at.marginMutants).isZero()
        assertThat(at.marginPoints).isEqualTo("0.0")

        // 5 of 7 against 65 %: 4.55 must be detected, so 5 must, and none can be lost; 71.4 - 65 = 6.4 points.
        val rounded = scoreKernel(killed = 5, survived = 2, noCoverage = 0)
        assertThat(rounded.marginMutants).isZero()
        assertThat(rounded.marginPoints).isEqualTo("6.4")

        // Below the floor the margin is negative: the mutants it must gain, and the points it lacks, truncated as the
        // score is (64.95 % reads 64.9 %, so -0.1 points, never -0.0).
        val under = scoreKernel(killed = 1299, survived = 701, noCoverage = 0)
        assertThat(under.marginMutants).isEqualTo(-1)
        assertThat(under.marginPoints).isEqualTo("-0.1")
        assertThat(scoreKernel(killed = 64, survived = 36, noCoverage = 0).marginPoints).isEqualTo("-1.0")
    }

    @Test
    fun timedOutMutantsAreCountedApartThoughTheyCountAsDetected() {
        val xml = mutations(
            mutation("ai.speckitty.charter.kernel.domain.A", "KILLED"),
            mutation("ai.speckitty.charter.kernel.domain.A", "TIMED_OUT"),
            mutation("ai.speckitty.charter.kernel.domain.A", "TIMED_OUT"),
            mutation("ai.speckitty.charter.kernel.domain.A", "SURVIVED"),
        )

        val result = MutationFloors.score(MutationFloors.parse(xml).mutants, listOf(kernel)).single()

        assertThat(result.timedOut).isEqualTo(2)
        assertThat(result.detected).isEqualTo(3)
        assertThat(MutationFloors.toJson(":kernel", 4, listOf(result))).contains(
            "\"timedOut\": 2,\n",
            "\"marginMutants\": 0,\n",
            "\"marginPoints\": 10.0,\n",
        )
    }

    @Test
    fun aFailingScoreIsTruncatedNeverRoundedUpToItsFloor() {
        // 1299 of 2000 is 64.95 %: rounded to one decimal it would read 65.0 %, its floor.
        val result = scoreKernel(killed = 1299, survived = 701, noCoverage = 0)

        assertThat(result.failure(":kernel")).isEqualTo(
            ":kernel kernel: mutation score 64.9 % is below its floor 65 % (#239, ADR-0062)",
        )
        assertThat(MutationFloors.percent(2, 3)).isEqualTo("66.6")
        assertThat(MutationFloors.percent(13, 20)).isEqualTo("65.0")
        assertThat(MutationFloors.percent(1, 0)).isEqualTo("0.0")
    }

    @Test
    fun aMutationWithoutStatusOrClassIsCountedAndRefusesTheReport() {
        val xml = mutations(
            mutation("ai.speckitty.charter.kernel.domain.A", "KILLED"),
            mutation("ai.speckitty.charter.kernel.domain.A", "KILLED").replace(" status='KILLED'", ""),
            mutation("ai.speckitty.charter.kernel.domain.A", "SURVIVED").replace("<mutatedClass>", "<mutatedKlass>"),
        )

        assertThat(MutationFloors.parse(xml).unreadable).isEqualTo(2)
        val outcome = MutationFloors.evaluate(":kernel", "build/reports/pitest/mutations.xml", xml, listOf(kernel))
        assertThat(outcome.summary).isNull()
        assertThat(outcome.failures).containsExactly(
            ":kernel: 2 <mutation> element(s) in build/reports/pitest/mutations.xml lack a status or a mutatedClass," +
                " so the report cannot be scored (#239, ADR-0062)",
        )
    }

    @Test
    fun aMissingReportFailsWithoutASummary() {
        val outcome = MutationFloors.evaluate(":kernel", "build/reports/pitest/mutations.xml", null, listOf(kernel))

        assertThat(outcome.summary).isNull()
        assertThat(outcome.lines).isEmpty()
        assertThat(outcome.failures).containsExactly(
            ":kernel: there is no PIT report at build/reports/pitest/mutations.xml, so no floor can be checked",
        )
    }

    @Test
    fun aModuleWithoutGroupsFails() {
        val outcome = MutationFloors.evaluate(":kernel", "mutations.xml", EMPTY_REPORT, emptyList())

        assertThat(outcome.summary).isNull()
        assertThat(outcome.failures).containsExactly(
            ":kernel applies charter.mutation-conventions but declares no mutationFloors group",
        )
    }

    @Test
    fun aScoredReportGivesTheLinesTheSummaryAndTheFailures() {
        val outcome = MutationFloors.evaluate(
            ":kernel",
            "mutations.xml",
            report(killed = 2, survived = 1, noCoverage = 1),
            listOf(kernel),
        )

        assertThat(outcome.lines).containsExactly(":kernel kernel: 4 mutants, score 50.0 %, strength 66.6 %, floor 65 %")
        assertThat(outcome.summary).contains("\"group\": \"kernel\"", "\"passed\": false")
        assertThat(outcome.failures).containsExactly(":kernel kernel: mutation score 50.0 % is below its floor 65 % (#239, ADR-0062)")
    }

    @Test
    fun aGroupMustNameAPackageAndAPercentage() {
        assertThatThrownBy { MutationFloors.Group("g", emptyList(), emptyList(), 50) }
            .hasMessageContaining("names no package")
        assertThatThrownBy { MutationFloors.Group("g", listOf("a"), emptyList(), 101) }
            .hasMessageContaining("not a percentage")
    }

    /**
     * The key contract with `scripts/ci/mutation_summary.py`: this summary, written by [MutationFloors.toJson], is the
     * committed fixture `scripts/ci/fixtures/floors.json` that the Python tests read. A change to the summary's keys or
     * layout fails here until the fixture is rewritten, and then fails the Python tests until the reader follows:
     *
     *     ./gradlew -p build-logic test --tests MutationFloorsTest -PupdateMutationFixture=true
     */
    @Test
    fun theCommittedSummaryFixtureIsWhatToJsonWrites() {
        val packs = MutationFloors.Group(
            "domain+application",
            listOf("ai.speckitty.charter.packs.application", "ai.speckitty.charter.packs.domain"),
            listOf("ai.speckitty.charter.packs.application.config"),
            85,
        )
        val storage = MutationFloors.Group("infra.storage", listOf("ai.speckitty.charter.packs.infra.storage"), emptyList(), 85)
        val xml = mutations(
            *(List(9) { mutation("ai.speckitty.charter.packs.domain.Pack", "KILLED") } +
                mutation("ai.speckitty.charter.packs.domain.Pack", "SURVIVED") +
                List(3) { mutation("ai.speckitty.charter.packs.infra.storage.FileStore", "KILLED") } +
                mutation("ai.speckitty.charter.packs.infra.storage.FileStore\$Entry", "NO_COVERAGE")).toTypedArray(),
        )
        val mutants = MutationFloors.parse(xml).mutants
        val json = MutationFloors.toJson(":packs", mutants.size, MutationFloors.score(mutants, listOf(packs, storage)))

        val fixture = File(checkNotNull(System.getProperty("mutationFloors.fixture")) { "mutationFloors.fixture is not set" })
        if (System.getProperty("mutationFloors.updateFixture") == "true") {
            fixture.parentFile.mkdirs()
            fixture.writeText(json)
        }
        assertThat(fixture).describedAs("rewrite it with -PupdateMutationFixture=true").hasContent(json)
    }

    private companion object {
        /** Every status PIT writes, and whether PIT counts it as detected (`DetectionStatus.isDetected`). */
        val PIT_STATUSES: Map<String, Boolean> = linkedMapOf(
            "KILLED" to true,
            "TIMED_OUT" to true,
            "NON_VIABLE" to true,
            "MEMORY_ERROR" to true,
            "RUN_ERROR" to true,
            "EQUIVALENT" to true,
            "SURVIVED" to false,
            "NO_COVERAGE" to false,
            "NOT_STARTED" to false,
            "STARTED" to false,
        )

        const val EMPTY_REPORT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mutations partial=\"true\">\n</mutations>\n"
    }

    private fun scoreKernel(killed: Int, survived: Int, noCoverage: Int): MutationFloors.GroupResult =
        MutationFloors.score(MutationFloors.parse(report(killed, survived, noCoverage)).mutants, listOf(kernel)).single()

    /** A report of class A in kernel.domain, every mutant described with a raw `<` as PIT writes it. */
    private fun report(killed: Int, survived: Int, noCoverage: Int): String = mutations(
        *(List(killed) { "KILLED" } + List(survived) { "SURVIVED" } + List(noCoverage) { "NO_COVERAGE" })
            .map { mutation("ai.speckitty.charter.kernel.domain.A", it) }
            .toTypedArray(),
    )

    private fun mutations(vararg mutation: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mutations partial=\"true\">\n" +
            mutation.joinToString("\n") + "\n</mutations>\n"

    private fun mutation(mutatedClass: String, status: String): String =
        "<mutation detected='${checkNotNull(PIT_STATUSES[status]) { status }}' status='$status' numberOfTestsRun='1'>" +
            "<sourceFile>A.java</sourceFile><mutatedClass>$mutatedClass</mutatedClass><mutatedMethod>m</mutatedMethod>" +
            "<methodDescription>(I)Z</methodDescription><lineNumber>3</lineNumber>" +
            "<mutator>org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator</mutator>" +
            "<indexes><index>1</index></indexes><blocks><block>0</block></blocks><killingTest/>" +
            "<description>changed conditional boundary: x < 3 became x <= 3</description></mutation>"
}
