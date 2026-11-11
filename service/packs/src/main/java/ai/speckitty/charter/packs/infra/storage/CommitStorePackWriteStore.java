package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ActivationImpact;
import ai.speckitty.charter.packs.application.PackRemovalStore;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.application.RegistrationImpact;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.application.WriteFacts;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackFinding;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * <p>It resolves a pack identifier to its live directory exactly as {@link DomainRepositoryService} and
 * {@link PackCommitStore} do (a configured name wins, otherwise the location whose {@code pack.yaml} declares the
 * {@code pack_id}), and reads the revision and the shard text from that directory. It never mutates the pack itself;
 * {@link #commit} delegates the stage-and-swap to {@link PackCommitStore}, under the same per-pack in-JVM write lock
 * ({@link PackWriteLocks}) {@link #exclusively} holds across a registration's load, precondition and commit. That lock is
 * taken inside the {@link RegistryWriteLock}, always in that order (R-lock), and {@link #commit} brackets its swap with
 * the {@link PackWriteGeneration}, so a verified read can tell a swap ran.
 *
 * <p>{@link #commit} is the one choke point every per-artifact write goes through, so it is where the write is judged
 * over every closure the pack joins as it will be after the write, under both locks and just before the swap (IC-15,
 * operator ruling DM-01M6VG6GC40AKYYFRR35SV8HVW): no registration service can leave the check out, and a closure that
 * would no longer compose refuses the write with its code before anything is staged.
 */
@Service
public final class CommitStorePackWriteStore implements PackWriteStore, PackRemovalStore {

    private final DomainRepositoryService.Locations locations;
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
    public CommitStorePackWriteStore(
            DomainRepositoryService.Locations locations, SerializationStrategy strategy, PackCommitStore commitStore) {
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
            DomainRepositoryService.Locations locations,
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
            DomainRepositoryService.Locations locations,
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
            DomainRepositoryService.Locations locations,
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
        return FileSystemPackSources.digestOfPack(resolve(pack), pack.value());
    }

    @Override
    public PackRevision currentRevision(PackIdentifier pack) {
        Path directory = resolve(pack);
        try {
            // Over bytes, as PackCommitStore.revisionOf: a digested file need not be UTF-8 text.
            List<byte[]> contents = new ArrayList<>();
            for (String relative : PackFileSet.digested(directory)) {
                contents.add(Files.readAllBytes(directory.resolve(relative)));
            }
            return PackRevision.computeOverBytes(contents);
        } catch (IOException failure) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot read the revision of pack " + pack.value(), failure);
        }
    }

    @Override
    public List<Relationship> registeredRelationships(PackIdentifier pack) {
        Path shard = resolve(pack).resolve(RelationshipShard.SHARD_PATH);
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
            PackFinding first = read.findings().getFirst();
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "the relationship shard of pack " + pack.value() + " does not bind (" + first.file() + ": "
                            + first.detail() + "); refusing to rewrite it");
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
            return underPackLock(normalized(location), () -> work.apply(location));
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
            requireClosuresCompose(pack, change);
            return generation.bracket(location.name(), () -> commitStore.commit(pack, ifMatch, artifact, shard));
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
    public Authored authored(PackIdentifier pack) {
        Path directory = resolve(pack);
        Path parent = directory.getParent();
        if (parent == null || directory.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a pack directory needs a parent directory");
        }
        FilesystemPackStore.GraphRead read = new FilesystemPackStore(parent, strategy)
                .readGraphs(new PackIdentifier(directory.getFileName().toString()));
        Set<Urn> declared = new LinkedHashSet<>();
        List<Relationship> edges = new ArrayList<>();
        for (GraphDocument document : read.documents()) {
            if (!RelationshipShard.SHARD_PATH.equals(document.file())) {
                declaredNodes(document).forEach(declared::add);
                document.edges()
                        .forEach(edge -> DomainRepositoryService.typedEdge(edge).ifPresent(edges::add));
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
            Path directory = resolve(pack);
            List<String> files = removal.artifacts().isEmpty()
                    ? List.of()
                    : ArtifactFiles.of(directory, removal.artifacts(), strategy);
            // The shard is rewritten only when an edge goes: a removal adds none, so equal sizes mean none went.
            @Nullable String shard = removal.relationships().size()
                            == registeredRelationships(pack).size()
                    ? null
                    : RelationshipShard.write(removal.relationships());
            Map<String, String> presets = new LinkedHashMap<>();
            removal.presets()
                    .forEach(preset ->
                            presets.put(PresetFiles.pathOf(preset.id()), PresetYaml.write(PresetFiles.toDso(preset))));
            return generation.bracket(location.name(), () -> commitStore.remove(pack, files, shard, presets));
        });
    }

    /** The pack's live directory, resolved as {@link DomainRepositoryService}/{@link PackCommitStore} resolve it. */
    private Path resolve(PackIdentifier pack) {
        return normalized(location(pack));
    }

    /** The configured location of a pack: the one of that name, otherwise the one whose {@code pack.yaml} declares the id. */
    private PackLocation location(PackIdentifier pack) {
        List<PackLocation> configured = locations.all();
        for (PackLocation location : configured) {
            if (location.name().equals(pack.value())) {
                return location;
            }
        }
        for (PackLocation location : configured) {
            if (declaresId(normalized(location), pack)) {
                return location;
            }
        }
        // The identifier matches no configured pack: the caller asked wrongly (CharterRefusalCode pack_not_found).
        throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
    }

    private static Path normalized(PackLocation location) {
        return location.root().toAbsolutePath().normalize();
    }

    private boolean declaresId(Path directory, PackIdentifier pack) {
        Path parent = directory.getParent();
        if (parent == null || directory.getFileName() == null) {
            return false;
        }
        try {
            return new FilesystemPackStore(parent, strategy)
                    .readDescriptor(new PackIdentifier(directory.getFileName().toString()))
                    .packId()
                    .equals(pack.value());
        } catch (CharterRefusal | UncheckedIOException unreadable) {
            return false;
        }
    }
}
