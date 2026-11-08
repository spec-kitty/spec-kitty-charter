package ai.speckitty.charter.packs.infra.fetch;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetchRefusal.Reason;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

/**
 * The guarded HTTPS exchange (NFR-004, research D8a, R9): the service's only outbound HTTP call, shared by the guide
 * fetcher and the archive fetcher. What a body may be (its cap and the {@code Accept} it asks for) is the
 * {@link HttpsExchange.BodyPolicy} of each call; every other guard is the exchange's and is the same for both.
 *
 * <ul>
 *   <li><b>Direct.</b> The client is built with {@link HttpClient.Builder#NO_PROXY}: a JVM proxy
 *       ({@code https.proxyHost}) would resolve and connect on the service's behalf and void every address check, so
 *       it is never used. A deployment whose only egress is a proxy cannot fetch; it sends the content.
 *   <li><b>Every hop checked.</b> Redirects are never followed by the client ({@link HttpClient.Redirect#NEVER}).
 *       Before the first request and each followed redirect (301, 302, 303, 307, 308; at most
 *       {@code maxRedirects}) the target must be absolute {@code https} (the scheme in any letter case), name no port
 *       but 443, carry no user information, name a host that is not an IP literal and is exactly on the allowlist,
 *       and resolve to global unicast addresses only.
 *   <li><b>Rebinding closed.</b> The client resolves the host again when it connects; that lookup goes through
 *       {@link GlobalAddressResolverProvider}, which refuses a non-global answer for every allowlisted host, so the
 *       connect cannot land on an address the check would have refused. Such a refusal is typed
 *       {@code NON_GLOBAL_ADDRESS} through the guard's refusal record of this fetch ({@code GuardRefusals.Attempt}),
 *       since the client reports it only as a failed connect. Each exchange runs on a client of its own, whose
 *       every task works for the fetch, so a refusal is attributed to the fetch that caused it and to no other.
 *   <li><b>One deadline.</b> Fixed once when the fetch starts; the resolution, every hop and the whole body run
 *       against what is left of it. A hop waits on its future for the remaining time and is then cancelled
 *       ({@code cancel(true)} aborts the exchange); {@code orTimeout} is not used, because completing the future
 *       would leave the exchange running. The pre-check's lookup runs on a thread of its own, interrupted when the
 *       deadline passes. The JDK's built-in resolver blocks in the operating system's resolver
 *       ({@code getaddrinfo}), which an interrupt cannot stop: such a thread ends only when the operating system gives
 *       up (its resolver timeout and attempts, about ten seconds with common defaults). At most
 *       {@value #LOOKUP_SLOTS} lookups run at once, so lingering lookups cannot pile up; a fetch that finds none free
 *       is refused {@code UNREACHABLE} before it resolves anything.
 *   <li><b>Bounded body.</b> A success whose {@code Content-Length} declares more than the policy's cap is refused
 *       before its body is read; any other body is streamed through a counting subscriber that fails past the cap.
 *   <li><b>No credentials.</b> No authenticator, no cookie handler; only {@code Accept} and {@code Accept-Encoding}
 *       are added to the request.
 * </ul>
 *
 * <p>The exchange hands back the bytes and the headers as they came: whether a body is the right kind of body (a media
 * type, a character set, the leading bytes of an archive) is the caller's to judge.
 *
 * <p>A fetch typed {@code UNREACHABLE} logs its cause at DEBUG, by the exception classes and messages and the host,
 * with the URI's path and query cut from every message, as the refusal itself never names them.
 */
public final class GuardedHttpsExchange implements HttpsExchange {

    private static final Log LOGGER = LogFactory.getLog(GuardedHttpsExchange.class);

    private static final Set<Integer> FOLLOWED_REDIRECTS = Set.of(301, 302, 303, 307, 308);

    /** The most pre-check lookups that run at once, finished or abandoned at the deadline. */
    static final int LOOKUP_SLOTS = 8;

    /** The only port a fetch goes to; a URI that names no port uses it. */
    private static final int HTTPS_PORT = 443;

    private final GuideAllowlist allowlist;
    private final Duration timeout;
    private final int maxRedirects;
    private final Resolver resolver;
    private final Transport transport;
    private final Semaphore lookupSlots;

    /** Name resolution as the exchange's pre-check sees it; production uses the JVM resolver (and so the guard). */
    @FunctionalInterface
    interface Resolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    /**
     * One request and its response, for one fetch; production sends it on a guarded {@link HttpClient} of its own whose
     * every task works for the fetch's {@code attempt}.
     */
    @FunctionalInterface
    interface Transport {
        CompletableFuture<HttpResponse<byte[]>> send(
                HttpRequest request, HttpResponse.BodyHandler<byte[]> bodyHandler, GuardRefusals.Attempt attempt);
    }

    /**
     * The production exchange: a direct {@link HttpClient} and the JVM's (guarded) resolver.
     *
     * @param allowlist the hosts that may be fetched from
     * @param timeout the deadline over the whole exchange
     * @param maxRedirects the most redirects followed
     * @return the exchange
     */
    public static GuardedHttpsExchange create(GuideAllowlist allowlist, Duration timeout, int maxRedirects) {
        return new GuardedHttpsExchange(
                allowlist,
                timeout,
                maxRedirects,
                host -> List.of(InetAddress.getAllByName(host)),
                clientPerExchange(timeout, UnaryOperator.identity()),
                LOOKUP_SLOTS);
    }

    /**
     * The production transport: each exchange is sent on a client of its own ({@link #newHttpClient(Duration,
     * Executor)}), whose every task works for the fetch's attempt, so the guard's refusals are attributed to that fetch
     * alone. The client is stopped once the exchange is done: answered, failed or cancelled at the deadline.
     *
     * @param connectTimeout the bound on one connect
     * @param route the request actually sent for a checked one: the identity in production; a test rewrites the
     *     target to a loopback server
     * @return the transport
     */
    static Transport clientPerExchange(Duration connectTimeout, UnaryOperator<HttpRequest> route) {
        return (request, bodyHandler, attempt) -> {
            HttpClient client = newHttpClient(connectTimeout, attempt.executor(GuardedHttpsExchange::onVirtualThread));
            CompletableFuture<HttpResponse<byte[]>> sent = client.sendAsync(route.apply(request), bodyHandler);
            CompletableFuture<?> _ = sent.whenComplete((_, _) -> client.shutdownNow());
            return sent;
        };
    }

    private static void onVirtualThread(Runnable task) {
        Thread.ofVirtual().name("https-fetch").start(task);
    }

    /**
     * The client every production fetch goes through: direct, never following redirects, no cookies, no
     * authenticator.
     *
     * @param connectTimeout the bound on one connect
     * @return the client
     */
    static HttpClient newHttpClient(Duration connectTimeout) {
        return builder(connectTimeout).build();
    }

    /**
     * {@link #newHttpClient(Duration)}, its tasks run by the given executor.
     *
     * @param connectTimeout the bound on one connect
     * @param executor the executor of the client's tasks
     * @return the client
     */
    static HttpClient newHttpClient(Duration connectTimeout, Executor executor) {
        return builder(connectTimeout).executor(executor).build();
    }

    private static HttpClient.Builder builder(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout);
    }

    GuardedHttpsExchange(
            GuideAllowlist allowlist,
            Duration timeout,
            int maxRedirects,
            Resolver resolver,
            Transport transport,
            int lookupSlots) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("the HTTPS exchange timeout must be positive");
        }
        if (maxRedirects < 0) {
            throw new IllegalArgumentException("the HTTPS exchange redirect limit must not be negative");
        }
        if (lookupSlots <= 0) {
            throw new IllegalArgumentException("the HTTPS exchange needs at least one lookup slot");
        }
        this.allowlist = allowlist;
        this.timeout = timeout;
        this.maxRedirects = maxRedirects;
        this.resolver = resolver;
        this.transport = transport;
        this.lookupSlots = new Semaphore(lookupSlots);
    }

    @Override
    public Fetched fetch(URI uri, BodyPolicy policy) throws GuideFetchRefusal {
        long deadline = System.nanoTime() + timeout.toNanos();
        GuardRefusals.Attempt attempt = new GuardRefusals.Attempt();
        URI current = uri;
        int redirects = 0;
        while (true) {
            String host = checkTarget(current);
            checkAddresses(current, host, deadline, attempt);
            HttpResponse<byte[]> response = exchange(withoutFragment(current), host, policy, deadline, attempt);
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new Fetched(host, response.headers(), Objects.requireNonNullElse(response.body(), new byte[0]));
            }
            if (status < 300 || status >= 400) {
                throw new GuideFetchRefusal(Reason.HTTP_STATUS, host, "the response status is " + status);
            }
            if (!FOLLOWED_REDIRECTS.contains(status)) {
                throw new GuideFetchRefusal(Reason.REDIRECT_REFUSED, host, "status " + status + " is not followed");
            }
            if (redirects == maxRedirects) {
                throw new GuideFetchRefusal(
                        Reason.TOO_MANY_REDIRECTS, host, "more than " + maxRedirects + " redirects");
            }
            redirects++;
            current = redirectTarget(current, response, host);
        }
    }

    /** The per-hop URI checks; returns the normalised host. */
    private String checkTarget(URI target) throws GuideFetchRefusal {
        String host = target.getHost();
        if (!target.isAbsolute() || !"https".equalsIgnoreCase(target.getScheme())) {
            throw new GuideFetchRefusal(Reason.NOT_HTTPS, host, "only https is fetched");
        }
        String authority = target.getRawAuthority();
        if (target.getRawUserInfo() != null || (authority != null && authority.contains("@"))) {
            throw new GuideFetchRefusal(Reason.USERINFO, host, "the URI carries user information");
        }
        if (host == null) {
            throw new GuideFetchRefusal(
                    Reason.HOST_NOT_ALLOWED, null, "the URI has no plain DNS host (IDN and underscores are refused)");
        }
        if (target.getPort() != -1 && target.getPort() != HTTPS_PORT) {
            throw new GuideFetchRefusal(Reason.PORT_NOT_ALLOWED, host, "only port " + HTTPS_PORT + " is fetched");
        }
        String normalised = GuideAllowlist.normalise(host);
        if (isIpLiteral(normalised)) {
            throw new GuideFetchRefusal(Reason.IP_LITERAL, host, "the host is an IP literal");
        }
        if (!allowlist.allows(normalised)) {
            throw new GuideFetchRefusal(Reason.HOST_NOT_ALLOWED, host, "the host is not on the allowlist");
        }
        return normalised;
    }

    /**
     * Bracketed IPv6, or a last label that is numeric or hexadecimal: the forms the JDK parses as an address without
     * a lookup ({@code 127.0.0.1}, {@code 127.1}, {@code 2130706433}, {@code 0x7f.1}). No DNS top-level label is
     * numeric.
     */
    public static boolean isIpLiteral(String normalisedHost) {
        if (normalisedHost.startsWith("[") || normalisedHost.contains(":")) {
            return true;
        }
        String last = normalisedHost.substring(normalisedHost.lastIndexOf('.') + 1);
        return !last.isEmpty() && (last.chars().allMatch(ch -> ch >= '0' && ch <= '9') || last.startsWith("0x"));
    }

    /**
     * Resolves under the deadline and refuses unless every address is global unicast. The lookup runs on a thread of
     * its own in one of the lookup slots, and is interrupted when the deadline passes first.
     */
    private void checkAddresses(URI target, String host, long deadline, GuardRefusals.Attempt attempt)
            throws GuideFetchRefusal {
        if (!lookupSlots.tryAcquire()) {
            throw new GuideFetchRefusal(
                    Reason.UNREACHABLE, host, "too many name lookups are still running; try again later");
        }
        CompletableFuture<List<InetAddress>> lookup = new CompletableFuture<>();
        Thread lookupThread = Thread.ofVirtual().name("https-fetch-resolve").start(attempt.bound(() -> {
            try {
                lookup.complete(resolver.resolve(host));
            } catch (UnknownHostException | RuntimeException e) {
                lookup.completeExceptionally(e);
            } finally {
                lookupSlots.release();
            }
        }));
        List<InetAddress> addresses = List.of();
        boolean answered = false;
        try {
            addresses = await(lookup, target, host, deadline, attempt);
            answered = true;
        } finally {
            if (!answered) {
                // Outran the deadline (or was refused): stop the lookup where the resolver lets it (class comment).
                lookupThread.interrupt();
            }
        }
        if (addresses.isEmpty()) {
            throw new GuideFetchRefusal(Reason.UNREACHABLE, host, "the host resolves to no address");
        }
        if (!addresses.stream().allMatch(AddressPolicy::isGlobalUnicast)) {
            throw new GuideFetchRefusal(
                    Reason.NON_GLOBAL_ADDRESS, host, "the host resolves to an address that is not global unicast");
        }
    }

    private HttpResponse<byte[]> exchange(
            URI target, String host, BodyPolicy policy, long deadline, GuardRefusals.Attempt attempt)
            throws GuideFetchRefusal {
        HttpRequest request = HttpRequest.newBuilder(target)
                .GET()
                .timeout(Duration.ofNanos(remaining(host, deadline)))
                .header("Accept", policy.accept())
                .header("Accept-Encoding", "identity")
                .build();
        CompletableFuture<HttpResponse<byte[]>> sent = sendAs(attempt, request, policy);
        return await(sent, target, host, deadline, attempt);
    }

    /** Sends on the calling thread as the attempt's, for the part of the send the client runs before it returns. */
    private CompletableFuture<HttpResponse<byte[]>> sendAs(
            GuardRefusals.Attempt attempt, HttpRequest request, BodyPolicy policy) {
        return attempt.call(() -> transport.send(request, info -> bodySubscriber(info, policy.maxBytes()), attempt));
    }

    /**
     * The body's subscriber: a success that declares a {@code Content-Length} over the cap is refused before a byte of
     * its body is read; every other body is counted as it streams.
     */
    private static HttpResponse.BodySubscriber<byte[]> bodySubscriber(HttpResponse.ResponseInfo info, long maxBytes) {
        boolean success = info.statusCode() >= 200 && info.statusCode() < 300;
        long declared = info.headers().firstValueAsLong("Content-Length").orElse(-1L);
        return success && declared > maxBytes
                ? CappedBodySubscriber.refusing(maxBytes)
                : new CappedBodySubscriber(maxBytes);
    }

    /** Waits for the future for what is left of the deadline, cancelling it when the time runs out. */
    private static <T> T await(
            CompletableFuture<T> future, URI target, String host, long deadline, GuardRefusals.Attempt attempt)
            throws GuideFetchRefusal {
        try {
            return future.get(remaining(host, deadline), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new GuideFetchRefusal(Reason.TIMEOUT, host, "the exchange outran its deadline");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new GuideFetchRefusal(Reason.UNREACHABLE, host, "the fetch was interrupted");
        } catch (ExecutionException e) {
            throw refusalFor(e.getCause(), target, host, attempt);
        } catch (GuideFetchRefusal e) {
            future.cancel(true);
            throw e;
        }
    }

    /**
     * Types a failed lookup or exchange. A failure the resolver guard caused is {@code NON_GLOBAL_ADDRESS} even when
     * {@code HttpClient} reports it as a bare connect failure (the guard refused the host in this fetch). Any other
     * failure is {@code UNREACHABLE}, its cause logged at DEBUG without the URI's path or query.
     */
    private static GuideFetchRefusal refusalFor(
            @Nullable Throwable failure, URI target, String host, GuardRefusals.Attempt attempt) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof CappedBodySubscriber.BodyTooLargeException) {
                return new GuideFetchRefusal(Reason.TOO_LARGE, host, "the body exceeds the size cap");
            }
            if (cause instanceof NonGlobalAddressException) {
                return new GuideFetchRefusal(
                        Reason.NON_GLOBAL_ADDRESS, host, "the host resolves to an address that is not global unicast");
            }
            if (cause instanceof HttpTimeoutException) {
                return new GuideFetchRefusal(Reason.TIMEOUT, host, "the exchange outran its deadline");
            }
        }
        if (attempt.refused(host)) {
            return new GuideFetchRefusal(
                    Reason.NON_GLOBAL_ADDRESS, host, "the host resolved to an address that is not global unicast");
        }
        String kind = failure == null ? "unknown failure" : failure.getClass().getSimpleName();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("An HTTPS fetch from " + host + " is unreachable: " + causeOf(failure, target));
        }
        return new GuideFetchRefusal(Reason.UNREACHABLE, host, "the host could not be reached (" + kind + ")");
    }

    /**
     * The failure's cause chain for the log, each link as its class and message, with the URI, its path and its query
     * cut from every message.
     *
     * @param failure the failure
     * @param target the URI fetched
     * @return the chain, links joined by {@code <- }
     */
    static String causeOf(@Nullable Throwable failure, URI target) {
        if (failure == null) {
            return "unknown failure";
        }
        StringBuilder chain = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (!chain.isEmpty()) {
                chain.append(" <- ");
            }
            chain.append(cause.getClass().getName());
            String message = cause.getMessage();
            if (message != null) {
                chain.append(": ").append(redacted(message, target));
            }
        }
        return chain.toString();
    }

    private static String redacted(String message, URI target) {
        String out = message.replace(target.toString(), "<uri>");
        String query = target.getRawQuery();
        if (query != null && !query.isEmpty()) {
            out = out.replace(query, "<query>");
        }
        String path = target.getRawPath();
        if (path != null && path.length() > 1) {
            out = out.replace(path, "<path>");
        }
        return out;
    }

    private static long remaining(String host, long deadline) throws GuideFetchRefusal {
        long left = deadline - System.nanoTime();
        if (left <= 0) {
            throw new GuideFetchRefusal(Reason.TIMEOUT, host, "the exchange outran its deadline");
        }
        return left;
    }

    private static URI redirectTarget(URI current, HttpResponse<byte[]> response, String host)
            throws GuideFetchRefusal {
        Optional<String> location = response.headers().firstValue("Location");
        if (location.isEmpty() || location.get().isBlank()) {
            throw new GuideFetchRefusal(Reason.REDIRECT_REFUSED, host, "a redirect without a Location");
        }
        try {
            return current.resolve(URI.create(location.get().strip()));
        } catch (IllegalArgumentException e) {
            throw new GuideFetchRefusal(Reason.REDIRECT_REFUSED, host, "the redirect Location is not a valid URI");
        }
    }

    private static URI withoutFragment(URI uri) {
        String text = uri.toString();
        int hash = text.indexOf('#');
        return hash < 0 ? uri : URI.create(text.substring(0, hash));
    }
}
