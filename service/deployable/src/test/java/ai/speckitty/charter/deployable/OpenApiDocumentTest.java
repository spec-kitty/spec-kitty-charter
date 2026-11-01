package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code GET /api/v1/openapi.yaml} over the whole application (#209): the answer is the bundle the contract build
 * wrote, byte for byte, tagged with the checksum the build wrote beside it; a held copy is revalidated with a
 * {@code 304}; and the request is refused like any other. The expected bytes, digest and version come from the
 * build output ({@code build.gradle.kts} passes both files), never from the service's own copy. Every request
 * starts from {@link #document()}, so each refusal is the same request as the {@code 200} that proves the route
 * answers.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + OpenApiDocumentTest.TOKEN)
@AutoConfigureMockMvc
class OpenApiDocumentTest {

    static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String PATH = "/api/v1/openapi.yaml";

    private static final byte[] BUNDLE = read("charter.test.contract-bundle");
    /** The checksum file reads {@code <hex>  charter-openapi.yaml}, as {@code sha256sum} writes it. */
    private static final String DIGEST =
            new String(read("charter.test.contract-bundle-checksum"), StandardCharsets.US_ASCII).substring(0, 64);

    private static final String ETAG = "\"sha256:" + DIGEST + "\"";

    @Autowired
    private MockMvc mvc;

    private static MockHttpServletRequestBuilder document() {
        return get(PATH).header("Authorization", "Bearer " + TOKEN);
    }

    private MockHttpServletResponse answer(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    @Test
    void theServedBundleKeepsItsComponentsAndReadsWithinTheYamlReadersDefaultLimit() {
        // #294: the bundle refers to its components instead of inlining each use, so it stays well below snakeyaml's
        // default limit of 3 MiB of code points, and a client reads it without raising that limit.
        Map<?, ?> document = (Map<?, ?>)
                new Load(LoadSettings.builder().build()).loadFromString(new String(BUNDLE, StandardCharsets.UTF_8));
        Map<?, ?> components = (Map<?, ?>) document.get("components");

        assertThat(keys(components))
                .contains("schemas", "responses", "parameters", "headers", "examples")
                .doesNotContain("pathItems");
        assertThat(keys((Map<?, ?>) components.get("schemas")))
                .contains("CharterRefusal", "CharterRefusalCode", "Problem");
        assertThat(new String(BUNDLE, StandardCharsets.UTF_8)
                        .lines()
                        .filter(line -> line.contains("$ref:"))
                        .map(String::strip))
                .isNotEmpty()
                .allMatch(line -> line.startsWith("$ref: '#/components/") || line.startsWith("- $ref: '#/components/"));
    }

    @Test
    void theBuildOutputIsNotAsciiSoAWrongCharsetWouldShowInTheBytes() {
        assertThat(DIGEST).matches("[0-9a-f]{64}");
        assertThat(new String(BUNDLE, StandardCharsets.UTF_8)).containsPattern("[^\\x00-\\x7F]");
    }

    @Test
    void aClientWithTheTokenGetsTheBundleByteForByteWithItsChecksumAsETag() throws Exception {
        MockHttpServletResponse response = answer(document());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Content-Type")).isEqualTo("application/yaml");
        assertThat(response.getContentAsByteArray()).isEqualTo(BUNDLE);
        assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(BUNDLE.length));
        assertThat(response.getHeaders("ETag")).containsExactly(ETAG);
        assertThat(response.getHeader("Charter-Contract-Version")).isEqualTo(infoVersion());
        assertThat(response.getHeader("Charter-Worktree")).isNotBlank();
        assertThat(response.getHeaderNames()).doesNotContain("Charter-Source-Fingerprint");
    }

    @Test
    void aMatchingIfNoneMatchIsAnswered304WithoutABodyAndWithTheETag() throws Exception {
        for (String held : new String[] {ETAG, "W/" + ETAG, "\"older\", " + ETAG, "*"}) {
            MockHttpServletResponse response = answer(document().header("If-None-Match", held));

            assertThat(response.getStatus()).as(held).isEqualTo(304);
            assertThat(response.getContentAsByteArray()).as(held).isEmpty();
            assertThat(response.getHeaders("ETag")).as(held).containsExactly(ETAG);
            assertThat(response.getContentType()).as(held).isNull();
            assertThat(response.getHeader("Charter-Contract-Version")).as(held).isEqualTo(infoVersion());
            assertThat(response.getHeader("Charter-Worktree")).as(held).isNotBlank();
            assertThat(response.getHeaderNames()).as(held).doesNotContain("Charter-Source-Fingerprint");
        }
    }

    @Test
    void ifNoneMatchSentOnTwoHeaderLinesIsOneList() throws Exception {
        MockHttpServletResponse response = answer(document().header("If-None-Match", "\"older\"", ETAG));

        assertThat(response.getStatus()).isEqualTo(304);
    }

    /** Spring's own conditional check would answer the first two 304; the contract answers 200. */
    @Test
    void aValueThatIsNotAnEntityTagListOrDoesNotMatchGetsTheDocument() throws Exception {
        for (String held : new String[] {"garbage " + ETAG, "w/" + ETAG, "\"sha256:" + "0".repeat(64) + "\""}) {
            MockHttpServletResponse response = answer(document().header("If-None-Match", held));

            assertThat(response.getStatus()).as(held).isEqualTo(200);
            assertThat(response.getContentAsByteArray()).as(held).isEqualTo(BUNDLE);
            assertThat(response.getHeaders("ETag")).as(held).containsExactly(ETAG);
        }
    }

    /**
     * {@code HEAD} reaches the same handler and the same conditional rule. Dropping the body is the servlet
     * container's part of {@code HEAD}, which MockMvc does not play, so it is not asserted here.
     */
    @Test
    void headAnswersAsGet() throws Exception {
        MockHttpServletResponse full = answer(head(PATH).header("Authorization", "Bearer " + TOKEN));
        MockHttpServletResponse held =
                answer(head(PATH).header("Authorization", "Bearer " + TOKEN).header("If-None-Match", ETAG));

        assertThat(full.getStatus()).isEqualTo(200);
        assertThat(full.getHeader("Content-Type")).isEqualTo("application/yaml");
        assertThat(full.getHeader("Content-Length")).isEqualTo(String.valueOf(BUNDLE.length));
        assertCharterHeadersAndETag(full);
        assertThat(held.getStatus()).isEqualTo(304);
        assertThat(held.getContentAsByteArray()).isEmpty();
        assertCharterHeadersAndETag(held);
    }

    /** An {@code Accept} the operation cannot answer is a 406 through the shared Problem handling, untagged. */
    @Test
    void anAcceptItCannotAnswerIsA406ProblemWithoutTheETag() throws Exception {
        MockHttpServletResponse response = answer(document().accept(MediaType.APPLICATION_JSON));

        assertThat(response.getStatus()).isEqualTo(406);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).isEqualTo("{\"title\":\"Not Acceptable\",\"status\":406}");
        assertThat(response.getHeader("Charter-Contract-Version")).isEqualTo(infoVersion());
        assertThat(response.getHeaderNames()).doesNotContain("ETag");
    }

    private static void assertCharterHeadersAndETag(MockHttpServletResponse response) {
        assertThat(response.getHeaders("ETag")).containsExactly(ETAG);
        assertThat(response.getHeader("Charter-Contract-Version")).isEqualTo(infoVersion());
        assertThat(response.getHeader("Charter-Worktree")).isNotBlank();
        assertThat(response.getHeaderNames()).doesNotContain("Charter-Source-Fingerprint");
    }

    @Test
    void withoutTheTokenItIsA401ProblemThatDoesNotShowTheETag() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            get(PATH).header("If-None-Match", ETAG),
            get(PATH).header("Authorization", "Bearer " + "f".repeat(36)).header("If-None-Match", ETAG)
        }) {
            MockHttpServletResponse response = answer(request);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(response.getHeaderNames()).doesNotContain("ETag");
            assertThat(response.getContentAsString()).isEqualTo("{\"title\":\"Unauthorized\",\"status\":401}");
        }
    }

    @Test
    void aBrowserRequestIsA403ProblemEvenWithTheTokenAndAMatchingTag() throws Exception {
        MockHttpServletResponse response =
                answer(document().header("Origin", "https://evil.example").header("If-None-Match", ETAG));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getHeaderNames()).doesNotContain("ETag");
        assertThat(response.getContentAsString()).isEqualTo("{\"title\":\"Forbidden\",\"status\":403}");
    }

    /** The served document's {@code info.version}, read from the build output, not from the service. */
    private static String infoVersion() {
        Map<?, ?> document = (Map<?, ?>)
                new Load(LoadSettings.builder().build()).loadFromString(new String(BUNDLE, StandardCharsets.UTF_8));
        return (String) ((Map<?, ?>) document.get("info")).get("version");
    }

    private static List<String> keys(Map<?, ?> map) {
        return map.keySet().stream().map(String::valueOf).toList();
    }

    private static byte[] read(String property) {
        String path = System.getProperty(property);
        if (path == null) {
            throw new IllegalStateException(property + " is not set; the build passes the bundle to the tests");
        }
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}
