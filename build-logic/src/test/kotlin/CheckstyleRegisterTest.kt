import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.util.stream.Stream

/** The exception register's bookkeeping check (ADR-0058). */
class CheckstyleRegisterTest {

    private val today = LocalDate.of(2026, 11, 6)

    private fun register(size: String, vararg entries: String) = """
        <?xml version="1.0"?>
        <!-- The one register of exceptions.

          Size: $size.
        -->
        <suppressions>
        ${entries.joinToString("\n")}
        </suppressions>
    """.trimIndent()

    private fun comment(sunset: String = "2027-05-04", rule: String = "MethodLength") = """
        <!--
          rule:   $rule
          file:   service/missions/src/test/java/ai/speckitty/charter/missions/domain/LifecycleTest.java
          reason: The table reads best in one place.
          sunset: $sunset
        -->
    """.trimIndent()

    private fun suppress(sunset: String = "2027-05-04") =
        comment(sunset) + "\n" + """<suppress checks="MethodLength" files="LifecycleTest\.java"/>"""

    @Test
    fun anEmptyRegisterPasses() {
        assertThat(CheckstyleRegister.check(register("0 entries"), today)).isEmpty()
    }

    @Test
    fun anEntryWithAllFieldsAndALiveSunsetPasses() {
        assertThat(CheckstyleRegister.check(register("1 entry", suppress()), today)).isEmpty()
    }

    @Test
    fun aCommentOnlyEntryForAnotherGateCountsAndPasses() {
        val errorProne = comment(rule = "ErrorProne:UnusedVariable")
        assertThat(CheckstyleRegister.check(register("2 entries", suppress(), errorProne), today)).isEmpty()
    }

    @Test
    fun aCommentOnlyEntryIsCountedAgainstSize() {
        assertThat(CheckstyleRegister.check(register("0 entries", comment(rule = "NullAway")), today))
            .singleElement().asString().contains("Size: 0 entries", "holds 1")
    }

    @Test
    fun aCommentedOutSuppressIsNotAnEntry() {
        val disabled = "<!-- <suppress checks=\"MethodLength\" files=\"Old\\.java\"/> -->"
        assertThat(CheckstyleRegister.check(register("0 entries", disabled), today)).isEmpty()
    }

    @Test
    fun theLastValidDayPasses() {
        assertThat(CheckstyleRegister.check(register("1 entry", suppress("2026-11-06")), today)).isEmpty()
    }

    @Test
    fun aMissingSizeFails() {
        assertThat(CheckstyleRegister.check(register("unknown"), today))
            .singleElement().asString().contains("nothing about Size")
    }

    @Test
    fun aSuppressXpathWithoutACommentIsCountedAndFails() {
        val bare = """<suppress-xpath checks="MethodLength" query="//METHOD_DEF"/>"""
        assertThat(CheckstyleRegister.check(register("1 entry", bare), today))
            .anyMatch { it.contains("lacks 'rule:'") }.noneMatch { it.contains("the header says") }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sunsetFailures")
    fun anEntryWithABadSunsetFails(name: String, entry: String, expected: String) {
        assertThat(CheckstyleRegister.check(register("1 entry", entry), today))
            .singleElement().asString().contains(expected)
    }

    companion object {
        private val xpath = """<suppress-xpath checks="MethodLength" query="//METHOD_DEF"/>"""
        private fun commented(sunset: String) = """
            <!--
              rule:   MethodLength
              file:   Some.java
              reason: The table reads best in one place.
              sunset: $sunset
            -->
        """.trimIndent()

        @JvmStatic
        fun sunsetFailures(): Stream<Arguments> = Stream.of(
            Arguments.of("expired suppress", commented("2026-11-05") + "\n" + xpath, "sunset 2026-11-05 has passed"),
            Arguments.of("expired comment-only", commented("2026-11-05"), "sunset 2026-11-05 has passed"),
            Arguments.of("malformed date", commented("next spring") + "\n" + xpath, "'next spring' is not an ISO date"),
        )
    }
}
