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
 * <p>An absent or unreadable pack, and an absent tactic, are refused with a not-found code ({@link
 * RefusalCode#notFound()}), which the inbound edge answers as a client error rather than as a server fault.
 */
@Service
public final class TacticReadService {

    private final PackRepository packs;
    private final TacticRepository tactics;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that lists a pack's tactics
     * @param tactics the port that fetches one tactic
     */
    @Autowired
    public TacticReadService(PackRepository packs, TacticRepository tactics) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.tactics = Objects.requireNonNull(tactics, "tactics");
    }

    /**
     * The readable tactics of a pack.
     *
     * @param pack the pack's identity
     * @return the pack's tactics, in the port's stable order
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when the pack is absent or unreadable
     */
    public List<Tactic> tacticsOf(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        try {
            return packs.tacticsOf(pack);
        } catch (CharterRefusal refused) {
            throw packRefusal(pack, refused);
        }
    }

    /**
     * One tactic of a pack.
     *
     * @param pack the pack's identity
     * @param tactic the tactic's identity
     * @return the tactic
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when the pack is absent or unreadable, or with
     *     {@link RefusalCode#TACTIC_NOT_FOUND} when the pack has no such tactic
     */
    public Tactic tactic(PackIdentifier pack, TacticIdentifier tactic) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(tactic, "tactic");
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
