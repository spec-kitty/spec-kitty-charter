package ai.speckitty.charter.kernel.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An artifact that fills roles (ADR-0069): one primary role and optional alt-roles. The kernel names the facts so a
 * slice can judge a role rule, such as the fallback-profile rule (ADR-0068), without depending on the slice that owns
 * the artifact.
 */
public interface RoleBearer {

    /** The reserved role of a fallback profile (ADR-0068 decision 4); it fits no role a step asks for. */
    String GENERIC = "generic";

    /** The longest role token, the cap of an identifier (#446). */
    int ROLE_MAX_LENGTH = IdentifierGrammar.MAX_SLUG_LENGTH;

    /**
     * Whether a text is a role token: a {@link IdentifierGrammar#LETTER_FIRST_SLUG letter-first kebab slug} of at most
     * {@value #ROLE_MAX_LENGTH} characters (ADR-0075 decision 2). It is the one grammar of every role: a profile's role
     * and alt-roles, a mission step's and a procedure step's role, and a role pin. It says nothing of the reserved
     * {@value #GENERIC}, which each site rules on itself.
     *
     * @param text the candidate, possibly {@code null}
     * @return {@code true} when it is
     */
    static boolean isRole(@Nullable String text) {
        return IdentifierGrammar.isLetterFirstSlug(text);
    }

    /**
     * The primary role.
     *
     * @return the role
     */
    String role();

    /**
     * The alt-roles, in authored order.
     *
     * @return the alt-roles; empty when there are none
     */
    List<String> altRoles();

    /**
     * Whether a human carries out the artifact rather than an agent. An artifact that declares no performer is
     * agent-performed.
     *
     * @return {@code true} only when the artifact declares a human performer
     */
    boolean humanPerformed();
}
