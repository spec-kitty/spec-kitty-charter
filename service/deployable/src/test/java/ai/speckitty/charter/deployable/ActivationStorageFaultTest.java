package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.infra.storage.FileActivationRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A fault of the activation store is the service's, not the client's: a stored document that cannot be read back is
 * answered {@code 500 internal_error}, with the generic detail and nothing of the failure (no exception name, no stack
 * frame, no path), on the read and on the write that reads it first.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + ActivationTestWiring.TOKEN)
@AutoConfigureMockMvc
@Import({ConfigRootTestConfiguration.class, ActivationStorageFaultTest.BrokenStore.class})
class ActivationStorageFaultTest {

    private static final String BROKEN = "broken";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A file register holding one document that is not JSON, over the fixture packs. */
    @TestConfiguration(proxyBeanMethods = false)
    static class BrokenStore {
        @Bean
        @Primary
        ActivationTestWiring.FixturePacks fixturePacks() {
            return new ActivationTestWiring.FixturePacks();
        }

        @Bean
        @Primary
        ActivationRepository brokenActivationRepository() {
            try {
                Path root = Files.createTempDirectory("charter-broken-activations");
                Path file = Files.createDirectories(root.resolve(BROKEN)).resolve("activation.json");
                Files.writeString(file, "{ this is not an activation", StandardCharsets.UTF_8);
                return new FileActivationRepository(root);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Autowired
    MockMvc mvc;

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ActivationTestWiring.TOKEN))
                .andReturn()
                .getResponse();
    }

    private static void isAnInternalErrorThatLeaksNothing(MockHttpServletResponse answer) throws Exception {
        String text = answer.getContentAsString();
        assertThat(answer.getStatus()).as(text).isEqualTo(500);
        assertThat(answer.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode body = JSON.readTree(text);
        assertThat(body.path("code").asString()).isEqualTo("internal_error");
        assertThat(body.path("detail").asString())
                .isEqualTo("The charter service failed unexpectedly; the failure is logged.");
        assertThat(text)
                .doesNotContain("Exception")
                .doesNotContain("at ai.")
                .doesNotContain(BROKEN)
                .doesNotContain("activation.json");
    }

    @Test
    void anUnreadableStoredDocumentIsAnInternalErrorOnRead() throws Exception {
        isAnInternalErrorThatLeaksNothing(send(get("/api/v1/activations/" + BROKEN)));
        isAnInternalErrorThatLeaksNothing(send(get("/api/v1/activations/" + BROKEN + "/packs")));
    }

    @Test
    void anUnreadableStoredDocumentIsAnInternalErrorOnAConditionalWrite() throws Exception {
        isAnInternalErrorThatLeaksNothing(send(put("/api/v1/activations/" + BROKEN)
                .header(HttpHeaders.IF_MATCH, "*")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\": 1}")));
    }
}
