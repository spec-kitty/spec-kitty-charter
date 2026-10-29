package ai.speckitty.charter.packs.infra.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetcher;
import ai.speckitty.charter.packs.infra.fetch.config.GuideFetchConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

/**
 * {@code charter.guide-fetch.*} (T035): the defaults bound, the fetcher bean wired, and the allowlist installed into
 * the JVM-wide resolver guard for the life of the context only.
 */
class GuideFetchConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(GuideFetchConfiguration.class);
    private GuideAllowlist before = GuideAllowlist.EMPTY;

    @BeforeEach
    void remember() {
        before = GlobalAddressResolverProvider.installed();
    }

    @AfterEach
    void restore() {
        GlobalAddressResolverProvider.install(before);
    }

    @Test
    void theDefaultsAreAnEmptyAllowlistTenSecondsOneMebibyteAndThreeRedirects() {
        GuideFetchProperties properties = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("charter.guide-fetch", GuideFetchProperties.class);

        assertThat(properties.allowedHosts()).isEmpty();
        assertThat(properties.allowlist()).isEqualTo(GuideAllowlist.EMPTY);
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.maxSize()).isEqualTo(DataSize.ofBytes(1024 * 1024));
        assertThat(properties.maxRedirects()).isEqualTo(3);
    }

    @Test
    void configuredHostsAreNormalisedAndBound() {
        GuideFetchProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                        "charter.guide-fetch.allowed-hosts", "Guide.Example., raw.githubusercontent.com",
                        "charter.guide-fetch.timeout", "5s",
                        "charter.guide-fetch.max-size", "512KB",
                        "charter.guide-fetch.max-redirects", "1")))
                .bindOrCreate("charter.guide-fetch", GuideFetchProperties.class);

        assertThat(properties.allowlist().hosts())
                .containsExactlyInAnyOrder("guide.example", "raw.githubusercontent.com");
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.maxSize()).isEqualTo(DataSize.ofKilobytes(512));
        assertThat(properties.maxRedirects()).isEqualTo(1);
    }

    @Test
    void byDefaultTheFetcherRefusesEveryUriAndTheGuardActsOnNothing() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(GuideFetcher.class);
            assertThat(GlobalAddressResolverProvider.installed()).isEqualTo(GuideAllowlist.EMPTY);
            assertThatThrownBy(
                            () -> context.getBean(GuideFetcher.class).fetch(URI.create("https://guide.example/g.md")))
                    .isInstanceOfSatisfying(
                            GuideFetchRefusal.class,
                            refusal ->
                                    assertThat(refusal.reason()).isEqualTo(GuideFetchRefusal.Reason.HOST_NOT_ALLOWED));
        });
    }

    @Test
    void theConfiguredAllowlistIsInstalledForTheContextAndRemovedWhenItCloses() {
        runner.withPropertyValues("charter.guide-fetch.allowed-hosts=Guide.Example.")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(GlobalAddressResolverProvider.installed().allows("guide.example"))
                            .isTrue();
                });

        assertThat(GlobalAddressResolverProvider.installed()).isEqualTo(GuideAllowlist.EMPTY);
    }

    @Test
    void anInvalidLimitFailsTheStart() {
        runner.withPropertyValues("charter.guide-fetch.timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }
}
