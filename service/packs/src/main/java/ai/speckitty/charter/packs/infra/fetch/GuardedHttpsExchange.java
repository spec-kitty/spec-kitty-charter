package ai.speckitty.charter.packs.infra.fetch;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** The guarded HTTPS exchange (skeleton: signatures only, built next). */
public final class GuardedHttpsExchange implements HttpsExchange {

    /** The most pre-check lookups that run at once. */
    static final int LOOKUP_SLOTS = 8;

    /** Name resolution as the pre-check sees it. */
    @FunctionalInterface
    interface Resolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    /** One request and its response, for one fetch. */
    @FunctionalInterface
    interface Transport {
        CompletableFuture<HttpResponse<byte[]>> send(
                HttpRequest request, HttpResponse.BodyHandler<byte[]> bodyHandler, GuardRefusals.Attempt attempt);
    }

    GuardedHttpsExchange(
            GuideAllowlist allowlist,
            Duration timeout,
            int maxRedirects,
            Resolver resolver,
            Transport transport,
            int lookupSlots) {}

    @Override
    public Fetched fetch(URI uri, BodyPolicy policy) throws GuideFetchRefusal {
        throw new UnsupportedOperationException("the guarded exchange is not built yet");
    }
}
