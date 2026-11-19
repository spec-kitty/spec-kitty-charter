package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Pep440Range;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.GlossaryTerm;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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

    /** Whether a write is made or only judged (ADR-0081 D5). */
    public enum Mode {
        /** The write is judged and, when nothing refuses it, made. */
        COMMIT,
        /**
         * The write is judged by the same steps, under the same locks, and never made: nothing is stored, no history
         * entry is written and nothing is told it was stored.
         */
        DRY_RUN
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
         * Checks the preconditions against the stored revision: a registration that asks for the pack to be absent
         * (create only) or to be at a revision, and finds otherwise, is stale.
         *
         * @param stored the stored pack's revision; empty when no pack is stored under the name
         * @throws PreconditionException {@code STALE} (412) when they do not hold
         */
        public void requireMet(Optional<PackRevision> stored) {
            if (ifNoneMatchAny && stored.isPresent()) {
                throw stale("If-None-Match: * asks for a pack that does not exist, and it does; read the pack again.");
            }
            if (ifMatch != null && (stored.isEmpty() || !PackRevision.matches(ifMatch, stored.get()))) {
                throw stale("The If-Match precondition is stale; read the pack again.");
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
     * @param notices the floor and lock notices of FR-027
     * @param diagnostics the load warnings of the staged pack, kept apart from its violations (OD-7); empty when the
     *     load found nothing to warn about
     * @param hasDocument whether the stored pack holds a document ({@code README.md}), which its {@code doc} link is
     *     present for
     * @param previousVersion the {@code pack_version} of the stored pack a replace moved from (ADR-0081 D4); empty when
     *     the registration created the pack or changed nothing
     * @param dryRun whether the registration was only judged ({@link Mode#DRY_RUN}): the outcome is the one it would have
     *     had, and nothing was written
     */
    public record RegistrationOutcome(
            Outcome outcome,
            PackIdentifier pack,
            PackDescriptor descriptor,
            PackLevel level,
            PackRevision revision,
            RegistrationImpact.Notices notices,
            List<Diagnostic> diagnostics,
            boolean hasDocument,
            Optional<String> previousVersion,
            boolean dryRun) {

        /**
         * Checks the fields and copies the lists.
         *
         * @param outcome what was done
         * @param pack the pack's name
         * @param descriptor the descriptor
         * @param level the level
         * @param revision the revision
         * @param notices the notices
         * @param diagnostics the load warnings
         * @param hasDocument whether the stored pack holds a document
         * @param previousVersion the version a replace moved from
         * @param dryRun whether the registration was only judged
         */
        public RegistrationOutcome {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(notices, "notices");
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(previousVersion, "previousVersion");
        }
    }

    private final PackReading reading;
    private final WholePackWriteStore.Loader loader;
    private final WholePackWriteStore store;
    private final PackRevisions revisions;
    private final StagedPackViews views;
    private final WritePolicyGate gate;
    private final InboundReferences inbound;
    private final RegistrationImpact impact;

    /**
     * A service over its ports.
     *
     * @param reading reads a staged tree once (#554, S6): the one read the registration judges
     * @param loader assembles a staged tree from the read the registration judged
     * @param store locks, swaps and creates
     * @param revisions the revision of a stored pack
     * @param views the stored packs, read through one session per registration: each session lists the configured
     *     locations once, however many packs the identity checks look at
     * @param gate the mutability gate
     * @param inbound the inbound scan
     * @param impact the impact check of step 9
     */
    @Autowired
    public PackRegistrationService(
            PackReading reading,
            WholePackWriteStore.Loader loader,
            WholePackWriteStore store,
            PackRevisions revisions,
            StagedPackViews views,
            WritePolicyGate gate,
            InboundReferences inbound,
            RegistrationImpact impact) {
        this.reading = Objects.requireNonNull(reading, "reading");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.store = Objects.requireNonNull(store, "store");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.views = Objects.requireNonNull(views, "views");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.impact = Objects.requireNonNull(impact, "impact");
    }

    /** The upload as the decisive steps judge it. */
    private record Upload(
            String packId,
            StagedPack staged,
            WholePackWriteStore.Contents contents,
            List<Diagnostic> diagnostics,
            PackRevision revision,
            Preconditions preconditions,
            Mode mode) {

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
        return register(packId, staged, preconditions, () -> {});
    }

    /**
     * Registers a pack as {@link #register(String, StagedPack, Preconditions)} does, calling {@code beforeCommit} once the
     * decisive steps have judged the upload and just before anything is written. The job runs it to learn that the
     * deadline or a shutdown ended the registration first: it throws, and nothing is stored. A call that returned is
     * the point of no return, so the job ends as the commit does.
     *
     * @param packId the name the pack is stored under (the path parameter)
     * @param staged the extracted upload
     * @param preconditions the conditional headers
     * @param beforeCommit called under the locks, after the impact check and before the swap; whatever it throws ends
     *     the registration with nothing written
     * @return what was done and the revision
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal a refusal of the flow, with its code
     * @throws ai.speckitty.charter.kernel.transport.PreconditionException {@code STALE} (412) when the precondition
     *     does not hold
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time (503)
     */
    public RegistrationOutcome register(
            String packId, StagedPack staged, Preconditions preconditions, Runnable beforeCommit) {
        return register(packId, staged, preconditions, beforeCommit, Mode.COMMIT);
    }

    /**
     * Registers a pack as {@link #register(String, StagedPack, Preconditions, Runnable)} does, or, in
     * {@link Mode#DRY_RUN}, judges it by the same steps and stops where the commit would begin (ADR-0081 D5): every
     * refusal is the registration's own, and the outcome is the one the registration would have had, flagged as a dry
     * run. {@code beforeCommit} runs in both modes, so a deadline ends a dry run as it ends a registration.
     *
     * @param packId the name the pack is stored under (the path parameter)
     * @param staged the extracted upload
     * @param preconditions the conditional headers
     * @param beforeCommit called under the locks, after the impact check and before the swap or the end of the dry run
     * @param mode whether the registration is made or only judged
     * @return what was done, or would have been done, and the revision
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal a refusal of the flow, with its code
     * @throws ai.speckitty.charter.kernel.transport.PreconditionException {@code STALE} (412) when the precondition
     *     does not hold
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time (503)
     */
    public RegistrationOutcome register(
            String packId, StagedPack staged, Preconditions preconditions, Runnable beforeCommit, Mode mode) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(beforeCommit, "beforeCommit");
        Objects.requireNonNull(staged, "staged");
        Objects.requireNonNull(preconditions, "preconditions");
        requireValidPackId(packId);
        // The slow steps run before any lock: the one read and its judgement (step 2) and the revision (step 4).
        ReadUpload read = readUpload(reading, loader, packId, staged);
        // The files the read lists as not held leave the staged tree before its revision and the commit (FR-005).
        loader.removeNotHeld(staged, read.read().notHeld());
        // #110: the manifest the service will hold is derived from the tree and stamped before the revision.
        loader.stampManifest(staged, read.read());
        PackRevision revision = store.revisionOf(staged);
        Upload upload = new Upload(packId, staged, read.contents(), read.diagnostics(), revision, preconditions, mode);
        return store.exclusively(packId, () -> decide(upload, beforeCommit));
    }

    /**
     * A staged tree read once and judged (#554, S1, S6, FR-006, step 2 of IC-04): what a registration holds of it.
     *
     * @param read the one read of the tree
     * @param contents what the tree holds, assembled from that read
     * @param diagnostics the read's warnings, as the registration answers them
     */
    public record ReadUpload(PackReadout read, WholePackWriteStore.Contents contents, List<Diagnostic> diagnostics) {

        /**
         * Checks the fields and copies the diagnostics.
         *
         * @param read the one read
         * @param contents what the tree holds
         * @param diagnostics the warnings
         */
        public ReadUpload {
            Objects.requireNonNull(read, "read");
            Objects.requireNonNull(contents, "contents");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /**
     * Step 2 of a registration (#554, S1, S6, S8, FR-006): reads the staged tree once through {@code reading}, under
     * the name the pack is registered as, and refuses it when the readout's rationale fails, with every fault of it in
     * its order ({@link PackRefusals#of}): the read faults, the storage edge's composition and the manifest gate, which
     * the port's implementation joined. A clean read is then assembled by {@code loader} from that read alone; nothing
     * reads the tree again. A fetched source whose descriptor declares another name than its target is refused
     * {@code name_mismatch} after the judgement (F8).
     *
     * @param reading the one reader
     * @param loader assembles the read
     * @param packId the name the pack is registered as
     * @param staged the extracted upload
     * @return the read, what the tree holds and its diagnostics
     * @throws CharterRefusal {@code VALIDATION_FAILED} with a violation per fault, or {@code name_mismatch}
     */
    public static ReadUpload readUpload(
            PackReading reading, WholePackWriteStore.Loader loader, String packId, StagedPack staged) {
        PackReadout read = reading.read(
                new PackRoot(packId, Optional.empty(), staged.directory().toString()));
        if (read.rationale().isFailing()) {
            throw PackRefusals.of(read.rationale());
        }
        WholePackWriteStore.Contents contents = loader.loadStaged(read);
        staged.declaredName()
                .filter(target -> !target.equals(contents.pack().descriptor().name()))
                .ifPresent(target -> {
                    throw SourceRefusal.NAME_MISMATCH.refusal();
                });
        return new ReadUpload(read, contents, PackRefusals.diagnostics(read.rationale()));
    }

    /** Steps 3 and 5 to 10, under the registry lock and then the pack's own. */
    private RegistrationOutcome decide(Upload upload, Runnable beforeCommit) {
        store.requireWritableLocation(upload.packId());
        // One session for the identity step and the inbound scan: every listing of the locations parses every
        // pack.yaml,
        // so a read per pack looked at would make the registration grow with the square of the stored packs (NFR-009).
        // The registry lock is held, so nothing a session has read goes stale before it is dropped.
        StagedPackViews.View view = views.session().stored();
        Optional<Stored> stored = checkIdentity(upload, view);
        Optional<RegistrationOutcome> unchanged = applyVersionRule(upload, stored);
        if (unchanged.isPresent()) {
            return unchanged.get();
        }
        checkPreconditions(upload.preconditions(), stored);
        // The order runs after the preconditions: a client that named what it replaces, and lost a race to another
        // writer, learns that from its precondition (412), not from the version the winner left (ADR-0081 D2).
        stored.ifPresent(replaced -> requireNewer(upload, replaced.descriptor().packVersion()));
        Before before = beforeOf(stored);
        gate.authorizeStaged(upload.contents().pack(), writesOf(upload.contents(), before));
        requireNoInboundReference(upload, stored, before, view);
        RegistrationImpact.Notices notices = impact.check(replacementOf(upload, stored));
        beforeCommit.run();
        if (upload.mode() == Mode.DRY_RUN) {
            return outcome(
                    stored.isPresent() ? Outcome.REPLACED : Outcome.CREATED,
                    upload,
                    upload.revision(),
                    notices,
                    stored.map(replaced -> replaced.descriptor().packVersion()));
        }
        return commit(upload, stored, notices);
    }

    private static void requireValidPackId(String packId) {
        if (!PackIdentifier.isPackId(packId)) {
            String detail = PackIdentifier.GRAMMAR_DETAIL;
            throw CharterRefusal.withViolations(
                    RefusalCode.VALIDATION_FAILED,
                    detail,
                    List.of(new RefusalViolation("packId", "invalid_pack_id", detail)));
        }
    }

    // ---- step 3: the identity ------------------------------------------------------------------------------------

    private Optional<Stored> checkIdentity(Upload upload, StagedPackViews.View view) {
        if (upload.level() == PackLevel.BUILT_IN) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    "A built-in pack is read-only; only org and project packs are registered.");
        }
        List<ConfiguredPack> configured = view.catalog().configured();
        requireNoCaseTwin(upload.packId(), configured);
        Optional<ConfiguredPack> named = configured.stream()
                .filter(candidate -> candidate.id().value().equals(upload.packId()))
                .findFirst();
        if (named.isEmpty()) {
            requireFreePackId(upload, configured, view.descriptors());
            requireFreeName(upload, configured, Optional.empty(), view.descriptors());
            return Optional.empty();
        }
        Stored replaced = new Stored(
                named.get(),
                view.descriptors().get(named.get().id()),
                revisions.currentRevision(named.get().id()));
        requireSameIdentity(upload, replaced);
        requireFreeName(upload, configured, Optional.of(named.get()), view.descriptors());
        return Optional.of(replaced);
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

    /** A new pack may not take the {@code pack_id} of a pack that is stored. */
    private static void requireFreePackId(
            Upload upload, List<ConfiguredPack> configured, PackDescriptorRepository descriptors) {
        for (ConfiguredPack other : configured) {
            Optional<PackDescriptor> held = descriptorOf(descriptors, other);
            if (held.isPresent()
                    && held.get().packId().equals(upload.descriptor().packId())) {
                throw conflict("The pack_id " + held.get().packId() + " is held by the stored pack "
                        + other.id().value() + ".");
            }
        }
    }

    /** A pack, new or replaced, may not take the name of another stored pack. */
    private static void requireFreeName(
            Upload upload,
            List<ConfiguredPack> configured,
            Optional<ConfiguredPack> replaced,
            PackDescriptorRepository descriptors) {
        for (ConfiguredPack other : configured) {
            Optional<PackDescriptor> held = descriptorOf(descriptors, other);
            if (!replaced.filter(other::equals).isPresent()
                    && held.isPresent()
                    && held.get().name().equals(upload.descriptor().name())) {
                throw conflict("The name " + held.get().name() + " is held by the stored pack "
                        + other.id().value() + ".");
            }
        }
    }

    /** A stored pack whose descriptor cannot be read holds no identity this check can compare. */
    private static Optional<PackDescriptor> descriptorOf(PackDescriptorRepository descriptors, ConfiguredPack stored) {
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
     * The same revision is a no-op, whatever the precondition says. The stored version with another revision is a
     * conflict, and so is a version the name was registered at before with another revision, stored or not (IC-16,
     * ADR-0065 D9): a label never names two contents, and that is judged before the preconditions. That a replacement
     * moves the version forward (ADR-0081 D2) is judged after them ({@link #requireNewer}).
     */
    private Optional<RegistrationOutcome> applyVersionRule(Upload upload, Optional<Stored> stored) {
        if (stored.isPresent() && stored.get().revision().equals(upload.revision())) {
            return Optional.of(outcome(Outcome.UNCHANGED, upload, upload.revision()));
        }
        if (stored.isPresent()
                && PackVersionHistory.sameLabel(
                        stored.get().descriptor().packVersion(),
                        upload.descriptor().packVersion())) {
            requireNewer(upload, stored.get().descriptor().packVersion());
        }
        PackVersionHistory.requireNotReused(
                upload.packId(),
                store.versionHistory(upload.packId()),
                upload.descriptor().packVersion(),
                upload.revision());
        return Optional.empty();
    }

    /**
     * Refuses an upload whose {@code pack_version} is not newer than the stored one (ADR-0081 D2). Both versions passed
     * the read's PEP 440 check, so they always order; a local segment does not order, so {@code 1.0+a} to
     * {@code 1.0+b} is not newer.
     */
    private static void requireNewer(Upload upload, String storedVersion) {
        String version = upload.descriptor().packVersion();
        if (Pep440Range.compareVersions(version, storedVersion) > 0) {
            return;
        }
        String detail = version.equals(storedVersion)
                ? "The pack " + upload.packId() + " is stored at pack_version " + storedVersion
                        + " with another revision; send a newer pack_version, or the content that is stored."
                : "The pack " + upload.packId() + " is stored at pack_version " + storedVersion
                        + ", and the upload's pack_version " + version
                        + " is not newer; a replacement moves the version forward, or sends the content that is stored.";
        throw new CharterRefusal(RefusalCode.PACK_VERSION_CONFLICT, detail);
    }

    private static void checkPreconditions(Preconditions preconditions, Optional<Stored> stored) {
        preconditions.requireMet(stored.map(Stored::revision));
    }

    private static PreconditionException stale(String detail) {
        return new PreconditionException(PreconditionException.Kind.STALE, detail);
    }

    // ---- steps 7 and 8: the diff, the write gate and the inbound scan --------------------------------------------

    /**
     * What the stored pack held, to diff the upload against: the artifacts it defined, the digest of each, the edges it
     * wrote and the terms its glossary contexts held. A stored pack that cannot be read whole is diffed by what can
     * still be read of it ({@link WholePackWriteStore.Loader#salvageStored}): the gate and the inbound scan never switch
     * off for a pack that is unreadable, and a pack whose artifacts cannot be told at all is refused. Its terms cannot
     * be told, so none are held: a term it drops is found with its context, when the upload drops the context.
     *
     * @param defined the artifacts held, as bare URNs
     * @param digests the digest of each file-backed artifact
     * @param edges the typed edges written, by their key
     * @param loose the stored graph entries that cannot be typed, by their pair
     * @param terms the terms held, as bare URNs
     */
    private record Before(
            Set<String> defined,
            Map<Urn, String> digests,
            Map<String, Relationship> edges,
            Map<String, Urn> loose,
            Set<String> terms) {

        static final Before NOTHING = new Before(Set.of(), Map.of(), Map.of(), Map.of(), Set.of());

        static Before of(WholePackWriteStore.Contents contents) {
            return new Before(
                    heldBy(contents),
                    contents.digests(),
                    edgesOf(contents),
                    Map.of(),
                    Set.copyOf(contents.facts().terms()));
        }

        static Before of(WholePackWriteStore.Remains remains) {
            return new Before(
                    remains.defined(), remains.digests(), keyed(remains.edges()), remains.looseEdges(), Set.of());
        }
    }

    private Before beforeOf(Optional<Stored> stored) {
        if (stored.isEmpty()) {
            return Before.NOTHING;
        }
        Path directory = store.liveDirectory(stored.get().configured().id());
        try {
            return Before.of(loader.loadStored(directory));
        } catch (CharterRefusal unreadable) {
            return Before.of(loader.salvageStored(directory));
        }
    }

    private static String edgeKey(Relationship edge) {
        return String.join(
                "|",
                edge.fromReference().toString(),
                edge.type().token(),
                edge.toReference().toString());
    }

    private static Set<String> heldBy(WholePackWriteStore.Contents contents) {
        Set<String> held = new TreeSet<>(contents.facts().defined());
        contents.digests().keySet().forEach(urn -> held.add(urn.toString()));
        return held;
    }

    private static Map<String, Relationship> edgesOf(WholePackWriteStore.Contents contents) {
        return keyed(contents.facts().edges());
    }

    /** The writes of the diff: what the upload adds, changes and removes, and each edge it adds or drops. */
    private static List<WritePolicyGate.Write> writesOf(WholePackWriteStore.Contents upload, Before before) {
        Set<WritePolicyGate.Write> writes = new LinkedHashSet<>();
        Set<String> now = heldBy(upload);
        for (String urn : now) {
            if (!before.defined().contains(urn)) {
                write(writes, WritePolicyGate.Operation.REGISTER, urn);
            }
        }
        for (String urn : before.defined()) {
            if (!now.contains(urn)) {
                write(writes, WritePolicyGate.Operation.REMOVE, urn);
            }
        }
        upload.digests().forEach((urn, digest) -> {
            if (before.defined().contains(urn.toString())
                    && !digest.equals(before.digests().get(urn))) {
                writes.add(new WritePolicyGate.Write(WritePolicyGate.Operation.REPLACE, urn));
            }
        });
        edgeWrites(upload, before, writes);
        return List.copyOf(writes);
    }

    /**
     * Each edge the upload adds or drops, on its source, and a tension on each side (ADR-0045 decision 1); a stored
     * entry that cannot be typed is dropped unless the upload has the pair.
     */
    private static void edgeWrites(
            WholePackWriteStore.Contents upload, Before before, Set<WritePolicyGate.Write> writes) {
        Map<String, Relationship> edges = edgesOf(upload);
        changedEdges(edges, before.edges()).forEach(edge -> writes.addAll(writesOn(edge)));
        Set<String> pairs = new TreeSet<>();
        edges.values().forEach(edge -> pairs.add(edge.fromReference() + "|" + edge.toReference()));
        before.loose().forEach((pair, source) -> {
            if (!pairs.contains(pair)) {
                writes.add(new WritePolicyGate.Write(WritePolicyGate.Operation.RELATIONSHIP, source));
            }
        });
    }

    /**
     * The edges one side holds and the other does not, the upload's added edges first. An edge is keyed on its written
     * source, relation and target, so a tension and a structural relation on one pair are two edges: storage is
     * canonical (ADR-0045 ruling G1), and a pair holds at most one of each class.
     *
     * @param upload the upload's edges, by key
     * @param before the stored edges, by key
     * @return the added edges, then the dropped ones
     */
    static List<Relationship> changedEdges(Map<String, Relationship> upload, Map<String, Relationship> before) {
        List<Relationship> changed = new ArrayList<>();
        upload.forEach((key, edge) -> {
            if (!before.containsKey(key)) {
                changed.add(edge);
            }
        });
        before.forEach((key, edge) -> {
            if (!upload.containsKey(key)) {
                changed.add(edge);
            }
        });
        return changed;
    }

    /**
     * The edges by key, the first of a key kept.
     *
     * @param edges the edges in document order
     * @return them by key
     */
    static Map<String, Relationship> keyed(List<Relationship> edges) {
        Map<String, Relationship> keyed = new LinkedHashMap<>();
        edges.forEach(edge -> keyed.putIfAbsent(edgeKey(edge), edge));
        return keyed;
    }

    /**
     * What the gate judges for an added or dropped edge: its source, and for a tension its target too, since a tension
     * names two rules and neither side is privileged (ADR-0045 decision 1).
     *
     * @param edge the edge
     * @return the writes, the source first
     */
    static List<WritePolicyGate.Write> writesOn(Relationship edge) {
        WritePolicyGate.Write source = new WritePolicyGate.Write(WritePolicyGate.Operation.RELATIONSHIP, edge.from());
        return edge.type() == RelationType.TENSION
                ? List.of(source, new WritePolicyGate.Write(WritePolicyGate.Operation.RELATIONSHIP, edge.to()))
                : List.of(source);
    }

    private static void write(Set<WritePolicyGate.Write> writes, WritePolicyGate.Operation operation, String urn) {
        Urn.parse(urn).ifPresent(parsed -> writes.add(new WritePolicyGate.Write(operation, parsed)));
    }

    /** Before the compose: a removed artifact that another pack still refers to is a 409, not a composition 422. */
    private void requireNoInboundReference(
            Upload upload, Optional<Stored> stored, Before before, StagedPackViews.View view) {
        if (stored.isEmpty()) {
            return;
        }
        Set<String> now = heldBy(upload.contents());
        Set<Urn> removed = new LinkedHashSet<>();
        before.defined().stream()
                .filter(urn -> !now.contains(urn))
                .forEach(urn -> Urn.parse(urn).ifPresent(removed::add));
        // A term the upload drops from a context it keeps: a context it drops takes its terms with it already.
        Set<String> termsNow = Set.copyOf(upload.contents().facts().terms());
        before.terms().stream()
                .filter(term -> !termsNow.contains(term))
                .flatMap(term -> Urn.parse(term).stream())
                .filter(term -> GlossaryTerm.contextOf(term)
                        .filter(context -> now.contains(context.toString()))
                        .isPresent())
                .forEach(removed::add);
        List<InboundReferences.Referrer> referrers = inbound.scan(
                view,
                new InboundReferences.StagedTree(
                        Optional.of(stored.get().configured().id()),
                        Optional.of(stored.get().descriptor().name()),
                        upload.descriptor(),
                        upload.contents().facts()),
                removed);
        if (!referrers.isEmpty()) {
            throw referenced(upload, referrers);
        }
    }

    /** The refusal names every referrer by its pack and artifact (see {@link InboundReferences#refusal}). */
    private static CharterRefusal referenced(Upload upload, List<InboundReferences.Referrer> referrers) {
        return InboundReferences.refusal(
                "The pack " + upload.packId() + " cannot drop " + referrers.size()
                        + " artifact(s) that other packs still refer to; the first is "
                        + referrers.getFirst().removed()
                        + ".",
                "registration",
                referrers);
    }

    // ---- step 9: the impact check ---------------------------------------------------------------------------------

    /** The pack as it will be once stored: the upload's descriptor and facts, under the name the pack is stored by. */
    private static RegistrationImpact.Replacement replacementOf(Upload upload, Optional<Stored> stored) {
        ConfiguredPack configured = stored.map(Stored::configured)
                .orElseGet(() -> new ConfiguredPack(new PackIdentifier(upload.packId()), upload.level()));
        return new RegistrationImpact.Replacement(
                configured,
                upload.descriptor(),
                upload.contents().facts(),
                Optional.of(upload.contents().pack()));
    }

    // ---- step 10: the commit -------------------------------------------------------------------------------------

    private RegistrationOutcome commit(Upload upload, Optional<Stored> stored, RegistrationImpact.Notices notices) {
        PackRevision revision = stored.isPresent()
                ? store.replaceWhole(stored.get().configured().id(), upload.staged())
                : store.create(upload.packId(), upload.staged());
        impact.committed(upload.packId(), notices);
        return outcome(
                stored.isPresent() ? Outcome.REPLACED : Outcome.CREATED,
                upload,
                revision,
                notices,
                stored.map(replaced -> replaced.descriptor().packVersion()));
    }

    private static RegistrationOutcome outcome(Outcome outcome, Upload upload, PackRevision revision) {
        return outcome(outcome, upload, revision, RegistrationImpact.Notices.NONE, Optional.empty());
    }

    private static RegistrationOutcome outcome(
            Outcome outcome,
            Upload upload,
            PackRevision revision,
            RegistrationImpact.Notices notices,
            Optional<String> previousVersion) {
        return new RegistrationOutcome(
                outcome,
                new PackIdentifier(upload.packId()),
                upload.descriptor(),
                upload.level(),
                revision,
                notices,
                upload.diagnostics(),
                upload.contents().pack().document().isPresent(),
                previousVersion,
                upload.mode() == Mode.DRY_RUN);
    }
}
