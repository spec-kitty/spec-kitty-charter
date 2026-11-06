package ai.speckitty.charter.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * The slice-independent guard of a JSON request body, applied by a body advice before the message converter reads it:
 * the bounded read (#310, #323) and the first top-level member sent as an explicit {@code null}, or a list sent with a
 * {@code null} item (#292, #309). It decides nothing about the refusal: a caller maps each {@link BodyFault} to its own
 * code. The packs registration advices answer {@code 422 validation_failed}; the activation body advice answers
 * {@code 422 activation_invalid} with a violation built from {@link NullValue#pointer()}. Both slices' inbound adapters
 * share it from this module, which names no slice.
 *
 * <p>The body is read as the message converter reads it, so the check cannot be stepped around: in the charset the
 * request declares when that is not one JSON detects itself, and the last of a repeated member counts. It is read as a
 * token stream; no tree of the body is built. A body that is not a readable JSON object is left to the converter. The
 * mapper is a default one. The converter's is Spring Boot's, which reads tokens alike as long as no
 * {@code spring.jackson} read feature is set (none is); one that is set (comments, say) must be set here too. The body
 * is read at most one byte past a limit, so an oversized one is never held whole.
 */
public final class JsonBodyGuard {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** No {@code null} in the member; never held by a {@link NullValue}. */
    private static final int NONE = -2;

    // The charsets Spring's AbstractJacksonHttpMessageConverter treats as self-detecting and so reads from the bytes,
    // leaving Jackson to detect the encoding (endianness included): the five JsonEncoding names, plus US-ASCII and the
    // bare UTF-16/UTF-32 it also admits. We must list exactly these, because for a bare UTF-16/UTF-32 with no BOM a
    // Java InputStreamReader would default to big-endian while the converter detects the real endianness, so a body
    // in one of them would otherwise step past this guard (see JsonBodyGuardTest). The converter matches the
    // charset's canonical name case-sensitively (spring-web 7.0.9, readJavaType), and so does this check.
    private static final Set<String> DETECTED =
            Set.of("UTF-8", "UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE", "US-ASCII", "UTF-16", "UTF-32");

    private JsonBodyGuard() {}

    /** What the guard found wrong with a body; the caller maps it to its own refusal. */
    public sealed interface BodyFault {

        /**
         * The body is larger than the limit.
         *
         * @param limit the largest body accepted, in bytes
         */
        record TooLarge(long limit) implements BodyFault {}

        /**
         * The body sends a member as {@code null}, or a list with a {@code null} item.
         *
         * @param value the member and what is {@code null} in it
         */
        record NullFound(NullValue value) implements BodyFault {}
    }

    /**
     * A member a body sends as {@code null}, or a list member that holds a {@code null} item.
     *
     * @param member the member's JSON name
     * @param item the index of the list's first {@code null} item, or {@link #WHOLE} when the member itself is
     *     {@code null}
     */
    public record NullValue(String member, int item) {

        /** The member itself is {@code null}, not an item in it. */
        public static final int WHOLE = -1;

        /**
         * The RFC 6901 JSON Pointer of what is {@code null}: the member, and the item in it when one is.
         *
         * @return for example {@code /references} or {@code /references/3}
         */
        public String pointer() {
            String escaped = member.replace("~", "~0").replace("/", "~1");
            return item == WHOLE ? "/" + escaped : "/" + escaped + "/" + item;
        }

        /**
         * The refusal detail, naming the list (and the item). It speaks of a list, so it is for a list member only; a
         * caller that guards another field words that field's refusal itself.
         *
         * @return the detail
         */
        public String detail() {
            return item == WHOLE
                    ? "The " + member + " list is null; a list is sent as an array, [] when it is empty, and an"
                            + " optional one may be left out."
                    : "Item " + item + " of the " + member + " list is null; a list holds no null item.";
        }
    }

    /**
     * Reads a body, refusing one larger than the limit before it is read whole: by its {@code Content-Length} before a
     * byte is read, and otherwise one byte past the limit (#310, NFR-005).
     *
     * @param message the request
     * @param limit the largest body accepted, in bytes
     * @param refusal builds the caller's refusal of a {@link BodyFault.TooLarge}
     * @return the body
     * @throws IOException when the body cannot be read
     */
    public static byte[] readBounded(
            HttpInputMessage message, long limit, Function<BodyFault, ? extends RuntimeException> refusal)
            throws IOException {
        if (message.getHeaders().getContentLength() > limit) {
            throw refusal.apply(new BodyFault.TooLarge(limit));
        }
        byte[] body;
        try (InputStream in = message.getBody()) {
            // One byte past the limit is enough to know the body is over it; the rest is never read.
            body = in.readNBytes(Math.toIntExact(Math.min(limit + 1, Integer.MAX_VALUE - 8)));
        }
        if (body.length > limit) {
            throw refusal.apply(new BodyFault.TooLarge(limit));
        }
        return body;
    }

    /**
     * The first of the given lists the body sends as an explicit {@code null} or with a {@code null} item.
     *
     * @param body the request body
     * @param contentType the request's content type, whose charset the body is read in; {@code null} when it has none
     * @param lists the top-level list members, looked at in this order
     * @return the member and what is {@code null} in it; empty when nothing is, or the body is not a readable JSON
     *     object
     */
    public static Optional<NullValue> firstNull(byte[] body, @Nullable MediaType contentType, List<String> lists) {
        return firstNull(body, contentType, List.of(), lists);
    }

    /**
     * The first of the given members the body sends as an explicit {@code null}, or, for a list, with a {@code null}
     * item.
     *
     * @param body the request body
     * @param contentType the request's content type, whose charset the body is read in; {@code null} when it has none
     * @param fields the members that are not lists, looked at first, in this order; only an explicit {@code null}
     *     counts for these
     * @param lists the list members, looked at next, in this order; an explicit {@code null} or a {@code null} item
     *     counts for these
     * @return the member and what is {@code null} in it; empty when nothing is, or the body is not a readable JSON
     *     object
     */
    public static Optional<NullValue> firstNull(
            byte[] body, @Nullable MediaType contentType, List<String> fields, List<String> lists) {
        Map<String, NullValue> nulls;
        try (JsonParser parser = parser(body, contentType)) {
            nulls = nullMembers(parser, fields, lists);
        } catch (JacksonException unreadable) {
            // Not ours to answer: the converter refuses the body as unreadable.
            return Optional.empty();
        }
        return Stream.concat(fields.stream(), lists.stream())
                .map(nulls::get)
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * The watched members of a top-level object that are {@code null} or hold a {@code null} item, by name; empty when
     * the body is no single JSON object.
     */
    private static Map<String, NullValue> nullMembers(JsonParser parser, List<String> fields, List<String> lists) {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            return Map.of();
        }
        Map<String, NullValue> nulls = new HashMap<>();
        for (JsonToken token = parser.nextToken(); token == JsonToken.PROPERTY_NAME; token = parser.nextToken()) {
            String member = parser.currentName();
            JsonToken value = parser.nextToken();
            int item = fields.contains(member) || lists.contains(member)
                    ? nullIn(parser, value, lists.contains(member))
                    : skipped(parser);
            // The last of a repeated member counts, as it does for the converter.
            if (item == NONE) {
                nulls.remove(member);
            } else {
                nulls.put(member, new NullValue(member, item));
            }
        }
        // A token after the object: the converter refuses the body as unreadable (FAIL_ON_TRAILING_TOKENS).
        return parser.nextToken() == null ? nulls : Map.of();
    }

    /** What is {@code null} in a watched member's value: {@link NullValue#WHOLE}, an item's index, or {@link #NONE}. */
    private static int nullIn(JsonParser parser, JsonToken value, boolean list) {
        if (value == JsonToken.VALUE_NULL) {
            return NullValue.WHOLE;
        }
        if (value == JsonToken.START_ARRAY && list) {
            return firstNullItem(parser);
        }
        return skipped(parser);
    }

    /** Skips a member that is not watched, or whose value cannot hold a {@code null} this guard answers. */
    private static int skipped(JsonParser parser) {
        parser.skipChildren();
        return NONE;
    }

    /** Reads an array to its end; the index of its first {@code null} item, or {@link #NONE}. */
    private static int firstNullItem(JsonParser parser) {
        int first = NONE;
        int index = 0;
        for (JsonToken item = parser.nextToken(); item != JsonToken.END_ARRAY; item = parser.nextToken(), index++) {
            if (item == null) {
                // Defensive: Jackson throws on an end of input inside an array before this; the converter refuses it.
                return NONE;
            }
            if (item == JsonToken.VALUE_NULL) {
                first = first == NONE ? index : first;
            } else {
                parser.skipChildren();
            }
        }
        return first;
    }

    /**
     * Whether the message converter reads a body declared in this charset from its bytes, leaving JSON to detect the
     * encoding (endianness included), rather than decoding it in the charset. A reader of a request body that must see
     * what the converter binds decodes it itself only when this is false.
     *
     * @param charset the charset the request declares
     * @return true when the converter detects the encoding from the bytes
     */
    public static boolean detectsItself(Charset charset) {
        return DETECTED.contains(charset.name());
    }

    private static JsonParser parser(byte[] body, @Nullable MediaType contentType) {
        Charset charset = contentType == null ? null : contentType.getCharset();
        if (charset == null || detectsItself(charset)) {
            return JSON.createParser(body);
        }
        return JSON.createParser(new InputStreamReader(new ByteArrayInputStream(body), charset));
    }

    /**
     * A message that hands on a body already read, with the original headers.
     *
     * @param headers the request's headers
     * @param body the body, read
     * @return the message the converter reads instead
     */
    public static HttpInputMessage replay(HttpHeaders headers, byte[] body) {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }
}
