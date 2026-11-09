package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.MissionStepContractGate;
import ai.speckitty.charter.api.model.MissionStepContractInput;
import ai.speckitty.charter.api.model.MissionStepContractStep;
import ai.speckitty.charter.api.model.ReferencedArtifact;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * How the {@code Artifact} envelope serializes: the kind members its kind does not name are omitted, never written as
 * {@code null}, and so are the optional members of a mission step contract's steps, inputs and gates, by the same
 * contract rule the shared web transport's {@code ProblemJson} applies to the problem and link bodies (a member the
 * schema neither requires nor allows to be {@code null} is omitted, which {@code NullOmissionTest} checks against the
 * schemas). The rule reaches the generated DTOs only through the mix-in.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ArtifactJson.Hints.class)
public class ArtifactJson {

    /** For Spring, which finds the configuration by component scan. */
    public ArtifactJson() {}

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that omits the {@code null} members of the artifact DTOs and of a step contract's parts
     */
    @Bean
    public JacksonModule artifactJsonModule() {
        return new SimpleModule("charter-artifact-json")
                .setMixInAnnotation(Artifact.class, OmitAbsentMembers.class)
                .setMixInAnnotation(ReferencedArtifact.class, OmitAbsentMembers.class)
                .setMixInAnnotation(MissionStepContractStep.class, OmitAbsentMembers.class)
                .setMixInAnnotation(MissionStepContractInput.class, OmitAbsentMembers.class)
                .setMixInAnnotation(MissionStepContractGate.class, OmitAbsentMembers.class);
    }

    /** The mix-in: every {@code null} member of the target is left out of the JSON. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class OmitAbsentMembers {}

    /**
     * What a native executable needs to apply the mix-in (ADR-0046 decision 5): it is read only for its class
     * annotation, so the type alone is registered.
     */
    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            hints.reflection().registerType(OmitAbsentMembers.class);
        }
    }
}
