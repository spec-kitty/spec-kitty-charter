package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One pack an activation builds on (the contract's {@code PackPin}, ADR-0033 D6). Equal by both fields.
 *
 * @param packId the id the pack is registered under: a {@link PackIdentifier}, which admits only a pack id (a kebab
 *     slug of at most 63 characters, ADR-0075 decision 2), so a pin can only name a pack that can be registered (#583,
 *     D7)
 * @param version the pinned pack version, not blank and at most 64 characters; empty for the version the service holds
 */
public record PackRef(PackIdentifier packId, Optional<String> version) {

    private static final int MAX_VERSION_LENGTH = 64;

    /**
     * Checks the fields.
     *
     * @param packId the pack's registered id
     * @param version the pinned version
     * @throws IllegalArgumentException when the version is blank or too long
     */
    public PackRef {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(version, "version");
        if (version.filter(v -> v.isBlank() || v.length() > MAX_VERSION_LENGTH).isPresent()) {
            throw new IllegalArgumentException("A pinned pack version is 1 to 64 characters and not blank.");
        }
    }

    /**
     * A pack at the version the service holds.
     *
     * @param packId the pack's registered id
     * @return the reference
     */
    public static PackRef of(PackIdentifier packId) {
        return new PackRef(packId, Optional.empty());
    }

    /**
     * A pack pinned to a version.
     *
     * @param packId the pack's registered id
     * @param version the version, as the pack declares it
     * @return the reference
     */
    public static PackRef pinned(PackIdentifier packId, String version) {
        return new PackRef(packId, Optional.of(version));
    }

    /**
     * The reference as a canonical value tree, for {@link Jcs}.
     *
     * @return {@code packId}, then {@code version} when pinned
     */
    Map<String, Object> toCanonicalTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("packId", packId.value());
        version.ifPresent(v -> tree.put("version", v));
        return tree;
    }
}
