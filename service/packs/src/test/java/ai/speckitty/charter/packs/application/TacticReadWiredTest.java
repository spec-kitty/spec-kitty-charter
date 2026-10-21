package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.config.ApplicationConfiguration;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import ai.speckitty.charter.packs.infra.storage.config.StorageConfiguration;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * The storage and application slice provisioned through its real configurations, with a pack root pointed at a
 * fixture: the tactics are read through the production path (SC-001, SC-003), and an unknown pack is refused as
 * not-found rather than as a server fault.
 */
@SpringBootTest(classes = {StorageConfiguration.class, ApplicationConfiguration.class, TacticReadWiredTest.Root.class})
class TacticReadWiredTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M5DEH8ROUNDTRIPFIXTURE0A");

    /** The one thing a deployment configures: where the packs are. Everything else is the real beans. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Root {
        @Bean
        DomainRepositoryService.Locations locations() throws URISyntaxException {
            Path pack = Path.of(TacticReadWiredTest.class
                    .getResource("/roundtrip-fixtures/pack-a")
                    .toURI());
            List<PackLocation> locations = List.of(new PackLocation("pack-a", PackLevel.ORG, pack));
            return () -> locations;
        }
    }

    @Autowired
    private TacticReadService service;

    @Autowired
    private ApplicationContext context;

    @Test
    void readsTheFixturePacksTacticsThroughTheRealBeans() {
        // Beta is readable and alpha is too; the fixture's broken tactic is left out, not an error.
        assertThat(service.tacticsOf(PACK)).extracting(Tactic::name).contains("Alpha tactic", "Beta tactic");
        assertThat(service.tactic(PACK, new TacticIdentifier("alpha-tactic")).packId())
                .isEqualTo(PACK);
    }

    @Test
    void anUnknownPackIsRefusedAsNotFoundThroughTheSameWiring() {
        assertThatThrownBy(() -> service.tacticsOf(new PackIdentifier("no-such-pack")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void eachRepositoryPortHasExactlyOneRealization() {
        assertThat(context.getBeansOfType(PackRepository.class)).hasSize(1);
        assertThat(context.getBeansOfType(TacticRepository.class)).hasSize(1);
    }
}
