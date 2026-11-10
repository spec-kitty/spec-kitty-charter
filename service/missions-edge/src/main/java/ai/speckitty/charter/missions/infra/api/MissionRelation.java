package ai.speckitty.charter.missions.infra.api;

/**
 * The link relations the mission-type reads build their links with (#99), each spelled by its wire key. Every wire key
 * is a row of the relation table of {@code docs/development/hypermedia-hateoas.md}, which a parity test checks. The
 * missions slice keeps its own relations: it may not name the packs slice's inbound adapter.
 */
public enum MissionRelation {
    /** The resource itself. */
    SELF("self"),
    /** The pack's artifacts of the resource's kind. */
    COLLECTION("collection"),
    /** The mission type of a step or a skeleton. */
    MISSION_TYPE("mission-type"),
    /** The step contract bound to a step. */
    STEP_CONTRACT("step-contract"),
    /** The stored skeleton an expected output artifact of a step names. */
    SKELETON("skeleton"),
    /** The pack-scoped read of the winner a read under an activation answers: its mission type in the pack that holds it. */
    ARTIFACT("artifact");

    private final String wireKey;

    MissionRelation(String wireKey) {
        this.wireKey = wireKey;
    }

    /**
     * The relation's key in {@code _links}.
     *
     * @return the wire key, for example {@code mission-type}
     */
    public String wireKey() {
        return wireKey;
    }
}
