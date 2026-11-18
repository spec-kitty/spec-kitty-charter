package ai.speckitty.charter.kernel.domain.kinds;

/**
 * The {@link ai.speckitty.charter.kernel.domain.NodeKind#ROUTING_CATALOG} kind, as a type (ADR-0080). It is never
 * instantiated, and it is in no group: no relation admits a catalog at either end.
 */
public final class RoutingCatalog implements Kind {

    private RoutingCatalog() {}
}
