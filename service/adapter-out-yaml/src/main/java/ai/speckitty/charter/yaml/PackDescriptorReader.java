package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.RefusalCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code pack.yaml} as facts. The declared {@code pack_level} is reported as written, or as empty when the
 * key is absent; {@code PackPolicy} decides what a missing or different level means.
 */
final class PackDescriptorReader {

    private PackDescriptorReader() {}

    static PackDescriptor read(String text, String packName) {
        Object document;
        try {
            document = Yaml.load(text);
        } catch (RuntimeException e) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of " + packName + " is not valid YAML", e);
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of " + packName + " is not a mapping");
        }
        Optional<PackLevel> declaredLevel = text(root, "pack_level", packName)
                .map(token -> PackLevel.fromToken(token)
                        .orElseThrow(() -> new CharterRefusal(
                                RefusalCode.PACK_UNREADABLE,
                                "pack.yaml of " + packName + " declares an unknown pack_level '" + token + "'")));
        return new PackDescriptor(
                required(root, "pack_id", packName),
                required(root, "pack_version", packName),
                text(root, "parent_pack", packName),
                required(root, "name", packName),
                declaredLevel,
                Optional.empty(),
                List.of());
    }

    private static String required(Map<?, ?> root, String key, String packName) {
        return text(root, key, packName)
                .orElseThrow(() ->
                        new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of " + packName + " has no " + key));
    }

    private static Optional<String> text(Map<?, ?> root, String key, String packName) {
        Object value = root.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String s) {
            return Optional.of(s);
        }
        throw new CharterRefusal(
                RefusalCode.PACK_UNREADABLE, "pack.yaml of " + packName + ": " + key + " must be a string");
    }
}
