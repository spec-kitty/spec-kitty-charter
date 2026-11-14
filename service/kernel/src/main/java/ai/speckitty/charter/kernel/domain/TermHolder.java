package ai.speckitty.charter.kernel.domain;

/**
 * An artifact that holds terms (ADR-0049 decision 3): a glossary context, whose terms are its items, addressed
 * {@code term:<context-id>/<term-id>}. The kernel names the fact so a slice can judge whether a term is held, such as
 * the term read under an activation, without depending on the slice that owns the artifact, as {@link RoleBearer} does
 * for roles.
 */
public interface TermHolder {

    /**
     * Whether the artifact holds a term of an id.
     *
     * @param termId the term's id, as a request names it; an id outside the term grammar names no term
     * @return {@code true} when it holds a term of that id
     */
    boolean holdsTerm(String termId);

    /**
     * The detail of the refusal of a glossary context no pack, or no merge, holds: one text for the pack read and the
     * read under an activation (ruling {@code DM-01M7B1Q4BPQB95EVQBRWN4Q4F8}), naming only the id the request sent.
     *
     * @param contextId the context's id, as the request names it
     * @return {@code glossary context `<contextId>` is not held}
     */
    static String contextNotHeld(String contextId) {
        return "glossary context `" + contextId + "` is not held";
    }

    /**
     * The detail of the refusal of a term its context does not hold: one text for the pack read and the read under an
     * activation (ruling {@code DM-01M7B1Q4BPQB95EVQBRWN4Q4F8}), naming only the ids the request sent.
     *
     * @param contextId the context's id, as the request names it
     * @param termId the term's id, as the request names it
     * @return {@code term `<termId>` is not held in glossary context `<contextId>`}
     */
    static String termNotHeld(String contextId, String termId) {
        return "term `" + termId + "` is not held in glossary context `" + contextId + "`";
    }
}
