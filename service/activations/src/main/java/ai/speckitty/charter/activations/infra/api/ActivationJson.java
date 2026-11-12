package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.ActivationChange;
import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.DeactivatedArtifacts;
import ai.speckitty.charter.api.model.EffectiveArtifact;
import ai.speckitty.charter.api.model.EffectiveArtifactRead;
import ai.speckitty.charter.api.model.EffectiveArtifactSuggestion;
import ai.speckitty.charter.api.model.EffectiveContextArtifact;
import ai.speckitty.charter.api.model.EffectiveGraphNode;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.PackPin;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectedArtifacts;
import ai.speckitty.charter.api.model.SelectionRequest;
import ai.speckitty.charter.api.model.SkippedReport;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * How the activation documents serialize (#125, ADR-0020 D4): an absent optional member stays absent, never
 * {@code null}, and an empty list stays {@code []}. The three states of the document (absent means the default,
 * {@code []} means empty, a list means that set) rest on the difference: the generator leaves an optional list
 * {@code null} until it is set ({@code containerDefaultToNull}), and this mix-in leaves a {@code null} member out.
 * As with the shared web transport's {@code ProblemJson}, the rule reaches the generated DTOs only through the mix-in,
 * and only the models with an optional member and no required nullable one, which {@code NullOmissionTest} checks
 * against the schemas. The hypermedia {@code Link} is mixed in once, by {@code ProblemJson}, for every slice. The
 * native executable reads the mix-in by reflection, so {@link ActivationRuntimeHints} registers it and the DTOs it
 * reaches.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ActivationRuntimeHints.class)
public class ActivationJson {

    /** For Spring, which finds the configuration by component scan. */
    public ActivationJson() {}

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that omits the {@code null} members of the activation DTOs
     */
    @Bean
    public JacksonModule activationJsonModule() {
        return new SimpleModule("charter-activation-json")
                .setMixInAnnotation(Activation.class, OmitAbsentMembers.class)
                .setMixInAnnotation(ActivationConfig.class, OmitAbsentMembers.class)
                .setMixInAnnotation(PackPin.class, OmitAbsentMembers.class)
                .setMixInAnnotation(SelectedArtifacts.class, OmitAbsentMembers.class)
                .setMixInAnnotation(DeactivatedArtifacts.class, OmitAbsentMembers.class)
                .setMixInAnnotation(SelectionRequest.class, OmitAbsentMembers.class)
                .setMixInAnnotation(ExclusionRequest.class, OmitAbsentMembers.class)
                .setMixInAnnotation(PresetApplication.class, OmitAbsentMembers.class)
                .setMixInAnnotation(ActivationChange.class, OmitAbsentMembers.class)
                .setMixInAnnotation(EffectiveArtifact.class, OmitAbsentMembers.class)
                .setMixInAnnotation(EffectiveArtifactRead.class, OmitAbsentMembers.class)
                .setMixInAnnotation(EffectiveArtifactSuggestion.class, OmitAbsentMembers.class)
                .setMixInAnnotation(EffectiveContextArtifact.class, OmitAbsentMembers.class)
                .setMixInAnnotation(EffectiveGraphNode.class, OmitAbsentMembers.class)
                .setMixInAnnotation(SkippedReport.class, OmitAbsentMembers.class);
    }

    /**
     * The mix-in, for the native executable's hints ({@link ActivationRuntimeHints}).
     *
     * @return the mix-in class
     */
    static Class<?> omitAbsentMembers() {
        return OmitAbsentMembers.class;
    }

    /** The mix-in: every {@code null} member of the target is left out of the JSON. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class OmitAbsentMembers {}
}
