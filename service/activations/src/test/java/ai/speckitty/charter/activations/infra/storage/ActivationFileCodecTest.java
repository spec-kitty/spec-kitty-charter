package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.Jcs;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ActivationFileCodecTest {

    private static final ActivationId ID = new ActivationId("codec-id");

    /** A document with every member: pinned and unpinned packs, both list objects, and {@code []} kept. */
    private static ActivationDocument everyMember() {
        return ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("zeta", "2.0.0"), PackRef.of("alpha")))
                .activatedKinds(List.of(ArtifactKind.TACTIC, ArtifactKind.DIRECTIVE))
                .missionTypes(List.of("software-dev", "research"))
                .languages(List.of())
                .selected(ArtifactKind.TACTIC, List.of("b-tactic", "a-tactic"))
                .selected(ArtifactKind.STYLEGUIDE, List.of())
                .deactivated(ArtifactKind.DIRECTIVE, List.of("DIRECTIVE_001"))
                .build()
                .document();
    }

    @Test
    void encodesTheJcsBytesOfTheCanonicalTree() {
        ActivationDocument document = everyMember();

        assertThat(ActivationFileCodec.encode(document)).isEqualTo(Jcs.canonical(document.toCanonicalTree()));
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

        assertThat(ActivationFileCodec.decode(ID, utf8("{\"schemaVersion\":1}")))
                .isEqualTo(minimal);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "not json",
                "[]",
                "{\"schemaVersion\":1",
                "{}",
                "{\"schemaVersion\":\"1\"}",
                "{\"schemaVersion\":2}",
                "{\"packs\":[],\"schemaVersion\":1}",
                "{\"packs\":[{\"packId\":\"a\",\"packId2\":\"b\"}],\"schemaVersion\":1}",
                "{\"packs\":[{\"packId\":\"Not A Slug\"}],\"schemaVersion\":1}",
                "{\"packs\":[{\"packId\":\"a\"},{\"packId\":\"a\"}],\"schemaVersion\":1}",
                "{\"activatedKinds\":[\"no_such_kind\"],\"schemaVersion\":1}",
                "{\"activatedKinds\":[\"mission_type\"],\"schemaVersion\":1}",
                "{\"activatedKinds\":\"tactic\",\"schemaVersion\":1}",
                "{\"deactivated\":{\"tactic\":[]},\"schemaVersion\":1}",
                "{\"languages\":[1],\"schemaVersion\":1}",
                "{\"schemaVersion\":1,\"surprise\":true}",
                "{ \"schemaVersion\": 1 }",
                "{\"languages\":[\"b\",\"a\"],\"schemaVersion\":1}"
            })
    void refusesAFileThatIsNotACanonicalDocumentAndNamesTheId(String text) {
        assertThatThrownBy(() -> ActivationFileCodec.decode(ID, utf8(text)))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining("codec-id");
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
