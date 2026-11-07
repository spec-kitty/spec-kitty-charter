package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.Jcs;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.ActivationDirectoryName;
import ai.speckitty.charter.activations.infra.storage.FileActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * NFR-005 and SC-003: the activation documents survive a restart of the service. The whole application boots on a
 * configuration root, writes 20 documents through the wired {@link ActivationRepository}, closes, boots again on the
 * same root and reads each one back: the files are byte-identical and the revisions equal. The test drives the
 * repository bean directly.
 */
class ActivationRestartTest {

    private static final int DOCUMENTS = 20;
    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";

    @TempDir
    Path root;

    private ConfigurableApplicationContext boot() {
        return new SpringApplicationBuilder(CharterServiceApplication.class)
                .web(WebApplicationType.NONE)
                // The guard refuses a token from the command line; a default property stands in for the environment.
                .properties("charter.worktree-token=" + TOKEN)
                .run("--charter.config-root=" + root);
    }

    private static ActivationDocument document(int n) {
        return ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("pack-" + n, "1.0." + n), PackRef.of("shared")))
                .selected(ArtifactKind.TACTIC, List.of("tactic-" + n, "shared-tactic"))
                .selected(ArtifactKind.MISSION_TYPE, List.of("software-dev"))
                .languages(List.of("java", "lang-" + n))
                .deactivated(ArtifactKind.TACTIC, List.of("tactic-" + n))
                .build()
                .document();
    }

    private Path file(ActivationId id) {
        return root.resolve(ConfigRoot.ACTIVATIONS)
                .resolve(ActivationDirectoryName.encode(id))
                .resolve("activation.json");
    }

    @Test
    void everyDocumentReadsBackByteIdenticalAfterARestart() throws IOException {
        Map<ActivationId, byte[]> written = new LinkedHashMap<>();
        Map<ActivationId, Revision> revisions = new LinkedHashMap<>();
        try (ConfigurableApplicationContext first = boot()) {
            ActivationRepository repository = first.getBean(ActivationRepository.class);
            assertThat(repository).isInstanceOf(FileActivationRepository.class);
            for (int n = 0; n < DOCUMENTS; n++) {
                ActivationId id = new ActivationId("restart-" + n);
                StoredActivation stored = repository.create(id, document(n)).orElseThrow();
                revisions.put(id, stored.revision());
                written.put(id, Files.readAllBytes(file(id)));
            }
        }

        try (ConfigurableApplicationContext second = boot()) {
            ActivationRepository repository = second.getBean(ActivationRepository.class);
            for (Map.Entry<ActivationId, byte[]> entry : written.entrySet()) {
                ActivationId id = entry.getKey();
                StoredActivation read = repository.find(id).orElseThrow();
                assertThat(read.revision()).isEqualTo(revisions.get(id));
                assertThat(Files.readAllBytes(file(id))).isEqualTo(entry.getValue());
                assertThat(Jcs.canonical(read.document().toCanonicalTree())).isEqualTo(entry.getValue());
            }
        }
        assertThat(written).hasSize(DOCUMENTS);
    }
}
