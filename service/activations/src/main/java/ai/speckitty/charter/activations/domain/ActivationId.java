package ai.speckitty.charter.activations.domain;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * An activation's opaque id, minted by the service or chosen by the client (ADR-0020 D2, D3). A ULID and a UUID both
 * satisfy its grammar, {@code ^[A-Za-z0-9][A-Za-z0-9-]{0,63}$}; the service never parses it. Equal by value.
 *
 * @param value the id
 */
public record ActivationId(String value) {

    private static final Pattern GRAMMAR = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]{0,63}");

    /**
     * Checks the grammar.
     *
     * @param value the id
     * @throws IllegalArgumentException when the id breaks the grammar
     */
    public ActivationId {
        Objects.requireNonNull(value, "value");
        if (!GRAMMAR.matcher(value).matches()) {
            throw new IllegalArgumentException("An activation id is 1 to 64 letters, digits and hyphens, not a "
                    + "leading hyphen: '" + value + "'.");
        }
    }

    /**
     * Mints a new id. The domain generates no ULID itself: the application supplies the generator.
     *
     * @param ulid the generator, for example of ULIDs
     * @return the new id
     * @throws IllegalArgumentException when the generator answers text that breaks the grammar
     */
    public static ActivationId mint(Supplier<String> ulid) {
        return new ActivationId(ulid.get());
    }

    @Override
    public String toString() {
        return value;
    }
}
