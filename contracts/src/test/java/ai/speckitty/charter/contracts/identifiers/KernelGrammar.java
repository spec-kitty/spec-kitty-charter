package ai.speckitty.charter.contracts.identifiers;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RoleBearer;
import ai.speckitty.charter.kernel.markdown.SlotId;
import java.util.Optional;

/**
 * The kernel predicates an identifier site of the contracts restates (#585), each with the grammar form the contracts
 * write it in.
 *
 * <p>The mapping rule, in full: a site's pattern, without its {@code ^...$} anchors and a leading {@code (?!.*\n)}
 * newline guard, must equal one {@link #form}. The form picks the grammar; a sibling {@code not: {const: generic}}
 * turns the letter-first slug into the role that refuses {@value RoleBearer#GENERIC}; and a slug capped at {@value
 * PackIdentifier#MAX_LENGTH} is a pack id, the only kernel slug with that cap. Nothing else is read.
 */
enum KernelGrammar {

    /** {@link IdentifierGrammar#isSlug}: a kebab slug of at most 64 characters, digit-first allowed. */
    SLUG("[a-z0-9]+(-[a-z0-9]+)*"),

    /** {@link PackIdentifier#isPackId}: the slug capped at 63. */
    PACK_ID(""),

    /** {@link IdentifierGrammar#isLetterFirstSlug}, which is {@link RoleBearer#isRole}: the id and role grammar. */
    LETTER_FIRST_SLUG("[a-z][a-z0-9]*(-[a-z0-9]+)*"),

    /** {@link RoleBearer#isRole} that is not {@value RoleBearer#GENERIC}, written without lookahead in the contract. */
    ROLE_NOT_GENERIC(
            "(?:(?:[a-fh-z][a-z0-9]*|g(?:[a-df-z0-9][a-z0-9]*|e(?:[a-mo-z0-9][a-z0-9]*|n(?:[a-df-z0-9][a-z0-9]*"
                    + "|e(?:[a-qs-z0-9][a-z0-9]*|r(?:[a-hj-z0-9][a-z0-9]*|i(?:[abd-z0-9][a-z0-9]*|c[a-z0-9]+)?)?)?)?)?)?)"
                    + "(?:-[a-z0-9]+)*|generic(?:-[a-z0-9]+)+)"),

    /** {@link SlotId}: two letter-first parts, each at most 64 characters. */
    SLOT_ID(SlotId.GRAMMAR.substring(1, SlotId.GRAMMAR.length() - 1));

    private static final String NEWLINE_GUARD = "(?!.*\\n)";

    private final String form;

    KernelGrammar(String form) {
        this.form = form;
    }

    /** The kernel's verdict. */
    boolean accepts(String text) {
        return switch (this) {
            case SLUG -> IdentifierGrammar.isSlug(text);
            case PACK_ID -> PackIdentifier.isPackId(text);
            case LETTER_FIRST_SLUG -> RoleBearer.isRole(text);
            case ROLE_NOT_GENERIC -> RoleBearer.isRole(text) && !RoleBearer.GENERIC.equals(text);
            case SLOT_ID -> SlotId.tryParse(text).isPresent();
        };
    }

    /**
     * The grammar a site restates, by the rule above.
     *
     * @return empty when the pattern is none of the forms
     */
    static Optional<KernelGrammar> of(String pattern, Integer maxLength, boolean refusesGeneric) {
        String inner = inner(pattern);
        if (inner.equals(SLUG.form)) {
            return Optional.of(Integer.valueOf(PackIdentifier.MAX_LENGTH).equals(maxLength) ? PACK_ID : SLUG);
        }
        if (inner.equals(LETTER_FIRST_SLUG.form)) {
            return Optional.of(refusesGeneric ? ROLE_NOT_GENERIC : LETTER_FIRST_SLUG);
        }
        for (KernelGrammar grammar : new KernelGrammar[] {ROLE_NOT_GENERIC, SLOT_ID}) {
            if (inner.equals(grammar.form)) {
                return Optional.of(grammar);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a pattern is identifier-like: without anchors and the newline guard, it is written only in the slug
     * alphabet and plain regex operators ({@code a-z 0-9 [ ] ( ) ? : | * + -}), holds at least one character class
     * (a pattern of literals only, such as the URN prefix {@code ^directive:}, is a constant, not a grammar), and holds
     * no {@code ::}, which marks a pack-qualified reference, a grammar of its own. Underscores, dots, slashes, escapes, braces and upper
     * case all put a pattern outside: those are kind tokens, versions, paths, URNs, digests and the like.
     */
    static boolean identifierLike(String pattern) {
        String inner = inner(pattern);
        return inner.contains("[") && inner.matches("[a-z0-9\\[\\]()?:|*+-]+") && !inner.contains("::");
    }

    private static String inner(String pattern) {
        String inner = pattern;
        if (inner.startsWith("^")) {
            inner = inner.substring(1);
        }
        if (inner.startsWith(NEWLINE_GUARD)) {
            inner = inner.substring(NEWLINE_GUARD.length());
        }
        return inner.endsWith("$") ? inner.substring(0, inner.length() - 1) : inner;
    }
}
