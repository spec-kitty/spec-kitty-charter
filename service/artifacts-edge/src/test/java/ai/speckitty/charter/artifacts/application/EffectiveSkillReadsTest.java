package ai.speckitty.charter.artifacts.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The skill item under an activation is one read of the activations port: the facts and the skill its pack stores, from
 * one session; the packs port maps the kernel artifact and reads no pack. The list and the whole service are
 * {@code EffectiveSkillsControllerTest} and the contract scenarios.
 */
class EffectiveSkillReadsTest {

    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final PackIdentifier CORE = new PackIdentifier("core");
    private static final Scope SCOPE = new Scope("a-1", "sha256:rev", "sha256:closure", FINGERPRINT);
    private static final String ETAG = "sha256:" + "e".repeat(64);

    private final List<String> asked = new ArrayList<>();

    private static <T> T found(Outcome<T> outcome) {
        assertThat(outcome).isInstanceOf(Outcome.Found.class);
        return ((Outcome.Found<T>) outcome).value();
    }

    @Test
    void theItemMapsTheKernelArtifactTheActivationReadThroughThePacksPort() {
        CharterArtifact entity = landPrSkill();
        ActivationArtifacts.Facts facts = landPrFacts();
        SkillView view = landPrView();
        ActivationArtifacts activation = activationHolding(facts, entity);
        SkillReads skills = skillsViewing(view);

        EffectiveSkillReads.Read read =
                found(new EffectiveSkillReads(activation, skills).skill("a-1", "land-pr", null));

        assertThat(read.facts()).isEqualTo(facts);
        assertThat(read.skill()).isEqualTo(view);
        assertThat(asked).containsExactly("view land-pr for The read of the skill land-pr under activation a-1");
    }

    /** The kernel artifact the activation holds: the core pack's {@code land-pr} skill. */
    private static CharterArtifact landPrSkill() {
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
                return "land-pr";
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return "land-pr";
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
    }

    /** The activation's facts of the skill: built in, inactive, not selected. */
    private static ActivationArtifacts.Facts landPrFacts() {
        return new ActivationArtifacts.Facts(
                new Held("land-pr", CORE, PackLevel.BUILT_IN, "open"),
                "inactive",
                false,
                "not_selected",
                List.of(),
                Optional.empty());
    }

    /** The view the packs port answers for the skill. */
    private static SkillView landPrView() {
        return new SkillView(
                "land-pr",
                CORE,
                "1.0",
                "Land a PR",
                "Lands a pull request.",
                "prompt",
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.of("body"));
    }

    /** The activations port: it answers the skill with the facts and the entity, and refuses every other read. */
    private static ActivationArtifacts activationHolding(ActivationArtifacts.Facts facts, CharterArtifact entity) {
        return new ActivationArtifacts() {
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

                throw new UnsupportedOperationException();
            }

            @Override
            public Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
                return new Outcome.Found<>(new SkillWinner(SCOPE, facts, entity), ETAG);
            }
        };
    }

    /** The packs port: it maps the kernel artifact to the view, records the ask, and reads no pack. */
    private SkillReads skillsViewing(SkillView view) {
        return new SkillReads() {
            @Override
            public Listed list(PackIdentifier pack) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Read skill(PackIdentifier pack, String skillId) {
                throw new UnsupportedOperationException("the item maps the activation's read and reads no pack");
            }

            @Override
            public Listed list(PackIdentifier pack, String expectedFingerprint) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SkillView view(CharterArtifact skill, String read) {
                asked.add("view " + skill.localId() + " for " + read);
                return view;
            }
        };
    }
}
