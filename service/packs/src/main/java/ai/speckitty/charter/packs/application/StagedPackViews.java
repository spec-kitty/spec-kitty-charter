package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.Objects;
import java.util.Optional;

/**
 * Port: the stored packs as the registration impact check composes them, with the pack being written standing in the
 * place of the stored one of its name, or beside them when it is new. A view is the three ports a compose reads through,
 * so {@code OfferingReadService} and {@code ClosureQueries} run over it unchanged and no compose rule is copied; only
 * the persistence edge implements ports, so the overlay is its.
 */
public interface StagedPackViews {

    /**
     * The packs a compose reads through.
     *
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param artifacts what the compose reads of each
     */
    record View(PackCatalogRepository catalog, PackDescriptorRepository descriptors, PackArtifactRepository artifacts) {

        /**
         * Checks the ports.
         *
         * @param catalog the configured packs
         * @param descriptors their descriptors
         * @param artifacts what the compose reads of each
         */
        public View {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(descriptors, "descriptors");
            Objects.requireNonNull(artifacts, "artifacts");
        }
    }

    /**
     * One check's view of the packs. What a session reads of the stored packs it keeps, so that each pack's files are
     * read once however many closures hold the pack; a session lives for one check, under the registry lock, which
     * every pack write takes, so what it kept cannot go stale.
     */
    interface Session {

        /**
         * The stored packs as they are.
         *
         * @return the view
         */
        View stored();

        /**
         * The same stored packs with a pack written: in the place of the stored one of its configured name, or beside
         * them.
         *
         * @param written the pack as it will be stored
         * @return the view, which shares what this session has read
         */
        View with(RegistrationImpact.Replacement written);

        /**
         * The stored packs' aggregates, for a rule that reads an entity (ADR-0068); empty when the session cannot load
         * them.
         *
         * @return the store, when there is one
         */
        default Optional<ai.speckitty.charter.packs.domain.PackRepository> packs() {
            return Optional.empty();
        }
    }

    /**
     * Opens a session over the stored packs.
     *
     * @return a session with nothing read yet
     */
    Session session();
}
