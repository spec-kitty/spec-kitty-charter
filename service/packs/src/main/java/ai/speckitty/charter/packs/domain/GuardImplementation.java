package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a guard is {@code implemented_by} (ADR-0054 decisions 3 and 6): a built-in from the closed list, or an asset,
 * bare in the guard's own pack or qualified {@code <pack>::asset:<id>} across packs (ADR-0053 decision 7).
 */
public sealed interface GuardImplementation permits GuardImplementation.Builtin, GuardImplementation.AssetBacked {

    /** {@code builtin:<id>}, {@code asset:<id>} or {@code <pack>::asset:<id>}. */
    Pattern SPELLING_SHAPE = Pattern.compile("builtin:(" + IdentifierGrammar.SLUG + ")|(?:(" + IdentifierGrammar.SLUG
            + ")::)?asset:([A-Za-z0-9_/.-]++)");

    /** The longest pack name an asset-backed spelling may qualify with (#440). */
    int MAX_PACK_LENGTH = IdentifierGrammar.MAX_SLUG_LENGTH;

    /**
     * Reads the authored spelling.
     *
     * @param text the {@code implemented_by} value
     * @return the implementation
     * @throws IllegalArgumentException when the text is neither spelling, names a built-in not on the list, or qualifies
     *     with a pack name over 64 characters
     */
    static GuardImplementation parse(String text) {
        // The qualifier ends at the first "::" (an asset id holds no colon), so its length is known before matching;
        // the refusal does not echo the text, which is unbounded.
        if (text.indexOf("::") > MAX_PACK_LENGTH) {
            throw new IllegalArgumentException(
                    "implemented_by names a pack longer than " + MAX_PACK_LENGTH + " characters");
        }
        Matcher m = SPELLING_SHAPE.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("implemented_by '" + text + "' is neither builtin:<id> nor asset:<id>");
        }
        if (m.group(1) != null) {
            String id = m.group(1);
            return new Builtin(BuiltinGuard.fromId(id)
                    .orElseThrow(() -> new IllegalArgumentException("builtin:" + id + " is not a built-in guard")));
        }
        String pack = m.group(2);
        return new AssetBacked(Optional.ofNullable(pack), m.group(3));
    }

    /**
     * The authored spelling, the inverse of {@link #parse(String)}.
     *
     * @return the text
     */
    String spelling();

    /**
     * A built-in the client's own code implements; it runs in the client's process and needs no trust check.
     *
     * @param guard the built-in
     */
    record Builtin(BuiltinGuard guard) implements GuardImplementation {
        /**
         * Checks the built-in.
         *
         * @param guard the built-in
         */
        public Builtin {
            Objects.requireNonNull(guard, "guard");
        }

        @Override
        public String spelling() {
            return "builtin:" + guard.id();
        }
    }

    /**
     * An executable asset, run to the guard/1 interface.
     *
     * @param pack the pack that holds the asset, when it is another pack's; empty for the guard's own pack
     * @param assetId the asset's id
     */
    record AssetBacked(Optional<String> pack, String assetId) implements GuardImplementation {
        /**
         * Checks the parts.
         *
         * @param pack the other pack's name, possibly empty
         * @param assetId the asset id
         */
        public AssetBacked {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(assetId, "assetId");
        }

        @Override
        public String spelling() {
            return pack.map(p -> p + "::").orElse("") + "asset:" + assetId;
        }
    }
}
