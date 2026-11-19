package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.application.ActivationImpact;
import ai.speckitty.charter.packs.application.PackRemovalStore;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.application.RegistrationImpact;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.application.WriteFacts;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The persistence-edge adapter that binds the application-ring {@link PackWriteStore} port to WP04's
 * {@link PackCommitStore} (atomic aggregate commit) and WP02's {@link RelationshipShard} (the registered-relationship
 * shard). It lives in {@code infra.storage}, so it may compose both; the registration services depend only on the
 * port, keeping the onion dependency rule intact (ADR-0006).
 *
 * <p>It resolves a pack identifier to its live directory through {@link PackLocator}, as {@link DomainRepositoryService}
 * and {@link PackCommitStore} do (a configured name wins, otherwise the location whose {@code pack.yaml} declares the
 * {@code pack_id}), and reads the revision and the shard text from that directory. It never mutates the pack itself;
 * {@link #commit} delegates the stage-and-swap to {@link PackCommitStore}, under the same per-pack in-JVM write lock
 * ({@link PackWriteLocks}) {@link #exclusively} holds across a registration's load, precondition and commit. That lock is
 * taken inside the {@link RegistryWriteLock}, always in that order (R-lock), and {@link #commit} brackets its swap with
 * the {@link PackWriteGeneration}, so a verified read can tell a swap ran.
 *
 * <p>{@link #commit} is the one choke point every per-artifact write goes through, so it is where the write is judged
 * over every closure the pack joins as it will be after the write, under both locks and just before the swap (IC-15,
 * operator ruling DM-01M6VG6GC40AKYYFRR35SV8HVW): no registration service can leave the check out, and a closure that
 * would no longer compose refuses the write with its code before anything is staged. Before that, a write or a removal
 * of a kind the reference closure judges is refused with {@code registration_conflict} when the pack it leaves would not
 * close (#355, {@link #requireReferencesClose}).
 */
@Service
public final class CommitStorePackWriteStore implements PackWriteStore, PackRemovalStore {

    /**
     * The kinds a write of which can change whether the pack closes over its references (#355): an asset, a guard, a
     * lifecycle, a mission type, a template. A write of any other kind leaves the closure as the load found it.
     */
    private static final Set<ArtifactKind> REFERENCE_KINDS = EnumSet.of(
            ArtifactKind.ASSET,
            ArtifactKind.GUARD,
            ArtifactKind.LIFECYCLE,
            ArtifactKind.MISSION_TYPE,
            ArtifactKind.TEMPLATE);

    private final Locations locations;
    private final SerializationStrategy strategy;
    private final PackCommitStore commitStore;
    private final RegistryWriteLock registryLock;
    private final PackWriteGeneration generation;
    private final RegistrationImpact impact;

    /**
     * An adapter over the configured locations and WP04's commit store, judging each write with an impact check over
     * the same locations and no stored activation.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format (used to read a descriptor when resolving by declared id)
     * @param commitStore WP04's aggregate-commit store
     */
    public CommitStorePackWriteStore(Locations locations, SerializationStrategy strategy, PackCommitStore commitStore) {
        this(locations, strategy, commitStore, RegistryWriteLock.shared(), PackWriteGeneration.shared());
    }

    /**
     * An adapter over the configured locations and WP04's commit store, as Spring wires it: every write is judged with the
     * deployment's impact check, which composes the stored activations as well; a context without one gets a check over the
     * same locations and no stored activation.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format (used to read a descriptor when resolving by declared id)
     * @param commitStore WP04's aggregate-commit store
     * @param impact the impact check every write is judged with, when the context has one
     */
    @Autowired
    public CommitStorePackWriteStore(
            Locations locations,
            SerializationStrategy strategy,
            PackCommitStore commitStore,
            ObjectProvider<RegistrationImpact> impact) {
        this(
                locations,
                strategy,
                commitStore,
                RegistryWriteLock.shared(),
                PackWriteGeneration.shared(),
                impact.getIfAvailable(() ->
                        new RegistrationImpact(new StagedPackViewFactory(locations, strategy), ActivationImpact.NONE)));
    }

    /**
     * An adapter over an explicit registry lock and write generation; a test injects a short lock timeout and a
     * generation of its own.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     * @param commitStore WP04's aggregate-commit store
     * @param registryLock the lock every write takes before the pack's own
     * @param generation the write generation a commit's swap is bracketed with
     */
    public CommitStorePackWriteStore(
            Locations locations,
            SerializationStrategy strategy,
            PackCommitStore commitStore,
            RegistryWriteLock registryLock,
            PackWriteGeneration generation) {
        this(
                locations,
                strategy,
                commitStore,
                registryLock,
                generation,
                new RegistrationImpact(new StagedPackViewFactory(locations, strategy), ActivationImpact.NONE));
    }

    /**
     * An adapter over an explicit registry lock, write generation and impact check; a test plants the stored
     * activations the check composes.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     * @param commitStore WP04's aggregate-commit store
     * @param registryLock the lock every write takes before the pack's own
     * @param generation the write generation a commit's swap is bracketed with
     * @param impact the impact check every write is judged with
     */
    public CommitStorePackWriteStore(
            Locations locations,
            SerializationStrategy strategy,
            PackCommitStore commitStore,
            RegistryWriteLock registryLock,
            PackWriteGeneration generation,
            RegistrationImpact impact) {
        this.impact = Objects.requireNonNull(impact, "impact");
        this.locations = Objects.requireNonNull(locations, "locations");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.commitStore = Objects.requireNonNull(commitStore, "commitStore");
        this.registryLock = Objects.requireNonNull(registryLock, "registryLock");
        this.generation = Objects.requireNonNull(generation, "generation");
    }

    /** The digest of the pack's files with the path of each, as the closure's fingerprint digests them (#416). */
    @Override
    public String digestOf(PackIdentifier pack) {
        return reading(pack, directory -> FileSystemPackSources.digestOfPack(directory, pack.value()));
    }

    @Override
    public PackRevision currentRevision(PackIdentifier pack) {
        return reading(pack, directory -> {
            try {
                // Over bytes, as PackCommitStore.revisionOf: a digested file need not be UTF-8 text.
                List<byte[]> contents = new ArrayList<>();
                for (String relative : PackFileSet.digested(directory, pack.value())) {
                    contents.add(Files.readAllBytes(directory.resolve(relative)));
                }
                return PackRevision.computeOverBytes(contents);
            } catch (IOException failure) {
                throw new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE, "cannot read the revision of pack " + pack.value(), failure);
            }
        });
    }

    @Override
    public List<Relationship> registeredRelationships(PackIdentifier pack) {
        return reading(pack, directory -> registeredRelationships(pack, directory));
    }

    private static List<Relationship> registeredRelationships(PackIdentifier pack, Path directory) {
        Path shard = directory.resolve(RelationshipShard.SHARD_PATH);
        if (!Files.isRegularFile(shard)) {
            return List.of(); // a pack nothing has been registered into yet
        }
        String text;
        try {
            text = Files.readString(shard, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot read the relationship shard of pack " + pack.value(), failure);
        }
        RelationshipShard.ShardRead read = RelationshipShard.read(pack.value(), text);
        if (!read.findings().isEmpty()) {
            // A shard is a pack file the fingerprint covers; one that does not bind whole is an unreadable pack, not
            // a fault of the request. Building a write on the bound subset would silently drop the rest.
            Reason first = read.findings().getFirst();
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "the relationship shard of pack " + pack.value() + " does not bind ("
                            + first.location().file() + ": " + first.detail() + "); refusing to rewrite it");
        }
        return read.relationships();
    }

    @Override
    public <T> T exclusively(PackIdentifier pack, Supplier<T> work) {
        // The registry lock first, then the pack's own (R-lock): every pack write goes through here, so the per-kind
        // registrations and the relationship write all take them in that order. A busy registry refuses before the
        // work.
        // The pack is resolved under the registry lock too: with no swap running, a listing cannot miss a directory
        // that a concurrent writer is renaming.
        return exclusivelyAt(pack, location -> work.get());
    }

    /**
     * As {@link #exclusively}, and tells the work the configured location the pack resolved to, found once, so the write
     * can key its write generation by the configured name without another listing.
     */
    private <T> T exclusivelyAt(PackIdentifier pack, Function<PackLocation, T> work) {
        return registryLock.exclusively(() -> {
            PackLocation location = location(pack);
            return underPackLock(PackLocator.directoryOf(location), () -> work.apply(location));
        });
    }

    /** Runs {@code work} under the lock of the resolved entry, so a pack addressed by name and by id shares one lock,
     * the lock the listing takes to repair an interrupted commit (#281). */
    private <T> T underPackLock(Path directory, Supplier<T> work) {
        ReentrantLock lock = PackWriteLocks.of(directory);
        lock.lock();
        try {
            // StorageRootLocations repairs on the listing, behind its domain/overlap/read-only guards; this covers
            // locations that do not list (a fixed set). Deliberately unguarded here: a fixed Locations is a trusted,
            // self-contained set with no outside-link or cross-pack adversary, and this only ever repairs that one
            // pack's own directory. On the listing path, all() has already repaired or refused the pack before
            // resolve(pack) reaches this line, so for StorageRootLocations this is a no-op.
            PackCommitStore.missingLiveDirectory(directory).ifPresent(PackCommitStore::restoreInterruptedSwap);
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PackRevision commit(PackIdentifier pack, @Nullable String ifMatch, Change change) {
        @Nullable String shard = change.relationships().map(RelationshipShard::write).orElse(null);
        @Nullable CharterArtifact artifact = change.artifact().orElse(null);
        return exclusivelyAt(pack, location -> {
            change.artifact()
                    .filter(written -> REFERENCE_KINDS.contains(written.kind()))
                    .ifPresent(written -> requireReferencesClose(change.revalidated()));
            requireClosuresCompose(pack, change);
            return generation.bracket(location.name(), () -> commitStore.commit(pack, ifMatch, artifact, shard));
        });
    }

    /**
     * Whether a removal can leave an artifact naming what it removes: an asset (a guard names it), a guard (a lifecycle
     * names it), or a template that a mission type which stays names. Nothing the closure judges names a lifecycle or a
     * mission type, and a mission type goes with its own templates, so its removal reads no pack here.
     */
    private static boolean mayBreakReferences(List<Urn> removed) {
        Set<String> types = removed.stream()
                .filter(urn -> urn.kind() == NodeKind.MISSION_TYPE)
                .map(Urn::id)
                .collect(Collectors.toSet());
        return removed.stream().anyMatch(urn -> switch (urn.kind()) {
            case ASSET, GUARD -> true;
            case TEMPLATE ->
                !types.contains(urn.id().substring(0, Math.max(0, urn.id().indexOf('/'))));
            default -> false;
        });
    }

    /**
     * Refuses a per-file write whose pack would not close over its references (#355, operator rulings of 2026-11-17):
     * {@code 409 registration_conflict}, with the reason the load would record when it left the artifact out. It runs
     * under the registry lock and the pack's own, before the impact check and before anything is staged. The pack it
     * judges is the aggregate as it will be after the write; a loaded aggregate leaves out what does not close, so
     * only what the write changes can fail. No route writes or removes an asset, guard, lifecycle or template yet (work
     * breakdown item 13 adds them); a route that does inherits this check.
     */
    private static void requireReferencesClose(Pack after) {
        // Before the If-Match comparison in the commit store, as the impact check is: a stale write whose pack would
        // also not close answers 409, not 412.
        ReferenceSubjects.firstRefusal(after).ifPresent(finding -> {
            throw new CharterRefusal(RefusalCode.REGISTRATION_CONFLICT, finding.refusal());
        });
    }

    /**
     * Judges the closures the pack joins as the pack will be after the write: the artifact it adds or replaces held, and
     * the registered relationships it leaves in place of the stored ones. Runs under the registry lock and the pack's
     * own, so what the check reads cannot change before the swap.
     */
    private void requireClosuresCompose(PackIdentifier pack, Change change) {
        List<Relationship> registeredBefore =
                change.relationships().isPresent() ? registeredRelationships(pack) : List.of();
        impact.check(
                pack,
                facts -> {
                    PackCompositionFacts written = change.artifact()
                            .map(artifact -> WriteFacts.withArtifact(facts, artifact.urn()))
                            .orElse(facts);
                    return change.relationships()
                            .map(after -> WriteFacts.withRegisteredEdges(written, registeredBefore, after))
                            .orElse(written);
                },
                Optional.of(change.revalidated()));
    }

    @Override
    public PackIdentifier configuredName(PackIdentifier pack) {
        return new PackIdentifier(location(pack).name());
    }

    @Override
    public List<Relationship> authoredRelationships(PackIdentifier pack) {
        return authored(pack).edges();
    }

    @Override
    public Authored authored(PackIdentifier pack) {
        return reading(pack, directory -> authored(pack, directory));
    }

    @Override
    public List<MissionTypeFacts> missionTypes(PackIdentifier pack) {
        return reading(pack, directory -> {
            Path parent = directory.getParent();
            if (parent == null || directory.getFileName() == null) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a pack directory needs a parent directory");
            }
            PackIdentifier key = new PackIdentifier(directory.getFileName().toString());
            List<MissionTypeFacts> types = new ArrayList<>();
            for (MissionTypeDso type :
                    new FilesystemPackStore(parent, strategy).readAll(key, MissionTypeBinding.INSTANCE)) {
                types.add(PackAssembly.missionTypeFacts(MissionTypeBinding.INSTANCE.fileName(type.id()), type));
            }
            return types;
        });
    }

    private Authored authored(PackIdentifier pack, Path directory) {
        Path parent = directory.getParent();
        if (parent == null || directory.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a pack directory needs a parent directory");
        }
        FilesystemPackStore.GraphRead read = new FilesystemPackStore(parent, strategy)
                .readGraphs(new PackIdentifier(directory.getFileName().toString()));
        if (!read.violations().isEmpty()) {
            // Fail closed, as the edges and the composition facts do: what an unread document declares or joins cannot
            // be judged, and a removal that skipped it would pass what it never saw.
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "a graph document of " + pack.value() + " cannot be read");
        }
        Set<Urn> declared = new LinkedHashSet<>();
        List<Relationship> edges = new ArrayList<>();
        for (GraphDocument document : read.documents()) {
            if (!RelationshipShard.SHARD_PATH.equals(document.file())) {
                declaredNodes(document).forEach(declared::add);
                document.edges()
                        .forEach(edge -> CompositionFactsBuilder.typedEdge(edge).ifPresent(edges::add));
            }
        }
        return new Authored(declared, edges);
    }

    /** The nodes a graph document declares, as URNs; a node that names no URN is left out. */
    private static Stream<Urn> declaredNodes(GraphDocument document) {
        return switch (document) {
            case GraphDocument.Shard shard -> shard.nodes().stream().flatMap(node -> Urn.parse(node.urn()).stream());
            case GraphDocument.Fragment fragment ->
                fragment.nodes().stream()
                        .flatMap(node -> NodeKind.fromPlural(node.kind()).stream()
                                .flatMap(kind -> Urn.parse(kind.token() + ":" + node.id()).stream()));
        };
    }

    @Override
    public PackRevision remove(PackIdentifier pack, Removal removal) {
        return exclusivelyAt(pack, location -> {
            if (mayBreakReferences(removal.artifacts())) {
                requireReferencesClose(new DomainRepositoryService(locations, strategy)
                        .load(pack)
                        .without(removal.artifacts()));
            }
            Path directory = resolve(pack);
            List<String> files = removal.artifacts().isEmpty()
                    ? List.of()
                    : ArtifactFiles.of(directory, removal.artifacts(), strategy);
            // The shard is rewritten only when an edge goes: a removal adds none, so equal sizes mean none went.
            @Nullable String shard = removal.relationships().size()
                            == registeredRelationships(pack).size()
                    ? null
                    : RelationshipShard.write(removal.relationships());
            Map<String, String> rewritten = new LinkedHashMap<>();
            removal.presets()
                    .forEach(preset -> rewritten.put(
                            PresetFiles.pathOf(preset.id()), PresetYaml.write(PresetFiles.toDso(preset))));
            rewritten.putAll(withoutLinks(directory, removal.droppedLinks()));
            return generation.bracket(location.name(), () -> commitStore.remove(pack, files, shard, rewritten));
        });
    }

    @Override
    public void requireRemovable(PackIdentifier pack) {
        requireRemovableEntry(pack, resolve(pack));
    }

    @Override
    public void removeWhole(PackIdentifier pack) {
        exclusivelyAt(pack, location -> {
            Path directory = PackLocator.directoryOf(location);
            requireRemovableEntry(pack, directory);
            return generation.bracket(location.name(), () -> {
                commitStore.removeWhole(directory, location.name());
                return directory;
            });
        });
    }

    /**
     * A pack is removed only as a plain directory directly under the writable root (ADR-0081 P9): a pack in a read-only
     * location is the operator's, and a linked pack is removed by removing its link, so the service never deletes outside
     * its writable root.
     */
    private void requireRemovableEntry(PackIdentifier pack, Path directory) {
        Optional<Path> root = locations.writeRoot();
        Path parent = directory.getParent();
        boolean underRoot = root.isPresent()
                && parent != null
                && parent.toAbsolutePath().normalize().equals(root.get().toAbsolutePath().normalize());
        if (!underRoot) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    "The pack " + pack.value() + " is in a read-only location; only a pack the service stores is removed.");
        }
        if (Files.isSymbolicLink(directory)) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    "The pack " + pack.value() + " is a link; remove the link to remove it.");
        }
    }

    /**
     * Each author-written graph document that holds one of the links a removal drops, written again without them
     * (operator rulings DM-01M7CF9PA1V2DS0P7ZZVANJ1WZ and DM-01M7DTNBMQ6Y88RKJWD3ZFH6G6): a removed mission type goes
     * with the edges that link it to its steps and the scope edges from its own step actions, and nothing else of the
     * document changes but its layout, which is written from scratch.
     */
    private Map<String, String> withoutLinks(Path directory, List<Relationship> dropped) {
        Path parent = directory.getParent();
        if (dropped.isEmpty() || parent == null || directory.getFileName() == null) {
            return Map.of();
        }
        FilesystemPackStore.GraphRead read = new FilesystemPackStore(parent, strategy)
                .readGraphs(new PackIdentifier(directory.getFileName().toString()));
        Map<String, String> out = new LinkedHashMap<>();
        for (GraphDocument document : read.documents()) {
            if (RelationshipShard.SHARD_PATH.equals(document.file())) {
                continue;
            }
            List<GraphDocument.Edge> kept = document.edges().stream()
                    .filter(edge -> CompositionFactsBuilder.typedEdge(edge)
                            .filter(typed -> dropped.stream().anyMatch(link -> sameEdge(link, typed)))
                            .isEmpty())
                    .toList();
            if (kept.size() != document.edges().size()) {
                out.put(document.file(), GraphDocumentYaml.write(withEdges(document, kept)));
            }
        }
        return out;
    }

    /** The same written edge: its relation and both ends as written, qualifiers included. */
    private static boolean sameEdge(Relationship one, Relationship other) {
        return one.type() == other.type()
                && one.from().equals(other.from())
                && one.fromPack().equals(other.fromPack())
                && one.to().equals(other.to())
                && one.toPack().equals(other.toPack());
    }

    private static GraphDocument withEdges(GraphDocument document, List<GraphDocument.Edge> edges) {
        return switch (document) {
            case GraphDocument.Shard shard ->
                new GraphDocument.Shard(
                        shard.file(),
                        shard.schemaVersion(),
                        shard.generatedAt(),
                        shard.generatedBy(),
                        shard.nodes(),
                        edges);
            case GraphDocument.Fragment fragment ->
                new GraphDocument.Fragment(fragment.file(), fragment.nodes(), edges);
        };
    }

    /** The pack's live directory, at the location {@link PackLocator} resolves: what a write under the lock addresses. */
    private Path resolve(PackIdentifier pack) {
        return PackLocator.directoryOf(location(pack));
    }

    /**
     * A lock-free read of the pack at the location {@link PackLocator} resolves, over the directory a reader reads during
     * a replace's swap window too, kept only when no swap moved it ({@link PackLocator#read}, #490).
     */
    private <T> T reading(PackIdentifier pack, Function<Path, T> body) {
        return PackLocator.read(locations, location(pack), body);
    }

    /** The configured location of a pack, as {@link PackLocator} resolves it. */
    private PackLocation location(PackIdentifier pack) {
        return PackLocator.locate(locations, strategy, pack);
    }
}
