package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Facts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Words;
import ai.speckitty.charter.artifacts.application.EffectiveSkillReads;
import ai.speckitty.charter.artifacts.application.SkillReads;
import ai.speckitty.charter.artifacts.application.SkillView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
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
 * The skill reads under an activation, over fake ports and the real application ring: the list of the winning copies
 * and one skill whatever its state, with the facts the activation gives it, its optional facts only when they are
 * there, the skill as its pack declares it, the activation's tag and the source fingerprint, and a {@code 304} that
 * sends the tag alone. The routing and the whole service are the contract scenarios
 * ({@code effective-assets-and-skills.feature}).
 */
class EffectiveSkillsControllerTest {

    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final String CLOSURE = "sha256:" + "c".repeat(64);
    private static final String TAG = "sha256:" + "e".repeat(64);
    private static final PackIdentifier CORE = new PackIdentifier("core");
    private static final Scope SCOPE = new Scope("a-1", REVISION, CLOSURE, FINGERPRINT);
    private static final Held LAND = new Held("land-pr", CORE, PackLevel.BUILT_IN, "open");

    private final List<String> asked = new ArrayList<>();

    private static SkillView view(String id) {
        return new SkillView(
                id,
                CORE,
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
                Optional.of("Land it.\n"));
    }

    private ActivationArtifacts activation(Facts facts) {
        return new ActivationArtifacts() {
            @Override
            public Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch) {
                return outcome(ifNoneMatch, new Winners(SCOPE, List.of(LAND)));
            }

            @Override
            public Outcome<Winner> asset(String activationId, String assetId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Outcome<Winner> assetPayload(String activationId, String assetId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Referrer referrer(String activationId, String kindToken, String artifactId) {

                throw new UnsupportedOperationException();
            }

            @Override
            public Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
                return outcome(ifNoneMatch, new SkillWinner(SCOPE, facts, entity(skillId)));
            }

            private <T> Outcome<T> outcome(@Nullable String ifNoneMatch, T value) {
                return EntityTags.noneMatch(ifNoneMatch, TAG)
                        ? new Outcome.NotModified<>(TAG)
                        : new Outcome.Found<>(value, TAG);
            }
        };
    }

    private SkillReads packs() {
        return new SkillReads() {
            @Override
            public Listed list(PackIdentifier pack) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Read skill(PackIdentifier pack, String skillId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Listed list(PackIdentifier pack, String expectedFingerprint) {
                asked.add("list " + pack.value());
                return new Listed(
                        List.of(EffectiveSkillsControllerTest.view("land-pr")), "sha256:revision", expectedFingerprint);
            }

            @Override
            public SkillView view(CharterArtifact skill, String read) {
                asked.add("view " + skill.localId());
                return EffectiveSkillsControllerTest.view(skill.localId());
            }
        };
    }

    private MockMvc mvc(Facts facts) {
        JsonMapper json = JsonMapper.builder()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        EffectiveBodies bodies = new EffectiveBodies("/api/v1", new AssetBodies("/api/v1"), new SkillBodies("/api/v1"));
        return MockMvcBuilders.standaloneSetup(
                        new EffectiveSkillsController(new EffectiveSkillReads(activation(facts), packs()), bodies))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new RefusalHandler(), new DeferredETag())
                .build();
    }

    private static Facts inactive() {
        return new Facts(LAND, "inactive", false, "not_selected", Optional.empty(), Optional.empty());
    }

    @Test
    void theListAnswersEachWinningCopyLinkedUnderTheActivationAndInItsPack() throws Exception {
        mvc(inactive())
                .perform(get("/api/v1/activations/a-1/artifacts/skill").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + TAG + "\""))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(content().json("""
                        {
                          "items": [
                            {"id": "land-pr", "title": "Land a PR", "description": "Lands a pull request.",
                             "form": "prompt", "_links": {
                               "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/skill/land-pr"},
                               "artifact": {"href": "/api/v1/packs/core/artifacts/skill/land-pr"}}}
                          ],
                          "_links": {
                            "self": {"href": "/api/v1/activations/a-1/artifacts/skill"},
                            "up": {"href": "/api/v1/activations/a-1"}
                          }
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("list core");
    }

    @Test
    void aSkillTheActivationDoesNotSelectIsAnsweredWithItsStateAndTheSkill() throws Exception {
        mvc(inactive())
                .perform(get("/api/v1/activations/a-1/artifacts/skill/land-pr").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + TAG + "\""))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(content().json("""
                        {
                          "activationId": "a-1",
                          "revision": "%s",
                          "governanceFingerprint": "%s",
                          "kind": "skill",
                          "artifactId": "land-pr",
                          "packId": "core",
                          "level": "built-in",
                          "state": "inactive",
                          "required": false,
                          "policy": "open",
                          "reason": "not_selected",
                          "skill": {
                            "schemaVersion": "1.0",
                            "id": "land-pr",
                            "title": "Land a PR",
                            "description": "Lands a pull request.",
                            "form": "prompt",
                            "triggers": ["land the pr"],
                            "parameters": [],
                            "tools": [],
                            "body": "Land it.\\n"
                          },
                          "_links": {
                            "self": {"href": "/api/v1/activations/a-1/artifacts/skill/land-pr"},
                            "artifact": {"href": "/api/v1/packs/core/artifacts/skill/land-pr"},
                            "collection": {"href": "/api/v1/activations/a-1/artifacts/skill"},
                            "up": {"href": "/api/v1/activations/a-1"}
                          }
                        }
                        """.formatted(REVISION, CLOSURE), JsonCompareMode.STRICT));
        assertThat(asked)
                .as("the item maps the activation's read and reads no pack")
                .containsExactly("view land-pr");
    }

    @Test
    void aSatisfiedOrSuggestedSkillCarriesThoseFacts() throws Exception {
        Facts facts = new Facts(
                LAND,
                "inactive",
                true,
                "overridden",
                Optional.of(new Urn(NodeKind.SKILL, "land-fast")),
                Optional.of(new Words(Optional.of("when"), Optional.empty())));

        mvc(facts)
                .perform(get("/api/v1/activations/a-1/artifacts/skill/land-pr"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"required": true, "reason": "overridden",
                         "satisfiedBy": {"kind": "skill", "artifactId": "land-fast"},
                         "suggestion": {"when": "when"}}
                        """, JsonCompareMode.LENIENT));
    }

    @Test
    void aMatchingIfNoneMatchIs304WithTheTagAlone() throws Exception {
        MockMvc mvc = mvc(inactive());
        for (String path : new String[] {
            "/api/v1/activations/a-1/artifacts/skill", "/api/v1/activations/a-1/artifacts/skill/land-pr"
        }) {
            mvc.perform(get(path).header("If-None-Match", "\"" + TAG + "\""))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", "\"" + TAG + "\""))
                    .andExpect(header().doesNotExist("Charter-Source-Fingerprint"));
        }
        assertThat(asked).isEmpty();
    }

    private static CharterArtifact entity(String id) {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return ArtifactKind.SKILL;
            }

            @Override
            public PackIdentifier packId() {
                return CORE;
            }

            @Override
            public String localId() {
                return id;
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
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
