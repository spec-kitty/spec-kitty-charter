package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Reads {@code pack.yaml} as facts, through the bundled {@code pack-descriptor} schema. A file that is not YAML or
 * breaks the schema is {@link FileRead.Unreadable}; any other is a {@link PackDescriptor} of what the file states,
 * with nothing defaulted: the declared {@code pack_level} is as written or empty and {@code requires_core} is the raw
 * text. The schema is closed, so a key it does not declare, a retired field included, makes the file unreadable
 * (ADR-0036 decision 3). {@code PackPolicy} decides what any of it means.
 */
final class PackDescriptorReader {

    private static final String FILE = "pack.yaml";
    private static final String KIND = "pack-descriptor";

    private PackDescriptorReader() {}

    static FileRead<PackDescriptor> read(String text) {
        ParsedYaml parsed = ParsedYaml.parse(text, FILE);
        if (parsed.failure() != null) {
            return new FileRead.Unreadable<>(FILE, parsed.failure(), List.of());
        }
        Object document = parsed.document();
        List<SchemaViolation> violations = PackSchemas.bundled().validate(KIND, FILE, document);
        if (!violations.isEmpty()) {
            return new FileRead.Unreadable<>(FILE, null, violations);
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalStateException("a pack descriptor passed the schema but is no mapping");
        }
        Optional<PackLevel> level = optional(root, "pack_level")
                .map(token -> PackLevel.fromToken(token)
                        .orElseThrow(() -> new IllegalStateException(
                                "pack_level '" + token + "' passed the schema but is no pack level")));
        List<PackOverride> overrides = overrides(root);
        Optional<String> settledTwice = PackDescriptor.artifactSettledTwice(overrides);
        if (settledTwice.isPresent()) {
            // The schema's uniqueItems cannot say "one overrides edge per artifact": two edges naming different
            // members are not identical items. The descriptor owns that rule; the reader refuses the second edge
            // as that keyword would.
            return new FileRead.Unreadable<>(
                    FILE,
                    null,
                    List.of(new SchemaViolation(
                            FILE, List.of("overrides"), "uniqueItems", Optional.empty(), settledTwice, Map.of())));
        }
        return new FileRead.Read<>(
                FILE,
                new PackDescriptor(
                        required(root, "pack_id"),
                        required(root, "pack_version"),
                        optional(root, "parent_pack"),
                        required(root, "name"),
                        level,
                        optional(root, "requires_core"),
                        new PackPaths(
                                optional(root, "active_charter"),
                                optional(root, "pack_drg"),
                                optional(root, "mutability_policy")),
                        dependencies(root),
                        overrides,
                        optional(root, "schema_version"),
                        optional(root, "fallback_profile")));
    }

    /** The declared dependencies; the schema has already closed every entry. */
    private static List<PackDependency> dependencies(Map<?, ?> root) {
        @Nullable Object raw = root.get("depends_on");
        if (!(raw instanceof List<?> items)) {
            return List.of();
        }
        List<PackDependency> declared = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> entry)) {
                throw new IllegalStateException("a depends_on entry passed the schema but is no mapping");
            }
            declared.add(new PackDependency(required(entry, "pack_id"), required(entry, "range")));
        }
        return List.copyOf(declared);
    }

    /** The declared sibling conflicts the descriptor settles, in file order; the schema has already closed every entry. */
    private static List<PackOverride> overrides(Map<?, ?> root) {
        @Nullable Object raw = root.get("overrides");
        if (!(raw instanceof List<?> items)) {
            return List.of();
        }
        List<PackOverride> declared = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> entry)) {
                throw new IllegalStateException("an overrides entry passed the schema but is no mapping");
            }
            declared.add(new PackOverride(required(entry, "artifact"), required(entry, "member")));
        }
        return List.copyOf(declared);
    }

    /** A string the schema has already required; anything else means the reader and the schema have drifted apart. */
    private static String required(Map<?, ?> root, String key) {
        return optional(root, key)
                .orElseThrow(() -> new IllegalStateException("'" + key + "' passed the schema but is not a string"));
    }

    private static Optional<String> optional(Map<?, ?> root, String key) {
        @Nullable Object value = root.get(key);
        return value instanceof String s ? Optional.of(s) : Optional.empty();
    }
}
