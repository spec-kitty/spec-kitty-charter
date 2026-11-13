package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The port of the activation twins of the asset and skill reads (ADR-0037 as amended 2026-11-09; research R1, R2 and
 * R15): what an activation names of an asset or a skill, the winner of the layer merge and the source fingerprint of
 * the read that named it, in the edge's own words. The adapter is {@code artifacts.infra.activations}, the one place of
 * the edge that asks the activations slice; the application ring names no activations type. What the winner's pack
 * stores (an asset's descriptor and bytes, a skill's summary) is the packs port's to read, against that fingerprint.
 *
 * <p>Each read judges the activation, then whether a pack of its closure holds what it names, both before
 * {@code If-None-Match}; the refusals are the activations slice's, unchanged.
 */
public interface ActivationArtifacts {

    /**
     * The winners of one kind under an activation, in id order: {@code listEffectiveSkills} or
     * {@code listEffectiveAssets}.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param kind {@code skill} or {@code asset}
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winners, or that the client holds the current representation
     * @throws CharterRefusal with {@code ACTIVATION_NOT_FOUND}, {@code PACK_UNREADABLE}, the code of a closure that
     *     cannot compose or merge, or {@code STALE_INPUT}
     */
    Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch);

    /**
     * The winner of one asset under an activation, under the tag of {@code getEffectiveAsset}.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param assetId the asset's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winner, or that the client holds the current representation
     * @throws CharterRefusal as {@link #winners} does, and with {@code ARTIFACT_NOT_FOUND} when no pack of the closure
     *     holds the asset
     */
    Outcome<Winner> asset(String activationId, String assetId, @Nullable String ifNoneMatch);

    /**
     * The winner of one asset under an activation, under the tag of {@code getEffectiveAssetPayload}.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param assetId the asset's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winner, or that the client holds the current representation
     * @throws CharterRefusal as {@link #asset} does
     */
    Outcome<Winner> assetPayload(String activationId, String assetId, @Nullable String ifNoneMatch);

    /**
     * The winner of one skill under an activation, whatever its state, with its facts and the skill as the winning
     * pack stores it, all of one verified read.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param skillId the skill's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the skill, or that the client holds the current representation
     * @throws CharterRefusal as {@link #winners} does, and with {@code ARTIFACT_NOT_FOUND} when no pack of the closure
     *     holds the skill
     */
    Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch);

    /**
     * What a read under an activation answers.
     *
     * @param <T> the answer of a {@code 200}
     */
    sealed interface Outcome<T> {

        /**
         * The client holds the current representation ({@code 304}).
         *
         * @param etag the current entity tag, unquoted
         * @param <T> the answer of a {@code 200}
         */
        record NotModified<T>(String etag) implements Outcome<T> {

            /**
             * Checks the tag.
             *
             * @param etag the tag
             */
            public NotModified {
                Objects.requireNonNull(etag, "etag");
            }
        }

        /**
         * The current representation.
         *
         * @param value the answer
         * @param etag its entity tag, unquoted
         * @param <T> the answer of a {@code 200}
         */
        record Found<T>(T value, String etag) implements Outcome<T> {

            /**
             * Checks the fields.
             *
             * @param value the answer
             * @param etag the tag
             */
            public Found {
                Objects.requireNonNull(value, "value");
                Objects.requireNonNull(etag, "etag");
            }
        }
    }

    /**
     * The activation a read answers under, and the read it answered from.
     *
     * @param activationId the activation's id
     * @param revision the activation's revision
     * @param governanceFingerprint the digest of the packs of the activation's closure
     * @param sourceFingerprint the whole-source fingerprint of the read, which a read of a winner's pack is checked
     *     against
     */
    record Scope(String activationId, String revision, String governanceFingerprint, String sourceFingerprint) {

        /**
         * Checks the fields.
         *
         * @param activationId the activation
         * @param revision the revision
         * @param governanceFingerprint the closure's digest
         * @param sourceFingerprint the source fingerprint
         */
        public Scope {
            Objects.requireNonNull(activationId, "activationId");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }
    }

    /**
     * The version of an artifact the layer merge keeps: the pack and the level that hold it, and its policy.
     *
     * @param id the artifact's id
     * @param packId the configured identifier of the pack that holds the version in force
     * @param level that pack's level
     * @param policy the mutability level's token ({@code open}, {@code extendable} or {@code locked})
     */
    record Held(String id, PackIdentifier packId, PackLevel level, String policy) {

        /**
         * Checks the fields.
         *
         * @param id the id
         * @param packId the holding pack
         * @param level its level
         * @param policy the policy token
         */
        public Held {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(policy, "policy");
        }
    }

    /**
     * The winners of one kind.
     *
     * @param scope the activation and the read
     * @param winners the winners, by id
     */
    record Winners(Scope scope, List<Held> winners) {

        /**
         * Checks and copies the fields.
         *
         * @param scope the scope
         * @param winners the winners
         */
        public Winners {
            Objects.requireNonNull(scope, "scope");
            winners = List.copyOf(Objects.requireNonNull(winners, "winners"));
        }
    }

    /**
     * The winner of one artifact of a kind the activation does not resolve: no state.
     *
     * @param scope the activation and the read
     * @param winner the winner
     */
    record Winner(Scope scope, Held winner) {

        /**
         * Checks the fields.
         *
         * @param scope the scope
         * @param winner the winner
         */
        public Winner {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(winner, "winner");
        }
    }

    /**
     * The words of the {@code suggests} edge that makes an artifact out of force a suggestion.
     *
     * @param when when the suggestion applies, as the edge states it
     * @param reason why, as the edge states it
     */
    record Words(Optional<String> when, Optional<String> reason) {

        /**
         * Checks the fields.
         *
         * @param when the when
         * @param reason the reason
         */
        public Words {
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The facts the activation gives a winner of a kind it resolves, as the effective list gives them.
     *
     * @param held the winner
     * @param state the state's token
     * @param required whether a governing preset requires it or a pack locks it
     * @param reason the reason's token
     * @param satisfiedBy the override that satisfies its requirement, when one does
     * @param suggestion the words of the edge that suggests it, when it is a suggestion
     */
    record Facts(
            Held held,
            String state,
            boolean required,
            String reason,
            Optional<Urn> satisfiedBy,
            Optional<Words> suggestion) {

        /**
         * Checks the fields.
         *
         * @param held the winner
         * @param state the state
         * @param required the required flag
         * @param reason the reason
         * @param satisfiedBy the satisfying override
         * @param suggestion the suggestion's words
         */
        public Facts {
            Objects.requireNonNull(held, "held");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(satisfiedBy, "satisfiedBy");
            Objects.requireNonNull(suggestion, "suggestion");
        }
    }

    /**
     * The winner of one skill with its facts and the skill as the winning pack stores it.
     *
     * @param scope the activation and the read
     * @param facts the facts of the winner
     * @param skill the winning skill, a kernel artifact the packs port narrows
     */
    record SkillWinner(Scope scope, Facts facts, CharterArtifact skill) {

        /**
         * Checks the fields.
         *
         * @param scope the scope
         * @param facts the facts
         * @param skill the skill
         */
        public SkillWinner {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(skill, "skill");
        }
    }
}
