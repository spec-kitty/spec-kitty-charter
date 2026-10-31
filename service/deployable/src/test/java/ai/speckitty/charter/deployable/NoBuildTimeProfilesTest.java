package ai.speckitty.charter.deployable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;

/**
 * The service's beans do not depend on the active profile (ADR-0046 decision 5). Spring AOT decides {@code @Profile}
 * when the native image is built, so a bean gated by a profile is fixed by the profiles active at build time: a native
 * executable built without the {@code container} profile and started with it would leave out the profile's checks
 * (ADR-0041), failing open. A profile is read at run time instead, with {@code Environment.matchesProfiles}.
 */
class NoBuildTimeProfilesTest {

    private static final JavaClasses SERVICE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ai.speckitty.charter");

    @Test
    void noServiceClassIsGatedByAProfile() {
        noClasses()
                .should()
                .beAnnotatedWith(Profile.class)
                .because("Spring AOT fixes a profile when the native image is built (ADR-0046 decision 5)")
                .check(SERVICE);
    }

    @Test
    void noServiceBeanMethodIsGatedByAProfile() {
        noMethods()
                .should()
                .beAnnotatedWith(Profile.class)
                .because("Spring AOT fixes a profile when the native image is built (ADR-0046 decision 5)")
                .check(SERVICE);
    }

    /**
     * What AOT records is the set of bean definitions. The container wiring registers the same beans whether or not the
     * profile is active, so the image AOT builds serves both starts.
     */
    @Test
    void theContainerWiringRegistersTheSameBeansWithAndWithoutTheProfile(@TempDir Path root) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(ContainerWiring.class, ContainerStartupCheck.class)
                .withPropertyValues(
                        "server.port=8080",
                        "charter.config-root=" + root,
                        "charter.container.secrets-dir=" + root.resolve("secrets"),
                        "charter.container.allowed-hosts=charter.example.org");
        List<String> without = beanNames(runner);
        List<String> with =
                beanNames(runner.withPropertyValues("spring.profiles.active=" + ContainerProperties.PROFILE));
        assertThat(with).isEqualTo(without).contains("containerWiring", "containerStartupCheck");
    }

    private static List<String> beanNames(ApplicationContextRunner runner) {
        String[][] names = new String[1][];
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            names[0] = context.getBeanDefinitionNames();
        });
        return Arrays.stream(names[0]).sorted().toList();
    }
}
