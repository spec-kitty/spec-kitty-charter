package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.PackDocumentRead;
import ai.speckitty.charter.api.model.PackRegistration;
import ai.speckitty.charter.api.model.PackRegistrationNotices;
import ai.speckitty.charter.api.model.PackRegistrationProblem;
import ai.speckitty.charter.api.model.PackRegistrationStatus;
import ai.speckitty.charter.api.model.PackSource;
import ai.speckitty.charter.api.model.PackSourceRecord;
import ai.speckitty.charter.api.model.RefusalPack;
import ai.speckitty.charter.api.model.RefusalViolation;
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
 * How the whole-pack registration models, and the pack document read's, serialize: a member the schema neither requires nor allows to be {@code null}
 * (the outcome of a read of the stored pack, the end time of a running job) is omitted, never written as {@code null},
 * by the contract rule {@code NullOmissionTest} checks against the schemas. The rule reaches the generated DTOs only
 * through the mix-in. The status, the stored pack and the problem of a refused registration are answered by the
 * registration controller; a native executable also needs the DTOs' reflection hints, which {@link Hints} registers.
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
                .setMixInAnnotation(PackDocumentRead.class, OmitAbsentMembers.class)
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
     * What a native executable needs to write the registration answers (ADR-0046 decision 5): the mix-in is read only for
     * its class annotation, so the type alone is registered, and the DTOs a status and a stored pack reach bind through
     * their accessors, so each is registered for binding.
     */
    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            hints.reflection().registerType(OmitAbsentMembers.class);
            new BindingReflectionHintsRegistrar()
                    .registerReflectionHints(
                            hints.reflection(),
                            PackRegistrationStatus.class,
                            PackRegistration.class,
                            PackDocumentRead.class,
                            PackRegistrationNotices.class,
                            PackRegistrationProblem.class,
                            ArtifactReference.class,
                            RefusalPack.class,
                            RefusalViolation.class,
                            Link.class);
        }
    }
}
