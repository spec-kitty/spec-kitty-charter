package ai.speckitty.charter.contracts.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Every definition file of the charter contract is reached from {@code openapi.yaml} through {@code $ref}s (#312).
 * The bundle holds only what {@code openapi.yaml} reaches, so a file nothing reaches is in no bundle: no check of the
 * bundle ever reads it, and the refusal codes or rules it states are judged nowhere. An {@code _index.yaml} list is not
 * a reference. A file is referenced from an operation, or deleted.
 */
class ReachableContractFilesTest {

    /** The directories whose files are definitions an operation reaches; scenarios and changes are not. */
    private static final List<String> DEFINITIONS =
            List.of("paths", "responses", "parameters", "headers", "schemas", "examples");

    private static final String ENTRY = "openapi.yaml";

    @Test
    void everyDefinitionFileOfTheCharterContractIsReachedFromItsEntry() {
        Path contract = Path.of(System.getProperty("charter.contractDir"));
        Set<Path> reached = reached(contract.resolve(ENTRY));
        assertThat(reached).as("the files the walk reached").hasSizeGreaterThan(DEFINITIONS.size());
        assertThat(unreached(contract, reached))
                .as("definition files no $ref from %s reaches; reference each from an operation or delete it", ENTRY)
                .isEmpty();
    }

    @Test
    void aFileReachedOnlyFromAnUnreachedFileIsUnreached(@TempDir Path contract) throws IOException {
        write(contract, ENTRY, "paths:\n  /a:\n    $ref: paths/a.yaml\n");
        write(contract, "paths/a.yaml", "get:\n  responses:\n    '200':\n      $ref: '../responses/A.yaml#/x'\n");
        write(contract, "responses/A.yaml", "description: A.\n");
        write(contract, "responses/Orphan.yaml", "content:\n  schema:\n    $ref: ../schemas/OnlyTheOrphanUses.yaml\n");
        write(contract, "responses/_index.yaml", "files:\n  - A.yaml\n  - Orphan.yaml\n");
        write(contract, "schemas/OnlyTheOrphanUses.yaml", "type: string\n");
        write(contract, "scenarios/x.yaml", "not: a definition\n");

        assertThat(unreached(contract, reached(contract.resolve(ENTRY))))
                .containsExactly("responses/Orphan.yaml", "schemas/OnlyTheOrphanUses.yaml");
    }

    /** The files reached from {@code entry} through the file part of each {@code $ref}, the entry included. */
    private static Set<Path> reached(Path entry) {
        Set<Path> reached = new HashSet<>();
        Deque<Path> next = new ArrayDeque<>(List.of(entry.normalize()));
        while (!next.isEmpty()) {
            Path file = next.pop();
            if (!Files.isRegularFile(file) || !reached.add(file)) {
                continue;
            }
            Set<String> refs = new HashSet<>();
            refs(read(file), refs);
            for (String ref : refs) {
                String target = ref.contains("#") ? ref.substring(0, ref.indexOf('#')) : ref;
                if (!target.isEmpty()) {
                    next.push(file.resolveSibling(target).normalize());
                }
            }
        }
        return reached;
    }

    /** The definition files under {@code contract} not in {@code reached}, relative and sorted; no index list. */
    private static List<String> unreached(Path contract, Set<Path> reached) {
        try (Stream<Path> files = Files.walk(contract)) {
            return files.filter(file -> file.toString().endsWith(".yaml"))
                    .filter(file -> !file.getFileName().toString().equals("_index.yaml"))
                    .filter(file -> DEFINITIONS.contains(
                            contract.relativize(file).getName(0).toString()))
                    .filter(file -> !reached.contains(file.normalize()))
                    .map(file -> contract.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void refs(Object node, Set<String> refs) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if ("$ref".equals(key) && value instanceof String ref) {
                    refs.add(ref);
                } else {
                    refs(value, refs);
                }
            });
        } else if (node instanceof List<?> list) {
            list.forEach(child -> refs(child, refs));
        }
    }

    private static Object read(Path file) {
        try {
            return new Load(LoadSettings.builder().setLabel(file.toString()).build())
                    .loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path root, String name, String text) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
