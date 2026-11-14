package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Facts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Words;
import ai.speckitty.charter.artifacts.application.ContextsInForce;
import ai.speckitty.charter.artifacts.application.EffectiveGlossaryReads;
import ai.speckitty.charter.artifacts.application.GlossaryReads;
import ai.speckitty.charter.artifacts.application.GlossaryView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.web.DeferredETag;
import ai.speckitty.charter.web.RefusalHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The glossary reads under an activation, over fake ports and the real application ring: the list of the winning copies
 * with no pack named, one context whatever its state with the facts the activation gives it and every term, one term
 * with its context's facts, the optional facts only when they are there, the activation's tag and the source
 * fingerprint, a {@code 304} that sends the tag alone, and a refusal answered by the shared handler. The routing and the
 * whole service are the contract scenarios ({@code effective-glossary.feature}).
 */
class EffectiveGlossariesControllerTest {

    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final String CLOSURE = "sha256:" + "c".repeat(64);
    private static final String TAG = "sha256:" + "e".repeat(64);
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final Scope SCOPE = new Scope("a-1", REVISION, CLOSURE, FINGERPRINT);
    private static final Held KITTY = new Held("spec-kitty-core", PROJ, PackLevel.PROJECT, "open");
    private static final GlossaryView.Term MISSION = new GlossaryView.Term(
            "mission",
            "mission",
            "A unit of planned work.",
            "active",
            List.of(),
            List.of("feature"),
            List.of(),
            List.of("built-in::term:spec-kitty-core/work-package"),
            "term:spec-kitty-core/mission");
    private static final GlossaryView CONTEXT = new GlossaryView(
            "spec-kitty-core",
            PROJ,
            "1.0",
            "Spec Kitty core",
            "Canonical terminology.",
            List.of("spec-kitty"),
            List.of(),
            List.of(),
            List.of(),
            List.of(MISSION));

    private final List<String> asked = new ArrayList<>();
    private @Nullable CharterRefusal refusal;

    private ActivationArtifacts activation(Facts facts) {
        return new ActivationArtifacts() {
            @Override
            public Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch) {
                return outcome(ifNoneMatch, new Winners(SCOPE, List.of(KITTY)));
            }

            @Override
            public Outcome<GlossaryWinner> glossary(
                    String activationId, String contextId, @Nullable String ifNoneMatch) {
                return outcome(ifNoneMatch, new GlossaryWinner(SCOPE, facts, entity(contextId)));
            }

            @Override
            public Outcome<ContextsInForce> contextsInForce(
                    String activationId, ContextsInForce.Vocabulary vocabulary, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no vocabulary is read here");
            }

            @Override
            public Outcome<GlossaryWinner> glossaryTerm(
                    String activationId, String contextId, String termId, @Nullable String ifNoneMatch) {
                return outcome(ifNoneMatch, new GlossaryWinner(SCOPE, facts, entity(contextId)));
            }

            @Override
            public Outcome<Winner> asset(String activationId, String assetId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no asset is read here");
            }

            @Override
            public Outcome<Winner> assetPayload(String activationId, String assetId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no asset is read here");
            }

            @Override
            public Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no skill is read here");
            }

            @Override
            public Referrer referrer(String activationId, String kindToken, String artifactId) {
                throw new UnsupportedOperationException("no reference is read here");
            }

            private <T> Outcome<T> outcome(@Nullable String ifNoneMatch, T value) {
                if (refusal != null) {
                    throw refusal;
                }
                return EntityTags.noneMatch(ifNoneMatch, TAG)
                        ? new Outcome.NotModified<>(TAG)
                        : new Outcome.Found<>(value, TAG);
            }
        };
    }

    private GlossaryReads packs() {
        return new GlossaryReads() {
            @Override
            public Listed list(PackIdentifier pack) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Listed list(PackIdentifier pack, String expectedFingerprint) {
                asked.add("list " + pack.value());
                return new Listed(List.of(CONTEXT), "sha256:revision", expectedFingerprint);
            }

            @Override
            public GlossaryView view(CharterArtifact context, String read) {
                asked.add("view " + context.localId());
                return CONTEXT;
            }

            @Override
            public Read context(PackIdentifier pack, String contextId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TermRead term(PackIdentifier pack, String contextId, String termId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private MockMvc mvc(Facts facts) {
        JsonMapper json = JsonMapper.builder()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        EffectiveGlossaryBodies bodies = new EffectiveGlossaryBodies("/api/v1/", new GlossaryBodies("/api/v1"));
        return MockMvcBuilders.standaloneSetup(new EffectiveGlossariesController(
                        new EffectiveGlossaryReads(activation(facts), packs()), bodies))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new RefusalHandler(), new DeferredETag())
                .build();
    }

    private static Facts excluded() {
        return new Facts(KITTY, "excluded", false, "deactivated", List.of(), Optional.empty());
    }

    @Test
    void theListAnswersEachWinningCopyLinkedUnderTheActivationAndInItsPackAndNamesNoPack() throws Exception {
        mvc(excluded())
                .perform(get("/api/v1/activations/a-1/artifacts/glossary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + TAG + "\""))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(content().json("""
                        {
                          "items": [
                            {"id": "spec-kitty-core", "name": "Spec Kitty core",
                             "description": "Canonical terminology.", "tags": ["spec-kitty"], "termCount": 1,
                             "_links": {
                               "effective-artifact": {
                                 "href": "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core"},
                               "artifact": {"href": "/api/v1/packs/proj/artifacts/glossary/spec-kitty-core"}}}
                          ],
                          "_links": {
                            "self": {"href": "/api/v1/activations/a-1/artifacts/glossary"},
                            "up": {"href": "/api/v1/activations/a-1"}
                          }
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("list proj");
    }

    @Test
    void aContextTheActivationDeactivatesIsAnsweredWithItsStateEveryTermAndItsLinks() throws Exception {
        mvc(excluded())
                .perform(get("/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + TAG + "\""))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(content().json("""
                        {
                          "activationId": "a-1",
                          "revision": "%s",
                          "governanceFingerprint": "%s",
                          "kind": "glossary",
                          "artifactId": "spec-kitty-core",
                          "packId": "proj",
                          "level": "project",
                          "state": "excluded",
                          "required": false,
                          "policy": "open",
                          "reason": "deactivated",
                          "glossary": {
                            "schemaVersion": "1.0",
                            "id": "spec-kitty-core",
                            "name": "Spec Kitty core",
                            "description": "Canonical terminology.",
                            "tags": ["spec-kitty"],
                            "terms": [
                              {"id": "mission", "name": "mission", "definition": "A unit of planned work.",
                               "status": "active", "aliases": ["feature"],
                               "seeAlso": ["built-in::term:spec-kitty-core/work-package"]}
                            ]
                          },
                          "_links": {
                            "self": {"href": "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core"},
                            "artifact": {"href": "/api/v1/packs/proj/artifacts/glossary/spec-kitty-core"},
                            "collection": {"href": "/api/v1/activations/a-1/artifacts/glossary"},
                            "term": {
                              "href": "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/{termId}",
                              "templated": true},
                            "up": {"href": "/api/v1/activations/a-1"}
                          }
                        }
                        """.formatted(REVISION, CLOSURE), JsonCompareMode.STRICT));
        assertThat(asked)
                .as("the item maps the activation's read and reads no pack")
                .containsExactly("view spec-kitty-core");
    }

    @Test
    void aTermIsAnsweredWithItsContextsFactsAndLinksItselfItsPackReadAndItsContext() throws Exception {
        mvc(excluded())
                .perform(get("/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/mission")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + TAG + "\""))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(content().json("""
                        {
                          "activationId": "a-1",
                          "revision": "%s",
                          "governanceFingerprint": "%s",
                          "kind": "glossary",
                          "artifactId": "spec-kitty-core",
                          "packId": "proj",
                          "level": "project",
                          "state": "excluded",
                          "required": false,
                          "policy": "open",
                          "reason": "deactivated",
                          "term": {
                            "id": "mission", "name": "mission", "definition": "A unit of planned work.",
                            "status": "active", "aliases": ["feature"],
                            "seeAlso": ["built-in::term:spec-kitty-core/work-package"]
                          },
                          "_links": {
                            "self": {
                              "href": "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/mission"},
                            "artifact": {"href": "/api/v1/packs/proj/artifacts/glossary/spec-kitty-core/terms/mission"},
                            "up": {"href": "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core"}
                          }
                        }
                        """.formatted(REVISION, CLOSURE), JsonCompareMode.STRICT));
    }

    @Test
    void aSatisfiedOrSuggestedContextAndItsTermsCarryThoseFacts() throws Exception {
        Facts facts = new Facts(
                KITTY,
                "inactive",
                true,
                "overridden",
                List.of(new Urn(NodeKind.GLOSSARY_CONTEXT, "kitty-words")),
                Optional.of(new Words(Optional.of("when"), Optional.empty())));
        MockMvc mvc = mvc(facts);
        for (String path : new String[] {
            "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core",
            "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/mission"
        }) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"required": true, "reason": "overridden",
                             "satisfiedBy": [{"kind": "glossary", "artifactId": "kitty-words"}],
                             "suggestion": {"when": "when"}}
                            """, JsonCompareMode.LENIENT));
        }
    }

    @Test
    void aMatchingIfNoneMatchIs304WithTheTagAlone() throws Exception {
        MockMvc mvc = mvc(excluded());
        for (String path : new String[] {
            "/api/v1/activations/a-1/artifacts/glossary",
            "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core",
            "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/mission"
        }) {
            mvc.perform(get(path).header("If-None-Match", "\"" + TAG + "\""))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", "\"" + TAG + "\""))
                    .andExpect(header().doesNotExist("Charter-Source-Fingerprint"));
        }
        assertThat(asked).isEmpty();
    }

    @Test
    void aRefusalOfTheActivationIsAnsweredByTheSharedHandlerWithItsDetail() throws Exception {
        refusal = new CharterRefusal(
                RefusalCode.ARTIFACT_NOT_FOUND, "term `lane` is not held in glossary context `spec-kitty-core`");

        mvc(excluded())
                .perform(get("/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/lane"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("artifact_not_found"))
                .andExpect(jsonPath("$.detail").value("term `lane` is not held in glossary context `spec-kitty-core`"));
    }

    private static CharterArtifact entity(String id) {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return ArtifactKind.GLOSSARY;
            }

            @Override
            public PackIdentifier packId() {
                return PROJ;
            }

            @Override
            public String localId() {
                return id;
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.of("1.0");
            }

            @Override
            public String label() {
                return id;
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
    }
}
