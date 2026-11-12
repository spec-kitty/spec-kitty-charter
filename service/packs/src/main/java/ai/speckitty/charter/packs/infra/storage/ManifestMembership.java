package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.Preset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * <p>Path order is the order of the paths' UTF-16 code units. Every write derives the lists and then stamps the derived
 * fields ({@link PackManifests#recompute}): the whole-pack write, the staged tree of a whole-pack registration, and
 * the commit of a per-artifact {@code PUT} or {@code DELETE}. A pack that held no manifest gets one. A whole-pack
 * registration and the round-trip gate refuse an uploaded manifest whose lists differ from the derived ones
 * ({@link #mismatch}); the order of the entries and their derived hashes are not judged, as the write restamps them.
 */
final class ManifestMembership {

    /** The {@code schema_version} every derived manifest carries. */
    static final String SCHEMA_VERSION = "1.0";

    /** The violation code of a membership mismatch. */
    static final String VIOLATION_CODE = "manifest_membership";

    private ManifestMembership() {}

    /**
     * The derived manifest of a pack: its lists, with every derived field empty until {@link PackManifests#recompute}
     * stamps it.
     *
     * @param pack the pack the write holds
     * @param layout where each artifact is stored
     * @return the manifest with the derived lists, in path order
     */
    static PackManifestDso derived(Pack pack, PackLayout layout) {
        List<PackManifestDso.Constituent> constituents = new ArrayList<>();
        for (CharterArtifact artifact : pack.artifacts()) {
            constituents.add(new PackManifestDso.Constituent(
                    artifact.kind().token(), artifact.localId(), layout.pathOf(artifact), ""));
        }
        constituents.sort(Comparator.comparing(PackManifestDso.Constituent::path));
        List<PackManifestDso.PresetEntry> presets = new ArrayList<>();
        for (Preset preset : pack.presets()) {
            presets.add(new PackManifestDso.PresetEntry(preset.id(), PresetFiles.pathOf(preset.id()), ""));
        }
        presets.sort(Comparator.comparing(PackManifestDso.PresetEntry::path));
        return new PackManifestDso(SCHEMA_VERSION, "", constituents, presets);
    }

    /**
     * The derived manifest of the pack stored in a directory, as {@code store} loads it: the one assembly the store's
     * whole-pack load, its registration check and its write share, with its own bindings and companion rules, so the
     * lists name exactly the artifacts that load holds. A file the load does not hold (a read violation, a step
     * enhancement withheld for a repeated stem) is not listed.
     *
     * @param directory the pack's directory
     * @param store the whole-pack store that reads it
     * @return the manifest with the derived lists, in path order
     * @throws CharterRefusal as {@link WholePackStore#load} does, except that an unreadable graph document is tolerated,
     *     as a read tolerates it
     */
    static PackManifestDso derivedAt(Path directory, WholePackStore store) {
        WholePackStore.LoadedPack loaded = store.loadTolerant(directory);
        return derived(loaded.pack(), loaded.layout());
    }

    /**
     * Derives the manifest of the pack stored in a directory, stamps its derived fields from the files there, and
     * writes it to {@code pack-manifest.yaml}, replacing the manifest the directory held or creating it.
     *
     * @param directory the pack's directory, a staged copy the caller owns
     * @param store the whole-pack store that reads it ({@link #derivedAt})
     * @param strategy the serialization format the manifest is written in
     * @throws IOException when the manifest cannot be written
     * @throws CharterRefusal when the pack cannot be read, or a derived path holds no file
     */
    static void stamp(Path directory, WholePackStore store, SerializationStrategy strategy) throws IOException {
        PackManifestDso derived = derivedAt(directory, store);
        PackManifests.Recomputed recomputed = PackManifests.recompute(derived, path -> bytesAt(directory, path));
        if (!recomputed.missing().isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    PackFileSet.MANIFEST + " would list " + recomputed.missing().getFirst()
                            + ", which the pack does not hold as a file");
        }
        Files.writeString(
                directory.resolve(PackFileSet.MANIFEST),
                strategy.writeManifest(recomputed.stamped()),
                StandardCharsets.UTF_8);
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
        List<String> listedConstituents = constituents(listed);
        Set<String> derivedConstituents = new LinkedHashSet<>(constituents(derived));
        List<String> listedPresets = presets(listed);
        Set<String> derivedPresets = new LinkedHashSet<>(presets(derived));
        List<String> out = new ArrayList<>();
        distinct(listedConstituents)
                .filter(entry -> !derivedConstituents.contains(entry))
                .forEach(entry -> out.add("lists constituent " + entry + ", which the pack does not hold"));
        distinct(listedPresets)
                .filter(entry -> !derivedPresets.contains(entry))
                .forEach(entry -> out.add("lists preset " + entry + ", which the pack does not hold"));
        repeated(listedConstituents, "constituent", out);
        repeated(listedPresets, "preset", out);
        derivedConstituents.stream()
                .filter(entry -> !listedConstituents.contains(entry))
                .forEach(entry -> out.add("leaves out constituent " + entry + ", which the pack holds"));
        derivedPresets.stream()
                .filter(entry -> !listedPresets.contains(entry))
                .forEach(entry -> out.add("leaves out preset " + entry + ", which the pack holds"));
        return List.copyOf(out);
    }

    private static Stream<String> distinct(List<String> entries) {
        return new LinkedHashSet<>(entries).stream();
    }

    /** One reason per entry the manifest lists more than once, in the order of its first listing. */
    private static void repeated(List<String> entries, String what, List<String> out) {
        Map<String, Long> counts =
                entries.stream().collect(Collectors.groupingBy(e -> e, LinkedHashMap::new, Collectors.counting()));
        counts.forEach((entry, count) -> {
            if (count > 1) {
                out.add("lists " + what + " " + entry + (count == 2 ? " twice" : " " + count + " times"));
            }
        });
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
        if (Companions.inPack(directory, path) instanceof Companions.Inside at && Files.isRegularFile(at.file())) {
            return FilesystemPackStore.readBytes(at.file());
        }
        return Optional.empty();
    }
}
