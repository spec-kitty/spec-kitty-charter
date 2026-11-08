package ai.speckitty.charter.packs.infra.fetch;

import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.GLOBAL;
import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetchRefusal.Reason;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange.BodyPolicy;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange.Fetched;
import ai.speckitty.charter.packs.infra.fetch.HttpsGuideFetcherTest.Canned;
import ai.speckitty.charter.packs.infra.fetch.HttpsGuideFetcherTest.FakeTransport;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The shared exchange under the archive body policy (T083, NFR-004): every guard of the guide fetcher holds when the
 * body is an archive of up to a larger cap, each refusal with an accepted value next to it. Requests go to the same
 * {@link FakeTransport} the guide fetcher's matrix uses; no test here opens a socket.
 */
class GuardedHttpsExchangeTest {

    private static final String HOST = "packs.example.org";
    private static final String URL = "https://packs.example.org/acme.tar.gz";
    private static final int MIB = 1024 * 1024;
    private static final String ACCEPT = "application/gzip, application/zip;q=0.9";
    private static final BodyPolicy ARCHIVES = new BodyPolicy(4L * MIB, ACCEPT);
    private static final byte[] GZIP = {0x1f, (byte) 0x8b, 8, 0, 1, 2, 3};

    private final Map<String, List<InetAddress>> dns = new HashMap<>(Map.of(
            HOST, List.of(GLOBAL), "mirror.example.org", List.of(GLOBAL), "inner.example.org", List.of(PRIVATE)));
    private final FakeTransport transport = new FakeTransport();

    private GuardedHttpsExchange exchange(String... allowed) {
        return exchange(Duration.ofSeconds(10), 3, allowed);
    }

    private GuardedHttpsExchange exchange(Duration timeout, int redirects, String... allowed) {
        GuideAllowlist allowlist = new GuideAllowlist(
                Arrays.stream(allowed).map(GuideAllowlist::normalise).collect(Collectors.toSet()));
        return new GuardedHttpsExchange(
                allowlist, timeout, redirects, this::lookUp, transport, GuardedHttpsExchange.LOOKUP_SLOTS);
    }

    private List<InetAddress> lookUp(String host) throws UnknownHostException {
        List<InetAddress> addresses = dns.get(host);
        if (addresses == null) {
            throw new UnknownHostException(host);
        }
        return addresses;
    }

    private static Canned archive(byte[] body) {
        return new Canned(200, Map.of("Content-Type", List.of("application/gzip")), List.of(body), false);
    }

    private void assertRefused(GuardedHttpsExchange exchange, String uri, Reason reason) {
        assertThatThrownBy(() -> exchange.fetch(URI.create(uri), ARCHIVES))
                .isInstanceOfSatisfying(
                        GuideFetchRefusal.class,
                        refusal -> assertThat(refusal.reason())
                                .as(refusal.getMessage())
                                .isEqualTo(reason));
    }

    @Test
    void anAllowlistedArchiveComesBackAsItsBytesAndHeadersUntouched() throws GuideFetchRefusal {
        transport.route(URL, archive(GZIP));

        Fetched fetched = exchange(HOST).fetch(URI.create(URL), ARCHIVES);

        assertThat(fetched.host()).isEqualTo(HOST);
        assertThat(fetched.toByteArray()).isEqualTo(GZIP);
        assertThat(fetched.headers().firstValue("Content-Type")).contains("application/gzip");
    }

    @Test
    void theRequestCarriesThePolicysAcceptAskedForIdentityAndNoCredential() throws GuideFetchRefusal {
        transport.route(URL, archive(GZIP));

        exchange(HOST).fetch(URI.create(URL), ARCHIVES);

        assertThat(transport.requests).hasSize(1).allSatisfy(request -> {
            assertThat(request.headers().firstValue("Accept")).contains(ACCEPT);
            assertThat(request.headers().firstValue("Accept-Encoding")).contains("identity");
            assertThat(request.headers().map().keySet())
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .doesNotContain("authorization", "cookie", "proxy-authorization");
            assertThat(request.method()).isEqualTo("GET");
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://packs.example.org/acme.tar.gz",
                "ftp://packs.example.org/acme.tar.gz",
                "acme.tar.gz",
                "//packs.example.org/acme.tar.gz"
            })
    void anythingButAbsoluteHttpsIsRefused(String uri) {
        assertRefused(exchange(HOST), uri, Reason.NOT_HTTPS);
        assertThat(transport.requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://user@packs.example.org/acme.tar.gz",
                "https://user:secret@packs.example.org/acme.tar.gz",
                "https://packs.example.org@inner.example.org/acme.tar.gz"
            })
    void userInformationIsRefused(String uri) {
        assertRefused(exchange(HOST, "inner.example.org"), uri, Reason.USERINFO);
        assertThat(transport.requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://127.0.0.1/a.tar.gz",
                "https://[::1]/a.tar.gz",
                "https://169.254.169.254/latest/meta-data",
                "https://2130706433/a.tar.gz",
                "https://8.8.8.8/a.tar.gz"
            })
    void anIpLiteralIsRefusedEvenWhenListed(String uri) {
        assertRefused(exchange(HOST, URI.create(uri).getHost()), uri, Reason.IP_LITERAL);
        assertThat(transport.requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://other.example.org/a.tar.gz",
                "https://sub.packs.example.org/a.tar.gz",
                "https://packs.example.org.evil.example/a.tar.gz"
            })
    void aHostNotExactlyOnTheAllowlistIsRefused(String uri) {
        assertRefused(exchange(HOST), uri, Reason.HOST_NOT_ALLOWED);
        assertRefused(exchange(), URL, Reason.HOST_NOT_ALLOWED);
        assertThat(transport.requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://packs.example.org:22/a.tar.gz",
                "https://packs.example.org:80/a.tar.gz",
                "https://packs.example.org:8443/a.tar.gz"
            })
    void aPortOtherThan443IsRefusedBeforeAnyRequest(String uri) throws GuideFetchRefusal {
        assertRefused(exchange(HOST), uri, Reason.PORT_NOT_ALLOWED);
        assertThat(transport.requests).isEmpty();

        transport.route("https://packs.example.org:443/a.tar.gz", archive(GZIP));
        assertThat(exchange(HOST)
                        .fetch(URI.create("https://packs.example.org:443/a.tar.gz"), ARCHIVES)
                        .toByteArray())
                .isEqualTo(GZIP);
    }

    @ParameterizedTest(name = "{0}: {1} refused, {2} accepted")
    @MethodSource("ai.speckitty.charter.packs.infra.fetch.AddressClasses#refusedWithAcceptedNeighbour")
    void everyNonGlobalClassIsRefusedBeforeAnyRequest(String addressClass, InetAddress refused, InetAddress accepted)
            throws GuideFetchRefusal {
        transport.route(URL, archive(GZIP));
        dns.put(HOST, List.of(refused));
        assertRefused(exchange(HOST), URL, Reason.NON_GLOBAL_ADDRESS);
        assertThat(transport.requests).as(addressClass).isEmpty();

        dns.put(HOST, List.of(accepted));
        assertThat(exchange(HOST).fetch(URI.create(URL), ARCHIVES).toByteArray())
                .isEqualTo(GZIP);
    }

    @Test
    void aMixedAnswerWithOneGlobalAndOnePrivateAddressIsRefused() {
        dns.put(HOST, List.of(GLOBAL, PRIVATE));

        assertRefused(exchange(HOST), URL, Reason.NON_GLOBAL_ADDRESS);
        assertThat(transport.requests).isEmpty();
    }

    @Test
    void aRedirectToPlainHttpIsRefusedAndNeverFollowed() {
        transport.route(URL, Canned.redirect(302, "http://packs.example.org/acme.tar.gz"));

        assertRefused(exchange(HOST), URL, Reason.NOT_HTTPS);
        assertThat(transport.requested()).containsExactly(URL);
    }

    @Test
    void aRedirectToAHostOffTheAllowlistIsRefusedBeforeItIsRequested() {
        transport.route(URL, Canned.redirect(302, "https://mirror.example.org/acme.tar.gz"));
        transport.route("https://mirror.example.org/acme.tar.gz", archive(GZIP));

        assertRefused(exchange(HOST), URL, Reason.HOST_NOT_ALLOWED);
        assertThat(transport.requested()).containsExactly(URL);
    }

    @Test
    void aRedirectToAHostThatResolvesPrivateIsRefusedBeforeItIsRequested() {
        transport.route(URL, Canned.redirect(301, "https://inner.example.org/acme.tar.gz"));

        assertRefused(exchange(HOST, "inner.example.org"), URL, Reason.NON_GLOBAL_ADDRESS);
        assertThat(transport.requested()).containsExactly(URL);
    }

    @Test
    void aRedirectBetweenAllowlistedHostsIsFollowedAndAFourthIsRefused() throws GuideFetchRefusal {
        transport.route(URL, Canned.redirect(302, "https://mirror.example.org/acme.tar.gz"));
        transport.route("https://mirror.example.org/acme.tar.gz", archive(GZIP));
        assertThat(exchange(HOST, "mirror.example.org")
                        .fetch(URI.create(URL), ARCHIVES)
                        .host())
                .isEqualTo("mirror.example.org");

        transport.route("https://packs.example.org/1", Canned.redirect(302, "https://packs.example.org/2"));
        transport.route("https://packs.example.org/2", Canned.redirect(302, "https://packs.example.org/3"));
        transport.route("https://packs.example.org/3", Canned.redirect(302, "https://packs.example.org/4"));
        transport.route("https://packs.example.org/4", Canned.redirect(302, "https://packs.example.org/5"));
        transport.route("https://packs.example.org/5", archive(GZIP));
        assertRefused(exchange(HOST), "https://packs.example.org/1", Reason.TOO_MANY_REDIRECTS);
        assertThat(exchange(HOST)
                        .fetch(URI.create("https://packs.example.org/2"), ARCHIVES)
                        .toByteArray())
                .isEqualTo(GZIP);
    }

    @Test
    void theCapIsThePolicysNotTheGuidesAndOneByteMoreIsRefused() throws GuideFetchRefusal {
        transport.route(URL, Canned.chunked(4 * MIB, MIB).with("Content-Type", "application/gzip"));
        assertThat(exchange(HOST).fetch(URI.create(URL), ARCHIVES).length()).isEqualTo(4 * MIB);

        transport.route(URL, Canned.chunked(4 * MIB + 1, MIB).with("Content-Type", "application/gzip"));
        assertRefused(exchange(HOST), URL, Reason.TOO_LARGE);
    }

    @Test
    void aDeclaredLengthOverTheCapIsRefusedBeforeABodyByteIsRead() {
        transport.route(
                URL,
                Canned.chunked(4 * MIB + 1, MIB)
                        .with("Content-Type", "application/gzip")
                        .with("Content-Length", String.valueOf(4 * MIB + 1)));

        assertRefused(exchange(HOST), URL, Reason.TOO_LARGE);
        assertThat(transport.chunksDelivered).isZero();
    }

    @Test
    void anExchangeThatNeverAnswersOutrunsItsDeadline() {
        transport.route(URL, Canned.hanging());

        assertRefused(exchange(Duration.ofMillis(200), 3, HOST), URL, Reason.TIMEOUT);
    }

    @Test
    void aFinalStatusThatIsNotSuccessIsRefusedAsAStatus() {
        transport.route(URL, new Canned(404, Map.of(), List.of(), false));

        assertRefused(exchange(HOST), URL, Reason.HTTP_STATUS);
    }

    @Test
    void aRefusalNamesTheHostAndNeverEchoesThePathOrQuery() {
        assertThatThrownBy(() -> exchange(HOST)
                        .fetch(URI.create("https://other.example.org/secret-path?token=s3cr3t"), ARCHIVES))
                .hasMessageContaining("other.example.org")
                .hasMessageNotContaining("s3cr3t")
                .hasMessageNotContaining("secret-path");
    }

    @Test
    void aPolicyNeedsAPositiveCapBelowTwoGibibytes() {
        assertThatThrownBy(() -> new BodyPolicy(0, ACCEPT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BodyPolicy(Integer.MAX_VALUE, ACCEPT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
