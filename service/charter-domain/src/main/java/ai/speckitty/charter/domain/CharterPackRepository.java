package ai.speckitty.charter.domain;

/** Port: loads the configured charter offering. Implementations live in outbound adapters. */
public interface CharterPackRepository {

    /**
     * Loads every configured pack.
     *
     * @return every configured pack, in layer order
     * @throws CharterRefusal when any pack cannot be loaded; a partial offering is never returned
     */
    CharterOffering load();
}
