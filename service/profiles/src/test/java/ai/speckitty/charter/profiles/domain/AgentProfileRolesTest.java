package ai.speckitty.charter.profiles.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0069: a profile's primary role and alt-roles, and role fit; ADR-0068: the reserved role generic. */
@DisplayName("AgentProfile role and alt-roles (ADR-0069)")
class AgentProfileRolesTest {

    private static final PackIdentifier PACK = new PackIdentifier("built-in");

    private static AgentProfile.Builder base(String id) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(PACK)
                .name("Name of " + id)
                .purpose("Purpose of " + id)
                .specialization(new Specialization("Focus", Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Test
    @DisplayName("role is the primary role; alt-roles keep their authored order")
    void roleAndAltRoles() {
        AgentProfile pedro = base("python-pedro")
                .role("implementer")
                .altRole("reviewer")
                .altRole("documentarian")
                .build();

        assertThat(pedro.role()).isEqualTo("implementer");
        assertThat(pedro.altRoles()).containsExactly("reviewer", "documentarian");
        assertThat(base("renata").role("reviewer").build().altRoles()).isEmpty();
    }

    @Test
    @DisplayName("a primary-role fit outranks an alt-role fit, which outranks no fit")
    void roleFitRanking() {
        AgentProfile pedro =
                base("python-pedro").role("implementer").altRole("reviewer").build();
        AgentProfile renata = base("reviewer-renata").role("reviewer").build();

        assertThat(renata.roleFit("reviewer")).isEqualTo(RoleFit.PRIMARY);
        assertThat(pedro.roleFit("reviewer")).isEqualTo(RoleFit.ALT);
        assertThat(pedro.roleFit("architect")).isEqualTo(RoleFit.NONE);
    }

    @Test
    @DisplayName("a profile whose role is the reserved generic fits no role, not even generic (ADR-0068 decision 4)")
    void genericFitsNoRole() {
        AgentProfile fallback =
                base("generic-agent").role(AgentProfile.GENERIC_ROLE).build();

        assertThat(fallback.roleFit("implementer")).isEqualTo(RoleFit.NONE);
        assertThat(fallback.roleFit("generic")).isEqualTo(RoleFit.NONE);
        assertThat(base("renata").role("reviewer").build().roleFit("generic")).isEqualTo(RoleFit.NONE);
    }

    @Test
    @DisplayName("each malformed declaration is refused, while its well-formed control builds")
    void refusals() {
        assertThat(base("ok").role("implementer").altRole("reviewer").build()).isNotNull();
        assertThat(base("cap").role("r".repeat(64)).build().role()).hasSize(64);
        record Case(String expected, AgentProfile.Builder builder) {}
        List<Case> cases = List.of(
                new Case(
                        AgentProfile.ALT_ROLES_DISTINCT,
                        base("dup").role("implementer").altRole("implementer")),
                new Case(
                        AgentProfile.ALT_ROLES_DISTINCT,
                        base("dup2").role("a").altRole("b").altRole("b")),
                new Case(
                        AgentProfile.ALT_ROLES_NOT_GENERIC,
                        base("generic-alt").role("reviewer").altRole("generic")),
                new Case("lowercase letters, digits and hyphens", base("upper").role("Implementer")),
                new Case(
                        "lowercase letters, digits and hyphens",
                        base("underscore").role("qa_lead")),
                new Case("at most 64", base("long").role("r".repeat(65))));
        for (Case c : cases) {
            assertThatThrownBy(() -> c.builder().build())
                    .as(c.expected())
                    .isInstanceOf(ConstraintBreachException.class)
                    .hasMessageContaining(c.expected());
        }
        assertThatThrownBy(() -> base("none").build()).isInstanceOf(NullPointerException.class);
    }
}
