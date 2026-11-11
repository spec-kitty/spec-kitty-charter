package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.web.JsonBodyGuard;
import ai.speckitty.charter.web.JsonBodyGuard.BodyFault;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * The members of a registration body: the first one the body sends that its schema does not declare (ADR-0064), the
 * list members {@link JsonBodyGuard} looks at for an explicit {@code null} (#292) or a {@code null} item (#309), and the
 * registration's refusal of what the guard finds.
 *
 * <p>Every registration schema is closed ({@code additionalProperties: false}), but the converter, as Spring Boot
 * configures it, skips a member the DTO does not declare. So a body that still sends a member the contract removed (a
 * directive's {@code relationships}, say) would be answered as a success while what it asserted was dropped. The
 * members a body may send are read off the generated DTO, and any other is refused before the converter reads the
 * body.
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
 */
final class RegistrationLists {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The registration members ADR-0064 removed because they carried relationships, as the wire spells them. */
    private static final Set<String> REMOVED_EDGE_MEMBERS =
            Set.of("relationships", "references", "directiveRefs", "procedures");

    private RegistrationLists() {}

    /**
     * The JSON names of every member a generated DTO declares, the members a registration body may send.
     *
     * @param dto the generated request DTO
     * @return the names its {@link JsonProperty} getters carry
     * @throws IllegalStateException when the DTO shows none, so the check can never be switched off unseen
     */
    static Set<String> members(Class<?> dto) {
        Set<String> members = Arrays.stream(dto.getMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && m.getParameterCount() == 0)
                .map(m -> m.getAnnotation(JsonProperty.class))
                .filter(p -> p != null && !p.value().isEmpty())
                .map(JsonProperty::value)
                .collect(Collectors.toUnmodifiableSet());
        if (members.isEmpty()) {
            throw new IllegalStateException(dto.getSimpleName() + " shows no member to accept.");
        }
        return members;
    }

    /**
     * The first top-level member the body sends that is not one of {@code declared}. The body is read as the converter
     * reads it ({@link JsonBodyGuard#detectsItself}), so the check cannot be stepped around by a declared charset.
     *
     * @param body the request body
     * @param contentType the request's content type, whose charset the body is read in; {@code null} when it has none
     * @param declared the members the registration's schema declares
     * @return the first undeclared member; empty when there is none, or the body is not a readable JSON object
     */
    static Optional<String> firstUnknown(byte[] body, @Nullable MediaType contentType, Set<String> declared) {
        try (JsonParser parser = parser(body, contentType)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return Optional.empty();
            }
            for (JsonToken token = parser.nextToken(); token == JsonToken.PROPERTY_NAME; token = parser.nextToken()) {
                String member = parser.currentName();
                if (!declared.contains(member)) {
                    return Optional.of(member);
                }
                parser.nextToken();
                parser.skipChildren();
            }
        } catch (JacksonException unreadable) {
            // Not ours to answer: the converter refuses the body as unreadable.
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * The first member of an item of a top-level list that the item's closed schema does not declare (ADR-0067: a
     * principle is {@code {name, description}}), named {@code <list>[<n>].<member>}. The body is read as
     * {@link #firstUnknown} reads it.
     *
     * @param body the request body
     * @param contentType the request's content type; {@code null} when it has none
     * @param list the top-level list whose items are checked
     * @param declared the members the item's schema declares
     * @return the first undeclared member, by its place; empty when there is none, or the body is not readable
     */
    static Optional<String> firstUnknownInItems(
            byte[] body, @Nullable MediaType contentType, String list, Set<String> declared) {
        try (JsonParser parser = parser(body, contentType)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return Optional.empty();
            }
            for (JsonToken token = parser.nextToken(); token == JsonToken.PROPERTY_NAME; token = parser.nextToken()) {
                boolean wanted = list.equals(parser.currentName());
                JsonToken value = parser.nextToken();
                if (!wanted || value != JsonToken.START_ARRAY) {
                    parser.skipChildren();
                    continue;
                }
                int index = 0;
                for (JsonToken item = parser.nextToken(); item != JsonToken.END_ARRAY; item = parser.nextToken()) {
                    if (item == JsonToken.START_OBJECT) {
                        for (JsonToken key = parser.nextToken();
                                key == JsonToken.PROPERTY_NAME;
                                key = parser.nextToken()) {
                            String member = parser.currentName();
                            if (!declared.contains(member)) {
                                return Optional.of(list + "[" + index + "]." + member);
                            }
                            parser.nextToken();
                            parser.skipChildren();
                        }
                    } else {
                        parser.skipChildren();
                    }
                    index++;
                }
            }
        } catch (JacksonException unreadable) {
            // Not ours to answer: the converter refuses the body as unreadable.
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * The refusal detail for a member the registration's schema does not declare. A member that once carried
     * relationships (ADR-0064) is told where a relationship is written now; any other member, a typo say, is named
     * only.
     *
     * @param registration the registration's kind, as a refusal names it
     * @param member the undeclared member
     * @return the detail
     */
    static String unknownMemberDetail(String registration, String member) {
        String detail = "A " + registration + " registration has no member " + member;
        return REMOVED_EDGE_MEMBERS.contains(member)
                ? detail + "; a relationship is registered through PUT /packs/{packId}/relationships."
                : detail + ".";
    }

    private static JsonParser parser(byte[] body, @Nullable MediaType contentType) {
        Charset charset = contentType == null ? null : contentType.getCharset();
        if (charset == null || JsonBodyGuard.detectsItself(charset)) {
            return JSON.createParser(body);
        }
        return JSON.createParser(new InputStreamReader(new ByteArrayInputStream(body), charset));
    }

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
     * A registration's refusal of what the body guard finds: {@code validation_failed}, naming the limit or the list.
     *
     * @param registration the registration's kind, as the refusal names it
     * @return the refusal of each fault
     */
    static Function<BodyFault, CharterRefusal> refusal(String registration) {
        return fault -> new CharterRefusal(
                RefusalCode.VALIDATION_FAILED,
                switch (fault) {
                    case BodyFault.TooLarge tooLarge ->
                        "The " + registration + " registration body is larger than " + tooLarge.limit() + " bytes.";
                    case BodyFault.NullFound found -> found.value().detail();
                });
    }
}
