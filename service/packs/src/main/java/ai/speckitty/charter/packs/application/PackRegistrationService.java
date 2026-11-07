package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
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

    /** The grammar of the name a pack is stored under: also the grammar of a directory under the pack domain. */
    private static final Pattern PACK_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}$");

    /** The upload as the decisive steps judge it. */
    private record Upload(
            String packId,
            StagedPack staged,
            WholePackWriteStore.Contents contents,
            PackRevision revision,
            Preconditions preconditions) {

        PackDescriptor descriptor() {
            return contents.pack().descriptor();
        }

        PackLevel level() {
            return descriptor().declaredLevel().orElse(PackLevel.ORG);
        }
    }

    /** The pack the upload replaces: how it is configured, what its descriptor says and its revision. */
    private record Stored(ConfiguredPack configured, PackDescriptor descriptor, PackRevision revision) {}

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
        requireValidPackId(packId);
        // The slow steps run before any lock: the load (step 2) and the revision of the staged tree (step 4).
        WholePackWriteStore.Contents contents = loader.loadStaged(staged);
        PackRevision revision = store.revisionOf(staged);
        Upload upload = new Upload(packId, staged, contents, revision, preconditions);
        return store.exclusively(packId, () -> decide(upload));
    }

    /** Steps 3 and 5 to 10, under the registry lock and then the pack's own. */
    private RegistrationOutcome decide(Upload upload) {
        store.requireWritableLocation(upload.packId());
        Optional<Stored> stored = checkIdentity(upload);
        Optional<RegistrationOutcome> unchanged = applyVersionRule(upload, stored);
        if (unchanged.isPresent()) {
            return unchanged.get();
        }
        checkPreconditions(upload.preconditions(), stored);
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(inbound, "inbound");
        return commit(upload, stored);
    }

    private static void requireValidPackId(String packId) {
        if (!PACK_ID.matcher(packId).matches()) {
            String detail = "The pack id must match " + PACK_ID.pattern() + ": a lower-case letter or digit first, then"
                    + " lower-case letters, digits and hyphens, 63 characters at most.";
            throw CharterRefusal.withViolations(
                    RefusalCode.VALIDATION_FAILED,
                    detail,
                    List.of(new RefusalViolation("packId", "invalid_pack_id", detail)));
        }
    }

    // ---- step 3: the identity ------------------------------------------------------------------------------------

    private Optional<Stored> checkIdentity(Upload upload) {
        if (upload.level() == PackLevel.BUILT_IN) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    "A built-in pack is read-only; only org and project packs are registered.");
        }
        List<ConfiguredPack> configured = catalog.configured();
        requireNoCaseTwin(upload.packId(), configured);
        Optional<ConfiguredPack> named = configured.stream()
                .filter(candidate -> candidate.id().value().equals(upload.packId()))
                .findFirst();
        if (named.isEmpty()) {
            requireNewIdentity(upload, configured);
            return Optional.empty();
        }
        Stored stored = new Stored(
                named.get(),
                descriptors.get(named.get().id()),
                revisions.currentRevision(named.get().id()));
        requireSameIdentity(upload, stored);
        return Optional.of(stored);
    }

    /** An id that folds to another stored id would be one directory on a file system that folds case. */
    private static void requireNoCaseTwin(String packId, List<ConfiguredPack> configured) {
        String folded = packId.toLowerCase(Locale.ROOT);
        configured.stream()
                .map(candidate -> candidate.id().value())
                .filter(name ->
                        !name.equals(packId) && name.toLowerCase(Locale.ROOT).equals(folded))
                .findFirst()
                .ifPresent(twin -> {
                    throw new CharterRefusal(
                            RefusalCode.REGISTRATION_CONFLICT,
                            "The pack id " + packId + " is the same as the stored pack " + twin
                                    + " once case is folded.");
                });
    }

    private static void requireSameIdentity(Upload upload, Stored stored) {
        if (upload.level() != stored.configured().level()) {
            throw invalid("The pack " + upload.packId() + " is stored at level "
                    + stored.configured().level().token() + "; a replace keeps the level, and the upload declares "
                    + upload.level().token() + ".");
        }
        if (!upload.descriptor().packId().equals(stored.descriptor().packId())) {
            throw invalid("The pack " + upload.packId() + " is stored with pack_id "
                    + stored.descriptor().packId() + "; a replace keeps the pack_id, and the upload declares "
                    + upload.descriptor().packId() + ".");
        }
    }

    /** A new pack may not take the {@code pack_id} or the name of a pack that is stored. */
    private void requireNewIdentity(Upload upload, List<ConfiguredPack> configured) {
        for (ConfiguredPack other : configured) {
            Optional<PackDescriptor> held = descriptorOf(other);
            if (held.isPresent()
                    && held.get().packId().equals(upload.descriptor().packId())) {
                throw conflict("The pack_id " + held.get().packId() + " is held by the stored pack "
                        + other.id().value() + ".");
            }
            if (held.isPresent() && held.get().name().equals(upload.descriptor().name())) {
                throw conflict("The name " + held.get().name() + " is held by the stored pack "
                        + other.id().value() + ".");
            }
        }
    }

    /** A stored pack whose descriptor cannot be read holds no identity this check can compare. */
    private Optional<PackDescriptor> descriptorOf(ConfiguredPack stored) {
        try {
            return descriptors.find(stored.id());
        } catch (CharterRefusal unreadable) {
            return Optional.empty();
        }
    }

    private static CharterRefusal invalid(String detail) {
        return new CharterRefusal(RefusalCode.VALIDATION_FAILED, detail);
    }

    private static CharterRefusal conflict(String detail) {
        return new CharterRefusal(RefusalCode.REGISTRATION_CONFLICT, detail);
    }

    // ---- steps 5 and 6: the version rule and the preconditions ---------------------------------------------------

    /**
     * The same revision is a no-op, whatever the precondition says; the same version with another revision is a
     * conflict; any other version goes on.
     */
    private Optional<RegistrationOutcome> applyVersionRule(Upload upload, Optional<Stored> stored) {
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        if (stored.get().revision().equals(upload.revision())) {
            return Optional.of(outcome(Outcome.UNCHANGED, upload, upload.revision()));
        }
        if (stored.get().descriptor().packVersion().equals(upload.descriptor().packVersion())) {
            throw new CharterRefusal(
                    RefusalCode.PACK_VERSION_CONFLICT,
                    "The pack " + upload.packId() + " is stored at pack_version "
                            + upload.descriptor().packVersion()
                            + " with another revision; send a new pack_version, or the content that is stored.");
        }
        return Optional.empty();
    }

    private static void checkPreconditions(Preconditions preconditions, Optional<Stored> stored) {
        if (preconditions.ifNoneMatchAny() && stored.isPresent()) {
            throw stale("If-None-Match: * asks for a pack that does not exist, and it does; read the pack again.");
        }
        String ifMatch = preconditions.ifMatch();
        if (ifMatch != null
                && (stored.isEmpty()
                        || !PackRevision.matches(ifMatch, stored.get().revision()))) {
            throw stale("The If-Match precondition is stale; read the pack again.");
        }
    }

    private static PreconditionException stale(String detail) {
        return new PreconditionException(PreconditionException.Kind.STALE, detail);
    }

    // ---- step 10: the commit -------------------------------------------------------------------------------------

    private RegistrationOutcome commit(Upload upload, Optional<Stored> stored) {
        if (stored.isPresent()) {
            PackRevision revision = store.replaceWhole(stored.get().configured().id(), upload.staged());
            return outcome(Outcome.REPLACED, upload, revision);
        }
        return outcome(Outcome.CREATED, upload, store.create(upload.packId(), upload.staged()));
    }

    private static RegistrationOutcome outcome(Outcome outcome, Upload upload, PackRevision revision) {
        return new RegistrationOutcome(
                outcome, new PackIdentifier(upload.packId()), upload.descriptor(), upload.level(), revision, List.of());
    }
}
