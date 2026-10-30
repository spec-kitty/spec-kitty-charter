package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.support.SpringApplicationJsonEnvironmentPostProcessor;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** How {@link GuardWiring} turns the token configuration into the registered guard, and what it refuses to start on. */
@ExtendWith(OutputCaptureExtension.class)
class GuardWiringTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String CONTAINER = "spring.profiles.active=container";

    @Configuration(proxyBeanMethods = false)
    static class Surroundings {
        @Bean
        ServiceInfoQuery serviceInfoQuery() {
            return new ServiceInfoQuery("wt-1", new ConsistentRead(() -> "sha256:" + "0".repeat(64)));
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(Surroundings.class, GuardWiring.class);

    private static FilterRegistrationBean<?> registration(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed().hasSingleBean(FilterRegistrationBean.class);
        return context.getBean(FilterRegistrationBean.class);
    }

    private static int statusOf(FilterRegistrationBean<?> registration, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        registration.getFilter().doFilter(request, response, chain);
        return chain.getRequest() == null ? response.getStatus() : 200;
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/info");
        request.setServerName("localhost");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private static void assertRefusedWithoutTheToken(AssertableApplicationContext context, CapturedOutput output) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("charter.worktree-token");
        assertThat(rootMessages(context.getStartupFailure())).doesNotContain(TOKEN);
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    private static String failureText(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        return rootMessages(context.getStartupFailure());
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }

    @Test
    void aConfiguredTokenRegistersTheGuardAtHighestPrecedenceForRequestDispatchesOnly() {
        runner.withPropertyValues("charter.worktree-token=" + TOKEN).run(context -> {
            FilterRegistrationBean<?> registration = registration(context);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
            assertThat(registration.determineDispatcherTypes()).isEqualTo(EnumSet.of(DispatcherType.REQUEST));
            assertThat(registration.getUrlPatterns()).containsExactly("/*");
            assertThat(registration.isEnabled()).isTrue();
            assertThat(registration.getFilter()).isInstanceOf(ai.speckitty.charter.packs.infra.api.WorktreeGuard.class);
        });
    }

    @Test
    void theRegisteredGuardEnforcesTheConfiguredToken() {
        runner.withPropertyValues("charter.worktree-token=" + TOKEN).run(context -> {
            FilterRegistrationBean<?> registration = registration(context);
            assertThat(statusOf(registration, request("Bearer " + TOKEN))).isEqualTo(200);
            assertThat(statusOf(registration, request(null))).isEqualTo(401);
            assertThat(statusOf(registration, request("Bearer " + TOKEN + "x"))).isEqualTo(401);
        });
    }

    @Test
    void aTokenOfExactlyThirtyTwoCharactersIsAccepted() {
        runner.withPropertyValues("charter.worktree-token=" + "a".repeat(32))
                .run(context -> assertThat(registration(context)).isNotNull());
    }

    @Test
    void aBlankTokenRefusesTheStart(CapturedOutput output) {
        runner.withPropertyValues("charter.worktree-token=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("charter.worktree-token");
        });
        runner.withPropertyValues("charter.worktree-token=" + " ".repeat(40))
                .run(context -> assertThat(context).hasFailed());
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void aBlankTokenRefusesTheStartEvenWithTheDevelopmentFlag() {
        runner.withPropertyValues("charter.worktree-token=", "charter.insecure-no-token=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aTokenShorterThanThirtyTwoCharactersRefusesTheStart(CapturedOutput output) {
        String shortToken = "a".repeat(31);
        runner.withPropertyValues("charter.worktree-token=" + shortToken).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("32");
            assertThat(rootMessages(context.getStartupFailure())).doesNotContain(shortToken);
        });
        assertThat(output.getAll()).doesNotContain(shortToken);
    }

    @Test
    void aTokenFromASystemPropertyRefusesTheStart(CapturedOutput output) {
        runner.withSystemProperties("charter.worktree-token=" + TOKEN)
                .run(context -> assertRefusedWithoutTheToken(context, output));
    }

    @Test
    void aTokenFromASystemPropertyInAnotherSpellingRefusesTheStart(CapturedOutput output) {
        runner.withSystemProperties("charter.worktreeToken=" + TOKEN)
                .run(context -> assertRefusedWithoutTheToken(context, output));
        runner.withSystemProperties("charter.WORKTREE_TOKEN=" + TOKEN)
                .run(context -> assertRefusedWithoutTheToken(context, output));
    }

    @Test
    void aTokenFromTheCommandLineRefusesTheStart(CapturedOutput output) {
        runner.withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SimpleCommandLinePropertySource("--charter.worktree-token=" + TOKEN)))
                .run(context -> assertRefusedWithoutTheToken(context, output));
    }

    @Test
    void aSystemPropertyTokenIsRefusedEvenWhenTheEnvironmentHoldsAValidOne(CapturedOutput output) {
        runner.withSystemProperties("charter.worktree-token=" + "b".repeat(40))
                .withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addLast(new SystemEnvironmentPropertySource(
                                "test-environment", Map.of("CHARTER_WORKTREE_TOKEN", TOKEN))))
                .run(context -> assertRefusedWithoutTheToken(context, output));
    }

    private static final String JSON_TOKEN = "{\"charter\":{\"worktree-token\":\"" + TOKEN + "\"}}";

    /** {@code ApplicationContextRunner} runs no environment post-processors, so the JSON unpacking is run here. */
    private static void unpackApplicationJson(org.springframework.context.ConfigurableApplicationContext context) {
        new SpringApplicationJsonEnvironmentPostProcessor()
                .postProcessEnvironment(context.getEnvironment(), new SpringApplication());
    }

    @Test
    void aTokenFromSpringApplicationJsonOnTheCommandLineRefusesTheStart(CapturedOutput output) {
        runner.withInitializer(context -> {
                    context.getEnvironment()
                            .getPropertySources()
                            .addFirst(new SimpleCommandLinePropertySource("--spring.application.json=" + JSON_TOKEN));
                    unpackApplicationJson(context);
                })
                .run(context -> assertRefusedWithoutTheToken(context, output));
    }

    @Test
    void aTokenFromSpringApplicationJsonInASystemPropertyRefusesTheStart(CapturedOutput output) {
        runner.withSystemProperties("spring.application.json=" + JSON_TOKEN)
                .withInitializer(GuardWiringTest::unpackApplicationJson)
                .run(context -> assertRefusedWithoutTheToken(context, output));
        runner.withSystemProperties("spring.application.json={\"charter.WORKTREE_TOKEN\":\"" + TOKEN + "\"}")
                .withInitializer(GuardWiringTest::unpackApplicationJson)
                .run(context -> assertRefusedWithoutTheToken(context, output));
    }

    private static String tokenJson(String charterMembers) {
        return "{\"charter\":{" + charterMembers + "}}";
    }

    /** Each of these is JSON Boot unpacks to {@code charter.worktree-token}; none of them has the token first. */
    static java.util.stream.Stream<String> jsonThatBindsTheToken() {
        String quoted = "\"" + TOKEN + "\"";
        return java.util.stream.Stream.of(
                tokenJson("\"worktree-id\":\"wt\",\"worktree-token\":" + quoted),
                tokenJson("\"worktree-token\":" + quoted + ",\"worktree-id\":\"wt\""),
                tokenJson("\"worktree-id\":\"wt\",\"worktree\\u002dtoken\":" + quoted),
                tokenJson("\"worktree-id\":\"wt\",\"worktreeToken\":" + quoted),
                "{\"other\":1,\"charter.worktree-token\":" + quoted + "}",
                "{\"other\":{\"x\":[1,2]},\"charter\":{\"WORKTREE_TOKEN\":" + quoted + "}}");
    }

    @ParameterizedTest
    @MethodSource("jsonThatBindsTheToken")
    void aTokenInAnyJsonOnTheCommandLineRefusesTheStart(String json, CapturedOutput output) {
        runner.withInitializer(context -> {
                    context.getEnvironment()
                            .getPropertySources()
                            .addFirst(new SimpleCommandLinePropertySource("--spring.application.json=" + json));
                    unpackApplicationJson(context);
                })
                .run(context -> assertRefusedWithoutTheToken(context, output));
    }

    @Test
    void aTokenNestedAsWorktreeTokenInJsonOnTheCommandLineIsRefusedToo(CapturedOutput output) {
        // Boot does not bind charter.worktree.token to charter.worktree-token, but the refusal does not depend on that.
        String json = tokenJson("\"worktree-id\":\"wt\",\"worktree\":{\"token\":\"" + TOKEN + "\"}");
        runner.withInitializer(context -> {
                    context.getEnvironment()
                            .getPropertySources()
                            .addFirst(new SimpleCommandLinePropertySource("--spring.application.json=" + json));
                    unpackApplicationJson(context);
                })
                .run(context -> assertRefusedWithoutTheToken(context, output));
    }

    @ParameterizedTest
    @MethodSource("jsonThatBindsTheToken")
    void aTokenInAnyJsonInASystemPropertyRefusesTheStart(String json, CapturedOutput output) {
        runner.withSystemProperties("spring.application.json=" + json)
                .withInitializer(GuardWiringTest::unpackApplicationJson)
                .run(context -> assertRefusedWithoutTheToken(context, output));
    }

    @ParameterizedTest
    @MethodSource("jsonThatBindsTheToken")
    void aTokenInAnyJsonInTheEnvironmentIsAccepted(String json) {
        runner.withInitializer(context -> {
                    context.getEnvironment()
                            .getPropertySources()
                            .addLast(new SystemEnvironmentPropertySource(
                                    "test-environment", Map.of("SPRING_APPLICATION_JSON", json)));
                    unpackApplicationJson(context);
                })
                .run(context -> assertThat(statusOf(registration(context), request("Bearer " + TOKEN)))
                        .isEqualTo(200));
    }

    @Test
    void jsonOnTheCommandLineThatDoesNotNameTheTokenDoesNotStopAnEnvironmentToken() {
        runner.withInitializer(context -> {
                    context.getEnvironment()
                            .getPropertySources()
                            .addFirst(new SimpleCommandLinePropertySource(
                                    "--spring.application.json={\"charter\":{\"worktree-id\":\"wt\"}}"));
                    context.getEnvironment()
                            .getPropertySources()
                            .addLast(new SystemEnvironmentPropertySource(
                                    "test-environment", Map.of("CHARTER_WORKTREE_TOKEN", TOKEN)));
                    unpackApplicationJson(context);
                })
                .run(context -> assertThat(statusOf(registration(context), request("Bearer " + TOKEN)))
                        .isEqualTo(200));
    }

    @Test
    void aTokenFromSpringApplicationJsonInTheEnvironmentIsAccepted() {
        runner.withInitializer(context -> {
                    context.getEnvironment()
                            .getPropertySources()
                            .addLast(new SystemEnvironmentPropertySource(
                                    "test-environment", Map.of("SPRING_APPLICATION_JSON", JSON_TOKEN)));
                    unpackApplicationJson(context);
                })
                .run(context -> assertThat(statusOf(registration(context), request("Bearer " + TOKEN)))
                        .isEqualTo(200));
    }

    @Test
    void aTokenFromTheEnvironmentIsAccepted() {
        runner.withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addLast(new SystemEnvironmentPropertySource(
                                "test-environment", Map.of("CHARTER_WORKTREE_TOKEN", TOKEN))))
                .run(context -> assertThat(statusOf(registration(context), request(null)))
                        .isEqualTo(401));
    }

    @Test
    void aTokenFromAnotherPropertySourceIsAccepted() {
        runner.withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new MapPropertySource("a-config-file", Map.of("charter.worktree-token", TOKEN))))
                .run(context -> assertThat(statusOf(registration(context), request("Bearer " + TOKEN)))
                        .isEqualTo(200));
    }

    @Test
    void noTokenWithoutTheDevelopmentFlagRefusesTheStart() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("charter.worktree-token");
        });
    }

    @Test
    void theFlagSetToAnythingButTrueDoesNotAllowStartingWithoutAToken() {
        runner.withPropertyValues("charter.insecure-no-token=false")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("charter.insecure-no-token=yes-please")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theDevelopmentFlagStartsWithoutATokenAndWarnsAtEveryStart(CapturedOutput output) {
        for (int start = 1; start <= 2; start++) {
            runner.withPropertyValues("charter.insecure-no-token=true")
                    .run(context -> assertThat(statusOf(registration(context), request(null)))
                            .isEqualTo(200));
            assertThat(output.getAll()).as("start %d", start).containsSubsequence("WARN", "charter.insecure-no-token");
            assertThat(countOf(output.getAll(), "charter.insecure-no-token")).isEqualTo(start);
        }
    }

    @Test
    void theBrowserChecksAreOnEvenWithTheDevelopmentFlag() {
        runner.withPropertyValues("charter.insecure-no-token=true").run(context -> {
            FilterRegistrationBean<?> registration = registration(context);
            MockHttpServletRequest origin = request(null);
            origin.addHeader("Origin", "https://evil.example");
            assertThat(statusOf(registration, origin)).isEqualTo(403);
            MockHttpServletRequest rebound = request(null);
            rebound.addHeader("Host", "attacker.example");
            rebound.setServerName("attacker.example");
            assertThat(statusOf(registration, rebound)).isEqualTo(403);
        });
    }

    @Test
    void aTokenBeatsTheDevelopmentFlag() {
        runner.withPropertyValues("charter.worktree-token=" + TOKEN, "charter.insecure-no-token=true")
                .run(context -> {
                    FilterRegistrationBean<?> registration = registration(context);
                    assertThat(statusOf(registration, request(null))).isEqualTo(401);
                    assertThat(statusOf(registration, request("Bearer " + TOKEN)))
                            .isEqualTo(200);
                });
    }

    @Test
    void theTokenIsNeverLoggedWhateverTheStart(CapturedOutput output) {
        runner.withPropertyValues("charter.worktree-token=" + TOKEN).run(context -> registration(context));
        runner.withPropertyValues("charter.worktree-token=" + TOKEN, "charter.insecure-no-token=true")
                .run(context -> registration(context));
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    // Under the container profile the refusals say where a container's token goes (ADR-0041 decision 3); without
    // it they are the local daemon's, as before.

    @Test
    void withoutTheProfileTheRefusalsAreTheLocalDaemonsWordForWord() {
        runner.run(context -> assertThat(context.getStartupFailure())
                .rootCause()
                .hasMessage("charter.worktree-token is required: set CHARTER_WORKTREE_TOKEN in the environment (for"
                        + " development only, charter.insecure-no-token=true starts the service without one)"));
        runner.withPropertyValues("charter.worktree-token=")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessage("charter.worktree-token is blank; it must be at least 32 characters"));
        runner.withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SimpleCommandLinePropertySource("--charter.worktree-token=" + TOKEN)))
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessage("charter.worktree-token must come from the environment (CHARTER_WORKTREE_TOKEN),"
                                + " not from the command line or a system property, where other processes can read"
                                + " it"));
    }

    @Test
    void underTheContainerProfileTheMissingTokenRefusalNamesTheEnvVarAndTheMountedFile() {
        runner.withPropertyValues(CONTAINER)
                .run(context -> assertThat(failureText(context))
                        .contains("CHARTER_WORKTREE_TOKEN")
                        .contains("/run/secrets/charter.worktree-token")
                        .doesNotContain("charter.insecure-no-token"));
    }

    @Test
    void underTheContainerProfileTheRefusalNamesTheConfiguredSecretsDirectory() {
        runner.withPropertyValues(CONTAINER, "charter.container.secrets-dir=/srv/secrets/")
                .run(context -> assertThat(failureText(context))
                        .contains("/srv/secrets/charter.worktree-token")
                        .doesNotContain("/run/secrets"));
    }

    @Test
    void underTheContainerProfileATokenFromTheCommandLineIsRefusedNamingBothAcceptedForms(CapturedOutput output) {
        runner.withPropertyValues(CONTAINER)
                .withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SimpleCommandLinePropertySource("--charter.worktree-token=" + TOKEN)))
                .run(context -> {
                    assertThat(failureText(context))
                            .contains("not from the command line")
                            .contains("CHARTER_WORKTREE_TOKEN")
                            .contains("/run/secrets/charter.worktree-token");
                    assertRefusedWithoutTheToken(context, output);
                });
    }

    @Test
    void underTheContainerProfileABlankTokenSaysAnEmptyEnvVarHidesTheMountedFile() {
        runner.withPropertyValues(CONTAINER)
                .withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addLast(new SystemEnvironmentPropertySource(
                                "test-environment", Map.of("CHARTER_WORKTREE_TOKEN", ""))))
                .run(context -> assertThat(failureText(context))
                        .contains("charter.worktree-token is blank")
                        .contains("an empty CHARTER_WORKTREE_TOKEN hides"));
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }
}
