package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads a pack's paradigms through the repository ports (FR-014), the paradigm half of the US2 reference slice. It is
 * a faithful copy of {@link DirectiveReadService}: both reads are verified by {@link ConsistentRead}, the inputs are
 * digested before and after the load, and the answer carries the digest both agree on.
 *
 * <p>Each returned {@link Paradigm} carries its {@link Paradigm#relationships() relationships} populated: a paradigm
 * is its own effective origin and derives those edges from its authored {@code directiveRefs} (minted as
 * {@code requires} edges to directives, WP03) and its typed {@code references} (minted as {@code suggests}). A
 * paradigm therefore originates relationships, so its read SHOULD carry them, and does (ADR-0025 Decision 3/4). The
 * referenced directives themselves are resolved on demand by the walk ({@link GraphWalk}), not by this read.
 *
 * <p>The list asks the aggregating {@link PackRepository} for the pack and projects its paradigms (the sanctioned list
 * path; it adds no method to the persistence service), while the single read is a point read through
 * {@link ParadigmRepository}. A pack the load cannot resolve is refused {@link RefusalCode#PACK_NOT_FOUND}; an absent
 * paradigm is refused {@link RefusalCode#PARADIGM_NOT_FOUND}, exactly as the directive reads refuse their absences.
 */
@Service
public final class ParadigmReadService {

    private static final String STALE_ONE = "The inputs changed while the paradigm was read; read it again.";

    private final PackRepository packs;
    private final ParadigmRepository paradigms;
    private final ConsistentRead read;
    private final PackRevisions revisions;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that loads a pack aggregate (its paradigms are projected for the list)
     * @param paradigms the port that fetches one paradigm
     * @param read the verifier that digests the inputs before and after each read
     * @param revisions the pack revision, read inside the verified read for an item's {@code ETag}
     */
    @Autowired
    public ParadigmReadService(
            PackRepository packs, ParadigmRepository paradigms, ConsistentRead read, PackRevisions revisions) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.paradigms = Objects.requireNonNull(paradigms, "paradigms");
        this.read = Objects.requireNonNull(read, "read");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
    }

    /**
     * The readable paradigms of a pack, with the digest of the inputs they were read from. Each carries its
     * relationships populated (FR-014).
     *
     * @param pack the pack's identity
     * @return the pack's paradigms, in the aggregate's stable order, and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when any configured pack or pack file cannot be
     *     read, with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, or with
     *     {@link RefusalCode#STALE_INPUT} when the inputs changed while the paradigms were read
     */
    public ConsistentRead.Verified<List<Paradigm>> paradigmsOf(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return read.read("The inputs changed while the paradigms were read; read them again.", () -> {
            try {
                return packs.load(pack).paradigms();
            } catch (CharterRefusal refused) {
                throw packRefusal(pack, refused);
            }
        });
    }

    /**
     * One paradigm of a pack, with the digest of the inputs it was read from, its relationships populated (FR-014).
     *
     * @param pack the pack's identity
     * @param paradigm the paradigm's identity
     * @return the paradigm and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when a pack or pack file cannot be read, with
     *     {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, with
     *     {@link RefusalCode#PARADIGM_NOT_FOUND} when the pack has no such paradigm, or with
     *     {@link RefusalCode#STALE_INPUT} when the inputs changed while the paradigm was read (a change beats a
     *     not-found)
     */
    public ConsistentRead.Verified<Paradigm> paradigm(PackIdentifier pack, ParadigmIdentifier paradigm) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(paradigm, "paradigm");
        return read.read(STALE_ONE, () -> loadOne(pack, paradigm));
    }

    /**
     * One paradigm of a pack with the pack revision it was read at, for an item read's body and {@code ETag}: both are
     * taken inside the one verified read, so they name the same state.
     *
     * @param pack the pack's identity
     * @param paradigm the paradigm's identity
     * @return the paradigm and its revision, and the verified fingerprint
     * @throws CharterRefusal as {@link #paradigm(PackIdentifier, ParadigmIdentifier)} does
     */
    public ConsistentRead.Verified<Revised<Paradigm>> paradigmWithRevision(
            PackIdentifier pack, ParadigmIdentifier paradigm) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(paradigm, "paradigm");
        return read.read(STALE_ONE, () -> new Revised<>(loadOne(pack, paradigm), revisionOf(pack)));
    }

    private Paradigm loadOne(PackIdentifier pack, ParadigmIdentifier paradigm) {
        try {
            return paradigms
                    .load(pack, paradigm)
                    .orElseThrow(() -> new CharterRefusal(
                            RefusalCode.PARADIGM_NOT_FOUND,
                            "Pack " + pack.value() + " has no paradigm " + paradigm.value() + "."));
        } catch (CharterRefusal refused) {
            if (refused.code() == RefusalCode.PARADIGM_NOT_FOUND) {
                throw refused;
            }
            throw packRefusal(pack, refused);
        }
    }

    private PackRevision revisionOf(PackIdentifier pack) {
        try {
            return revisions.currentRevision(pack);
        } catch (CharterRefusal refused) {
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
