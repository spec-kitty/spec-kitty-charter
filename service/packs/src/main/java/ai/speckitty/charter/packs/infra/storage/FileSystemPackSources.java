package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.Fingerprint;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackSources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The packs' source files as one fingerprint: the files a load reads, {@code pack.yaml} first, then the root
 * {@code *.graph.yaml} shards (sorted by file name) and {@code drg/fragment.yaml}. Every call reads the files again.
 */
public final class FileSystemPackSources implements PackSources {

    private final Supplier<List<PackLocation>> locations;

    /**
     * Sources over the packs {@code locations} names.
     *
     * @param locations the configured packs, asked again on every fingerprint
     */
    public FileSystemPackSources(Supplier<List<PackLocation>> locations) {
        this.locations = locations;
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
