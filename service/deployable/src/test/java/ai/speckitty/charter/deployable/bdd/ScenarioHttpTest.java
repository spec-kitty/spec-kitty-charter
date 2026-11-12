package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The seam the switch gate reads (ADR-0059 D1): a refusal counts toward its operation only when a step asserted its
 * status, so marking an exchange as asserted marks the last one, and no other.
 */
class ScenarioHttpTest {

    /** Answers 404 for any name, so no exchange probes {@code /info} after it. */
    @RestController
    static class Missing {

        @GetMapping("/api/v1/{name}")
        ResponseEntity<Void> get(@PathVariable String name) {
            return ResponseEntity.notFound().build();
        }
    }

    private final ScenarioHttp http =
            new ScenarioHttp(MockMvcBuilders.standaloneSetup(new Missing()).build(), "token");

    @Test
    void anAssertedStatusMarksTheLastExchangeAlone() throws Exception {
        http.get("/setup");
        http.get("/refused");
        assertThat(http.statusAsserted(1))
                .as("no exchange is marked before a status is asserted")
                .isFalse();

        http.statusAsserted();

        assertThat(http.exchanges()).hasSize(2);
        assertThat(http.statusAsserted(0)).as("the setup request").isFalse();
        assertThat(http.statusAsserted(1)).as("the asserted request").isTrue();
    }

    @Test
    void aStatusCannotBeAssertedBeforeARequest() {
        assertThatThrownBy(http::statusAsserted).isInstanceOf(IllegalStateException.class);
    }
}
