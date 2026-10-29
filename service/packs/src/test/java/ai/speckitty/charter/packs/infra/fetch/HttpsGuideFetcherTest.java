package ai.speckitty.charter.packs.infra.fetch;

import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.GLOBAL;
import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetchRefusal.Reason;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.stream.Stream;
import javax.net.ssl.SSLSession;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The offline matrix of the guarded fetcher (T036, NFR-004, US5-AS5): every refusal with an accepted value next to
 * it. Requests go to a {@link FakeTransport} that answers from canned responses, reads bodies through the fetcher's
 * own body handler (so the streamed cap is the production code) and records every request, header and connect
 * target. Names resolve through stub resolvers. No test in this class opens a socket.
 */
class HttpsGuideFetcherTest {

    private static final String HOST = "guide.example";
    private static final String GUIDE = "# Guide\n\nUse the tool.\n";
    private static final int MIB = 1024 * 1024;

    private final Map<String, List<InetAddress>> dns = new HashMap<>(
            Map.of(HOST, List.of(GLOBAL), "mirror.example", List.of(GLOBAL), "inner.example", List.of(PRIVATE)));
    private final FakeTransport transport = new FakeTransport();

    private HttpsGuideFetcher fetcher(String... allowed) {
        return fetcher(Duration.ofSeconds(10), allowed);
    }

    private HttpsGuideFetcher fetcher(Duration timeout, String... allowed) {
        return new HttpsGuideFetcher(
                GuideAllowlist.of(List.of(allowed)), timeout, MIB, 3, this::lookUp, transport, new GuardRefusals());
    }

    private List<InetAddress> lookUp(String host) throws UnknownHostException {
        List<InetAddress> addresses = dns.get(host);
        if (addresses == null) {
            throw new UnknownHostException(host);
        }
        return addresses;
    }

    private static void assertRefused(HttpsGuideFetcher fetcher, String uri, Reason reason) {
        assertThatThrownBy(() -> fetcher.fetch(URI.create(uri)))
                .isInstanceOfSatisfying(
                        GuideFetchRefusal.class,
                        refusal -> assertThat(refusal.reason())
                                .as(refusal.getMessage())
                                .isEqualTo(reason));
    }

    @Test
    void anAllowlistedHttpsGuideIsFetchedAsUtf8Text() throws GuideFetchRefusal {
        transport.route("https://guide.example/g.md", Canned.ok(GUIDE));

        assertThat(fetcher(HOST).fetch(URI.create("https://guide.example/g.md")))
                .isEqualTo(GUIDE);
    }

    @Nested
    class TheUriItself {

        @ParameterizedTest
        @ValueSource(
                strings = {"http://guide.example/g.md", "ftp://guide.example/g.md", "g.md", "//guide.example/g.md"})
        void anythingButAbsoluteHttpsIsRefused(String uri) throws GuideFetchRefusal {
            assertRefused(fetcher(HOST), uri, Reason.NOT_HTTPS);
            transport.routeAnyHost("/g.md", Canned.ok(GUIDE));
            assertThat(fetcher(HOST).fetch(URI.create("HTTPS://guide.example/g.md")))
                    .isEqualTo(GUIDE);
            assertThat(transport.requests).hasSize(1);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "https://user@guide.example/g.md",
                    "https://user:secret@guide.example/g.md",
                    "https://guide.example@inner.example/g.md",
                    "https://user@a_b.example/g.md"
                })
        void userInformationIsRefused(String uri) throws GuideFetchRefusal {
            assertRefused(fetcher(HOST, "inner.example"), uri, Reason.USERINFO);
            assertThat(transport.requests).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "https://127.0.0.1/g.md",
                    "https://[::1]/g.md",
                    "https://[::ffff:127.0.0.1]/g.md",
                    "https://2130706433/g.md",
                    "https://8.8.8.8/g.md"
                })
        void anIpLiteralIsRefusedEvenWhenListed(String uri) {
            String listed = URI.create(uri).getHost();
            assertRefused(fetcher(HOST, listed), uri, Reason.IP_LITERAL);
            assertThat(transport.requests).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "https://other.example/g.md",
                    "https://evil-guide.example/g.md",
                    "https://guide.example.evil.example/g.md",
                    "https://sub.guide.example/g.md"
                })
        void aHostNotExactlyOnTheAllowlistIsRefused(String uri) {
            assertRefused(fetcher(HOST), uri, Reason.HOST_NOT_ALLOWED);
            assertThat(transport.requests).isEmpty();
        }

        /** IDN, underscores and dotted forms {@link URI} does not read as a host: no host, even if listed as written. */
        @ParameterizedTest
        @ValueSource(
                strings = {
                    "https://exämple.com/g.md",
                    "https://a_b.example/g.md",
                    "https://127.1/g.md",
                    "https://0x7f.1/g.md",
                    "https://93.184.215.14./g.md"
                })
        void aUriWithoutAPlainDnsHostIsRefusedEvenWhenItsAuthorityIsListed(String uri) {
            assertRefused(fetcher(HOST, URI.create(uri).getRawAuthority()), uri, Reason.HOST_NOT_ALLOWED);
            assertRefused(fetcher(HOST), "https:///g.md", Reason.HOST_NOT_ALLOWED);
            assertThat(transport.requests).isEmpty();
        }

        @Test
        void theEmptyAllowlistRefusesEveryHost() {
            assertRefused(fetcher(), "https://guide.example/g.md", Reason.HOST_NOT_ALLOWED);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {"https://GUIDE.Example/g.md", "https://guide.example./g.md", "https://Guide.EXAMPLE./g.md"})
        void caseAndATrailingDotAreNormalisedOnBothSides(String uri) throws GuideFetchRefusal {
            transport.routeAnyHost("/g.md", Canned.ok(GUIDE));

            assertThat(fetcher("Guide.Example.").fetch(URI.create(uri))).isEqualTo(GUIDE);
        }

        @Test
        void aRefusalNamesTheHostAndNeverEchoesThePathOrQuery() {
            assertThatThrownBy(() -> fetcher(HOST).fetch(URI.create("https://other.example/secret-path?token=s3cr3t")))
                    .hasMessageContaining("other.example")
                    .hasMessageNotContaining("s3cr3t")
                    .hasMessageNotContaining("secret-path");
        }

        @Test
        void theFragmentIsNotSent() throws GuideFetchRefusal {
            transport.route("https://guide.example/g.md", Canned.ok(GUIDE));

            assertThat(fetcher(HOST).fetch(URI.create("https://guide.example/g.md#usage")))
                    .isEqualTo(GUIDE);
            assertThat(transport.requests.getFirst().uri()).hasNoFragment();
        }
    }

    @Nested
    class Addresses {

        @ParameterizedTest(name = "{0}: {1} refused, {2} accepted")
        @MethodSource("ai.speckitty.charter.packs.infra.fetch.AddressClasses#refusedWithAcceptedNeighbour")
        void everyNonGlobalClassIsRefusedBeforeAnyRequest(
                String addressClass, InetAddress refused, InetAddress accepted) throws GuideFetchRefusal {
            transport.route("https://guide.example/g.md", Canned.ok(GUIDE));
            dns.put(HOST, List.of(refused));
            assertRefused(fetcher(HOST), "https://guide.example/g.md", Reason.NON_GLOBAL_ADDRESS);
            assertThat(transport.requests).as(addressClass).isEmpty();

            dns.put(HOST, List.of(accepted));
            assertThat(fetcher(HOST).fetch(URI.create("https://guide.example/g.md")))
                    .isEqualTo(GUIDE);
        }

        @Test
        void aMixedAnswerWithOneGlobalAndOnePrivateAddressIsRefused() {
            dns.put(HOST, List.of(GLOBAL, PRIVATE));

            assertRefused(fetcher(HOST), "https://guide.example/g.md", Reason.NON_GLOBAL_ADDRESS);
            assertThat(transport.requests).isEmpty();
        }

        @Test
        void aLookupTheResolverGuardRefusedIsANonGlobalRefusal() {
            HttpsGuideFetcher guarded = new HttpsGuideFetcher(
                    GuideAllowlist.of(List.of(HOST)),
                    Duration.ofSeconds(10),
                    MIB,
                    3,
                    host -> {
                        throw new NonGlobalAddressException(host);
                    },
                    transport,
                    new GuardRefusals());

            assertRefused(guarded, "https://guide.example/g.md", Reason.NON_GLOBAL_ADDRESS);
        }

        @Test
        void anUnresolvableHostIsUnreachable() {
            assertRefused(fetcher("missing.example"), "https://missing.example/g.md", Reason.UNREACHABLE);
        }
    }

    @Nested
    class Redirects {

        @ParameterizedTest
        @ValueSource(ints = {301, 302, 303, 307, 308})
        void eachFollowableRedirectIsFollowedAndRechecked(int status) throws GuideFetchRefusal {
            transport.route("https://guide.example/a", Canned.redirect(status, "https://mirror.example/g.md"));
            transport.route("https://mirror.example/g.md", Canned.ok(GUIDE));

            assertThat(fetcher(HOST, "mirror.example").fetch(URI.create("https://guide.example/a")))
                    .isEqualTo(GUIDE);
        }

        @Test
        void threeRedirectsAreAcceptedAndAFourthIsRefused() throws GuideFetchRefusal {
            transport.route("https://guide.example/1", Canned.redirect(302, "/2"));
            transport.route("https://guide.example/2", Canned.redirect(302, "/3"));
            transport.route("https://guide.example/3", Canned.redirect(302, "4"));
            transport.route("https://guide.example/4", Canned.ok(GUIDE));
            assertThat(fetcher(HOST).fetch(URI.create("https://guide.example/1")))
                    .isEqualTo(GUIDE);

            transport.route("https://guide.example/0", Canned.redirect(302, "/1"));
            assertRefused(fetcher(HOST), "https://guide.example/0", Reason.TOO_MANY_REDIRECTS);
        }

        @Test
        void aRedirectToPlainHttpIsRefused() {
            transport.route("https://guide.example/a", Canned.redirect(301, "http://guide.example/g.md"));

            assertRefused(fetcher(HOST), "https://guide.example/a", Reason.NOT_HTTPS);
            assertThat(transport.requested()).containsExactly("https://guide.example/a");
        }

        @Test
        void aRedirectToAHostOffTheAllowlistIsRefused() {
            transport.route("https://guide.example/a", Canned.redirect(302, "https://mirror.example/g.md"));

            assertRefused(fetcher(HOST), "https://guide.example/a", Reason.HOST_NOT_ALLOWED);
            assertThat(transport.requested()).containsExactly("https://guide.example/a");
        }

        @Test
        void aRedirectToAnAllowlistedHostWithAPrivateAddressIsRefusedBeforeConnecting() {
            transport.route("https://guide.example/a", Canned.redirect(307, "https://inner.example/g.md"));

            assertRefused(fetcher(HOST, "inner.example"), "https://guide.example/a", Reason.NON_GLOBAL_ADDRESS);
            assertThat(transport.requested()).containsExactly("https://guide.example/a");
        }

        @Test
        void aRedirectCarryingUserInformationOrAnIpLiteralIsRefused() {
            transport.route("https://guide.example/a", Canned.redirect(302, "https://u@mirror.example/g.md"));
            transport.route("https://guide.example/b", Canned.redirect(302, "https://10.0.0.1/g.md"));

            assertRefused(fetcher(HOST, "mirror.example"), "https://guide.example/a", Reason.USERINFO);
            assertRefused(fetcher(HOST, "10.0.0.1"), "https://guide.example/b", Reason.IP_LITERAL);
        }

        @ParameterizedTest
        @ValueSource(ints = {300, 304, 305, 306, 399})
        void aThreeHundredThatIsNotAFollowableRedirectIsRefused(int status) {
            transport.route("https://guide.example/a", Canned.redirect(status, "https://guide.example/g.md"));

            assertRefused(fetcher(HOST), "https://guide.example/a", Reason.REDIRECT_REFUSED);
        }

        @Test
        void aRedirectWithoutALocationIsRefused() {
            transport.route("https://guide.example/a", new Canned(302, Map.of(), List.of(), false));

            assertRefused(fetcher(HOST), "https://guide.example/a", Reason.REDIRECT_REFUSED);
        }

        @ParameterizedTest
        @ValueSource(ints = {199, 400, 404, 500, 503})
        void aFinalStatusOutsideTwoHundredIsRefused(int status) {
            transport.route("https://guide.example/a", new Canned(status, Map.of(), List.of(), false));

            assertRefused(fetcher(HOST), "https://guide.example/a", Reason.HTTP_STATUS);
        }
    }

    /**
     * DNS rebinding: the name answers a global address to the check and a private one when the client connects. The
     * fake transport resolves the host again at connect time, as {@link HttpClient} does, and records where it would
     * connect. With the resolver guard on that path the connect lookup is refused, typed as a non-global address, and nothing is ever connected to;
     * the control without the guard shows the private address the guard keeps the client from.
     */
    @Nested
    class Rebinding {

        @Test
        void theGuardRefusesTheConnectLookupSoNoConnectionReachesThePrivateAddress() {
            Rebinder rebinder = new Rebinder(GLOBAL, PRIVATE);
            GuardRefusals refusals = new GuardRefusals();
            GlobalAddressResolverProvider.Guard guard =
                    new GlobalAddressResolverProvider.Guard(rebinder, () -> GuideAllowlist.of(List.of(HOST)), refusals);
            HttpsGuideFetcher.Resolver viaGuard =
                    host -> guard.lookupByName(host, Rebinder.ANY).toList();
            transport.connectThrough(viaGuard);
            transport.route("https://guide.example/g.md", Canned.ok(GUIDE));
            HttpsGuideFetcher fetcher = new HttpsGuideFetcher(
                    GuideAllowlist.of(List.of(HOST)), Duration.ofSeconds(10), MIB, 3, viaGuard, transport, refusals);

            assertRefused(fetcher, "https://guide.example/g.md", Reason.NON_GLOBAL_ADDRESS);
            assertThat(rebinder.lookups).isEqualTo(2);
            assertThat(transport.connectTargets).isEmpty();
        }

        @Test
        void withoutTheGuardTheSameRebindingWouldConnectToThePrivateAddress() throws GuideFetchRefusal {
            Rebinder rebinder = new Rebinder(GLOBAL, PRIVATE);
            HttpsGuideFetcher.Resolver unguarded =
                    host -> rebinder.lookupByName(host, Rebinder.ANY).toList();
            transport.connectThrough(unguarded);
            transport.route("https://guide.example/g.md", Canned.ok(GUIDE));
            HttpsGuideFetcher fetcher = new HttpsGuideFetcher(
                    GuideAllowlist.of(List.of(HOST)),
                    Duration.ofSeconds(10),
                    MIB,
                    3,
                    unguarded,
                    transport,
                    new GuardRefusals());

            assertThat(fetcher.fetch(URI.create("https://guide.example/g.md"))).isEqualTo(GUIDE);
            assertThat(transport.connectTargets).containsExactly(PRIVATE);
        }

        @Test
        void aStableGlobalAnswerConnectsToTheCheckedAddress() throws GuideFetchRefusal {
            Rebinder stable = new Rebinder(GLOBAL, GLOBAL);
            GuardRefusals refusals = new GuardRefusals();
            GlobalAddressResolverProvider.Guard guard =
                    new GlobalAddressResolverProvider.Guard(stable, () -> GuideAllowlist.of(List.of(HOST)), refusals);
            HttpsGuideFetcher.Resolver viaGuard =
                    host -> guard.lookupByName(host, Rebinder.ANY).toList();
            transport.connectThrough(viaGuard);
            transport.route("https://guide.example/g.md", Canned.ok(GUIDE));
            HttpsGuideFetcher fetcher = new HttpsGuideFetcher(
                    GuideAllowlist.of(List.of(HOST)), Duration.ofSeconds(10), MIB, 3, viaGuard, transport, refusals);

            assertThat(fetcher.fetch(URI.create("https://guide.example/g.md"))).isEqualTo(GUIDE);
            assertThat(transport.connectTargets).containsExactly(GLOBAL);
        }
    }

    @Nested
    class Size {

        @Test
        void exactlyOneMebibyteIsAccepted() throws GuideFetchRefusal {
            transport.route("https://guide.example/g.md", Canned.chunked(MIB, 64 * 1024));

            assertThat(fetcher(HOST).fetch(URI.create("https://guide.example/g.md")))
                    .hasSize(MIB);
        }

        @Test
        void oneByteMoreIsRefusedWhileStreamingWithoutReadingTheRest() {
            transport.route("https://guide.example/g.md", Canned.chunked(4 * MIB + 1, 64 * 1024));

            assertRefused(fetcher(HOST), "https://guide.example/g.md", Reason.TOO_LARGE);
            assertThat(transport.cancelled).isTrue();
            assertThat(transport.chunksDelivered).isEqualTo(17);

            transport.route("https://guide.example/h.md", Canned.chunked(MIB + 1, 64 * 1024));
            assertRefused(fetcher(HOST), "https://guide.example/h.md", Reason.TOO_LARGE);
        }
    }

    @Nested
    class Deadline {

        @Test
        void anExchangeThatOutrunsTheDeadlineIsRefusedAndCancelled() throws GuideFetchRefusal {
            transport.route("https://guide.example/slow", Canned.hanging());

            assertRefused(fetcher(Duration.ofMillis(200), HOST), "https://guide.example/slow", Reason.TIMEOUT);
            assertThat(transport.hanging)
                    .singleElement()
                    .satisfies(f -> assertThat(f.isCancelled()).isTrue());

            transport.route("https://guide.example/fast", Canned.ok(GUIDE));
            assertThat(fetcher(Duration.ofMillis(200), HOST).fetch(URI.create("https://guide.example/fast")))
                    .isEqualTo(GUIDE);
        }

        @Test
        void aSlowLookupCountsAgainstTheSameDeadline() {
            HttpsGuideFetcher slowDns = new HttpsGuideFetcher(
                    GuideAllowlist.of(List.of(HOST)),
                    Duration.ofMillis(200),
                    MIB,
                    3,
                    host -> {
                        try {
                            Thread.sleep(5_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return List.of(GLOBAL);
                    },
                    transport,
                    new GuardRefusals());
            long started = System.nanoTime();

            assertRefused(slowDns, "https://guide.example/g.md", Reason.TIMEOUT);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
            assertThat(transport.requests).isEmpty();
        }
    }

    @Nested
    class Content {

        @ParameterizedTest
        @ValueSource(strings = {"gzip", "br", "deflate", "identity, gzip"})
        void aContentEncodingOtherThanIdentityIsRefused(String encoding) throws GuideFetchRefusal {
            transport.route("https://guide.example/e", Canned.ok(GUIDE).with("Content-Encoding", encoding));
            assertRefused(fetcher(HOST), "https://guide.example/e", Reason.ENCODED);

            transport.route("https://guide.example/i", Canned.ok(GUIDE).with("Content-Encoding", "identity"));
            assertThat(fetcher(HOST).fetch(URI.create("https://guide.example/i")))
                    .isEqualTo(GUIDE);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "text/plain; charset=ISO-8859-1",
                    "text/markdown;charset=utf-16",
                    "text/plain; charset=bogus!"
                })
        void aCharsetOtherThanUtf8IsRefused(String contentType) {
            transport.route("https://guide.example/c", Canned.ok(GUIDE).with("Content-Type", contentType));

            assertRefused(fetcher(HOST), "https://guide.example/c", Reason.NOT_UTF8);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "text/markdown",
                    "text/markdown; charset=utf-8",
                    "text/plain; charset=\"UTF-8\"",
                    "text/plain;CHARSET=utf8"
                })
        void anAbsentOrUtf8CharsetIsAccepted(String contentType) throws GuideFetchRefusal {
            transport.route(
                    "https://guide.example/c", Canned.ok("# Gids – één\n").with("Content-Type", contentType));

            assertThat(fetcher(HOST).fetch(URI.create("https://guide.example/c")))
                    .isEqualTo("# Gids – één\n");
        }

        @ParameterizedTest
        @MethodSource("ai.speckitty.charter.packs.infra.fetch.HttpsGuideFetcherTest#malformedUtf8")
        void malformedUtf8IsRefused(byte[] body) {
            transport.route("https://guide.example/m", Canned.bytes(body));

            assertRefused(fetcher(HOST), "https://guide.example/m", Reason.NOT_UTF8);
        }
    }

    static Stream<byte[]> malformedUtf8() {
        return Stream.of(
                new byte[] {(byte) 0xc3, 0x28}, // truncated two-byte sequence
                new byte[] {(byte) 0xc0, (byte) 0xaf}, // overlong '/'
                new byte[] {(byte) 0xed, (byte) 0xa0, (byte) 0x80}, // UTF-16 surrogate
                new byte[] {'#', ' ', (byte) 0xff});
    }

    @Test
    void noCredentialOrCookieHeaderIsEverSentAndIdentityIsAsked() throws GuideFetchRefusal {
        transport.route("https://guide.example/a", Canned.redirect(302, "https://mirror.example/g.md"));
        transport.route("https://mirror.example/g.md", Canned.ok(GUIDE));

        fetcher(HOST, "mirror.example").fetch(URI.create("https://guide.example/a"));

        assertThat(transport.requests).hasSize(2).allSatisfy(request -> {
            assertThat(request.headers().map()).doesNotContainKeys("Authorization", "Cookie", "Proxy-Authorization");
            assertThat(request.headers().firstValue("Accept-Encoding")).contains("identity");
            assertThat(request.method()).isEqualTo("GET");
        });
    }

    @Test
    void theProductionClientIsDirectFollowsNoRedirectAndCarriesNoCookiesOrAuthenticator() {
        try (HttpClient client = HttpsGuideFetcher.newHttpClient(Duration.ofSeconds(10))) {
            assertThat(client.proxy()).containsSame(HttpClient.Builder.NO_PROXY);
            assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
            assertThat(client.cookieHandler()).isEmpty();
            assertThat(client.authenticator()).isEmpty();
            assertThat(client.connectTimeout()).contains(Duration.ofSeconds(10));
        }
    }

    @Test
    void theProductionFetcherWithTheDefaultEmptyAllowlistRefusesBeforeAnyNetworkUse() {
        try (HttpsGuideFetcher production =
                HttpsGuideFetcher.create(GuideAllowlist.EMPTY, Duration.ofSeconds(10), MIB, 3)) {
            assertRefused(production, "https://guide.example/g.md", Reason.HOST_NOT_ALLOWED);
        }
    }

    @Test
    void invalidLimitsAreRefusedAtConstruction() {
        GuideAllowlist list = GuideAllowlist.of(List.of(HOST));
        assertThatThrownBy(() -> new HttpsGuideFetcher(
                        list, Duration.ZERO, MIB, 3, this::lookUp, transport, new GuardRefusals()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpsGuideFetcher(
                        list, Duration.ofSeconds(1), 0, 3, this::lookUp, transport, new GuardRefusals()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpsGuideFetcher(
                        list, Duration.ofSeconds(1), MIB, -1, this::lookUp, transport, new GuardRefusals()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- test doubles --------------------------------------------------------------------------------------------

    /** A canned response: status, headers, body chunks; {@code hang} never answers. */
    record Canned(int status, Map<String, List<String>> headers, List<byte[]> chunks, boolean hang) {

        static Canned ok(String body) {
            return bytes(body.getBytes(StandardCharsets.UTF_8));
        }

        static Canned bytes(byte[] body) {
            return new Canned(200, Map.of(), List.of(body), false);
        }

        static Canned chunked(int size, int chunk) {
            List<byte[]> chunks = new ArrayList<>();
            for (int sent = 0; sent < size; sent += chunk) {
                byte[] bytes = new byte[Math.min(chunk, size - sent)];
                Arrays.fill(bytes, (byte) 'a');
                chunks.add(bytes);
            }
            return new Canned(200, Map.of(), chunks, false);
        }

        static Canned redirect(int status, String location) {
            return new Canned(status, Map.of("Location", List.of(location)), List.of(), false);
        }

        static Canned hanging() {
            return new Canned(200, Map.of(), List.of(), true);
        }

        Canned with(String name, String value) {
            Map<String, List<String>> more = new HashMap<>(headers);
            more.put(name, List.of(value));
            return new Canned(status, more, chunks, hang);
        }
    }

    /** Answers 'global' to the first lookup and the second address to every later one. */
    static final class Rebinder implements InetAddressResolver {
        static final LookupPolicy ANY = LookupPolicy.of(LookupPolicy.IPV4 | LookupPolicy.IPV6);
        private final Deque<InetAddress> answers = new ArrayDeque<>();
        private final InetAddress later;
        int lookups;

        Rebinder(InetAddress first, InetAddress later) {
            answers.add(first);
            this.later = later;
        }

        @Override
        public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) {
            lookups++;
            InetAddress next = answers.poll();
            return Stream.of(next == null ? later : next);
        }

        @Override
        public String lookupByAddress(byte[] addr) throws UnknownHostException {
            throw new UnknownHostException("no reverse lookups");
        }
    }

    /** A transport that answers from canned responses and records what the client would have done. */
    static final class FakeTransport implements HttpsGuideFetcher.Transport {
        private final Map<String, Canned> routes = new HashMap<>();
        private final Map<String, Canned> anyHostRoutes = new HashMap<>();
        private HttpsGuideFetcher.@Nullable Resolver connect;
        final List<HttpRequest> requests = new ArrayList<>();
        final List<InetAddress> connectTargets = new ArrayList<>();
        final List<CompletableFuture<HttpResponse<byte[]>>> hanging = new ArrayList<>();
        boolean cancelled;
        int chunksDelivered;

        void route(String uri, Canned canned) {
            routes.put(uri, canned);
        }

        void routeAnyHost(String path, Canned canned) {
            anyHostRoutes.put(path, canned);
        }

        /** Resolves the host again at connect time through this resolver, as the real client does. */
        void connectThrough(HttpsGuideFetcher.Resolver resolver) {
            connect = resolver;
        }

        List<String> requested() {
            return requests.stream().map(r -> r.uri().toString()).toList();
        }

        @Override
        public CompletableFuture<HttpResponse<byte[]>> send(
                HttpRequest request, HttpResponse.BodyHandler<byte[]> bodyHandler) {
            requests.add(request);
            HttpsGuideFetcher.Resolver resolver = connect;
            if (resolver != null) {
                try {
                    connectTargets.add(resolver.resolve(request.uri().getHost()).getFirst());
                } catch (UnknownHostException e) {
                    // What HttpClient reports when its own lookup fails: the address stays unresolved.
                    ConnectException failure = new ConnectException("unresolved");
                    failure.initCause(new UnresolvedAddressException());
                    return CompletableFuture.failedFuture(failure);
                }
            }
            Canned canned = routes.getOrDefault(
                    request.uri().toString(),
                    anyHostRoutes.getOrDefault(
                            request.uri().getRawPath(), new Canned(404, Map.of(), List.of(), false)));
            if (canned.hang()) {
                CompletableFuture<HttpResponse<byte[]>> never = new CompletableFuture<>();
                hanging.add(never);
                return never;
            }
            HttpHeaders headers = HttpHeaders.of(canned.headers(), (name, value) -> true);
            HttpResponse.BodySubscriber<byte[]> subscriber = bodyHandler.apply(new HttpResponse.ResponseInfo() {
                @Override
                public int statusCode() {
                    return canned.status();
                }

                @Override
                public HttpHeaders headers() {
                    return headers;
                }

                @Override
                public HttpClient.Version version() {
                    return HttpClient.Version.HTTP_1_1;
                }
            });
            cancelled = false;
            chunksDelivered = 0;
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {}

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
            for (byte[] chunk : canned.chunks()) {
                if (cancelled) {
                    break;
                }
                chunksDelivered++;
                subscriber.onNext(List.of(ByteBuffer.wrap(chunk)));
            }
            if (!cancelled) {
                subscriber.onComplete();
            }
            return subscriber
                    .getBody()
                    .toCompletableFuture()
                    .thenApply(body -> new Response(canned.status(), request, headers, body));
        }
    }

    private static final class Response implements HttpResponse<byte[]> {
        private final int statusCode;
        private final HttpRequest request;
        private final HttpHeaders headers;
        private final byte[] body;

        Response(int statusCode, HttpRequest request, HttpHeaders headers, byte[] body) {
            this.statusCode = statusCode;
            this.request = request;
            this.headers = headers;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public byte[] body() {
            return body;
        }

        @Override
        public Optional<HttpResponse<byte[]>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
