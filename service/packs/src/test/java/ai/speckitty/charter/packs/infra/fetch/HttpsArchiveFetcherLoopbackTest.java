package ai.speckitty.charter.packs.infra.fetch;

import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.GLOBAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.infra.archive.BoundedArchiveExtractor;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The https archive source over the production client and the real extractor, against a JDK {@link HttpServer} on
 * loopback, reached only through the transport seam (the checked {@code https://packs.example.org/...} request is
 * rewritten to the loopback server, as the guide fetcher's loopback test does). The address policy and the allowlist
 * still judge the checked URL, and nothing leaves the loopback interface. A characterisation of the path the unit tests
 * hold piece by piece: a real tar.gz arrives, is spooled owner-only and extracted; the cap, the headers on the wire and
 * a redirect to plain http are the production client's.
 */
class HttpsArchiveFetcherLoopbackTest {

    private static final int MIB = 1024 * 1024;

    @TempDir
    Path directory;

    private HttpServer server;
    private final Map<String, Map<String, List<String>>> seenHeaders = new ConcurrentHashMap<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext(
                "/acme.tar.gz", exchange -> respond(exchange, "application/gzip", tarGz("pack.yaml", "name: acme\n")));
        server.createContext("/over", exchange -> respond(exchange, "application/gzip", gzipPrefixed(MIB + 1)));
        server.createContext("/to-http", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://packs.example.org/acme.tar.gz");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpsArchiveFetcher fetcher() {
        URI loopback = URI.create("http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort());
        GuardedHttpsExchange.Transport toLoopback = GuardedHttpsExchange.clientPerExchange(
                Duration.ofSeconds(2),
                request -> HttpRequest.newBuilder(request, (name, value) -> true)
                        .uri(loopback.resolve(request.uri().getRawPath()))
                        .build());
        GuardedHttpsExchange exchange = new GuardedHttpsExchange(
                GuideAllowlist.of(List.of("packs.example.org")),
                Duration.ofSeconds(10),
                3,
                host -> List.of(GLOBAL),
                toLoopback,
                GuardedHttpsExchange.LOOKUP_SLOTS);
        return new HttpsArchiveFetcher(exchange, new BoundedArchiveExtractor(), MIB, new FileStagingArea(directory));
    }

    private static PackSourceRequest request(String path) {
        return new PackSourceRequest(
                PackSourceKind.HTTPS_ARCHIVE, "https://packs.example.org" + path, null, null, null, null);
    }

    @Test
    void aRealTarGzArrivesIsSpooledOwnerOnlyAndExtracted() throws Exception {
        Staged staged = fetcher().fetch(request("/acme.tar.gz"), directory);

        assertThat(Files.readString(staged.pack().directory().resolve("pack.yaml")))
                .isEqualTo("name: acme\n");
        assertThat(staged.source()).containsEntry("kind", "https_archive");
        assertThat(Files.getPosixFilePermissions(directory.resolve("source-archive")))
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    void theRequestOnTheWireCarriesNoCredentialOrCookieAndAsksForIdentityAndArchives() throws Exception {
        fetcher().fetch(request("/acme.tar.gz"), directory);

        Map<String, List<String>> headers = seenHeaders.get("/acme.tar.gz");
        assertThat(headers.keySet())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .doesNotContain("authorization", "cookie", "proxy-authorization");
        assertThat(headers.get("Accept-encoding")).containsExactly("identity");
        assertThat(headers.get("Accept")).singleElement().asString().contains("application/gzip");
    }

    @Test
    void aBodyOneByteOverTheCapIsRefusedAndNothingIsSpooled(@TempDir Path other) {
        assertThatThrownBy(() -> fetcher().fetch(request("/over"), other))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.getMessage()).startsWith("too_large: "));
        assertThat(other).as("nothing was spooled").isEmptyDirectory();
    }

    @Test
    void aRedirectToPlainHttpIsRefusedAsAUrlRefusalAndNeverFollowed() {
        assertThatThrownBy(() -> fetcher().fetch(request("/to-http"), directory))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.getMessage()).startsWith("url_refused: "));
    }

    private void respond(HttpExchange exchange, String type, byte[] body) throws IOException {
        seenHeaders.put(exchange.getRequestURI().getPath(), Map.copyOf(exchange.getRequestHeaders()));
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] tarGz(String name, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setSize(data.length);
            tar.putArchiveEntry(entry);
            tar.write(data);
            tar.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }

    /** A body of exactly {@code size} bytes that starts with the gzip magic. */
    private static byte[] gzipPrefixed(int size) {
        byte[] body = new byte[size];
        body[0] = 0x1f;
        body[1] = (byte) 0x8b;
        return body;
    }
}
