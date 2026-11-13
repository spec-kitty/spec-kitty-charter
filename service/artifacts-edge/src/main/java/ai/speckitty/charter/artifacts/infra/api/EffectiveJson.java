package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.model.EffectiveSkillRead;
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
 * How the reads under an activation serialize: an optional member they leave {@code null} is omitted, never written as
 * {@code null}, by the contract rule {@code NullOmissionTest} checks against the schemas. {@code EffectiveSkillRead}
 * has two optional members ({@code satisfiedBy}, {@code suggestion}) and no nullable one; {@code EffectiveAssetRead} has
 * no optional member, and the {@code Asset} and {@code Skill} members are mixed in where the pack-scoped reads mix them
 * in. The rule reaches the generated DTO only through the mix-in.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(EffectiveJson.Hints.class)
public class EffectiveJson {

    /** For Spring, which finds the configuration by component scan. */
    public EffectiveJson() {}

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that omits the {@code null} members of the skill read under an activation
     */
    @Bean
    public JacksonModule effectiveArtifactsJsonModule() {
        return new SimpleModule("charter-effective-artifacts-json")
                .setMixInAnnotation(EffectiveSkillRead.class, OmitAbsentMembers.class);
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
