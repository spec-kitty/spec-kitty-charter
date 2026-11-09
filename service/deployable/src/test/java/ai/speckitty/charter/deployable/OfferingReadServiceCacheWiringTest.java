package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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

    @Autowired
    CompositionFactsCache cache;

    private int read() throws Exception {
        return mvc.perform(get("/api/v1/offering")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ActivationTestWiring.TOKEN))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void theWiredOfferingReadsTheFactsOfEachPackThroughTheSharedCache() throws Exception {
        cache.clear();
        long loads = cache.loads();
        long hits = cache.hits();

        assertThat(read()).isEqualTo(200);
        long loadedByTheFirstRead = cache.loads() - loads;
        long hitsOfTheFirstRead = cache.hits() - hits;
        assertThat(read()).isEqualTo(200);

        assertThat(loadedByTheFirstRead)
                .as("the first read of the configured packs loads their facts from the store, through the cache")
                .isPositive();
        assertThat(hitsOfTheFirstRead).as("a cold cache answers nothing").isZero();
        assertThat(cache.loads() - loads)
                .as("the second read loads nothing more: the cache answers each pack")
                .isEqualTo(loadedByTheFirstRead);
        assertThat(cache.hits() - hits).as("and it answers once per pack").isEqualTo(loadedByTheFirstRead);
    }
}
