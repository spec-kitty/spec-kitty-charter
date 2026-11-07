package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetchRefusal.Reason;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The https exchange of the contract scenarios: it serves the archive a scenario armed for a URL, to the hosts a scenario
 * allowed, and refuses every other host as {@link Reason#HOST_NOT_ALLOWED} and every URL nothing was armed for as a
 * status refusal, as the real exchange does by default (its allowlist is empty). It opens no socket: the guarded exchange
 * itself is tested offline in {@code packs} (WP17). The scenarios' Spring context makes it the {@code @Primary}
 * {@link HttpsExchange}, so the archive fetcher above it (the digest, the media type, the extraction, the name check)
 * runs for real.
 */
public final class StubArchiveExchange implements HttpsExchange {

    private final Set<String> allowed = ConcurrentHashMap.newKeySet();
    private final Map<URI, Fetched> served = new ConcurrentHashMap<>();

    /**
     * Allows a host until the scenario ends.
     *
     * @param host the host
     */
    public void allow(String host) {
        allowed.add(GuideAllowlist.normalise(host));
    }

    /**
     * Serves {@code body} for {@code url} until the scenario ends.
     *
     * @param url the URL
     * @param body the archive's bytes
     * @param contentType the {@code Content-Type} the URL answers
     */
    public void serve(URI url, byte[] body, String contentType) {
        HttpHeaders headers = HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (name, value) -> true);
        served.put(url, new Fetched(GuideAllowlist.normalise(url.getHost()), headers, body.clone()));
    }

    /** Forgets every allowed host and armed URL, so each scenario starts with an allowlist as empty as the default. */
    public void reset() {
        allowed.clear();
        served.clear();
    }

    @Override
    public Fetched fetch(URI uri, BodyPolicy policy) throws GuideFetchRefusal {
        String host = uri.getHost();
        if (host == null || !allowed.contains(GuideAllowlist.normalise(host))) {
            throw new GuideFetchRefusal(Reason.HOST_NOT_ALLOWED, host, "the host is not on the allowlist");
        }
        Fetched armed = served.get(uri);
        if (armed == null) {
            throw new GuideFetchRefusal(Reason.HTTP_STATUS, host, "the response status is 404");
        }
        if (armed.length() > policy.maxBytes()) {
            throw new GuideFetchRefusal(Reason.TOO_LARGE, host, "the body exceeds the size cap");
        }
        return new Fetched(armed.host(), armed.headers(), armed.toByteArray());
    }
}
