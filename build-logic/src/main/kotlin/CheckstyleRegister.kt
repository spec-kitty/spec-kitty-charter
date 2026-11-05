import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Checks the exception register of the hand-written gates (`config/checkstyle/suppressions.xml`, ADR-0058) for its
 * own bookkeeping: each entry carries its comment fields, no entry is past its sunset, and the header's `Size:`
 * counts the entries. It runs no Checkstyle and does not ask whether an entry is still needed. Pure: no Gradle API.
 */
object CheckstyleRegister {

    private val FIELDS = listOf("rule", "file", "reason", "sunset")
    private val SIZE = Regex("""Size:\s*(\d+)""")
    private val RULE_LINE = Regex("""(?m)^\s*rule:""")
    private val TOKEN = Regex("""<!--(.*?)-->|<suppress(?:-xpath)?\b""", RegexOption.DOT_MATCHES_ALL)

    /** One register entry: the text of the comment above it ("" when none) and its 1-based line. */
    private data class Entry(val comment: String, val line: Int)

    /**
     * The entries: each `<suppress>` element, and each comment with a `rule:` line (a comment-only entry, which
     * records an exception to another gate). A comment that holds `<suppress` but no `rule:` line is ignored.
     */
    private fun entries(xml: String, from: Int): List<Entry> {
        val found = mutableListOf<Entry>()
        var pending: MatchResult? = null
        fun line(at: Int) = xml.substring(0, at).count { it == '\n' } + 1
        fun settle() = pending?.let { if (RULE_LINE.containsMatchIn(it.groupValues[1])) found += Entry(it.groupValues[1], line(it.range.first)) }
        for (token in TOKEN.findAll(xml, from)) {
            if (token.value.startsWith("<!--")) {
                settle()
                pending = token
            } else {
                found += Entry(pending?.groupValues?.get(1) ?: "", line(token.range.first))
                pending = null
            }
        }
        settle()
        return found
    }

    /** The problems of [xml], one message each; empty when the register is in order. [today] is the sunset's reference. */
    fun check(xml: String, today: LocalDate): List<String> {
        val body = xml.lastIndexOf("<suppressions")
        val header = if (body < 0) xml else xml.substring(0, body)
        val entries = entries(xml, maxOf(body, 0))
        val problems = mutableListOf<String>()
        val sizeText = SIZE.find(header)?.groupValues?.get(1)
        val stated = sizeText?.toIntOrNull()
        if (stated != entries.size) {
            val said = when {
                sizeText == null -> "nothing about Size"
                stated == null -> "Size: $sizeText, which is not a number I can read"
                else -> "Size: $stated entries"
            }
            problems += "the header says $said, but the register holds ${entries.size}: correct the header"
        }
        for (entry in entries) {
            val line = entry.line
            val fields = FIELDS.associateWith { name ->
                Regex("""(?m)^\s*$name:[ \t]*(\S.*)$""").find(entry.comment)?.groupValues?.get(1)?.trim()
            }
            for ((name, value) in fields) {
                if (value == null) problems += "line $line: the comment above the entry lacks '$name:'"
            }
            val sunset = fields.getValue("sunset") ?: continue
            try {
                if (LocalDate.parse(sunset).isBefore(today)) {
                    problems += "line $line: sunset $sunset has passed (today is $today): remove the entry or re-justify it"
                }
            } catch (_: DateTimeParseException) {
                problems += "line $line: sunset '$sunset' is not an ISO date (yyyy-mm-dd)"
            }
        }
        return problems
    }
}
