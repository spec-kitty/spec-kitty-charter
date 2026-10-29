package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ai.speckitty.charter.packs.infra.api.ContractDocument;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The document leaves the service as the UTF-8 bytes it was built from, whatever it holds: a document of text outside
 * Latin-1 goes through the application's real message converters. Spring's own string converter would write
 * ISO-8859-1; Boot's writes UTF-8, and this test pins that, independently of what the bundle happens to contain.
 */
@SpringBootTest(
        classes = {CharterServiceApplication.class, OpenApiDocumentEncodingTest.Fixture.class},
        properties = "charter.worktree-token=" + OpenApiDocumentTest.TOKEN)
@AutoConfigureMockMvc
class OpenApiDocumentEncodingTest {

    private static final byte[] TEXT = "info: {title: café — € 漢字 🐈}\n".getBytes(StandardCharsets.UTF_8);

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixture {
        @Bean
        @Primary
        ContractDocument fixtureDocument() {
            return new ContractDocument(TEXT);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void theBodyIsTheDocumentsUtf8BytesUnderAContentTypeWithoutCharset() throws Exception {
        MockHttpServletResponse response = mvc.perform(
                        get("/api/v1/openapi.yaml").header("Authorization", "Bearer " + OpenApiDocumentTest.TOKEN))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Content-Type")).isEqualTo("application/yaml");
        assertThat(response.getContentAsByteArray()).isEqualTo(TEXT);
    }
}
