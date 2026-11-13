package ai.speckitty.charter.kernel.validation;

import java.util.function.Predicate;

/** Ready-made constraints and the factory for custom ones. */
public final class Constraints {

    private static final Constraint<String> NOT_BLANK =
            new Constraint<>("mustn't be blank", text -> text != null && !text.isBlank());

    private Constraints() {}

    /**
     * A value object's invariant: code {@link ReasonCode#INVARIANT}, a {@link Severity#VIOLATION}, whose detail is its
     * description. {@link Constraint#coded}, {@link Constraint#locatedBy} and {@link Constraint#detailedBy} refine
     * it.
     *
     * @param description the human description
     * @param predicate the rule; true means the value is acceptable
     * @param <T> the validated type
     * @return the constraint
     */
    public static <T> Constraint<T> of(String description, Predicate<? super T> predicate) {
        return new Constraint<>(description, predicate);
    }

    /**
     * The rule that text is neither null nor blank.
     *
     * @return the shared instance
     */
    public static Constraint<String> notBlank() {
        return NOT_BLANK;
    }
}
