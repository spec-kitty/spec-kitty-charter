package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * A pack fixture's {@code pack-manifest.yaml}, stamped through the service's own write path (#630, plan D4):
 * {@link ManifestMembership#stamp} over {@link PackManifestDerivation#derivedAt}, never a copy of it. Since a
 * registration and validate mode refuse a stale manifest ({@code manifest_stale}), a fixture that changes a pack's files
 * restamps its manifest, so the fault it plants is its one cause. The example packs are restamped with
 * {@code ManifestStampHelper}.
 */
public final class ManifestStamps {

    private static final SerializationStrategy YAML = new YamlSerializationStrategy();

    private ManifestStamps() {}

    /**
     * Stamps a pack's manifest as every service write does: the derived lists, each {@code content_hash} from the bytes
     * there, then {@code manifest_hash}. A leading comment block of the manifest it replaces is kept.
     *
     * @param pack the pack directory
     * @param packId the registered id the pack is read under
     */
    public static void stamp(Path pack, String packId) {
        Path manifest = pack.resolve(PackFileSet.MANIFEST);
        try {
            String header =
                    Files.isRegularFile(manifest) ? header(Files.readString(manifest, StandardCharsets.UTF_8)) : "";
            ManifestMembership.stamp(
                    pack,
                    directory -> PackManifestDerivation.derivedAt(new PackIdentifier(packId), directory, YAML),
                    YAML);
            Files.writeString(
                    manifest, header + Files.readString(manifest, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Sets a manifest's {@code manifest_hash} to the hash of its fields as written, leaving its lists and every
     * {@code content_hash} as they are: a fixture that edits the lists on purpose then differs only in membership.
     *
     * @param pack the pack directory
     */
    public static void rehash(Path pack) {
        Path manifest = pack.resolve(PackFileSet.MANIFEST);
        try {
            String text = Files.readString(manifest, StandardCharsets.UTF_8);
            if (!(YAML.readManifest(text, PackFileSet.MANIFEST) instanceof FileRead.Read<PackManifestDso> read)) {
                throw new IllegalArgumentException(manifest + " does not read as a manifest");
            }
            Files.writeString(
                    manifest,
                    text.replace(
                            "manifest_hash: " + read.value().manifestHash(),
                            "manifest_hash: " + PackManifests.manifestHash(read.value())),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String header(String text) {
        return text.lines()
                .takeWhile(line -> line.startsWith("#"))
                .map(line -> line + "\n")
                .collect(Collectors.joining());
    }
}
