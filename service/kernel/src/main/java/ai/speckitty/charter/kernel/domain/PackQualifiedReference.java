package ai.speckitty.charter.kernel.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A reference an artifact makes to another artifact of the offering: bare ({@code <urn>}), a reference inside the
 * writer's own pack, or pack-qualified ({@code <pack-name>::<urn>}), which names the pack it relies on. The URN part
 * is opaque: this type never parses, splits or re-spells it, so a URN holding colons and dots survives intact.
 *
 * @param packName the pack a qualified reference names; empty for a bare reference
 * @param urn the URN part, opaque and never empty
 */
public record PackQualifiedReference(Optional<String> packName, String urn) {

    /**
     * The optional {@code <pack-name>::} qualifier as the contracts write it in a reference pattern, and as {@code
     * SubstepCandidate} writes its grammar: a pack id ({@link PackIdentifier}) spelled as {@link
     * IdentifierGrammar#SLUG_AS_WRITTEN}. It is the one written form of the qualifier, and the schema pin tests and the
     * contract parity test read it. A pattern cannot cap one part of a longer string without lookahead, so the written
     * form leaves out the {@value PackIdentifier#MAX_LENGTH}-character cap; {@link #isPackName} enforces it (#582).
     */
    public static final String QUALIFIER = "(" + IdentifierGrammar.SLUG_AS_WRITTEN + "::)?";

    /** The longest pack name a refusal repeats; a longer one is cut, since the text is unbounded. */
    private static final int ECHO_LENGTH = PackIdentifier.MAX_LENGTH + 1;

    /**
     * Checks the parts.
     *
     * @param packName the pack a qualified reference names; empty for a bare reference
     * @param urn the URN part
     * @throws IllegalArgumentException when the URN is empty or holds the {@code ::} qualifier, or a named pack is no
     *     pack id
     */
    public PackQualifiedReference {
        Objects.requireNonNull(packName, "packName");
        Objects.requireNonNull(urn, "urn");
        if (urn.isEmpty()) {
            throw new IllegalArgumentException("A reference needs a non-empty URN");
        }
        if (urn.contains("::")) {
            throw new IllegalArgumentException("The URN part of a reference holds no :: qualifier");
        }
        if (packName.isPresent() && !isPackName(packName.get())) {
            throw new IllegalArgumentException(refusal(packName.get()));
        }
    }

    /**
     * Whether a text is a pack name: the pack-name part of a reference is a pack id, the name a pack is registered
     * under (ADR-0075 decision 2, #582).
     *
     * @param name the candidate pack name
     * @return {@code true} when it is a pack id ({@link PackIdentifier#isPackId})
     */
    public static boolean isPackName(String name) {
        return PackIdentifier.isPackId(name);
    }

    /**
     * The refusal of a pack name that is no pack id, for a caller that refuses one in the same words.
     *
     * @param name the refused pack name
     * @return the refusal: the pack-id grammar, then the name, cut after {@value #ECHO_LENGTH} characters
     */
    public static String refusal(String name) {
        String shown = name.length() > ECHO_LENGTH ? name.substring(0, ECHO_LENGTH) + "..." : name;
        return "A pack-qualified reference names its pack by a pack id. " + PackIdentifier.GRAMMAR_DETAIL
                + " It names '" + shown + "'.";
    }

    /**
     * Parses a reference as an artifact writes it.
     *
     * @param text the reference, for example {@code writing::directive:DIRECTIVE_009}
     * @return the reference, bare or pack-qualified
     * @throws IllegalArgumentException when the qualified form is malformed: no pack name before {@code ::}, an empty
     *     URN after it, more than one {@code ::} qualifier, or a pack name that is no pack id
     */
    public static PackQualifiedReference parse(String text) {
        Objects.requireNonNull(text, "text");
        String[] parts = text.split("::", -1);
        if (parts.length == 1) {
            return new PackQualifiedReference(Optional.empty(), text);
        }
        if (parts.length > 2) {
            throw new IllegalArgumentException("A reference holds at most one :: qualifier: " + text);
        }
        if (parts[0].isEmpty()) {
            throw new IllegalArgumentException("A pack-qualified reference names a pack before ::");
        }
        return new PackQualifiedReference(Optional.of(parts[0]), parts[1]);
    }

    /**
     * Parses the id of a typed reference ({@code {type, id}}), whose id may carry the qualifier, into the reference it
     * writes: the qualifier of the id, and the URN {@code <type>:<bare id>}.
     *
     * @param kindToken the node-kind token of the reference's type, for example {@code tactic}
     * @param id the reference's id, for example {@code writing::tdd}
     * @return the reference as the artifact writes it, for example {@code writing::tactic:tdd}
     * @throws IllegalArgumentException when the id's qualifier is malformed
     */
    public static PackQualifiedReference typed(String kindToken, String id) {
        PackQualifiedReference parsed = parse(id);
        return new PackQualifiedReference(parsed.packName(), kindToken + ":" + parsed.urn());
    }

    /**
     * The URN part as a bare {@link Urn}.
     *
     * @return the URN; empty when the URN part is not a URN of a known node kind
     */
    public Optional<Urn> asUrn() {
        return Urn.parse(urn);
    }

    /**
     * Whether the reference names a pack.
     *
     * @return {@code true} when the reference is pack-qualified
     */
    public boolean qualified() {
        return packName.isPresent();
    }

    @Override
    public String toString() {
        return packName.map(name -> name + "::" + urn).orElse(urn);
    }
}
