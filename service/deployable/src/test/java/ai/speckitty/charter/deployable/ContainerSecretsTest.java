package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.infra.api.WorktreeGuard;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The container profile as it ships (ADR-0041 decision 3): {@code application-container.yaml} imports the secrets
 * directory as a required config tree, so the token and the container settings bind from files named after their
 * property or from env vars, and a container without the directory does not start. Each start is the shipped
 * application with an environment of its own; no token comes from the builder, so a bound token is the one the file
 * or the env var gave.
 */
@ExtendWith(OutputCaptureExtension.class)
class ContainerSecretsTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String OTHER_TOKEN = "fedcba9876543210fedcba9876543210fedc";
    private static final String HOSTS = "charter.example.org,localhost";
    private static final String TOKEN_FILE = "charter.worktree-token";
    private static final String HOSTS_FILE = "charter.container.allowed-hosts";

    /** An environment whose env vars are the given map and nothing else, as a container's would be. */
    private static StandardEnvironment environment(Map<String, Object> variables) {
        return new StandardEnvironment() {
            @Override
            public Map<String, Object> getSystemEnvironment() {
                return variables;
            }
        };
    }

    /** The shipped service under the container profile, with the given env vars and no token of the builder's. */
    private static SpringApplicationBuilder container(Map<String, Object> variables) {
        return new SpringApplicationBuilder(CharterServiceApplication.class)
                .web(WebApplicationType.NONE)
                .environment(environment(variables))
                .profiles(ContainerProperties.PROFILE);
    }

    /** The secrets directory and an existing configuration root, as a container's command line would give them. */
    private static String[] arguments(Path secrets, Path root) {
        return new String[] {
            "--charter.container.secrets-dir=" + secrets, CharterServiceApplication.CONFIG_ROOT_OPTION + "=" + root
        };
    }

    private static Path secrets(Path dir, Map<String, String> files) throws IOException {
        Path secrets = Files.createDirectories(dir.resolve("secrets"));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Files.writeString(secrets.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
        }
        return secrets;
    }

    private static Path root(Path dir) throws IOException {
        return Files.createDirectories(dir.resolve("root"));
    }

    /** The status the registered guard answers a loopback request with this bearer; 200 when it lets it through. */
    private static int guardStatus(ConfigurableApplicationContext context, String token) throws Exception {
        FilterRegistrationBean<?> registration = context.getBeansOfType(FilterRegistrationBean.class).values().stream()
                .filter(candidate -> candidate.getFilter() instanceof WorktreeGuard)
                .findFirst()
                .orElseThrow();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/info");
        request.setServerName("localhost");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        registration.getFilter().doFilter(request, response, chain);
        return chain.getRequest() == null ? response.getStatus() : 200;
    }

    private static void assertBoundToTheTokenAndHosts(ConfigurableApplicationContext context) throws Exception {
        assertThat(context.getBean(ContainerProperties.class).allowedHosts())
                .containsExactly("charter.example.org", "localhost");
        assertThat(guardStatus(context, TOKEN)).isEqualTo(200);
        assertThat(guardStatus(context, OTHER_TOKEN)).isEqualTo(401);
    }

    /** Every message and cause of a failure, and its stack, as one text. */
    private static String fullText(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }

    private static void assertNoToken(String text, String token) {
        assertThat(text).as("output and failure text").doesNotContain(token);
    }

    private static Throwable failureOf(SpringApplicationBuilder builder, String... arguments) {
        try (ConfigurableApplicationContext context = builder.run(arguments)) {
            throw new AssertionError("the start was expected to fail, and " + context.getId() + " started");
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    @Test
    void theTokenAndTheAllowedHostsBindFromFilesInTheSecretsDirectory(@TempDir Path dir) throws Exception {
        // The trailing newline an editor or `echo` leaves is dropped.
        Path secrets = secrets(dir, Map.of(TOKEN_FILE, TOKEN + "\n", HOSTS_FILE, HOSTS));
        try (var context = container(Map.of()).run(arguments(secrets, root(dir)))) {
            assertBoundToTheTokenAndHosts(context);
        }
    }

    @Test
    void theSameKeysBindFromEnvVarsWithAnEmptySecretsDirectory(@TempDir Path dir) throws Exception {
        Path secrets = secrets(dir, Map.of());
        Map<String, Object> variables =
                Map.of("CHARTER_WORKTREE_TOKEN", TOKEN, "CHARTER_CONTAINER_ALLOWEDHOSTS", HOSTS);
        try (var context = container(variables).run(arguments(secrets, root(dir)))) {
            assertBoundToTheTokenAndHosts(context);
        }
    }

    @Test
    void theSecretsDirectoryCanBeNamedByItsEnvVar(@TempDir Path dir) throws Exception {
        Path secrets = secrets(dir, Map.of(TOKEN_FILE, TOKEN, HOSTS_FILE, HOSTS));
        Map<String, Object> variables = Map.of("CHARTER_CONTAINER_SECRETS_DIR", secrets.toString());
        try (var context = container(variables).run(CharterServiceApplication.CONFIG_ROOT_OPTION + "=" + root(dir))) {
            assertBoundToTheTokenAndHosts(context);
        }
    }

    @Test
    void aMissingSecretsDirectoryFailsTheStartNamingIt(@TempDir Path dir) throws Exception {
        Path absent = dir.resolve("absent");
        Map<String, Object> variables =
                Map.of("CHARTER_WORKTREE_TOKEN", TOKEN, "CHARTER_CONTAINER_ALLOWEDHOSTS", HOSTS);
        Throwable failure = failureOf(container(variables), arguments(absent, root(dir)));
        assertThat(failure).isInstanceOf(ConfigDataResourceNotFoundException.class);
        assertThat(failure.getMessage()).contains(absent.toString());
        assertNoToken(fullText(failure), TOKEN);
    }

    @Test
    void aFileNamedInTheEnvVarStyleSetsNoPropertyAndIsRefused(@TempDir Path dir, CapturedOutput output)
            throws Exception {
        // The file maps to no property (ADR-0041 decision 3); the secrets directory may set only its own keys, so the
        // start is refused naming the file, not its content.
        Path secrets = secrets(dir, Map.of("CHARTER_WORKTREE_TOKEN", TOKEN, HOSTS_FILE, HOSTS));
        Throwable failure = failureOf(container(Map.of()), arguments(secrets, root(dir)));
        assertThat(fullText(failure))
                .contains("may set only charter.worktree-token")
                .contains("remove CHARTER_WORKTREE_TOKEN");
        assertNoToken(fullText(failure) + output.getAll(), TOKEN);
    }

    @Test
    void anEmptyTokenEnvVarHidesTheMountedTokenFile(@TempDir Path dir, CapturedOutput output) throws Exception {
        Path secrets = secrets(dir, Map.of(TOKEN_FILE, TOKEN, HOSTS_FILE, HOSTS));
        Throwable failure = failureOf(container(Map.of("CHARTER_WORKTREE_TOKEN", "")), arguments(secrets, root(dir)));
        assertThat(fullText(failure)).contains("charter.worktree-token is blank");
        assertNoToken(fullText(failure) + output.getAll(), TOKEN);
    }

    @Test
    void aShortTokenFileIsRefusedWithoutItsValueInTheOutput(@TempDir Path dir, CapturedOutput output) throws Exception {
        String shortToken = "short-planted-secret-0123";
        Path secrets = secrets(dir, Map.of(TOKEN_FILE, shortToken, HOSTS_FILE, HOSTS));
        Throwable failure = failureOf(container(Map.of()), arguments(secrets, root(dir)));
        assertThat(fullText(failure)).contains("charter.worktree-token is too short");
        assertNoToken(fullText(failure) + output.getAll(), shortToken);
    }

    @Test
    void theNoTokenCheckFailsWhenTheTextHoldsTheToken() {
        // The positive control of the checks above: they would see a token that leaked.
        assertThatThrownBy(() -> assertNoToken("refused: " + TOKEN, TOKEN)).isInstanceOf(AssertionError.class);
    }

    @Test
    void theProfileListensOnAllInterfacesOnPort8080(@TempDir Path dir) throws Exception {
        Path secrets = secrets(dir, Map.of(TOKEN_FILE, TOKEN, HOSTS_FILE, HOSTS));
        try (var context = container(Map.of()).run(arguments(secrets, root(dir)))) {
            assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("8080");
            assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("0.0.0.0");
        }
    }
}
