package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * NFR-010 over the wiring: a registration or removal attempt is entered in the audit log exactly once, whichever layer
 * ends it. The service as it ships (guard, audit filter, admission, jobs), with a recording {@link AuditLog} in place of
 * the log line, takes an unauthenticated {@code PUT} (the guard's 401), an oversized one (the admission's 413), one that
 * is accepted (202, entered by its job when it ends) and a {@code DELETE} the service refuses (404): four attempts,
 * four entries.
 */
@SpringBootTest(
        classes = {CharterServiceApplication.class, RegistrationAuditWiringTest.Recording.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationAuditWiringTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final int OVER_THE_LIMIT = 32 * 1024 * 1024 + 1;
    private static final List<AuditLog.Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static Path configRoot = Path.of("");

    @LocalServerPort
    private int port;

    /** Records what the service enters, in place of the log line. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Recording {

        @Bean
        @Primary
        AuditLog recordingAuditLog() {
            return ENTRIES::add;
        }
    }

    @DynamicPropertySource
    static void registrationOn(DynamicPropertyRegistry registry) throws IOException {
        configRoot = Files.createTempDirectory("charter-audit-wiring");
        Files.createDirectories(configRoot.resolve(ConfigRoot.PACKS));
        registry.add("charter.worktree-token", () -> TOKEN);
        registry.add("charter.config-root", () -> configRoot.toString());
        registry.add("charter.registration.enabled", () -> "true");
    }

    @AfterAll
    static void deleteRoot() throws IOException {
        try (Stream<Path> walk = Files.walk(configRoot)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpRequest.Builder to(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
    }

    /**
     * An upload that declares more than 32 MiB and sends none of it: the admission refuses on the declared length, before
     * it reads a byte, so the exchange is headers and a status line, with no half-sent body to meet a closing connection.
     */
    private String oversizedPutStatusLine() throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(30_000);
            String request = "PUT /api/v1/packs/acme HTTP/1.1\r\nHost: 127.0.0.1\r\nAuthorization: Bearer " + TOKEN
                    + "\r\nContent-Type: application/zip\r\nContent-Length: " + OVER_THE_LIMIT
                    + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String line = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                    .readLine();
            return line == null ? "" : line.substring(0, Math.min(line.length(), "HTTP/1.1 413".length()));
        }
    }

    private static byte[] pack() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("pack.yaml"));
            zip.write("""
                    pack_id: 01M4P01D5TWB5FKZGNNQG3943R
                    pack_version: 1.0.0
                    parent_pack: null
                    name: acme
                    pack_level: org
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void anUnauthenticatedPutAnOversizedPutAnAcceptedPutAndARefusedDeleteAreEnteredOnceEach() throws Exception {
        ENTRIES.clear();

        HttpResponse<String> unauthenticated = send(to("/packs/acme")
                .header("Content-Type", "application/zip")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(pack())));
        String oversized = oversizedPutStatusLine();
        HttpResponse<String> accepted = send(to("/packs/acme")
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/zip")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(pack())));
        HttpResponse<String> refusedDelete = send(to("/packs/ghost/artifacts/directive/D1")
                .header("Authorization", "Bearer " + TOKEN)
                .header("If-Match", "*")
                .DELETE());

        assertThat(unauthenticated.statusCode()).as(unauthenticated.body()).isEqualTo(401);
        assertThat(oversized).isEqualTo("HTTP/1.1 413");
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(202);
        assertThat(refusedDelete.statusCode()).as(refusedDelete.body()).isEqualTo(404);
        // The accepted registration is entered by its job when it ends.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (ENTRIES.size() < 4 && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        Thread.sleep(300); // a second entry for the same attempt would arrive in this time
        assertThat(ENTRIES)
                .as("one entry for each of the four attempts")
                .extracting(AuditLog.Entry::operation, AuditLog.Entry::outcome)
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple("registerPackFromArchive", "refused:401"),
                        org.assertj.core.api.Assertions.tuple("registerPackFromArchive", "refused:413"),
                        org.assertj.core.api.Assertions.tuple("registerPackFromArchive", "succeeded:created"),
                        org.assertj.core.api.Assertions.tuple("deletePackArtifact", "refused:404"));
    }
}
