package ai.speckitty.charter.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.scanner.ScannerImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;
import org.snakeyaml.engine.v2.tokens.ScalarToken;
import org.snakeyaml.engine.v2.tokens.Token;

/**
 * A lossless YAML document model. The text is held as an ordered list of segments: every token the
 * YAML scanner produces, and the trivia (whitespace, comments, line breaks) between them. Rendering
 * concatenates the segments, so an unedited document renders byte for byte as it was read, and an
 * edit replaces one scalar segment while every other byte stays put. This is the document model of
 * docs/architecture/hexagonal-service.md ("Three models, kept apart"); the codec choice for write
 * migration is still open question 6.
 */
public final class YamlDocument {

    /** One run of source text: a token, or the trivia before the next token. */
    public sealed interface Segment permits Trivia, TokenText, Scalar {
        /**
         * The segment's source text.
         *
         * @return the exact characters the segment covers; rendering concatenates these
         */
        String text();
    }

    /**
     * Text between two tokens: whitespace, comments and line breaks.
     *
     * @param text the exact source text; never empty
     */
    public record Trivia(String text) implements Segment {}

    /**
     * A token other than a scalar, such as an indicator or a flow bracket.
     *
     * @param id the scanner's kind of token
     * @param text the token's exact source text; never empty, as zero-width tokens own no text
     */
    public record TokenText(Token.ID id, String text) implements Segment {}

    /**
     * A scalar token: its source text, its style and its value as the parser reads it.
     *
     * @param text the scalar's exact source text, quotes and escapes included
     * @param style how the scalar is written: plain, single-quoted, double-quoted, literal or folded
     * @param value the scalar's value as the YAML reader takes it
     */
    public record Scalar(String text, ScalarStyle style, String value) implements Segment {}

    private final List<Segment> segments;

    private YamlDocument(List<Segment> segments) {
        this.segments = segments;
    }

    /**
     * Reads {@code text} into segments.
     *
     * @param text a YAML stream
     * @return the document; rendering it gives {@code text} back byte for byte
     * @throws org.snakeyaml.engine.v2.exceptions.YamlEngineException when the scanner cannot tokenize the text
     */
    public static YamlDocument parse(String text) {
        Objects.requireNonNull(text, "text");
        int[] codePoints = text.codePoints().toArray();
        LoadSettings settings = Yaml.settings();
        ScannerImpl scanner = new ScannerImpl(settings, new StreamReader(settings, text));
        List<Segment> segments = new ArrayList<>();
        int position = 0;
        while (scanner.hasNext()) {
            Token token = scanner.next();
            int start = token.getStartMark().map(Mark::getIndex).orElse(position);
            int end = token.getEndMark().map(Mark::getIndex).orElse(start);
            if (start < position) {
                // A zero-width structural token (block end) reported behind the cursor: it owns no text.
                start = position;
                end = Math.max(end, position);
            }
            if (start > position) {
                segments.add(new Trivia(slice(codePoints, position, start)));
            }
            String tokenText = slice(codePoints, start, end);
            if (token instanceof ScalarToken scalar) {
                segments.add(new Scalar(tokenText, scalar.getStyle(), scalar.getValue()));
            } else if (!tokenText.isEmpty()) {
                segments.add(new TokenText(token.getTokenId(), tokenText));
            }
            position = end;
        }
        if (position < codePoints.length) {
            segments.add(new Trivia(slice(codePoints, position, codePoints.length)));
        }
        return new YamlDocument(segments);
    }

    private static String slice(int[] codePoints, int start, int end) {
        return new String(codePoints, start, end - start);
    }

    /**
     * Every segment, in document order.
     *
     * @return an unmodifiable copy of the segments
     */
    public List<Segment> segments() {
        return List.copyOf(segments);
    }

    /**
     * The scalar segments, in document order.
     *
     * @return the scalars; empty when the document has none
     */
    public List<Scalar> scalars() {
        return segments.stream()
                .filter(Scalar.class::isInstance)
                .map(Scalar.class::cast)
                .toList();
    }

    /**
     * Renders the document from its segments.
     *
     * @return the concatenated segment texts
     */
    public String render() {
        return segments.stream().map(Segment::text).collect(Collectors.joining());
    }

    /**
     * Replaces the value of the {@code index}-th scalar (in document order), writing it in the scalar's
     * own style where that style can carry the value, and as a double-quoted scalar otherwise.
     *
     * @param index the scalar's position among the document's scalars, from 0
     * @param value the new value
     * @return a new document; this one does not change
     * @throws IndexOutOfBoundsException when the document has no scalar at {@code index}
     */
    public YamlDocument withScalar(int index, String value) {
        List<Segment> edited = new ArrayList<>(segments);
        int seen = 0;
        for (int i = 0; i < edited.size(); i++) {
            if (edited.get(i) instanceof Scalar scalar && seen++ == index) {
                edited.set(
                        i, new Scalar(ScalarWriter.write(value, scalar.style(), scalar.text()), scalar.style(), value));
                return new YamlDocument(edited);
            }
        }
        throw new IndexOutOfBoundsException("No scalar " + index + " in a document of " + seen + " scalars");
    }

    /**
     * Rewrites every scalar value through {@code edit}, each written as {@link #withScalar} writes one.
     * Used by the round-trip: rewriting every scalar with its own value must load to the same data.
     *
     * @param edit the new value of a scalar, given its current value
     * @return a new document; this one does not change
     */
    public YamlDocument mapScalars(java.util.function.UnaryOperator<String> edit) {
        List<Segment> edited = new ArrayList<>(segments.size());
        for (Segment segment : segments) {
            if (segment instanceof Scalar scalar) {
                String value = edit.apply(scalar.value());
                edited.add(new Scalar(ScalarWriter.write(value, scalar.style(), scalar.text()), scalar.style(), value));
            } else {
                edited.add(segment);
            }
        }
        return new YamlDocument(edited);
    }

    /**
     * The {@code index}-th scalar.
     *
     * @param index the scalar's position among the document's scalars, from 0
     * @return the scalar; empty when the document has fewer scalars
     */
    public Optional<Scalar> scalar(int index) {
        List<Scalar> all = scalars();
        return index < all.size() ? Optional.of(all.get(index)) : Optional.empty();
    }
}
