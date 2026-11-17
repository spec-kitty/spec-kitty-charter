package ai.speckitty.charter.packs.infra.storage.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The store half's faults placed and stated once (#554, WP12, R2 row 19): the plain functions of {@link AssemblyRules}. */
@DisplayName("AssemblyRules: the store half's own faults (#554, WP12)")
class AssemblyRulesTest {

    @Test
    @DisplayName("a declared path resolves beside the file that declares it, . and .. resolved")
    void aDeclaredPathResolvesBesideItsFile() {
        assertThat(AssemblyRules.sibling("toolguides/a.toolguide.yaml", "a.guide.md"))
                .isEqualTo("toolguides/a.guide.md");
        assertThat(AssemblyRules.sibling("missions/x/x.mission-type.yaml", "./steps/../steps/a.prompt.md"))
                .isEqualTo("missions/x/steps/a.prompt.md");
        assertThat(AssemblyRules.sibling("a.yaml", "b.md")).isEqualTo("b.md");
        assertThat(AssemblyRules.sibling("a.yaml", "../out.md")).isEqualTo("../out.md");
        assertThat(AssemblyRules.sibling("d/a.yaml", "../../out.md")).isEqualTo("../out.md");
    }

    @Test
    @DisplayName("a companion fault moves to the companion's file with its reason, unless either file is stated")
    void aCompanionFaultIsPlacedAtTheCompanion() {
        PackElementViolation missing =
                new PackElementViolation("toolguides/a.toolguide.yaml", StorageCodes.COMPANION, "a.guide.md: missing");

        assertThat(AssemblyRules.companionFault(missing, Set.of())).hasValueSatisfying(fault -> {
            assertThat(fault.code()).isEqualTo(StorageCodes.COMPANION);
            assertThat(fault.location()).isEqualTo(Location.in("toolguides/a.guide.md"));
            assertThat(fault.detail()).isEqualTo("missing");
        });
        assertThat(AssemblyRules.companionFault(missing, Set.of("toolguides/a.guide.md")))
                .isEmpty();
        assertThat(AssemblyRules.companionFault(missing, Set.of("toolguides/a.toolguide.yaml")))
                .isEmpty();
    }

    @Test
    @DisplayName("a companion fault with no declared path stays at its file")
    void aCompanionFaultWithoutAPathStays() {
        PackElementViolation odd = new PackElementViolation("a.yaml", StorageCodes.COMPANION, "named by two");

        assertThat(AssemblyRules.companionFault(odd, Set.of())).hasValueSatisfying(fault -> {
            assertThat(fault.location()).isEqualTo(Location.in("a.yaml"));
            assertThat(fault.detail()).isEqualTo("named by two");
        });
    }
}
