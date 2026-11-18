package ai.speckitty.charter.routing.infra.api;

import ai.speckitty.charter.api.model.ModelRecommendation;
import ai.speckitty.charter.api.model.ModelRecommendationCandidate;
import ai.speckitty.charter.api.model.ModelRecommendationCatalog;
import ai.speckitty.charter.api.model.ModelRecommendationTier;
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
 * How the recommendation models serialize, by the contract rule the other models follow (a member the schema neither
 * requires nor allows to be {@code null} is omitted; {@code NullOmissionTest} checks it against the schemas). The
 * answer's optional members are {@code tierConstraint} and {@code modelTier}, the tier's is {@code offer}, and a
 * candidate's are the members of the other source ({@code score}, {@code fitScore}, {@code costTier},
 * {@code withinTierConstraint}, {@code effort}) and its own optional ones ({@code rationale}, {@code withinModelTier},
 * {@code effort}); none of them is nullable, so each is left out when absent. The rule reaches the generated DTOs only
 * through the mix-ins, which a native executable reads by reflection ({@link Hints}).
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(RoutingJson.Hints.class)
public class RoutingJson {

    /** For Spring, which finds the configuration by component scan. */
    public RoutingJson() {}

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that omits the optional members of the recommendation models when they are {@code null}
     */
    @Bean
    public JacksonModule routingJsonModule() {
        return new SimpleModule("charter-routing-json")
                .setMixInAnnotation(ModelRecommendation.class, OmitAbsentMembers.class)
                .setMixInAnnotation(ModelRecommendationTier.class, OmitAbsentMembers.class)
                .setMixInAnnotation(ModelRecommendationCandidate.class, OmitAbsentMembers.class);
    }

    /** The mix-in of a model whose non-required members are all optional and never null: absent ones are left out. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class OmitAbsentMembers {}

    /**
     * What a native executable needs (ADR-0046 decision 5): the DTOs of the recommendation bind through their
     * accessors, and the mix-in is read by reflection, for its annotation only.
     */
    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            new BindingReflectionHintsRegistrar()
                    .registerReflectionHints(
                            hints.reflection(),
                            ModelRecommendation.class,
                            ModelRecommendationCatalog.class,
                            ModelRecommendationTier.class,
                            ModelRecommendationCandidate.class);
            hints.reflection().registerType(OmitAbsentMembers.class);
        }
    }
}
