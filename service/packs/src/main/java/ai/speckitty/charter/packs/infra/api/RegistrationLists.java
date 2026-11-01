package ai.speckitty.charter.packs.infra.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The list members of a registration body, and the first one a body sends as an explicit {@code null} (#292).
 *
 * <p>The generated DTO reads an absent list and a {@code null} one alike ({@code containerDefaultToNull}), and the
 * controllers read both as empty ({@link OptionalLists}). The contract types every list as an array, so {@code null}
 * is never a value: a registration leaves out a list it does not carry, and an explicit {@code null} is refused before
 * the converter reads the body. The members are read off the generated DTO, so a list the contract adds is covered
 * without a change here. Every list member of a registration is a top-level one; none of their item schemas holds a
 * list.
 */
final class RegistrationLists {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RegistrationLists() {}

    /**
     * The JSON names of a generated DTO's list members, sorted.
     *
     * @param dto the generated request DTO
     * @return the names its {@link List}-typed {@link JsonProperty} getters carry
     */
    static List<String> of(Class<?> dto) {
        return Arrays.stream(dto.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && m.getParameterCount() == 0)
                .filter(m -> List.class.isAssignableFrom(m.getReturnType()))
                .map(m -> m.getAnnotation(JsonProperty.class))
                .filter(p -> p != null && !p.value().isEmpty())
                .map(JsonProperty::value)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * The first of the given members the body sends as an explicit {@code null}.
     *
     * @param body the request body
     * @param members the members to look at, in the order they are looked at
     * @return the member; empty when none is {@code null}, or the body is not a JSON object
     */
    static Optional<String> firstExplicitNull(byte[] body, List<String> members) {
        JsonNode tree;
        try {
            tree = JSON.readTree(body);
        } catch (JacksonException unreadable) {
            // Not ours to answer: the converter refuses the body as unreadable.
            return Optional.empty();
        }
        if (tree == null || !tree.isObject()) {
            return Optional.empty();
        }
        return members.stream()
                .filter(member -> tree.has(member) && tree.get(member).isNull())
                .findFirst();
    }

    /**
     * The refusal detail for a list sent as {@code null}.
     *
     * @param member the list member
     * @return the detail
     */
    static String nullListDetail(String member) {
        return "The " + member + " list is null; a registration leaves out a list it does not carry, and [] is an"
                + " empty one.";
    }
}
