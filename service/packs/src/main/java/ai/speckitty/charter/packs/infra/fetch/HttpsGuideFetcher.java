package ai.speckitty.charter.packs.infra.fetch;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetchRefusal.Reason;
import ai.speckitty.charter.packs.application.GuideFetcher;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;

/**
 * The guarded HTTPS adapter of {@link GuideFetcher} (NFR-004, research D8a): the service's only outbound HTTP call.
 *
 * <ul>
 *   <li><b>Direct.</b> The client is built with {@link HttpClient.Builder#NO_PROXY}: a JVM proxy
 *       ({@code https.proxyHost}) would resolve and connect on the service's behalf and void every address check, so
 *       it is never used. A deployment whose only egress is a proxy cannot fetch guides; it sends the content.
 *   <li><b>Every hop checked.</b> Redirects are never followed by the client ({@link HttpClient.Redirect#NEVER}).
 *       Before the first request and each followed redirect (301, 302, 303, 307, 308; at most
 *       {@code maxRedirects}) the target must be absolute {@code https}, carry no user information, name a host that
 *       is not an IP literal and is exactly on the allowlist, and resolve to global unicast addresses only.
 *   <li><b>Rebinding closed.</b> The client resolves the host again when it connects; that lookup goes through
 *       {@link GlobalAddressResolverProvider}, which refuses a non-global answer for every allowlisted host, so the
 *       connect cannot land on an address the check would have refused. Such a refusal is typed
 *       {@code NON_GLOBAL_ADDRESS} through the guard's refusal record ({@code GuardRefusals}), since the client
 *       reports it only as a failed connect.
 *   <li><b>One deadline.</b> Fixed once when the fetch starts; the resolution, every hop and the whole body run
 *       against what is left of it. A hop waits on its future for the remaining time and is then cancelled
 *       ({@code cancel(true)} aborts the exchange); {@code orTimeout} is not used, because completing the future
 *       would leave the exchange running.
 *   <li><b>Bounded body.</b> Streamed through a counting subscriber that fails past {@code maxBytes}.
 *   <li><b>Plain UTF-8 only.</b> {@code Content-Encoding} absent or {@code identity}; charset absent or UTF-8; the
 *       body decoded strictly.
 *   <li><b>No credentials.</b> No authenticator, no cookie handler; only {@code Accept} and {@code Accept-Encoding}
 *       are added to the request.
 * </ul>
 */
public final class HttpsGuideFetcher implements GuideFetcher, AutoCloseable {

    private static final Set<Integer> FOLLOWED_REDIRECTS = Set.of(301, 302, 303, 307, 308);

    private final GuideAllowlist allowlist;
    private final Duration timeout;
    private final long maxBytes;
    private final int maxRedirects;
    private final Resolver resolver;
    private final Transport transport;
    private final GuardRefusals guardRefusals;

    /** Name resolution as the fetcher's pre-check sees it; production uses the JVM resolver (and so the guard). */
    @FunctionalInterface
    interface Resolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    /** One request and its response; production wraps the guarded {@link HttpClient}. */
    @FunctionalInterface
    interface Transport extends AutoCloseable {
        CompletableFuture<HttpResponse<byte[]>> send(HttpRequest request, HttpResponse.BodyHandler<byte[]> bodyHandler);

        @Override
        default void close() {}
    }

    /**
     * The production fetcher: a direct {@link HttpClient} and the JVM's (guarded) resolver.
     *
     * @param allowlist the hosts a guide may be fetched from
     * @param timeout the deadline over the whole exchange
     * @param maxBytes the largest body accepted
     * @param maxRedirects the most redirects followed
     * @return the fetcher; closing it closes its client
     */
    public static HttpsGuideFetcher create(
            GuideAllowlist allowlist, Duration timeout, long maxBytes, int maxRedirects) {
        HttpClient client = newHttpClient(timeout);
        Transport transport = new Transport() {
            @Override
            public CompletableFuture<HttpResponse<byte[]>> send(
                    HttpRequest request, HttpResponse.BodyHandler<byte[]> bodyHandler) {
                return client.sendAsync(request, bodyHandler);
            }

            @Override
            public void close() {
                client.close();
            }
        };
        return new HttpsGuideFetcher(
                allowlist,
                timeout,
                maxBytes,
                maxRedirects,
                host -> List.of(InetAddress.getAllByName(host)),
                transport,
                GlobalAddressResolverProvider.refusals());
    }

    /**
     * The client every production fetch goes through: direct, never following redirects, no cookies, no
     * authenticator.
     *
     * @param connectTimeout the bound on one connect
     * @return the client
     */
    static HttpClient newHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .build();
    }

    HttpsGuideFetcher(
            GuideAllowlist allowlist,
            Duration timeout,
            long maxBytes,
            int maxRedirects,
            Resolver resolver,
            Transport transport,
            GuardRefusals guardRefusals) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("the guide fetch timeout must be positive");
        }
        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("the guide size cap must be positive and below 2 GiB");
        }
        if (maxRedirects < 0) {
            throw new IllegalArgumentException("the guide redirect limit must not be negative");
        }
        this.allowlist = allowlist;
        this.timeout = timeout;
        this.maxBytes = maxBytes;
        this.maxRedirects = maxRedirects;
        this.resolver = resolver;
        this.transport = transport;
        this.guardRefusals = guardRefusals;
    }

    @Override
    public String fetch(URI uri) throws GuideFetchRefusal {
        long deadline = System.nanoTime() + timeout.toNanos();
        URI current = uri;
        int redirects = 0;
        while (true) {
            String host = checkTarget(current);
            checkAddresses(host, deadline);
            HttpResponse<byte[]> response = exchange(withoutFragment(current), host, deadline);
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return decode(response, host);
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

    @Override
    public void close() {
        transport.close();
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
        String normalised = GuideAllowlist.normalise(host);
        if (isIpLiteral(normalised)) {
            throw new GuideFetchRefusal(Reason.IP_LITERAL, host, "the host is an IP literal");
        }
        if (!allowlist.allows(normalised)) {
            throw new GuideFetchRefusal(Reason.HOST_NOT_ALLOWED, host, "the host is not on the guide allowlist");
        }
        return normalised;
    }

    /**
     * Bracketed IPv6, or a last label that is numeric or hexadecimal: the forms the JDK parses as an address without
     * a lookup ({@code 127.0.0.1}, {@code 127.1}, {@code 2130706433}, {@code 0x7f.1}). No DNS top-level label is
     * numeric.
     */
    static boolean isIpLiteral(String normalisedHost) {
        if (normalisedHost.startsWith("[") || normalisedHost.contains(":")) {
            return true;
        }
        String last = normalisedHost.substring(normalisedHost.lastIndexOf('.') + 1);
        return !last.isEmpty() && (last.chars().allMatch(ch -> ch >= '0' && ch <= '9') || last.startsWith("0x"));
    }

    /** Resolves under the deadline and refuses unless every address is global unicast. */
    private void checkAddresses(String host, long deadline) throws GuideFetchRefusal {
        long started = System.nanoTime();
        CompletableFuture<List<InetAddress>> lookup = new CompletableFuture<>();
        Thread.ofVirtual().name("guide-fetch-resolve").start(() -> {
            try {
                lookup.complete(resolver.resolve(host));
            } catch (UnknownHostException | RuntimeException e) {
                lookup.completeExceptionally(e);
            }
        });
        List<InetAddress> addresses = await(lookup, host, deadline, started);
        if (addresses.isEmpty()) {
            throw new GuideFetchRefusal(Reason.UNREACHABLE, host, "the host resolves to no address");
        }
        if (!addresses.stream().allMatch(AddressPolicy::isGlobalUnicast)) {
            throw new GuideFetchRefusal(
                    Reason.NON_GLOBAL_ADDRESS, host, "the host resolves to an address that is not global unicast");
        }
    }

    private HttpResponse<byte[]> exchange(URI target, String host, long deadline) throws GuideFetchRefusal {
        HttpRequest request = HttpRequest.newBuilder(target)
                .GET()
                .timeout(Duration.ofNanos(remaining(host, deadline)))
                .header("Accept", "text/markdown, text/plain;q=0.9, */*;q=0.1")
                .header("Accept-Encoding", "identity")
                .build();
        long started = System.nanoTime();
        return await(transport.send(request, info -> new CappedBodySubscriber(maxBytes)), host, deadline, started);
    }

    /** Waits for the future for what is left of the deadline, cancelling it when the time runs out. */
    private <T> T await(CompletableFuture<T> future, String host, long deadline, long started)
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
            throw refusalFor(e.getCause(), host, started);
        } catch (GuideFetchRefusal e) {
            future.cancel(true);
            throw e;
        }
    }

    /**
     * Types a failed lookup or exchange. A failure the resolver guard caused is {@code NON_GLOBAL_ADDRESS} even when
     * {@code HttpClient} reports it as a bare connect failure (the guard refused the host since the step started).
     */
    private GuideFetchRefusal refusalFor(@Nullable Throwable failure, String host, long started) {
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
        if (guardRefusals.refusedSince(host, started)) {
            return new GuideFetchRefusal(
                    Reason.NON_GLOBAL_ADDRESS, host, "the host resolved to an address that is not global unicast");
        }
        String kind = failure == null ? "unknown failure" : failure.getClass().getSimpleName();
        return new GuideFetchRefusal(Reason.UNREACHABLE, host, "the host could not be reached (" + kind + ")");
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

    private static String decode(HttpResponse<byte[]> response, String host) throws GuideFetchRefusal {
        for (String value : response.headers().allValues("Content-Encoding")) {
            for (String coding : value.split(",", -1)) {
                String token = coding.strip();
                if (!token.isEmpty() && !"identity".equalsIgnoreCase(token)) {
                    throw new GuideFetchRefusal(Reason.ENCODED, host, "the body is content-encoded");
                }
            }
        }
        Optional<String> contentType = response.headers().firstValue("Content-Type");
        if (contentType.isPresent()) {
            String charset = charsetOf(contentType.get());
            if (charset != null && !isUtf8(charset)) {
                throw new GuideFetchRefusal(Reason.NOT_UTF8, host, "the declared charset is not UTF-8");
            }
        }
        try {
            CharBuffer text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Objects.requireNonNullElse(response.body(), new byte[0])));
            return text.toString();
        } catch (CharacterCodingException e) {
            throw new GuideFetchRefusal(Reason.NOT_UTF8, host, "the body is not valid UTF-8");
        }
    }

    private static @Nullable String charsetOf(String contentType) {
        String[] parts = contentType.split(";", -1);
        for (int i = 1; i < parts.length; i++) {
            String parameter = parts[i].strip();
            int equals = parameter.indexOf('=');
            if (equals > 0
                    && "charset".equalsIgnoreCase(parameter.substring(0, equals).strip())) {
                String value = parameter.substring(equals + 1).strip();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static boolean isUtf8(String charset) {
        try {
            return Charset.forName(charset).equals(StandardCharsets.UTF_8);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return false;
        }
    }
}
