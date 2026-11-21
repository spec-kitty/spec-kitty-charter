package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.roundtrip.FileOutcome;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.domain.roundtrip.Reading;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Outcome;
import ai.speckitty.charter.packvalidation.roundtrip.MeaningComparator;
import ai.speckitty.charter.packvalidation.roundtrip.RoundTripObservation;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The production flow of the whole-pack round trip ({@link PackRoundTrip}, ADR-0075 decision 8 as amended on
 * 2026-11-15): what it counts, what it reads and what it records. The gate that judges it is
 * {@code pack-validation}'s {@code WholePackRoundTripTest}.
 */
class WholePackFlowTest {

    private static final String BUILT_IN = ExamplePacks.BUILT_IN;

    /** The id of {@link PackManifestRoundTripTest#fixture}, also the name of its directory. */
    private static final PackIdentifier MANIFEST_PACK = new PackIdentifier(PackManifestRoundTripTest.FIXTURE);

    /**
     * The mission templates under {@code missions/<type>/templates/} census as {@code template} (#194, ADR-0044, which
     * ADR-0040 deferred them to), beside the pack's top-level {@code templates/}: every file there is one.
     */
    @Test
    void missionTemplatesAreTemplates() {
        SortedMap<String, String> census = PackRoundTrip.census(ExamplePacks.pack(BUILT_IN));

        List<String> missionTemplates = census.keySet().stream()
                .filter(path -> path.matches("missions/[^/]+/templates/.+"))
                .toList();
        assertThat(census)
                .as("the example pack holds an orientation README beside its templates, which is excluded (#451)")
                .containsEntry(
                        "missions/software-dev/templates/README.md",
                        PackRoundTripResult.EXCLUDED + "*/missions/*/templates/**/README.md");
        assertThat(missionTemplates)
                .filteredOn(path -> census.get(path).equals("template"))
                .as("every mission template but the orientation READMEs, which are excluded")
                .isNotEmpty()
                // The filters below deliberately restate, in the test, the production rule that excludes a mission
                // template from the census (READMEs, hidden files, plan-field-declaration.yaml): an independent mirror,
                // so a change to the production rule that drops or admits a file shows up as a difference here.
                .hasSize((int) missionTemplates.stream()
                        .filter(path -> !path.endsWith("/README.md") && !path.equals("README.md"))
                        .filter(path -> !path.endsWith("plan-field-declaration.yaml"))
                        .filter(path -> !Path.of(path).getFileName().toString().startsWith("."))
                        .count())
                .noneSatisfy(path -> assertThat(path).endsWith("README.md"));
        assertThat(census.get("missions/software-dev/templates/README.md"))
                .as("an orientation README is not a template")
                .isNotEqualTo("template");
        assertThat(census.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith("templates/"))
                        .map(Map.Entry::getValue)
                        .toList())
                .as("every file under the top-level templates/ is a template")
                .isNotEmpty()
                .containsOnly("template");
    }

    /**
     * The flow reads each side in its content form, decided before any text is read: a payload as bytes, the pack
     * document as its Markdown model, a YAML file as its tree; it records every written file; and its census and
     * modelled kinds are the public entry's.
     */
    @Test
    void theFlowReadsEachFileInItsFormAndRecordsEveryWrittenFile(@TempDir Path temp) {
        Path pack = ExamplePacks.pack(BUILT_IN);
        Path target = temp.resolve(BUILT_IN);

        PackRoundTripResult result = PackRoundTrip.run(new PackIdentifier(BUILT_IN), pack, target);

        assertThat(result.census()).isEqualTo(PackRoundTrip.census(pack));
        assertThat(result.modelledKinds()).isEqualTo(PackRoundTrip.modelledKinds());
        assertThat(source(result, PackDocumentMarkdown.FILE)).isInstanceOf(Reading.Markdown.class);
        assertThat(source(result, PolicyYaml.FILE)).isInstanceOf(Reading.Yaml.class);
        assertThat(result.census().entrySet().stream()
                        .filter(entry -> entry.getValue().equals("asset-payload"))
                        .map(Map.Entry::getKey)
                        .toList())
                .isNotEmpty()
                .allSatisfy(path -> assertThat(source(result, path)).isInstanceOf(Reading.Bytes.class));
        assertThat(result.written())
                .as("every written file, and only those")
                .isNotEmpty()
                .allSatisfy(path -> assertThat(target.resolve(path)).isRegularFile())
                .contains(((FileOutcome.Written) result.outcomes().get(PolicyYaml.FILE)).writtenPath());
    }

    /**
     * The verdict's comparator names the manifest's gate kind and the graph shard's by literal, as {@code pack-validation}
     * cannot name the codecs: they stay the flow's own kinds.
     */
    @Test
    void theComparatorsKindNamesAreTheFlows() {
        assertThat(MeaningComparator.MANIFEST)
                .isEqualTo(PackManifestYaml.SCHEMA)
                .isEqualTo(DomainRoundTrip.MANIFEST);
        assertThat(MeaningComparator.GRAPH_SHARD).isEqualTo(DomainRoundTrip.GRAPH_SHARD);
        assertThat(PackRoundTrip.modelledKinds()).contains(MeaningComparator.MANIFEST, MeaningComparator.GRAPH_SHARD);
    }

    private static Reading source(PackRoundTripResult result, String path) {
        return ((FileOutcome.Written) result.outcomes().get(path)).source();
    }

    /**
     * #195: the flow verifies the manifest's derived fields on both sides, which the comparison skips. A writer that
     * keeps the source's hashes is red at {@code written stale}, an edited source at {@code source stale}; the faithful
     * run over the fresh fixture is green.
     */
    @Test
    void theManifestsDerivedFieldsAreVerifiedOnBothSides(@TempDir Path temp) throws IOException, URISyntaxException {
        Path fresh = PackManifestRoundTripTest.fixture(temp.resolve("fresh"));
        Path edited = PackManifestRoundTripTest.fixture(temp.resolve("edited"));
        Path tactic = edited.resolve(PackManifestRoundTripTest.TACTIC);
        Files.writeString(tactic, Files.readString(tactic) + "# edited\n");
        SerializationStrategy yaml = new YamlSerializationStrategy();

        Map<String, Optional<String>> clean = RoundTripObservation.of(
                        PackRoundTrip.run(new DomainRoundTrip(yaml), MANIFEST_PACK, fresh, temp.resolve("out/clean")))
                .observed();
        PackRoundTripResult copiedRun = PackRoundTrip.run(
                new DomainRoundTrip(new PackManifestRoundTripTest.StaleManifestWriter(yaml, fresh)),
                MANIFEST_PACK,
                fresh,
                temp.resolve("out/copied"));
        Map<String, Optional<String>> copied =
                RoundTripObservation.of(copiedRun).observed();
        Map<String, Optional<String>> sourceStale = RoundTripObservation.of(
                        PackRoundTrip.run(new DomainRoundTrip(yaml), MANIFEST_PACK, edited, temp.resolve("out/edited")))
                .observed();

        assertThat(clean).allSatisfy((path, red) -> assertThat(red).as(path).isEmpty());
        assertThat(((FileOutcome.Written) copiedRun.outcomes().get(PackFileSet.MANIFEST)).writtenStale())
                .as("the flow records the stale fields")
                .isNotEmpty();
        assertThat(copied.get(PackFileSet.MANIFEST))
                .contains(
                        "written stale: constituents{directives/018-doctrine-versioning.directive.yaml}.content_hash, "
                                + "constituents{" + PackManifestRoundTripTest.TACTIC + "}.content_hash, "
                                + "presets{presets/convenience.yaml}.content_hash, "
                                + "presets{presets/governing.yaml}.content_hash");
        assertThat(sourceStale.get(PackFileSet.MANIFEST))
                .contains("source stale: constituents{" + PackManifestRoundTripTest.TACTIC + "}.content_hash");
    }

    /**
     * Operator ruling R34 (#631): extra files in packs are not processed. The production round trip, behind a spy that
     * records what the adapter is handed, never hands it a file the census does not hold: an unmodelled file at any
     * depth, an unnamed {@code policy.yaml} and an unnamed companion, each of which maps to a modelled kind or none. It
     * hands it every file the census holds.
     */
    @Test
    void extraFilesInPacksAreNeverHandedToTheRoundTripAdapter(@TempDir Path temp) throws IOException {
        Path org = ExamplePacks.copyOf(ExamplePacks.ORG, temp.resolve("packs"));
        List<String> extras =
                List.of("qa/notes.txt", "qa/Deep/Er/NOTES.TXT", "policy.yaml", "toolguides/ghost.guide.md");
        for (String extra : extras) {
            Files.createDirectories(org.resolve(extra).getParent());
            Files.writeString(org.resolve(extra), "schema_version: '1.0'\n");
        }
        DomainRoundTrip production = new DomainRoundTrip(new YamlSerializationStrategy());
        List<Map<String, String>> handed = new ArrayList<>();
        WholePackRoundTrip spy = new WholePackRoundTrip() {
            @Override
            public Set<String> modelledKinds() {
                return production.modelledKinds();
            }

            @Override
            public Map<String, Outcome> roundTrip(String name, Path source, Path target, Map<String, String> kindOf) {
                handed.add(Map.copyOf(kindOf));
                return production.roundTrip(name, source, target, kindOf);
            }
        };

        PackRoundTripResult result =
                PackRoundTrip.run(spy, new PackIdentifier(ExamplePacks.ORG), org, temp.resolve("written"));

        assertThat(result.notHeld())
                .as("the census holds none of the extra files")
                .containsAll(extras);
        assertThat(handed).singleElement().satisfies(kindOf -> {
            assertThat(kindOf)
                    .as("no file the census does not hold")
                    .doesNotContainKeys(result.notHeld().toArray(String[]::new));
            SortedMap<String, String> held = new TreeMap<>(result.census());
            held.keySet().removeAll(result.notHeld());
            assertThat(kindOf).as("every file the census holds").containsExactlyInAnyOrderEntriesOf(held);
        });
        assertThat(result.outcomes()).doesNotContainKeys(extras.toArray(String[]::new));
        assertThat(result.written()).doesNotContainAnyElementsOf(extras);
    }

    /** The census: every file of a pack with its gate kind (#451, ADR-0044 decision 6). */
    @Nested
    class TheCensus {

        @TempDir
        Path temp;

        @Test
        void filesTakeTheirSchemaKindOrWhereTheyLive() throws IOException {
            Path pack = temp.resolve("built-in");
            for (String path : List.of(
                    "pack.yaml",
                    "README.md",
                    "tactic.graph.yaml",
                    "tactics/x/a.tactic.yaml",
                    "missions/m/mission.yaml",
                    "toolguides/GUIDE.md",
                    "toolguides/notes.txt")) {
                Files.createDirectories(pack.resolve(path).getParent());
                Files.writeString(pack.resolve(path), "x");
            }

            assertThat(WholePackCensus.of(pack))
                    .containsExactlyEntriesOf(new TreeMap<>(Map.of(
                            "missions/m/mission.yaml", "retired:*/missions/*/mission.yaml",
                            "README.md", "pack-document",
                            "pack.yaml", "pack-descriptor",
                            "tactic.graph.yaml", "graph-shard",
                            "tactics/x/a.tactic.yaml", "tactic",
                            "toolguides/GUIDE.md", "excluded:**/*.md",
                            "toolguides/notes.txt", "other:toolguides")));
        }

        @Test
        void aSymbolicLinkIsCountedNeverFollowed() throws IOException {
            Path pack = temp.resolve("built-in");
            Files.createDirectories(pack.resolve("tactics"));
            Files.writeString(pack.resolve("tactics/a.tactic.yaml"), "x");
            SymbolicLinks.create(pack.resolve("link.tactic.yaml"), pack.resolve("tactics/a.tactic.yaml"));

            assertThat(WholePackCensus.of(pack))
                    .containsEntry("link.tactic.yaml", WholePackCensus.SYMLINK)
                    .containsEntry("tactics/a.tactic.yaml", "tactic");
        }

        @Test
        void theCensusTakesAnExcludedKindFromTheTable(@TempDir Path pack) throws IOException {
            Files.createDirectories(pack.resolve("docs"));
            Files.writeString(pack.resolve("docs/notes.md"), "# note\n");
            Files.writeString(pack.resolve("notes.txt"), "stray");
            Files.createDirectories(pack.resolve("missions/x/templates"));
            Files.writeString(pack.resolve("missions/x/templates/README.md"), "# t\n");

            assertThat(WholePackCensus.of(pack))
                    .containsEntry("docs/notes.md", WholePackCensus.EXCLUDED + "**/*.md")
                    .containsEntry(
                            "missions/x/templates/README.md",
                            WholePackCensus.EXCLUDED + "*/missions/*/templates/**/README.md")
                    .containsEntry("notes.txt", "other:notes.txt");
            assertThat(WholePackCensus.kindOf(pack.getFileName().toString(), "README.md"))
                    .as("the pack document is a file of the pack, not an excluded one")
                    .isEqualTo("pack-document");
        }
    }
}
