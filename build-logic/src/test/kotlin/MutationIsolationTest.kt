import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** ADR-0060 (#387): no gate of the build reaches PIT or its floor check, however indirectly. */
class MutationIsolationTest {

    /** The shape of the real graph: verifyAll on each module's check, check on test; pitest beside them, not under. */
    private val real = mapOf(
        ":verifyAll" to listOf(":kernel:check", ":packs:check"),
        ":kernel:check" to listOf(":kernel:test"),
        ":packs:check" to listOf(":packs:test", ":packs:spotlessCheck"),
        ":packs:pitest" to listOf(":packs:classes"),
        ":packs:checkMutationFloors" to emptyList(),
    )

    private fun graph(edges: Map<String, List<String>>) = { path: String -> edges[path].orEmpty() }

    private fun tasks(edges: Map<String, List<String>>) = (edges.keys + edges.values.flatten()).toSet()

    @Test
    fun aGraphOfTheRealShapeHasNoViolationThoughPitestIsInTheSameInvocation() {
        assertThat(MutationIsolation.violation(tasks(real), graph(real))).isNull()
    }

    @Test
    fun verifyAllReachingPitestThroughACheckFailsNamingBoth() {
        val planted = real + (":packs:check" to listOf(":packs:test", ":packs:pitest"))

        assertThat(MutationIsolation.violation(tasks(planted), graph(planted)))
            .startsWith("Mutation testing runs only in mutation-nightly.yml")
            .contains(":verifyAll reaches :packs:pitest", ":packs:check reaches :packs:pitest")
    }

    @Test
    fun aGateReachingTheFloorCheckAloneFailsToo() {
        val planted = real + (":verifyAll" to listOf(":kernel:check", ":packs:check", ":packs:checkMutationFloors"))

        assertThat(MutationIsolation.violation(tasks(planted), graph(planted)))
            .contains(":verifyAll reaches :packs:checkMutationFloors")
    }

    @Test
    fun aDeepDependencyIsFoundAndACycleEnds() {
        val planted = mapOf(
            ":kernel:check" to listOf(":kernel:a"),
            ":kernel:a" to listOf(":kernel:b"),
            ":kernel:b" to listOf(":kernel:a", ":kernel:pitest"),
        )

        assertThat(MutationIsolation.reached(":kernel:check", graph(planted))).containsExactly(":kernel:pitest")
    }

    @Test
    fun onlyVerifyAllAndCheckAreGates() {
        assertThat(MutationIsolation.isGate(":verifyAll")).isTrue()
        assertThat(MutationIsolation.isGate(":packs:check")).isTrue()
        assertThat(MutationIsolation.isGate(":packs:pitest")).isFalse()
        assertThat(MutationIsolation.isGate(":packs:checkMutationFloors")).isFalse()
        assertThat(MutationIsolation.isGate(":packs:verifyAll")).isFalse()
    }
}
