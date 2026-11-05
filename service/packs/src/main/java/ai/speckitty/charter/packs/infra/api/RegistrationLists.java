package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.infra.api.JsonBodyGuard.BodyFault;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The list members of a registration body, which {@link JsonBodyGuard} looks at for an explicit {@code null} (#292) or
 * a {@code null} item (#309), and the registration's refusal of what the guard finds.
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
