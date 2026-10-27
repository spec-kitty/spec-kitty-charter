package ai.speckitty.charter.packs.infra.storage;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The one directory the service keeps its files under, with a dedicated subdirectory per domain, so the same layout
 * serves a single user's machine and a shared, hosted store:
 *
 * <pre>
 * &lt;root&gt;/
 *   packs/&lt;pack&gt;/            every registered pack, of any level (built-in, org, project)
 *   activations/&lt;id&gt;/        the activation documents, keyed by their opaque activationId (ADR-0020)
 * </pre>
 *
 * <p>Nothing here creates a directory: a root or a domain directory that does not exist yet holds nothing. Packs are
 * provisioned by copying them into {@code packs/} until registration of new packs is served.
 *
 * @param root the configuration root, absolute and normalized
 */
public record ConfigRoot(Path root) {

    /** The directory of the pack domain. */
    public static final String PACKS = "packs";

    /** The directory of the activation domain. */
    public static final String ACTIVATIONS = "activations";

    /**
     * Normalizes the root, so a relative one is fixed once, against the working directory at startup.
     *
     * @param root the configuration root
     */
    public ConfigRoot {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    /**
     * The pack domain's directory: one subdirectory per pack.
     *
     * @return {@code <root>/packs}
     */
    public Path packs() {
        return root.resolve(PACKS);
    }

    /**
     * The activation domain's directory, reserved for the activation documents (no activation API is served yet).
     *
     * @return {@code <root>/activations}
     */
    public Path activations() {
        return root.resolve(ACTIVATIONS);
    }
}
