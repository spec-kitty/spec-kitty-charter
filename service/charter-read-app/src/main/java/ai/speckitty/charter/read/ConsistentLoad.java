package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.CharterOffering;
import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackSources;
import ai.speckitty.charter.domain.RefusalCode;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Loads the offering together with the digest of the files it was loaded from, and proves the two belong together: the
 * inputs are digested before the load and again after it, and a difference means they changed while the read was
 * answered ({@link RefusalCode#STALE_INPUT}). The digest of an answer is therefore one the answer was computed under,
 * so a {@code Charter-Source-Fingerprint} that reports it cannot disagree with the answer.
 *
 * <p>A load that fails is judged the same way. A file that is half written when the load parses it makes the load
 * refuse (for example with {@link RefusalCode#PACK_UNREADABLE}); if the inputs digest differently afterwards, the
 * failure was the change's, not the pack's, and the read answers {@code STALE_INPUT}, which may be retried, instead of
 * a permanent refusal.
 *
 * <p>An input that the first digest could read and the second cannot (a file that became unreadable during the read)
 * has changed too: the second digest's refusal, like a failed load's, is {@code STALE_INPUT}.
 *
 * <p><b>The residual window.</b> The check sees a difference between two digests. A change made after the second
 * digest is not seen (the answer is then as fresh as any answer is the moment after it is made), and neither is a
 * change that is reverted between the two digests (ABA): the bytes the load read may have been the changed ones while
 * both digests are of the original.
 */
public final class ConsistentLoad {

    private static final String STALE_DETAIL =
            "The packs or the project configuration changed while the read was answered; read again.";

    /**
     * An offering and the digest of the inputs it was loaded from.
     *
     * @param offering the loaded offering
     * @param sourceFingerprint the digest the inputs had before and after the load
     */
    public record Loaded(CharterOffering offering, String sourceFingerprint) {}

    private final CharterPackRepository packs;
    private final PackSources sources;

    /**
     * A load over {@code packs}, digested through {@code sources}.
     *
     * @param packs the repository port; the offering is loaded afresh on every call
     * @param sources the pack files port; it must name the files {@code packs} loads from, and it is the same one the
     *     service info digests, so a read's fingerprint equals the info's for unchanged inputs
     */
    public ConsistentLoad(CharterPackRepository packs, PackSources sources) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.sources = Objects.requireNonNull(sources, "sources");
    }

    /**
     * Digests the inputs, loads the offering, and digests the inputs again.
     *
     * @return the offering and the digest both reads agree on
     * @throws CharterRefusal with {@link RefusalCode#STALE_INPUT} when the two digests differ, when the second digest
     *     is refused, or when the load was refused and the inputs digest differently afterwards (or cannot be
     *     digested again); with the first
     *     digest's refusal when an input cannot be read ({@link RefusalCode#PACK_UNREADABLE}); otherwise with the
     *     load's own refusal. The second digest being refused counts as a change, so it is {@code STALE_INPUT}. A failure that is not a refusal is not changed
     */
    public Loaded load() {
        String first = sources.fingerprint();
        CharterOffering offering;
        try {
            offering = packs.load();
        } catch (CharterRefusal refused) {
            if (!first.equals(digestOrNull())) {
                throw stale();
            }
            throw refused;
        }
        String second = digestOrNull();
        if (!first.equals(second)) {
            throw stale();
        }
        return new Loaded(offering, first);
    }

    private @Nullable String digestOrNull() {
        return digestOrNull(sources);
    }

    /**
     * The digest of {@code sources} now, or {@code null} when it cannot be taken: an input that was readable for the
     * first digest and is not for a later one has changed. Shared with {@link ServiceInfoQuery}.
     */
    static @Nullable String digestOrNull(PackSources sources) {
        try {
            return sources.fingerprint();
        } catch (CharterRefusal unreadable) {
            return null;
        }
    }

    private static CharterRefusal stale() {
        return new CharterRefusal(RefusalCode.STALE_INPUT, STALE_DETAIL);
    }
}
