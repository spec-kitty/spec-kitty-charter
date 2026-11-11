package ai.speckitty.charter.kernel.domain;

import java.util.List;

/**
 * An artifact that fills roles (ADR-0069): one primary role and optional alt-roles. The kernel names the facts so a
 * slice can judge a role rule, such as the fallback-profile rule (ADR-0068), without depending on the slice that owns
 * the artifact.
 */
public interface RoleBearer {

    /** The reserved role of a fallback profile (ADR-0068 decision 4); it fits no role a step asks for. */
    String GENERIC = "generic";

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
}
