package ai.speckitty.charter.contracts.identifiers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * One schema object of the contracts that carries a string {@code pattern}, with the keywords that decide a string's
 * verdict beside it: {@code maxLength}, {@code minLength} and a {@code not: {const: ...}}.
 *
 * @param location the file, relative to {@code contracts/}, and the JSON pointer of the object
 * @param pattern the ECMA-262 pattern
 * @param maxLength the cap, or {@code null}
 * @param minLength the floor, or {@code null}
 * @param forbidden the value a sibling {@code not: {const: ...}} refuses, or {@code null}
 */
record IdentifierSite(
        String location,
        String pattern,
        @Nullable Integer maxLength,
        @Nullable Integer minLength,
        @Nullable String forbidden) {

    /** The schema files: the charter and shared OpenAPI parts, the offering schemas and the pack schemas. */
    static final List<String> SCHEMA_ROOTS = List.of("charter", "_shared", "offering/schemas", "packs/schemas");

    /** Directories under those roots that hold instances, not schemas. */
    private static final Set<String> NOT_SCHEMAS = Set.of("examples", "scenarios", "changes");

    /** The schema's verdict on a string (ASCII, so {@code length()} is the code-point count JSON Schema uses). */
    boolean accepts(String text, EcmaPattern compiled) {
        return (maxLength == null || text.length() <= maxLength)
                && (minLength == null || text.length() >= minLength)
                && !text.equals(forbidden)
                && compiled.accepts(text);
    }

    /** Every site of every schema file under {@code contracts}. */
    static List<IdentifierSite> discover(Path contracts) {
        List<IdentifierSite> sites = new ArrayList<>();
        for (String root : SCHEMA_ROOTS) {
            for (Path file : schemaFiles(contracts.resolve(root))) {
                String name = contracts.relativize(file).toString().replace('\\', '/');
                collect(name + "#", load(file), sites);
            }
        }
        return sites;
    }

    private static List<Path> schemaFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().matches(".*\\.(ya?ml|json)"))
                    .filter(file -> root.relativize(file).getNameCount() == 1
                            || !NOT_SCHEMAS.contains(
                                    root.relativize(file).getName(0).toString()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static @Nullable Object load(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return new Load(LoadSettings.builder().setLabel(file.toString()).build()).loadFromString(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void collect(String pointer, @Nullable Object node, List<IdentifierSite> sites) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("pattern") instanceof String pattern) {
                sites.add(new IdentifierSite(
                        pointer,
                        pattern,
                        integer(map.get("maxLength")),
                        integer(map.get("minLength")),
                        forbidden(map.get("not"))));
            }
            map.forEach((key, value) -> collect(pointer + "/" + key, value, sites));
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                collect(pointer + "/" + i, list.get(i), sites);
            }
        }
    }

    private static @Nullable Integer integer(@Nullable Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static @Nullable String forbidden(@Nullable Object not) {
        return not instanceof Map<?, ?> map && map.size() == 1 && map.get("const") instanceof String value
                ? value
                : null;
    }
}
