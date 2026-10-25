package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads a pack's directives through the repository ports (FR-006), the directive half of the US1 reference slice. It
 * mirrors {@link TacticReadService}: both reads are verified by {@link ConsistentRead}, the inputs are digested before
 * and after the load, and the answer carries the digest both agree on.
 *
 * <p>Each returned {@link Directive} carries its {@link Directive#relationships() relationships} populated: the
 * directive entity is its own effective origin and derives those edges from its authored {@code references} and
 * {@code procedures} fields (ADR-0025 Decision 3/4; the storage adapter assembles the entity, and the entity assembles
 * its relationships). Standalone registered edges live in the pack's relationship shard and are reached by the walk,
 * not by this read.
 *
 * <p>The list asks the aggregating {@link PackRepository} for the pack and projects its directives (the prompt's
 * sanctioned list path; it adds no method to the persistence service), while the single read is a point read through
 * {@link DirectiveRepository}. A pack the load cannot resolve is refused {@link RefusalCode#PACK_NOT_FOUND}; an absent
 * directive is refused {@link RefusalCode#DIRECTIVE_NOT_FOUND}, exactly as the tactic reads refuse their absences.
 */
@Service
public final class DirectiveReadService {

    private final PackRepository packs;
    private final DirectiveRepository directives;
    private final ConsistentRead read;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that loads a pack aggregate (its directives are projected for the list)
     * @param directives the port that fetches one directive
     * @param read the verifier that digests the inputs before and after each read
     */
    @Autowired
    public DirectiveReadService(PackRepository packs, DirectiveRepository directives, ConsistentRead read) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.directives = Objects.requireNonNull(directives, "directives");
        this.read = Objects.requireNonNull(read, "read");
    }

    /**
     * The readable directives of a pack, with the digest of the inputs they were read from. Each carries its
     * relationships populated (FR-006).
     *
     * @param pack the pack's identity
     * @return the pack's directives, in the aggregate's stable order, and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when any configured pack or pack file cannot be
     *     read, with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, or with
     *     {@link RefusalCode#STALE_INPUT} when the inputs changed while the directives were read
     */
    public ConsistentRead.Verified<List<Directive>> directivesOf(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return read.read("The inputs changed while the directives were read; read them again.", () -> {
            try {
                return packs.load(pack).directives();
            } catch (CharterRefusal refused) {
                throw packRefusal(pack, refused);
            }
        });
    }

    /**
     * One directive of a pack, with the digest of the inputs it was read from, its relationships populated (FR-006).
     *
     * @param pack the pack's identity
     * @param directive the directive's identity
     * @return the directive and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when a pack or pack file cannot be read, with
     *     {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier, with
     *     {@link RefusalCode#DIRECTIVE_NOT_FOUND} when the pack has no such directive, or with
     *     {@link RefusalCode#STALE_INPUT} when the inputs changed while the directive was read (a change beats a
     *     not-found)
     */
    public ConsistentRead.Verified<Directive> directive(PackIdentifier pack, DirectiveIdentifier directive) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(directive, "directive");
        return read.read("The inputs changed while the directive was read; read it again.", () -> {
            try {
                return directives
                        .load(pack, directive)
                        .orElseThrow(() -> new CharterRefusal(
                                RefusalCode.DIRECTIVE_NOT_FOUND,
                                "Pack " + pack.value() + " has no directive " + directive.value() + "."));
            } catch (CharterRefusal refused) {
                if (refused.code() == RefusalCode.DIRECTIVE_NOT_FOUND) {
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
