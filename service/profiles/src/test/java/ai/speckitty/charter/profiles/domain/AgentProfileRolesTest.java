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
        assertThat(RoleFit.PRIMARY.outranks(RoleFit.ALT)).isTrue();
        assertThat(RoleFit.ALT.outranks(RoleFit.NONE)).isTrue();
        assertThat(RoleFit.ALT.outranks(RoleFit.PRIMARY)).isFalse();
        assertThat(RoleFit.PRIMARY.outranks(RoleFit.PRIMARY)).isFalse();
        assertThat(RoleFit.NONE.fits()).isFalse();
        assertThat(RoleFit.ALT.fits()).isTrue();
    }

    @Test
    @DisplayName("a profile whose role is the reserved generic fits no role, not even generic (ADR-0068 decision 4)")
    void genericFitsNoRole() {
        AgentProfile fallback =
                base("generic-agent").role(AgentProfile.GENERIC_ROLE).build();

        assertThat(fallback.roleFit("implementer")).isEqualTo(RoleFit.NONE);
        assertThat(fallback.roleFit("generic")).isEqualTo(RoleFit.NONE);
    }

    @Test
    @DisplayName("the reserved role operator is taken only by a human-performed profile, as role or as alt-role")
    void operatorIsHumanOnly() {
        AgentProfile human = base("human-operator")
                .role(AgentProfile.OPERATOR_ROLE)
                .performer(Optional.of(Performer.HUMAN))
                .build();
        AgentProfile humanAlt = base("lead")
                .role("reviewer")
                .altRole(AgentProfile.OPERATOR_ROLE)
                .performer(Optional.of(Performer.HUMAN))
                .build();

        assertThat(human.humanPerformed()).isTrue();
        assertThat(humanAlt.roleFit(AgentProfile.OPERATOR_ROLE)).isEqualTo(RoleFit.ALT);
        for (AgentProfile.Builder agent : List.of(
                base("silent").role(AgentProfile.OPERATOR_ROLE),
                base("agent").role(AgentProfile.OPERATOR_ROLE).performer(Optional.of(Performer.AGENT)),
                base("agent-alt").role("reviewer").altRole(AgentProfile.OPERATOR_ROLE))) {
            assertThatThrownBy(agent::build)
                    .isInstanceOf(ConstraintBreachException.class)
                    .hasMessageContaining(AgentProfile.OPERATOR_ROLE_HUMAN_ONLY);
        }
    }

    @Test
    @DisplayName("a performer token reads back as its performer, and an unknown token as none")
    void performerTokens() {
        assertThat(Performer.fromToken("agent")).contains(Performer.AGENT);
        assertThat(Performer.fromToken("human")).contains(Performer.HUMAN);
        assertThat(Performer.HUMAN.token()).isEqualTo("human");
        assertThat(Performer.fromToken("robot")).isEmpty();
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
                new Case("letter-first kebab slugs", base("upper").role("Implementer")),
                new Case("letter-first kebab slugs", base("underscore").role("qa_lead")),
                new Case("at most 64", base("long").role("r".repeat(65))),
                new Case("letter-first kebab slugs", base("doubled").role("qa--lead")),
                new Case(
                        "letter-first kebab slugs",
                        base("trailing").role("reviewer").altRole("qa-")));
        for (Case c : cases) {
            assertThatThrownBy(() -> c.builder().build())
                    .as(c.expected())
                    .isInstanceOf(ConstraintBreachException.class)
                    .hasMessageContaining(c.expected());
        }
        assertThatThrownBy(() -> base("none").build()).isInstanceOf(NullPointerException.class);
    }
}
