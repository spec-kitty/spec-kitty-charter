package ai.speckitty.charter.domain;

import java.util.Objects;
import java.util.Optional;

/** The authored {@code pack.yaml} of a Charter Pack. */
public record PackDescriptor(
        String packId, String packVersion, Optional<String> parentPack, String name, PackLevel level) {
    public PackDescriptor {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(parentPack, "parentPack");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
    }
}
