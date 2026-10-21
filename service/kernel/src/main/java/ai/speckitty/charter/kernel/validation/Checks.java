package ai.speckitty.charter.kernel.validation;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Reusable predicates over a field of the validated value. */
public final class Checks {

    private Checks() {}

    /**
     * Holds when the extracted field is not null.
     *
     * @param extractor reads the field
     * @param <S> the validated type
     * @return the predicate
     */
    public static <S> Predicate<S> haveNonNullField(Function<S, ? extends @Nullable Object> extractor) {
        return value -> extractor.apply(value) != null;
    }

    /**
     * Holds when the extracted field is null.
     *
     * @param extractor reads the field
     * @param <S> the validated type
     * @return the predicate
     */
    public static <S> Predicate<S> haveNullField(Function<S, ? extends @Nullable Object> extractor) {
        return value -> Objects.isNull(extractor.apply(value));
    }

    /**
     * Holds when the extracted text is neither null nor blank.
     *
     * @param extractor reads the text
     * @param <S> the validated type
     * @return the predicate
     */
    public static <S> Predicate<S> haveNonBlank(Function<S, @Nullable String> extractor) {
        return value -> {
            String text = extractor.apply(value);
            return text != null && !text.isBlank();
        };
    }
}
