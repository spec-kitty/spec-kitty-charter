package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads a pack's toolguides through the repository ports (FR-015), the read half of the US3 toolguide slice. It copies
 * {@link DirectiveReadService}: both reads are verified by {@link ConsistentRead}, the inputs are digested before and
 * after the load, and the answer carries the digest both agree on.
 *
 * <p>Unlike a directive, a toolguide mints no relationships: its {@code references} are bare citation strings, not
 * typed edges, so {@link Toolguide#relationships()} is always empty and the read carries no originating edges. There is
 * no walk half for this kind.
 *
 * <p>The list asks the aggregating {@link PackRepository} for the pack and projects its toolguides (the sanctioned list
 * path; it adds no method to the persistence service), while the single read is a point read through
 * {@link ToolguideRepository}. A pack the load cannot resolve is refused {@link RefusalCode#PACK_NOT_FOUND}; an absent
 * toolguide is refused {@link RefusalCode#TOOLGUIDE_NOT_FOUND}, exactly as the paradigm reads refuse their absences.
 */
@Service
public final class ToolguideReadService {

    private final PackRepository packs;
    private final ToolguideRepository toolguides;
    private final ConsistentRead read;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that loads a pack aggregate (its toolguides are projected for the list)
     * @param toolguides the port that fetches one toolguide
     * @param read the verifier that digests the inputs before and after each read
     */
    @Autowired
    public ToolguideReadService(PackRepository packs, ToolguideRepository toolguides, ConsistentRead read) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.toolguides = Objects.requireNonNull(toolguides, "toolguides");
        this.read = Objects.requireNonNull(read, "read");
    }

    /**
     * The readable toolguides of a pack, with the digest of the inputs they were read from. Each carries an empty
     * relationship list (FR-015; a toolguide mints none).
     *
     * @param pack the pack's identity
     * @return the pack's toolguides, in the aggregate's stable order, and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, with
     *     {@link RefusalCode#PACK_UNREADABLE} when a configured pack or pack file cannot be read, or with
     *     {@link RefusalCode#STALE_INPUT} when the inputs changed while the toolguides were read
     */
    public ConsistentRead.Verified<List<Toolguide>> toolguidesOf(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return read.read("The inputs changed while the toolguides were read; read them again.", () -> {
            try {
                return packs.load(pack).toolguides();
            } catch (CharterRefusal refused) {
                throw packRefusal(pack, refused);
            }
        });
    }

    /**
     * One toolguide of a pack, with the digest of the inputs it was read from. Its relationships are empty (FR-015).
     *
     * @param pack the pack's identity
     * @param toolguide the toolguide's identity
     * @return the toolguide and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier,
     *     with {@link RefusalCode#TOOLGUIDE_NOT_FOUND} when the pack has no such toolguide, with
     *     {@link RefusalCode#PACK_UNREADABLE} when a pack or pack file cannot be read, or with {@link RefusalCode#STALE_INPUT} when the inputs changed while the toolguide was read (a change beats a
     *     not-found)
     */
    public ConsistentRead.Verified<Toolguide> toolguide(PackIdentifier pack, ToolguideIdentifier toolguide) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(toolguide, "toolguide");
        return read.read("The inputs changed while the toolguide was read; read it again.", () -> {
            try {
                return toolguides
                        .load(pack, toolguide)
                        .orElseThrow(() -> new CharterRefusal(
                                RefusalCode.TOOLGUIDE_NOT_FOUND,
                                "Pack " + pack.value() + " has no toolguide " + toolguide.value() + "."));
            } catch (CharterRefusal refused) {
                if (refused.code() == RefusalCode.TOOLGUIDE_NOT_FOUND) {
                    throw refused;
                }
                throw packRefusal(pack, refused);
            }
        });
    }

    /**
     * A pack the storage cannot resolve or read is, to the caller, a pack that is not there. The original
     * {@code PACK_UNREADABLE} refusal is kept as the cause so the inbound edge can leave a diagnostic trail even though
     * the 404 body names nothing of it. Any other refusal passes through.
     */
    private static CharterRefusal packRefusal(PackIdentifier pack, CharterRefusal refused) {
        if (refused.code() != RefusalCode.PACK_UNREADABLE) {
            return refused;
        }
        return new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.", refused);
    }
}
