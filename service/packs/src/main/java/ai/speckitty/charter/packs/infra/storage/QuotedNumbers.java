package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.validation.Location;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.CollectionEndEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.DocumentStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.MappingStartEvent;
import org.snakeyaml.engine.v2.events.NodeEvent;
import org.snakeyaml.engine.v2.events.ScalarEvent;
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
 * repair makes. "Quoted" is any string the reader reads there: single- or double-quoted, escaped, tagged {@code !!str},
 * or a block scalar; the repair writes the value in place of the whole node. An anchored value, a file with an alias or
 * more than one document is left, and the refusal says why.
 */
final class QuotedNumbers {

    /** How the fix ends: what the author does, or has {@code remediate} do. */
    private static final String REMEDY = " without quotes, or run pack-doctor remediate";

    /** The fix without {@code remediate}, where {@code remediate} cannot make it. */
    private static final String BY_HAND = " without quotes";

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
     * A detail without the promise that {@code remediate} repairs it, for a place {@code remediate} never repairs (a
     * fault joined into the one pointerless detail of an unreadable policy file): {@code ... write 15 without quotes}.
     *
     * @param detail any detail
     * @return the detail without the remedy, or as it was when it names none
     */
    static String withoutRemedy(String detail) {
        return detail.endsWith(REMEDY) ? detail.substring(0, detail.length() - REMEDY.length()) + BY_HAND : detail;
    }

    /**
     * A finding {@code remediate} would repair, worded for its refusal of a pack that holds other faults too.
     *
     * @param finding a finding that {@link #namesTheFix}
     * @return the finding, its detail saying {@code remediate} repairs it once the rest is fixed
     */
    static Reason blocked(Reason finding) {
        return reworded(finding, "; remediate does once the pack's other faults are fixed");
    }

    /**
     * A finding {@code remediate} could not repair, worded for its refusal.
     *
     * @param finding a finding that {@link #namesTheFix}
     * @param why why it was left as it was ({@link Repairs#unrepaired})
     * @return the finding, its detail saying why {@code remediate} cannot repair it
     */
    static Reason unrepaired(Reason finding, String why) {
        return reworded(finding, "; remediate cannot: " + why);
    }

    private static Reason reworded(Reason finding, String instead) {
        String detail = withoutRemedy(finding.detail());
        return new Reason(
                finding.rule(),
                finding.origin(),
                finding.result(),
                finding.code(),
                finding.location(),
                detail + instead);
    }

    /**
     * What {@link #repair} did.
     *
     * @param repaired each value written plain, once a place, in the order of the first finding at it
     * @param unrepaired each place a finding named the fix of that was left as it was, with why
     * @param gone a file a finding names that the copy no longer holds: the pack changed under the run
     */
    record Repairs(List<PackRemediation.Repaired> repaired, Map<Location, String> unrepaired, Optional<String> gone) {
        /**
         * Copies the collections.
         *
         * @param repaired the values written plain
         * @param unrepaired the places left, with why
         * @param gone the file gone, if any
         */
        Repairs {
            repaired = List.copyOf(repaired);
            unrepaired = Map.copyOf(unrepaired);
        }
    }

    /**
     * Writes plain, in the pack directory {@code pack}, each value a finding's detail names the fix of: the value's whole
     * node (its quotes, a tag, escapes, a block scalar's indicator and lines) is replaced by the value, and a line break
     * that ended the node stays. A value is written only when its file still holds, at the finding's place, a scalar of
     * that value whose finding is the fault {@link #fault} words for it, in the file's one document, with no anchor on
     * it and no alias in the file; and only when the file then reads as before but for those values, now numbers.
     * Anything else is left as it is, with why, for the verdict to refuse again.
     *
     * @param pack the pack directory, a copy the caller may change
     * @param findings the findings of the storage verdict
     * @return what was written plain and what was left
     * @throws IOException when a file cannot be read or written
     */
    static Repairs repair(Path pack, List<Reason> findings) throws IOException {
        Map<String, List<Integer>> byFile = new LinkedHashMap<>();
        for (int index = 0; index < findings.size(); index++) {
            Reason finding = findings.get(index);
            if (namesTheFix(finding) && finding.location().file().endsWith(".yaml")) {
                byFile.computeIfAbsent(finding.location().file(), k -> new ArrayList<>())
                        .add(index);
            }
        }
        Map<Integer, PackRemediation.Repaired> repaired = new TreeMap<>();
        Map<Location, String> unrepaired = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : byFile.entrySet()) {
            Path path = pack.resolve(entry.getKey());
            if (!Files.isRegularFile(path)) {
                return new Repairs(List.of(), Map.of(), Optional.of(entry.getKey()));
            }
            FileRepair file = new FileRepair(Files.readString(path, StandardCharsets.UTF_8));
            entry.getValue().forEach(index -> file.take(index, findings.get(index)));
            Optional<String> text = file.repairedText();
            if (text.isPresent()) {
                Files.writeString(path, text.get(), StandardCharsets.UTF_8);
                repaired.putAll(file.repaired);
            } else {
                file.repaired
                        .values()
                        .forEach(each -> file.left.put(
                                new Location(each.file(), each.pointer()),
                                "writing it plain would change more than its value"));
                file.repaired.clear();
            }
            unrepaired.putAll(file.left);
        }
        return new Repairs(List.copyOf(repaired.values()), unrepaired, Optional.empty());
    }

    /** What a place the repaired text does not hold reads as: equal to no loaded document. */
    private static final Object NOT_FOUND = new Object();

    /** One file's repair: its text, the scalars it holds, and what each finding at it came to. */
    private static final class FileRepair {
        private final String text;
        private final Walk walk;
        private final Map<Integer, Span> spans = new TreeMap<>();
        private final Map<Integer, PackRemediation.Repaired> repaired = new TreeMap<>();
        private final Map<Location, String> left = new HashMap<>();

        FileRepair(String text) {
            this.text = text;
            this.walk = Walk.of(text);
        }

        /** Judges one finding: a span to replace, a place already taken, or a reason it is left. */
        void take(int index, Reason finding) {
            Location place = finding.location();
            ScalarEvent scalar = walk.scalars().get(Findings.segments(place.pointer()));
            Optional<String> why = walk.refusal();
            if (why.isPresent() || scalar == null) {
                left.put(place, why.orElse("its place holds no single value"));
                return;
            }
            Optional<Span> span = Span.of(text, place.pointer(), scalar, finding);
            if (span.isEmpty()) {
                left.put(
                        place,
                        scalar.getAnchor().isPresent() ? "it carries an anchor" : "its value is no quoted number");
                return;
            }
            if (spans.values().stream()
                    .anyMatch(taken -> taken.start() == span.get().start())) {
                return; // a second finding at a node already taken: it is replaced once
            }
            spans.put(index, span.get());
            repaired.put(
                    index,
                    new PackRemediation.Repaired(
                            place.file(),
                            place.pointer(),
                            span.get().value(),
                            span.get().integer()));
        }

        /** The text with every span replaced, when it reads as the old text with those values as numbers. */
        Optional<String> repairedText() {
            if (spans.isEmpty()) {
                return Optional.empty();
            }
            List<Span> ordered = new ArrayList<>(spans.values());
            // From the end, so each span's offsets still hold when it is replaced.
            ordered.sort(Comparator.comparingInt(Span::start).reversed());
            StringBuilder written = new StringBuilder(text);
            ordered.forEach(span -> written.replace(span.start(), span.end(), span.replacement()));
            String after = written.toString();
            return readsAsExpected(after, ordered) ? Optional.of(after) : Optional.empty();
        }

        /** Whether the repaired text loads as the old text does with each repaired value read as a number. */
        private boolean readsAsExpected(String after, List<Span> replaced) {
            ParsedYaml was = ParsedYaml.parse(text, "before");
            ParsedYaml now = ParsedYaml.parse(after, "after");
            if (was.failure() != null || now.failure() != null) {
                return false;
            }
            Object expected = was.document();
            for (Span span : replaced) {
                expected = replaced(expected, Findings.segments(span.pointer()), number(span.value()));
            }
            return Objects.equals(expected, now.document());
        }

        /** The loaded document with the node at {@code place} replaced; {@code NOT_FOUND} when there is none there. */
        @SuppressWarnings("unchecked")
        private static @Nullable Object replaced(
                @Nullable Object document, List<String> place, @Nullable Object value) {
            if (place.isEmpty()) {
                return value;
            }
            String segment = place.getFirst();
            List<String> rest = place.subList(1, place.size());
            if (document instanceof Map<?, ?> map && map.containsKey(segment)) {
                ((Map<Object, @Nullable Object>) map).put(segment, replaced(map.get(segment), rest, value));
                return map;
            }
            if (document instanceof List<?> list && isIndex(segment, list.size())) {
                int index = Integer.parseInt(segment);
                ((List<@Nullable Object>) list).set(index, replaced(list.get(index), rest, value));
                return list;
            }
            return NOT_FOUND;
        }

        private static boolean isIndex(String segment, int size) {
            return segment.matches("0|[1-9][0-9]{0,8}") && Integer.parseInt(segment) < size;
        }

        private static @Nullable Object number(String value) {
            return ParsedYaml.parse(value, "number").document();
        }
    }

    /**
     * A scalar's place in a file's text, in chars: its whole node from its first property (an anchor or a tag) or
     * indicator to its end, a block scalar's line breaks included, and what is written there.
     */
    private record Span(int start, int end, String pointer, String value, String trailing, boolean integer) {

        String replacement() {
            return value + trailing;
        }

        /** The span of a scalar whose finding is the fault worded for its value, with no anchor. */
        static Optional<Span> of(String text, String pointer, ScalarEvent scalar, Reason finding) {
            // A plain scalar with no tag is read by its form, so it is no string the reader holds there.
            boolean readAsText = scalar.getScalarStyle() != ScalarStyle.PLAIN || scalar.getTag().isPresent();
            if (!readAsText
                    || scalar.getAnchor().isPresent()
                    || scalar.getStartMark().isEmpty()
                    || scalar.getEndMark().isEmpty()) {
                return Optional.empty();
            }
            Optional<Boolean> integer = fieldType(scalar.getValue(), finding);
            if (integer.isEmpty()) {
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
            String source = text.substring(start, end);
            String trailing = source.substring(source.stripTrailing().length());
            return Optional.of(new Span(start, end, pointer, scalar.getValue(), trailing, integer.get()));
        }

        /** The field's type, {@code true} for an integer, when the finding is the fault worded for the value. */
        private static Optional<Boolean> fieldType(String value, Reason finding) {
            for (boolean integer : new boolean[] {true, false}) {
                if (qualifies(value, integer) && finding.detail().endsWith(": " + fault(value, integer))) {
                    return Optional.of(integer);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The scalars of a file's one document by their place, keys read as plain text, and the reason none may be repaired
     * in place: an alias, more than one document, or a text the parser refuses.
     */
    private record Walk(Map<List<String>, ScalarEvent> scalars, Optional<String> refusal) {

        static Walk of(String text) {
            Walker walker = new Walker();
            try {
                for (Event event : new Parse(Yaml.settings()).parseString(text)) {
                    Optional<String> refused = walker.accept(event);
                    if (refused.isPresent()) {
                        return new Walk(Map.of(), refused);
                    }
                }
            } catch (YamlEngineException refused) {
                return new Walk(Map.of(), Optional.of("the file cannot be read"));
            }
            return new Walk(walker.scalars, Optional.empty());
        }
    }

    /** The event walk: the open collections, the path to the next node, and the scalars met. */
    private static final class Walker {
        private final Deque<Frame> open = new ArrayDeque<>();
        private final List<String> path = new ArrayList<>();
        private final Map<List<String>, ScalarEvent> scalars = new HashMap<>();
        private int documents;

        /** Takes one event; a reason when the file cannot be repaired in place. */
        Optional<String> accept(Event event) {
            if (event instanceof DocumentStartEvent && ++documents > 1) {
                return Optional.of("the file holds more than one document");
            }
            if (event instanceof AliasEvent) {
                return Optional.of("the file uses an alias");
            }
            if (event instanceof CollectionEndEvent) {
                close();
            } else if (event instanceof NodeEvent node) {
                return node(node);
            }
            return Optional.empty();
        }

        private void close() {
            open.pop();
            if (!path.isEmpty()) {
                path.remove(path.size() - 1);
            }
            advanceParent();
        }

        private Optional<String> node(NodeEvent node) {
            Frame parent = open.peek();
            if (parent != null && parent.awaitingKey) {
                if (!(node instanceof ScalarEvent key)) {
                    return Optional.of("a key is not plain text"); // the reader refuses the file too
                }
                parent.key = key.getValue();
                parent.awaitingKey = false;
                return Optional.empty();
            }
            if (node instanceof ScalarEvent scalar) {
                List<String> here = new ArrayList<>(path);
                if (parent != null) {
                    here.add(parent.segment());
                }
                scalars.put(List.copyOf(here), scalar);
                advanceParent();
            } else if (node instanceof CollectionStartEvent) {
                if (parent != null) {
                    path.add(parent.segment());
                }
                open.push(new Frame(node instanceof MappingStartEvent));
            }
            return Optional.empty();
        }

        private void advanceParent() {
            Frame parent = open.peek();
            if (parent != null) {
                parent.advance();
            }
        }
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
}
