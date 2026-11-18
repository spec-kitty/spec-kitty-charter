package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.routing.domain.RoutingCatalog;
import java.util.Objects;

/**
 * A routing catalog as the edge answers it (ADR-0080 decision 6): the pack that holds it and the catalog, a value of the
 * routing slice's domain, which is plain Java and depends on nothing but the kernel (ADR-0037, amendment of
 * 2026-11-18), so the edge maps it without a copy of its own.
 *
 * @param packId the pack that holds the catalog
 * @param catalog the catalog
 */
public record RoutingCatalogView(PackIdentifier packId, RoutingCatalog catalog) {

    /**
     * Checks the members.
     *
     * @param packId the pack
     * @param catalog the catalog
     */
    public RoutingCatalogView {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * The catalog's id.
     *
     * @return its id, its file stem
     */
    public String id() {
        return catalog.id();
    }
}
