package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.infra.storage.rules.ManifestRules;
import ai.speckitty.charter.packs.infra.storage.rules.TreeCensus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The membership of a pack manifest (#110, ADR-0042 amendment of 2026-11-12, ADR-0074 decision 7): which files
 * {@code pack-manifest.yaml} lists. It is derived from the pack a write holds, never carried as read:
 *
 * <ul>
 *   <li>one constituent per artifact the pack holds, with the artifact's kind token, its id and the file it is stored
 *       in, in path order;
 *   <li>one preset entry per preset, at {@code presets/<id>.yaml}, in path order.
 * </ul>
 *
 * <p>The lists are derived by {@link PackManifestDerivation}, in the order of the paths' UTF-16 code units. Every
 * write derives the lists and then stamps the derived
 * fields ({@link PackManifests#recompute}): the whole-pack write, the staged tree of a whole-pack registration, and
 * the commit of a per-artifact {@code PUT} or {@code DELETE}. A pack that held no manifest gets one. A whole-pack
 * registration and the round-trip gate refuse an uploaded manifest whose lists differ from the derived ones
 * ({@link #mismatch}); the order of the entries and their derived hashes are not judged, as the write restamps them.
 */
final class ManifestMembership {

    private ManifestMembership() {}

    /**
     * Derives the manifest of the pack stored in a directory, stamps its derived fields from the files there, and
     * writes it to {@code pack-manifest.yaml}, replacing the manifest the directory held or creating it. The lists
     * come from {@code derivation}, the store's {@link PackManifestDerivation#derivedAt}, which holds the same
     * artifacts as the strict load the registration check runs, so the two derivations never split.
     *
     * @param directory the pack's directory, a staged copy the caller owns
     * @param derivation the derived manifest of a directory, as the store that reads it derives it
     * @param strategy the serialization format the manifest is written in
     * @throws IOException when the manifest cannot be written
     * @throws CharterRefusal when the pack cannot be read, a derived path holds no file, or the manifest path holds
     *     something that is not a regular file (a directory, a link: the write would follow it)
     */
    static void stamp(Path directory, Function<Path, PackManifestDso> derivation, SerializationStrategy strategy)
            throws IOException {
        Path manifestFile = directory.resolve(PackFileSet.MANIFEST);
        if (Files.exists(manifestFile, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(manifestFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, PackFileSet.MANIFEST + " is not a regular file");
        }
        PackManifestDso derived = derivation.apply(directory);
        PackManifests.Recomputed recomputed = PackManifests.recompute(derived, path -> bytesAt(directory, path));
        if (!recomputed.missing().isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    PackFileSet.MANIFEST + " would list " + recomputed.missing().getFirst()
                            + ", which the pack does not hold as a file");
        }
        Files.writeString(manifestFile, strategy.writeManifest(recomputed.stamped()), StandardCharsets.UTF_8);
    }

    /**
     * Each difference between a manifest's lists and the derived ones, as a reason: first the listed entries the pack
     * does not hold, then the held entries the manifest leaves out, each in the order of its own list. A constituent is
     * compared by kind token, id and path, a preset entry by id and path; the order of the entries and every derived
     * field are not compared. An entry the manifest lists more than once is a reason of its own, after the unheld ones.
     *
     * @param listed the manifest as read
     * @param derived the derived manifest of the same pack
     * @return the reasons; empty when the lists hold the same entries
     */
    static List<String> mismatch(PackManifestDso listed, PackManifestDso derived) {
        return ManifestRules.mismatch(entries(listed), entries(derived));
    }

    /**
     * A manifest's lists in the manifest's own words, as the census holds them: each constituent as
     * {@code <kind>:<id> at <path>}, each preset entry as {@code <id> at <path>}, in the manifest's order.
     *
     * @param manifest a manifest
     * @return its entries
     */
    static TreeCensus.Entries entries(PackManifestDso manifest) {
        return new TreeCensus.Entries(constituents(manifest), presets(manifest));
    }

    private static List<String> constituents(PackManifestDso manifest) {
        return manifest.constituents().stream()
                .map(c -> c.kind() + ":" + c.id() + " at " + c.path())
                .toList();
    }

    private static List<String> presets(PackManifestDso manifest) {
        return manifest.presets().stream().map(p -> p.id() + " at " + p.path()).toList();
    }

    private static Optional<byte[]> bytesAt(Path directory, String path) {
        if (Companions.inPack(directory, path) instanceof Companions.Inside at
                && PackFileSet.unlinkedFile(directory, at.packRelative())) {
            return FilesystemPackStore.readBytes(at.file());
        }
        return Optional.empty();
    }
}
