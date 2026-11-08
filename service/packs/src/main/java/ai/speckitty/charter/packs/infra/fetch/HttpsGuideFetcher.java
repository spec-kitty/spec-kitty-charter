package ai.speckitty.charter.packs.infra.fetch;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetchRefusal.Reason;
import ai.speckitty.charter.packs.application.GuideFetcher;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange.BodyPolicy;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange.Fetched;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * The guarded HTTPS adapter of {@link GuideFetcher} (NFR-004, research D8a): the guide's side of the shared
 * {@link GuardedHttpsExchange}. Every guard of the exchange applies (https only, the exact allowlist, global unicast
 * addresses only, the resolver guard against rebinding, checked redirect hops, one deadline, no proxy, no credentials);
 * this class is the guide's body policy, and the judgement of what comes back:
 *
 * <ul>
 *   <li><b>Bounded body.</b> At most {@code maxBytes}, the exchange's cap.
 *   <li><b>Markdown text only.</b> A {@code Content-Type} of {@code text/markdown}, {@code text/x-markdown} or
 *       {@code text/plain} (an absent one is refused); {@code Content-Encoding} absent or {@code identity}; charset
 *       absent or UTF-8; the body decoded strictly.
 * </ul>
 */
public final class HttpsGuideFetcher implements GuideFetcher, AutoCloseable {

    /** The most pre-check lookups that run at once, finished or abandoned at the deadline. */
    static final int LOOKUP_SLOTS = GuardedHttpsExchange.LOOKUP_SLOTS;

    /** The media types a guide is accepted as, lower-cased, without parameters. */
    private static final Set<String> MEDIA_TYPES = Set.of("text/markdown", "text/x-markdown", "text/plain");

    private static final String ACCEPT = "text/markdown, text/x-markdown;q=0.9, text/plain;q=0.8";

    private final GuardedHttpsExchange exchange;
    private final BodyPolicy policy;

    /** Name resolution as the fetcher's pre-check sees it; production uses the JVM resolver (and so the guard). */
    @FunctionalInterface
    interface Resolver extends GuardedHttpsExchange.Resolver {}

    /**
     * One request and its response, for one fetch; production sends it on a guarded {@link java.net.http.HttpClient} of
     * its own whose every task works for the fetch's {@code attempt}.
     */
    @FunctionalInterface
    interface Transport extends GuardedHttpsExchange.Transport {}

    /**
     * The production fetcher: a direct {@link java.net.http.HttpClient} and the JVM's (guarded) resolver.
     *
     * @param allowlist the hosts a guide may be fetched from
     * @param timeout the deadline over the whole exchange
     * @param maxBytes the largest body accepted
     * @param maxRedirects the most redirects followed
     * @return the fetcher
     */
    public static HttpsGuideFetcher create(
            GuideAllowlist allowlist, Duration timeout, long maxBytes, int maxRedirects) {
        return new HttpsGuideFetcher(
                new BodyPolicy(maxBytes, ACCEPT), GuardedHttpsExchange.create(allowlist, timeout, maxRedirects));
    }

    /**
     * The production transport: each exchange is sent on a client of its own, whose every task works for the fetch's
     * attempt.
     *
     * @param connectTimeout the bound on one connect
     * @param route the request actually sent for a checked one: the identity in production; a test rewrites the
     *     target to a loopback server
     * @return the transport
     */
    static Transport clientPerExchange(Duration connectTimeout, UnaryOperator<HttpRequest> route) {
        return GuardedHttpsExchange.clientPerExchange(connectTimeout, route)::send;
    }

    /**
     * The client every production fetch goes through: direct, never following redirects, no cookies, no
     * authenticator.
     *
     * @param connectTimeout the bound on one connect
     * @return the client
     */
    static java.net.http.HttpClient newHttpClient(Duration connectTimeout) {
        return GuardedHttpsExchange.newHttpClient(connectTimeout);
    }

    HttpsGuideFetcher(
            GuideAllowlist allowlist,
            Duration timeout,
            long maxBytes,
            int maxRedirects,
            Resolver resolver,
            Transport transport,
            int lookupSlots) {
        this(
                new BodyPolicy(maxBytes, ACCEPT),
                new GuardedHttpsExchange(allowlist, timeout, maxRedirects, resolver, transport, lookupSlots));
    }

    private HttpsGuideFetcher(BodyPolicy policy, GuardedHttpsExchange exchange) {
        this.policy = policy;
        this.exchange = exchange;
    }

    @Override
    public String fetch(URI uri) throws GuideFetchRefusal {
        Fetched fetched = exchange.fetch(uri, policy);
        return decode(fetched);
    }

    /** Nothing to close: each exchange's client is stopped when the exchange ends. */
    @Override
    public void close() {}

    /**
     * Bracketed IPv6, or a last label that is numeric or hexadecimal: the forms the JDK parses as an address without
     * a lookup.
     *
     * @param normalisedHost the normalised host
     * @return whether it is an IP literal
     */
    static boolean isIpLiteral(String normalisedHost) {
        return GuardedHttpsExchange.isIpLiteral(normalisedHost);
    }

    /**
     * The failure's cause chain for the log, with the URI, its path and its query cut from every message.
     *
     * @param failure the failure
     * @param target the URI fetched
     * @return the chain, links joined by {@code <- }
     */
    static String causeOf(@Nullable Throwable failure, URI target) {
        return GuardedHttpsExchange.causeOf(failure, target);
    }

    private static String decode(Fetched fetched) throws GuideFetchRefusal {
        String host = fetched.host();
        HttpHeaders headers = fetched.headers();
        for (String value : headers.allValues("Content-Encoding")) {
            for (String coding : value.split(",", -1)) {
                String token = coding.strip();
                if (!token.isEmpty() && !"identity".equalsIgnoreCase(token)) {
                    throw new GuideFetchRefusal(Reason.ENCODED, host, "the body is content-encoded");
                }
            }
        }
        Optional<String> contentType = headers.firstValue("Content-Type");
        if (contentType.isEmpty()) {
            throw new GuideFetchRefusal(Reason.CONTENT_TYPE, host, "the response declares no Content-Type");
        }
        if (!MEDIA_TYPES.contains(mediaTypeOf(contentType.get()))) {
            throw new GuideFetchRefusal(
                    Reason.CONTENT_TYPE, host, "the Content-Type is not text/markdown, text/x-markdown or text/plain");
        }
        String charset = charsetOf(contentType.get());
        if (charset != null && !isUtf8(charset)) {
            throw new GuideFetchRefusal(Reason.NOT_UTF8, host, "the declared charset is not UTF-8");
        }
        try {
            CharBuffer text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(fetched.toByteArray()));
            return text.toString();
        } catch (CharacterCodingException e) {
            throw new GuideFetchRefusal(Reason.NOT_UTF8, host, "the body is not valid UTF-8");
        }
    }

    /** The media type of a {@code Content-Type} value: its type and subtype, lower-cased, without parameters. */
    private static String mediaTypeOf(String contentType) {
        int semicolon = contentType.indexOf(';');
        String type = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return type.strip().toLowerCase(Locale.ROOT);
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
