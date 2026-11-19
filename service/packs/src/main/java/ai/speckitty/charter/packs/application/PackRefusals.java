package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackRefusalMapping;
import java.util.List;

/**
 * The pack refusals of the API edge: one mapping per edge.
 *
 * <ul>
 *   <li>The read services': a pack the storage cannot read (its aggregate or its revision) is, to the caller, a pack
 *       that is not there ({@link #packRefusal}). The original {@code PACK_UNREADABLE} refusal
 *       is kept as the cause so the inbound edge can leave a diagnostic trail although the 404 body names nothing of
 *       it; any other refusal passes through, an identifier no configured pack answers to being already refused
 *       {@code PACK_NOT_FOUND} by the storage.
 *   <li>A registration's (#554, FR-008, S6): the verdict of the one read, as the domain port {@code PackReading}
 *       returns it, is the refusal of the upload ({@link #of}) and the diagnostics of an accepted one
 *       ({@link #diagnostics}), through the domain's one mapping ({@link PackRefusalMapping}, #478 S3), which
 *       pack-check calls too.
 * </ul>
 */
final class PackRefusals {

    private PackRefusals() {}

    /**
     * The refusal of an upload whose verdict fails ({@link PackRefusalMapping#of}).
     *
     * @param verdict the verdict of the one read
     * @return the refusal
     * @throws IllegalArgumentException when the verdict passes
     */
    static CharterRefusal of(Rationale verdict) {
        return PackRefusalMapping.of(verdict);
    }

    /**
     * The diagnostics of an accepted upload ({@link PackRefusalMapping#diagnostics}).
     *
     * @param verdict the verdict of the one read
     * @return the diagnostics
     * @throws IllegalStateException for a warning no diagnostic code spells
     */
    static List<Diagnostic> diagnostics(Rationale verdict) {
        return PackRefusalMapping.diagnostics(verdict);
    }

    /**
     * The refusal the caller sees for one a pack read raised.
     *
     * @param pack the pack read
     * @param refused the refusal the read raised
     * @return {@code PACK_NOT_FOUND} caused by {@code refused} when it is {@code PACK_UNREADABLE}, else {@code refused}
     */
    static CharterRefusal packRefusal(PackIdentifier pack, CharterRefusal refused) {
        if (refused.code() != RefusalCode.PACK_UNREADABLE) {
            return refused;
        }
        return new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.", refused);
    }
}
