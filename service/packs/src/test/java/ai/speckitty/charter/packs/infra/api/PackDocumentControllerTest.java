package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackDocumentQuery;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.domain.PackDocumentText;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.web.ProblemHandler;
import ai.speckitty.charter.web.RefusalHandler;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP contract of {@code GET /packs/{packId}/doc} (#451): the form the {@code Accept} header chooses, the
 * {@code ETag} both forms share, {@code Vary: Accept} on every 200, 304 and 406, the 304 the service decides itself, and
 * the two 404 codes. Each case states what the contract states and, for the choice of form, what RFC 9110, section
 * 12.5.1, asks: quality first, then specificity.
 */
class PackDocumentControllerTest {

    private static final String README =
            "---\naudience: pack authors\n---\n\n# Acme review pack\n\nWhat the pack is for.\n\n## Contents\n\nDirectives.\n";
    private static final String BODY = "What the pack is for.\n\n## Contents\n\nDirectives.\n";
    private static final PackIdentifier ACME = new PackIdentifier("acme");
    private static final PackRevision REVISION = PackRevision.compute(List.of("acme", "revision"));
    private static final String TAG = "\"" + REVISION.token() + "\"";
    private static final String FINGERPRINT = "sha256:" + "a".repeat(64);
    private static final String PATH = "/api/v1/packs/acme/doc";

    private final PackDocumentQuery query = mock(PackDocumentQuery.class);
    private final MockMvc mvc = mvc(query);

    PackDocumentControllerTest() {
        read(Optional.of("Acme review pack"), BODY);
    }

    private static MockMvc mvc(PackDocumentQuery query) {
        PackRegistrationMapper mapper = new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler());
        ServiceInfoQuery info =
                new ServiceInfoQuery("worktree-1", new ConsistentRead(new FileSystemPackSources(List::of)));
        return MockMvcBuilders.standaloneSetup(new PackRegistrationController(
                        mock(RegistrationJobs.class),
                        mock(PackSummaryQuery.class),
                        mock(PackArchiveReader.class),
                        mapper,
                        PackSourceIntake.none(),
                        mock(PackRemovalController.class),
                        new PackDocumentController(query, mapper)))
                .setMessageConverters(HttpConverters.serverConverters().toArray(new HttpMessageConverter<?>[0]))
                .setControllerAdvice(new RefusalHandler(), new ProblemHandler(), new PackDocumentETag())
                .addFilters(new CharterHeaders(info))
                .addPlaceholderValue("charter.api.base-path", "/api/v1")
                .build();
    }

    private void read(Optional<String> title, String body) {
        PackDocumentText text = new PackDocumentText(README, title, body);
        when(query.document("acme"))
                .thenReturn(
                        new ConsistentRead.Verified<>(new PackDocumentQuery.Read(ACME, text, REVISION), FINGERPRINT));
    }

    private ResultActions accepting(String accept) throws Exception {
        return mvc.perform(get(PATH).header("Accept", accept));
    }

    // ---- the two forms -------------------------------------------------------------------------------------------

    @Test
    void markdownIsTheHeldReadmeByteForByteWithTheSharedHeaders() throws Exception {
        accepting("text/markdown")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown;charset=UTF-8"))
                .andExpect(content().string(README))
                .andExpect(header().string("ETag", TAG))
                .andExpect(header().string("Vary", "Accept"))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().exists("Charter-Contract-Version"))
                .andExpect(header().string("Charter-Worktree", "worktree-1"));
    }

    @Test
    void jsonIsTheTitleTheBodyAndTheTwoLinksWithTheSameTagAndVary() throws Exception {
        accepting("application/json")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.title").value("Acme review pack"))
                .andExpect(jsonPath("$.body").value(BODY))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/packs/acme/doc"))
                .andExpect(jsonPath("$._links.pack.href").value("/api/v1/packs/acme"))
                .andExpect(jsonPath("$._links.length()").value(2))
                .andExpect(header().string("ETag", TAG))
                .andExpect(header().string("Vary", "Accept"))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT));
    }

    @Test
    void theJsonBodyHasNoFrontmatterAndNoTitleLine() throws Exception {
        accepting("application/json")
                .andExpect(jsonPath("$.body")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("audience"))))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.startsWith("#"))));
    }

    @Test
    void aDocumentWithNoTitleLeavesTheTitleOutOfTheJsonForm() throws Exception {
        read(Optional.empty(), "Words.\n");

        accepting("application/json")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.body").value("Words.\n"));
    }

    // ---- the choice of form: quality, then specificity -----------------------------------------------------------

    @ParameterizedTest(name = "Accept: {0} answers JSON")
    @ValueSource(
            strings = {
                "application/json",
                "application/*",
                "*/*",
                "text/markdown;q=0.5, application/json",
                "text/markdown;q=0.5, */*",
                "text/*;q=0.1, application/*",
                "application/json, text/markdown;q=0.9",
                "image/png, application/json"
            })
    void theseAcceptValuesAnswerJson(String accept) throws Exception {
        accepting(accept).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @ParameterizedTest(name = "Accept: {0} answers Markdown")
    @ValueSource(
            strings = {
                "text/markdown",
                "text/*",
                "text/markdown, application/json;q=0.5",
                "application/json;q=0.5, text/markdown",
                "text/*, application/json;q=0.5",
                "text/markdown, */*;q=0.1"
            })
    void theseAcceptValuesAnswerMarkdown(String accept) throws Exception {
        accepting(accept)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown;charset=UTF-8"));
    }

    @Test
    void noAcceptAnswersJson() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    /**
     * A tie in quality and in specificity is left to the server (RFC 9110, section 12.5.1). Two mappings with disjoint
     * {@code produces} settle it by the order the client wrote the types in; a range that covers both ({@code *}{@code /*})
     * is not a tie between two named types, and JSON, the form the contract lists first, answers it.
     */
    @ParameterizedTest(name = "Accept: {0} is a tie and answers {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "application/json, text/markdown|application/json",
                "text/markdown, application/json|text/markdown",
                "text/*, application/*|text/markdown",
                "application/*, text/*|application/json",
                "text/markdown;q=0.8, application/json;q=0.8|text/markdown",
                "application/json;q=0.8, text/markdown;q=0.8|application/json"
            })
    void aTieGoesToTheTypeTheClientListsFirst(String accept, String answered) throws Exception {
        accepting(accept).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(answered));
    }

    // ---- 406 -----------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "Accept: {0} is 406")
    @ValueSource(strings = {"image/png", "application/xml", "text/html", "application/problem+json"})
    void anAcceptThatMatchesNeitherFormIsA406ProblemWithNoCodeAndTheSharedHeaders(String accept) throws Exception {
        accepting(accept)
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(header().string("Vary", "Accept"))
                .andExpect(header().exists("Charter-Contract-Version"))
                .andExpect(header().string("Charter-Worktree", "worktree-1"))
                .andExpect(header().doesNotExist("Charter-Source-Fingerprint"))
                .andExpect(header().doesNotExist("ETag"));
    }

    // ---- 304: the service decides, not Spring --------------------------------------------------------------------

    @ParameterizedTest(name = "If-None-Match: {0} is 304 for {1}")
    @CsvSource(delimiter = '|', textBlock = """
                    TAG|application/json
                    TAG|text/markdown
                    TAG|*/*
                    W/TAG|application/json
                    W/TAG|text/markdown
                    *|application/json
                    *|text/markdown
                    "sha256:other", TAG|application/json
                    ,TAG|text/markdown
                    """)
    void aMatchingIfNoneMatchIsA304WithTheTagVaryAndNoBody(String ifNoneMatch, String accept) throws Exception {
        mvc.perform(get(PATH).header("Accept", accept).header("If-None-Match", ifNoneMatch.replace("TAG", TAG)))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", TAG))
                .andExpect(header().string("Vary", "Accept"))
                .andExpect(header().exists("Charter-Contract-Version"))
                .andExpect(header().string("Charter-Worktree", "worktree-1"))
                .andExpect(header().doesNotExist("Charter-Source-Fingerprint"))
                .andExpect(content().string(""));
    }

    @ParameterizedTest(name = "If-None-Match: {0} is not a match and answers 200")
    @ValueSource(
            strings = {
                "\"sha256:0000000000000000000000000000000000000000000000000000000000000000\"",
                "garbage",
                "garbage TAG",
                "w/TAG"
            })
    void aValueThatIsNotAnEntityTagListOrNamesAnotherTagMatchesNothing(String ifNoneMatch) throws Exception {
        // "garbage TAG" is the value Spring's own check would take for a match: it scans for any quoted tag.
        mvc.perform(get(PATH)
                        .header("Accept", "text/markdown")
                        .header("If-None-Match", ifNoneMatch.replace("TAG", TAG)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", TAG))
                .andExpect(header().string("Vary", "Accept"))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT));
    }

    @Test
    void theTagIsTheSameForBothForms() throws Exception {
        String markdown = accepting("text/markdown").andReturn().getResponse().getHeader("ETag");
        String json = accepting("application/json").andReturn().getResponse().getHeader("ETag");

        assertThat(markdown).isEqualTo(json).isEqualTo(TAG);
    }

    // ---- the two 404 codes ---------------------------------------------------------------------------------------

    @ParameterizedTest(name = "a pack with no document is pack_document_not_found for Accept: {0}")
    @ValueSource(strings = {"text/markdown", "application/json", "*/*"})
    void aPackWithNoDocumentIsA404WithItsOwnCode(String accept) throws Exception {
        when(query.document("bare"))
                .thenThrow(new CharterRefusal(RefusalCode.PACK_DOCUMENT_NOT_FOUND, "Pack bare holds no document."));

        mvc.perform(get("/api/v1/packs/bare/doc").header("Accept", accept))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("pack_document_not_found"))
                .andExpect(header().exists("Charter-Contract-Version"))
                .andExpect(header().doesNotExist("Charter-Source-Fingerprint"));
    }

    @ParameterizedTest(name = "an unknown pack is pack_not_found for Accept: {0}")
    @ValueSource(strings = {"text/markdown", "application/json", "*/*"})
    void anUnknownPackIsA404WithPackNotFound(String accept) throws Exception {
        when(query.document("nobody"))
                .thenThrow(new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack nobody was not found."));

        mvc.perform(get("/api/v1/packs/nobody/doc").header("Accept", accept))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("pack_not_found"));
    }

    @Test
    void aDocumentTheStoreRefusesIsA500PackUnreadable() throws Exception {
        when(query.document("broken"))
                .thenThrow(new CharterRefusal(RefusalCode.PACK_UNREADABLE, "broken/README.md: carriage return"));

        mvc.perform(get("/api/v1/packs/broken/doc").header("Accept", "text/markdown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("pack_unreadable"));
    }

    @Test
    void theRequestForAnotherMethodIsNotTheDocumentRead() throws Exception {
        MockHttpServletRequestBuilder post =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH);
        mvc.perform(post).andExpect(status().isMethodNotAllowed());
    }
}
