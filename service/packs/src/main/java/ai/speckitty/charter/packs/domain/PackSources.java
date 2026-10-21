package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;

/** Port: the files the configured charter offering is loaded from. Implementations live in outbound adapters. */
public interface PackSources {

    /**
     * A digest of every file the offering is loaded from: each file's path relative to its pack, under the pack's
     * configured name, and its bytes, in load order. It changes whenever a loaded file does; it says nothing about
     * where the files are on the host.
     *
     * @return {@code sha256:} and 64 lower-case hex digits
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when a pack or one of its files cannot be read
     */
    String fingerprint();
}
