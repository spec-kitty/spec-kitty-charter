package ai.speckitty.charter.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The authored {@code pack.yaml} of a Charter Pack.
 *
 * @param packId the pack's {@code pack_id}
 * @param packVersion the pack's {@code pack_version}
 * @param parentPack the pack's {@code parent_pack}; empty for a root pack
 * @param name the pack's {@code name}
 * @param level the pack's {@code pack_level}, or the configured level when the file declares none
 */
public record PackDescriptor(
        String packId, String packVersion, Optional<String> parentPack, String name, PackLevel level) {
    /**
     * Checks that every field is present.
     *
     * @param packId the pack's {@code pack_id}
     * @param packVersion the pack's {@code pack_version}
     * @param parentPack the pack's {@code parent_pack}, possibly empty
     * @param name the pack's {@code name}
     * @param level the pack's level
     */
    public PackDescriptor {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(parentPack, "parentPack");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
    }
}
