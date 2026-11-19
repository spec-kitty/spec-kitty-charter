package ai.speckitty.charter.kernel.domain;

import ai.speckitty.charter.kernel.validation.Constraint;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import org.jspecify.annotations.Nullable;

/**
 * The descriptor identity of a {@code Pack}: the {@code pack_id} its {@code pack.yaml} declares, the pack's immutable
 * identity across registrations (the {@code Ulid} of {@code pack-descriptor.schema.json}). It is a {@link
 * IdentifierGrammar#ULID ULID}. It is never the name a pack is registered, stored and addressed under: that is the
 * {@link PackIdentifier} (ADR-0075 decision 2, #583). No code converts one into the other; the configured packs map
 * one to the other.
 *
 * @param value the ULID text
 */
public record PackUlid(String value) {

    /** What a descriptor's pack id must be, as a refusal says it. */
    public static final String GRAMMAR_DETAIL = "A descriptor's pack_id must be a ULID: 26 characters of upper-case"
            + " Crockford base32 (the digits and the capital letters without I, L, O and U).";

    private static final Constraint<String> RULE = IdentifierGrammar.ulid().described(GRAMMAR_DETAIL);

    private static final ModularRuleset<String> RULES =
            ModularRuleset.<String>emptyRules().must(RULE);

    /**
     * Checks that the text is a ULID.
     *
     * @param value the ULID text
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text is null or no ULID
     */
    public PackUlid {
        RULES.guard(value);
    }

    /**
     * Whether a text is a descriptor pack id, for a caller that refuses a bad one in its own terms.
     *
     * @param text the candidate, possibly {@code null}
     * @return {@code true} when the text is a ULID
     */
    public static boolean isUlid(@Nullable String text) {
        return IdentifierGrammar.isUlid(text);
    }
}
