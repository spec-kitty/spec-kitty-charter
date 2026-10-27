package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.GraphDocument;
import ai.speckitty.charter.packs.domain.Pack;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Loads a whole pack from a directory into the {@link Pack} aggregate, and writes an aggregate to a new directory
 * (ADR-0036 decision 5). This is the seam the whole-pack round-trip gate drives.
 *
 * <p>The load reads {@code pack.yaml}, every file of every kind bound in {@link KindBindings} and the DRG documents
 * (the root {@code *.graph.yaml} shards and {@code drg/fragment.yaml}), and records where each artifact sat in a
 * {@link PackLayout}. It is partial-tolerant like {@link DomainRepositoryService#load(PackIdentifier)}: an unreadable
 * file is a violation on the aggregate. The pack's identity is the {@code pack_id} its descriptor declares.
 *
 * <p>The write puts {@code pack.yaml}, each artifact at its layout path and each DRG document at its own path into a
 * staging directory beside the target, then renames the staging directory into place, as {@link PackCommitStore}
 * does; a failure removes the staging directory and leaves no target. It writes what the aggregate models and nothing
 * else. It refuses a target that already exists (replacing a live pack is the commit store's job) and a pack with read
 * violations, whose dropped files would otherwise vanish without a word.
 */
public final class WholePackStore {

    /**
     * A loaded pack and where each of its artifacts sat.
     *
     * @param pack the aggregate
     * @param layout each artifact's pack-relative path
     */
    public record LoadedPack(Pack pack, PackLayout layout) {
        /**
         * Checks the fields.
         *
         * @param pack the aggregate
         * @param layout the layout
         */
        public LoadedPack {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(layout, "layout");
        }
    }

    private final SerializationStrategy strategy;

    /**
     * A store over a format.
     *
     * @param strategy the serialization format
     */
    public WholePackStore(SerializationStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    /**
     * Loads every modelled part of the pack in {@code directory}.
     *
     * @param directory the pack directory
     * @return the aggregate and its layout
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the directory is not a pack directory or has no
     *     readable {@code pack.yaml}
     */
    public LoadedPack load(Path directory) {
        Path packDir = directory.toAbsolutePath().normalize();
        Path parent = packDir.getParent();
        if (parent == null || packDir.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a pack directory needs a parent directory");
        }
        FilesystemPackStore store = new FilesystemPackStore(parent, strategy);
        PackIdentifier key = new PackIdentifier(String.valueOf(packDir.getFileName()));
        FilesystemPackStore.PackRead read = store.read(key);
        PackIdentifier id = new PackIdentifier(read.descriptor().packId());
        Pack.Builder builder = Pack.builder().id(id).descriptor(read.descriptor().toDescriptor());
        read.violations().forEach(builder::violation);
        Map<Urn, String> places = new HashMap<>();
        for (KindBinding<?, ?> kind : KindBindings.all()) {
            DomainRepositoryService.collect(id, kind, read, builder, places);
        }
        FilesystemPackStore.GraphRead graphs = store.readGraphs(key);
        graphs.documents().forEach(builder::graph);
        graphs.violations().forEach(builder::violation);
        return new LoadedPack(builder.build(), new PackLayout(places));
    }

    /**
     * Writes a loaded pack to a directory that does not exist yet.
     *
     * @param loaded the pack and its layout
     * @param target the directory to create
     * @throws CharterRefusal with {@code VALIDATION_FAILED} when the pack has read violations; with
     *     {@code PACK_UNREADABLE} when the target exists, a path leaves the target, two parts share a path, or the
     *     write fails. In every case no target and no staging directory remain.
     */
    public void write(LoadedPack loaded, Path target) {
        Pack pack = loaded.pack();
        if (!pack.violations().isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    "pack " + pack.id().value() + " has " + pack.violations().size()
                            + " unreadable files; writing it would drop them");
        }
        Path destination = target.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "the write target needs a parent directory");
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "refusing to write pack " + pack.id().value() + " over an existing "
                            + destination.getFileName());
        }
        @Nullable Path staging = null;
        try {
            Files.createDirectories(parent);
            staging = Files.createTempDirectory(parent, ".pack-write-");
            Set<String> written = new HashSet<>();
            put(staging, PackFileSet.PACK_YAML, strategy.writeDescriptor(PackDescriptorDso.of(pack.descriptor())),
                    written);
            for (CharterArtifact artifact : pack.artifacts()) {
                put(staging, loaded.layout().pathOf(artifact), text(artifact), written);
            }
            for (GraphDocument graph : pack.graphs()) {
                put(staging, graph.file(), strategy.writeGraph(graph), written);
            }
            PackCommitStore.moveDirectory(staging, destination);
            staging = null; // moved into place; nothing left to clean up
        } catch (CharterRefusal refusal) {
            PackCommitStore.deleteTree(staging);
            throw refusal;
        } catch (IOException | RuntimeException failure) {
            PackCommitStore.deleteTree(staging);
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot write pack " + pack.id().value(), failure);
        }
    }

    private String text(CharterArtifact artifact) {
        KindBinding<?, ?> kind = KindBindings.of(artifact.kind())
                .orElseThrow(() -> new IllegalArgumentException("cannot persist a " + artifact.kind() + " artefact"));
        return text(kind, artifact);
    }

    private <D> String text(KindBinding<D, ?> kind, CharterArtifact artifact) {
        return strategy.write(kind, kind.dsoOf(artifact));
    }

    /** Writes one file of the staged pack; a path that leaves the pack, or one already written, is refused. */
    private static void put(Path staging, String relative, String content, Set<String> written) throws IOException {
        Path file = staging.resolve(relative).normalize();
        if (!file.startsWith(staging) || file.equals(staging)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "path " + relative + " leaves the pack");
        }
        if (!written.add(staging.relativize(file).toString())) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "two parts of the pack are written to " + relative);
        }
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
