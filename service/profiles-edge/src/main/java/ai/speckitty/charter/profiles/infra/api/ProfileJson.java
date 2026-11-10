package ai.speckitty.charter.profiles.infra.api;

import ai.speckitty.charter.api.model.AgentProfile;
import ai.speckitty.charter.api.model.AgentProfileArtifactReference;
import ai.speckitty.charter.api.model.AgentProfileCollaboration;
import ai.speckitty.charter.api.model.AgentProfileDirectiveReference;
import ai.speckitty.charter.api.model.AgentProfileExcluding;
import ai.speckitty.charter.api.model.AgentProfileFieldValues;
import ai.speckitty.charter.api.model.AgentProfileModeDefault;
import ai.speckitty.charter.api.model.AgentProfileSelfReviewProtocol;
import ai.speckitty.charter.api.model.AgentProfileSelfReviewStep;
import ai.speckitty.charter.api.model.AgentProfileSpecialization;
import ai.speckitty.charter.api.model.AgentProfileSpecializationContext;
import ai.speckitty.charter.api.model.EffectiveAgentProfileRead;
import ai.speckitty.charter.api.model.EffectiveGovernanceEntry;
import ai.speckitty.charter.api.model.EffectiveGovernanceSuggestion;
import ai.speckitty.charter.api.model.PackGovernanceEntry;
import ai.speckitty.charter.api.model.PackGovernanceSuggestion;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * How the profile models serialize, by the contract rule the other models follow (a member the schema neither requires
 * nor allows to be {@code null} is omitted, a required nullable member is written as {@code null}; {@code
 * NullOmissionTest} checks it against the schemas). {@code AgentProfile} and its specialization hold both kinds of
 * member, so a class-wide rule would drop the {@code null} of a required nullable one ({@code model}, {@code excluding}):
 * the mix-in marks the optional members one by one. {@link PackGovernanceSuggestion} has optional members only, and so do
 * the answer of the read under an activation and its entries ({@code satisfiedBy} and {@code suggestion} are the optional
 * members, as the activation set's own reads have them; the activation adapter's JSON module owns the suggestion and
 * the reference they hold). The rule
 * reaches the generated DTOs only through the mix-ins, which a native executable reads by reflection ({@link Hints}).
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ProfileJson.Hints.class)
public class ProfileJson {

    /** For Spring, which finds the configuration by component scan. */
    public ProfileJson() {}

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that omits the optional members of the profile models when they are {@code null}
     */
    @Bean
    public JacksonModule profileJsonModule() {
        return new SimpleModule("charter-profile-json")
                .setMixInAnnotation(AgentProfile.class, OptionalProfileMembers.class)
                .setMixInAnnotation(AgentProfileSpecialization.class, OptionalSpecializationMembers.class)
                .setMixInAnnotation(PackGovernanceSuggestion.class, OmitAbsentMembers.class)
                .setMixInAnnotation(EffectiveAgentProfileRead.class, OmitAbsentMembers.class)
                .setMixInAnnotation(EffectiveGovernanceEntry.class, OmitAbsentMembers.class)
                .setMixInAnnotation(EffectiveGovernanceSuggestion.class, OmitAbsentMembers.class);
    }

    /**
     * The members of the profile the pack schema defaults: left out when the profile does not declare them. A mix-in's
     * methods are matched to the model's accessors by name and carry only the annotation, so none is called.
     */
    @SuppressWarnings("UnusedMethod")
    private abstract static class OptionalProfileMembers {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract @Nullable String getDescription();

        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract @Nullable String getSchemaVersion();

        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract @Nullable Integer getRoutingPriority();

        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract @Nullable Integer getMaxConcurrentTasks();

        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract @Nullable Boolean getSentinel();

        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract @Nullable String getInitializationDeclaration();
    }

    /** The prose members of the specialization the pack schema defaults; matched by name, never called. */
    @SuppressWarnings("UnusedMethod")
    private abstract static class OptionalSpecializationMembers {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract @Nullable String getSecondaryAwareness();

        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract @Nullable String getAvoidanceBoundary();

        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract @Nullable String getSuccessDefinition();
    }

    /** The mix-in of a model whose members are all optional: every {@code null} member is left out. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class OmitAbsentMembers {}

    /**
     * What a native executable needs (ADR-0046 decision 5): the DTOs of the profile read bind through their accessors,
     * and the mix-ins are read by reflection, for their annotations only.
     */
    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            new BindingReflectionHintsRegistrar()
                    .registerReflectionHints(
                            hints.reflection(),
                            AgentProfile.class,
                            AgentProfileSpecialization.class,
                            AgentProfileCollaboration.class,
                            AgentProfileModeDefault.class,
                            AgentProfileSpecializationContext.class,
                            AgentProfileDirectiveReference.class,
                            AgentProfileArtifactReference.class,
                            AgentProfileSelfReviewProtocol.class,
                            AgentProfileSelfReviewStep.class,
                            AgentProfileExcluding.class,
                            AgentProfileFieldValues.class,
                            PackGovernanceEntry.class,
                            PackGovernanceSuggestion.class,
                            EffectiveAgentProfileRead.class,
                            EffectiveGovernanceEntry.class,
                            EffectiveGovernanceSuggestion.class);
            hints.reflection()
                    .registerType(OptionalProfileMembers.class)
                    .registerType(OptionalSpecializationMembers.class)
                    .registerType(OmitAbsentMembers.class);
        }
    }
}
