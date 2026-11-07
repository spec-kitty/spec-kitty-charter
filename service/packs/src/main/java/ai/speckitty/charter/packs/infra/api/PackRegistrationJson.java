package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.PackRegistration;
import ai.speckitty.charter.api.model.PackRegistrationNotices;
import ai.speckitty.charter.api.model.PackRegistrationProblem;
import ai.speckitty.charter.api.model.PackRegistrationStatus;
import ai.speckitty.charter.api.model.PackSource;
import ai.speckitty.charter.api.model.PackSourceRecord;
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
 * How the whole-pack registration models serialize: a member the schema neither requires nor allows to be {@code null}
 * (the outcome of a read of the stored pack, the end time of a running job) is omitted, never written as {@code null},
 * by the contract rule {@code NullOmissionTest} checks against the schemas. The rule reaches the generated DTOs only
 * through the mix-in. The operations that answer these models are reserved until the registration service is built; the
 * mix-in is here because the check covers every generated model.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(PackRegistrationJson.Hints.class)
public class PackRegistrationJson {

    /** For Spring, which finds the configuration by component scan. */
    public PackRegistrationJson() {}

    /**
     * The module Spring Boot adds to the application's JSON mapper.
     *
     * @return a module that omits the {@code null} members of the registration DTOs
     */
    @Bean
    public JacksonModule packRegistrationJsonModule() {
        return new SimpleModule("charter-pack-registration-json")
                .setMixInAnnotation(PackRegistration.class, OmitAbsentMembers.class)
                .setMixInAnnotation(PackRegistrationNotices.class, OmitAbsentMembers.class)
                .setMixInAnnotation(PackRegistrationProblem.class, OmitAbsentMembers.class)
                .setMixInAnnotation(PackRegistrationStatus.class, OmitAbsentMembers.class)
                .setMixInAnnotation(PackSource.class, OmitAbsentMembers.class)
                .setMixInAnnotation(PackSourceRecord.class, OmitAbsentMembers.class);
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
