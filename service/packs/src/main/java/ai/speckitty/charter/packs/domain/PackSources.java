package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.Collection;

/** Port: the files the configured charter offering is loaded from. Implementations live in outbound adapters. */
public interface PackSources {

    /**
     * A digest of every pack file the service loads, plus the declared graph inputs, and any other configured input
     * the adapter digests, in a fixed order: each file's path relative to its pack, under the pack's configured name,
     * and its bytes. It changes whenever one of those files does; it says nothing about where the files are on the
     * host.
     *
     * @return {@code sha256:} and 64 lower-case hex digits
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when a pack or one of its files cannot be read
     */
    String fingerprint();

    /**
     * The digest of the named packs' files alone, as {@link #fingerprint()} digests them: a change to the files of a
     * pack that is not named leaves it as it is. The default answers the digest of every input, which is correct but
     * wider; an adapter that can tell the packs apart overrides it.
     *
     * @param packNames the configured names of the packs
     * @return {@code sha256:} and 64 lower-case hex digits
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when a named pack or one of its files cannot be
     *     read
     */
    default String fingerprintOf(Collection<String> packNames) {
        return fingerprint();
    }
}
