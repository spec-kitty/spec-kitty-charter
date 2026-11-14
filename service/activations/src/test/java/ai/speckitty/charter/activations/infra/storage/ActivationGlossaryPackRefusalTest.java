package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A stored version 2 document that holds the replaced {@code glossary_pack} member is refused, not upgraded and not
 * ignored (research R2, DM-01M7B1Q1HW2KDEEW3MKCK2A2PT; FR-005, C-001): {@code glossary} is the kind's only token. The
 * same document with {@code glossary} is the positive control. The old member appears here only because this is a
 * refusal test.
 */
@DisplayName("a stored activation holding glossary_pack is refused; the same document with glossary loads")
class ActivationGlossaryPackRefusalTest {

    private static final ActivationId ID = new ActivationId("glossary-activation");

    @TempDir
    Path temp;

    private FileActivationRepository storing(String json) throws IOException {
        Path root = temp.resolve("activations");
        Path file = root.resolve(ActivationDirectoryName.encode(ID)).resolve("activation.json");
        Files.createDirectories(file.getParent());
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        return new FileActivationRepository(root);
    }

    /** The document in canonical form (RFC 8785): its members in code-point order, so deactivated before schemaVersion. */
    private static String document(String list, String token) {
        String member = "\"" + list + "\":{\"" + token + "\":[\"x\"]}";
        String version = "\"schemaVersion\":2";
        return "{" + (list.compareTo("schemaVersion") < 0 ? member + "," + version : version + "," + member) + "}";
    }

    @ParameterizedTest(name = "{0}.glossary_pack")
    @ValueSource(strings = {"selected", "deactivated"})
    @DisplayName("a stored v2 document holding glossary_pack is an ActivationStorageException naming the activation")
    void theOldMemberIsRefused(String list) throws IOException {
        FileActivationRepository store = storing(document(list, "glossary_pack"));

        assertThatThrownBy(() -> store.find(ID))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining(ID.value())
                .hasMessageContaining("is not an activation document")
                .rootCause()
                .hasMessageContaining("glossary_pack");
        assertThat(store.list())
                .as("the listing leaves the unreadable document out")
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}.glossary")
    @ValueSource(strings = {"selected", "deactivated"})
    @DisplayName("positive control: the same document with glossary loads, holding the context id")
    void theCanonicalMemberLoads(String list) throws IOException {
        String json = document(list, "glossary");
        FileActivationRepository store = storing(json);

        ActivationDocument document =
                store.find(ID).map(StoredActivation::document).orElseThrow();

        assertThat(list.equals("selected") ? document.selected() : document.deactivated())
                .containsOnlyKeys(ArtifactKind.GLOSSARY)
                .hasEntrySatisfying(
                        ArtifactKind.GLOSSARY, ids -> assertThat(ids).containsExactlyElementsOf(List.of("x")));
        assertThat(new String(ActivationFileCodec.encode(document), StandardCharsets.UTF_8))
                .as("the control is the canonical form, so only the member differs from the refused document")
                .isEqualTo(json);
    }
}
