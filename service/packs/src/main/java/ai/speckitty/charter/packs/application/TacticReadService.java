package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads a pack's tactics through the repository ports. Both reads are point reads: the list asks the port for the
 * tactics only, and the single read asks for the one tactic, so neither assembles the whole pack aggregate.
 *
 * <p>Every read is verified by {@link ConsistentRead}: the inputs are digested before and after the load, and the
 * answer carries the digest both agree on. A pack the load cannot resolve, and an absent tactic, are refused with a
 * not-found code ({@link RefusalCode#notFound()}), which the inbound edge answers as a client error; the mapping runs
 * inside the verified load, so a change of the inputs beats it.
 */
@Service
public final class TacticReadService {

    private final PackRepository packs;
    private final TacticRepository tactics;
    private final ConsistentRead read;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that lists a pack's tactics
     * @param tactics the port that fetches one tactic
     * @param read the verifier that digests the inputs before and after each read
     */
    @Autowired
    public TacticReadService(PackRepository packs, TacticRepository tactics, ConsistentRead read) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.tactics = Objects.requireNonNull(tactics, "tactics");
        this.read = Objects.requireNonNull(read, "read");
    }

    /**
     * The readable tactics of a pack, with the digest of the inputs they were read from.
     *
     * @param pack the pack's identity
     * @return the pack's tactics, in the port's stable order, and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when any configured pack or pack file
     *     cannot be read (refused by the first digest, which covers every configured pack),
     *     with {@link RefusalCode#PACK_NOT_FOUND} when no configured
     *     pack has this identifier, or with {@link RefusalCode#STALE_INPUT} when the inputs changed while the tactics
     *     were read, whatever the load answered
     */
    public ConsistentRead.Verified<List<Tactic>> tacticsOf(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return read.read("The inputs changed while the tactics were read; read them again.", () -> {
            try {
                return packs.tacticsOf(pack);
            } catch (CharterRefusal refused) {
                throw packRefusal(pack, refused);
            }
        });
    }

    /**
     * One tactic of a pack, with the digest of the inputs it was read from.
     *
     * @param pack the pack's identity
     * @param tactic the tactic's identity
     * @return the tactic and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when any configured pack or pack file
     *     cannot be read (refused by the first digest, which covers every configured pack),
     *     with {@link RefusalCode#PACK_NOT_FOUND} when no configured
     *     pack has this identifier, with {@link RefusalCode#TACTIC_NOT_FOUND} when the pack has no such tactic, or with
     *     {@link RefusalCode#STALE_INPUT} when the inputs changed while the tactic was read (a change beats a
     *     not-found)
     */
    public ConsistentRead.Verified<Tactic> tactic(PackIdentifier pack, TacticIdentifier tactic) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(tactic, "tactic");
        return read.read("The inputs changed while the tactic was read; read it again.", () -> {
            try {
                return tactics.load(pack, tactic)
                        .orElseThrow(() -> new CharterRefusal(
                                RefusalCode.TACTIC_NOT_FOUND,
                                "Pack " + pack.value() + " has no tactic " + tactic.value() + "."));
            } catch (CharterRefusal refused) {
                if (refused.code() == RefusalCode.TACTIC_NOT_FOUND) {
                    throw refused;
                }
                throw packRefusal(pack, refused);
            }
        });
    }

    /**
     * A pack the storage cannot resolve or read is, to the caller, a pack that is not there. The original
     * {@code PACK_UNREADABLE} refusal is kept as the cause so the inbound edge can leave a diagnostic trail even
     * though the 404 body names nothing of it.
     */
    private static CharterRefusal packRefusal(PackIdentifier pack, CharterRefusal refused) {
        if (refused.code() != RefusalCode.PACK_UNREADABLE) {
            return refused;
        }
        return new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.", refused);
    }
}
