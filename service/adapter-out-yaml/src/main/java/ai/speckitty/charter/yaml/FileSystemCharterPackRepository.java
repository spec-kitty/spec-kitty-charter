package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.CharterOffering;
import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.Diagnostic;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.RefusalCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Loads Charter Packs from directories. A pack's graph is its root {@code *.graph.yaml} shards (sorted by
 * file name) followed by {@code drg/fragment.yaml}, the two places core's runtime reads. Every call reads
 * the files again; caching arrives with the service lifecycle work.
 */
public final class FileSystemCharterPackRepository implements CharterPackRepository {

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

    static CharterPack loadPack(PackLocation location) {
        Path root = location.root();
        if (!Files.isDirectory(root)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + location.name() + " is not a directory");
        }
        Optional<PackDescriptor> descriptor = Optional.empty();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Path packYaml = root.resolve("pack.yaml");
        if (Files.isRegularFile(packYaml)) {
            PackDescriptorReader.Result result =
                    PackDescriptorReader.read(read(packYaml, location), location.name(), location.level());
            descriptor = Optional.of(result.descriptor());
            diagnostics.addAll(result.diagnostics());
        } else {
            diagnostics.add(new Diagnostic(
                    Diagnostic.Code.PACK_LEVEL_MISSING,
                    location.level().token() + " pack " + location.name() + " has no pack.yaml; the configured level "
                            + location.level().token() + " is used"));
        }
        DoctrineGraph graph = DoctrineGraph.empty();
        for (Path shard : shards(root, location)) {
            graph = graph.merge(GraphDocumentReader.read(
                    read(shard, location),
                    GraphDocumentReader.Shape.SHARD,
                    location.name() + "/" + root.relativize(shard)));
        }
        Path fragment = root.resolve("drg").resolve("fragment.yaml");
        if (Files.isRegularFile(fragment)) {
            graph = graph.merge(GraphDocumentReader.read(
                    read(fragment, location),
                    GraphDocumentReader.Shape.FRAGMENT,
                    location.name() + "/drg/fragment.yaml"));
        }
        return new CharterPack(location.name(), location.level(), descriptor, graph, diagnostics);
    }

    private static List<Path> shards(Path root, PackLocation location) {
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".graph.yaml") && Files.isRegularFile(p))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + location.name() + " cannot be listed", e);
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
