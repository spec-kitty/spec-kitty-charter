package ai.speckitty.charter.packs.infra.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** The seam that supplies the configured pack locations, read on every call so a reconfiguration is seen. */
@FunctionalInterface
public interface Locations {
    /**
     * The configured packs.
     *
     * @return every configured pack location
     */
    List<PackLocation> all();

    /**
     * Why a commit may not write the pack whose live directory is {@code realDirectory}, or empty when it may. The
     * default writes every configured pack; the pack domain keeps a pack linked from outside the configuration root
     * read-only (ADR-0038 as amended 2026-10-31).
     *
     * @param realDirectory the pack's live directory, its real path
     * @return the reason, to follow the pack's name in a refusal; empty when it may be written
     */
    default Optional<String> whyReadOnly(Path realDirectory) {
        return Optional.empty();
    }

    /**
     * The one directory the service writes whole packs into ({@code <config-root>/packs}, ADR-0065 D3), when these
     * locations have one. A fixed set of locations has none and cannot take a whole-pack registration.
     *
     * @return the directory, absolute and normalized; empty for a fixed set
     */
    default Optional<Path> writeRoot() {
        return Optional.empty();
    }
}
