package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.Assembly;
import ai.speckitty.charter.packs.domain.Preset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The one derivation of a pack manifest's lists (#110, ADR-0042, #554 T024): one constituent per artifact the pack holds
 * (its kind token, its id and the file it is stored in) and one preset entry per preset at {@code presets/<id>.yaml},
 * each list in path order (the order of the paths' UTF-16 code units).
 *
 * <p>The reader derives the set it puts in the census beside the listed one, the manifest stamp derives the lists it
 * writes, and the whole-pack write derives the lists of the pack it holds, all through here, so no two of them can
 * split (A-D1). It is a plain class, not a bean, and names no store bean (A-B3): the derivation of a stored directory
 * reads it with the store's own reader and builds through the Spring-free {@link PackAssembly}, so the reader that calls
 * it reaches no framework ({@code THE_PACKS_LOAD_REACHES_NO_FRAMEWORK}).
 */
final class PackManifestDerivation {

    /** The {@code schema_version} every derived manifest carries. */
    static final String SCHEMA_VERSION = "1.0";

    private PackManifestDerivation() {}

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
     * The derived manifest of the pack stored in a directory, read with the bundled bindings and companion rules.
     *
     * @param directory the pack's directory
     * @param strategy the serialization format the pack is read in
     * @return the manifest with the derived lists, in path order
     * @throws CharterRefusal as {@link #derivedAt(Path, SerializationStrategy, List, Companions.Rules)} does
     */
    static PackManifestDso derivedAt(Path directory, SerializationStrategy strategy) {
        return derivedAt(directory, strategy, KindBindings.all(), Companions.Rules.bundled());
    }

    /**
     * The derived manifest of the pack stored in a directory: the artifacts and presets its whole-pack load holds, read
     * by the one reader's {@link StoreRead} half (the census derives through the same read, WP05 review MINOR-5) and
     * built by {@link PackAssembly} with the store half's own withholding ({@link StoreRead#assembly}), so
     * the lists name exactly the artifacts that load holds. A file the load does not hold (a read violation, a step
     * enhancement withheld for a repeated stem) is not listed. An unreadable graph document is tolerated, as a read
     * tolerates it: the strict load and this one hold the same artifacts, so the registration check and the stamp never
     * derive different lists.
     *
     * @param directory the pack's directory
     * @param strategy the serialization format the pack is read in
     * @param bindings the bindings the pack is read with
     * @param companionRules the table that classifies companion paths and the schemas of Markdown companions
     * @return the manifest with the derived lists, in path order
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the directory is not a pack directory or has no readable
     *     {@code pack.yaml}; with {@code VALIDATION_FAILED} when the readable artifacts break an aggregate invariant
     */
    static PackManifestDso derivedAt(
            Path directory,
            SerializationStrategy strategy,
            List<KindBinding<?, ?>> bindings,
            Companions.Rules companionRules) {
        Path packDir = directory.toAbsolutePath().normalize();
        String name = packDir.getFileName() == null ? "pack" : String.valueOf(packDir.getFileName());
        // The store's read refuses a directory that is not there before it lists anything.
        PackFileSet.Walk walk = Files.isDirectory(packDir) ? PackFileSet.walk(packDir, name) : PackFileSet.Walk.NONE;
        StoreRead read = StoreRead.of(packDir, name, walk, strategy, bindings, companionRules);
        return switch (read.assembly()) {
            case Assembly.Assembled held -> derived(PackAssembly.build(held), read.layout());
            case Assembly.Refused refused -> throw new CharterRefusal(refused.code(), refused.message());
            case Assembly.None none -> throw new IllegalStateException("the store's read gave no assembly input");
        };
    }
}
