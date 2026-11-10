package ai.speckitty.charter.missions.infra.api;

import ai.speckitty.charter.api.model.MissionExpectedArtifact;
import ai.speckitty.charter.api.model.MissionPathConventions;
import ai.speckitty.charter.api.model.MissionStep;
import ai.speckitty.charter.api.model.MissionType;
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
 * How the mission-type reads serialize: an optional member they leave {@code null} is omitted, never written as
 * {@code null} (a type without content, a step without guidelines, an output without a template path or a skeleton
 * link), by the contract rule the shared web transport's {@code ProblemJson} applies, which {@code NullOmissionTest}
 * checks against the schemas. The {@code Artifact} envelope is mixed in by the packs adapter, the {@code Link} by the
 * web transport. The rule reaches the generated DTOs only through the mix-in.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(MissionJson.Hints.class)
public class MissionJson {

    /** For Spring, which finds the configuration by component scan. */
    public MissionJson() {}

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that omits the {@code null} members of the mission-type DTOs
     */
    @Bean
    public JacksonModule missionJsonModule() {
        return new SimpleModule("charter-mission-json")
                .setMixInAnnotation(MissionType.class, OmitAbsentMembers.class)
                .setMixInAnnotation(MissionPathConventions.class, OmitAbsentMembers.class)
                .setMixInAnnotation(MissionStep.class, OmitAbsentMembers.class)
                .setMixInAnnotation(MissionExpectedArtifact.class, OmitAbsentMembers.class);
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
