package ai.speckitty.charter.profiles.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0069: a profile's primary role and alt-roles, the deprecated positional roles list, and role fit. */
@DisplayName("AgentProfile roles (ADR-0069)")
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
    @DisplayName("role and alt-roles: primary first, alt-roles in authored order")
    void newShape() {
        AgentProfile pedro =
                base("python-pedro").primaryRole("implementer").altRole("reviewer").build();

        assertThat(pedro.role()).isEqualTo("implementer");
        assertThat(pedro.altRoles()).containsExactly("reviewer");
        assertThat(pedro.roles()).containsExactly("implementer", "reviewer");
        assertThat(pedro.declaresLegacyRoles()).isFalse();
        assertThat(pedro.legacyRoles()).isEmpty();
    }

    @Test
    @DisplayName("the deprecated roles list reads as role = first entry, alt-roles = the rest, each once")
    void legacyShapeReadsAsPrimaryAndAlts() {
        AgentProfile legacy = base("old")
                .role("implementer")
                .role("reviewer")
                .role("implementer")
                .role("reviewer")
                .build();

        assertThat(legacy.declaresLegacyRoles()).isTrue();
        assertThat(legacy.role()).isEqualTo("implementer");
        assertThat(legacy.altRoles()).containsExactly("reviewer");
        assertThat(legacy.legacyRoles()).containsExactly("implementer", "reviewer", "implementer", "reviewer");
    }

    @Test
    @DisplayName("a primary-role match outranks an alt-role match, which outranks no match")
    void roleFitRanking() {
        AgentProfile pedro =
                base("python-pedro").primaryRole("implementer").altRole("reviewer").build();
        AgentProfile renata = base("reviewer-renata").primaryRole("reviewer").build();

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
    @DisplayName("each malformed declaration is refused, while its well-formed control builds")
    void refusals() {
        assertThat(base("ok").primaryRole("implementer").altRole("reviewer").build())
                .isNotNull();
        record Case(String expected, AgentProfile.Builder builder) {}
        List<Case> cases = List.of(
                new Case("needs a role", base("none")),
                new Case("never both shapes", base("both").primaryRole("implementer").role("reviewer")),
                new Case("alt-roles need a role", base("alts-on-legacy").role("implementer").altRole("reviewer")),
                new Case("must not repeat its role", base("dup").primaryRole("implementer").altRole("implementer")),
                new Case("must be distinct", base("dup2").primaryRole("a").altRole("b").altRole("b")),
                new Case("lowercase letters", base("bad").primaryRole("Implementer")),
                new Case("at most 64", base("long").primaryRole("r".repeat(65))));
        for (Case c : cases) {
            assertThatThrownBy(() -> c.builder().build())
                    .as(c.expected())
                    .isInstanceOf(ConstraintBreachException.class)
                    .hasMessageContaining(c.expected());
        }
        assertThat(base("cap").primaryRole("r".repeat(64)).build().role()).hasSize(64);
    }
}
