package ai.speckitty.charter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

/**
 * What the servlet container answers before Spring MVC sees the request: a malformed path, a missing
 * {@code Host}, an oversized header. Each is the closed {@code Problem} of the status, as
 * {@code application/problem+json}, and never Tomcat's HTML error report. The requests are written to a raw
 * socket, because an HTTP client would refuse to send them.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "charter.worktree-token=" + ConnectorErrorsTest.TOKEN)
class ConnectorErrorsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String HOST = "Host: 127.0.0.1\r\n";
    private static final String AUTHORIZATION = "Authorization: Bearer " + TOKEN + "\r\n";

    @LocalServerPort
    private int port;

    /** A response as read off the wire. */
    private record Raw(int status, Map<String, List<String>> headers, String body) {

        /** The single value of a header; fails when the header is absent or repeated. */
        String only(String name) {
            return assertThat(headers.get(name)).as(name).hasSize(1).actual().get(0);
        }
    }

    private Raw exchange(String requestLine, String headers) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(10_000);
            var out = socket.getOutputStream();
            out.write((requestLine + "\r\n" + headers + "Connection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
            int split = response.indexOf("\r\n\r\n");
            String head = split < 0 ? response : response.substring(0, split);
            String body = split < 0 ? "" : response.substring(split + 4);
            List<String> lines = head.lines().toList();
            Map<String, List<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (String line : lines.subList(1, lines.size())) {
                int colon = line.indexOf(':');
                headerMap
                        .computeIfAbsent(line.substring(0, colon), name -> new ArrayList<>())
                        .add(line.substring(colon + 1).strip());
            }
            return new Raw(
                    Integer.parseInt(lines.get(0).substring("HTTP/1.1 ".length(), "HTTP/1.1 ".length() + 3)),
                    headerMap,
                    dechunked(headerMap, body));
        }
    }

    private static String dechunked(Map<String, List<String>> headers, String body) {
        if (!headers.getOrDefault("Transfer-Encoding", List.of()).equals(List.of("chunked"))) {
            return body;
        }
        StringBuilder out = new StringBuilder();
        int at = 0;
        while (true) {
            int eol = body.indexOf("\r\n", at);
            int size = Integer.parseInt(body.substring(at, eol).strip(), 16);
            if (size == 0) {
                return out.toString();
            }
            out.append(body, eol + 2, eol + 2 + size);
            at = eol + 2 + size + 2;
        }
    }

    static Stream<String[]> malformedRequests() {
        return Stream.of(
                new String[] {"GET /api/v1/packs/%zz HTTP/1.1", HOST},
                new String[] {"GET /api/v1/pa|cks HTTP/1.1", HOST},
                new String[] {"GET /api/v1/packs/a%2Fb HTTP/1.1", HOST},
                new String[] {"GET /api/v1/packs HTTP/1.1", ""},
                new String[] {"GET /api/v1/packs HTTP/1.1", HOST + "X-Big: " + "a".repeat(20_000) + "\r\n"});
    }

    @ParameterizedTest
    @MethodSource("malformedRequests")
    void aRequestTheConnectorRejectsIsAnswerWithTheClosedProblem(String requestLine, String headers) throws Exception {
        Raw response = exchange(requestLine, headers);

        assertThat(response.status()).isBetween(400, 499);
        assertThat(response.only("Content-Type")).isEqualTo("application/problem+json");
        assertThat(JSON.readTree(response.body()).propertyNames()).containsExactlyInAnyOrder("title", "status");
        assertThat(JSON.readTree(response.body()).get("status").asInt()).isEqualTo(response.status());
        assertThat(response.body().toLowerCase(Locale.ROOT)).doesNotContain("tomcat", "apache", "<html");
    }

    @ParameterizedTest
    @MethodSource("malformedRequests")
    void aRequestTheConnectorRejectsAnswersBeforeTheGuardAndCarriesNoCharterHeader(String requestLine, String headers)
            throws Exception {
        Raw response = exchange(requestLine, headers);

        assertThat(response.headers()).doesNotContainKeys("Charter-Contract-Version", "Charter-Worktree");
        assertThat(response.headers()).doesNotContainKey("Charter-Source-Fingerprint");
    }

    @Test
    void aRebindingHostNameIsRefusedOverARealSocketEvenWithTheRightToken() throws Exception {
        Raw response = exchange("GET /api/v1/info HTTP/1.1", "Host: attacker.example:8080\r\n" + AUTHORIZATION);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.only("Content-Type")).isEqualTo("application/problem+json");
        assertThat(response.headers()).doesNotContainKey("WWW-Authenticate");
        assertThat(response.headers()).doesNotContainKey("Charter-Source-Fingerprint");
        assertThat(JSON.readTree(response.body()).propertyNames()).containsExactlyInAnyOrder("title", "status");
    }

    @Test
    void aLoopbackHostNameIsAcceptedOverARealSocketAtAnyPort() throws Exception {
        for (String host : new String[] {"127.0.0.1:1", "localhost:9", "[::1]:65535", "LOCALHOST"}) {
            Raw response = exchange("GET /api/v1/no-such-path HTTP/1.1", "Host: " + host + "\r\n" + AUTHORIZATION);
            assertThat(response.status()).as(host).isEqualTo(404);
        }
    }

    @Test
    void aRequestWithoutTheTokenIsAnswered401OverARealSocketWithTheCharterHeaders() throws Exception {
        Raw response = exchange("GET /api/v1/no-such-path HTTP/1.1", HOST);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.only("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.only("Charter-Contract-Version")).isNotBlank();
        assertThat(response.only("Charter-Worktree")).isNotBlank();
        assertThat(response.headers()).doesNotContainKey("Charter-Source-Fingerprint");
    }

    @Test
    void traceIsRefusedWithAProblemWhoseAllowHeaderNamesOnlyWhatTheServiceSupports() throws Exception {
        Raw response = exchange("TRACE /api/v1/packs HTTP/1.1", HOST + "X-Echo: must-not-come-back\r\n");

        assertThat(response.status()).isEqualTo(405);
        assertThat(response.only("Content-Type")).isEqualTo("application/problem+json");
        assertThat(response.only("Allow")).isEqualTo("GET,HEAD,OPTIONS,POST");
        assertThat(JSON.readTree(response.body()).propertyNames()).containsExactlyInAnyOrder("title", "status");
        assertThat(response.body()).doesNotContain("must-not-come-back");
    }
}
