package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.ActivationChange;
import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.DeactivatedArtifacts;
import ai.speckitty.charter.api.model.EffectiveGraphNode;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.PackPin;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectedArtifacts;
import ai.speckitty.charter.api.model.SelectionRequest;
import ai.speckitty.charter.api.model.SharedSkip;
import ai.speckitty.charter.api.model.SkippedDeactivated;
import ai.speckitty.charter.api.model.SkippedReport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * The native executable reads the activation mix-in only by reflection (ADR-0046 decision 5): without these hints it
 * writes the {@code null} members of an activation answer, or an empty body.
 */
class ActivationRuntimeHintsTest {

    /** Every DTO {@link ActivationJson} mixes the omission rule into. */
    static final List<Class<?>> MIXED_IN = List.of(
            Activation.class,
            ActivationConfig.class,
            PackPin.class,
            SelectedArtifacts.class,
            DeactivatedArtifacts.class,
            SelectionRequest.class,
            ExclusionRequest.class,
            PresetApplication.class,
            EffectiveGraphNode.class);

    /** The per-artifact and preset writes' answer, and every type it reaches, which the native executable writes. */
    static final List<Class<?>> ANSWERED = List.of(
            ActivationChange.class,
            SkippedReport.class,
            SkippedDeactivated.class,
            SharedSkip.class,
            ArtifactReference.class,
            Link.class);

    private final RuntimeHints hints = registered();

    private static RuntimeHints registered() {
        RuntimeHints hints = new RuntimeHints();
        new ActivationRuntimeHints().registerHints(hints, ActivationRuntimeHintsTest.class.getClassLoader());
        return hints;
    }

    @Test
    void theActivationJsonConfigurationImportsTheHints() {
        assertThat(ActivationJson.class.getAnnotation(ImportRuntimeHints.class))
                .isNotNull()
                .extracting(ImportRuntimeHints::value)
                .isEqualTo(new Class<?>[] {ActivationRuntimeHints.class});
    }

    @Test
    void theMixInIsVisible() {
        assertThat(RuntimeHintsPredicates.reflection().onType(ActivationJson.omitAbsentMembers()))
                .accepts(hints);
    }

    @ParameterizedTest
    @FieldSource("MIXED_IN")
    void eachMixedInDtoBindsWithItsAccessors(Class<?> dto) {
        assertThat(RuntimeHintsPredicates.reflection().onType(dto)).accepts(hints);
    }

    @ParameterizedTest
    @FieldSource("ANSWERED")
    void eachTypeOfTheWritesAnswerBindsWithItsAccessors(Class<?> dto) {
        assertThat(RuntimeHintsPredicates.reflection().onType(dto)).accepts(hints);
    }

    @Test
    void theAccessorsOfTheWritesAnswerAreRegistered() {
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(ActivationChange.class, "getSkipped"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(SharedSkip.class, "getReferencedBy"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(SkippedDeactivated.class, "getRequiredBy"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(ArtifactReference.class, "getArtifactId"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(ArtifactReference.KindEnum.class, "getValue"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(Link.class, "getHref"))
                .accepts(hints);
    }

    @Test
    void theAccessorsOfTheDocumentAreRegistered() {
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(ActivationConfig.class, "getSchemaVersion"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(PackPin.class, "getPackId"))
                .accepts(hints);
    }
}
