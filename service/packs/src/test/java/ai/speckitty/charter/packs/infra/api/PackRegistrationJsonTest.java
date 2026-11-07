package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * The registration models' mix-in, split from the shared problem JSON: the configuration imports its own hints, and the
 * native executable can read the mix-in (ADR-0046 decision 5).
 */
class PackRegistrationJsonTest {

    @Test
    void theConfigurationImportsItsHints() {
        assertThat(PackRegistrationJson.class.getAnnotation(ImportRuntimeHints.class))
                .isNotNull()
                .extracting(ImportRuntimeHints::value)
                .isEqualTo(new Class<?>[] {PackRegistrationJson.Hints.class});
    }

    @Test
    void theMixInIsVisible() {
        RuntimeHints hints = new RuntimeHints();
        new PackRegistrationJson.Hints().registerHints(hints, PackRegistrationJsonTest.class.getClassLoader());

        assertThat(hints.reflection().typeHints())
                .extracting(hint -> hint.getType())
                .containsExactly(TypeReference.of(PackRegistrationJson.class.getName() + "$OmitAbsentMembers"));
    }
}
