package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ai.speckitty.charter.kernel.metering.WorkMeter;
import ai.speckitty.charter.packs.application.CacheWork;
import ai.speckitty.charter.packs.domain.ResolutionWork;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The NFR-003 gate (decision {@code DM-01M6RN5SG19KXAHH4VGT9QBYA1}): the work of an effective read over ten packs and two
 * thousand artifacts, counted, never timed. One request does one compose of the closure (the facts of each pack read once,
 * from the store when the cache is cold and from the cache when it is warm), one in-force resolution, and walks no more
 * than the fixture's own walk, which is derived from it below (the cycle check visits each node it starts from once, and
 * the fixed point runs the strata twice, over the chain of overrides and then over the chain without its pruned head);
 * and it digests the pack files three times, the two of the verified read and the one
 * of the closure's own digest, which the closure-scoped tags (decision {@code 01M6Y11E55YP5TG1EJ1MDKCFRP}) added: each pack
 * of the closure once, digested on its own so the digest does not depend on the configured order (NFR-001). Wall time is
 * {@link EffectiveReadBudgetTest}'s, which only reports. The counts are those of a {@code WorkMeter} bound around the
 * request alone: the cache's loads and hits ({@code CacheWork}) and the resolutions and their walk visits
 * ({@code ResolutionWork}), with the cache cold and then warm.
 */
class EffectiveReadOperationBudgetTest {

    /** The nodes the cycle check visits: each selected artifact once, and the required one it also starts from. */
    private static final long CYCLE_CHECK = EffectiveReadFixture.SELECTED + 1L;

    /** The steps of the reach over the chain of overrides: its k-th artifact walks the {@code CHAIN - k} below it. */
    private static final long CHAIN_REACH = (long) EffectiveReadFixture.CHAIN * (EffectiveReadFixture.CHAIN + 1) / 2;

    /**
     * What one resolution walks: the cycle check, the reach of the first round, and the reach of the second round, in
     * which the pruned head of the chain no longer overrides (its {@code CHAIN} steps are gone). A resolution that
     * runs its strata a third time, or twice over from the start, walks more.
     */
    private static final long ONE_RESOLUTION = CYCLE_CHECK + CHAIN_REACH + (CHAIN_REACH - EffectiveReadFixture.CHAIN);

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

    /** What one request did: its metered work, and the deltas of the digest counters around it. */
    private record Work(
            long loads, long hits, long resolutions, long walkVisits, int wholeDigests, int closureDigests) {}

    private Work measured() throws Exception {
        return measured(fixture, EffectiveReadFixture.PATH, "\"hasNextPage\":true");
    }

    /** What one request to the path did, its answer a 200 that contains the text. */
    private static Work measured(EffectiveReadFixture fixture, String path, String contains) throws Exception {
        int whole = fixture.digests.whole();
        int closure = fixture.digests.closure();
        WorkMeter.Measured<MockHttpServletResponse> measured = WorkMeter.measure(() -> fixture.mvc
                .perform(get(path).accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse());
        MockHttpServletResponse answer = measured.value();
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getContentAsString()).contains(contains);
        return new Work(
                measured.work().of(CacheWork.LOAD),
                measured.work().of(CacheWork.HIT),
                measured.work().of(ResolutionWork.RESOLUTION),
                measured.work().of(ResolutionWork.RESOLUTION_VISIT),
                fixture.digests.whole() - whole,
                fixture.digests.closure() - closure);
    }

    @Test
    void aColdReadComposesOnceResolvesOnceAndWalksNoMoreThanOneResolutionOfTheFixture() throws Exception {
        fixture.coldStart();

        Work cold = measured();

        assertThat(cold.loads())
                .as("one compose: the facts of each of the ten packs, read from the store once")
                .isEqualTo(EffectiveReadFixture.PACKS);
        assertThat(cold.hits()).as("a cold cache answers nothing").isZero();
        assertThat(cold.resolutions()).as("one in-force resolution").isEqualTo(1);
        assertThat(cold.walkVisits())
                .as("walk node visits: no more than one resolution of the fixture walks")
                .isLessThanOrEqualTo(ONE_RESOLUTION)
                .as("and the fixed point iterated, so the second round's reach was walked too")
                .isGreaterThan(CYCLE_CHECK + CHAIN_REACH);
        assertThat(cold.wholeDigests())
                .as("the verified read digests before and after the load")
                .isEqualTo(2);
        assertThat(cold.closureDigests())
                .as("and the closure's own digest, once over each pack of the closure")
                .isEqualTo(EffectiveReadFixture.PACKS);
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
        assertThat(warm.walkVisits()).isLessThanOrEqualTo(ONE_RESOLUTION).isGreaterThan(CYCLE_CHECK + CHAIN_REACH);
        assertThat(warm.wholeDigests()).isEqualTo(2);
        assertThat(warm.closureDigests()).isEqualTo(EffectiveReadFixture.PACKS);
    }

    @Test
    void aNotModifiedReadDoesTheComposeAndNeitherTheMergeNorTheResolution() throws Exception {
        String etag = read().getHeader("ETag");
        WorkMeter.Measured<MockHttpServletResponse> notModified = WorkMeter.measure(() -> fixture.mvc
                .perform(get(EffectiveReadFixture.PATH).header("If-None-Match", etag))
                .andReturn()
                .getResponse());

        assertThat(notModified.value().getStatus()).isEqualTo(304);
        assertThat(notModified.work().of(ResolutionWork.RESOLUTION)).isZero();
    }

    @Test
    void theCacheKeyChangesWhenAPacksRevisionDoesAndTheNextReadLoadsAgain() throws Exception {
        read();
        fixture.revise();

        Work revised = measured();

        assertThat(revised.loads()).isEqualTo(EffectiveReadFixture.PACKS);
        assertThat(revised.hits()).isZero();
    }

    // ---- #602 (ADR-0045 decisions 9 and 10, NFR-004): the tensions add no compose and no resolution ----------------

    /** The work of one cold read of the artifact list over the base fixture: the counts every other read is held to. */
    private static Work listedCold(EffectiveReadFixture fixture) throws Exception {
        fixture.coldStart();
        return measured(fixture, EffectiveReadFixture.PATH, "\"hasNextPage\":true");
    }

    @Test
    void theGraphAndSkippedReadsOfAClosureWithNoTensionDoTheWorkOfTheListRead() throws Exception {
        Work list = listedCold(fixture);

        fixture.coldStart();
        Work graph = measured(fixture, EffectiveReadFixture.GRAPH, "\"edges\":[");
        fixture.coldStart();
        Work skipped = measured(fixture, EffectiveReadFixture.SKIPPED, "\"report\"");

        assertThat(graph)
                .as("the graph read: one compose, one resolution, the same walk and digests")
                .isEqualTo(list);
        assertThat(skipped).as("the skipped report: the same").isEqualTo(list);
        assertThat(graph.loads()).isEqualTo(EffectiveReadFixture.PACKS);
        assertThat(graph.resolutions()).isEqualTo(1);
    }

    @Test
    void theGraphAndContextReadsOfAClosureWithATensionResolveOnceAndAnswerIt(@TempDir Path root) throws Exception {
        EffectiveReadFixture tensions = new EffectiveReadFixture(root, true);
        String pair = "\"source\":\"directive:" + EffectiveReadFixture.TENSION_A + "\"";

        tensions.coldStart();
        Work graph = measured(tensions, EffectiveReadFixture.GRAPH, "\"relation\":\"tension\"");
        tensions.coldStart();
        Work context = measured(tensions, EffectiveReadFixture.CONTEXT, "\"tensions\":[{" + pair);
        Work warmGraph = measured(
                tensions,
                EffectiveReadFixture.GRAPH,
                "\"reconciledBy\":[\"directive:" + EffectiveReadFixture.RECONCILER + "\"]");

        assertThat(graph.loads()).as("one compose of the ten packs").isEqualTo(EffectiveReadFixture.PACKS);
        assertThat(graph.resolutions())
                .as("one in-force resolution, the tensions included")
                .isEqualTo(1);
        assertThat(graph.wholeDigests()).isEqualTo(2);
        assertThat(graph.closureDigests()).isEqualTo(EffectiveReadFixture.PACKS);
        assertThat(context.loads()).isEqualTo(EffectiveReadFixture.PACKS);
        assertThat(context.resolutions())
                .as("the context and its tensions resolve once")
                .isEqualTo(1);
        assertThat(warmGraph.loads()).isZero();
        assertThat(warmGraph.resolutions()).isEqualTo(1);
    }
}
