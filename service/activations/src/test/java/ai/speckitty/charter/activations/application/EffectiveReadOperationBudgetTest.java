package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ai.speckitty.charter.packs.domain.InForceResolution;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The NFR-003 gate (decision {@code DM-01M6RN5SG19KXAHH4VGT9QBYA1}): the work of an effective read over ten packs and two
 * thousand artifacts, counted, never timed. One request does one compose of the closure (the facts of each pack read once,
 * from the store when the cache is cold and from the cache when it is warm), one in-force resolution, and walks at most
 * three times the closure's nodes; and it digests the pack files three times, the two of the verified read and the one
 * of the closure's own digest, which the closure-scoped tags (decision {@code 01M6Y11E55YP5TG1EJ1MDKCFRP}) added. Wall time is
 * {@link EffectiveReadBudgetTest}'s, which only reports. The counts are taken through the test hooks on
 * {@code CompositionFactsCache} and {@code InForceResolution}, as a delta around the request, with the cache cold and
 * then warm.
 */
class EffectiveReadOperationBudgetTest {

    private EffectiveReadFixture fixture;

    @BeforeEach
    void setUp(@TempDir Path root) {
        fixture = new EffectiveReadFixture(root);
    }

    private MockHttpServletResponse read() throws Exception {
        return fixture.mvc
                .perform(get(EffectiveReadFixture.PATH).accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse();
    }

    /** What one request did, as the deltas of the hooks and the digest counters around it. */
    private record Work(
            long loads, long hits, long resolutions, long walkVisits, int wholeDigests, int closureDigests) {}

    private Work measured() throws Exception {
        long loads = fixture.cache.loads();
        long hits = fixture.cache.hits();
        long resolutions = InForceResolution.resolutions();
        long visits = InForceResolution.walkVisits();
        int whole = fixture.digests.whole();
        int closure = fixture.digests.closure();
        MockHttpServletResponse answer = read();
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getContentAsString()).contains("\"hasNextPage\":true");
        return new Work(
                fixture.cache.loads() - loads,
                fixture.cache.hits() - hits,
                InForceResolution.resolutions() - resolutions,
                InForceResolution.walkVisits() - visits,
                fixture.digests.whole() - whole,
                fixture.digests.closure() - closure);
    }

    @Test
    void aColdReadComposesOnceResolvesOnceAndWalksWithinThreeTimesTheClosure() throws Exception {
        fixture.cache.clear();

        Work cold = measured();

        assertThat(cold.loads())
                .as("one compose: the facts of each of the ten packs, read from the store once")
                .isEqualTo(EffectiveReadFixture.PACKS);
        assertThat(cold.hits()).as("a cold cache answers nothing").isZero();
        assertThat(cold.resolutions()).as("one in-force resolution").isEqualTo(1);
        assertThat(cold.walkVisits())
                .as("walk node visits, at most 3 x the closure's %d nodes", EffectiveReadFixture.ARTIFACTS)
                .isPositive()
                .isLessThanOrEqualTo(3L * EffectiveReadFixture.ARTIFACTS);
        assertThat(cold.wholeDigests())
                .as("the verified read digests before and after the load")
                .isEqualTo(2);
        assertThat(cold.closureDigests())
                .as("and the closure's own digest, once")
                .isEqualTo(1);
    }

    @Test
    void aWarmReadComposesFromTheCacheOnceAndResolvesOnce() throws Exception {
        read();

        Work warm = measured();

        assertThat(warm.loads()).as("nothing is read from the store").isZero();
        assertThat(warm.hits())
                .as("the one compose answered by the cache, a pack at a time")
                .isEqualTo(EffectiveReadFixture.PACKS);
        assertThat(warm.resolutions()).isEqualTo(1);
        assertThat(warm.walkVisits()).isLessThanOrEqualTo(3L * EffectiveReadFixture.ARTIFACTS);
        assertThat(warm.wholeDigests()).isEqualTo(2);
        assertThat(warm.closureDigests()).isEqualTo(1);
    }

    @Test
    void aNotModifiedReadDoesTheComposeAndNeitherTheMergeNorTheResolution() throws Exception {
        String etag = read().getHeader("ETag");
        long resolutions = InForceResolution.resolutions();

        MockHttpServletResponse notModified = fixture.mvc
                .perform(get(EffectiveReadFixture.PATH).header("If-None-Match", etag))
                .andReturn()
                .getResponse();

        assertThat(notModified.getStatus()).isEqualTo(304);
        assertThat(InForceResolution.resolutions() - resolutions).isZero();
    }

    @Test
    void theCacheKeyChangesWhenAPacksRevisionDoesAndTheNextReadLoadsAgain() throws Exception {
        read();
        fixture.revise();

        Work revised = measured();

        assertThat(revised.loads()).isEqualTo(EffectiveReadFixture.PACKS);
        assertThat(revised.hits()).isZero();
    }
}
