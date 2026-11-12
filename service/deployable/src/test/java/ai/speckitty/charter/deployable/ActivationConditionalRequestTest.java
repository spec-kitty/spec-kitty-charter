package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.transport.EntityTags;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * The conditional requests of the activation register (J10), through the whole service: the reads answer {@code 304}
 * themselves, and the answer agrees with the weak comparison Spring's {@code HttpEntityMethodProcessor} would make on a
 * {@code 200} carrying the same {@code ETag}; a write's malformed precondition is a {@code 400} without a code.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + ActivationTestWiring.TOKEN)
@AutoConfigureMockMvc
@Import({ConfigRootTestConfiguration.class, ActivationTestWiring.class})
class ActivationConditionalRequestTest {

    private static final String DOCUMENT = "/api/v1/activations/c-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    InMemoryActivationRepository repository;

    @Autowired
    ActivationTestWiring.FixturePacks packs;

    private String revision;

    /** The tag getActivation answers now: over the revision and the closure, not the revision (decision 01M6Y11E55YP5TG1EJ1MDKCFRP). */
    private String documentTag() throws Exception {
        MockHttpServletResponse read = send(get(DOCUMENT));
        assertThat(read.getStatus()).isEqualTo(200);
        String tag = read.getHeader(HttpHeaders.ETAG);
        assertThat(tag).matches("\"sha256:[0-9a-f]{64}\"").isNotEqualTo("\"" + revision + "\"");
        return tag;
    }

    /** The revision of the document now stored, from the body of a read. */
    private String storedRevision() throws Exception {
        return JSON.readTree(send(get(DOCUMENT)).getContentAsString())
                .path("revision")
                .asString();
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ActivationTestWiring.TOKEN);
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(authorized(request)).andReturn().getResponse();
    }

    private MockHttpServletResponse putDocument(String body, String... headers) throws Exception {
        MockHttpServletRequestBuilder request =
                put(DOCUMENT).contentType(MediaType.APPLICATION_JSON).content(body);
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return send(request);
    }

    @BeforeEach
    void storeOne() throws Exception {
        repository.clear();
        packs.fingerprint("sha256:fixture");
        MockHttpServletResponse created =
                putDocument("{\"schemaVersion\": 2, \"selected\": {\"mission_type\": [\"research\"]}}");
        assertThat(created.getStatus()).isEqualTo(201);
        revision = JSON.readTree(created.getContentAsString()).path("revision").asString();
        assertThat(created.getHeader(HttpHeaders.ETAG)).isEqualTo("\"" + revision + "\"");
    }

    /** What Spring's weak comparison says of an {@code If-None-Match} against a {@code 200}'s {@code ETag}. */
    private static boolean springSaysNotModified(String ifNoneMatch, String etag) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", DOCUMENT);
        request.addHeader(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        return new ServletWebRequest(request, new MockHttpServletResponse()).checkNotModified(etag);
    }

    @ParameterizedTest
    @ValueSource(strings = {"W/\"%s\"", "\"%s\"", "\"other\", W/\"%s\"", "\"other\"", "W/\"other\"", "\"%s-x\""})
    void theDocumentReadAnswers304ExactlyWhenSpringsWeakComparisonWould(String template) throws Exception {
        String quoted = documentTag();
        String tag = quoted.substring(1, quoted.length() - 1);
        String ifNoneMatch = template.replace("%s", tag);
        String etag = EntityTags.quote(tag);

        MockHttpServletResponse read = send(get(DOCUMENT).header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch));

        boolean notModified = springSaysNotModified(ifNoneMatch, etag);
        assertThat(EntityTags.noneMatch(ifNoneMatch, tag)).as(ifNoneMatch).isEqualTo(notModified);
        assertThat(read.getStatus()).as(ifNoneMatch).isEqualTo(notModified ? 304 : 200);
        assertThat(read.getHeader(HttpHeaders.ETAG)).isEqualTo(etag);
        if (notModified) {
            assertThat(read.getContentAsString()).as("a 304 has no body").isEmpty();
        }
        assertThat(read.getHeader("Charter-Source-Fingerprint")).isNull();
    }

    @Test
    void aStarAnswers304AsTheContractSays() throws Exception {
        // If-None-Match: * matches any current representation (RFC 9110 13.1.2, IfNoneMatch.yaml). Spring's
        // checkNotModified does not match a lone * on a GET, but it re-checks only a 200, never this explicit 304.
        MockHttpServletResponse read = send(get(DOCUMENT).header(HttpHeaders.IF_NONE_MATCH, "*"));

        assertThat(read.getStatus()).isEqualTo(304);
        assertThat(read.getHeader(HttpHeaders.ETAG)).isEqualTo(documentTag());
    }

    @Test
    void aWeakTagOfTheClosureTagAnswers304AndTheRevisionIsNoTagOfTheRead() throws Exception {
        String tag = documentTag();

        MockHttpServletResponse weak = send(get(DOCUMENT).header(HttpHeaders.IF_NONE_MATCH, "W/" + tag));
        MockHttpServletResponse revisionOnly =
                send(get(DOCUMENT).header(HttpHeaders.IF_NONE_MATCH, "\"" + revision + "\""));

        assertThat(weak.getStatus()).isEqualTo(304);
        assertThat(weak.getContentAsString()).isEmpty();
        assertThat(revisionOnly.getStatus())
                .as("the revision is what a write answers, not this read's tag")
                .isEqualTo(200);
    }

    @Test
    void theDocumentReadsTagChangesWithAPackOfTheClosureAndSoDoesThePreconditionOfAWriteThatSendsIt() throws Exception {
        String tag = documentTag();
        assertThat(send(get(DOCUMENT).header(HttpHeaders.IF_NONE_MATCH, tag)).getStatus())
                .isEqualTo(304);

        packs.fingerprint("sha256:changed");

        assertThat(send(get(DOCUMENT).header(HttpHeaders.IF_NONE_MATCH, tag)).getStatus())
                .isEqualTo(200);
        assertThat(putDocument("{\"schemaVersion\": 2}", HttpHeaders.IF_MATCH, tag)
                        .getStatus())
                .as("a closure tag of the closure before the pack changed")
                .isEqualTo(412);
        assertThat(putDocument("{\"schemaVersion\": 2}", HttpHeaders.IF_MATCH, "\"" + revision + "\"")
                        .getStatus())
                .as("the revision is still current")
                .isEqualTo(200);
    }

    @Test
    void theClosureReadAnswers304UntilTheDocumentOrThePacksChange() throws Exception {
        MockHttpServletResponse first = send(get(DOCUMENT + "/packs"));
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(first.getHeader("Charter-Source-Fingerprint")).isNull();
        String etag = first.getHeader(HttpHeaders.ETAG);
        assertThat(etag).matches("\"sha256:[0-9a-f]{64}\"").isNotEqualTo("\"" + revision + "\"");

        assertThat(send(get(DOCUMENT + "/packs").header(HttpHeaders.IF_NONE_MATCH, etag))
                        .getStatus())
                .isEqualTo(304);

        packs.fingerprint("sha256:changed");
        assertThat(send(get(DOCUMENT + "/packs").header(HttpHeaders.IF_NONE_MATCH, etag))
                        .getStatus())
                .isEqualTo(200);

        packs.fingerprint("sha256:fixture");
        assertThat(putDocument("{\"schemaVersion\": 2}").getStatus()).isEqualTo(200);
        assertThat(send(get(DOCUMENT + "/packs").header(HttpHeaders.IF_NONE_MATCH, etag))
                        .getStatus())
                .isEqualTo(200);
    }

    @Test
    void anIfNoneMatchOtherThanStarOnAWriteIs400WithoutACode() throws Exception {
        for (String value : List.of("\"x\"", "W/\"" + revision + "\"", "\"" + revision + "\"")) {
            MockHttpServletResponse answer = putDocument("{\"schemaVersion\": 2}", HttpHeaders.IF_NONE_MATCH, value);

            assertThat(answer.getStatus()).as(value).isEqualTo(400);
            assertThat(JSON.readTree(answer.getContentAsString()).has("code"))
                    .as(value)
                    .isFalse();
        }
        assertThat(storedRevision()).isEqualTo(revision);
    }

    @Test
    void bothValidatorsOnAWriteAre400WithoutACodeAndChangeNothing() throws Exception {
        int reads = packs.reads();

        MockHttpServletResponse answer = putDocument(
                "{\"schemaVersion\": 2}", HttpHeaders.IF_MATCH, "\"" + revision + "\"", HttpHeaders.IF_NONE_MATCH, "*");

        assertThat(answer.getStatus()).isEqualTo(400);
        assertThat(JSON.readTree(answer.getContentAsString()).has("code")).isFalse();
        assertThat(JSON.readTree(answer.getContentAsString()).path("detail").asString())
                .isEqualTo("The headers If-Match and If-None-Match exclude each other, and the request sends both.");
        assertThat(packs.reads()).isEqualTo(reads);
        assertThat(storedRevision()).isEqualTo(revision);
    }

    @Test
    void anIfMatchThatIsNotOneQuotedRevisionIs400() throws Exception {
        MockHttpServletResponse answer =
                putDocument("{\"schemaVersion\": 2}", HttpHeaders.IF_MATCH, "W/\"" + revision + "\"");

        assertThat(answer.getStatus()).isEqualTo(400);
    }

    @Test
    void aStaleIfMatchIs412AndAFreshOneIs200() throws Exception {
        MockHttpServletResponse stale =
                putDocument("{\"schemaVersion\": 2}", HttpHeaders.IF_MATCH, "\"sha256:" + "0".repeat(64) + "\"");
        MockHttpServletResponse fresh =
                putDocument("{\"schemaVersion\": 2}", HttpHeaders.IF_MATCH, "\"" + revision + "\"");

        assertThat(stale.getStatus()).isEqualTo(412);
        assertThat(fresh.getStatus()).isEqualTo(200);
        assertThat(fresh.getHeader(HttpHeaders.LOCATION)).isNull();
    }

    @Test
    void deleteHonoursIfMatch() throws Exception {
        String other = "\"sha256:" + "0".repeat(64) + "\"";

        assertThat(send(delete(DOCUMENT).header(HttpHeaders.IF_MATCH, other)).getStatus())
                .isEqualTo(412);
        assertThat(send(delete(DOCUMENT).header(HttpHeaders.IF_MATCH, "\"" + revision + "\""))
                        .getStatus())
                .isEqualTo(204);
        assertThat(send(get(DOCUMENT)).getStatus()).isEqualTo(404);
    }
}
