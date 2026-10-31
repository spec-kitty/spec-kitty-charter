package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.DeactivatedArtifacts;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.PackPin;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectedArtifacts;
import ai.speckitty.charter.api.model.SelectionRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * How the activation documents serialize (#125, ADR-0020 D4): an absent optional member stays absent, never
 * {@code null}, and an empty list stays {@code []}. The three states of the document (absent means the default,
 * {@code []} means empty, a list means that set) rest on the difference: the generator leaves an optional list
 * {@code null} until it is set ({@code containerDefaultToNull}), and this mix-in leaves a {@code null} member out.
 * As with {@link ProblemJson}, the rule reaches the generated DTOs only through the mix-in, and only the models with an
 * optional member and no required nullable one, which {@code NullOmissionTest} checks against the schemas.
 */
@Configuration(proxyBeanMethods = false)
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
                .setMixInAnnotation(ActivationConfig.class, OmitAbsentMembers.class)
                .setMixInAnnotation(PackPin.class, OmitAbsentMembers.class)
                .setMixInAnnotation(SelectedArtifacts.class, OmitAbsentMembers.class)
                .setMixInAnnotation(DeactivatedArtifacts.class, OmitAbsentMembers.class)
                .setMixInAnnotation(SelectionRequest.class, OmitAbsentMembers.class)
                .setMixInAnnotation(ExclusionRequest.class, OmitAbsentMembers.class)
                .setMixInAnnotation(PresetApplication.class, OmitAbsentMembers.class)
                .setMixInAnnotation(Link.class, OmitAbsentMembers.class);
    }

    /** The mix-in: every {@code null} member of the target is left out of the JSON. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class OmitAbsentMembers {}
}
