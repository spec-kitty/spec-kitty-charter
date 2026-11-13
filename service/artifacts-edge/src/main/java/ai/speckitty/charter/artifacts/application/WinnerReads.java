package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.function.Supplier;

/**
 * The second read of an activation twin (research R15): what the pack that holds a winner stores, read after the
 * activation named the winner and checked against the source fingerprint of that read. The activation's read found the
 * winner in that pack a moment ago, so a pack that is configured no longer ({@code pack_not_found}) or no longer holds
 * the artifact ({@code artifact_not_found}) means the packs changed between the two reads: {@code stale_input}, never a
 * refusal of a pack or artifact the request did not name.
 *
 * <p>The packs slice's {@code WinnerReferences} maps a vanished winner to {@code stale_input} too, on purpose and not as
 * a copy of this one: the references read refuses an absent artifact with the not-found code of its kind
 * ({@code directive_not_found}, {@code tactic_not_found}, ...), which this edge cannot see, because the references
 * twin hands it any listed kind. This mapping serves the asset and skill twins, whose reads refuse with
 * {@code artifact_not_found} alone. Each stays beside the read whose refusals it knows.
 */
final class WinnerReads {

    private static final String STALE = "The packs changed since the activation named the winner; read it again.";

    private WinnerReads() {}

    /**
     * Runs the second read.
     *
     * @param read the read of the winner's pack, against the fingerprint of the first read
     * @param <T> its answer
     * @return its answer
     * @throws CharterRefusal with {@code stale_input} when the winner's pack or the artifact is gone, or as the read
     *     refuses otherwise
     */
    static <T> T of(Supplier<T> read) {
        try {
            return read.get();
        } catch (CharterRefusal refusal) {
            if (refusal.code() == RefusalCode.PACK_NOT_FOUND || refusal.code() == RefusalCode.ARTIFACT_NOT_FOUND) {
                throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE, refusal);
            }
            throw refusal;
        }
    }

    /**
     * The refusal of a winner the activation named that its pack's read does not list.
     *
     * @return {@code stale_input}
     */
    static CharterRefusal stale() {
        return new CharterRefusal(RefusalCode.STALE_INPUT, STALE);
    }
}
