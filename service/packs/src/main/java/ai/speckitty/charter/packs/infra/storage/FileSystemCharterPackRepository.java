package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.Fingerprint;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.CharterOffering;
import ai.speckitty.charter.packs.domain.CharterPack;
import ai.speckitty.charter.packs.domain.CharterPackRepository;
import ai.speckitty.charter.packs.domain.PackInspector;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackSources;
import java.io.IOException;
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
 * the files again; caching arrives with the service lifecycle work. The same files, with {@code pack.yaml} first,
 * are what {@link #fingerprint()} digests.
 */
public final class FileSystemCharterPackRepository implements CharterPackRepository, PackSources {

    private static final FileSystemPackInspector INSPECTOR = new FileSystemPackInspector();

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

    /**
     * Loads one pack: the inspector reads its files as facts and {@code PackPolicy} judges them and builds the pack.
     * No code or text is chosen here.
     */
    static CharterPack loadPack(PackLocation location) {
        return PackPolicy.load(INSPECTOR.inspect(
                new PackRoot(
                        location.name(),
                        Optional.of(location.level()),
                        location.root().toString()),
                PackInspector.Scope.LOAD_PATH));
    }

    private static List<Path> shards(Path root, PackLocation location) {
        try (Stream<Path> files = Files.list(root)) {
            return FileSystemPackInspector.graphShards(files, location.name(), Files::isRegularFile);
        } catch (IOException e) {
            throw FileSystemPackInspector.unlistable(location.name(), e);
        }
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
}
