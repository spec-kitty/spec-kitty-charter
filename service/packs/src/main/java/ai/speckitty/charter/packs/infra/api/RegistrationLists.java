package ai.speckitty.charter.packs.infra.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * The list members of a registration body, and the first one a body sends as an explicit {@code null} (#292).
 *
 * <p>The generated DTO reads an absent list and a {@code null} one alike ({@code containerDefaultToNull}), and the
 * controllers read an absent optional list as empty ({@link OptionalLists}). The contract types every list as an
 * array, so {@code null} is never a value: a registration leaves out a list it does not carry, and an explicit
 * {@code null} is refused before the converter reads the body. The members are read off the generated DTO, so a list
 * the contract adds is guarded without a change to the main code ({@code RegistrationListsTest} pins the names). Every
 * list member of a registration is a top-level one; none of their item schemas holds a list.
 *
 * <p>The body is read as the message converter reads it, so the check cannot be stepped around: in the charset the
 * request declares when that is not one JSON detects itself, and the last of a repeated member counts. It is read as
 * a token stream; no tree of the body is built. A body that is not a readable JSON object is left to the converter.
 * The mapper is a default one, as the converter's is: the service configures no reading feature.
 */
final class RegistrationLists {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The charsets JSON detects from the bytes, which the converter therefore reads from the bytes too. */
    private static final Set<String> DETECTED = Set.of("UTF-8", "UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE");

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
     * The first of the given members the body sends as an explicit {@code null}.
     *
     * @param body the request body
     * @param contentType the request's content type, whose charset the body is read in; {@code null} when it has none
     * @param members the members to look at, in the order they are looked at
     * @return the member; empty when none is {@code null}, or the body is not a readable JSON object
     */
    static Optional<String> firstExplicitNull(byte[] body, @Nullable MediaType contentType, List<String> members) {
        Set<String> nulls = new HashSet<>();
        try (JsonParser parser = parser(body, contentType)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return Optional.empty();
            }
            for (JsonToken token = parser.nextToken(); token == JsonToken.PROPERTY_NAME; token = parser.nextToken()) {
                String member = parser.currentName();
                JsonToken value = parser.nextToken();
                // The last of a repeated member counts, as it does for the converter.
                if (value == JsonToken.VALUE_NULL) {
                    nulls.add(member);
                } else {
                    nulls.remove(member);
                    parser.skipChildren();
                }
            }
        } catch (JacksonException unreadable) {
            // Not ours to answer: the converter refuses the body as unreadable.
            return Optional.empty();
        }
        return members.stream().filter(nulls::contains).findFirst();
    }

    private static JsonParser parser(byte[] body, @Nullable MediaType contentType) {
        Charset charset = contentType == null ? null : contentType.getCharset();
        if (charset == null || DETECTED.contains(charset.name().toUpperCase(Locale.ROOT))) {
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
