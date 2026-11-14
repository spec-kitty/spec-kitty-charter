package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.artifacts.application.GlossaryReads;
import ai.speckitty.charter.artifacts.application.GlossaryView;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.web.DeferredETag;
import ai.speckitty.charter.web.RefusalHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The glossary reads' controller over a fake port: the three answers, their headers and links, the refusals the shared
 * handler answers, and that Spring answers no {@code 304} the contract does not declare. The routing, the parameter
 * grammar and the whole service are the contract scenarios ({@code glossary.feature}).
 */
class GlossariesControllerTest {

    private static final String FINGERPRINT = "sha256:" + "b".repeat(64);
    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final String ETAG = "\"" + REVISION + "\"";
    private static final PackIdentifier QA = new PackIdentifier("qa");

    private static final GlossaryView.Term MISSION = new GlossaryView.Term(
            "mission",
            "mission",
            "A unit of planned work.",
            "active",
            List.of(),
            List.of("feature"),
            List.of(),
            List.of(),
            "term:core/mission");
    private static final GlossaryView CORE = new GlossaryView(
            "core",
            QA,
            "1.0",
            "Core",
            "The core vocabulary.",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(MISSION));

    private final List<String> asked = new ArrayList<>();

    private static MockMvc mvc(GlossaryReads reads) {
        JsonMapper json = JsonMapper.builder()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        return MockMvcBuilders.standaloneSetup(new GlossariesController(reads, new GlossaryBodies("/api/v1")))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new RefusalHandler(), new DeferredETag())
                .build();
    }

    private GlossaryReads reads() {
        return new GlossaryReads() {
            @Override
            public Listed list(PackIdentifier pack) {
                asked.add("list " + pack.value());
                return new Listed(List.of(CORE), REVISION, FINGERPRINT);
            }

            @Override
            public Read context(PackIdentifier pack, String contextId) {
                asked.add("context " + pack.value() + "/" + contextId);
                return new Read(CORE, REVISION, FINGERPRINT);
            }

            @Override
            public TermRead term(PackIdentifier pack, String contextId, String termId) {
                asked.add("term " + pack.value() + "/" + contextId + "/" + termId);
                return new TermRead(MISSION, contextId, pack, REVISION, FINGERPRINT);
            }
        };
    }

    @Test
    void theListAnswersEachContextWithItsTermCountItsSelfLinkAndTheListsOwn() throws Exception {
        mvc(reads())
                .perform(get("/api/v1/packs/qa/artifacts/glossary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(content().json("""
                        {
                          "packId": "qa",
                          "items": [
                            {"id": "core", "name": "Core", "description": "The core vocabulary.", "termCount": 1,
                             "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/glossary/core"}}}
                          ],
                          "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/glossary"}}
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("list qa");
    }

    @Test
    void aContextIsTheEnvelopeWithEveryTermAndItsTemplatedTermLink() throws Exception {
        mvc(reads())
                .perform(get("/api/v1/packs/qa/artifacts/glossary/core").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(content().json("""
                        {
                          "kind": "glossary",
                          "id": "core",
                          "packId": "qa",
                          "_links": {
                            "self": {"href": "/api/v1/packs/qa/artifacts/glossary/core"},
                            "collection": {"href": "/api/v1/packs/qa/artifacts/glossary"},
                            "term": {"href": "/api/v1/packs/qa/artifacts/glossary/core/terms/{termId}", "templated": true}
                          },
                          "glossary": {
                            "schemaVersion": "1.0",
                            "id": "core",
                            "name": "Core",
                            "description": "The core vocabulary.",
                            "terms": [
                              {"id": "mission", "name": "mission", "definition": "A unit of planned work.",
                               "status": "active", "aliases": ["feature"]}
                            ]
                          }
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("context qa/core");
    }

    @Test
    void aTermIsAnsweredWithItsUrnItsContextAndItsLinks() throws Exception {
        mvc(reads())
                .perform(get("/api/v1/packs/qa/artifacts/glossary/core/terms/mission")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(content().json("""
                        {
                          "id": "mission",
                          "name": "mission",
                          "definition": "A unit of planned work.",
                          "status": "active",
                          "aliases": ["feature"],
                          "urn": "term:core/mission",
                          "contextId": "core",
                          "_links": {
                            "self": {"href": "/api/v1/packs/qa/artifacts/glossary/core/terms/mission"},
                            "up": {"href": "/api/v1/packs/qa/artifacts/glossary/core"}
                          }
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("term qa/core/mission");
    }

    @Test
    void theReadsTakeNoIfNoneMatchSoAMatchingTagIsStillA200() throws Exception {
        MockMvc mvc = mvc(reads());
        for (String path : List.of(
                "/api/v1/packs/qa/artifacts/glossary",
                "/api/v1/packs/qa/artifacts/glossary/core",
                "/api/v1/packs/qa/artifacts/glossary/core/terms/mission")) {
            mvc.perform(get(path).header("If-None-Match", ETAG))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", ETAG));
        }
    }

    @Test
    void aRefusalOfTheReadIsAnsweredByTheSharedHandlerWithItsDetail() throws Exception {
        GlossaryReads refusing = new GlossaryReads() {
            @Override
            public Listed list(PackIdentifier pack) {
                throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack nope was not found.");
            }

            @Override
            public Read context(PackIdentifier pack, String contextId) {
                throw new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, "glossary context `nothing` is not held");
            }

            @Override
            public TermRead term(PackIdentifier pack, String contextId, String termId) {
                throw new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, GlossaryReads.termNotHeld(contextId, termId));
            }
        };
        MockMvc mvc = mvc(refusing);

        mvc.perform(get("/api/v1/packs/nope/artifacts/glossary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("pack_not_found"));
        mvc.perform(get("/api/v1/packs/qa/artifacts/glossary/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("artifact_not_found"))
                .andExpect(jsonPath("$.detail").value("glossary context `nothing` is not held"));
        mvc.perform(get("/api/v1/packs/qa/artifacts/glossary/core/terms/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("artifact_not_found"))
                .andExpect(jsonPath("$.detail").value("term `nothing` is not held in glossary context `core`"));
    }
}
