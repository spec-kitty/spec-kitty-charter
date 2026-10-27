package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Loads a whole pack from a directory into the {@link Pack} aggregate, and writes an aggregate to a new directory
 * (ADR-0037 decision 5). This is the seam the whole-pack round-trip gate drives.
 *
 * <p>The load reads {@code pack.yaml} and every file of every kind bound in {@link KindBindings} into the aggregate,
 * records where each artifact sat in a {@link PackLayout}, and reads the DRG documents (the root {@code *.graph.yaml}
 * shards and {@code drg/fragment.yaml}, the files the source fingerprint covers) with every field. It lists every
 * other file of the pack as {@linkplain LoadedPack#unmodelled() unmodelled}: nothing models those yet. It is
 * partial-tolerant like {@link DomainRepositoryService#load(PackIdentifier)}: an unreadable artifact file is a
 * violation on the aggregate. An unreadable DRG document refuses the pack as {@code drg_schema_error} (ADR-0036
 * decision 3). The pack's identity is the {@code pack_id} its descriptor declares, whatever the directory is called. The
 * load reads the directory as it finds it; it takes no lock, so a pack swapped by a concurrent commit may be read
 * half before and half after.
 *
 * <p>The write puts {@code pack.yaml}, each artifact at its layout path and each DRG document at its own path into a
 * staging directory beside the target, then renames the staging directory into place, as {@link PackCommitStore}
 * does; a failure removes the staging directory and leaves no target. It writes what the aggregate and the DRG
 * documents hold and nothing else: the unmodelled files are not copied, and the caller has their list. It refuses a
 * target that already exists (replacing a live pack is the commit store's job), a pack with read violations, and a
 * descriptor with undeclared keys, whose values the descriptor does not keep: writing either would drop content
 * without a word.
 */
public final class WholePackStore {

    /**
     * A loaded pack and what is stored beside the aggregate.
     *
     * @param pack the aggregate
     * @param layout each artifact's pack-relative path
     * @param graphs the DRG documents as authored, the root shards by name then the fragment
     * @param unmodelled the pack-relative paths of every other file of the pack, sorted: what the write will not
     *     reproduce
     */
    public record LoadedPack(Pack pack, PackLayout layout, List<GraphDocument> graphs, List<String> unmodelled) {
        /**
         * Checks the fields and copies the lists.
         *
         * @param pack the aggregate
         * @param layout the layout
         * @param graphs the DRG documents
         * @param unmodelled the files nothing models
         */
        public LoadedPack {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(layout, "layout");
            graphs = List.copyOf(graphs);
            unmodelled = List.copyOf(unmodelled);
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
     * @return the aggregate, its layout, the DRG documents and the unmodelled files
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the directory is not a pack directory or has no
     *     readable {@code pack.yaml}; with {@code DRG_SCHEMA_ERROR} when a DRG document cannot be read or breaks its
     *     schema; with {@code VALIDATION_FAILED} when the readable artifacts break an aggregate invariant, such as two
     *     files declaring one id
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
        Pack.Builder builder =
                Pack.builder().id(id).descriptor(read.descriptor().toDescriptor());
        read.violations().forEach(builder::violation);
        Map<Urn, String> places = new HashMap<>();
        for (KindBinding<?, ?> kind : KindBindings.all()) {
            DomainRepositoryService.collect(id, kind, read, builder, places);
        }
        FilesystemPackStore.GraphRead graphs = store.readGraphs(key);
        if (!graphs.violations().isEmpty()) {
            PackElementViolation first = graphs.violations().getFirst();
            throw new CharterRefusal(
                    RefusalCode.DRG_SCHEMA_ERROR, id.value() + "/" + first.file() + ": " + first.reason());
        }
        Pack pack;
        try {
            pack = builder.build();
        } catch (ConstraintBreachException breach) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(breach.getMessage()), breach);
        }
        Set<String> modelled = new HashSet<>(places.values());
        modelled.add(PackFileSet.PACK_YAML);
        graphs.documents().forEach(graph -> modelled.add(graph.file()));
        return new LoadedPack(pack, new PackLayout(places), graphs.documents(), unmodelled(packDir, modelled, key));
    }

    /** Every regular file of the pack that is not in {@code modelled}, pack-relative and sorted. */
    private static List<String> unmodelled(Path packDir, Set<String> modelled, PackIdentifier key) {
        try (Stream<Path> walk = Files.walk(packDir)) {
            return walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> packDir.relativize(path)
                            .toString()
                            .replace(path.getFileSystem().getSeparator(), "/"))
                    .filter(file -> !modelled.contains(file))
                    .sorted()
                    .toList();
        } catch (IOException | RuntimeException e) {
            throw PackFileSet.unlistable(key.value(), e);
        }
    }

    /**
     * Writes a loaded pack to a directory that does not exist yet.
     *
     * @param loaded the pack, its layout and its DRG documents
     * @param target the directory to create
     * @throws CharterRefusal with {@code VALIDATION_FAILED} when the pack has read violations or its descriptor has
     *     undeclared keys; with {@code PACK_UNREADABLE} when the target exists, a path leaves the target, two parts
     *     share a path, or the write fails. In every case no staging directory remains, and no target unless it was
     *     there before.
     */
    public void write(LoadedPack loaded, Path target) {
        Pack pack = loaded.pack();
        if (!pack.violations().isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    "pack " + pack.id().value() + " has " + pack.violations().size()
                            + " unreadable files; writing it would drop them");
        }
        if (!pack.descriptor().undeclaredKeys().isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    "pack.yaml of " + pack.id().value() + " has undeclared keys "
                            + pack.descriptor().undeclaredKeys()
                            + " whose values are not kept; writing it would drop them");
        }
        Path destination = target.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "the write target needs a parent directory");
        }
        @Nullable Path staging = null;
        @Nullable Path reserved = null;
        try {
            Files.createDirectories(parent);
            reserved = reserve(destination, pack);
            staging = Files.createTempDirectory(parent, ".pack-write-");
            Set<String> written = new HashSet<>();
            put(
                    staging,
                    PackFileSet.PACK_YAML,
                    strategy.writeDescriptor(PackDescriptorDso.of(pack.descriptor())),
                    written);
            for (CharterArtifact artifact : pack.artifacts()) {
                put(staging, loaded.layout().pathOf(artifact), text(artifact), written);
            }
            for (GraphDocument graph : loaded.graphs()) {
                put(staging, graph.file(), strategy.writeGraph(graph), written);
            }
            moveOver(staging, reserved);
            staging = null; // moved into place; nothing left to clean up
            reserved = null;
        } catch (CharterRefusal refusal) {
            PackCommitStore.deleteTree(staging);
            release(reserved);
            throw refusal;
        } catch (IOException | RuntimeException failure) {
            PackCommitStore.deleteTree(staging);
            release(reserved);
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "cannot write pack " + pack.id().value(),
                    failure);
        }
    }

    /**
     * Creates the empty target directory, which fails if anything is there already: the check and the claim are one
     * step, so a target that appears after the check cannot be written over.
     */
    private static Path reserve(Path destination, Pack pack) throws IOException {
        try {
            return Files.createDirectory(destination);
        } catch (java.nio.file.FileAlreadyExistsException taken) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "refusing to write pack " + pack.id().value() + " over an existing " + destination.getFileName());
        }
    }

    /** Renames the staged pack over the empty directory this write reserved. */
    private static void moveOver(Path staging, Path reserved) throws IOException {
        try {
            Files.move(staging, reserved, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.delete(reserved);
            Files.move(staging, reserved);
        }
    }

    /** Removes the reserved target if it is still the empty directory this write created. */
    private static void release(@Nullable Path reserved) {
        if (reserved == null) {
            return;
        }
        try {
            Files.deleteIfExists(reserved);
        } catch (IOException ignored) {
            // the original failure is the one worth reporting
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

    /**
     * Writes one file of the staged pack. A path that leaves the pack is refused, and so is one already written, case
     * ignored, so a case-insensitive file system cannot fold two parts into one file.
     */
    private static void put(Path staging, String relative, String content, Set<String> written) throws IOException {
        Path file = staging.resolve(relative).normalize();
        if (!file.startsWith(staging) || file.equals(staging)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "path " + relative + " leaves the pack");
        }
        if (!written.add(staging.relativize(file).toString().toLowerCase(Locale.ROOT))) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "two parts of the pack are written to " + relative);
        }
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
