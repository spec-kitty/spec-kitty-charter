package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.Fingerprint;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackSources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The packs' source files as one fingerprint. The list of files per pack is {@link PackFileSet}'s: {@code pack.yaml},
 * every file of a registered kind with its companion files, and the declared graph inputs (the root
 * {@code *.graph.yaml} shards and {@code drg/fragment.yaml}), and nothing else. Every call reads the files again, each
 * pack inside the identity-checked re-read of {@link PackLocator#read}, so a pack a replace is swapping is digested
 * whole from one directory, its aside copy while the live one is missing (#490).
 */
public final class FileSystemPackSources implements PackSources {

    private final Locations locations;

    /**
     * Sources over the packs {@code locations} names.
     *
     * @param locations the configured packs, asked again on every fingerprint
     */
    public FileSystemPackSources(Locations locations) {
        this.locations = locations;
    }

    @Override
    public String fingerprint() {
        return digestNamed(locations, locations.all(), PackFileSet::digested);
    }

    @Override
    public String fingerprintOf(Collection<String> packNames) {
        List<PackLocation> configured = locations.all();
        for (String name : packNames) {
            if (configured.stream().noneMatch(location -> location.name().equals(name))) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + name + " is not configured");
            }
        }
        return digestNamed(
                locations,
                configured.stream()
                        .filter(location -> packNames.contains(location.name()))
                        .toList(),
                PackFileSet::digested);
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
        return digestNamed(() -> locations, locations, (root, packName) -> filesOf.apply(root));
    }

    /**
     * The fingerprint of the packs, each pack's files named by {@code filesOf} given the pack's directory and its
     * configured name (which a listing refusal names).
     */
    private static String digestNamed(
            Locations configured, List<PackLocation> locations, BiFunction<Path, String, List<String>> filesOf) {
        Fingerprint fingerprint = Fingerprint.sha256();
        for (PackLocation location : locations) {
            // Each pack is read whole from one directory, then added: a read a swap moved runs again, from scratch.
            List<Part> parts =
                    PackLocator.read(configured, location, directory -> partsOf(directory, location.name(), filesOf));
            parts.forEach(part -> fingerprint.add(part.name()).add(part.bytes()));
        }
        return fingerprint.value();
    }

    /**
     * The digest of one pack's files as {@link #fingerprintOf} digests it, path and bytes of each: the digest of
     * {@code fingerprintOf(List.of(packName))} for the pack at {@code root}.
     *
     * @param root the pack's directory
     * @param packName the name the pack is configured under
     * @return {@code sha256:} and 64 lower-case hex digits
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when the pack or one of its files cannot be read
     */
    static String digestOfPack(Path root, String packName) {
        Fingerprint fingerprint = Fingerprint.sha256();
        partsOf(root, packName, PackFileSet::digested)
                .forEach(part -> fingerprint.add(part.name()).add(part.bytes()));
        return fingerprint.value();
    }

    /** One digested file: its name under the pack's configured name, and its bytes. */
    private static final class Part {
        private final String name;
        private final byte[] bytes;

        Part(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }

        String name() {
            return name;
        }

        byte[] bytes() {
            return bytes;
        }
    }

    private static List<Part> partsOf(Path directory, String name, BiFunction<Path, String, List<String>> filesOf) {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + name + " is not a directory");
        }
        List<Part> parts = new ArrayList<>();
        for (String relative : filesOf.apply(root, name)) {
            // The relative path is in '/' form, so the digest is the same on every OS.
            parts.add(new Part(name + "/" + relative, bytes(root.resolve(relative), name, relative)));
        }
        return parts;
    }

    private static byte[] bytes(Path file, String packName, String relative) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, packName + "/" + relative + " cannot be read", e);
        }
    }
}
