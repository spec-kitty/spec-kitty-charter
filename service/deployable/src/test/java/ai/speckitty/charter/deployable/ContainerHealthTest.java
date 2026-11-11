package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;

/**
 * The health check and the management surface end to end, on a real server under each profile (ADR-0041 decision 7,
 * issue #232). Under the container profile exactly {@code GET /actuator/health} answers up or down without the token,
 * still behind the host and browser checks; locally it needs the token like every path. No other endpoint is exposed,
 * whatever token the request carries, and the token never reaches the output, even with debug logging on.
 */
@ExtendWith(OutputCaptureExtension.class)
class ContainerHealthTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String BEARER = "Bearer " + TOKEN;
    private static final String UP = "{\"status\":\"UP\"}";

    /** A status line, the header block and a body, as the server sent them. */
    private record Answer(int status, String head, String body) {}

    /** One raw HTTP/1.1 request, so the {@code Host} header and the request target are exactly what the test says. */
    private static Answer send(int port, String method, String target, Map<String, String> headers) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(10_000);
            StringBuilder request = new StringBuilder(method + " " + target + " HTTP/1.1\r\n");
            headers.forEach((name, value) ->
                    request.append(name).append(": ").append(value).append("\r\n"));
            request.append("Connection: close\r\n\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream in = socket.getInputStream();
            String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int status = Integer.parseInt(response.substring(9, 12));
            int split = response.indexOf("\r\n\r\n");
            String head = split < 0 ? response : response.substring(0, split);
            String body = split < 0 ? "" : response.substring(split + 4);
            return new Answer(status, head, dechunked(response, body));
        }
    }

    /** The body without chunk framing, when the server chunked it. */
    private static String dechunked(String response, String body) {
        if (!response.toLowerCase(java.util.Locale.ROOT).contains("transfer-encoding: chunked")) {
            return body;
        }
        StringBuilder out = new StringBuilder();
        String rest = body;
        while (true) {
            int end = rest.indexOf("\r\n");
            int size = Integer.parseInt(rest.substring(0, end).strip(), 16);
            if (size == 0) {
                return out.toString();
            }
            out.append(rest, end + 2, end + 2 + size);
            rest = rest.substring(end + 2 + size + 2);
        }
    }

    private static Map<String, String> headers(String... pairs) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            headers.put(pairs[i], pairs[i + 1]);
        }
        return headers;
    }

    private static Answer get(int port, String target, String... pairs) throws IOException {
        return send(port, "GET", target, headers(pairs));
    }

    /** An environment with no env vars, so the machine running the test cannot change the start. */
    private static StandardEnvironment clean() {
        return new StandardEnvironment() {
            @Override
            public Map<String, Object> getSystemEnvironment() {
                return Map.of();
            }
        };
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    /**
     * The shipped service on a real server under the container profile, the token in its secrets directory. The profile
     * refuses port 0, so a free port is picked first; another process can take it before the server binds, so a start
     * that loses that race is tried again on another port.
     */
    private static ConfigurableApplicationContext container(Path dir, String... more) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                return container(dir, freePort(), more);
            } catch (RuntimeException failure) {
                if (attempt == 3 || !lostThePort(failure)) {
                    throw failure;
                }
            }
        }
    }

    private static boolean lostThePort(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.BindException
                    || cause instanceof org.springframework.boot.web.server.PortInUseException) {
                return true;
            }
        }
        return false;
    }

    private static ConfigurableApplicationContext container(Path dir, int port, String... more) throws IOException {
        Path secrets = Files.createDirectories(dir.resolve("secrets"));
        Files.writeString(secrets.resolve("charter.worktree-token"), TOKEN, StandardCharsets.UTF_8);
        Path root = Files.createDirectories(dir.resolve("root"));
        String[] base = {
            "--server.port=" + port,
            "--server.address=127.0.0.1",
            "--charter.container.secrets-dir=" + secrets,
            "--charter.container.allowed-hosts=localhost,charter.internal",
            CharterServiceApplication.CONFIG_ROOT_OPTION + "=" + root
        };
        String[] all = Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, all, base.length, more.length);
        return new SpringApplicationBuilder(CharterServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .environment(clean())
                .profiles(ContainerProperties.PROFILE)
                .run(all);
    }

    /** The shipped service on a real loopback server without the profile, the token as the launcher would give it. */
    private static ConfigurableApplicationContext local(Path dir) throws IOException {
        Path root = Files.createDirectories(dir.resolve("root"));
        return new SpringApplicationBuilder(CharterServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .environment(clean())
                .properties(Map.of("charter.worktree-token", TOKEN))
                .run(CharterServiceApplication.CONFIG_ROOT_OPTION + "=" + root);
    }

    private static int port(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port", "-1"));
    }

    @Test
    void underTheContainerProfileTheHealthCheckAnswersUpWithoutATokenAndNothingMore(@TempDir Path dir)
            throws IOException {
        try (var context = container(dir)) {
            Answer health = get(port(context), "/actuator/health", "Host", "charter.internal:8080");
            assertThat(health.status()).isEqualTo(200);
            assertThat(health.body()).isEqualTo(UP);
            assertThat(health.head().toLowerCase(java.util.Locale.ROOT))
                    .containsPattern("content-type: application/(vnd\\.spring-boot\\.actuator\\.v3\\+)?json");
            Answer withQuery = get(port(context), "/actuator/health?x=1", "Host", "charter.internal:8080");
            assertThat(withQuery.status())
                    .as("a query string is not part of the path")
                    .isEqualTo(200);
            assertThat(withQuery.body()).isEqualTo(UP);
        }
    }

    @Test
    void underTheContainerProfileTheHealthCheckStillNeedsAnAllowedHostAndNoBrowserHeader(@TempDir Path dir)
            throws IOException {
        try (var context = container(dir)) {
            int port = port(context);
            assertThat(get(port, "/actuator/health", "Host", "attacker.example").status())
                    .isEqualTo(403);
            assertThat(get(port, "/actuator/health", "Host", "localhost", "Origin", "https://evil.example")
                            .status())
                    .isEqualTo(403);
            assertThat(get(port, "http://attacker.example/actuator/health", "Host", "localhost")
                            .status())
                    .as("an absolute-form target that disagrees with Host never reaches the endpoint")
                    .isEqualTo(400);
            assertThat(get(port, "http://localhost/actuator/health", "Host", "localhost")
                            .status())
                    .as("an absolute-form target that agrees is the same request")
                    .isEqualTo(200);
        }
    }

    @Test
    void underTheContainerProfileANearMissOfTheHealthCheckNeedsTheToken(@TempDir Path dir) throws IOException {
        try (var context = container(dir)) {
            int port = port(context);
            assertThat(send(port, "HEAD", "/actuator/health", headers("Host", "localhost"))
                            .status())
                    .isEqualTo(401);
            for (String target : new String[] {
                "/actuator/health/",
                "/actuator/health/liveness",
                "/actuator/health/readiness",
                "/actuator/health/ping",
                "/actuator/health;x=y",
                "/actuator/%68ealth",
                "/actuator",
                "/actuator/env",
                "//actuator/health",
                "/actuator//health",
                "/ACTUATOR/HEALTH",
                "http://localhost/actuator/env"
            }) {
                assertThat(get(port, target, "Host", "localhost").status())
                        .as(target)
                        .isEqualTo(401);
            }
        }
    }

    @Test
    void underTheContainerProfileNoOtherEndpointAnswersEvenWithTheToken(@TempDir Path dir) throws IOException {
        try (var context = container(dir)) {
            int port = port(context);
            for (String endpoint : new String[] {
                "env", "configprops", "beans", "loggers", "heapdump", "threaddump", "mappings", "info", "sbom"
            }) {
                Answer answer = get(port, "/actuator/" + endpoint, "Host", "localhost", "Authorization", BEARER);
                assertThat(answer.status()).as(endpoint).isEqualTo(404);
            }
        }
    }

    @Test
    void underTheContainerProfileThereIsNoDiscoveryPageAndNoHealthComponent(@TempDir Path dir) throws IOException {
        try (var context = container(dir)) {
            int port = port(context);
            assertThat(get(port, "/actuator", "Host", "localhost", "Authorization", BEARER)
                            .status())
                    .isEqualTo(404);
            for (String component : new String[] {"diskSpace", "ping", "liveness"}) {
                assertThat(get(port, "/actuator/health/" + component, "Host", "localhost", "Authorization", BEARER)
                                .status())
                        .as(component)
                        .isEqualTo(404);
            }
            Answer withToken = get(port, "/actuator/health", "Host", "localhost", "Authorization", BEARER);
            assertThat(withToken.body()).as("the token reveals nothing more").isEqualTo(UP);
        }
    }

    @Test
    void underTheContainerProfileAForwardedHostChangesNothingOnAKubernetesPlatform(@TempDir Path dir)
            throws IOException {
        try (var context = container(dir, "--spring.main.cloud-platform=kubernetes")) {
            int port = port(context);
            Answer forwarded =
                    get(port, "/actuator/health", "Host", "localhost", "X-Forwarded-Host", "attacker.example");
            assertThat(forwarded.status()).isEqualTo(200);
            assertThat(forwarded.body())
                    .as("no probe groups, though Kubernetes is detected")
                    .isEqualTo(UP);
            assertThat(get(port, "/actuator/health", "Host", "attacker.example", "X-Forwarded-Host", "localhost")
                            .status())
                    .isEqualTo(403);
        }
    }

    @Test
    void underTheContainerProfileJmxIsOff(@TempDir Path dir) throws IOException {
        try (var context = container(dir)) {
            assertThat(context.getBeanFactory().getBeanNamesIterator())
                    .toIterable()
                    .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("jmx"));
        }
    }

    @Test
    void underTheContainerProfileAWidenedSettingFailsTheStart(@TempDir Path dir) {
        assertThatThrownBy(() -> container(dir, "--management.endpoints.web.exposure.include=*"))
                .hasStackTraceContaining("management.endpoints.web.exposure.include must be health");
        assertThatThrownBy(() -> container(dir, "--logging.level.root=TRACE"))
                .hasStackTraceContaining(
                        "logging.level must not put org.apache.coyote.http11.Http11InputBuffer at TRACE");
    }

    @Test
    void withoutTheProfileTheHealthCheckNeedsTheTokenLikeEveryPath(@TempDir Path dir) throws IOException {
        try (var context = local(dir)) {
            int port = port(context);
            assertThat(get(port, "/actuator/health", "Host", "localhost").status())
                    .isEqualTo(401);
            Answer health = get(port, "/actuator/health", "Host", "localhost", "Authorization", BEARER);
            assertThat(health.status()).isEqualTo(200);
            assertThat(health.body()).isEqualTo(UP);
            assertThat(get(port, "/actuator/env", "Host", "localhost", "Authorization", BEARER)
                            .status())
                    .isEqualTo(404);
        }
    }

    @Test
    void theTokenNeverReachesTheOutputEvenWithDebugLoggingOnTheTomcatRequestLogger(
            @TempDir Path dir, CapturedOutput output) throws IOException {
        StringBuilder answers = new StringBuilder();
        try (var context =
                container(dir, "--debug", "--logging.level.root=DEBUG", "--logging.level.org.apache.coyote=DEBUG")) {
            int port = port(context);
            for (Answer answer : new Answer[] {
                get(port, "/actuator/health", "Host", "localhost"),
                get(port, "/api/v1/info", "Host", "localhost", "Authorization", BEARER),
                get(port, "/api/v1/info", "Host", "localhost", "Authorization", "Bearer " + TOKEN + "x"),
                get(port, "/api/v1/info", "Host", "attacker.example", "Authorization", BEARER)
            }) {
                answers.append(answer.head()).append(answer.body());
            }
        }
        assertThat(output.getAll()).as("debug logging is really on").contains("DEBUG");
        assertThat(output.getAll()).as("captured output").doesNotContain(TOKEN);
        assertThat(answers).as("the answers").doesNotContain(TOKEN);
    }
}
