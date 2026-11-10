package ai.speckitty.charter.kernel.markdown;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The slot grammar of a step prompt (ADR-0066 decision 2): the declared slots in order, or typed faults. Pure: it reads
 * text and names no code, no pack and no refusal. The caller decides what a fault means.
 *
 * <p>A <b>marker candidate</b> is an HTML comment, {@code <!--} up to the first {@code -->} after it, whose content,
 * trimmed, ends in {@code :start} or {@code :end}. Every other comment is text ({@code <!-- decision_id: x -->}), and
 * so is a comment never closed. {@code <!-->} and {@code <!--->} are complete comments (CommonMark), with no content.
 * A candidate counts wherever it stands: in an HTML block, in inline code, in a fenced
 * block and in a frontmatter value ("any marker counts", d7). Its id, the content minus the suffix, trimmed, must be a
 * {@link SlotId}.
 *
 * <p>A slot is a pair {@code <!-- id:start -->} ... {@code <!-- id:end -->}. These are {@link Kind#MALFORMED}, each
 * found at the marker that breaks the rule and each marker breaking at most one: a candidate whose id is not a
 * {@code SlotId}; an end with no open slot, or whose id is not the open slot's; a start whose id is already declared;
 * a start inside an open slot; a start still open at the end of the text. A broken marker is otherwise ignored, so one
 * mistake can raise more than one fault (a start declared twice also leaves its end without a start). A slot between
 * whose markers lies anything but ASCII space, tab or LF is declared, with {@link Slot#empty()} false, and also raises
 * {@link Kind#NOT_EMPTY}, which the caller may treat as blocking or as a warning; {@code MALFORMED} is always
 * blocking. "Whitespace" is that explicit set, never {@link Character#isWhitespace(char)}: a no-break space or a
 * zero-width space is content. A carriage return cannot reach this parser, because the codec refuses it first.
 *
 * <p>The input is the canonical text of the prompt, {@code MarkdownCodec.write(document)}, frontmatter included
 * (A5), so that load, validate mode and the read agree. The scan is a single forward pass, linear in the text, with no
 * regular expression. The same text gives equal answers.
 */
public final class PromptSlots {

    private static final String COMMENT_OPEN = "<!--";
    private static final String COMMENT_CLOSE = "-->";
    private static final String START_SUFFIX = ":start";
    private static final String END_SUFFIX = ":end";
    private static final int MAX_SHOWN_ID = 64;

    private PromptSlots() {}

    /** What is wrong with a prompt's slots. */
    public enum Kind {
        /** A marker breaks the grammar: always blocking. */
        MALFORMED,
        /** A slot holds more than whitespace between its markers. */
        NOT_EMPTY
    }

    /**
     * A declared slot.
     *
     * @param id the slot's id
     * @param position the slot's place in declaration order, from 0
     * @param empty whether only ASCII space, tab and LF lie between its markers
     */
    public record Slot(SlotId id, int position, boolean empty) {
        /** Checks the position. */
        public Slot {
            Objects.requireNonNull(id, "id");
            if (position < 0) {
                throw new IllegalArgumentException("position must not be negative, but is " + position);
            }
        }
    }

    /**
     * A fault found in a prompt.
     *
     * @param kind what kind of fault
     * @param slot the slot it concerns; empty when the marker's id is not a slot id
     * @param line the 1-based line, of the canonical text, of the marker that raised it (of the start marker for
     *     {@code NOT_EMPTY})
     * @param reason what is wrong
     */
    public record Fault(Kind kind, Optional<SlotId> slot, int line, String reason) {
        /** Checks the parts. */
        public Fault {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The answer: the slots declared, in order, and the faults found, in the order found.
     *
     * @param slots the declared slots
     * @param faults the faults; empty when the prompt's slots are well formed and empty
     */
    public record Parse(List<Slot> slots, List<Fault> faults) {
        /** Copies the lists. */
        public Parse {
            slots = List.copyOf(slots);
            faults = List.copyOf(faults);
        }

        /**
         * The faults of one kind.
         *
         * @param kind the kind
         * @return those faults, in the order found
         */
        public List<Fault> faults(Kind kind) {
            return faults.stream().filter(fault -> fault.kind() == kind).toList();
        }
    }

    /**
     * Reads the slots a prompt declares.
     *
     * @param text the canonical text of the prompt
     * @return the slots in declaration order and the faults found
     */
    public static Parse parse(String text) {
        Objects.requireNonNull(text, "text");
        MarkerScan scan = new MarkerScan(text);
        Pairing pairing = new Pairing(text);
        for (Optional<Marker> marker = scan.next(); marker.isPresent(); marker = scan.next()) {
            pairing.accept(marker.get());
        }
        return pairing.finish();
    }

    /**
     * Whether the text holds a marker candidate of any id, valid or not, in a pair or alone, or leaves a comment open:
     * the question a fragment is asked (d7, FR-004). A fragment's unclosed {@code <!--} would swallow the base prompt
     * once the fragment is rendered into it (ADR-0066), so it counts like a marker.
     *
     * @param text the text
     * @return true when some comment's trimmed content ends in {@code :start} or {@code :end}, or a {@code <!--} has
     *     no {@code -->} after it
     */
    public static boolean containsMarker(String text) {
        Objects.requireNonNull(text, "text");
        MarkerScan scan = new MarkerScan(text);
        return scan.next().isPresent() || scan.leftOpen();
    }

    /** An id as a reason shows it: the file is untrusted, so a long id is cut. */
    private static String bounded(String id) {
        return id.length() <= MAX_SHOWN_ID ? id : id.substring(0, MAX_SHOWN_ID) + "…";
    }

    private static boolean isSlotWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n';
    }

    /** The text with the slot whitespace trimmed from both ends. */
    private static String trim(String text, int from, int to) {
        int start = from;
        int end = to;
        while (start < end && isSlotWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSlotWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean onlyWhitespace(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!isSlotWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * A marker candidate.
     *
     * @param start whether it is a start marker, else an end marker
     * @param rawId the content minus the suffix, trimmed
     * @param id the id, when it is a slot id
     * @param line the 1-based line its comment opens on
     * @param begin the index of its {@code <!--}
     * @param end the index after its {@code -->}
     */
    private record Marker(boolean start, String rawId, Optional<SlotId> id, int line, int begin, int end) {}

    /** A single forward scan over the comments of a text; each call resumes where the last marker ended. */
    private static final class MarkerScan {
        private final String text;
        private int position;
        private int line = 1;
        private boolean leftOpen;

        MarkerScan(String text) {
            this.text = text;
        }

        /** Whether the scan reached a {@code <!--} that no {@code -->} follows; true only once {@link #next} is spent. */
        boolean leftOpen() {
            return leftOpen;
        }

        Optional<Marker> next() {
            while (true) {
                int open = text.indexOf(COMMENT_OPEN, position);
                if (open < 0) {
                    position = text.length();
                    return Optional.empty();
                }
                int body = open + COMMENT_OPEN.length();
                // CommonMark: <!--> and <!---> are complete comments; the search for --> starts after the opener.
                int close = text.startsWith(">", body) || text.startsWith("->", body)
                        ? body
                        : text.indexOf(COMMENT_CLOSE, body);
                if (close < 0) {
                    leftOpen = true;
                    position = text.length();
                    return Optional.empty();
                }
                int end = text.startsWith(">", body)
                        ? body + 1
                        : text.startsWith("->", body) ? body + 2 : close + COMMENT_CLOSE.length();
                line += newlines(position, open);
                int openLine = line;
                line += newlines(open, end);
                position = end;
                Optional<Marker> marker = candidate(trim(text, body, close), openLine, open, end);
                if (marker.isPresent()) {
                    return marker;
                }
            }
        }

        private int newlines(int from, int to) {
            int count = 0;
            for (int i = from; i < to; i++) {
                if (text.charAt(i) == '\n') {
                    count++;
                }
            }
            return count;
        }

        private static Optional<Marker> candidate(String content, int line, int begin, int end) {
            boolean start = content.endsWith(START_SUFFIX);
            if (!start && !content.endsWith(END_SUFFIX)) {
                return Optional.empty();
            }
            int suffix = start ? START_SUFFIX.length() : END_SUFFIX.length();
            String rawId = trim(content, 0, content.length() - suffix);
            return Optional.of(new Marker(start, rawId, SlotId.tryParse(rawId), line, begin, end));
        }
    }

    /** The pairing rules over the markers in the order found. */
    private static final class Pairing {
        private final String text;
        private final List<Slot> slots = new ArrayList<>();
        private final List<Fault> faults = new ArrayList<>();
        private final Set<SlotId> declared = new HashSet<>();
        private @Nullable Marker open;

        Pairing(String text) {
            this.text = text;
        }

        void accept(Marker marker) {
            if (marker.id().isEmpty()) {
                malformed(
                        marker,
                        "the id '" + bounded(marker.rawId()) + "' does not match ^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");
            } else if (marker.start()) {
                start(marker, marker.id().get());
            } else {
                end(marker, marker.id().get());
            }
        }

        Parse finish() {
            Marker unclosed = open;
            if (unclosed != null) {
                malformed(unclosed, "the slot is opened and never closed");
            }
            return new Parse(slots, faults);
        }

        private void start(Marker marker, SlotId id) {
            Marker current = open;
            if (declared.contains(id)) {
                malformed(marker, "the slot is declared more than once");
            } else if (current != null) {
                malformed(marker, "the slot opens inside the open slot " + bounded(current.rawId()));
            } else {
                declared.add(id);
                open = marker;
            }
        }

        private void end(Marker marker, SlotId id) {
            Marker current = open;
            if (current == null) {
                malformed(marker, "the end marker has no start before it");
            } else if (!current.rawId().equals(id.value())) {
                malformed(
                        marker,
                        "the end marker closes " + bounded(id.value()) + " while " + bounded(current.rawId())
                                + " is open");
            } else {
                close(current, marker, id);
            }
        }

        private void close(Marker start, Marker end, SlotId id) {
            boolean empty = onlyWhitespace(text, start.end(), end.begin());
            slots.add(new Slot(id, slots.size(), empty));
            if (!empty) {
                faults.add(new Fault(
                        Kind.NOT_EMPTY, Optional.of(id), start.line(), "the slot holds more than whitespace"));
            }
            open = null;
        }

        private void malformed(Marker marker, String reason) {
            faults.add(new Fault(Kind.MALFORMED, marker.id(), marker.line(), reason));
        }
    }
}
