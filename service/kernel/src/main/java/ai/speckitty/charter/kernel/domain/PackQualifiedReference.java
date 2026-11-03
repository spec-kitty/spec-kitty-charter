package ai.speckitty.charter.kernel.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

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
     * The grammar a pack name follows: a lower-case token that starts with a letter or digit. The pack descriptor
     * schema constrains {@code name} to the same pattern, so every authored pack can be referenced.
     */
    public static final String PACK_NAME_GRAMMAR = "^[a-z0-9][a-z0-9_-]*$";

    private static final Pattern PACK_NAME = Pattern.compile(PACK_NAME_GRAMMAR);

    /**
     * Checks the parts.
     *
     * @param packName the pack a qualified reference names; empty for a bare reference
     * @param urn the URN part
     * @throws IllegalArgumentException when the URN is empty or holds the {@code ::} qualifier, or a named pack
     *     breaks the pack-name grammar
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
        if (packName.isPresent() && !PACK_NAME.matcher(packName.get()).matches()) {
            throw new IllegalArgumentException(
                    "A pack-qualified reference names its pack as " + PACK_NAME.pattern() + ": " + packName.get());
        }
    }

    /**
     * Whether a text is a pack name under the grammar.
     *
     * @param name the candidate pack name
     * @return {@code true} when it follows {@link #PACK_NAME_GRAMMAR}
     */
    public static boolean isPackName(String name) {
        return PACK_NAME.matcher(name).matches();
    }

    /**
     * Parses a reference as an artifact writes it.
     *
     * @param text the reference, for example {@code writing::directive:DIRECTIVE_009}
     * @return the reference, bare or pack-qualified
     * @throws IllegalArgumentException when the qualified form is malformed: no pack name before {@code ::}, an empty
     *     URN after it, more than one {@code ::} qualifier, or a pack name outside the pack-name grammar
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
