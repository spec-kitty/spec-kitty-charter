package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.tngtech.archunit.core.domain.JavaClass;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Every Jackson mix-in of the service reaches a native executable (ADR-0046 decision 5): the class that declares it
 * imports runtime hints, and those hints register the mix-in for reflection. A JVM run never reads the hints, so a
 * deleted hint or a deleted {@code @ImportRuntimeHints} is green everywhere but here (#658, planted break 6 and the
 * registrars that share its shape).
 *
 * <p>A mix-in is an abstract nested class that carries a Jackson annotation on itself or on a member: the configurations
 * hand such a class to {@code setMixInAnnotation}, whatever the rule it declares ({@code @JsonInclude},
 * {@code @JsonIgnore}, {@code @JsonProperty}, ...). A Jackson annotation is one meta-annotated with
 * {@link JacksonAnnotation}. An interface used as a mix-in is out of scope: no configuration declares one.
 */
@DisplayName("Every mix-in is imported and registered by its declaring class's runtime hints (#658)")
class MixInHintsGateTest {

    /** The mix-ins found in the production classes. */
    private static List<Class<?>> productionMixIns() {
        return ServiceClasses.main().stream()
                .map(JavaClass::reflect)
                .filter(MixInHintsGateTest::isMixIn)
                .toList();
    }

    private static boolean isMixIn(Class<?> type) {
        return type.getEnclosingClass() != null
                && Modifier.isAbstract(type.getModifiers())
                && !type.isInterface()
                && (carriesJackson(type)
                        || Stream.concat(
                                        Arrays.stream(type.getDeclaredMethods()),
                                        Arrays.stream(type.getDeclaredFields()))
                                .anyMatch(MixInHintsGateTest::carriesJackson));
    }

    private static boolean carriesJackson(AnnotatedElement element) {
        return Arrays.stream(element.getAnnotations())
                .map(Annotation::annotationType)
                .anyMatch(annotation -> annotation.isAnnotationPresent(JacksonAnnotation.class));
    }

    /**
     * Why a mix-in would not reach a native executable, if it would not.
     *
     * @param mixIn the mix-in
     * @return the breach, or empty when the declaring class imports hints that register it
     */
    static Optional<String> breach(Class<?> mixIn) {
        Class<?> declaring = mixIn.getEnclosingClass();
        ImportRuntimeHints imported = declaring.getAnnotation(ImportRuntimeHints.class);
        if (imported == null) {
            return Optional.of(declaring.getName() + " declares the mix-in " + mixIn.getSimpleName()
                    + " but imports no runtime hints");
        }
        RuntimeHints hints = new RuntimeHints();
        for (Class<? extends RuntimeHintsRegistrar> registrar : imported.value()) {
            instantiate(registrar).registerHints(hints, MixInHintsGateTest.class.getClassLoader());
        }
        if (hints.reflection().getTypeHint(mixIn) == null) {
            return Optional.of(
                    declaring.getName() + "'s runtime hints do not register the mix-in " + mixIn.getSimpleName());
        }
        return Optional.empty();
    }

    private static RuntimeHintsRegistrar instantiate(Class<? extends RuntimeHintsRegistrar> registrar) {
        try {
            var constructor = registrar.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new LinkageError("Spring could not instantiate the registrar " + registrar.getName(), e);
        }
    }

    @Test
    void everyProductionMixInIsImportedAndRegistered() {
        assertThat(productionMixIns())
                .as("the mix-ins found: a selection that finds none would pass vacuously")
                .extracting(Class::getName)
                .contains(
                        "ai.speckitty.charter.missions.infra.api.MissionJson$OmitAbsentMembers",
                        "ai.speckitty.charter.routing.infra.api.RoutingJson$OmitAbsentMembers",
                        "ai.speckitty.charter.artifacts.infra.api.GlossaryJson$OmitAbsentMembers",
                        "ai.speckitty.charter.artifacts.infra.api.EffectiveJson$OmitAbsentMembers",
                        "ai.speckitty.charter.packs.infra.api.PresetJson$OmitAbsentMembers",
                        "ai.speckitty.charter.packs.infra.api.ArtifactJson$OmitAbsentMembers",
                        "ai.speckitty.charter.packs.infra.api.PackRegistrationJson$OmitAbsentMembers",
                        "ai.speckitty.charter.activations.infra.api.ActivationJson$OmitAbsentMembers",
                        "ai.speckitty.charter.web.ProblemJson$OmitAbsentMembers",
                        "ai.speckitty.charter.profiles.infra.api.ProfileJson$OptionalProfileMembers",
                        "ai.speckitty.charter.profiles.infra.api.ProfileJson$OmitAbsentMembers",
                        "ai.speckitty.charter.profiles.infra.api.ProfileJson$OptionalSpecializationMembers");

        assertThat(productionMixIns().stream().flatMap(mixIn -> breach(mixIn).stream()))
                .as("the mix-ins a native executable would not read")
                .isEmpty();
    }

    @Test
    void theGateBitesOnAMixInWhoseDeclaringClassImportsNoHints() {
        assertThat(isMixIn(NoImport.Members.class)).isTrue();
        assertThat(breach(NoImport.Members.class))
                .contains(NoImport.class.getName() + " declares the mix-in Members but imports no runtime hints");
    }

    @Test
    void theGateBitesOnAMixInTheImportedHintsForget() {
        assertThat(isMixIn(ForgetfulImport.Members.class)).isTrue();
        assertThat(breach(ForgetfulImport.Members.class))
                .contains(ForgetfulImport.class.getName() + "'s runtime hints do not register the mix-in Members");
    }

    @Test
    void theGatePassesAMixInTheImportedHintsRegister() {
        assertThat(isMixIn(Registered.Members.class)).isTrue();
        assertThat(breach(Registered.Members.class)).isEmpty();
    }

    @Test
    void aJacksonAnnotationOnAMemberMakesAMixInAndAConcreteOrNonJacksonClassIsNone() {
        assertThat(isMixIn(Registered.MemberAnnotated.class)).isTrue();
        assertThat(isMixIn(Registered.IgnoreOnly.class)).isTrue();
        assertThat(isMixIn(Registered.NotJackson.class)).isFalse();
        assertThat(isMixIn(Registered.Concrete.class)).isFalse();
    }

    // ---- Known-bad and known-good controls: test classes, so the production import never sees them. ----------

    /** A declaring class without {@code @ImportRuntimeHints}. */
    static class NoImport {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract static class Members {}
    }

    /** A declaring class whose hints register nothing. */
    @ImportRuntimeHints(ForgetfulImport.Hints.class)
    static class ForgetfulImport {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract static class Members {}

        static class Hints implements RuntimeHintsRegistrar {
            @Override
            public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
                // Forgets the mix-in.
            }
        }
    }

    /** A declaring class whose hints register its mix-ins. */
    @ImportRuntimeHints(Registered.Hints.class)
    static class Registered {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract static class Members {}

        @SuppressWarnings("UnusedMethod")
        abstract static class MemberAnnotated {
            @JsonInclude(JsonInclude.Include.NON_NULL)
            abstract @Nullable String getName();
        }

        /** A mix-in that never mentions {@code @JsonInclude}. */
        @SuppressWarnings("UnusedMethod")
        abstract static class IgnoreOnly {
            @JsonIgnore
            abstract @Nullable String getSecret();
        }

        @Deprecated
        abstract static class NotJackson {}

        @JsonInclude(JsonInclude.Include.NON_NULL)
        static class Concrete {}

        static class Hints implements RuntimeHintsRegistrar {
            @Override
            public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
                hints.reflection().registerType(Members.class).registerType(MemberAnnotated.class);
            }
        }
    }
}
