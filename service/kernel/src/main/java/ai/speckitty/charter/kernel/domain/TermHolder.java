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
}
