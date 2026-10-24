package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.Fingerprint;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackSources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The packs' source files as one fingerprint. The list of files per pack is {@link PackFileSet}'s: {@code pack.yaml},
 * every file the pack load reads (tactic, directive, styleguide and paradigm files) and the declared graph inputs (the
 * root {@code *.graph.yaml} shards and {@code drg/fragment.yaml}), and nothing else. Every call reads the files again.
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
        return digestNamed(locations.get(), PackFileSet::digested);
    }

    /**
     * The fingerprint of the packs, each pack's files named by {@code filesOf}. {@link #fingerprint()} does the same with
     * {@link PackFileSet#digested(Path, String)}; a test calls it with a list that leaves something out to prove the exactness
     * check notices.
     *
     * @param locations the configured packs
     * @param filesOf the pack-relative paths to digest for a (normalized) pack directory
     * @return {@code sha256:} and 64 lower-case hex digits
     */
    static String digest(List<PackLocation> locations, Function<Path, List<String>> filesOf) {
        return digestNamed(locations, (root, packName) -> filesOf.apply(root));
    }

    /**
     * The fingerprint of the packs, each pack's files named by {@code filesOf} given the pack's directory and its
     * configured name (which a listing refusal names).
     */
    private static String digestNamed(List<PackLocation> locations, BiFunction<Path, String, List<String>> filesOf) {
        Fingerprint fingerprint = Fingerprint.sha256();
        for (PackLocation location : locations) {
            Path root = location.root().toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                throw new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE, "pack " + location.name() + " is not a directory");
            }
            for (String relative : filesOf.apply(root, location.name())) {
                // The relative path is in '/' form, so the digest is the same on every OS.
                fingerprint
                        .add(location.name() + "/" + relative)
                        .add(bytes(root.resolve(relative), location.name(), relative));
            }
        }
        return fingerprint.value();
    }

    private static byte[] bytes(Path file, String packName, String relative) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, packName + "/" + relative + " cannot be read", e);
        }
    }
}
