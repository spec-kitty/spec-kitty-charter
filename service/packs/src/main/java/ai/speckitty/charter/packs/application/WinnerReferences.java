package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The references of the winner an activation named (research R10, #532): the pack read of the references one artifact
 * writes ({@link ArtifactReadService#references}), of the pack that holds the version in force, checked against the
 * source fingerprint of the read that named the winner. It computes nothing of its own: the references, their
 * resolution through the compose and their order are that read's, so the twin under an activation cannot answer other
 * references than the pack read of the winner's pack does.
 *
 * <p>The pack read is one {@link ConsistentRead}, and this read refuses {@link RefusalCode#STALE_INPUT} when it verified
 * another fingerprint than the caller holds. The activation found the winner in that pack a moment ago, so a pack that
 * is configured no longer ({@code pack_not_found}) or no longer holds the artifact (the not-found code of its kind) means
 * the packs changed between the two reads: {@code stale_input} too, never a refusal of a pack or an artifact the request
 * did not name. Any other refusal is the pack read's, unchanged.
 */
@Service
public final class WinnerReferences {

    private static final String STALE = "The packs changed since the activation named the winner; read it again.";

    private final References references;

    /**
     * The references read over the pack read of the references.
     *
     * @param reads the generic artifact reads, whose references read this one answers
     */
    @Autowired
    public WinnerReferences(ArtifactReadService reads) {
        this(Objects.requireNonNull(reads, "reads")::references);
    }

    /**
     * The references read over any read of the references, for a test.
     *
     * @param references the read of the references one artifact of a pack writes
     */
    WinnerReferences(References references) {
        this.references = Objects.requireNonNull(references, "references");
    }

    /**
     * The references the winner writes, as the pack read of its pack answers them, read at the fingerprint the caller
     * holds.
     *
     * @param pack the configured identifier of the pack that holds the winner
     * @param kind the winner's kind, a listed kind
     * @param id the winner's id
     * @param expectedFingerprint the whole-source fingerprint of the read that named the winner
     * @return the references, in the order the pack read gives them, and that fingerprint
     * @throws CharterRefusal with {@code stale_input} when the read verified another fingerprint, or the pack or the
     *     artifact is gone; otherwise as {@link ArtifactReadService#references} refuses
     */
    public ConsistentRead.Verified<List<ArtifactReadService.Reference>> of(
            PackIdentifier pack, ArtifactKind kind, String id, String expectedFingerprint) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(expectedFingerprint, "expectedFingerprint");
        RefusalCode absent = ArtifactReadService.absentCode(kind);
        ConsistentRead.Verified<List<ArtifactReadService.Reference>> verified;
        try {
            verified = references.read(pack, kind.token(), id);
        } catch (CharterRefusal refusal) {
            if (refusal.code() == RefusalCode.PACK_NOT_FOUND || refusal.code() == absent) {
                throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE, refusal);
            }
            throw refusal;
        }
        if (!verified.fingerprint().equals(expectedFingerprint)) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE);
        }
        return verified;
    }

    /** The read of the references one artifact of a pack writes: {@link ArtifactReadService#references}. */
    @FunctionalInterface
    interface References {

        /**
         * Reads the references.
         *
         * @param pack the referring pack
         * @param kindToken the referring artifact's kind token
         * @param id the referring artifact's id
         * @return the references and the verified fingerprint
         */
        ConsistentRead.Verified<List<ArtifactReadService.Reference>> read(
                PackIdentifier pack, String kindToken, String id);
    }
}
