package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class WorktreeGuardTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String UNAUTHORIZED = "{\"title\":\"Unauthorized\",\"status\":401}";
    private static final String FORBIDDEN = "{\"title\":\"Forbidden\",\"status\":403}";

    private final AtomicInteger digests = new AtomicInteger();
    private final ServiceInfoQuery info = new ServiceInfoQuery("wt-1", new ConsistentRead(() -> {
        digests.incrementAndGet();
        return "sha256:" + "0".repeat(64);
    }));
    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new ProblemJson().problemJsonModule())
            .build();
    private final WorktreeGuard guard = new WorktreeGuard(WorktreeToken.of(TOKEN), info, () -> mapper);

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/info");
        request.setServerName("localhost");
        return request;
    }

    private MockHttpServletRequest authorized() {
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }

    private record Outcome(MockHttpServletResponse response, MockFilterChain chain) {
        boolean passed() {
            return chain.getRequest() != null;
        }
    }

    private Outcome run(WorktreeGuard guard, MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        guard.doFilter(request, response, chain);
        return new Outcome(response, chain);
    }

    private Outcome run(MockHttpServletRequest request) throws ServletException, IOException {
        return run(guard, request);
    }

    private void assertUnauthorized(Outcome outcome) throws IOException {
        MockHttpServletResponse response = outcome.response();
        assertThat(outcome.passed())
                .as("the chain is never called on a refusal")
                .isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeaders("WWW-Authenticate")).containsExactly("Bearer");
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).isEqualTo(UNAUTHORIZED);
        assertCharterHeadersWithoutFingerprint(response);
    }

    private void assertForbidden(Outcome outcome) throws IOException {
        MockHttpServletResponse response = outcome.response();
        assertThat(outcome.passed())
                .as("the chain is never called on a refusal")
                .isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeaderNames()).doesNotContain("WWW-Authenticate");
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).isEqualTo(FORBIDDEN);
        assertCharterHeadersWithoutFingerprint(response);
    }

    private void assertCharterHeadersWithoutFingerprint(MockHttpServletResponse response) {
        assertThat(response.getHeaders("Charter-Contract-Version")).containsExactly(ContractVersion.value());
        assertThat(response.getHeaders("Charter-Worktree")).containsExactly("wt-1");
        assertThat(response.getHeaderNames()).doesNotContain("Charter-Source-Fingerprint");
        assertThat(digests).as("a refusal digests nothing").hasValue(0);
    }

    @Test
    void theRightTokenPassesTheRequestOnAndWritesNothing() throws Exception {
        Outcome outcome = run(authorized());
        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.response().getStatus()).isEqualTo(200);
        assertThat(outcome.response().getContentAsString()).isEmpty();
        assertThat(digests).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer ", "bearer ", "BEARER ", "bEaReR "})
    void theBearerSchemeIsMatchedWithoutRegardToCase(String scheme) throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", scheme + TOKEN);
        assertThat(run(request).passed()).isTrue();
    }

    @Test
    void aRequestWithoutAnAuthorizationHeaderIsUnauthorized() throws Exception {
        assertUnauthorized(run(request()));
    }

    @Test
    void aWrongTokenIsUnauthorizedAndTheBodyNeverEchoesIt() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer wrong-wrong-wrong-wrong-wrong-wrong-wrong");
        Outcome outcome = run(request);
        assertUnauthorized(outcome);
        assertThat(outcome.response().getContentAsString()).doesNotContain("wrong", TOKEN);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Basic dXNlcjpwYXNzd2Q=",
                "Bearer",
                "Bearer ",
                "Bearer  ",
                "Token 0123456789abcdef0123456789abcdef0123",
                "0123456789abcdef0123456789abcdef0123",
                "Bearer 0123456789abcdef0123456789abcdef0123 extra",
                "Bearer\t0123456789abcdef0123456789abcdef0123",
                "",
                " "
            })
    void aMalformedAuthorizationHeaderIsUnauthorized(String header) throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", header);
        assertUnauthorized(run(request));
    }

    @Test
    void twoAuthorizationHeadersAreUnauthorizedEvenWhenOneIsRight() throws Exception {
        MockHttpServletRequest request = authorized();
        request.addHeader("Authorization", "Bearer something-else");
        assertUnauthorized(run(request));
    }

    @Test
    void aTokenInTheQueryStringIsNotATokenAtAll() throws Exception {
        MockHttpServletRequest request = request();
        request.setQueryString("token=" + TOKEN);
        request.setParameter("token", TOKEN);
        assertUnauthorized(run(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Origin", "Sec-Fetch-Site", "Sec-Fetch-Mode"})
    void aBrowserHeaderIsForbiddenEvenWithTheRightToken(String header) throws Exception {
        MockHttpServletRequest request = authorized();
        request.addHeader(header, "https://evil.example");
        assertForbidden(run(request));
    }

    @Test
    void aBrowserHeaderWithAnEmptyValueIsStillForbidden() throws Exception {
        MockHttpServletRequest request = authorized();
        request.addHeader("Origin", "");
        assertForbidden(run(request));
    }

    @Test
    void theBrowserHeaderNamesAreMatchedWithoutRegardToCase() throws Exception {
        MockHttpServletRequest request = authorized();
        request.addHeader("origin", "null");
        assertForbidden(run(request));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "attacker.example",
                "attacker.example:8080",
                "localhost.attacker.example",
                "127.0.0.1.attacker.example",
                "192.168.1.5",
                "127.0.0.2",
                "0.0.0.0",
                "localhost.",
                "[::2]:8080",
                "[::1].attacker.example",
                ""
            })
    void aHostNameThatIsNotALoopbackNameIsForbiddenEvenWithTheRightToken(String host) throws Exception {
        MockHttpServletRequest request = authorized();
        request.addHeader("Host", host);
        request.setServerName(host);
        assertForbidden(run(request));
    }

    /** A request whose server name is not what its Host header says, as a container may report for an absolute-form target. */
    private static MockHttpServletRequest disagreeing(String serverName, String hostHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/info") {
            @Override
            public String getServerName() {
                return serverName;
            }
        };
        request.addHeader("Authorization", "Bearer " + TOKEN);
        request.addHeader("Host", hostHeader);
        return request;
    }

    @Test
    void aHostHeaderThatDisagreesWithTheServerNameIsJudgedToo() throws Exception {
        assertForbidden(run(disagreeing("localhost", "attacker.example")));
        assertForbidden(run(disagreeing("attacker.example", "localhost")));
        assertThat(run(disagreeing("localhost", "127.0.0.1:80")).passed()).isTrue();
    }

    @Test
    void twoHostHeadersAreForbiddenWhenAnyIsNotLoopback() throws Exception {
        MockHttpServletRequest request = authorized();
        request.addHeader("Host", "localhost");
        request.addHeader("Host", "attacker.example");
        assertForbidden(run(request));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"localhost", "LOCALHOST", "LocalHost:8080", "127.0.0.1", "127.0.0.1:54321", "[::1]", "[::1]:8080"
            })
    void aLoopbackHostNameIsAcceptedAtAnyPort(String host) throws Exception {
        MockHttpServletRequest request = authorized();
        request.addHeader("Host", host);
        request.setServerName(
                host.startsWith("[") ? host.substring(0, host.indexOf(']') + 1) : host.replaceAll(":\\d+$", ""));
        assertThat(run(request).passed()).isTrue();
    }

    @Test
    void aBareIpv6LoopbackServerNameIsAccepted() throws Exception {
        MockHttpServletRequest request = authorized();
        request.setServerName("::1");
        assertThat(run(request).passed()).isTrue();
    }

    @Test
    void aBrowserRequestWithoutATokenIsForbiddenNotUnauthorized() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("Origin", "https://evil.example");
        assertForbidden(run(request));
    }

    @Test
    void aRebindingHostWithAWrongTokenIsForbiddenNotUnauthorized() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("Host", "attacker.example");
        request.setServerName("attacker.example");
        request.addHeader("Authorization", "Bearer wrong");
        assertForbidden(run(request));
    }

    @Test
    void withoutAConfiguredTokenARequestPassesOn() throws Exception {
        WorktreeGuard open = new WorktreeGuard(null, info, () -> mapper);
        assertThat(run(open, request()).passed()).isTrue();
    }

    @Test
    void withoutAConfiguredTokenAWrongTokenPassesToo() throws Exception {
        WorktreeGuard open = new WorktreeGuard(null, info, () -> mapper);
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Basic abc");
        assertThat(run(open, request).passed()).isTrue();
    }

    @Test
    void withoutAConfiguredTokenTheBrowserChecksAreStillOn() throws Exception {
        WorktreeGuard open = new WorktreeGuard(null, info, () -> mapper);
        MockHttpServletRequest origin = request();
        origin.addHeader("Origin", "https://evil.example");
        assertForbidden(run(open, origin));
        MockHttpServletRequest fetch = request();
        fetch.addHeader("Sec-Fetch-Mode", "navigate");
        assertForbidden(run(open, fetch));
        MockHttpServletRequest host = request();
        host.addHeader("Host", "attacker.example");
        host.setServerName("attacker.example");
        assertForbidden(run(open, host));
    }

    @Test
    void everyMethodAndPathIsGuarded() throws Exception {
        for (String method : new String[] {"GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH"}) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/anything/at/all");
            request.setServerName("localhost");
            MockHttpServletResponse response = run(request).response();
            assertThat(response.getStatus()).as(method).isEqualTo(401);
        }
    }

    // A configured list (ADR-0041 D6) with the container's health exemption (D7), as GuardWiring builds it under the
    // container profile; the packs module only sees the policy.

    private static final String HEALTH = "/actuator/health";

    private final WorktreeGuard named = new WorktreeGuard(
            WorktreeToken.of(TOKEN),
            GuardPolicy.named(List.of("charter.internal")).withTokenExemptGet(HEALTH),
            info,
            () -> mapper);

    private static MockHttpServletRequest at(String method, String uri, String host) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setServerName(
                host.startsWith("[") ? host.substring(0, host.indexOf(']') + 1) : host.replaceAll(":\\d*$", ""));
        request.addHeader("Host", host);
        return request;
    }

    private static MockHttpServletRequest withToken(MockHttpServletRequest request) {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = {"charter.internal", "CHARTER.internal:8080", "charter.internal:", " charter.internal "})
    void aListedHostPassesAtAnyPortAndInAnyCase(String host) throws Exception {
        assertThat(run(named, withToken(at("GET", "/api/v1/info", host))).passed())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "localhost",
                "127.0.0.1",
                "attacker.example",
                "charter.internal.",
                "charter.internal.attacker.example",
                "attacker.charter.internal",
                "[::1]"
            })
    void anUnlistedHostIsForbiddenUnderAListEvenWithTheRightToken(String host) throws Exception {
        assertForbidden(run(named, withToken(at("GET", "/api/v1/info", host))));
    }

    @Test
    void underAListARequestWithoutAHostHeaderIsForbidden() throws Exception {
        MockHttpServletRequest request = withToken(new MockHttpServletRequest("GET", "/api/v1/info"));
        request.setServerName("charter.internal");
        assertForbidden(run(named, request));
    }

    @Test
    void withoutAListARequestWithoutAHostHeaderIsStillJudgedOnTheServerName() throws Exception {
        assertThat(run(authorized()).passed()).isTrue();
        MockHttpServletRequest elsewhere = authorized();
        elsewhere.setServerName("10.0.0.5");
        assertForbidden(run(elsewhere));
    }

    @Test
    void aHostWithWhitespaceBeforeItsPortIsStillForbidden() throws Exception {
        assertForbidden(run(disagreeing("localhost", "localhost :8080")));
    }

    @Test
    void underAListAnUnlistedServerNameIsForbiddenThoughHostIsListed() throws Exception {
        assertForbidden(run(named, disagreeing("attacker.example", "charter.internal")));
        assertThat(run(named, disagreeing("charter.internal", "charter.internal:8080"))
                        .passed())
                .isTrue();
    }

    @Test
    void underAListABrowserHeaderIsStillForbidden() throws Exception {
        MockHttpServletRequest request = withToken(at("GET", "/api/v1/info", "charter.internal"));
        request.addHeader("Origin", "https://charter.internal");
        assertForbidden(run(named, request));
    }

    @Test
    void underAListTheTokenIsStillRequiredOutsideTheExemptPath() throws Exception {
        assertUnauthorized(run(named, at("GET", "/api/v1/info", "charter.internal")));
    }

    @Test
    void theExemptGetPassesWithoutAToken() throws Exception {
        assertThat(run(named, at("GET", HEALTH, "charter.internal")).passed()).isTrue();
    }

    @Test
    void theExemptGetStillNeedsAnAcceptedHostAndNoBrowserHeader() throws Exception {
        assertForbidden(run(named, at("GET", HEALTH, "attacker.example")));
        MockHttpServletRequest browser = at("GET", HEALTH, "charter.internal");
        browser.addHeader("Sec-Fetch-Site", "cross-site");
        assertForbidden(run(named, browser));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HEAD", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"})
    void onlyAGetOfTheExemptPathSkipsTheToken(String method) throws Exception {
        assertUnauthorized(run(named, at(method, HEALTH, "charter.internal")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/actuator/health/",
                "/actuator/health;x=y",
                "/actuator/health/liveness",
                "/actuator/health/readiness",
                "/actuator/health/../env",
                "/actuator/%68ealth",
                "/ACTUATOR/health",
                "/actuator/Health",
                "/actuator/healthz",
                "/actuator",
                "/actuator/env",
                "//actuator/health",
                "/actuator//health"
            })
    void aNearMissOfTheExemptPathNeedsTheToken(String uri) throws Exception {
        assertUnauthorized(run(named, at("GET", uri, "charter.internal")));
    }

    @Test
    void theExemptPathUnderAContextPathNeedsTheToken() throws Exception {
        MockHttpServletRequest request = at("GET", "/ctx" + HEALTH, "charter.internal");
        request.setContextPath("/ctx");
        assertUnauthorized(run(named, request));
    }

    @Test
    void theLoopbackPolicyExemptsNothing() throws Exception {
        assertUnauthorized(run(at("GET", HEALTH, "localhost")));
    }
}
