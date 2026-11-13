package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.artifacts.application.EffectiveReferenceReads;
import ai.speckitty.charter.artifacts.application.ReferenceReads;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.web.DeferredETag;
import ai.speckitty.charter.web.RefusalHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The references read under an activation, over fake ports and the real application ring: the pack read of the
 * winner's pack, asked for the winner's pack, kind and id at the activation's source fingerprint, each reference mapped
 * as the pack read maps it, with {@code effective-artifact} only on a target the closure holds of a listed kind, the
 * answer's {@code self} and {@code up}, the source fingerprint and no {@code ETag}; the activation's refusals
 * unchanged; and a second read that no longer finds the winner {@code stale_input}. The parity with the pack read and
 * the routing are the contract scenarios ({@code effective-references.feature}).
 */
class EffectiveReferencesControllerTest {

    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final PackIdentifier CORE = new PackIdentifier("core");
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final Scope SCOPE =
            new Scope("a-1", "sha256:" + "a".repeat(64), "sha256:" + "c".repeat(64), FINGERPRINT);
    private static final Held D = new Held("D", PROJ, PackLevel.PROJECT, "open");
    private static final Urn T = new Urn(NodeKind.TACTIC, "T");
    private static final Urn Z = new Urn(NodeKind.TACTIC, "Z");
    private static final Urn TEMPLATE = new Urn(NodeKind.TEMPLATE, "notes");
    private static final ReferenceReads.Holder CORE_HOLDER =
            new ReferenceReads.Holder(CORE, "01M4P01D5TWB5FKZGNNQG3943R", "core-pack");

    private final List<String> asked = new ArrayList<>();

    /** The refusal the packs port answers with, if any. */
    private @Nullable CharterRefusal packsRefuse;

    private final ActivationArtifacts activations = new ActivationArtifacts() {
        @Override
        public Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch) {
            throw new UnsupportedOperationException();
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
            if (!activationId.equals("a-1")) {
                throw new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "No activation is stored as 'nope'.");
            }
            if (!kindToken.equals("directive")) {
                throw new CharterRefusal(
                        RefusalCode.KIND_NOT_FOUND, "The service serves no artifact kind " + kindToken + ".");
            }
            if (!artifactId.equals("D")) {
                throw new CharterRefusal(
                        RefusalCode.ARTIFACT_NOT_FOUND,
                        "No pack of the activation's closure holds the directive " + artifactId + ".");
            }
            return new Referrer(SCOPE, ArtifactKind.DIRECTIVE, D, Set.of(T, new Urn(NodeKind.DIRECTIVE, "D")));
        }

        @Override
        public Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
            throw new UnsupportedOperationException();
        }
    };

    private final ReferenceReads packs = (pack, kind, id, expectedFingerprint) -> {
        asked.add(pack.value() + " " + kind.token() + " " + id + " " + expectedFingerprint);
        if (packsRefuse != null) {
            throw packsRefuse;
        }
        return List.of(
                new ReferenceReads.Reference(
                        PackQualifiedReference.parse("core-pack::tactic:T"),
                        RelationType.REQUIRES,
                        T,
                        Optional.of(CORE_HOLDER),
                        Optional.of(ArtifactKind.TACTIC)),
                new ReferenceReads.Reference(
                        PackQualifiedReference.parse("core-pack::tactic:Z"),
                        RelationType.SUGGESTS,
                        Z,
                        Optional.of(CORE_HOLDER),
                        Optional.of(ArtifactKind.TACTIC)),
                new ReferenceReads.Reference(
                        PackQualifiedReference.parse("template:notes"),
                        RelationType.SUGGESTS,
                        TEMPLATE,
                        Optional.empty(),
                        Optional.empty()));
    };

    private MockMvc mvc() {
        JsonMapper json = JsonMapper.builder()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        EffectiveBodies bodies = new EffectiveBodies("/api/v1", new AssetBodies("/api/v1"), new SkillBodies("/api/v1"));
        return MockMvcBuilders.standaloneSetup(
                        new EffectiveReferencesController(new EffectiveReferenceReads(activations, packs), bodies))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new RefusalHandler(), new DeferredETag())
                .build();
    }

    @Test
    void theAnswerIsThePackReadOfTheWinnersPackWithEachHeldTargetLinkedUnderTheActivation() throws Exception {
        mvc().perform(get("/api/v1/activations/a-1/artifacts/directive/D/references")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(content().json("""
                        {
                          "items": [
                            {"reference": "core-pack::tactic:T", "relation": "requires",
                             "holdingPack": {"packId": "core", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943R",
                                             "name": "core-pack"},
                             "urn": "tactic:T",
                             "_links": {
                               "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/tactic/T"},
                               "artifact": {"href": "/api/v1/packs/core/artifacts/tactic/T"}}},
                            {"reference": "core-pack::tactic:Z", "relation": "suggests",
                             "holdingPack": {"packId": "core", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943R",
                                             "name": "core-pack"},
                             "urn": "tactic:Z",
                             "_links": {"artifact": {"href": "/api/v1/packs/core/artifacts/tactic/Z"}}},
                            {"reference": "template:notes", "relation": "suggests", "urn": "template:notes",
                             "_links": {}}
                          ],
                          "_links": {
                            "self": {"href": "/api/v1/activations/a-1/artifacts/directive/D/references"},
                            "up": {"href": "/api/v1/activations/a-1"}
                          }
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("proj directive D " + FINGERPRINT);
    }

    @Test
    void theActivationsRefusalsAreTheAnswerAndNothingIsReadOfThePacks() throws Exception {
        mvc().perform(get("/api/v1/activations/nope/artifacts/directive/D/references"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("activation_not_found"));
        mvc().perform(get("/api/v1/activations/a-1/artifacts/template/D/references"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("kind_not_found"));
        mvc().perform(get("/api/v1/activations/a-1/artifacts/directive/GHOST/references"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("artifact_not_found"));
        assertThat(asked).isEmpty();
    }

    @Test
    void aWinnerItsPackNoLongerHoldsIsStaleInputNotARefusalOfWhatTheRequestNamed() throws Exception {
        packsRefuse = new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, "gone");

        mvc().perform(get("/api/v1/activations/a-1/artifacts/directive/D/references"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("stale_input"));
    }

    @Test
    void anotherRefusalOfThePackReadIsTheAnswerUnchanged() throws Exception {
        packsRefuse = new CharterRefusal(RefusalCode.PACK_UNREADABLE, "unreadable");

        mvc().perform(get("/api/v1/activations/a-1/artifacts/directive/D/references"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("pack_unreadable"));
    }
}
