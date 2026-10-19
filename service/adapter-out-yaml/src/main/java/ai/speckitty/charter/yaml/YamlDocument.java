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
        String text();
    }

    public record Trivia(String text) implements Segment {}

    public record TokenText(Token.ID id, String text) implements Segment {}

    /** A scalar token: its source text, its style and its value as the parser reads it. */
    public record Scalar(String text, ScalarStyle style, String value) implements Segment {}

    private final List<Segment> segments;

    private YamlDocument(List<Segment> segments) {
        this.segments = segments;
    }

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

    public List<Segment> segments() {
        return List.copyOf(segments);
    }

    public List<Scalar> scalars() {
        return segments.stream()
                .filter(Scalar.class::isInstance)
                .map(Scalar.class::cast)
                .toList();
    }

    /** Renders the document from its segments. */
    public String render() {
        return segments.stream().map(Segment::text).collect(Collectors.joining());
    }

    /**
     * Replaces the value of the {@code index}-th scalar (in document order), writing it in the scalar's
     * own style where that style can carry the value, and as a double-quoted scalar otherwise.
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

    /** The text of the {@code index}-th scalar, if there is one. */
    public Optional<Scalar> scalar(int index) {
        List<Scalar> all = scalars();
        return index < all.size() ? Optional.of(all.get(index)) : Optional.empty();
    }
}
