package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;

/**
 * Port: loads the configured charter offering. Implementations live in outbound adapters.
 *
 * <p>The file-system implementation composes the domain's {@link PackPolicy}: it reads the files into
 * {@link PackContents} and {@code PackPolicy} decides the outcome. Issue 115 moves that composition into an
 * application-ring use case.
 */
public interface CharterPackRepository {

    /**
     * Loads every configured pack.
     *
     * @return every configured pack, in layer order
     * @throws CharterRefusal when any pack cannot be loaded; a partial offering is never returned
     */
    CharterOffering load();
}
