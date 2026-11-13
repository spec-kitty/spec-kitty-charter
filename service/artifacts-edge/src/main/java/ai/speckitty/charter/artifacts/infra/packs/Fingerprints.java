package ai.speckitty.charter.artifacts.infra.packs;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;

/**
 * The check of a read of a pack against the source fingerprint a caller already holds (research R15): the read an
 * activation answered names a winner at one fingerprint, and what the winner's pack stores must be read at the same,
 * else the answer would mix two states of the packs.
 */
final class Fingerprints {

    private static final String STALE = "The packs changed since the activation named the winner; read it again.";

    private Fingerprints() {}

    /**
     * Refuses a read whose verified fingerprint is not the one the caller holds.
     *
     * @param verified the fingerprint this read verified
     * @param expected the fingerprint of the caller's own verified read
     * @throws CharterRefusal with {@code stale_input} when they differ
     */
    static void require(String verified, String expected) {
        if (!verified.equals(expected)) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE);
        }
    }
}
