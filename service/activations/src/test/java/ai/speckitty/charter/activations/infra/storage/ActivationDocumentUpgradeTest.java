package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.Jcs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * The upgrade of a stored {@code schemaVersion: 1} document to version 2 (IC-20, FR-041): {@code missionTypes} become
 * {@code selected.mission_type}, every selection is kept and {@code activatedKinds} is discarded, whatever it named
 * (decision {@code DM-01M6RMJHDKNTXAM055Q8WR6SDN}: the gate was never enforced). The upgraded document is the one a
 * version 2 file of the same selections holds, with the same revision, so the in-force set does not move.
 */
class ActivationDocumentUpgradeTest {

    private static final ActivationId ID = new ActivationId("upgrade-id");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static Stream<Arguments> versionOneDocuments() {
        return Stream.of(
                Arguments.of("the minimal document", "{\"schemaVersion\":1}", "{\"schemaVersion\":2}"),
                Arguments.of(
                        "an absent activatedKinds keeps every selection",
                        "{\"schemaVersion\":1,\"selected\":{\"directive\":[\"D1\"],\"tactic\":[\"T1\"]}}",
                        "{\"schemaVersion\":2,\"selected\":{\"directive\":[\"D1\"],\"tactic\":[\"T1\"]}}"),
                Arguments.of(
                        "an empty activatedKinds keeps every selection",
                        "{\"activatedKinds\":[],\"schemaVersion\":1,\"selected\":{\"directive\":[\"D1\"],"
                                + "\"tactic\":[\"T1\"]}}",
                        "{\"schemaVersion\":2,\"selected\":{\"directive\":[\"D1\"],\"tactic\":[\"T1\"]}}"),
                Arguments.of(
                        "a narrowing activatedKinds keeps every selection",
                        "{\"activatedKinds\":[\"directive\"],\"schemaVersion\":1,\"selected\":{\"directive\":[\"D1\"],"
                                + "\"skill\":[\"land-pr\"],\"tactic\":[\"T1\"]}}",
                        "{\"schemaVersion\":2,\"selected\":{\"directive\":[\"D1\"],\"skill\":[\"land-pr\"],"
                                + "\"tactic\":[\"T1\"]}}"),
                Arguments.of(
                        "missionTypes become selected.mission_type",
                        "{\"missionTypes\":[\"research\",\"software-dev\"],\"schemaVersion\":1,"
                                + "\"selected\":{\"directive\":[\"SD\"]}}",
                        "{\"schemaVersion\":2,\"selected\":{\"directive\":[\"SD\"],"
                                + "\"mission_type\":[\"research\",\"software-dev\"]}}"),
                Arguments.of(
                        "an empty missionTypes and an empty allow list are none, stored absent",
                        "{\"activatedKinds\":[],\"missionTypes\":[],\"schemaVersion\":1,\"selected\":{\"tactic\":[]}}",
                        "{\"schemaVersion\":2}"),
                Arguments.of(
                        "packs, deny lists and languages are kept as they are",
                        "{\"activatedKinds\":[\"tactic\"],\"deactivated\":{\"directive\":[\"D9\"]},\"languages\":[],"
                                + "\"missionTypes\":[\"software-dev\"],\"packs\":[{\"packId\":\"zeta\","
                                + "\"version\":\"1.0\"},{\"packId\":\"alpha\"}],\"schemaVersion\":1}",
                        "{\"deactivated\":{\"directive\":[\"D9\"]},\"languages\":[],\"packs\":[{\"packId\":\"zeta\","
                                + "\"version\":\"1.0\"},{\"packId\":\"alpha\"}],\"schemaVersion\":2,"
                                + "\"selected\":{\"mission_type\":[\"software-dev\"]}}"),
                Arguments.of(
                        "a version 1 file need not be in canonical form",
                        "{\"schemaVersion\": 1, \"packs\": [{\"packId\": \"main\"}], \"missionTypes\": [\"software-dev\"],"
                                + " \"selected\": {\"directive\": [\"SD\"]}}",
                        "{\"packs\":[{\"packId\":\"main\"}],\"schemaVersion\":2,"
                                + "\"selected\":{\"directive\":[\"SD\"],\"mission_type\":[\"software-dev\"]}}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versionOneDocuments")
    void aVersionOneDocumentReadsAsItsVersionTwoUpgrade(String name, String versionOne, String versionTwo) {
        ActivationDocument upgraded = ActivationFileCodec.decode(ID, utf8(versionOne));

        assertThat(upgraded.schemaVersion()).isEqualTo(2);
        assertThat(text(ActivationFileCodec.encode(upgraded))).isEqualTo(versionTwo);
        ActivationDocument stored = ActivationFileCodec.decode(ID, utf8(versionTwo));
        assertThat(upgraded).isEqualTo(stored);
        assertThat(upgraded.revision()).isEqualTo(stored.revision());
    }

    @Test
    void theCorpusVersionOneDocumentUpgradesToTheMinimalDocument() throws IOException {
        ActivationDocument upgraded = ActivationFileCodec.decode(ID, corpusFile("invalid/02-schema-version-one.json"));

        assertThat(text(ActivationFileCodec.encode(upgraded))).isEqualTo("{\"schemaVersion\":2}");
    }

    /**
     * The corpus pins {@code activatedKinds} and {@code missionTypes} as refused on a version 2 document: the store
     * refuses them too, and the same members on a version 1 document are upgraded.
     */
    @Test
    void theCorpusRemnantMembersAreRefusedAtVersionTwoAndUpgradedAtVersionOne() throws IOException {
        byte[] activatedKinds = corpusFile("invalid/14-activated-kinds-left-out.json");
        byte[] missionTypes = corpusFile("invalid/18-mission-types-left-out.json");

        assertThatThrownBy(() -> ActivationFileCodec.decode(ID, activatedKinds))
                .isInstanceOf(ActivationStorageException.class);
        assertThatThrownBy(() -> ActivationFileCodec.decode(ID, missionTypes))
                .isInstanceOf(ActivationStorageException.class);
        assertThat(text(ActivationFileCodec.encode(ActivationFileCodec.decode(ID, atVersionOne(activatedKinds)))))
                .isEqualTo("{\"schemaVersion\":2}");
        assertThat(text(ActivationFileCodec.encode(ActivationFileCodec.decode(ID, atVersionOne(missionTypes)))))
                .isEqualTo("{\"schemaVersion\":2,\"selected\":{\"mission_type\":[\"software-dev\"]}}");
    }

    /** A corpus document with its {@code schemaVersion} set to 1, in canonical form. */
    private static byte[] atVersionOne(byte[] document) {
        Map<String, Object> tree = new LinkedHashMap<>();
        Map<?, ?> parsed = JSON.readValue(document, Map.class);
        parsed.forEach((name, value) -> tree.put(String.valueOf(name), value));
        tree.put("schemaVersion", 1);
        return Jcs.canonical(tree);
    }

    private static byte[] corpusFile(String name) throws IOException {
        return Files.readAllBytes(corpus().resolve(name));
    }

    /** The shared {@code ActivationConfig} corpus, found from the working directory up to the repository root. */
    private static Path corpus() {
        Path relative = Path.of("contracts", "offering", "corpus", "ActivationConfig");
        Optional<Path> at = Optional.of(Path.of("").toAbsolutePath());
        while (at.isPresent()) {
            Path candidate = at.orElseThrow().resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            at = Optional.ofNullable(at.orElseThrow().getParent());
        }
        throw new AssertionError(
                "the ActivationConfig corpus is not above " + Path.of("").toAbsolutePath());
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
