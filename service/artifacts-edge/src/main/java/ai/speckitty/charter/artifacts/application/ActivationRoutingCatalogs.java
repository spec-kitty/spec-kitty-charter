package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The port of the activation twin of the routing catalog item read (ADR-0080 decision 6): the winner of one catalog
 * under an activation, whatever its state. The list of the winners is {@link ActivationArtifacts#winners}'s.
 */
public interface ActivationRoutingCatalogs {

    /**
     * The winner of one routing catalog under an activation, whatever its state, with its facts and the catalog as the
     * winning pack stores it, all of one verified read.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param catalogId the catalog's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the catalog, or that the client holds the current representation
     * @throws CharterRefusal as {@link ActivationArtifacts#winners} does, and with {@code ARTIFACT_NOT_FOUND} when no
     *     pack of the closure holds the catalog
     */
    ActivationArtifacts.Outcome<CatalogWinner> routingCatalog(
            String activationId, String catalogId, @Nullable String ifNoneMatch);

    /**
     * The winner of one catalog with its facts and the catalog as the winning pack stores it.
     *
     * @param scope the activation and the read
     * @param facts the facts of the winner
     * @param catalog the winning catalog, a kernel artifact the packs port narrows
     */
    record CatalogWinner(ActivationArtifacts.Scope scope, ActivationArtifacts.Facts facts, CharterArtifact catalog) {

        /**
         * Checks the fields.
         *
         * @param scope the scope
         * @param facts the facts
         * @param catalog the catalog
         */
        public CatalogWinner {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(catalog, "catalog");
        }
    }
}
