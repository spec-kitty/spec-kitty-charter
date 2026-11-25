package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.rules.Findings;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.CollectionEndEvent;
import org.snakeyaml.engine.v2.events.DocumentStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.MappingStartEvent;
import org.snakeyaml.engine.v2.events.NodeEvent;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.events.SequenceStartEvent;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.Tag;

/**
 * A quoted number in a field the schema types {@code number} or {@code integer} (#651, operator ruling of 2026-11-25):
 * the one fault the service refuses that {@code pack-doctor remediate} repairs in a file's content. The service holds
 * only the canonical shape, a number, so it never reads the string as a number; the detail says how to fix it, and
 * remediate writes it unquoted.
 *
 * <p>A quoted value qualifies when the reader would read the same text, written plain, as a finite number of the field's
 * type: an integer for an {@code integer} field ({@code 15}, {@code -0}), an integer or a float for a {@code number}
 * field ({@code 15}, {@code 0.6}, {@code 1e-3}), under every guard of the reader ({@link ParsedYaml}: a number of more
 * than 1 000 digits, or one whose plain form would be, is no number it reads). {@code fifteen}, {@code 1.5} in an
 * integer field, {@code +1}, {@code .5}, {@code 0x1F}, {@code 1_000}, {@code 1e400} and {@code .inf} do not qualify:
 * the text written plain would still be refused. {@link Wording} words a qualifying value's fault with its fix, and
 * {@link PackRemediation} repairs the faults whose detail carries it, so the fix the detail promises is the one the
 * repair makes.
 */
final class QuotedNumbers {

    /** How the fix ends: what the author does, or has {@code remediate} do. */
    private static final String REMEDY = " without quotes, or run pack-doctor remediate";

    private QuotedNumbers() {}

    /**
     * Whether {@code text} written plain is read as a finite number of the field's type.
     *
     * @param text the quoted value, without its quotes
     * @param integer whether the field is typed {@code integer}; a {@code number} field takes a float too
     * @return {@code true} when unquoting the value gives the field a number it accepts by type
     */
    static boolean qualifies(String text, boolean integer) {
        Tag tag = Yaml.schema().getScalarResolver().resolve(text, true);
        if (!Tag.INT.equals(tag) && (integer || !Tag.FLOAT.equals(tag))) {
            return false;
        }
        // The reader's own guards: a number too long to read, or a float read as infinity, is no number it accepts.
        ParsedYaml read = ParsedYaml.parse(text, "quoted number");
        return read.failure() == null
                && (read.document() instanceof Integer
                        || read.document() instanceof Long
                        || read.document() instanceof BigInteger
                        || (!integer && read.document() instanceof BigDecimal));
    }

    /**
     * The type fault of a qualifying quoted value, after its origin and field: {@code "15" must be a number: write 15
     * without quotes, or run pack-doctor remediate}.
     *
     * @param text the quoted value, without its quotes; it {@link #qualifies}
     * @param integer whether the field is typed {@code integer}
     * @return the fault and its fix
     */
    static String fault(String text, boolean integer) {
        return "\"" + text + "\" must be " + (integer ? "an integer" : "a number") + ": write " + text + REMEDY;
    }

    /**
     * Whether a finding's detail names a fix {@link #repair} makes: a quick test on the detail, before any file is read.
     *
     * @param reason a finding of the storage verdict
     * @return {@code true} when its detail ends with the fix
     */
    static boolean namesTheFix(Reason reason) {
        return reason.detail().endsWith(REMEDY);
    }

    /**
     * Writes plain, in the pack directory {@code pack}, each quoted value a finding's detail names the fix of. A value
     * is unquoted only when its file still holds it at the finding's place as a quoted scalar of the same text, with no
     * tag and no anchor, in a file's one document, and the finding's detail is the fault {@link #fault} words for it;
     * anything else is left as it is, for the verdict to refuse again.
     *
     * @param pack the pack directory, a copy the caller may change
     * @param findings the findings of the storage verdict
     * @return what was unquoted, in the findings' order
     * @throws IOException when a file cannot be read or written
     */
    static List<PackRemediation.Repaired> repair(Path pack, List<Reason> findings) throws IOException {
        Map<String, List<Reason>> byFile = new LinkedHashMap<>();
        findings.stream()
                .filter(QuotedNumbers::namesTheFix)
                .filter(finding -> finding.location().file().endsWith(".yaml"))
                .forEach(finding -> byFile.computeIfAbsent(finding.location().file(), k -> new ArrayList<>())
                        .add(finding));
        List<PackRemediation.Repaired> unquoted = new ArrayList<>();
        for (Map.Entry<String, List<Reason>> entry : byFile.entrySet()) {
            Path path = pack.resolve(entry.getKey());
            String text = Files.readString(path, StandardCharsets.UTF_8);
            List<Span> spans = new ArrayList<>();
            for (Reason finding : entry.getValue()) {
                quoted(text, Findings.segments(finding.location().pointer()))
                        .flatMap(span -> unquotable(span, finding))
                        .ifPresent(span -> {
                            spans.add(span);
                            unquoted.add(new PackRemediation.Repaired(
                                    entry.getKey(), finding.location().pointer(), span.value(), span.integer()));
                        });
            }
            if (!spans.isEmpty()) {
                // From the end, so each span's offsets still hold when it is replaced.
                spans.sort(Comparator.comparingInt(Span::start).reversed());
                StringBuilder repaired = new StringBuilder(text);
                spans.forEach(span -> repaired.replace(span.start(), span.end(), span.value()));
                Files.writeString(path, repaired.toString(), StandardCharsets.UTF_8);
            }
        }
        return List.copyOf(unquoted);
    }

    /** A quoted scalar's place in a file's text, in chars, its quotes included, and its value. */
    private record Span(int start, int end, String value, boolean integer) {}

    /** The span, with the field's type, when the finding's detail is the fault worded for that value. */
    private static Optional<Span> unquotable(Span span, Reason finding) {
        for (boolean integer : new boolean[] {true, false}) {
            if (qualifies(span.value(), integer) && finding.detail().endsWith(": " + fault(span.value(), integer))) {
                return Optional.of(new Span(span.start(), span.end(), span.value(), integer));
            }
        }
        return Optional.empty();
    }

    /** One open collection of the event walk: its key or index for the next node, and whether a mapping awaits a key. */
    private static final class Frame {
        private final boolean mapping;
        private boolean awaitingKey;
        private int index;
        private String key = "";

        Frame(boolean mapping) {
            this.mapping = mapping;
            this.awaitingKey = mapping;
        }

        String segment() {
            return mapping ? key : Integer.toString(index);
        }

        /** A value of this collection ended. */
        void advance() {
            if (mapping) {
                awaitingKey = true;
            } else {
                index++;
            }
        }
    }

    /**
     * The quoted scalar at {@code pointer} in the file's only document: plain-text keys, no tag, no anchor, single- or
     * double-quoted, its source text exactly its quotes around its value. Empty for any other node, or a text the
     * parser refuses.
     */
    private static Optional<Span> quoted(String text, List<String> pointer) {
        Deque<Frame> open = new ArrayDeque<>();
        List<String> path = new ArrayList<>();
        int documents = 0;
        Optional<Span> found = Optional.empty();
        try {
            for (Event event : new Parse(Yaml.settings()).parseString(text)) {
                if (event instanceof DocumentStartEvent && ++documents > 1) {
                    return Optional.empty();
                }
                if (event instanceof CollectionEndEvent) {
                    open.pop();
                    if (!path.isEmpty()) {
                        path.remove(path.size() - 1);
                    }
                    if (!open.isEmpty()) {
                        open.peek().advance();
                    }
                    continue;
                }
                if (!(event instanceof NodeEvent node)) {
                    continue;
                }
                Frame parent = open.peek();
                if (parent != null && parent.awaitingKey) {
                    if (!(node instanceof ScalarEvent key)) {
                        return Optional.empty(); // a key that is no scalar: the reader refuses the file
                    }
                    parent.key = key.getValue();
                    parent.awaitingKey = false;
                    continue;
                }
                List<String> here = new ArrayList<>(path);
                if (parent != null) {
                    here.add(parent.segment());
                }
                if (node instanceof ScalarEvent scalar) {
                    if (here.equals(pointer)) {
                        found = span(text, scalar);
                    }
                    if (parent != null) {
                        parent.advance();
                    }
                } else if (node instanceof AliasEvent) {
                    if (parent != null) {
                        parent.advance();
                    }
                } else if (node instanceof MappingStartEvent || node instanceof SequenceStartEvent) {
                    if (parent != null) {
                        path.add(parent.segment());
                    }
                    open.push(new Frame(node instanceof MappingStartEvent));
                }
            }
        } catch (YamlEngineException refused) {
            return Optional.empty();
        }
        return found;
    }

    private static Optional<Span> span(String text, ScalarEvent scalar) {
        ScalarStyle style = scalar.getScalarStyle();
        if ((style != ScalarStyle.DOUBLE_QUOTED && style != ScalarStyle.SINGLE_QUOTED)
                || scalar.getTag().isPresent()
                || scalar.getAnchor().isPresent()
                || scalar.getStartMark().isEmpty()
                || scalar.getEndMark().isEmpty()) {
            return Optional.empty();
        }
        // The marks count code points; the text is indexed in chars.
        int codePoints = text.codePointCount(0, text.length());
        int startIndex = scalar.getStartMark().get().getIndex();
        int endIndex = scalar.getEndMark().get().getIndex();
        if (startIndex < 0 || endIndex > codePoints || startIndex >= endIndex) {
            return Optional.empty();
        }
        int start = text.offsetByCodePoints(0, startIndex);
        int end = text.offsetByCodePoints(start, endIndex - startIndex);
        String value = scalar.getValue();
        char quote = style == ScalarStyle.DOUBLE_QUOTED ? '"' : '\'';
        String source = text.substring(start, end);
        return source.equals(quote + value + quote)
                ? Optional.of(new Span(start, end, value, false))
                : Optional.empty();
    }
}
