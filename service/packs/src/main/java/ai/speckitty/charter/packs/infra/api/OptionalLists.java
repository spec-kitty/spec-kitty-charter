package ai.speckitty.charter.packs.infra.api;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An optional list member of a generated request DTO is {@code null} when the request leaves it out
 * ({@code containerDefaultToNull}), so the activation document can tell an absent member from {@code []} (#125,
 * ADR-0020 D4). A registration body has no such distinction: its absent list means empty, as it did before. An
 * explicit {@code null}, or a {@code null} item, never reaches here: {@link RegistrationBodyAdvice} and
 * {@link ToolguideRegistrationBodyAdvice} refuse it before the body is read (#292, #309).
 */
final class OptionalLists {

    private OptionalLists() {}

    /**
     * The list, or an empty one when the request left the member out.
     *
     * @param list the member's value, {@code null} when absent
     * @param <T> the element type
     * @return the list, never {@code null}
     */
    static <T> List<T> orEmpty(@Nullable List<T> list) {
        return list == null ? List.of() : list;
    }
}
