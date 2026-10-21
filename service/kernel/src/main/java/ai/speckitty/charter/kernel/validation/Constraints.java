package ai.speckitty.charter.kernel.validation;

/** Ready-made constraints and the factory for custom ones. */
public final class Constraints {

    private static final Constraint<String> NOT_BLANK =
            new Constraint<>("mustn't be blank", text -> text != null && !text.isBlank());

    private Constraints() {}

    /**
     * A custom constraint.
     *
     * @param description the human description
     * @param predicate the rule; true means the value is acceptable
     * @param <T> the validated type
     * @return the constraint
     */
    public static <T> Constraint<T> of(String description, java.util.function.Predicate<T> predicate) {
        return new Constraint<>(description, predicate);
    }

    /**
     * The constraint that text is neither null nor blank.
     *
     * @return the shared instance
     */
    public static Constraint<String> isNotBlank() {
        return NOT_BLANK;
    }
}
