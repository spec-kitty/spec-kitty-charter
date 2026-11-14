package ai.speckitty.charter.artifacts.infra.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.artifacts.application.ContextsInForce;
import ai.speckitty.charter.artifacts.application.ContextsInForce.Vocabulary;
import ai.speckitty.charter.artifacts.application.GlossaryView;
import ai.speckitty.charter.artifacts.application.VocabularyReads;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.web.DeferredETag;
import ai.speckitty.charter.web.RefusalHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The vocabulary reads of an activation (ADR-0076 decisions 4 to 9), over fake ports and the real application ring:
 * the terms with their links and only the members a term has, the Contextive document as {@code application/yaml} text
 * with no charset, the taboo list, the activation's tag and the source fingerprint on each {@code 200}, a {@code 304}
 * that sends the tag alone and reads no pack, a refusal answered by the shared handler, and a {@code 406} for an
 * {@code Accept} that excludes the read's media type. The whole service is the contract scenarios
 * ({@code vocabulary.feature}).
 *
 * <p>The packs: proj (project) holds spec-kitty-core with lane (deprecated, avoids track) and mission (avoids epic);
 * core (built-in) holds qa with flaky-test (avoids epic).
 */
class VocabularyControllerTest {

    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final String CLOSURE = "sha256:" + "c".repeat(64);
    private static final String TAG = "sha256:" + "e".repeat(64);
    private static final String QUOTED_TAG = "\"" + TAG + "\"";
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final PackIdentifier CORE = new PackIdentifier("core");
    private static final Scope SCOPE = new Scope("a-1", REVISION, CLOSURE, FINGERPRINT);
    private static final Held KITTY = new Held("spec-kitty-core", PROJ, PackLevel.PROJECT, "open");
    private static final Held QA = new Held("qa", CORE, PackLevel.BUILT_IN, "open");

    private static final GlossaryView KITTY_COPY = new GlossaryView(
            "spec-kitty-core",
            PROJ,
            "1.0",
            "Spec Kitty core",
            "Canonical terminology.",
            List.of(),
            List.of("kitty-specs/**"),
            List.of(),
            List.of(),
            List.of(
                    new GlossaryView.Term(
                            "mission",
                            "mission",
                            "A unit of planned work.",
                            "active",
                            List.of("Each mission has one spec."),
                            List.of("feature"),
                            List.of("epic"),
                            List.of("term:spec-kitty-core/lane"),
                            "term:spec-kitty-core/mission"),
                    new GlossaryView.Term(
                            "lane",
                            "lane",
                            "The old name of a worktree.",
                            "deprecated",
                            List.of(),
                            List.of(),
                            List.of("track"),
                            List.of(),
                            "term:spec-kitty-core/lane")));
    private static final GlossaryView QA_COPY = new GlossaryView(
            "qa",
            CORE,
            "1.0",
            "Quality assurance",
            "Testing words.",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new GlossaryView.Term(
                    "flaky-test",
                    "flaky test",
                    "A test that passes and fails on the same code.",
                    "active",
                    List.of(),
                    List.of(),
                    List.of("epic"),
                    List.of(),
                    "term:qa/flaky-test")));

    private final ActivationArtifacts activations = mock(ActivationArtifacts.class);
    private final VocabularyReads.WinningContexts packs = mock(VocabularyReads.WinningContexts.class);

    private void inForce(Vocabulary vocabulary) {
        when(activations.contextsInForce(eq("a-1"), eq(vocabulary), isNull()))
                .thenReturn(new Outcome.Found<>(new ContextsInForce(SCOPE, List.of(KITTY, QA)), TAG));
        when(packs.read(any())).thenReturn(Map.of("spec-kitty-core", KITTY_COPY, "qa", QA_COPY));
    }

    private MockMvc mvc() {
        JsonMapper json = JsonMapper.builder()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        return MockMvcBuilders.standaloneSetup(new VocabularyController(
                        new VocabularyReads(activations, packs), new VocabularyBodies("/api/v1/")))
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new RefusalHandler(), new DeferredETag())
                .build();
    }

    @Test
    void theTermsAreAnsweredByContextIdThenTermIdEachLinkedUnderTheActivation() throws Exception {
        inForce(Vocabulary.TERMS);

        mvc().perform(get("/api/v1/activations/a-1/vocabulary/terms").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", QUOTED_TAG))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(content().json("""
                        {
                          "activationId": "a-1",
                          "revision": "%s",
                          "governanceFingerprint": "%s",
                          "terms": [
                            {"urn": "term:qa/flaky-test", "contextId": "qa", "id": "flaky-test",
                             "name": "flaky test", "definition": "A test that passes and fails on the same code.",
                             "status": "active", "avoid": ["epic"], "packId": "core", "level": "built-in",
                             "_links": {
                               "effective-term": {"href": "/api/v1/activations/a-1/artifacts/glossary/qa/terms/flaky-test"},
                               "effective-glossary": {"href": "/api/v1/activations/a-1/artifacts/glossary/qa"}}},
                            {"urn": "term:spec-kitty-core/lane", "contextId": "spec-kitty-core", "id": "lane",
                             "name": "lane", "definition": "The old name of a worktree.", "status": "deprecated",
                             "avoid": ["track"], "packId": "proj", "level": "project",
                             "_links": {
                               "effective-term": {
                                 "href": "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/lane"},
                               "effective-glossary": {
                                 "href": "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core"}}},
                            {"urn": "term:spec-kitty-core/mission", "contextId": "spec-kitty-core", "id": "mission",
                             "name": "mission", "definition": "A unit of planned work.", "status": "active",
                             "examples": ["Each mission has one spec."], "aliases": ["feature"], "avoid": ["epic"],
                             "seeAlso": ["term:spec-kitty-core/lane"], "packId": "proj", "level": "project",
                             "_links": {
                               "effective-term": {
                                 "href": "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/mission"},
                               "effective-glossary": {
                                 "href": "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core"}}}
                          ],
                          "_links": {
                            "self": {"href": "/api/v1/activations/a-1/vocabulary/terms"},
                            "activation": {"href": "/api/v1/activations/a-1"}
                          }
                        }
                        """.formatted(REVISION, CLOSURE), JsonCompareMode.STRICT));
    }

    @Test
    void theContextiveDocumentIsYamlTextWithNoCharsetTheTagAndTheFingerprint() throws Exception {
        inForce(Vocabulary.CONTEXTIVE);

        mvc().perform(get("/api/v1/activations/a-1/vocabulary/contextive"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/yaml"))
                .andExpect(header().string("ETag", QUOTED_TAG))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(content().string("""
                        contexts:
                        - name: Quality assurance
                          domainVisionStatement: Testing words.
                          terms:
                          - name: flaky test
                            definition: A test that passes and fails on the same code.
                            meta:
                              urn: term:qa/flaky-test
                              status: active
                        - name: Spec Kitty core
                          domainVisionStatement: Canonical terminology.
                          paths:
                          - kitty-specs/**
                          terms:
                          - name: lane
                            definition: The old name of a worktree.
                            meta:
                              urn: term:spec-kitty-core/lane
                              status: deprecated
                          - name: mission
                            definition: A unit of planned work.
                            examples:
                            - Each mission has one spec.
                            aliases:
                            - feature
                            meta:
                              urn: term:spec-kitty-core/mission
                              status: active
                        """));
    }

    @Test
    void theTabooListHasOneEntryPerAvoidedWordPerTermByWordThenUrn() throws Exception {
        inForce(Vocabulary.TABOOS);

        mvc().perform(get("/api/v1/activations/a-1/vocabulary/taboos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", QUOTED_TAG))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(content().json("""
                        {
                          "activationId": "a-1",
                          "revision": "%s",
                          "governanceFingerprint": "%s",
                          "entries": [
                            {"word": "epic", "urn": "term:qa/flaky-test", "termName": "flaky test"},
                            {"word": "epic", "urn": "term:spec-kitty-core/mission", "termName": "mission"},
                            {"word": "track", "urn": "term:spec-kitty-core/lane", "termName": "lane"}
                          ],
                          "_links": {
                            "self": {"href": "/api/v1/activations/a-1/vocabulary/taboos"},
                            "activation": {"href": "/api/v1/activations/a-1"}
                          }
                        }
                        """.formatted(REVISION, CLOSURE), JsonCompareMode.STRICT));
    }

    @Test
    void aMatchingIfNoneMatchIs304WithTheTagAloneAndReadsNoPack() throws Exception {
        for (Vocabulary vocabulary : Vocabulary.values()) {
            when(activations.contextsInForce("a-1", vocabulary, QUOTED_TAG)).thenReturn(new Outcome.NotModified<>(TAG));
        }
        MockMvc mvc = mvc();
        for (String leaf : new String[] {"terms", "contextive", "taboos"}) {
            mvc.perform(get("/api/v1/activations/a-1/vocabulary/" + leaf).header("If-None-Match", QUOTED_TAG))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", QUOTED_TAG))
                    .andExpect(header().doesNotExist("Charter-Source-Fingerprint"))
                    .andExpect(header().doesNotExist("Content-Type"))
                    .andExpect(content().string(""));
        }
        verifyNoInteractions(packs);
    }

    @Test
    void aRefusalOfTheActivationIsAnsweredByTheSharedHandler() throws Exception {
        CharterRefusal refusal = new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "no activation nope");
        for (Vocabulary vocabulary : Vocabulary.values()) {
            when(activations.contextsInForce("nope", vocabulary, null)).thenThrow(refusal);
        }
        MockMvc mvc = mvc();
        for (String leaf : new String[] {"terms", "contextive", "taboos"}) {
            mvc.perform(get("/api/v1/activations/nope/vocabulary/" + leaf))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("activation_not_found"));
        }
        verifyNoInteractions(packs);
    }

    @Test
    void anAcceptThatExcludesTheReadsMediaTypeIs406AndReadsNothing() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(get("/api/v1/activations/a-1/vocabulary/contextive").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable());
        mvc.perform(get("/api/v1/activations/a-1/vocabulary/terms")
                        .accept(MediaType.parseMediaType("application/yaml")))
                .andExpect(status().isNotAcceptable());
        verifyNoInteractions(activations, packs);
    }
}
