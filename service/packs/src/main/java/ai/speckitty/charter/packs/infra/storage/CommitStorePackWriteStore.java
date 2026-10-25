package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
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
 * {@link #exclusively} holds across a registration's load, precondition and commit.
 */
@Service
public final class CommitStorePackWriteStore implements PackWriteStore {

    private final DomainRepositoryService.Locations locations;
    private final SerializationStrategy strategy;
    private final PackCommitStore commitStore;

    /**
     * One write lock per live pack directory (in this JVM), keyed by the resolved directory so a pack addressed by
     * its configured name and by its declared id shares one lock. The set of configured packs is small and fixed, so
     * the map stays bounded. Cross-process writers are not serialised by it.
     */
    private final ConcurrentMap<Path, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * An adapter over the configured locations and WP04's commit store.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format (used to read a descriptor when resolving by declared id)
     * @param commitStore WP04's aggregate-commit store
     */
    @Autowired
    public CommitStorePackWriteStore(
            DomainRepositoryService.Locations locations, SerializationStrategy strategy, PackCommitStore commitStore) {
        this.locations = Objects.requireNonNull(locations, "locations");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.commitStore = Objects.requireNonNull(commitStore, "commitStore");
    }

    @Override
    public PackRevision currentRevision(PackIdentifier pack) {
        Path directory = resolve(pack);
        try {
            List<String> contents = new ArrayList<>();
            for (String relative : PackFileSet.digested(directory)) {
                contents.add(Files.readString(directory.resolve(relative), StandardCharsets.UTF_8));
            }
            return PackRevision.compute(contents);
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
        Path directory = resolve(pack);
        ReentrantLock lock = locks.computeIfAbsent(directory, key -> new ReentrantLock());
        lock.lock();
        try {
            PackCommitStore.restoreInterruptedSwap(directory);
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PackRevision commit(PackIdentifier pack, @Nullable String ifMatch, Change change) {
        @Nullable String shard = change.relationships().map(RelationshipShard::write).orElse(null);
        @Nullable CharterArtifact artifact = change.artifact().orElse(null);
        return exclusively(pack, () -> commitStore.commit(pack, ifMatch, artifact, shard));
    }

    /** The pack's live directory, resolved as {@link DomainRepositoryService}/{@link PackCommitStore} resolve it. */
    private Path resolve(PackIdentifier pack) {
        List<PackLocation> configured = locations.all();
        for (PackLocation location : configured) {
            if (location.name().equals(pack.value())) {
                return normalized(location);
            }
        }
        for (PackLocation location : configured) {
            Path directory = normalized(location);
            if (declaresId(directory, pack)) {
                return directory;
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
