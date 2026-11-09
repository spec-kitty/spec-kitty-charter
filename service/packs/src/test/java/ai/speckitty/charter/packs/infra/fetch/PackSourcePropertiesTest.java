package ai.speckitty.charter.packs.infra.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * {@code charter.pack-sources.https.max-size} is at most 32 MiB, the archive bound the free-space check is sized from
 * (#418, F6): a larger value fails the start, naming the property, instead of making the check promise room it does not
 * ask for.
 */
class PackSourcePropertiesTest {

    private final ApplicationContextRunner start =
            new ApplicationContextRunner().withUserConfiguration(Properties.class);

    @Test
    void aMaxSizeOverThirtyTwoMebibytesFailsTheStartAndNamesTheProperty() {
        start.withPropertyValues("charter.pack-sources.https.max-size=33MB").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .rootCause()
                    .hasMessageContaining("charter.pack-sources.https.max-size")
                    .hasMessageContaining("32 MiB");
        });
        start.withPropertyValues("charter.pack-sources.https.max-size=33554433B")
                .run(context -> assertThat(context).as("one byte over").hasFailed());
    }

    @Test
    void aMaxSizeOfThirtyTwoMebibytesOrLessStarts() {
        start.withPropertyValues("charter.pack-sources.https.max-size=32MB")
                .run(context -> assertThat(context)
                        .getBean(PackSourceProperties.class)
                        .extracting(properties -> properties.https().maxSize())
                        .isEqualTo(DataSize.ofMegabytes(32)));
        start.withPropertyValues("charter.pack-sources.https.max-size=1MB")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void theDefaultIsThirtyTwoMebibytes() {
        start.run(context -> assertThat(
                        context.getBean(PackSourceProperties.class).https().maxSize())
                .isEqualTo(DataSize.ofMegabytes(32)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PackSourceProperties.class)
    static class Properties {}
}
