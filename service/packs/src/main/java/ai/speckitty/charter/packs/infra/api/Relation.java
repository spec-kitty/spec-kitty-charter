package ai.speckitty.charter.packs.infra.api;

/**
 * The relation keys this service puts in {@code _links} (ADR-0019 D3), each with its wire key. Every link the service
 * builds is keyed by one of these; the set is checked against the relation table of
 * {@code docs/development/hypermedia-hateoas.md} by {@code RelationSetParityTest} in {@code :deployable}, which is why
 * the enum and its wire key are public.
 */
public enum Relation {
    /** The resource itself. */
    SELF("self"),

    /** The collection the resource belongs to: an artifact's kind list. */
    COLLECTION("collection"),

    /** The service info. */
    INFO("info"),

    /** The configured packs in force. */
    OFFERING("offering"),

    /** A pack's artifact list, or its template. */
    ARTIFACTS("artifacts"),

    /** The contract document (RFC 8631). */
    SERVICE_DESC("service-desc"),

    /** The first page of a paged list. */
    FIRST("first"),

    /** The next page of a paged list, absent on the last. */
    NEXT("next");

    private final String wireKey;

    Relation(String wireKey) {
        this.wireKey = wireKey;
    }

    /**
     * The key of the relation in {@code _links}.
     *
     * @return the wire key, for example {@code service-desc}
     */
    public String wireKey() {
        return wireKey;
    }
}
