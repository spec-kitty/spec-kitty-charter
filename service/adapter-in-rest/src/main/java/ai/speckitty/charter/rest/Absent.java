package ai.speckitty.charter.rest;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The one place an absent read-model value becomes {@code null}: the generated transport DTOs model an omitted
 * optional member as a {@code null} field, so the mapping hands them {@code null} explicitly.
 */
final class Absent {

    private Absent() {}

    /** The value of {@code value}, or {@code null} when it is empty, for an optional member of a generated DTO. */
    static <T> @Nullable T asNull(Optional<T> value) {
        return value.orElse(null);
    }
}
