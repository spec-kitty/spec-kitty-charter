package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads a pack's styleguides through the repository ports (FR-013), the styleguide read half of the US2 replicated
 * slice. It mirrors {@link DirectiveReadService}: both reads are verified by {@link ConsistentRead}, the inputs are
 * digested before and after the load, and the answer carries the digest both agree on.
 *
 * <p>Each returned {@link Styleguide} carries its {@link Styleguide#relationships() relationships} — but a styleguide
 * mints none (its references are bare citation strings, ADR-0024/0025), so the list is always empty. There is no walk
 * half for this kind: the read is value-equality only.
 *
 * <p>The list asks the aggregating {@link PackRepository} for the pack and projects its styleguides (the prompt's
 * sanctioned list path; it adds no method to the persistence service), while the single read is a point read through
 * {@link StyleguideRepository}. A pack the load cannot resolve is refused {@link RefusalCode#PACK_NOT_FOUND}; an absent
 * styleguide is refused {@link RefusalCode#STYLEGUIDE_NOT_FOUND}, exactly as the directive read refuses its absences.
 */
@Service
public final class StyleguideReadService {

    private static final String STALE_ONE = "The inputs changed while the styleguide was read; read it again.";

    private final PackRepository packs;
    private final StyleguideRepository styleguides;
    private final ConsistentRead read;
    private final PackRevisions revisions;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that loads a pack aggregate (its styleguides are projected for the list)
     * @param styleguides the port that fetches one styleguide
     * @param read the verifier that digests the inputs before and after each read
     * @param revisions the pack revision, read inside the verified read for an item's {@code ETag}
     */
    @Autowired
    public StyleguideReadService(
            PackRepository packs, StyleguideRepository styleguides, ConsistentRead read, PackRevisions revisions) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.styleguides = Objects.requireNonNull(styleguides, "styleguides");
        this.read = Objects.requireNonNull(read, "read");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
    }

    /**
     * The readable styleguides of a pack, with the digest of the inputs they were read from. Each carries its
     * relationships (always empty for a styleguide, FR-013).
     *
     * @param pack the pack's identity
     * @return the pack's styleguides, in the aggregate's stable order, and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, or
     *     with {@link RefusalCode#STALE_INPUT} when the inputs changed while the styleguides were read
     */
    public ConsistentRead.Verified<List<Styleguide>> styleguidesOf(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return read.read("The inputs changed while the styleguides were read; read them again.", () -> {
            try {
                return packs.load(pack).styleguides();
            } catch (CharterRefusal refused) {
                throw packRefusal(pack, refused);
            }
        });
    }

    /**
     * One styleguide of a pack, with the digest of the inputs it was read from, its relationships (always empty for a
     * styleguide, FR-013).
     *
     * @param pack the pack's identity
     * @param styleguide the styleguide's identity
     * @return the styleguide and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, with
     *     {@link RefusalCode#STYLEGUIDE_NOT_FOUND} when the pack has no such styleguide, or with
     *     {@link RefusalCode#STALE_INPUT} when the inputs changed while the styleguide was read (a change beats a
     *     not-found)
     */
    public ConsistentRead.Verified<Styleguide> styleguide(PackIdentifier pack, StyleguideIdentifier styleguide) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(styleguide, "styleguide");
        return read.read(STALE_ONE, () -> loadOne(pack, styleguide));
    }

    /**
     * One styleguide of a pack with the pack revision it was read at, for an item read's body and {@code ETag}: both are
     * taken inside the one verified read, so they name the same state.
     *
     * @param pack the pack's identity
     * @param styleguide the styleguide's identity
     * @return the styleguide and its revision, and the verified fingerprint
     * @throws CharterRefusal as {@link #styleguide(PackIdentifier, StyleguideIdentifier)} does
     */
    public ConsistentRead.Verified<Revised<Styleguide>> styleguideWithRevision(
            PackIdentifier pack, StyleguideIdentifier styleguide) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(styleguide, "styleguide");
        return read.read(STALE_ONE, () -> new Revised<>(loadOne(pack, styleguide), revisionOf(pack)));
    }

    private Styleguide loadOne(PackIdentifier pack, StyleguideIdentifier styleguide) {
        try {
            return styleguides
                    .load(pack, styleguide)
                    .orElseThrow(() -> new CharterRefusal(
                            RefusalCode.STYLEGUIDE_NOT_FOUND,
                            "Pack " + pack.value() + " has no styleguide " + styleguide.value() + "."));
        } catch (CharterRefusal refused) {
            if (refused.code() == RefusalCode.STYLEGUIDE_NOT_FOUND) {
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
