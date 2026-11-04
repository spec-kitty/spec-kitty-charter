import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.ext.DefaultHandler2
import java.io.File
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

/**
 * The exception register of the hand-written Checkstyle gate (`config/checkstyle/suppressions.xml`, ADR-0056) and the
 * check that keeps it shrink-only. Checkstyle never reports a suppression that suppresses nothing, so the build runs
 * a probe: the same rules without the register, over the register's files only. An entry is live while the probe
 * reports each rule it lists on its method; any other entry is stale and fails the build, as does an entry that
 * lacks its comment fields, names a file that is gone, or a header `Size:` that miscounts.
 *
 * Pure: no Gradle API. charter.hand-written runs the probe and calls [verify].
 */
object CheckstyleRegister {

    /** The fields each entry's comment carries. */
    val COMMENT_FIELDS = listOf("rule", "file", "reason", "sunset")

    /** The rules whose violation is reported on a method or constructor, and so can be tied to one. */
    val METHOD_RULES = setOf("CyclomaticComplexity", "NPathComplexity", "MethodLength", "NestedIfDepth")

    /** The rules whose violation is reported on the declaration itself (its first annotation or modifier). */
    private val DECLARATION_RULES = setOf("CyclomaticComplexity", "NPathComplexity", "MethodLength")

    /**
     * One `suppress` or `suppress-xpath` element.
     *
     * @property rules the check names in `checks`, split on `|`
     * @property fileRegex the `files` attribute, a regex Checkstyle finds in the absolute path
     * @property method the method `query` names through `@text='…'`, or null for a file-wide entry
     * @property commentFields the `rule:`, `file:`, `reason:` and `sunset:` lines of the comment directly above
     * @property line the register line of the element, for messages
     */
    data class Entry(
        val rules: Set<String>,
        val fileRegex: String,
        val method: String?,
        val commentFields: Map<String, String>,
        val line: Int,
    ) {
        /** The comment's `file:` path, relative to the repository root, if any. */
        val file: String? get() = commentFields["file"]?.takeIf { it.isNotBlank() }
    }

    /** The parsed register: its entries and the header's `Size: N entries`, null when the header has none. */
    data class Register(val entries: List<Entry>, val statedSize: Int?)

    /** One error of a probe report: the check's simple name, the absolute file and the reported line. */
    data class Violation(val check: String, val file: String, val line: Int)

    /** What is wrong with the register. */
    enum class Kind { STALE, MISSING_FILE, MISSING_FIELD, SIZE_MISMATCH, UNMAPPED }

    /** One problem, at a register line, or 0 when it concerns the register as a whole. */
    data class Finding(val kind: Kind, val line: Int, val message: String) {
        override fun toString(): String = if (line > 0) "$kind suppressions.xml:$line: $message" else "$kind $message"
    }

    private val SIZE = Regex("""Size:\s*(\d+)\s+entr""")
    private val FIELD = Regex("""^(\w+):\s*(.*)$""")
    private val METHOD_TEXT = Regex("""@text\s*=\s*'([^']*)'""")

    /**
     * Reads a register. External DTDs and entities are never loaded, so the build does not fetch the Checkstyle DTD
     * the DOCTYPE names. Fails on XML that is not well formed.
     */
    fun parse(xml: String): Register {
        val entries = mutableListOf<Entry>()
        var statedSize: Int? = null
        var lastComment: String? = null
        var inRoot = false
        var locator: Locator? = null
        val handler = object : DefaultHandler2() {
            override fun setDocumentLocator(l: Locator) {
                locator = l
            }

            override fun comment(ch: CharArray, start: Int, length: Int) {
                val text = String(ch, start, length)
                if (!inRoot && statedSize == null) {
                    statedSize = SIZE.find(text)?.groupValues?.get(1)?.toInt()
                }
                lastComment = text
            }

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                if (qName == "suppressions") {
                    inRoot = true
                } else if (qName == "suppress" || qName == "suppress-xpath") {
                    entries += Entry(
                        rules = attributes.getValue("checks").orEmpty().split('|').map { it.trim() }
                            .filter { it.isNotEmpty() }.toSet(),
                        fileRegex = attributes.getValue("files").orEmpty(),
                        method = attributes.getValue("query")?.let { METHOD_TEXT.find(it)?.groupValues?.get(1) },
                        commentFields = commentFields(lastComment),
                        line = locator?.lineNumber ?: 0,
                    )
                }
                lastComment = null
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                lastComment = null
            }

            override fun resolveEntity(name: String?, publicId: String?, baseURI: String?, systemId: String?) =
                InputSource(StringReader(""))
        }
        newParser().apply { setProperty("http://xml.org/sax/properties/lexical-handler", handler) }
            .parse(InputSource(StringReader(xml)), handler)
        return Register(entries, statedSize)
    }

    /** Reads the errors of a Checkstyle XML report. */
    fun parseReport(xml: String): List<Violation> {
        val violations = mutableListOf<Violation>()
        var file: String? = null
        val handler = object : DefaultHandler2() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when (qName) {
                    "file" -> file = attributes.getValue("name")
                    "error" -> violations += Violation(
                        check = attributes.getValue("source").orEmpty().substringAfterLast('.').removeSuffix("Check"),
                        file = checkNotNull(file) { "A Checkstyle report error outside a <file> element" },
                        line = attributes.getValue("line")?.toIntOrNull() ?: 0,
                    )
                }
            }

            override fun resolveEntity(name: String?, publicId: String?, baseURI: String?, systemId: String?) =
                InputSource(StringReader(""))
        }
        newParser().parse(InputSource(StringReader(xml)), handler)
        return violations
    }

    private fun newParser() = SAXParserFactory.newInstance().apply {
        isNamespaceAware = false
        isValidating = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }.newSAXParser().apply {
        setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    }

    /** The `name: value` lines of a comment; a line that names no field continues the previous field's value. */
    private fun commentFields(comment: String?): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        var current: String? = null
        for (raw in comment.orEmpty().lines()) {
            val line = raw.trim()
            val match = FIELD.find(line)
            if (match != null && match.groupValues[1] in COMMENT_FIELDS) {
                current = match.groupValues[1]
                fields[current] = match.groupValues[2].trim()
            } else if (current != null && line.isNotEmpty()) {
                fields[current] = (fields[current] + " " + line).trim()
            }
        }
        return fields
    }

    /**
     * Judges a register for one module.
     *
     * @param register the parsed register
     * @param violations the module's probe report errors, the register switched off
     * @param sourceLine line [n] (1-based) of a source file, or null past its end
     * @param rootDir the repository root, against which the comments' `file:` paths resolve
     * @param moduleDir the module's directory: liveness is judged only for entries whose `file:` lies under it
     * @return the findings, empty when the register is in order
     */
    fun verify(
        register: Register,
        violations: List<Violation>,
        sourceLine: (File, Int) -> String?,
        rootDir: File,
        moduleDir: File,
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        if (register.statedSize != register.entries.size) {
            val stated = register.statedSize?.let { "says Size: $it entries" } ?: "states no 'Size: N entries.' line"
            findings += Finding(
                Kind.SIZE_MISMATCH, 0,
                "the header $stated, but the register holds ${register.entries.size}: correct the header",
            )
        }
        for (entry in register.entries) {
            findings += fieldFindings(entry)
            val path = entry.file ?: continue
            if (!rootDir.resolve(path).isFile) {
                findings += Finding(
                    Kind.MISSING_FILE, entry.line,
                    "file: $path does not exist: delete the entry, or correct its file: and files",
                )
            }
        }

        val mapped = mutableListOf<Pair<Violation, String?>>()
        for (violation in violations) {
            if (violation.check !in METHOD_RULES) {
                mapped += violation to null
                continue
            }
            val method = methodOf(violation.check, violation.line) { n -> sourceLine(File(violation.file), n) }
            if (method == null) {
                findings += Finding(
                    Kind.UNMAPPED, 0,
                    "the probe reports ${violation.check} at ${violation.file}:${violation.line}, but no method" +
                        " declaration can be read there: the check cannot judge the register, so it fails",
                )
            }
            mapped += violation to method
        }

        val module = moduleDir.canonicalFile
        for (entry in register.entries) {
            val path = entry.file ?: continue
            if (!rootDir.resolve(path).canonicalFile.startsWith(module)) continue
            findings += staleFindings(entry, mapped)
        }
        return findings
    }

    private fun fieldFindings(entry: Entry): List<Finding> {
        val findings = mutableListOf<Finding>()
        for (field in COMMENT_FIELDS) {
            if (entry.commentFields[field].isNullOrBlank()) {
                findings += Finding(
                    Kind.MISSING_FIELD, entry.line,
                    "the comment above the entry lacks '$field:': each entry carries rule, file, reason and sunset",
                )
            }
        }
        val sunset = entry.commentFields["sunset"]
        if (!sunset.isNullOrBlank() && !isIsoDate(sunset)) {
            findings += Finding(
                Kind.MISSING_FIELD, entry.line, "sunset: '$sunset' is not an ISO date (yyyy-mm-dd): write one",
            )
        }
        return findings
    }

    private fun isIsoDate(text: String): Boolean =
        try {
            LocalDate.parse(text)
            true
        } catch (_: DateTimeParseException) {
            false
        }

    private fun staleFindings(entry: Entry, violations: List<Pair<Violation, String?>>): List<Finding> {
        val regex = try {
            Regex(entry.fileRegex)
        } catch (e: IllegalArgumentException) {
            return listOf(Finding(Kind.MISSING_FIELD, entry.line, "files='${entry.fileRegex}' is not a regex: ${e.message}"))
        }
        if (entry.rules.isEmpty()) {
            return listOf(Finding(Kind.MISSING_FIELD, entry.line, "the entry has no checks: name the rules it suppresses"))
        }
        val where = entry.method?.let { "method $it of ${entry.file}" } ?: "${entry.file}"
        if (entry.method != null) {
            val unmappable = entry.rules - METHOD_RULES
            if (unmappable.isNotEmpty()) {
                return listOf(
                    Finding(
                        Kind.UNMAPPED, entry.line,
                        "the entry scopes ${unmappable.sorted()} to $where, but only ${METHOD_RULES.sorted()} can be" +
                            " tied to a method: make it a file-wide suppress",
                    ),
                )
            }
        }
        val dead = entry.rules.filter { rule ->
            violations.none { (violation, method) ->
                violation.check == rule &&
                    regex.containsMatchIn(violation.file) &&
                    (entry.method == null || method == entry.method)
            }
        }
        if (dead.isEmpty()) return emptyList()
        val fix = if (dead.size == entry.rules.size) "delete the entry" else "narrow checks to ${(entry.rules - dead.toSet()).sorted().joinToString("|")}"
        return listOf(
            Finding(Kind.STALE, entry.line, "${dead.sorted().joinToString(", ")} no longer fires on $where: $fix"),
        )
    }

    /**
     * The method a violation of [check] reported at [line] belongs to, or null when no declaration can be read.
     * The declaration rules report the declaration's first line, which may be an annotation; NestedIfDepth reports
     * the nested `if`, so the nearest enclosing declaration above it, at a smaller indentation, is taken.
     */
    fun methodOf(check: String, line: Int, sourceLine: (Int) -> String?): String? =
        if (check in DECLARATION_RULES) {
            declaredName(line, sourceLine)
        } else {
            enclosingDeclaration(line, sourceLine)
        }

    private const val DECLARATION_LOOKAHEAD = 30

    /** The name declared from [line] on: annotations are skipped, then the identifier before the parameters. */
    private fun declaredName(line: Int, sourceLine: (Int) -> String?): String? {
        val text = (line until line + DECLARATION_LOOKAHEAD).mapNotNull(sourceLine).joinToString("\n")
        var i = skipAnnotations(text)
        val start = i
        while (i < text.length && text[i] != '(' && text[i] != '{' && text[i] != ';' && text[i] != '=') i++
        if (i >= text.length || text[i] == ';' || text[i] == '=') return null
        // A compact record constructor has no parameter list: its name is the identifier before the body.
        return Regex("""([A-Za-z_$][\w$]*)\s*$""").find(text.substring(start, i))?.groupValues?.get(1)
    }

    private fun skipAnnotations(text: String): Int {
        var i = 0
        while (true) {
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length || text[i] != '@' || text.startsWith("@interface", i)) return i
            i++
            while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '$' || text[i] == '.')) i++
            var j = i
            while (j < text.length && text[j].isWhitespace()) j++
            if (j < text.length && text[j] == '(') i = skipParentheses(text, j)
        }
    }

    /** The index after the parenthesis that closes the one at [open], skipping string and character literals. */
    private fun skipParentheses(text: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '"', '\'' -> {
                    val quote = text[i]
                    i++
                    while (i < text.length && text[i] != quote) {
                        if (text[i] == '\\') i++
                        i++
                    }
                }
                '(' -> depth++
                ')' -> if (--depth == 0) return i + 1
            }
            i++
        }
        return i
    }

    private val MODIFIERS = "public|protected|private|static|final|abstract|synchronized|native|default|strictfp"
    private val NOT_A_TYPE = setOf("return", "new", "throw", "else", "case", "yield", "assert", "package", "import")

    /** A method declaration at the start of a line: modifiers, type parameters, a return type, a name, `(`. */
    private val METHOD_DECLARATION = Regex(
        """^\s*(?:@[\w.$]+(?:\([^)]*\))?\s+)*(?:(?:$MODIFIERS)\s+)*(?:<.+?>\s+)?""" +
            """([\w.$]+(?:<.*?>)?(?:\[\])*)\s+([A-Za-z_$][\w$]*)\s*\(""",
    )

    /** A constructor declaration: modifiers, a capitalised name, `(`, and no statement end on the line. */
    private val CONSTRUCTOR_DECLARATION = Regex(
        """^\s*(?:@[\w.$]+(?:\([^)]*\))?\s+)*(?:(?:$MODIFIERS)\s+)*(?:<.+?>\s+)?([A-Z][\w$]*)\s*\([^;]*$""",
    )

    private val CONTROL = Regex("""^\s*(?:if|else|for|while|do|switch|try|catch|finally|synchronized|return|throw|new)\b""")

    private fun enclosingDeclaration(line: Int, sourceLine: (Int) -> String?): String? {
        val at = sourceLine(line) ?: return null
        val indent = indentation(at)
        for (n in line - 1 downTo 1) {
            val text = sourceLine(n) ?: continue
            if (text.isBlank() || indentation(text) >= indent) continue
            declarationName(text)?.let { return it }
        }
        return null
    }

    /** The name a line declares as a method or constructor, or null when it is not such a declaration. */
    internal fun declarationName(text: String): String? {
        if (CONTROL.containsMatchIn(text)) return null
        val head = text.substringBefore('(')
        if ('=' in head || "->" in text.substringBefore('{')) return null
        METHOD_DECLARATION.find(text)?.let { match ->
            if (match.groupValues[1] !in NOT_A_TYPE) return match.groupValues[2]
        }
        return CONSTRUCTOR_DECLARATION.find(text)?.groupValues?.get(1)
    }

    private fun indentation(text: String): Int = text.length - text.trimStart().length
}
