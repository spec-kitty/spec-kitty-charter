package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.function.Supplier;

/**
 * The one pack mapping every read service applies: a pack the storage cannot read (its aggregate or its revision) is,
 * to the caller, a pack that is not there. The original {@code PACK_UNREADABLE} refusal is kept as the cause so the
 * inbound edge can leave a diagnostic trail although the 404 body names nothing of it; any other refusal passes
 * through, an identifier no configured pack answers to being already refused {@code PACK_NOT_FOUND} by the storage.
 */
final class PackRefusals {

    private PackRefusals() {}

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

    /**
     * Runs a pack read, mapping its refusal as {@link #packRefusal} does.
     *
     * @param pack the pack read
     * @param load the read
     * @param <T> what it returns
     * @return what the read returns
     * @throws CharterRefusal the mapped refusal
     */
    static <T> T asPackNotFound(PackIdentifier pack, Supplier<T> load) {
        try {
            return load.get();
        } catch (CharterRefusal refused) {
            throw packRefusal(pack, refused);
        }
    }
}
