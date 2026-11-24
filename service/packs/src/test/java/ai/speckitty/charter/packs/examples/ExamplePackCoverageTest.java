package ai.speckitty.charter.packs.examples;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.NotHeldFile;
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
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
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

    /**
     * ADR-0080, ADR-0070 decision 2 (#108): the routing content the recommendation's scenarios read is required. A
     * routing catalog sits at two levels (built-in's {@code default} and org-base's same-id override), a profile
     * declares a {@code model} and a step a {@code model_tier}.
     */
    @Test
    void theRoutingContentIsHeldAtTwoLevelsWithAProfileModelAndAStepTier() throws IOException {
        Path packs = ExamplePacks.packsDirectory();
        List<String> files = ExampleCoverage.files(packs);

        assertThat(files)
                .as("a routing catalog at two levels")
                .contains(
                        "built-in/routing_catalogs/default.routing-catalog.yaml",
                        "org-base/routing_catalogs/default.routing-catalog.yaml");
        assertThat(lines(packs, files, ".agent.yaml")).as("a profile's model").contains("model: model-x");
        assertThat(lines(packs, files, ".mission-type.yaml"))
                .as("a step's tier")
                .contains("  model_tier: low");
    }

    /**
     * WP10, T040 (ADR-0052 decision 8, #154): {@code built-in} holds the anti-pattern its default preset requires as a
     * file, and every {@code activated_anti_patterns} item of an example preset names an anti-pattern file its own pack
     * holds, so a {@code must_have} is held, not only declared.
     */
    @Test
    void everyPresetAntiPatternIsAFileItsPackHolds() {
        Path packs = ExamplePacks.packsDirectory();
        List<String> files = ExampleCoverage.files(packs);

        assertThat(files).contains("built-in/anti_patterns/indirection-for-repetition.anti-pattern.yaml");
        List<String> presetItems = new ArrayList<>();
        for (String file : files) {
            if (ExampleCoverage.matches("*/presets/*.yaml", file)) {
                String pack = file.substring(0, file.indexOf('/'));
                JsonNode preset = MAPPER.valueToTree(yaml(packs.resolve(file)));
                for (JsonNode item : preset.path("activated_anti_patterns")) {
                    presetItems.add(pack + "/" + antiPatternFile(item.path("id").asString()));
                }
            }
        }
        assertThat(presetItems).as("the preset items of the example packs").isNotEmpty();
        assertThat(files).containsAll(presetItems);
    }

    /**
     * WP10, T042 (ruling R-r, #154): every {@code anti_pattern} node of an example pack's graph documents names an
     * anti-pattern file the same pack holds: the node declares, the file holds.
     */
    @Test
    void everyExampleAntiPatternNodeNamesAFileItsPackHolds() {
        Path packs = ExamplePacks.packsDirectory();
        List<String> files = ExampleCoverage.files(packs);
        List<String> nodeFiles = new ArrayList<>();
        for (String file : files) {
            if (ExampleCoverage.matches("*/*.graph.yaml", file)) {
                String pack = file.substring(0, file.indexOf('/'));
                JsonNode shard = MAPPER.valueToTree(yaml(packs.resolve(file)));
                for (JsonNode node : shard.path("nodes")) {
                    if ("anti_pattern".equals(node.path("kind").asString())) {
                        String urn = node.path("urn").asString();
                        nodeFiles.add(pack + "/" + antiPatternFile(urn.substring(urn.indexOf(':') + 1)));
                    }
                }
            }
        }
        assertThat(nodeFiles).as("the anti-pattern nodes of the example packs").hasSize(18);
        assertThat(files).containsAll(nodeFiles);
    }

    /**
     * WP10, T042 (ADR-0052 decision 10, ADR-0053 decision 32): the example anti-pattern files write each of the three
     * severities at least once, so the fixtures cover the enum.
     */
    @Test
    void theExampleAntiPatternsCoverEverySeverity() {
        Path packs = ExamplePacks.packsDirectory();
        List<String> severities = new ArrayList<>();
        for (String file : ExampleCoverage.files(packs)) {
            if (file.endsWith(".anti-pattern.yaml")) {
                severities.add(MAPPER.valueToTree(yaml(packs.resolve(file)))
                        .path("severity")
                        .asString());
            }
        }
        assertThat(severities).contains("smell", "severe", "breaking");
    }

    /**
     * WP12, T047a (ruling R-r, ADR-0052 decision 16, #154): no styleguide or procedure of an example pack carries an
     * inline {@code anti_patterns} block; every anti-pattern is its own file, rejected by a {@code rejects} edge.
     */
    @Test
    void noExampleStyleguideOrProcedureCarriesAnInlineAntiPattern() throws IOException {
        Path packs = ExamplePacks.packsDirectory();
        List<String> carriers = new ArrayList<>();
        for (String file : ExampleCoverage.files(packs)) {
            if ((file.endsWith(".styleguide.yaml") || file.endsWith(".procedure.yaml"))
                    && Files.readAllLines(packs.resolve(file)).stream()
                            .anyMatch(line -> line.startsWith("anti_patterns:"))) {
                carriers.add(file);
            }
        }
        assertThat(carriers).isEmpty();
    }

    private static String antiPatternFile(String id) {
        return "anti_patterns/" + id + ".anti-pattern.yaml";
    }

    private static Object yaml(Path file) {
        try {
            return new Load(LoadSettings.builder().build()).loadFromString(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> lines(Path packs, List<String> files, String suffix) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String file : files) {
            if (file.endsWith(suffix)) {
                lines.addAll(Files.readAllLines(packs.resolve(file)));
            }
        }
        return lines;
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
        for (String name : ExamplePacks.names()) {
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
     * Whether the whole-pack store leaves a file of this rule unmodelled (a generated {@code pack-manifest.yaml}):
     * such a file is no pack content, so an example
     * pack cannot hold one without failing {@code everyPackLoadsWholeWithNoViolation}. The answer is read from the
     * store, never listed: a placeholder file at the rule's path in a copy of each example pack must come back
     * unmodelled from every pack. Once the store models the kind, this is false and the kind needs an example.
     */
    static boolean leftUnmodelled(Path packsDir, FileRule rule) {
        try {
            Path scratch = Files.createTempDirectory("coverage-unmodelled");
            try {
                for (String name : ExamplePacks.names()) {
                    Path copy = ExamplePacks.copyOf(name, scratch.resolve(name));
                    String relative = samplePath(rule.match());
                    Path sample = copy.resolve(relative);
                    Files.createDirectories(sample.getParent());
                    Files.writeString(sample, "{}\n");
                    WholePackStore.LoadedPack loaded = new WholePackStore(new YamlSerializationStrategy()).load(copy);
                    if (!loaded.notHeld().contains(NotHeldFile.unmodelled(relative))) {
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
