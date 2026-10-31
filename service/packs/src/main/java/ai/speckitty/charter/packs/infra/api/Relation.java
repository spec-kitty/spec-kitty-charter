package ai.speckitty.charter.packs.infra.api;

/**
 * The relation keys this service puts in {@code _links} (ADR-0019 D3), each with its wire key. Every link the service
 * builds is keyed by one of these; the set is checked against the relation table of
 * {@code docs/development/hypermedia-hateoas.md} by {@code RelationSetParityTest} in {@code :deployable}, which is why
 * the enum and its wire key are public.
 */
public enum Relation {
    SELF("self"),
    COLLECTION("collection"),
    INFO("info"),
    OFFERING("offering"),
    ARTIFACTS("artifacts"),
    SERVICE_DESC("service-desc"),
    FIRST("first"),
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
