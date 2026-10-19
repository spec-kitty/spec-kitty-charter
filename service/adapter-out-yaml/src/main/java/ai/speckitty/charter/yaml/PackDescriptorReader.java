package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.Diagnostic;
import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.RefusalCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Reads {@code pack.yaml}. An absent {@code pack_level} is a {@code PACK_LEVEL_MISSING} diagnostic and the
 * configured level is used, as core does during the deprecation window of core ADR 2026-10-11-2; a declared
 * level other than the configured one is refused by {@code CharterPack}.
 */
final class PackDescriptorReader {

    record Result(PackDescriptor descriptor, List<Diagnostic> diagnostics) {}

    private PackDescriptorReader() {}

    static Result read(String text, String packName, PackLevel configured) {
        Object document;
        try {
            document = Yaml.load(text);
        } catch (RuntimeException e) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of " + packName + " is not valid YAML", e);
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of " + packName + " is not a mapping");
        }
        @Nullable String levelToken = text(root, "pack_level", packName).orElse(null);
        PackLevel level = configured;
        List<Diagnostic> diagnostics = List.of();
        if (levelToken == null) {
            diagnostics = List.of(new Diagnostic(
                    Diagnostic.Code.PACK_LEVEL_MISSING,
                    "pack.yaml of " + packName + " has no pack_level; the configured level " + configured.token()
                            + " is used"));
        } else {
            level = PackLevel.fromToken(levelToken)
                    .orElseThrow(() -> new CharterRefusal(
                            RefusalCode.PACK_UNREADABLE,
                            "pack.yaml of " + packName + " declares an unknown pack_level '" + levelToken + "'"));
        }
        PackDescriptor descriptor = new PackDescriptor(
                required(root, "pack_id", packName),
                required(root, "pack_version", packName),
                text(root, "parent_pack", packName),
                required(root, "name", packName),
                level);
        return new Result(descriptor, diagnostics);
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
