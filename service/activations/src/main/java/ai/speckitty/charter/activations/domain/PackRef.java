package ai.speckitty.charter.activations.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One pack an activation builds on (the contract's {@code PackPin}, ADR-0033 D6). Equal by both fields.
 *
 * @param packId the pack's configured id, a slug ({@code ^[a-z0-9][a-z0-9_-]*$}, at most 64 characters)
 * @param version the pinned pack version, not blank and at most 64 characters; empty for the version the service holds
 */
public record PackRef(String packId, Optional<String> version) {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final int MAX_VERSION_LENGTH = 64;

    /**
     * Checks the fields.
     *
     * @param packId the pack's configured id
     * @param version the pinned version
     * @throws IllegalArgumentException when the id is not a slug, or the version is blank or too long
     */
    public PackRef {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(version, "version");
        if (!SLUG.matcher(packId).matches()) {
            throw new IllegalArgumentException("A pack id is a slug of at most 64 characters, not '" + packId + "'.");
        }
        if (version.filter(v -> v.isBlank() || v.length() > MAX_VERSION_LENGTH).isPresent()) {
            throw new IllegalArgumentException("A pinned pack version is 1 to 64 characters and not blank.");
        }
    }

    /**
     * A pack at the version the service holds.
     *
     * @param packId the pack's configured id
     * @return the reference
     */
    public static PackRef of(String packId) {
        return new PackRef(packId, Optional.empty());
    }

    /**
     * A pack pinned to a version.
     *
     * @param packId the pack's configured id
     * @param version the version, as the pack declares it
     * @return the reference
     */
    public static PackRef pinned(String packId, String version) {
        return new PackRef(packId, Optional.of(version));
    }

    /**
     * The reference as a canonical value tree, for {@link Jcs}.
     *
     * @return {@code packId}, then {@code version} when pinned
     */
    Map<String, Object> toCanonicalTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("packId", packId);
        version.ifPresent(v -> tree.put("version", v));
        return tree;
    }
}
