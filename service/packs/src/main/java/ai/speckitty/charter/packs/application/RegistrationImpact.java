package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Stub for the red commit. */
@Component
public final class RegistrationImpact {

    /**
     * The floor and lock changes of a registration (FR-027).
     *
     * @param floorAdded must-have items the floor gained
     * @param floorRemoved must-have items the floor lost
     * @param locksAdded locked artifacts gained
     * @param locksRemoved locked artifacts lost
     */
    public record Notices(
            List<String> floorAdded, List<String> floorRemoved, List<String> locksAdded, List<String> locksRemoved) {

        /** No change. */
        public static final Notices NONE = new Notices(List.of(), List.of(), List.of(), List.of());

        /**
         * Copies the lists.
         *
         * @param floorAdded gained
         * @param floorRemoved lost
         * @param locksAdded gained
         * @param locksRemoved lost
         */
        public Notices {
            floorAdded = List.copyOf(floorAdded);
            floorRemoved = List.copyOf(floorRemoved);
            locksAdded = List.copyOf(locksAdded);
            locksRemoved = List.copyOf(locksRemoved);
        }
    }

    /**
     * The state a pack will have once the write is stored.
     *
     * @param configured how the pack is configured
     * @param descriptor its descriptor
     * @param facts what the compose reads of it
     */
    public record Replacement(ConfiguredPack configured, PackDescriptor descriptor, PackCompositionFacts facts) {

        /**
         * Checks the fields.
         *
         * @param configured how the pack is configured
         * @param descriptor its descriptor
         * @param facts what the compose reads of it
         */
        public Replacement {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(facts, "facts");
        }
    }

    /**
     * The check over the packs and the activations.
     *
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param artifacts what the compose reads of each
     * @param activations the stored activations' root sets
     */
    public RegistrationImpact(
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            PackArtifactRepository artifacts,
            ActivationImpact activations) {}

    /**
     * The check as Spring wires it.
     *
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param artifacts what the compose reads of each
     * @param activations the activations slice's port, when there is one
     */
    @Autowired
    public RegistrationImpact(
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            PackArtifactRepository artifacts,
            ObjectProvider<ActivationImpact> activations) {
        this(catalog, descriptors, artifacts, activations.getIfAvailable(() -> ActivationImpact.NONE));
    }

    /**
     * Judges every closure the write joins.
     *
     * @param replacement the pack as it will be
     * @return the floor and lock changes
     */
    public Notices check(Replacement replacement) {
        return Notices.NONE;
    }

    /**
     * Tells the activations slice a registration was stored.
     *
     * @param packId the name the pack is stored under
     * @param notices what the registration changed
     */
    public void committed(String packId, Notices notices) {}
}
