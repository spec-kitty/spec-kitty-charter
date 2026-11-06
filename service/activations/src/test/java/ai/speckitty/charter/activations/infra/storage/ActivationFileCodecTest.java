package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.Jcs;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ActivationFileCodecTest {

    private static final ActivationId ID = new ActivationId("codec-id");

    /** A document with every member: pinned and unpinned packs, both list objects, mission types and {@code []} languages. */
    private static ActivationDocument everyMember() {
        return ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("zeta", "2.0.0"), PackRef.of("alpha")))
                .languages(List.of())
                .selected(ArtifactKind.TACTIC, List.of("b-tactic", "a-tactic"))
                .selected(ArtifactKind.MISSION_TYPE, List.of("software-dev", "research"))
                .deactivated(ArtifactKind.DIRECTIVE, List.of("DIRECTIVE_001"))
                .deactivated(ArtifactKind.MISSION_TYPE, List.of("documentation"))
                .build()
                .document();
    }

    @Test
    void encodesTheJcsBytesOfTheCanonicalTree() {
        ActivationDocument document = everyMember();

        assertThat(ActivationFileCodec.encode(document)).isEqualTo(Jcs.canonical(document.toCanonicalTree()));
    }

    @Test
    void writesSchemaVersionTwo() {
        assertThat(text(ActivationFileCodec.encode(
                        ActivationDocument.builder().build().document())))
                .isEqualTo("{\"schemaVersion\":2}");
        assertThat(text(ActivationFileCodec.encode(everyMember())))
                .isEqualTo("{\"deactivated\":{\"directive\":[\"DIRECTIVE_001\"],\"mission_type\":[\"documentation\"]},"
                        + "\"languages\":[],\"packs\":[{\"packId\":\"zeta\",\"version\":\"2.0.0\"},{\"packId\":\"alpha\"}],"
                        + "\"schemaVersion\":2,\"selected\":{\"mission_type\":[\"research\",\"software-dev\"],"
                        + "\"tactic\":[\"a-tactic\",\"b-tactic\"]}}");
    }

    @Test
    void anEmptyAllowListAndAnAbsentOneEncodeToTheSameBytesAndRevision() {
        ActivationDocument absent = ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D1"))
                .build()
                .document();
        ActivationDocument empty = ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D1"))
                .selected(ArtifactKind.SKILL, List.of())
                .selected(ArtifactKind.MISSION_TYPE, List.of())
                .build()
                .document();

        assertThat(ActivationFileCodec.encode(empty)).isEqualTo(ActivationFileCodec.encode(absent));
        assertThat(Revision.of(ActivationFileCodec.encode(empty))).isEqualTo(absent.revision());
    }

    @Test
    void decodesWhatItEncodesToAnEqualDocumentWithTheSameRevision() {
        ActivationDocument document = everyMember();

        ActivationDocument read = ActivationFileCodec.decode(ID, ActivationFileCodec.encode(document));

        assertThat(read).isEqualTo(document);
        assertThat(read.revision()).isEqualTo(document.revision());
        assertThat(ActivationFileCodec.encode(read)).isEqualTo(ActivationFileCodec.encode(document));
    }

    @Test
    void decodesTheMinimalDocument() {
        ActivationDocument minimal = ActivationDocument.builder().build().document();

        assertThat(ActivationFileCodec.decode(ID, utf8("{\"schemaVersion\":2}")))
                .isEqualTo(minimal);
    }

    @Test
    void decodesAVersionOneDocumentAsItsVersionTwoUpgrade() {
        ActivationDocument read = ActivationFileCodec.decode(
                ID,
                utf8("{\"activatedKinds\":[\"directive\"],\"missionTypes\":[\"software-dev\"],"
                        + "\"schemaVersion\":1,\"selected\":{\"directive\":[\"SD\"],\"tactic\":[\"T1\"]}}"));

        assertThat(read.schemaVersion()).isEqualTo(2);
        assertThat(text(ActivationFileCodec.encode(read)))
                .isEqualTo("{\"schemaVersion\":2,\"selected\":{\"directive\":[\"SD\"],"
                        + "\"mission_type\":[\"software-dev\"],\"tactic\":[\"T1\"]}}");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "not json",
                "[]",
                "{\"schemaVersion\":2",
                "{}",
                "{\"schemaVersion\":\"2\"}",
                "{\"schemaVersion\":0}",
                "{\"schemaVersion\":3}",
                "{\"packs\":[],\"schemaVersion\":2}",
                "{\"packs\":[{\"packId\":\"a\",\"packId2\":\"b\"}],\"schemaVersion\":2}",
                "{\"packs\":[{\"packId\":\"Not A Slug\"}],\"schemaVersion\":2}",
                "{\"packs\":[{\"packId\":\"a\"},{\"packId\":\"a\"}],\"schemaVersion\":2}",
                "{\"activatedKinds\":[],\"schemaVersion\":2}",
                "{\"missionTypes\":[],\"schemaVersion\":2}",
                "{\"schemaVersion\":2,\"selected\":{\"no_such_kind\":[\"x\"]}}",
                "{\"schemaVersion\":2,\"selected\":{\"template\":[\"x\"]}}",
                "{\"deactivated\":{\"tactic\":[]},\"schemaVersion\":2}",
                "{\"schemaVersion\":2,\"selected\":{\"tactic\":[]}}",
                "{\"schemaVersion\":2,\"selected\":{}}",
                "{\"languages\":[1],\"schemaVersion\":2}",
                "{\"schemaVersion\":2,\"surprise\":true}",
                "{ \"schemaVersion\": 2 }",
                "{\"languages\":[\"b\",\"a\"],\"schemaVersion\":2}"
            })
    void refusesAFileThatIsNotACanonicalVersionTwoDocumentAndNamesTheId(String text) {
        assertThatThrownBy(() -> ActivationFileCodec.decode(ID, utf8(text)))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining("codec-id");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"schemaVersion\":1,\"surprise\":true}",
                "{\"contextActivations\":[],\"schemaVersion\":1}",
                "{\"packs\":[],\"schemaVersion\":1}",
                "{\"packs\":[{\"packId\":\"a\"},{\"packId\":\"a\"}],\"schemaVersion\":1}",
                "{\"activatedKinds\":[\"no_such_kind\"],\"schemaVersion\":1}",
                "{\"activatedKinds\":[\"mission_type\"],\"schemaVersion\":1}",
                "{\"activatedKinds\":\"tactic\",\"schemaVersion\":1}",
                "{\"missionTypes\":\"software-dev\",\"schemaVersion\":1}",
                "{\"schemaVersion\":1,\"selected\":{\"mission_type\":[\"x\"]}}",
                "{\"deactivated\":{\"mission_type\":[\"x\"]},\"schemaVersion\":1}",
                "{\"deactivated\":{\"tactic\":[]},\"schemaVersion\":1}",
                "{\"languages\":[1],\"schemaVersion\":1}"
            })
    void refusesAVersionOneFileThatBreaksTheVersionOneShape(String text) {
        assertThatThrownBy(() -> ActivationFileCodec.decode(ID, utf8(text)))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining("codec-id");
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
