package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class GuardPolicyTest {

    private static MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void theLoopbackPolicyAcceptsTheLoopbackNamesOnlyAndRequiresNoHostHeader() {
        GuardPolicy loopback = GuardPolicy.loopback();
        assertThat(List.of("localhost", "LOCALHOST", "127.0.0.1", "[::1]", "::1"))
                .allMatch(loopback::acceptsHost);
        assertThat(List.of("charter.internal", "127.0.0.2", "0.0.0.0", "")).noneMatch(loopback::acceptsHost);
        assertThat(loopback.hostHeaderRequired()).isFalse();
        assertThat(loopback.exemptsFromToken(get("/actuator/health"))).isFalse();
    }

    @Test
    void aNamedPolicyAcceptsItsNamesWithoutRegardToCaseAndRequiresAHostHeader() {
        GuardPolicy named = GuardPolicy.named(List.of("Charter.Internal", "10.0.0.5"));
        assertThat(List.of("charter.internal", "CHARTER.INTERNAL", "10.0.0.5")).allMatch(named::acceptsHost);
        assertThat(List.of("localhost", "charter.internal.", "internal")).noneMatch(named::acceptsHost);
        assertThat(named.hostHeaderRequired()).isTrue();
    }

    @Test
    void aNamedPolicyRefusesAnEmptyListOrABlankName() {
        assertThatIllegalArgumentException().isThrownBy(() -> GuardPolicy.named(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> GuardPolicy.named(List.of("charter.internal", " ")));
    }

    @Test
    void anExemptPathMustBeAbsolute() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GuardPolicy.loopback().withTokenExemptGet("actuator/health"));
    }

    @Test
    void addingAnExemptPathLeavesTheOriginalPolicyUnchanged() {
        GuardPolicy base = GuardPolicy.named(List.of("charter.internal"));
        base.withTokenExemptGet("/actuator/health");
        assertThat(base.exemptsFromToken(get("/actuator/health"))).isFalse();
    }
}
