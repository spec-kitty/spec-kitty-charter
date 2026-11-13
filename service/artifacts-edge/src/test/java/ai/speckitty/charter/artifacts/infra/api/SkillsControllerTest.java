package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.artifacts.application.SkillReads;
import ai.speckitty.charter.artifacts.application.SkillView;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.web.DeferredETag;
import ai.speckitty.charter.web.RefusalHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The skill reads' controller over a fake port: the two answers, their headers and links, a prompt skill's body and a
 * wrapper skill's absent one, and that Spring answers no {@code 304} the contract does not declare. The routing and the
 * whole service are the contract scenarios ({@code skills.feature}).
 */
class SkillsControllerTest {

    private static final String FINGERPRINT = "sha256:" + "b".repeat(64);
    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final String ETAG = "\"" + REVISION + "\"";
    private static final PackIdentifier QA = new PackIdentifier("qa");

    private static final SkillView LAND = new SkillView(
            "land-pr",
            QA,
            "1.0",
            "Land a PR",
            "Lands a pull request.",
            "prompt",
            List.of("land the pr"),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            List.of(),
            Optional.empty(),
            List.of(),
            Optional.of("Land it — now.\n"));
    private static final SkillView STATUS = new SkillView(
            "pr-status",
            QA,
            "1.0",
            "Show the PR status",
            "Lists the open pull requests.",
            "wrapper",
            List.of(),
            List.of(),
            Optional.empty(),
            Optional.of(new SkillView.Expansion("builtin:status", Optional.empty())),
            List.of(),
            Optional.empty(),
            List.of(),
            Optional.empty());

    private final List<String> asked = new ArrayList<>();

    private static MockMvc mvc(SkillReads reads) {
        JsonMapper json = JsonMapper.builder()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        return MockMvcBuilders.standaloneSetup(new SkillsController(reads, new SkillBodies("/api/v1")))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new RefusalHandler(), new DeferredETag())
                .build();
    }

    private SkillReads reads() {
        return new SkillReads() {
            @Override
            public Listed list(PackIdentifier pack, String expectedFingerprint) {
                throw new UnsupportedOperationException("the pack route reads against no fingerprint");
            }

            @Override
            public SkillView view(CharterArtifact skill, String read) {
                throw new UnsupportedOperationException("the pack route maps no kernel artifact");
            }

            @Override
            public Listed list(PackIdentifier pack) {
                asked.add("list " + pack.value());
                return new Listed(List.of(LAND, STATUS), REVISION, FINGERPRINT);
            }

            @Override
            public Read skill(PackIdentifier pack, String skillId) {
                asked.add("skill " + pack.value() + "/" + skillId);
                return new Read(skillId.equals(LAND.id()) ? LAND : STATUS, REVISION, FINGERPRINT);
            }
        };
    }

    @Test
    void theListAnswersEachSkillsIdTitleDescriptionAndFormWithItsSelfLinkAndTheListsOwn() throws Exception {
        mvc(reads())
                .perform(get("/api/v1/packs/qa/artifacts/skill").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(content().json("""
                        {
                          "items": [
                            {"id": "land-pr", "title": "Land a PR", "description": "Lands a pull request.",
                             "form": "prompt",
                             "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/skill/land-pr"}}},
                            {"id": "pr-status", "title": "Show the PR status",
                             "description": "Lists the open pull requests.", "form": "wrapper",
                             "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/skill/pr-status"}}}
                          ],
                          "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/skill"}}
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("list qa");
    }

    @Test
    void aPromptSkillIsTheEnvelopeWithItsBodyAndLinksItsCollection() throws Exception {
        mvc(reads())
                .perform(get("/api/v1/packs/qa/artifacts/skill/land-pr").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(content().json("""
                        {
                          "kind": "skill",
                          "id": "land-pr",
                          "packId": "qa",
                          "_links": {
                            "self": {"href": "/api/v1/packs/qa/artifacts/skill/land-pr"},
                            "collection": {"href": "/api/v1/packs/qa/artifacts/skill"}
                          },
                          "skill": {
                            "schemaVersion": "1.0",
                            "id": "land-pr",
                            "title": "Land a PR",
                            "description": "Lands a pull request.",
                            "form": "prompt",
                            "triggers": ["land the pr"],
                            "parameters": [],
                            "tools": [],
                            "body": "Land it — now.\\n"
                          }
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("skill qa/land-pr");
    }

    @Test
    void aWrapperSkillHasNoBody() throws Exception {
        mvc(reads())
                .perform(get("/api/v1/packs/qa/artifacts/skill/pr-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skill.expandsTo.target").value("builtin:status"))
                .andExpect(jsonPath("$.skill.body").doesNotExist());
    }

    @Test
    void theReadsTakeNoIfNoneMatchSoAMatchingTagIsStillA200() throws Exception {
        MockMvc mvc = mvc(reads());
        for (String path : List.of("/api/v1/packs/qa/artifacts/skill", "/api/v1/packs/qa/artifacts/skill/land-pr")) {
            mvc.perform(get(path).header("If-None-Match", ETAG))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", ETAG));
        }
    }

    @Test
    void aRefusalOfTheReadIsAnsweredByTheSharedHandler() throws Exception {
        SkillReads refusing = new SkillReads() {
            @Override
            public Listed list(PackIdentifier pack, String expectedFingerprint) {
                throw new UnsupportedOperationException("the pack route reads against no fingerprint");
            }

            @Override
            public SkillView view(CharterArtifact skill, String read) {
                throw new UnsupportedOperationException("the pack route maps no kernel artifact");
            }

            @Override
            public Listed list(PackIdentifier pack) {
                throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack nope was not found.");
            }

            @Override
            public Read skill(PackIdentifier pack, String skillId) {
                throw new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, "Pack qa has no skill nothing.");
            }
        };
        MockMvc mvc = mvc(refusing);

        mvc.perform(get("/api/v1/packs/nope/artifacts/skill"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("pack_not_found"));
        mvc.perform(get("/api/v1/packs/qa/artifacts/skill/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("artifact_not_found"));
    }
}
