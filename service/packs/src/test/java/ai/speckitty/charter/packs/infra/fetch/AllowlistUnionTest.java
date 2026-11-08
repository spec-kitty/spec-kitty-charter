package ai.speckitty.charter.packs.infra.fetch;

import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.infra.fetch.GlobalAddressResolverProviderTest.Recording;
import ai.speckitty.charter.packs.infra.fetch.config.GuideFetchConfiguration;
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The resolver guard covers the union of every named allowlist (T084, squad finding 1): a host that only the archive
 * allowlist (or only the git one) names is rebinding-guarded exactly as a guide host is, checked at startup.
 */
class AllowlistUnionTest {

    private static final LookupPolicy ANY = LookupPolicy.of(LookupPolicy.IPV4 | LookupPolicy.IPV6);

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

    private static GlobalAddressResolverProvider.Guard guardOverThePrivateAnswer() {
        return new GlobalAddressResolverProvider.Guard(
                new Recording(PRIVATE), GlobalAddressResolverProvider::installed, new GuardRefusals());
    }

    @Test
    void aHostOnlyTheArchiveListNamesIsStillRebindingGuarded() {
        runner.withPropertyValues("charter.pack-sources.https.allowed-hosts=Packs.Example.Org.")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(GlobalAddressResolverProvider.installed().allows("packs.example.org"))
                            .isTrue();
                    assertThatThrownBy(() -> guardOverThePrivateAnswer().lookupByName("packs.example.org", ANY))
                            .isInstanceOf(NonGlobalAddressException.class);
                    assertThat(guardOverThePrivateAnswer().lookupByName("elsewhere.example.org", ANY))
                            .containsExactly(PRIVATE);
                });
    }

    @Test
    void aHostOnlyTheGitListNamesIsStillRebindingGuarded() {
        runner.withPropertyValues("charter.pack-sources.git.allowed-hosts=git.example.org")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThatThrownBy(() -> guardOverThePrivateAnswer().lookupByName("git.example.org", ANY))
                            .isInstanceOf(NonGlobalAddressException.class);
                });
    }

    @Test
    void theGuardActsOnTheUnionOfGuidesArchivesAndGit() {
        runner.withPropertyValues(
                        "charter.guide-fetch.allowed-hosts=guide.example",
                        "charter.pack-sources.https.allowed-hosts=packs.example.org",
                        "charter.pack-sources.git.allowed-hosts=git.example.org")
                .run(context -> assertThat(
                                GlobalAddressResolverProvider.installed().hosts())
                        .containsExactlyInAnyOrder("guide.example", "packs.example.org", "git.example.org"));
    }

    @Test
    void theUnionIsRemovedWhenTheContextCloses() {
        runner.withPropertyValues("charter.pack-sources.https.allowed-hosts=packs.example.org")
                .run(context -> assertThat(
                                GlobalAddressResolverProvider.installed().hosts())
                        .containsExactly("packs.example.org"));

        assertThat(GlobalAddressResolverProvider.installed()).isEqualTo(GuideAllowlist.EMPTY);
    }

    @Test
    void anArchiveEntryThatIsNotAPlainHostNameFailsTheStartNamingItsOwnKey() {
        runner.withPropertyValues("charter.pack-sources.https.allowed-hosts=https://packs.example.org")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("charter.pack-sources.https.allowed-hosts"));
    }

    @Test
    void theUnionIsRefusedAtStartupWhenTheGuardIsNotTheJvmResolver() {
        GuideAllowlist union = new GuideAllowlist(java.util.Set.of("packs.example.org"));

        assertThatThrownBy(() -> GlobalAddressResolverProvider.installRequiringGuard(union, () -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rebinding")
                .hasMessageContaining("charter.pack-sources");
        assertThat(GlobalAddressResolverProvider.installed()).isEqualTo(before);
    }

    @Test
    void theDefaultsAreAnEmptyArchiveAllowlistSixtySecondsThirtyTwoMebibytesThreeRedirectsAndEveryKindOff() {
        PackSourceProperties properties = new org.springframework.boot.context.properties.bind.Binder(
                        new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                                java.util.Map.of()))
                .bindOrCreate("charter.pack-sources", PackSourceProperties.class);

        assertThat(properties.https().allowedHosts()).isEmpty();
        assertThat(properties.https().timeout()).isEqualTo(java.time.Duration.ofSeconds(60));
        assertThat(properties.https().maxSize().toBytes()).isEqualTo(32L * 1024 * 1024);
        assertThat(properties.https().maxRedirects()).isEqualTo(3);
        assertThat(properties.git().allowedHosts()).isEmpty();
        assertThat(properties.https().enabled()).isFalse();
        assertThat(properties.git().enabled()).isFalse();
        assertThat(properties.filesystem().enabled()).isFalse();
        assertThat(properties.filesystem().root()).isNull();
        assertThat(List.copyOf(properties.https().allowedHosts())).isEmpty();
    }
}
