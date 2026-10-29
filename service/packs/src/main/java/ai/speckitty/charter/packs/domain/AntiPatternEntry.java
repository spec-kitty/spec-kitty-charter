package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;
import java.util.Optional;

/**
 * Something to avoid, as an entry of its owning artifact (a {@link Procedure} today). It is a value of its owner with
 * no identity of its own; only the title is required, so a bare "avoid this" entry needs no invented text.
 *
 * <p>It is distinct from {@link StyleguideAntiPattern}, whose four parts are all required; the two share a name in the
 * sanitized shape only, and the styleguide kind is left unchanged here.
 *
 * @param title the entry's {@code title}
 * @param content the entry's {@code content}, its body; empty when absent
 * @param badExample the entry's {@code bad_example}; empty when absent
 * @param goodExample the entry's {@code good_example}; empty when absent
 */
public record AntiPatternEntry(
        String title, Optional<String> content, Optional<String> badExample, Optional<String> goodExample) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("An anti-pattern entry needs a title that is not blank.", text -> !text.isBlank()));

    /**
     * Checks that every component is present and the title is not blank.
     *
     * @param title the entry's title
     * @param content the content, possibly empty
     * @param badExample the bad example, possibly empty
     * @param goodExample the good example, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the title is blank
     */
    public AntiPatternEntry {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(badExample, "badExample");
        Objects.requireNonNull(goodExample, "goodExample");
        RULES.guard(title);
    }
}
