package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Procedure;
import ai.speckitty.charter.packs.domain.ProcedureIdentifier;
import ai.speckitty.charter.packs.domain.ProcedureRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads a pack's procedures through the repository ports (#484), as {@link ToolguideReadService} reads toolguides: both
 * reads are verified by {@link ConsistentRead}, the inputs are digested before and after the load, and the answer
 * carries the digest both agree on.
 *
 * <p>A procedure carries no relationship: what its steps reference is authored in the pack's graph documents and read
 * through the references read ({@link ArtifactReadService#references}), ADR-0064. It has no write either; the pack
 * revision it is read with is the validator of the read's {@code ETag}.
 *
 * <p>A pack the load cannot resolve is refused {@link RefusalCode#PACK_NOT_FOUND}; an absent procedure is refused
 * {@link RefusalCode#ARTIFACT_NOT_FOUND}, the code of a kind served after the per-kind codes (as the agent profile).
 */
@Service
public final class ProcedureReadService {

    private static final String STALE_ONE = "The inputs changed while the procedure was read; read it again.";

    private final PackRepository packs;
    private final ProcedureRepository procedures;
    private final ConsistentRead read;
    private final PackRevisions revisions;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that loads a pack aggregate (its procedures are projected for the list)
     * @param procedures the port that fetches one procedure
     * @param read the verifier that digests the inputs before and after each read
     * @param revisions the pack revision, read inside the verified read for an item's {@code ETag}
     */
    @Autowired
    public ProcedureReadService(
            PackRepository packs, ProcedureRepository procedures, ConsistentRead read, PackRevisions revisions) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.procedures = Objects.requireNonNull(procedures, "procedures");
        this.read = Objects.requireNonNull(read, "read");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
    }

    /**
     * The readable procedures of a pack, with the digest of the inputs they were read from. Each carries an empty
     * relationship list (FR-015; a procedure mints none).
     *
     * @param pack the pack's identity
     * @return the pack's procedures, in the aggregate's stable order, and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, with
     *     {@link RefusalCode#PACK_UNREADABLE} when a configured pack or pack file cannot be read, or with
     *     {@link RefusalCode#STALE_INPUT} when the inputs changed while the procedures were read
     */
    public ConsistentRead.Verified<List<Procedure>> proceduresOf(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return read.read("The inputs changed while the procedures were read; read them again.", () -> {
            try {
                return packs.load(pack).procedures();
            } catch (CharterRefusal refused) {
                throw packRefusal(pack, refused);
            }
        });
    }

    /**
     * One procedure of a pack, with the digest of the inputs it was read from. Its relationships are empty (FR-015).
     *
     * @param pack the pack's identity
     * @param procedure the procedure's identity
     * @return the procedure and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier,
     *     with {@link RefusalCode#ARTIFACT_NOT_FOUND} when the pack has no such procedure, with
     *     {@link RefusalCode#PACK_UNREADABLE} when a pack or pack file cannot be read, or with {@link RefusalCode#STALE_INPUT} when the inputs changed while the procedure was read (a change beats a
     *     not-found)
     */
    public ConsistentRead.Verified<Procedure> procedure(PackIdentifier pack, ProcedureIdentifier procedure) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(procedure, "procedure");
        return read.read(STALE_ONE, () -> loadOne(pack, procedure));
    }

    /**
     * One procedure of a pack with the pack revision it was read at, for an item read's body and {@code ETag}: both are
     * taken inside the one verified read, so they name the same state.
     *
     * @param pack the pack's identity
     * @param procedure the procedure's identity
     * @return the procedure and its revision, and the verified fingerprint
     * @throws CharterRefusal as {@link #procedure(PackIdentifier, ProcedureIdentifier)} does
     */
    public ConsistentRead.Verified<Revised<Procedure>> procedureWithRevision(
            PackIdentifier pack, ProcedureIdentifier procedure) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(procedure, "procedure");
        return read.read(STALE_ONE, () -> new Revised<>(loadOne(pack, procedure), revisionOf(pack)));
    }

    private Procedure loadOne(PackIdentifier pack, ProcedureIdentifier procedure) {
        try {
            return procedures
                    .load(pack, procedure)
                    .orElseThrow(() -> new CharterRefusal(
                            RefusalCode.ARTIFACT_NOT_FOUND,
                            "Pack " + pack.value() + " has no procedure " + procedure.value() + "."));
        } catch (CharterRefusal refused) {
            if (refused.code() == RefusalCode.ARTIFACT_NOT_FOUND) {
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
     * A pack the storage cannot read is, to the caller, a pack that is not there; an identifier no configured pack
     * answers to is already refused {@code PACK_NOT_FOUND} by the storage and passes through. The original
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
