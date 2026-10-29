package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetcher;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The guide fetcher of the contract scenarios: it serves the text a scenario armed for a URI and refuses every other
 * URI with {@link GuideFetchRefusal.Reason#HOST_NOT_ALLOWED}, as the real fetcher's allowlist does by default (it is
 * empty). It opens no socket: the guarded fetch itself is tested offline in {@code packs} (WP08). The scenarios'
 * Spring context makes it the {@code @Primary} {@link GuideFetcher}.
 */
public final class StubGuideFetcher implements GuideFetcher {

    private final Map<URI, String> served = new ConcurrentHashMap<>();

    /**
     * Serves {@code text} for {@code uri} until the scenario ends.
     *
     * @param uri the URI
     * @param text the guide text the URI answers
     */
    public void serve(URI uri, String text) {
        served.put(uri, text);
    }

    /** Forgets every armed URI, so each scenario starts with an allowlist as empty as the default. */
    public void reset() {
        served.clear();
    }

    @Override
    public String fetch(URI uri) throws GuideFetchRefusal {
        String text = served.get(uri);
        if (text == null) {
            throw new GuideFetchRefusal(
                    GuideFetchRefusal.Reason.HOST_NOT_ALLOWED, uri.getHost(), "the host is not on the allowlist");
        }
        return text;
    }
}
