package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.Preset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
     * The derived manifest of the pack stored in a directory, read the way a registration assembles it.
     *
     * @param directory the pack's directory
     * @param strategy the serialization format
     * @return the manifest with the derived lists, in path order
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the directory has no parent or its descriptor cannot be
     *     read
     */
    static PackManifestDso derivedAt(Path directory, SerializationStrategy strategy) {
        Path packDir = directory.toAbsolutePath().normalize();
        Path parent = packDir.getParent();
        if (parent == null || packDir.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a pack directory needs a parent directory");
        }
        FilesystemPackStore.PackRead read = new FilesystemPackStore(parent, strategy, Companions.Rules.bundled())
                .read(new PackIdentifier(String.valueOf(packDir.getFileName())), KindBindings.all());
        Map<Urn, String> places = new HashMap<>();
        Pack pack = DomainRepositoryService.assembly(
                        new PackIdentifier(read.descriptor().packId()), read, places)
                .build();
        return derived(pack, new PackLayout(places));
    }

    /**
     * Derives the manifest of the pack stored in a directory, stamps its derived fields from the files there, and
     * writes it to {@code pack-manifest.yaml}, replacing the manifest the directory held or creating it.
     *
     * @param directory the pack's directory, a staged copy the caller owns
     * @param strategy the serialization format
     * @throws IOException when the manifest cannot be written
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the pack cannot be read, or a derived path holds no file
     */
    static void stamp(Path directory, SerializationStrategy strategy) throws IOException {
        PackManifestDso derived = derivedAt(directory, strategy);
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
     * field are not compared.
     *
     * @param listed the manifest as read
     * @param derived the derived manifest of the same pack
     * @return the reasons; empty when the lists hold the same entries
     */
    static List<String> mismatch(PackManifestDso listed, PackManifestDso derived) {
        Set<String> listedConstituents = constituents(listed);
        Set<String> derivedConstituents = constituents(derived);
        Set<String> listedPresets = presets(listed);
        Set<String> derivedPresets = presets(derived);
        List<String> out = new ArrayList<>();
        listedConstituents.stream()
                .filter(entry -> !derivedConstituents.contains(entry))
                .forEach(entry -> out.add("lists constituent " + entry + ", which the pack does not hold"));
        listedPresets.stream()
                .filter(entry -> !derivedPresets.contains(entry))
                .forEach(entry -> out.add("lists preset " + entry + ", which the pack does not hold"));
        derivedConstituents.stream()
                .filter(entry -> !listedConstituents.contains(entry))
                .forEach(entry -> out.add("leaves out constituent " + entry + ", which the pack holds"));
        derivedPresets.stream()
                .filter(entry -> !listedPresets.contains(entry))
                .forEach(entry -> out.add("leaves out preset " + entry + ", which the pack holds"));
        return List.copyOf(out);
    }

    private static Set<String> constituents(PackManifestDso manifest) {
        Set<String> out = new LinkedHashSet<>();
        manifest.constituents().forEach(c -> out.add(c.kind() + ":" + c.id() + " at " + c.path()));
        return out;
    }

    private static Set<String> presets(PackManifestDso manifest) {
        Set<String> out = new LinkedHashSet<>();
        manifest.presets().forEach(p -> out.add(p.id() + " at " + p.path()));
        return out;
    }

    private static Optional<byte[]> bytesAt(Path directory, String path) {
        if (Companions.inPack(directory, path) instanceof Companions.Inside at && Files.isRegularFile(at.file())) {
            return FilesystemPackStore.readBytes(at.file());
        }
        return Optional.empty();
    }
}
