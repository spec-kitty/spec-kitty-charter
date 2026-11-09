package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
     */
    public record RegistrationOutcome(
            Outcome outcome,
            PackIdentifier pack,
            PackDescriptor descriptor,
            PackLevel level,
            PackRevision revision,
            RegistrationImpact.Notices notices,
            List<Diagnostic> diagnostics) {

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
         */
        public RegistrationOutcome {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(notices, "notices");
            diagnostics = List.copyOf(diagnostics);
        }

        /**
         * An outcome with no diagnostics.
         *
         * @param outcome what was done
         * @param pack the pack's name
         * @param descriptor the descriptor
         * @param level the level
         * @param revision the revision
         * @param notices the notices
         */
        public RegistrationOutcome(
                Outcome outcome,
                PackIdentifier pack,
                PackDescriptor descriptor,
                PackLevel level,
                PackRevision revision,
                RegistrationImpact.Notices notices) {
            this(outcome, pack, descriptor, level, revision, notices, List.of());
        }
    }

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
     * @param loader reads a staged tree
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
            WholePackWriteStore.Loader loader,
            WholePackWriteStore store,
            PackRevisions revisions,
            StagedPackViews views,
            WritePolicyGate gate,
            InboundReferences inbound,
            RegistrationImpact impact) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.store = Objects.requireNonNull(store, "store");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.views = Objects.requireNonNull(views, "views");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.impact = Objects.requireNonNull(impact, "impact");
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
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(beforeCommit, "beforeCommit");
        Objects.requireNonNull(staged, "staged");
        Objects.requireNonNull(preconditions, "preconditions");
        requireValidPackId(packId);
        // The slow steps run before any lock: the load (step 2) and the revision of the staged tree (step 4).
        WholePackWriteStore.Contents contents = loader.loadStaged(staged);
        PackRevision revision = store.revisionOf(staged);
        Upload upload = new Upload(packId, staged, contents, revision, preconditions);
        return store.exclusively(packId, () -> decide(upload, beforeCommit));
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
        Before before = beforeOf(stored);
        gate.authorizeStaged(upload.contents().pack(), writesOf(upload.contents(), before));
        requireNoInboundReference(upload, stored, before, view);
        RegistrationImpact.Notices notices = impact.check(replacementOf(upload, stored));
        beforeCommit.run();
        return commit(upload, stored, notices);
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
     * The same revision is a no-op, whatever the precondition says; the same version with another revision is a
     * conflict, and so is a version the name was registered at before with another revision, stored or not (IC-16);
     * any other version goes on.
     */
    private Optional<RegistrationOutcome> applyVersionRule(Upload upload, Optional<Stored> stored) {
        if (stored.isPresent() && stored.get().revision().equals(upload.revision())) {
            return Optional.of(outcome(Outcome.UNCHANGED, upload, upload.revision()));
        }
        if (stored.isPresent()
                && stored.get()
                        .descriptor()
                        .packVersion()
                        .equals(upload.descriptor().packVersion())) {
            throw new CharterRefusal(
                    RefusalCode.PACK_VERSION_CONFLICT,
                    "The pack " + upload.packId() + " is stored at pack_version "
                            + upload.descriptor().packVersion()
                            + " with another revision; send a new pack_version, or the content that is stored.");
        }
        PackVersionHistory.requireNotReused(
                upload.packId(),
                store.versionHistory(upload.packId()),
                upload.descriptor().packVersion(),
                upload.revision());
        return Optional.empty();
    }

    private static void checkPreconditions(Preconditions preconditions, Optional<Stored> stored) {
        preconditions.requireMet(stored.map(Stored::revision));
    }

    private static PreconditionException stale(String detail) {
        return new PreconditionException(PreconditionException.Kind.STALE, detail);
    }

    // ---- steps 7 and 8: the diff, the write gate and the inbound scan --------------------------------------------

    /**
     * What the stored pack held, to diff the upload against: the artifacts it defined, the digest of each and the edges
     * it wrote. A stored pack that cannot be read whole is diffed by what can still be read of it
     * ({@link WholePackWriteStore.Loader#salvageStored}): the gate and the inbound scan never switch off for a pack
     * that is unreadable, and a pack whose artifacts cannot be told at all is refused.
     *
     * @param defined the artifacts held, as bare URNs
     * @param digests the digest of each file-backed artifact
     * @param edges the typed edges written, by their key
     */
    private record Before(
            Set<String> defined, Map<Urn, String> digests, Map<String, Relationship> edges, Map<String, Urn> loose) {

        static final Before NOTHING = new Before(Set.of(), Map.of(), Map.of(), Map.of());

        static Before of(WholePackWriteStore.Contents contents) {
            return new Before(heldBy(contents), contents.digests(), edgesOf(contents), Map.of());
        }

        static Before of(WholePackWriteStore.Remains remains) {
            Map<String, Relationship> edges = new LinkedHashMap<>();
            remains.edges().forEach(edge -> edges.putIfAbsent(edgeKey(edge), edge));
            return new Before(remains.defined(), remains.digests(), edges, remains.looseEdges());
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
        Map<String, Relationship> edges = new LinkedHashMap<>();
        for (Relationship edge : contents.facts().edges()) {
            edges.putIfAbsent(edgeKey(edge), edge);
        }
        return edges;
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

    /** Each edge the upload adds or drops, on its source; a stored entry that cannot be typed is dropped unless the upload has the pair. */
    private static void edgeWrites(
            WholePackWriteStore.Contents upload, Before before, Set<WritePolicyGate.Write> writes) {
        Map<String, Relationship> edges = edgesOf(upload);
        edges.forEach((key, edge) -> {
            if (!before.edges().containsKey(key)) {
                writes.add(new WritePolicyGate.Write(WritePolicyGate.Operation.RELATIONSHIP, edge.from()));
            }
        });
        before.edges().forEach((key, edge) -> {
            if (!edges.containsKey(key)) {
                writes.add(new WritePolicyGate.Write(WritePolicyGate.Operation.RELATIONSHIP, edge.from()));
            }
        });
        Set<String> pairs = new TreeSet<>();
        edges.values().forEach(edge -> pairs.add(edge.fromReference() + "|" + edge.toReference()));
        before.loose().forEach((pair, source) -> {
            if (!pairs.contains(pair)) {
                writes.add(new WritePolicyGate.Write(WritePolicyGate.Operation.RELATIONSHIP, source));
            }
        });
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
                configured, upload.descriptor(), upload.contents().facts());
    }

    // ---- step 10: the commit -------------------------------------------------------------------------------------

    private RegistrationOutcome commit(Upload upload, Optional<Stored> stored, RegistrationImpact.Notices notices) {
        PackRevision revision = stored.isPresent()
                ? store.replaceWhole(stored.get().configured().id(), upload.staged())
                : store.create(upload.packId(), upload.staged());
        impact.committed(upload.packId(), notices);
        return outcome(stored.isPresent() ? Outcome.REPLACED : Outcome.CREATED, upload, revision, notices);
    }

    private static RegistrationOutcome outcome(Outcome outcome, Upload upload, PackRevision revision) {
        return outcome(outcome, upload, revision, RegistrationImpact.Notices.NONE);
    }

    private static RegistrationOutcome outcome(
            Outcome outcome, Upload upload, PackRevision revision, RegistrationImpact.Notices notices) {
        return new RegistrationOutcome(
                outcome,
                new PackIdentifier(upload.packId()),
                upload.descriptor(),
                upload.level(),
                revision,
                notices,
                upload.contents().warnings().stream()
                        .map(PackFinding::toDiagnostic)
                        .toList());
    }
}
