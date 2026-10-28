package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.Payload;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The derived fields of a pack manifest (ADR-0043) and their one recompute. Both recipes are language-neutral, so any
 * reader can reproduce them byte for byte, whatever YAML emitter wrote the files:
 *
 * <ul>
 *   <li>{@code content_hash}: SHA-256, lowercase hex, of a file's bytes after every CRLF, then every lone CR, is
 *       replaced by LF, so a checkout's line endings do not change it;
 *   <li>{@code manifest_hash}: SHA-256, lowercase hex, of the UTF-8 bytes of the RFC 8785 (JCS) canonical JSON of the
 *       manifest object without {@code manifest_hash}: {@code schema_version}, {@code constituents} (each
 *       {@code kind}, {@code id}, {@code path}, {@code content_hash}) and, when the manifest lists any,
 *       {@code presets} (each {@code id}, {@code path}, {@code content_hash}).
 * </ul>
 *
 * <p>The writer recomputes every derived field from the bytes it writes; the reader takes them as written and checks
 * nothing; the round-trip gate verifies them on both sides by diffing a recompute against what
 * was read ({@link #stale}). One walk serves all three.
 */
final class PackManifests {

    private PackManifests() {}

    /**
     * A recompute: the manifest with every derived field stamped from the bytes found, and the listed paths that had
     * none.
     *
     * @param stamped the manifest with fresh derived fields; a missing file's entry keeps the hash it had
     * @param missing the listed paths with no bytes, in listed order
     */
    record Recomputed(PackManifestDso stamped, List<String> missing) {
        /**
         * Checks the fields and copies the list.
         *
         * @param stamped the stamped manifest
         * @param missing the missing paths
         */
        Recomputed {
            Objects.requireNonNull(stamped, "stamped");
            missing = List.copyOf(missing);
        }
    }

    /**
     * The {@code content_hash} of a file.
     *
     * @param bytes the file's bytes, as stored
     * @return 64 lowercase hex characters
     */
    static String contentHash(byte[] bytes) {
        return Payload.of(lf(bytes)).sha256();
    }

    /**
     * The {@code manifest_hash} of a manifest: its own value is ignored.
     *
     * @param manifest the manifest
     * @return 64 lowercase hex characters
     */
    static String manifestHash(PackManifestDso manifest) {
        return Payload.of(canonicalJson(manifest).getBytes(StandardCharsets.UTF_8))
                .sha256();
    }

    /**
     * Recomputes every derived field: each listed file's {@code content_hash} from its bytes, then the
     * {@code manifest_hash}. Membership is kept as listed.
     *
     * @param manifest the manifest to stamp
     * @param bytesAt the bytes of a pack-relative path, or empty when there is no such file
     * @return the stamped manifest and the listed paths that have no file
     */
    static Recomputed recompute(PackManifestDso manifest, Function<String, Optional<byte[]>> bytesAt) {
        List<String> missing = new ArrayList<>();
        List<PackManifestDso.Constituent> constituents = new ArrayList<>();
        for (PackManifestDso.Constituent c : manifest.constituents()) {
            String hash = hashOrMissing(c.path(), c.contentHash(), bytesAt, missing);
            constituents.add(new PackManifestDso.Constituent(c.kind(), c.id(), c.path(), hash));
        }
        List<PackManifestDso.PresetEntry> presets = new ArrayList<>();
        for (PackManifestDso.PresetEntry p : manifest.presets()) {
            presets.add(new PackManifestDso.PresetEntry(
                    p.id(), p.path(), hashOrMissing(p.path(), p.contentHash(), bytesAt, missing)));
        }
        PackManifestDso unstamped =
                new PackManifestDso(manifest.schemaVersion(), manifest.manifestHash(), constituents, presets);
        PackManifestDso stamped =
                new PackManifestDso(manifest.schemaVersion(), manifestHash(unstamped), constituents, presets);
        return new Recomputed(stamped, missing);
    }

    /**
     * Every derived field of {@code read} that does not verify against its own side's bytes, and every listed path with
     * no file: the gate's verification, in this order: {@code constituents{<path>}.content_hash},
     * {@code presets{<path>}.content_hash}, {@code manifest_hash}, {@code missing: <path>}. A {@code content_hash} is
     * checked against the file's bytes; the {@code manifest_hash} against the manifest's own fields as read, so a
     * restamped manifest with one stale file flags only that file. It takes the bytes rather than a {@link Recomputed},
     * so one walk serves the check.
     *
     * @param read the manifest as read
     * @param bytesAt the bytes of a pack-relative path on the same side, or empty when there is no such file
     * @return the stale fields; empty when every derived field verifies
     */
    static List<String> stale(PackManifestDso read, Function<String, Optional<byte[]>> bytesAt) {
        Recomputed recomputed = recompute(read, bytesAt);
        List<String> out = new ArrayList<>();
        List<PackManifestDso.Constituent> fresh = recomputed.stamped().constituents();
        for (int i = 0; i < fresh.size(); i++) {
            PackManifestDso.Constituent c = read.constituents().get(i);
            if (!recomputed.missing().contains(c.path())
                    && !c.contentHash().equals(fresh.get(i).contentHash())) {
                out.add("constituents{" + c.path() + "}.content_hash");
            }
        }
        List<PackManifestDso.PresetEntry> freshPresets = recomputed.stamped().presets();
        for (int i = 0; i < freshPresets.size(); i++) {
            PackManifestDso.PresetEntry p = read.presets().get(i);
            if (!recomputed.missing().contains(p.path())
                    && !p.contentHash().equals(freshPresets.get(i).contentHash())) {
                out.add("presets{" + p.path() + "}.content_hash");
            }
        }
        if (!read.manifestHash().equals(manifestHash(read))) {
            out.add("manifest_hash");
        }
        recomputed.missing().forEach(path -> out.add("missing: " + path));
        return List.copyOf(out);
    }

    private static String hashOrMissing(
            String path, String current, Function<String, Optional<byte[]>> bytesAt, List<String> missing) {
        Optional<byte[]> bytes = bytesAt.apply(path);
        if (bytes.isEmpty()) {
            missing.add(path);
            return current;
        }
        return contentHash(bytes.get());
    }

    /**
     * The bytes with every CRLF, then every lone CR, as LF.
     *
     * @param bytes the file's bytes
     * @return the normalized bytes
     */
    static byte[] lf(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            boolean crlfTail = bytes[i] == '\n' && i > 0 && bytes[i - 1] == '\r';
            if (bytes[i] == '\r') {
                out.write('\n');
            } else if (!crlfTail) {
                out.write(bytes[i]);
            }
        }
        return out.toByteArray();
    }

    /**
     * The RFC 8785 canonical JSON of the manifest without {@code manifest_hash}. Narrow on purpose: every value is a
     * string, a list or an object of the manifest's fixed keys, so canonical number output is never needed. Object
     * keys are written sorted by UTF-16 code units, which for these ASCII keys is their written order below.
     *
     * @param manifest the manifest
     * @return the canonical JSON text
     */
    static String canonicalJson(PackManifestDso manifest) {
        StringBuilder out = new StringBuilder("{\"constituents\":[");
        for (int i = 0; i < manifest.constituents().size(); i++) {
            PackManifestDso.Constituent c = manifest.constituents().get(i);
            out.append(i == 0 ? "" : ",")
                    .append("{\"content_hash\":")
                    .append(jcsString(c.contentHash()))
                    .append(",\"id\":")
                    .append(jcsString(c.id()))
                    .append(",\"kind\":")
                    .append(jcsString(c.kind()))
                    .append(",\"path\":")
                    .append(jcsString(c.path()))
                    .append('}');
        }
        out.append(']');
        if (!manifest.presets().isEmpty()) {
            out.append(",\"presets\":[");
            for (int i = 0; i < manifest.presets().size(); i++) {
                PackManifestDso.PresetEntry p = manifest.presets().get(i);
                out.append(i == 0 ? "" : ",")
                        .append("{\"content_hash\":")
                        .append(jcsString(p.contentHash()))
                        .append(",\"id\":")
                        .append(jcsString(p.id()))
                        .append(",\"path\":")
                        .append(jcsString(p.path()))
                        .append('}');
            }
            out.append(']');
        }
        return out.append(",\"schema_version\":")
                .append(jcsString(manifest.schemaVersion()))
                .append('}')
                .toString();
    }

    /**
     * A JSON string as RFC 8785 serializes it (ECMAScript {@code JSON.stringify}), narrowly: {@code "} and {@code \}
     * escaped; the C0 controls as {@code \b \t \n \f \r}, or else as a six-character escape (backslash, {@code u},
     * four lowercase hex digits); every other character as itself. A lone surrogate is not rejected, as RFC 8785 would:
     * text read from YAML cannot hold one.
     *
     * @param value the string
     * @return the quoted, escaped JSON string
     */
    static String jcsString(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
