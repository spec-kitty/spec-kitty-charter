package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ai.speckitty.charter.kernel.metering.WorkMeter;
import ai.speckitty.charter.packs.application.CacheWork;
import ai.speckitty.charter.packs.application.CompositionFactsCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The deployed {@code OfferingReadService} reads the facts of each pack through {@link CompositionFactsCache} on the
 * Spring path (IC-07, #416, hand-off N3 of the WP21 review): the bean Spring wires through its autowired constructor is
 * the one with the cache, so a second read of an unchanged pack is answered by the cache and reads no pack store.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + ActivationTestWiring.TOKEN)
@AutoConfigureMockMvc
@Import(ConfigRootTestConfiguration.class)
class OfferingReadServiceCacheWiringTest {

    @Autowired
    MockMvc mvc;

    private int read() throws Exception {
        return mvc.perform(get("/api/v1/offering")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ActivationTestWiring.TOKEN))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void theWiredOfferingReadsTheFactsOfEachPackThroughTheSharedCache() throws Exception {
        WorkMeter.Measured<Integer> first = WorkMeter.measure(this::read);
        WorkMeter.Measured<Integer> second = WorkMeter.measure(this::read);

        assertThat(first.value()).isEqualTo(200);
        assertThat(second.value()).isEqualTo(200);
        long throughTheCache = first.work().of(CacheWork.LOAD) + first.work().of(CacheWork.HIT);
        assertThat(throughTheCache)
                .as("the first read takes the facts of the configured packs through the cache, loaded or kept")
                .isPositive();
        assertThat(second.work().of(CacheWork.LOAD))
                .as("the second read loads nothing: the cache answers each pack")
                .isZero();
        assertThat(second.work().of(CacheWork.HIT))
                .as("and it answers once per pack the first read took")
                .isEqualTo(throughTheCache);
    }
}
