package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.CharterOffering;
import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.Fingerprint;
import ai.speckitty.charter.domain.PackContents;
import ai.speckitty.charter.domain.PackContents.FileRead;
import ai.speckitty.charter.domain.PackContents.RawGraph;
import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.PackPolicy;
import ai.speckitty.charter.domain.PackRoot;
import ai.speckitty.charter.domain.PackSources;
import ai.speckitty.charter.domain.RefusalCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Loads Charter Packs from directories. A pack's graph is its root {@code *.graph.yaml} shards (sorted by
 * file name) followed by {@code drg/fragment.yaml}, the two places core's runtime reads. Every call reads
 * the files again; caching arrives with the service lifecycle work. The same files, with {@code pack.yaml} first,
 * are what {@link #fingerprint()} digests.
 */
public final class FileSystemCharterPackRepository implements CharterPackRepository, PackSources {

    private final Supplier<List<PackLocation>> locations;

    /**
     * A repository over the packs {@code locations} names.
     *
     * @param locations the configured packs, asked again on every load
     */
    public FileSystemCharterPackRepository(Supplier<List<PackLocation>> locations) {
        this.locations = locations;
    }

    @Override
    public CharterOffering load() {
        return new CharterOffering(locations.get().stream()
                .map(FileSystemCharterPackRepository::loadPack)
                .toList());
    }

    @Override
    public String fingerprint() {
        Fingerprint fingerprint = Fingerprint.sha256();
        for (PackLocation location : locations.get()) {
            Path root = location.root();
            if (!Files.isDirectory(root)) {
                throw new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE, "pack " + location.name() + " is not a directory");
            }
            List<Path> files = new ArrayList<>();
            Path packYaml = root.resolve("pack.yaml");
            if (Files.isRegularFile(packYaml)) {
                files.add(packYaml);
            }
            files.addAll(shards(root, location));
            Path fragment = root.resolve("drg").resolve("fragment.yaml");
            if (Files.isRegularFile(fragment)) {
                files.add(fragment);
            }
            for (Path file : files) {
                // The relative path in '/' form, so the digest is the same on every OS.
                String relative = root.relativize(file)
                        .toString()
                        .replace(root.getFileSystem().getSeparator(), "/");
                fingerprint.add(location.name() + "/" + relative).add(bytes(file, location));
            }
        }
        return fingerprint.value();
    }

    static CharterPack loadPack(PackLocation location) {
        Path root = location.root();
        if (!Files.isDirectory(root)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + location.name() + " is not a directory");
        }
        FileRead<PackDescriptor> descriptor = new FileRead.Absent<>("pack.yaml");
        Path packYaml = root.resolve("pack.yaml");
        if (Files.isRegularFile(packYaml)) {
            descriptor = new FileRead.Read<>(
                    "pack.yaml", PackDescriptorReader.read(read(packYaml, location), location.name()));
        }
        DoctrineGraph graph = DoctrineGraph.empty();
        List<FileRead<RawGraph>> shardFacts = new ArrayList<>();
        for (Path shard : shards(root, location)) {
            graph = graph.merge(GraphDocumentReader.read(
                    read(shard, location),
                    GraphDocumentReader.Shape.SHARD,
                    location.name() + "/" + root.relativize(shard)));
            // The graph above is the real one; the facts only report that the shard is there.
            String file = root.relativize(shard).toString();
            shardFacts.add(new FileRead.Read<>(file, new RawGraph(file, RawGraph.Shape.SHARD, List.of(), List.of())));
        }
        FileRead<RawGraph> fragmentFacts = new FileRead.Absent<>("drg/fragment.yaml");
        Path fragment = root.resolve("drg").resolve("fragment.yaml");
        if (Files.isRegularFile(fragment)) {
            graph = graph.merge(GraphDocumentReader.read(
                    read(fragment, location),
                    GraphDocumentReader.Shape.FRAGMENT,
                    location.name() + "/drg/fragment.yaml"));
            fragmentFacts = new FileRead.Read<>(
                    "drg/fragment.yaml",
                    new RawGraph("drg/fragment.yaml", RawGraph.Shape.FRAGMENT, List.of(), List.of()));
        }
        PackContents contents = new PackContents(
                new PackRoot(location.name(), Optional.of(location.level()), root.toString()),
                descriptor,
                shardFacts,
                fragmentFacts,
                List.of(),
                List.of(),
                List.of(),
                List.of());
        return PackPolicy.load(contents, graph);
    }

    private static List<Path> shards(Path root, PackLocation location) {
        try (Stream<Path> files = Files.list(root)) {
            return graphShards(files, location, Files::isRegularFile);
        } catch (IOException e) {
            throw unlistable(location, e);
        }
    }

    /**
     * The graph shards of a pack directory listing: its {@code *.graph.yaml} regular files, sorted by name.
     *
     * @param files the directory listing; reading it may fail with an {@link UncheckedIOException}
     * @param location the pack the listing belongs to, named in a refusal
     * @param regularFile whether a listed path is a regular file
     * @return the shards, sorted
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when reading the listing fails, as when opening it fails
     */
    static List<Path> graphShards(Stream<Path> files, PackLocation location, Predicate<Path> regularFile) {
        try {
            return files.filter(p -> p.getFileName().toString().endsWith(".graph.yaml") && regularFile.test(p))
                    .sorted()
                    .toList();
        } catch (UncheckedIOException e) {
            // The JDK always sets the cause; the fallback only satisfies the nullness checker.
            throw unlistable(location, Objects.<Exception>requireNonNullElse(e.getCause(), e));
        }
    }

    private static CharterRefusal unlistable(PackLocation location, Exception cause) {
        return new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + location.name() + " cannot be listed", cause);
    }

    private static byte[] bytes(Path file, PackLocation location) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    location.name() + "/" + location.root().relativize(file) + " cannot be read",
                    e);
        }
    }

    private static String read(Path file, PackLocation location) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    location.name() + "/" + location.root().relativize(file) + " cannot be read",
                    e);
        }
    }
}
