package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * The artifact envelope's mix-in, split from the shared problem JSON: the configuration imports its own hints, and the
 * native executable can read the mix-in (ADR-0046 decision 5).
 */
class ArtifactJsonTest {

    @Test
    void theConfigurationImportsItsHints() {
        assertThat(ArtifactJson.class.getAnnotation(ImportRuntimeHints.class))
                .isNotNull()
                .extracting(ImportRuntimeHints::value)
                .isEqualTo(new Class<?>[] {ArtifactJson.Hints.class});
    }

    @Test
    void theMixInIsVisible() {
        RuntimeHints hints = new RuntimeHints();
        new ArtifactJson.Hints().registerHints(hints, ArtifactJsonTest.class.getClassLoader());

        assertThat(hints.reflection().typeHints())
                .extracting(hint -> hint.getType())
                .containsExactly(TypeReference.of(ArtifactJson.class.getName() + "$OmitAbsentMembers"));
    }
}
