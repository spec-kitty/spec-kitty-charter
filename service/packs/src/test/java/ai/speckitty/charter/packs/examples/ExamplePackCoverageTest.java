package ai.speckitty.charter.packs.examples;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.examples.ExampleCoverage.FileRule;
import ai.speckitty.charter.packs.examples.ExampleCoverage.PayloadRule;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The example packs cannot thin out (WP03, FR-003, SC-003): every property a bundled pack schema declares is set by
 * some example file, and every artifact kind with a pack file kind has an example file.
 */
class ExamplePackCoverageTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** T010: each declared property (nested and {@code $defs} included) is set by at least one example. */
    @Test
    void everySchemaPropertyHasAnExample() {
        assertThat(schemaGaps(ExamplePacks.packsDirectory())).isEmpty();
    }

    /** T010 control: a schema declaring a property no example sets fails, naming kind and pointer. */
    @Test
    void aPropertyNoExampleSetsIsNamed() {
        JsonNode schema = MAPPER.readTree("""
                {"type": "object", "$defs": {"step": {"type": "object", "properties": {
                    "id": {"type": "string"}, "unset_nested": {"type": "string"}}}},
                 "properties": {"id": {"type": "string"},
                                "steps": {"type": "array", "items": {"$ref": "#/$defs/step"}},
                                "unset": {"type": "string"}}}
                """);
        List<JsonNode> examples = List.of(MAPPER.readTree("{\"id\": \"a\", \"steps\": [{\"id\": \"s\"}]}"));

        assertThat(ExampleCoverage.gaps("fixture", schema, examples))
                .containsExactly("fixture: /steps/*/unset_nested has no example", "fixture: /unset has no example");
    }

    /** T011: every artifact kind that has a pack file kind has at least one example file across the three packs. */
    @Test
    void everyArtifactKindWithAFileKindHasAnExample() {
        assertThat(fileGlobs()).as("artifact kinds with a pack file kind").isNotEmpty();
        assertThat(kindGaps(ExampleCoverage.files(ExamplePacks.packsDirectory())))
                .isEmpty();
    }

    /** T011 control: without its guard files, the guard kind is named. */
    @Test
    void aKindWithNoExampleFileIsNamed() {
        List<String> withoutGuards = ExampleCoverage.files(ExamplePacks.packsDirectory()).stream()
                .filter(file -> !file.endsWith(".guard.yaml"))
                .toList();

        assertThat(kindGaps(withoutGuards)).containsExactly("guard has no example file (**/*.guard.yaml)");
    }

    /** T010 control over the real packs: a copy without one example field is named by kind and pointer. */
    @Test
    void anExampleFieldRemovedFromACopyIsNamed(@TempDir Path dir) throws IOException {
        Path packs = dir.resolve("packs");
        for (String name : List.of(ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT)) {
            ExamplePacks.copyOf(name, packs);
        }
        Path policy = packs.resolve(ExamplePacks.BUILT_IN).resolve("policy.yaml");
        Files.writeString(policy, Files.readString(policy).replace("  guard: open\n", ""));

        assertThat(schemaGaps(packs)).containsExactly("policy: /kind_defaults/guard has no example");
    }

    static List<String> kindGaps(List<String> files) {
        List<String> missing = new ArrayList<>();
        fileGlobs().forEach((kind, kindGlobs) -> {
            boolean exampled = files.stream()
                    .anyMatch(file -> kindGlobs.stream().anyMatch(glob -> ExampleCoverage.matches(glob, file)));
            if (!exampled) {
                missing.add(kind.token() + " has no example file (" + String.join(", ", kindGlobs) + ")");
            }
        });
        return missing;
    }

    static List<String> schemaGaps(Path packsDir) {
        List<String> gaps = new ArrayList<>();
        Map<String, List<FileRule>> bySchema = new LinkedHashMap<>();
        for (FileRule rule : ExampleCoverage.fileRules()) {
            bySchema.computeIfAbsent(rule.schema(), k -> new ArrayList<>()).add(rule);
        }
        bySchema.forEach((kind, rules) -> {
            List<JsonNode> examples = new ArrayList<>();
            rules.forEach(rule -> examples.addAll(ExampleCoverage.examples(packsDir, rule)));
            if (examples.isEmpty()) {
                if (!rules.stream().allMatch(rule -> leftUnmodelled(packsDir, rule))) {
                    gaps.add(kind + ": no example file");
                }
            } else {
                gaps.addAll(ExampleCoverage.gaps(kind, ExampleCoverage.schema(kind), examples));
            }
        });
        return gaps;
    }

    /**
     * Whether the whole-pack store leaves a file of this rule unmodelled (a project's {@code charter.yaml}, an org's
     * {@code org-charter.yaml}, a generated {@code pack-manifest.yaml}): such a file is no pack content, so an example
     * pack cannot hold one without failing {@code everyPackLoadsWholeWithNoViolation}. The answer is read from the
     * store, never listed: a placeholder file at the rule's path in a copy of each example pack must come back
     * unmodelled from every pack. Once the store models the kind, this is false and the kind needs an example.
     */
    static boolean leftUnmodelled(Path packsDir, FileRule rule) {
        try {
            Path scratch = Files.createTempDirectory("coverage-unmodelled");
            try {
                for (String name : List.of(ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT)) {
                    Path copy = ExamplePacks.copyOf(name, scratch.resolve(name));
                    String relative = samplePath(rule.match());
                    Path sample = copy.resolve(relative);
                    Files.createDirectories(sample.getParent());
                    Files.writeString(sample, "{}\n");
                    WholePackStore.LoadedPack loaded = new WholePackStore(new YamlSerializationStrategy()).load(copy);
                    if (!loaded.unmodelled().contains(relative)) {
                        return false;
                    }
                }
                return true;
            } finally {
                deleteTree(scratch);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A pack-relative path a rule's glob matches: the pack segment {@code *} dropped, {@code **}{@code /} empty. */
    static String samplePath(String match) {
        String path = match.startsWith("*/") ? match.substring(2) : match;
        return path.replace("**/", "").replace("*", "sample");
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** Each artifact kind with a pack file kind, mapped to the globs of its files and payloads rules. */
    private static Map<ArtifactKind, List<String>> fileGlobs() {
        Map<ArtifactKind, List<String>> globs = new LinkedHashMap<>();
        for (ArtifactKind kind : ArtifactKind.values()) {
            String schema = kind.token().replace('_', '-');
            List<String> kindGlobs = new ArrayList<>();
            for (FileRule rule : ExampleCoverage.fileRules()) {
                if (rule.schema().equals(schema)) {
                    kindGlobs.add(rule.match());
                }
            }
            for (PayloadRule rule : ExampleCoverage.payloadRules()) {
                if (rule.kind().equals(kind.token())) {
                    kindGlobs.add(rule.match());
                }
            }
            if (!kindGlobs.isEmpty()) {
                globs.put(kind, kindGlobs);
            }
        }
        return globs;
    }
}
