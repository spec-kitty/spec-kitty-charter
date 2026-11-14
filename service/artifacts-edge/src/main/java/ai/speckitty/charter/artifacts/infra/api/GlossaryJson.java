package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.model.GlossaryAppliesTo;
import ai.speckitty.charter.api.model.GlossaryContext;
import ai.speckitty.charter.api.model.GlossaryContextSummary;
import ai.speckitty.charter.api.model.GlossaryContextTerm;
import ai.speckitty.charter.api.model.GlossaryTerm;
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
 * How the glossary reads serialize (#106): an optional member they leave {@code null} (a context's tags or
 * {@code appliesTo}, one list of an {@code appliesTo}, a term's examples, aliases, forms to avoid or related terms) is
 * omitted, never written as {@code null}, by the contract rule {@code NullOmissionTest} checks against the schemas.
 * None of the glossary schemas has a nullable member. The {@code Artifact} envelope is mixed in by the packs slice's
 * {@code ArtifactJson}; the rule reaches the generated DTOs only through the mix-in.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(GlossaryJson.Hints.class)
public class GlossaryJson {

    /** For Spring, which finds the configuration by component scan. */
    public GlossaryJson() {}

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that omits the {@code null} members of a glossary context, its summary, its terms and a term's
     *     read
     */
    @Bean
    public JacksonModule glossaryJsonModule() {
        return new SimpleModule("charter-glossary-json")
                .setMixInAnnotation(GlossaryContextSummary.class, OmitAbsentMembers.class)
                .setMixInAnnotation(GlossaryContext.class, OmitAbsentMembers.class)
                .setMixInAnnotation(GlossaryAppliesTo.class, OmitAbsentMembers.class)
                .setMixInAnnotation(GlossaryContextTerm.class, OmitAbsentMembers.class)
                .setMixInAnnotation(GlossaryTerm.class, OmitAbsentMembers.class);
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
