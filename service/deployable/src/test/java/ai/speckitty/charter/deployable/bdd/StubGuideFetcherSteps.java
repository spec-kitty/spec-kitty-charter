package ai.speckitty.charter.deployable.bdd;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import java.net.URI;

/**
 * The guide-host step of contracts/charter/scenarios/README.md: what the {@link StubGuideFetcher} serves. A URI no
 * scenario armed is refused as a host off the allowlist.
 */
public class StubGuideFetcherSteps {

    private final StubGuideFetcher fetcher;

    /**
     * The steps over the scenarios' stub fetcher.
     *
     * @param fetcher the stub fetcher, the {@code @Primary} guide fetcher of the scenarios' context
     */
    public StubGuideFetcherSteps(StubGuideFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Before
    public void emptyAllowlist() {
        fetcher.reset();
    }

    @Given("the guide host serves {string} with:")
    public void serves(String uri, String text) {
        fetcher.serve(URI.create(uri), text);
    }
}
