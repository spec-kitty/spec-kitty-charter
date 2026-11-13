package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;
import java.util.Objects;

/**
 * The pack-scoped skill reads (#107, ADR-0074 decisions 4, 9 and 10): a pack's skills, and one skill with the body of a
 * prompt skill. It is the port of the edge (ADR-0037 as amended 2026-11-09): the adapter is {@code artifacts.infra.packs},
 * the one place of the edge that asks the packs slice, and each read is one verified read there, so the answer, its
 * revision and its fingerprint name the same inputs.
 *
 * <p>The refusals are the packs slice's, unchanged: an unknown pack is {@code pack_not_found}, a skill the pack does
 * not hold {@code artifact_not_found}, a pack that cannot be read {@code pack_unreadable}, and a change of the inputs
 * during the read {@code stale_input}.
 */
public interface SkillReads {

    /**
     * The skills a pack holds, in id order.
     *
     * @param pack the pack's identity, as the request names it
     * @return the skills, the pack revision and the verified fingerprint
     * @throws CharterRefusal as the packs slice refuses
     */
    Listed list(PackIdentifier pack);

    /**
     * One skill, with its body when it is a prompt skill.
     *
     * @param pack the pack's identity, as the request names it
     * @param skillId the skill's id
     * @return the skill, the pack revision and the verified fingerprint
     * @throws CharterRefusal as the packs slice refuses
     */
    Read skill(PackIdentifier pack, String skillId);

    /**
     * The skills a pack holds, read against the source fingerprint a caller already holds (research R15): the read of
     * the pack that holds the winners an activation named.
     *
     * @param pack the configured identifier of the pack
     * @param expectedFingerprint the whole-source fingerprint of the caller's own verified read
     * @return the skills, the pack revision and that fingerprint
     * @throws CharterRefusal as {@link #list(PackIdentifier)} refuses, and with {@code stale_input} when this read
     *     verified another fingerprint
     */
    Listed list(PackIdentifier pack, String expectedFingerprint);

    /**
     * The view of a skill another read already holds as a kernel artifact (the winner an activation read): the same
     * mapping as {@link #skill}, with nothing read.
     *
     * @param skill the skill, as its pack stores it
     * @param read the read that holds it, named by the fault when it is no skill
     * @return the view
     * @throws IllegalStateException when the artifact is no skill of the packs slice: a broken invariant of the read
     */
    SkillView view(CharterArtifact skill, String read);

    /**
     * What the list read answers.
     *
     * @param skills the pack's skills, in id order
     * @param revision the pack revision they were read at: the {@code ETag} token, without quotes
     * @param sourceFingerprint the whole-source digest the verified read agreed on
     */
    record Listed(List<SkillView> skills, String revision, String sourceFingerprint) {

        /**
         * Checks and copies the fields.
         *
         * @param skills the skills
         * @param revision the revision
         * @param sourceFingerprint the fingerprint
         */
        public Listed {
            skills = List.copyOf(Objects.requireNonNull(skills, "skills"));
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }
    }

    /**
     * What the item read answers.
     *
     * @param skill the skill
     * @param revision the pack revision it was read at: the {@code ETag} token, without quotes
     * @param sourceFingerprint the whole-source digest the verified read agreed on
     */
    record Read(SkillView skill, String revision, String sourceFingerprint) {

        /**
         * Checks the fields.
         *
         * @param skill the skill
         * @param revision the revision
         * @param sourceFingerprint the fingerprint
         */
        public Read {
            Objects.requireNonNull(skill, "skill");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }
    }
}
