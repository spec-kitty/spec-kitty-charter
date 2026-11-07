package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The whole-pack registration use case (ADR-0065, IC-04): a registration leaves the service holding exactly the pack
 * that was sent, or changes nothing. The slow steps (the load and the revision of the staged tree) run before any lock;
 * the decisive steps run under the registry write lock and then the pack's lock.
 */
@Service
public final class PackRegistrationService {

    /** What a registration did to the stored pack. */
    public enum Outcome {
        /** The pack did not exist and now does. */
        CREATED,
        /** The pack existed and was replaced whole. */
        REPLACED,
        /** The pack held exactly this content already; nothing was written. */
        UNCHANGED
    }

    /**
     * The conditional headers of a registration, as the controller passes them.
     *
     * @param ifMatch the {@code If-Match} field value, or null when the request carried none
     * @param ifNoneMatchAny whether the request carried {@code If-None-Match: *}
     */
    public record Preconditions(@Nullable String ifMatch, boolean ifNoneMatchAny) {

        /**
         * Checks that the two are not both given.
         *
         * @param ifMatch the {@code If-Match} field value, or null
         * @param ifNoneMatchAny whether {@code If-None-Match: *} was sent
         */
        public Preconditions {
            if (ifMatch != null && ifNoneMatchAny) {
                throw new IllegalArgumentException("If-Match and If-None-Match: * exclude each other");
            }
        }

        /**
         * No precondition.
         *
         * @return the empty preconditions
         */
        public static Preconditions none() {
            return new Preconditions(null, false);
        }

        /**
         * An {@code If-Match} precondition.
         *
         * @param ifMatch the field value
         * @return the preconditions
         */
        public static Preconditions ifMatch(String ifMatch) {
            return new Preconditions(Objects.requireNonNull(ifMatch, "ifMatch"), false);
        }

        /**
         * {@code If-None-Match: *}: create only.
         *
         * @return the preconditions
         */
        public static Preconditions createOnly() {
            return new Preconditions(null, true);
        }
    }

    /**
     * What a registration answers.
     *
     * @param outcome what was done
     * @param pack the pack's name, the identifier it is stored under
     * @param descriptor the stored pack's descriptor
     * @param level the level the pack is stored at
     * @param revision the revision of the stored state, the {@code ETag}
     * @param notices the floor and lock notices of FR-027; empty until the impact check is built
     */
    public record RegistrationOutcome(
            Outcome outcome,
            PackIdentifier pack,
            PackDescriptor descriptor,
            PackLevel level,
            PackRevision revision,
            List<String> notices) {

        /**
         * Checks the fields and copies the list.
         *
         * @param outcome what was done
         * @param pack the pack's name
         * @param descriptor the descriptor
         * @param level the level
         * @param revision the revision
         * @param notices the notices
         */
        public RegistrationOutcome {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(revision, "revision");
            notices = List.copyOf(notices);
        }
    }

    private final WholePackWriteStore.Loader loader;
    private final WholePackWriteStore store;
    private final PackRevisions revisions;
    private final PackCatalogRepository catalog;
    private final PackDescriptorRepository descriptors;
    private final WritePolicyGate gate;
    private final InboundReferences inbound;

    /**
     * A service over its ports.
     *
     * @param loader reads a staged tree
     * @param store locks, swaps and creates
     * @param revisions the revision of a stored pack
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param gate the mutability gate
     * @param inbound the inbound scan
     */
    @Autowired
    public PackRegistrationService(
            WholePackWriteStore.Loader loader,
            WholePackWriteStore store,
            PackRevisions revisions,
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            WritePolicyGate gate,
            InboundReferences inbound) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.store = Objects.requireNonNull(store, "store");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
    }

    /**
     * Registers a pack: the stored pack becomes exactly the staged tree, or nothing changes.
     *
     * @param packId the name the pack is stored under (the path parameter)
     * @param staged the extracted upload
     * @param preconditions the conditional headers
     * @return what was done and the revision
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal a refusal of the flow, with its code
     * @throws ai.speckitty.charter.kernel.transport.PreconditionException {@code STALE} (412) when the precondition
     *     does not hold
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time (503)
     */
    public RegistrationOutcome register(String packId, StagedPack staged, Preconditions preconditions) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(staged, "staged");
        Objects.requireNonNull(preconditions, "preconditions");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(descriptors, "descriptors");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(inbound, "inbound");
        Objects.requireNonNull(revisions, "revisions");
        WholePackWriteStore.Contents contents = loader.loadStaged(staged);
        // skeleton: every step of the flow lands in the next commits; this answers "nothing to do" for any upload
        return new RegistrationOutcome(
                Outcome.UNCHANGED,
                new PackIdentifier(packId),
                contents.pack().descriptor(),
                contents.pack().descriptor().declaredLevel().orElse(PackLevel.ORG),
                store.revisionOf(staged),
                List.of());
    }
}
