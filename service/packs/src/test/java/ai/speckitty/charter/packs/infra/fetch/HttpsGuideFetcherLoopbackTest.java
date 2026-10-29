package ai.speckitty.charter.packs.infra.fetch;

import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.GLOBAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetchRefusal.Reason;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The production client and body handling against a JDK {@link HttpServer} on loopback, reached only through the
 * transport seam: the test rewrites the checked {@code https://guide.example/...} request to the loopback server.
 * The production code has no way to reach loopback (the address policy refuses it, and no property relaxes that);
 * only the request's target is rewritten here, everything else (the direct client, the deadline, the streamed cap,
 * the headers on the wire) is the production path. No socket leaves the loopback interface.
 */
class HttpsGuideFetcherLoopbackTest {

    private static final int MIB = 1024 * 1024;
    private static final String GUIDE = "# Guide\n\nUse the tool.\n";

    private HttpServer server;
    private final Map<String, Map<String, List<String>>> seenHeaders = new ConcurrentHashMap<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/fast", exchange -> respond(exchange, GUIDE.getBytes(StandardCharsets.UTF_8)));
        server.createContext("/exact", exchange -> respond(exchange, filled(MIB)));
        server.createContext("/over", exchange -> respond(exchange, filled(MIB + 1)));
        server.createContext("/drip", this::drip);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpsGuideFetcher fetcher(Duration timeout) {
        URI loopback = URI.create("http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort());
        // The production transport (a client per exchange, its tasks bound to the fetch), its target rewritten.
        HttpsGuideFetcher.Transport toLoopback = HttpsGuideFetcher.clientPerExchange(
                Duration.ofSeconds(2),
                request -> HttpRequest.newBuilder(request, (name, value) -> true)
                        .uri(loopback.resolve(request.uri().getRawPath()))
                        .build());
        return new HttpsGuideFetcher(
                GuideAllowlist.of(List.of("guide.example")),
                timeout,
                MIB,
                3,
                host -> List.of(GLOBAL),
                toLoopback,
                HttpsGuideFetcher.LOOKUP_SLOTS);
    }

    @Test
    void aBodyThatDripsPastTheDeadlineIsRefusedThoughItsHeadersArriveAtOnce() throws GuideFetchRefusal {
        long started = System.nanoTime();

        assertThatThrownBy(() -> fetcher(Duration.ofMillis(700)).fetch(URI.create("https://guide.example/drip")))
                .isInstanceOfSatisfying(
                        GuideFetchRefusal.class,
                        refusal -> assertThat(refusal.reason()).isEqualTo(Reason.TIMEOUT));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
        assertThat(seenHeaders).containsKey("/drip");

        assertThat(fetcher(Duration.ofMillis(700)).fetch(URI.create("https://guide.example/fast")))
                .isEqualTo(GUIDE);
    }

    @Test
    void theStreamedCapAcceptsExactlyOneMebibyteAndRefusesOneByteMore() throws GuideFetchRefusal {
        assertThat(fetcher(Duration.ofSeconds(10)).fetch(URI.create("https://guide.example/exact")))
                .hasSize(MIB);
        assertThatThrownBy(() -> fetcher(Duration.ofSeconds(10)).fetch(URI.create("https://guide.example/over")))
                .isInstanceOfSatisfying(
                        GuideFetchRefusal.class,
                        refusal -> assertThat(refusal.reason()).isEqualTo(Reason.TOO_LARGE));
    }

    @Test
    void theRequestOnTheWireCarriesNoCredentialOrCookieAndAsksForIdentity() throws GuideFetchRefusal {
        fetcher(Duration.ofSeconds(10)).fetch(URI.create("https://guide.example/fast"));

        Map<String, List<String>> headers = seenHeaders.get("/fast");
        assertThat(headers.keySet())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .doesNotContain("authorization", "cookie", "proxy-authorization");
        assertThat(headers.get("Accept-encoding")).containsExactly("identity");
    }

    private void respond(HttpExchange exchange, byte[] body) throws IOException {
        seenHeaders.put(exchange.getRequestURI().getPath(), Map.copyOf(exchange.getRequestHeaders()));
        exchange.getResponseHeaders().add("Content-Type", "text/markdown; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void drip(HttpExchange exchange) throws IOException {
        seenHeaders.put(exchange.getRequestURI().getPath(), Map.copyOf(exchange.getRequestHeaders()));
        exchange.getResponseHeaders().add("Content-Type", "text/markdown");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            for (int i = 0; i < 100; i++) {
                out.write('#');
                out.flush();
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // the client gave up: expected
        }
    }

    private static byte[] filled(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 'a');
        return bytes;
    }
}
