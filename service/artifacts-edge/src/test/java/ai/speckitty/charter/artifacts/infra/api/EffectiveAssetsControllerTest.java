package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.artifacts.application.AssetReads;
import ai.speckitty.charter.artifacts.application.AssetView;
import ai.speckitty.charter.artifacts.application.EffectiveAssetReads;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
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
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The asset reads under an activation, over fake ports and the real application ring: the three answers, their links,
 * the payload's bytes and headers, that every answer carries the activation's tag (never the pack revision) and the
 * source fingerprint, that the application ring's {@code 304} is answered with the tag alone and reads no pack, and that
 * a refusal is the shared handler's. The routing and the whole service are the contract scenarios
 * ({@code effective-assets-and-skills.feature}).
 */
class EffectiveAssetsControllerTest {

    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final String CLOSURE = "sha256:" + "c".repeat(64);
    private static final String TAG = "sha256:" + "e".repeat(64);
    private static final String PACK_REVISION = "sha256:" + "9".repeat(64);
    private static final PackIdentifier CORE = new PackIdentifier("core");
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final Scope SCOPE = new Scope("a-1", REVISION, CLOSURE, FINGERPRINT);
    private static final Held RUN = new Held("run", PROJ, PackLevel.PROJECT, "extendable");
    private static final Held LOGO = new Held("logo", CORE, PackLevel.BUILT_IN, "open");
    private static final byte[] SCRIPT = "print('proj')\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private final List<String> asked = new ArrayList<>();

    private static AssetView view(String id, PackIdentifier pack) {
        return id.equals("run")
                ? new AssetView(
                        "run",
                        pack,
                        "text/x-python",
                        true,
                        "run.py",
                        Optional.of("Run, project copy"),
                        Optional.of(new AssetView.Executable("python", Optional.empty(), "run.py", List.of())))
                : new AssetView("logo", pack, "image/png", true, "logo.png", Optional.empty(), Optional.empty());
    }

    private ActivationArtifacts activation(@Nullable CharterRefusal refusal) {
        return new ActivationArtifacts() {
            @Override
            public Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch) {
                return outcome(ifNoneMatch, new Winners(SCOPE, List.of(LOGO, RUN)));
            }

            @Override
            public Outcome<Winner> asset(String activationId, String assetId, @Nullable String ifNoneMatch) {
                return outcome(ifNoneMatch, new Winner(SCOPE, assetId.equals("run") ? RUN : LOGO));
            }

            @Override
            public Outcome<Winner> assetPayload(String activationId, String assetId, @Nullable String ifNoneMatch) {
                return asset(activationId, assetId, ifNoneMatch);
            }

            @Override
            public Referrer referrer(String activationId, String kindToken, String artifactId) {

                throw new UnsupportedOperationException();
            }

            @Override
            public Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no skill is read here");
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

    private AssetReads packs() {
        return new AssetReads() {
            @Override
            public Listed list(PackIdentifier pack) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Read asset(PackIdentifier pack, String assetId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public PayloadRead payload(PackIdentifier pack, String assetId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Listed list(PackIdentifier pack, String expectedFingerprint) {
                asked.add("list " + pack.value());
                return new Listed(List.of(view("logo", pack), view("run", pack)), PACK_REVISION, expectedFingerprint);
            }

            @Override
            public Read asset(PackIdentifier pack, String assetId, String expectedFingerprint) {
                asked.add("asset " + pack.value() + "/" + assetId);
                return new Read(view(assetId, pack), PACK_REVISION, expectedFingerprint);
            }

            @Override
            public PayloadRead payload(PackIdentifier pack, String assetId, String expectedFingerprint) {
                asked.add("payload " + pack.value() + "/" + assetId);
                return new PayloadRead(view(assetId, pack), SCRIPT, PACK_REVISION, expectedFingerprint);
            }
        };
    }

    private MockMvc mvc(@Nullable CharterRefusal refusal) {
        JsonMapper json = JsonMapper.builder()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        AssetBodies assets = new AssetBodies("/api/v1");
        EffectiveBodies bodies = new EffectiveBodies("/api/v1", assets, new SkillBodies("/api/v1"));
        return MockMvcBuilders.standaloneSetup(
                        new EffectiveAssetsController(new EffectiveAssetReads(activation(refusal), packs()), bodies))
                .setMessageConverters(new ResourceHttpMessageConverter(), new JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new RefusalHandler(), new DeferredETag())
                .build();
    }

    @Test
    void theListAnswersEachWinningCopyLinkedUnderTheActivationAndInItsPack() throws Exception {
        mvc(null)
                .perform(get("/api/v1/activations/a-1/artifacts/asset").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + TAG + "\""))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(content().json("""
                        {
                          "items": [
                            {"id": "logo", "mime": "image/png", "executable": false, "_links": {
                              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/asset/logo"},
                              "artifact": {"href": "/api/v1/packs/core/artifacts/asset/logo"}}},
                            {"id": "run", "mime": "text/x-python", "executable": true, "_links": {
                              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/asset/run"},
                              "artifact": {"href": "/api/v1/packs/proj/artifacts/asset/run"}}}
                          ],
                          "_links": {
                            "self": {"href": "/api/v1/activations/a-1/artifacts/asset"},
                            "up": {"href": "/api/v1/activations/a-1"}
                          }
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactlyInAnyOrder("list core", "list proj");
    }

    @Test
    void theItemAnswersTheWinnerWithItsPolicyAndDescriptorAndNoState() throws Exception {
        mvc(null)
                .perform(get("/api/v1/activations/a-1/artifacts/asset/run").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + TAG + "\""))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(content().json("""
                        {
                          "activationId": "a-1",
                          "revision": "%s",
                          "governanceFingerprint": "%s",
                          "kind": "asset",
                          "artifactId": "run",
                          "packId": "proj",
                          "level": "project",
                          "policy": "extendable",
                          "asset": {
                            "id": "run",
                            "mime": "text/x-python",
                            "path": "run.py",
                            "title": "Run, project copy",
                            "executable": {"runtime": "python", "entry": "run.py", "args": []}
                          },
                          "_links": {
                            "self": {"href": "/api/v1/activations/a-1/artifacts/asset/run"},
                            "artifact": {"href": "/api/v1/packs/proj/artifacts/asset/run"},
                            "payload": {"href": "/api/v1/activations/a-1/artifacts/asset/run/payload"},
                            "collection": {"href": "/api/v1/activations/a-1/artifacts/asset"},
                            "up": {"href": "/api/v1/activations/a-1"}
                          }
                        }
                        """.formatted(REVISION, CLOSURE), JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("asset proj/run");
    }

    @Test
    void thePayloadIsTheWinningCopysBytesWithItsMimeTheActivationsTagAndTheSafetyHeaders() throws Exception {
        mvc(null)
                .perform(get("/api/v1/activations/a-1/artifacts/asset/run/payload")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/x-python"))
                .andExpect(header().string("ETag", "\"" + TAG + "\""))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(content().bytes(SCRIPT));
        assertThat(asked).containsExactly("payload proj/run");
    }

    @Test
    void aMatchingIfNoneMatchIs304WithTheTagAloneAndReadsNoPack() throws Exception {
        MockMvc mvc = mvc(null);
        for (String path : new String[] {
            "/api/v1/activations/a-1/artifacts/asset",
            "/api/v1/activations/a-1/artifacts/asset/run",
            "/api/v1/activations/a-1/artifacts/asset/run/payload"
        }) {
            for (String ifNoneMatch : new String[] {"\"" + TAG + "\"", "*"}) {
                mvc.perform(get(path).header("If-None-Match", ifNoneMatch))
                        .andExpect(status().isNotModified())
                        .andExpect(header().string("ETag", "\"" + TAG + "\""))
                        .andExpect(header().doesNotExist("Charter-Source-Fingerprint"))
                        .andExpect(content().bytes(new byte[0]));
            }
            // a value that is not an entity-tag list matches nothing
            mvc.perform(get(path).header("If-None-Match", "not an entity-tag list"))
                    .andExpect(status().isOk());
        }
        assertThat(asked).hasSize(4);
    }

    @Test
    void aRefusalOfTheActivationIsTheSharedHandlersAnswer() throws Exception {
        MockMvc mvc = mvc(new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "No activation is stored as 'nope'."));

        mvc.perform(get("/api/v1/activations/nope/artifacts/asset/run/payload"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("activation_not_found"));
        assertThat(asked).isEmpty();
    }
}
