import CheckstyleRegister.Kind
import CheckstyleRegister.Violation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The exception register's stale-entry and format check (ADR-0058). */
class CheckstyleRegisterTest {

    @TempDir
    lateinit var root: File

    private val alphaPath = "lib/src/main/java/org/example/Alpha.java"
    private val betaPath = "lib/src/test/java/org/example/Beta.java"

    private val alphaSource = """
        package org.example;

        final class Alpha {
            @Override
            public String toString() {
                return "alpha";
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Deprecated(since = "1 (old)")
            static <T extends Comparable<T>> java.util.List<T> sorted(
                    java.util.List<T> input) {
                return input;
            }

            void nested(int a, int b) {
                if (a > 0) {
                    Runnable r = () -> {
                        System.out.println(a);
                    };
                    if (b > 0) {
                        r.run();
                    }
                }
            }

            Alpha(int a) {
                if (a > 0) {
                    if (a > 1) {
                        System.out.println(a);
                    }
                }
            }
        }
    """.trimIndent()

    private fun comment(
        rule: String? = "CyclomaticComplexity",
        file: String? = alphaPath,
        reason: String? = "A table that reads best in one place.",
        sunset: String? = "2027-05-04",
    ) = buildString {
        append("    <!--\n")
        rule?.let { append("      rule:   $it\n") }
        file?.let { append("      file:   $it\n") }
        reason?.let { append("      reason: $it\n") }
        sunset?.let { append("      sunset: $it\n") }
        append("    -->\n")
    }

    private fun xpath(checks: String, files: String, method: String) =
        "    <suppress-xpath checks=\"$checks\" files=\"$files\"\n" +
            "                    query=\"//CLASS_DEF//METHOD_DEF[./IDENT[@text='$method']]\"/>\n"

    private fun register(size: Int, vararg entries: String) = """
        |<?xml version="1.0"?>
        |<!DOCTYPE suppressions PUBLIC
        |        "-//Checkstyle//DTD SuppressionXpathFilter Experimental Configuration 1.2//EN"
        |        "https://unreachable.invalid/dtds/suppressions_1_2_xpath_experimental.dtd">
        |<!--
        |  The register.
        |
        |  Size: $size entries.
        |-->
        |<suppressions>
        |${entries.joinToString("")}</suppressions>
        |""".trimMargin()

    private fun write(path: String, text: String = alphaSource): File =
        root.resolve(path).apply {
            parentFile.mkdirs()
            writeText(text)
        }

    private fun verify(xml: String, violations: List<Violation>, module: String = "lib") =
        CheckstyleRegister.verify(
            CheckstyleRegister.parse(xml),
            violations,
            { file, n -> file.readLines().getOrNull(n - 1) },
            root,
            root.resolve(module),
        )

    private fun at(check: String, path: String, line: Int) = Violation(check, root.resolve(path).absolutePath, line)

    private fun List<CheckstyleRegister.Finding>.kinds() = map { it.kind }

    private fun List<CheckstyleRegister.Finding>.only() = also { assertThat(it).hasSize(1) }.single()

    private fun lineOf(text: String, needle: String) = text.lines().indexOfFirst { needle in it } + 1

    @Test
    fun parsesEachEntryWithItsRulesMethodAndCommentFields() {
        val parsed = CheckstyleRegister.parse(
            register(1, comment(rule = "CyclomaticComplexity, NPathComplexity") +
                xpath("CyclomaticComplexity|NPathComplexity", "[/\\\\]Alpha\\.java", "toString")),
        )

        assertThat(parsed.statedSize).isEqualTo(1)
        parsed.entries.single().let { entry ->
            assertThat(entry.rules).containsExactlyInAnyOrder("CyclomaticComplexity", "NPathComplexity")
            assertThat(entry.fileRegex).isEqualTo("[/\\\\]Alpha\\.java")
            assertThat(entry.method).isEqualTo("toString")
            assertThat(entry.commentFields).containsEntry("file", alphaPath).containsEntry("sunset", "2027-05-04")
            assertThat(entry.line).isGreaterThan(10)
        }
    }

    @Test
    fun aFileWideSuppressIsReadWithoutAMethod() {
        val parsed = CheckstyleRegister.parse(register(1, comment() + "    <suppress checks=\"MagicNumber\" files=\"Alpha\\.java\"/>\n"))

        assertThat(parsed.entries.single().method).isNull()
        assertThat(parsed.entries.single().rules).containsExactly("MagicNumber")
    }

    @Test
    fun theDoctypeIsNeverFetchedSoAnUnreachableDtdStillParses() {
        // register() names a DTD on a host that does not resolve; loading it would fail the parse.
        assertThat(CheckstyleRegister.parse(register(0)).entries).isEmpty()
    }

    @Test
    fun aMatchingEntryIsLive() {
        write(alphaPath)
        val xml = register(1, comment() + xpath("CyclomaticComplexity", "Alpha\\.java", "toString"))

        assertThat(verify(xml, listOf(at("CyclomaticComplexity", alphaPath, lineOf(alphaSource, "@Override"))))).isEmpty()
    }

    @Test
    fun anEntryListingTwoRulesWhereOnlyOneFiresIsStaleNamingTheRuleToDrop() {
        write(alphaPath)
        val xml = register(1, comment() + xpath("CyclomaticComplexity|NPathComplexity", "Alpha\\.java", "toString"))

        verify(xml, listOf(at("CyclomaticComplexity", alphaPath, 4))).only().let { finding ->
            assertThat(finding.kind).isEqualTo(Kind.STALE)
            assertThat(finding.message).contains("NPathComplexity", "toString", "narrow checks to CyclomaticComplexity")
        }
    }

    @Test
    fun anEntryWhoseMethodNoLongerViolatesIsStale() {
        write(alphaPath)
        val xml = register(1, comment() + xpath("MethodLength", "Alpha\\.java", "toString"))
        val elsewhere = at("MethodLength", alphaPath, lineOf(alphaSource, "@SuppressWarnings"))

        verify(xml, listOf(elsewhere)).only().let { finding ->
            assertThat(finding.kind).isEqualTo(Kind.STALE)
            assertThat(finding.message).contains("MethodLength", "toString", "delete the entry")
            assertThat(finding.toString()).startsWith("STALE suppressions.xml:")
        }
    }

    @Test
    fun aViolationInAFileTheRegexDoesNotMatchDoesNotKeepTheEntryLive() {
        write(alphaPath)
        write(betaPath)
        val xml = register(1, comment() + xpath("CyclomaticComplexity", "[/\\\\]Alpha\\.java", "toString"))

        assertThat(verify(xml, listOf(at("CyclomaticComplexity", betaPath, 4))).kinds())
            .containsExactly(Kind.STALE)
    }

    @Test
    fun aMissingReasonOrABadSunsetIsAMissingField() {
        write(alphaPath)
        val live = listOf(at("CyclomaticComplexity", alphaPath, 4))
        val noReason = register(1, comment(reason = null) + xpath("CyclomaticComplexity", "Alpha\\.java", "toString"))
        val badSunset = register(1, comment(sunset = "May 2027") + xpath("CyclomaticComplexity", "Alpha\\.java", "toString"))
        val blankRule = register(1, comment(rule = "") + xpath("CyclomaticComplexity", "Alpha\\.java", "toString"))

        verify(noReason, live).only().let { finding ->
            assertThat(finding.kind).isEqualTo(Kind.MISSING_FIELD)
            assertThat(finding.message).contains("reason:")
        }
        verify(badSunset, live).only().let { finding ->
            assertThat(finding.kind).isEqualTo(Kind.MISSING_FIELD)
            assertThat(finding.message).contains("May 2027", "ISO date")
        }
        assertThat(verify(blankRule, live).kinds()).containsExactly(Kind.MISSING_FIELD)
    }

    @Test
    fun aSizeThatMiscountsTheEntriesIsASizeMismatch() {
        write(alphaPath)
        val xml = register(
            3,
            comment() + xpath("CyclomaticComplexity", "Alpha\\.java", "toString"),
            comment(rule = "MethodLength") + xpath("MethodLength", "Alpha\\.java", "sorted"),
        )
        val live = listOf(at("CyclomaticComplexity", alphaPath, 4), at("MethodLength", alphaPath, 9))

        verify(xml, live).only().let { finding ->
            assertThat(finding.kind).isEqualTo(Kind.SIZE_MISMATCH)
            assertThat(finding.message).contains("Size: 3", "holds 2")
        }
    }

    @Test
    fun aHeaderWithoutSizeIsASizeMismatch() {
        val xml = register(0).replace("Size: 0 entries.", "")

        assertThat(verify(xml, emptyList()).kinds()).containsExactly(Kind.SIZE_MISMATCH)
    }

    @Test
    fun aFileThatDoesNotExistIsAMissingFileInEveryModule() {
        val xml = register(1, comment() + xpath("CyclomaticComplexity", "Alpha\\.java", "toString"))

        assertThat(verify(xml, emptyList(), module = "elsewhere").kinds())
            .containsExactly(Kind.MISSING_FILE)
    }

    @Test
    fun livenessIsJudgedOnlyInTheModuleThatHoldsTheFile() {
        write(alphaPath)
        val xml = register(1, comment() + xpath("CyclomaticComplexity", "Alpha\\.java", "toString"))

        assertThat(verify(xml, emptyList(), module = "elsewhere")).isEmpty()
        assertThat(verify(xml, emptyList(), module = "lib").kinds()).containsExactly(Kind.STALE)
    }

    @Test
    fun aFileWideEntryIsLiveOnAnyViolationOfItsRuleInAMatchingFile() {
        write(alphaPath)
        val xml = register(1, comment(rule = "MagicNumber") + "    <suppress checks=\"MagicNumber\" files=\"Alpha\\.java\"/>\n")

        assertThat(verify(xml, listOf(at("MagicNumber", alphaPath, 30)))).isEmpty()
        assertThat(verify(xml, emptyList()).kinds()).containsExactly(Kind.STALE)
    }

    @Test
    fun aProbeViolationWithNoReadableDeclarationFailsLoudly() {
        write(alphaPath)
        val xml = register(1, comment() + xpath("CyclomaticComplexity", "Alpha\\.java", "toString"))
        val violations = listOf(
            at("CyclomaticComplexity", alphaPath, 4),
            at("NPathComplexity", alphaPath, 1),
        )

        verify(xml, violations).only().let { finding ->
            assertThat(finding.kind).isEqualTo(Kind.UNMAPPED)
            assertThat(finding.message).contains("NPathComplexity", "Alpha.java:1")
        }
    }

    @Test
    fun aMethodScopedEntryForARuleThatCannotBeTiedToAMethodFailsLoudly() {
        write(alphaPath)
        val xml = register(1, comment(rule = "MagicNumber") + xpath("MagicNumber", "Alpha\\.java", "toString"))

        assertThat(verify(xml, listOf(at("MagicNumber", alphaPath, 6))).kinds())
            .containsExactly(Kind.UNMAPPED)
    }

    @Test
    fun anAnnotatedDeclarationMapsToItsMethod() {
        val line = { n: Int -> alphaSource.lines().getOrNull(n - 1) }

        assertThat(CheckstyleRegister.methodOf("CyclomaticComplexity", lineOf(alphaSource, "@Override"), line))
            .isEqualTo("toString")
        assertThat(CheckstyleRegister.methodOf("MethodLength", lineOf(alphaSource, "@SuppressWarnings"), line))
            .isEqualTo("sorted")
        assertThat(CheckstyleRegister.methodOf("NPathComplexity", lineOf(alphaSource, "static <T"), line))
            .isEqualTo("sorted")
        assertThat(CheckstyleRegister.methodOf("MethodLength", lineOf(alphaSource, "Alpha(int a)"), line))
            .isEqualTo("Alpha")
    }

    @Test
    fun aCompactRecordConstructorMapsToItsName() {
        val source = listOf("record Point(int x) {", "    public Point {", "        if (x < 0) throw new X();", "    }", "}")

        assertThat(CheckstyleRegister.methodOf("MethodLength", 2) { source.getOrNull(it - 1) }).isEqualTo("Point")
    }

    @Test
    fun nestedIfDepthMapsToTheEnclosingMethodPastALambda() {
        val line = { n: Int -> alphaSource.lines().getOrNull(n - 1) }

        assertThat(CheckstyleRegister.methodOf("NestedIfDepth", lineOf(alphaSource, "if (b > 0)"), line))
            .isEqualTo("nested")
        assertThat(CheckstyleRegister.methodOf("NestedIfDepth", lineOf(alphaSource, "if (a > 1)"), line))
            .isEqualTo("Alpha")
    }

    @Test
    fun statementsAreNotDeclarations() {
        for (statement in listOf(
            "        return foo(a);", "        String s = format(a);", "        foo.bar(a);", "        } else if (a) {",
            "        throw new IllegalStateException(a);", "        case A -> handle(a);", "        for (int i = 0; ;) {",
            "        assertThat(a).isTrue();", "        Objects.requireNonNull(a);", "        yield compute(a);",
        )) {
            assertThat(CheckstyleRegister.declarationName(statement)).`as`(statement).isNull()
        }
        assertThat(CheckstyleRegister.declarationName("    private static int count(String a) {")).isEqualTo("count")
        assertThat(CheckstyleRegister.declarationName("    Map<String, List<Integer>> index(")).isEqualTo("index")
    }

    @Test
    fun aReportRestoredFromAnotherCheckoutIsReRootedOntoThisModule() {
        write(alphaPath)
        val xml = register(1, comment() + xpath("CyclomaticComplexity", "[/\\\\]Alpha\\.java", "toString"))
        val elsewhere = "/home/other/charter/.worktrees/mission-lane-b/$alphaPath"
        val report = """
            <?xml version="1.0" encoding="UTF-8"?>
            <checkstyle version="14.3.0">
            <file name="$elsewhere">
            <error line="${lineOf(alphaSource, "@Override")}" column="5" severity="error" message="m" source="com.puppycrawl.tools.checkstyle.checks.metrics.CyclomaticComplexityCheck"/>
            </file>
            </checkstyle>
        """.trimIndent()

        assertThat(verify(xml, CheckstyleRegister.parseReport(report))).isEmpty()
        assertThat(CheckstyleRegister.relocate(elsewhere, root.resolve("lib")))
            .isEqualTo(root.resolve("lib").canonicalFile.resolve("src/main/java/org/example/Alpha.java"))
    }

    @Test
    fun aReportedFileThatExistsOnlyInAnotherCheckoutIsNotReadAndFailsLoudly() {
        write(alphaPath)
        val other = root.resolve("other-checkout")
        write("other-checkout/lib/src/main/java/org/example/Gamma.java")
        val xml = register(1, comment() + xpath("CyclomaticComplexity", "Alpha\\.java", "toString"))
        val violations = listOf(
            at("CyclomaticComplexity", alphaPath, 4),
            Violation("NPathComplexity", other.resolve("lib/src/main/java/org/example/Gamma.java").absolutePath, 4),
        )

        verify(xml, violations).only().let { finding ->
            assertThat(finding.kind).isEqualTo(Kind.UNMAPPED)
            assertThat(finding.message).contains("Gamma.java:4", "no such file")
        }
    }

    @Test
    fun aCheckstyleReportIsReadAsSimpleCheckNamesFilesAndLines() {
        val report = """
            <?xml version="1.0" encoding="UTF-8"?>
            <checkstyle version="14.3.0">
            <file name="/repo/lib/Alpha.java">
            <error line="12" column="5" severity="error" message="m" source="com.puppycrawl.tools.checkstyle.checks.metrics.NPathComplexityCheck"/>
            </file>
            <file name="/repo/lib/Beta.java">
            </file>
            </checkstyle>
        """.trimIndent()

        assertThat(CheckstyleRegister.parseReport(report))
            .containsExactly(Violation("NPathComplexity", "/repo/lib/Alpha.java", 12))
    }
}
