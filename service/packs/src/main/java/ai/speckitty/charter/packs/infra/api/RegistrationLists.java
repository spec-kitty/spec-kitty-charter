package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * The list members of a registration body, the first one it sends as an explicit {@code null} (#292) or with a
 * {@code null} item (#309), and the bounded read of the body (#310).
 *
 * <p>The generated DTO reads an absent list and a {@code null} one alike ({@code containerDefaultToNull}), and the
 * controllers read an absent optional list as empty ({@link OptionalLists}). The contract types every list as an
 * array of strings or objects, so {@code null} is never a value, neither for the list nor for an item in it: a
 * registration leaves out a list it does not carry, and an explicit {@code null} list or item is refused before the
 * converter reads the body. The converter keeps a {@code null} item as a {@code null} element and bean validation does
 * not look at one, so without this check it would reach the controller's mapping and answer {@code 500}. The members
 * are read off the generated DTO, so a list the contract adds is guarded without a change to the main code
 * ({@code RegistrationListsTest} pins the names). Every list member of a registration is a top-level one; none of
 * their item schemas holds a list, and a {@code null} field inside an object item is bean validation's.
 *
 * <p>The body is read as the message converter reads it, so the check cannot be stepped around: in the charset the
 * request declares when that is not one JSON detects itself, and the last of a repeated member counts. It is read as
 * a token stream; no tree of the body is built. A body that is not a readable JSON object is left to the converter.
 * The mapper is a default one. The converter's is Spring Boot's, which reads tokens alike as long as no
 * {@code spring.jackson} read feature is set (none is); one that is set (comments, say) must be set here too. The
 * body is read at most one byte past a limit, so an oversized one is never held whole.
 */
final class RegistrationLists {

    /**
     * The largest registration body without a guide, in bytes: 1 MiB (#310). It is the toolguide's room for the fields
     * beside its guide, so every registration holds its fields to the same budget; the largest pack artifact the
     * service ships is about 14 KiB.
     */
    static final long BODY_LIMIT = 1024L * 1024;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** No {@code null} in the member; never held by a {@link NullValue}. */
    private static final int NONE = -2;

    // The charsets Spring's AbstractJacksonHttpMessageConverter treats as self-detecting and so reads from the bytes,
    // leaving Jackson to detect the encoding (endianness included): the five JsonEncoding names, plus US-ASCII and the
    // bare UTF-16/UTF-32 it also admits. We must list exactly these, because for a bare UTF-16/UTF-32 with no BOM a
    // Java InputStreamReader would default to big-endian while the converter detects the real endianness, so a body
    // in one of them would otherwise step past this guard (see RegistrationListsTest). The converter matches the
    // charset's canonical name case-sensitively (spring-web 7.0.9, readJavaType), and so does this check.
    private static final Set<String> DETECTED =
            Set.of("UTF-8", "UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE", "US-ASCII", "UTF-16", "UTF-32");

    private RegistrationLists() {}

    /**
     * The JSON names of a generated DTO's list members, sorted.
     *
     * @param dto the generated request DTO
     * @return the names its {@link List}-typed {@link JsonProperty} getters carry
     * @throws IllegalStateException when the DTO shows none, so a guard can never be switched off unseen (in a native
     *     image without the reflection metadata, say)
     */
    static List<String> of(Class<?> dto) {
        List<String> members = Arrays.stream(dto.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && m.getParameterCount() == 0)
                .filter(m -> List.class.isAssignableFrom(m.getReturnType()))
                .map(m -> m.getAnnotation(JsonProperty.class))
                .filter(p -> p != null && !p.value().isEmpty())
                .map(JsonProperty::value)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (members.isEmpty()) {
            throw new IllegalStateException(dto.getSimpleName() + " shows no list member to guard against null.");
        }
        return members;
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
    static Optional<NullValue> firstNull(
            byte[] body, @Nullable MediaType contentType, List<String> fields, List<String> lists) {
        Map<String, NullValue> nulls = new HashMap<>();
        try (JsonParser parser = parser(body, contentType)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return Optional.empty();
            }
            for (JsonToken token = parser.nextToken(); token == JsonToken.PROPERTY_NAME; token = parser.nextToken()) {
                String member = parser.currentName();
                JsonToken value = parser.nextToken();
                if (!fields.contains(member) && !lists.contains(member)) {
                    // Not a watched member: skipped, so the map holds at most one entry per watched member.
                    parser.skipChildren();
                    continue;
                }
                int item;
                if (value == JsonToken.VALUE_NULL) {
                    item = NullValue.WHOLE;
                } else if (value == JsonToken.START_ARRAY && lists.contains(member)) {
                    item = firstNullItem(parser);
                } else {
                    parser.skipChildren();
                    item = NONE;
                }
                // The last of a repeated member counts, as it does for the converter.
                if (item == NONE) {
                    nulls.remove(member);
                } else {
                    nulls.put(member, new NullValue(member, item));
                }
            }
            if (parser.nextToken() != null) {
                // A token after the object: the converter refuses the body as unreadable (FAIL_ON_TRAILING_TOKENS).
                return Optional.empty();
            }
        } catch (JacksonException unreadable) {
            // Not ours to answer: the converter refuses the body as unreadable.
            return Optional.empty();
        }
        return Stream.concat(fields.stream(), lists.stream())
                .map(nulls::get)
                .filter(Objects::nonNull)
                .findFirst();
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
     * A member a registration body sends as {@code null}, or a list member that holds a {@code null} item.
     *
     * @param member the member's JSON name
     * @param item the index of the list's first {@code null} item, or {@link #WHOLE} when the member itself is
     *     {@code null}
     */
    record NullValue(String member, int item) {

        /** The member itself is {@code null}, not an item in it. */
        static final int WHOLE = -1;

        /**
         * The refusal detail, naming the list (and the item). It speaks of a list, so it is for a list member only; a
         * caller that guards another field words that field's refusal itself.
         *
         * @return the detail
         */
        String detail() {
            return item == WHOLE
                    ? nullListDetail(member)
                    : "Item " + item + " of the " + member + " list is null; a list holds no null item.";
        }
    }

    private static JsonParser parser(byte[] body, @Nullable MediaType contentType) {
        Charset charset = contentType == null ? null : contentType.getCharset();
        if (charset == null || DETECTED.contains(charset.name())) {
            return JSON.createParser(body);
        }
        return JSON.createParser(new InputStreamReader(new ByteArrayInputStream(body), charset));
    }

    /**
     * The refusal detail for a list sent as {@code null}.
     *
     * @param member the list member
     * @return the detail
     */
    static String nullListDetail(String member) {
        return "The " + member + " list is null; a list is sent as an array, [] when it is empty, and an optional one"
                + " may be left out.";
    }

    /**
     * Reads a registration body, refusing one larger than the limit before it is read whole: by its
     * {@code Content-Length} before a byte is read, and otherwise one byte past the limit (#310, NFR-005).
     *
     * @param message the request
     * @param limit the largest body accepted, in bytes
     * @param registration the registration's kind, as the refusal names it
     * @return the body
     * @throws IOException when the body cannot be read
     * @throws CharterRefusal {@code validation_failed} when the body is larger than the limit
     */
    static byte[] readBounded(HttpInputMessage message, long limit, String registration) throws IOException {
        if (message.getHeaders().getContentLength() > limit) {
            throw tooLarge(limit, registration);
        }
        byte[] body;
        try (InputStream in = message.getBody()) {
            // One byte past the limit is enough to know the body is over it; the rest is never read.
            body = in.readNBytes(Math.toIntExact(Math.min(limit + 1, Integer.MAX_VALUE - 8)));
        }
        if (body.length > limit) {
            throw tooLarge(limit, registration);
        }
        return body;
    }

    private static CharterRefusal tooLarge(long limit, String registration) {
        return new CharterRefusal(
                RefusalCode.VALIDATION_FAILED,
                "The " + registration + " registration body is larger than " + limit + " bytes.");
    }

    /**
     * A message that hands on a body already read, with the original headers.
     *
     * @param headers the request's headers
     * @param body the body, read
     * @return the message the converter reads instead
     */
    static HttpInputMessage replay(HttpHeaders headers, byte[] body) {
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
