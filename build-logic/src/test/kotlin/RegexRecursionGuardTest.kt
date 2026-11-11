import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Hand-written main code (`service/<module>/src/main/java`) does not compile a regex with a repeated group whose
 * quantifier can backtrack (FR-011, #440): a group followed by `*`, `+` or `{n,}` must make that quantifier possessive
 * (`)*+`, `)++`, `){n,}+`), be an atomic group `(?>…)`, or carry an allow-list entry with a reason.
 *
 * This guard is a heuristic, not a regex analyser. What it reads, and nothing more:
 * - the first argument of each `Pattern.compile(` call, and the `regexp =` element of a `@Pattern` annotation;
 * - an argument made only of string literals, and of same-file `static final String` constants built the same way,
 *   joined by `+` (constants resolved recursively within the one file).
 *
 * Anything else (a parameter, a method call such as `regex.toString()`, a constant from another file, a text block)
 * is reported as "cannot judge", so it must be allow-listed with a reason or rewritten. A group counts as repeated
 * only by the quantifier written right after its `)`; a bounded repeat (`{0,2}`, `{3}`) is ignored because it cannot
 * recurse without limit, and so is `?`. It does not judge nested quantifiers inside a group (`(a+)+` is flagged for
 * the outer group, not proven safe by an inner possessive), lookarounds as bounds, or ambiguity between alternatives:
 * those judgements are the allow-list's reasons. The allow-list also covers patterns compiled by a linear-time engine
 * (re2j). SonarCloud rule S5998 (regex stack overflow) is the backstop for what this scan cannot see.
 *
 * "Bounded" means a small bound: the guard ignores any `{n,m}` repeat, so keeping `m` small is the author's job.
 * `contracts/`, `client-java/` and Kotlin sources are out of scope: it reads only `service/<module>/src/main/java`.
 *
 * Entry points it does not scan at all: `String.matches`, `Pattern.matches`, the regex argument of `replaceAll`,
 * `replaceFirst` and `split`, a statically imported `compile(…)`, and text blocks anywhere but a `Pattern.compile`
 * argument (where they are reported as "cannot judge").
 *
 * The scanned tree is every `.java` file under `<service>/<module>/src/main/java`, with `<service>` passed by
 * `build.gradle.kts` as the `regexGuard.serviceDir` system property; that file declares the same tree as the task's
 * input. Allow-list keys are paths under `<service>`, with `/` separators on every platform.
 */
class RegexRecursionGuardTest {

    private val serviceDir: File = File(
        requireNotNull(System.getProperty("regexGuard.serviceDir")) { "build.gradle.kts sets regexGuard.serviceDir" },
    )

    /** An accepted finding: the [detail] it was judged on, and the [reason] that judgement holds. */
    private data class Allowed(val detail: String, val reason: String)

    /**
     * `<path under service/>:<label>` to the accepted finding; the label is the constant or variable named. A finding
     * whose detail no longer equals the entry's fails as out of date, so a changed pattern is judged again.
     */
    private val allowList = mapOf(
        "packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Re2jRegularExpressionFactory.java:regex" to
            Allowed(
                "cannot judge `regex`",
                "re2j: com.google.re2j.Pattern matches in linear time and has no backtracking;"
                    + " EmbeddedSchemasTest#aCatastrophicPatternIsEvaluatedInLinearTime covers it",
            ),
        "packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileKind.java:regex.toString()" to Allowed(
            "cannot judge `regex.toString()`",
            "built from a glob: the only group glob() emits is (?:.+/)? (optional, never repeated), the rest is"
                + " [^/]* and Pattern.quote(...); PackFileKindTest#aSingleStarStaysInOneSegmentAndDoubleStarSpansSegments"
                + " proves a very deep path does not overflow",
        ),
        "packs/src/main/java/ai/speckitty/charter/packs/domain/GuardImplementation.java:SPELLING_SHAPE" to Allowed(
            "cannot judge `\"builtin:(\" + IdentifierGrammar.SLUG + \")|(?:(\" + IdentifierGrammar.SLUG\n" +
                "            + \")::)?asset:([A-Za-z0-9_/.-]++)\"`",
            "IdentifierGrammar.SLUG is the kernel's shared kebab grammar (#444), possessive, and judged by this guard"
                + " where IdentifierGrammar compiles it; here it sits in capture groups, one optional, none repeated",
        ),
        "missions/src/main/java/ai/speckitty/charter/missions/domain/GuardReference.java:SPELLING" to Allowed(
            "cannot judge `\"(?:(\" + IdentifierGrammar.SLUG + \")\" + SEPARATOR + \")?(\" + IdentifierGrammar.SLUG" +
                " + \")\"`",
            "IdentifierGrammar.SLUG is the kernel's shared kebab grammar (#444), possessive, and judged by this guard"
                + " where IdentifierGrammar compiles it; here it sits in capture groups, one optional, none repeated",
        ),
    )

    @Test
    fun mainCodeCompilesNoRecursivePatternOutsideTheAllowList() {
        val sources = mainSources()
        assertThat(sources).`as`("main sources found under %s", serviceDir).isNotEmpty()

        val findings = sources.flatMap { Guard.scan(it.relativeTo(serviceDir).invariantSeparatorsPath, it.readText()) }
        val unexplained = findings.filter { it.key !in allowList }
        assertThat(unexplained)
            .`as`("make the quantifier possessive (`)*+`, `)++`) or add an allow-list entry with a reason")
            .isEmpty()
        val stale = findings.filter { allowList[it.key]?.detail?.let { detail -> detail != it.detail } == true }
        assertThat(stale.map { "${it.key}: ${it.detail}" })
            .`as`("allow-list entry is out of date: the pattern changed, so judge it again and update the detail")
            .isEmpty()
        val used = findings.map { it.key }.toSet()
        assertThat(allowList.keys - used).`as`("allow-list entries that no longer match a finding").isEmpty()
    }

    /** Every `.java` file under `<service>/<module>/src/main/java`: the tree build.gradle.kts declares as input. */
    private fun mainSources(): List<File> = serviceDir.listFiles().orEmpty()
        .map { File(it, "src/main/java") }
        .filter { it.isDirectory }
        .flatMap { dir -> dir.walk().filter { it.isFile && it.name.endsWith(".java") }.toList() }

    @Test
    fun aBacktrackingRepeatedGroupIsFlagged() {
        val findings = Guard.scan("A.java", source("""Pattern.compile("^[a-z]+(-[a-z]+)*$")"""))
        assertThat(findings).hasSize(1)
        assertThat(findings.single().kind).isEqualTo(Kind.RECURSIVE)
        assertThat(findings.single().toString()).contains("A.java:3", "ID")
        for (shape in listOf("(?:-[a-z]+)+", "(a)*?", "(a){2,}", "(?:[^)]+)*")) {
            assertThat(Guard.scan("A.java", source("Pattern.compile(\"$shape\")"))).`as`(shape).hasSize(1)
        }
    }

    @Test
    fun possessiveAtomicBoundedAndOptionalGroupsPass() {
        val safe = listOf(
            "^[a-z]++(?:-[a-z]++)*+$",
            "(?>-[a-z]+)*",
            "[0-9]+(\\\\.[0-9]+){0,2}",
            "(?:a)?",
            "(a){2,}+",
            "\\\\(a\\\\)*",
            "[(]a[)]*",
        )
        for (shape in safe) {
            assertThat(Guard.scan("A.java", source("Pattern.compile(\"$shape\")"))).`as`(shape).isEmpty()
        }
    }

    @Test
    fun sameFileConstantsAndLiteralConcatenationAreFollowed() {
        val code = """
            class A {
                private static final String PART = "(-[a-z]+)" + "*";
                private static final String SAFE = "(-[a-z]+)*+";
                private static final Pattern ID = Pattern.compile("^[a-z]+" + PART);
                private static final Pattern OK = Pattern.compile(SAFE, Pattern.CASE_INSENSITIVE);
                @jakarta.validation.constraints.Pattern(regexp = PART) String host;
            }
        """.trimIndent()
        val findings = Guard.scan("A.java", code)
        assertThat(findings.map { it.key }).containsExactly("A.java:ID", "A.java:PART")
        assertThat(findings).allMatch { it.kind == Kind.RECURSIVE }
    }

    @Test
    fun aPatternBuiltOutsideTheFileCannotBeJudged() {
        val code = """
            class A {
                Pattern a(String regex) { return Pattern.compile(regex); }
                Pattern b(StringBuilder sb) { return Pattern.compile(sb.toString()); }
                Pattern c() { return Pattern.compile(Other.GRAMMAR + "x"); }
            }
        """.trimIndent()
        val findings = Guard.scan("A.java", code)
        assertThat(findings.map { it.key }).containsExactly("A.java:regex", "A.java:sb.toString()", "A.java:c")
        assertThat(findings).allMatch { it.kind == Kind.UNJUDGED }
    }

    @Test
    fun commentsAreNotScanned() {
        val code = "class A {\n// Pattern.compile(\"(a+)*\")\n/* Pattern.compile(\"(a+)*\") */\n}"
        assertThat(Guard.scan("A.java", code)).isEmpty()
    }

    private fun source(call: String) = "class A {\n\n    private static final Pattern ID = $call;\n}"

    enum class Kind { RECURSIVE, UNJUDGED }

    data class Finding(val file: String, val line: Int, val label: String, val kind: Kind, val detail: String) {
        val key get() = "$file:$label"
        override fun toString() = "$file:$line $label $kind: $detail"
    }

    object Guard {

        private const val MAX_DEPTH = 8
        private val compile = Regex("""\bPattern\s*\.\s*compile\s*\(""")
        private val regexp = Regex("""\bregexp\s*=""")
        private val assigned = Regex("""(\w+)\s*=\s*$""")
        private val identifier = Regex("""[A-Za-z_]\w*""")

        fun scan(file: String, text: String): List<Finding> {
            val code = blankComments(text)
            val constants = constants(code)
            val sites = compile.findAll(code).map { m -> m.range.first to argumentAt(code, m.range.last + 1) } +
                regexp.findAll(code).map { m -> m.range.first to argumentAt(code, m.range.last + 1) }
            return sites.sortedBy { it.first }.mapNotNull { (at, argument) ->
                val line = code.substring(0, at).count { it == '\n' } + 1
                val label = label(code, at, argument)
                when (val regex = evaluate(argument, constants, 0)) {
                    null -> Finding(file, line, label, Kind.UNJUDGED, "cannot judge `$argument`")
                    else -> recursiveGroup(regex)?.let { Finding(file, line, label, Kind.RECURSIVE, it) }
                }
            }.toList()
        }

        /** The index of a repeated, non-possessive, non-atomic group in [regex] described, or null. */
        fun recursiveGroup(regex: String): String? {
            val open = ArrayDeque<Int>()
            var i = 0
            while (i < regex.length) {
                when (regex[i]) {
                    '\\' -> i++
                    '[' -> i = classEnd(regex, i)
                    '(' -> open.addLast(i)
                    ')' -> {
                        val start = open.removeLastOrNull() ?: return null
                        val quantifier = unboundedQuantifierAt(regex, i + 1)
                        if (quantifier != null && !regex.startsWith("(?>", start)) {
                            val end = i + 1 + quantifier
                            if (regex.getOrNull(end) != '+') {
                                return "repeated group `${regex.substring(start, end)}` is not possessive in `$regex`"
                            }
                        }
                    }
                }
                i++
            }
            return null
        }

        /** The length of an unbounded quantifier (`*`, `+`, `{n,}`) at [at], or null. */
        private fun unboundedQuantifierAt(regex: String, at: Int): Int? = when (regex.getOrNull(at)) {
            '*', '+' -> 1
            '{' -> Regex("""\{\d+,}""").matchAt(regex, at)?.value?.length
            else -> null
        }

        /** The index of the `]` that closes the class opened at [at]. */
        private fun classEnd(regex: String, at: Int): Int {
            var i = at + 1
            if (regex.getOrNull(i) == '^') i++
            if (regex.getOrNull(i) == ']') i++
            var depth = 1
            while (i < regex.length) {
                when (regex[i]) {
                    '\\' -> i++
                    '[' -> depth++
                    ']' -> if (--depth == 0) return i
                }
                i++
            }
            return i
        }

        private fun label(code: String, at: Int, argument: String): String {
            if (identifier.matches(argument)) return argument
            val before = code.substring(code.lastIndexOfAny(charArrayOf(';', '{', '}'), at) + 1, at)
            assigned.find(before.trimEnd())?.let { return it.groupValues[1] }
            if (argument.matches(Regex("""[\w.]+\(\)"""))) return argument
            return Regex("""(\w+)\s*\([^()]*\)\s*\{[^{}]*$""").find(code.substring(0, at))?.groupValues?.get(1)
                ?: "line"
        }

        /** The text of the first argument of the call or element whose value starts at [start]. */
        private fun argumentAt(code: String, start: Int): String {
            var depth = 0
            var i = start
            while (i < code.length) {
                when (code[i]) {
                    '"', '\'' -> i = literalEnd(code, i)
                    '(', '{', '[' -> depth++
                    ')', '}', ']' -> if (depth-- == 0) break
                    ',', ';' -> if (depth == 0) break
                }
                i++
            }
            return code.substring(start, i).trim()
        }

        /** The value of [expression] if it is string literals and same-file constants joined by `+`, else null. */
        private fun evaluate(expression: String, constants: Map<String, String>, depth: Int): String? {
            if (depth > MAX_DEPTH || expression.startsWith("\"\"\"")) return null
            val out = StringBuilder()
            var i = 0
            var expectOperand = true
            while (i < expression.length) {
                val c = expression[i]
                when {
                    c.isWhitespace() -> i++
                    expectOperand && c == '"' -> {
                        val end = literalEnd(expression, i)
                        out.append(unescape(expression.substring(i + 1, end)))
                        i = end + 1
                        expectOperand = false
                    }
                    expectOperand && (c.isLetter() || c == '_') -> {
                        val name = identifier.matchAt(expression, i)!!.value
                        val value = constants[name]?.let { evaluate(it, constants, depth + 1) } ?: return null
                        out.append(value)
                        i += name.length
                        expectOperand = false
                    }
                    !expectOperand && c == '+' -> { i++; expectOperand = true }
                    else -> return null
                }
            }
            return if (expectOperand) null else out.toString()
        }

        /** Same-file `static final String` constants: name to the source text of their initialiser. */
        private fun constants(code: String): Map<String, String> =
            Regex("""\bstatic\s+final\s+String\s+(\w+)\s*=""").findAll(code)
                .associate { it.groupValues[1] to argumentAt(code, it.range.last + 1) }

        private fun literalEnd(code: String, start: Int): Int {
            var i = start + 1
            while (i < code.length && code[i] != code[start]) {
                if (code[i] == '\\') i++
                i++
            }
            return i
        }

        private fun unescape(literal: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < literal.length) {
                val c = literal[i]
                if (c != '\\' || i + 1 >= literal.length) {
                    out.append(c)
                    i++
                    continue
                }
                val next = literal[i + 1]
                if (next == 'u') {
                    out.append(literal.substring(i + 2, i + 6).toInt(16).toChar())
                    i += 6
                    continue
                }
                out.append(
                    when (next) {
                        'n' -> '\n'
                        't' -> '\t'
                        'r' -> '\r'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        's' -> ' '
                        else -> next
                    },
                )
                i += 2
            }
            return out.toString()
        }

        /** [text] with comments replaced by spaces, newlines kept, so offsets and line numbers still hold. */
        private fun blankComments(text: String): String {
            val out = StringBuilder(text)
            var i = 0
            while (i < text.length) {
                when {
                    text[i] == '"' || text[i] == '\'' -> i = literalEnd(text, i)
                    text.startsWith("//", i) -> {
                        val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                        for (j in i until end) out.setCharAt(j, ' ')
                        i = end
                    }
                    text.startsWith("/*", i) -> {
                        val end = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                        for (j in i until end) if (text[j] != '\n') out.setCharAt(j, ' ')
                        i = end - 1
                    }
                }
                i++
            }
            return out.toString()
        }
    }
}
