package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusalOf;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The registration impact check of IC-15 (FR-025, FR-027) and the hand-off rulings WP14's review left to it. Every refusal
 * is paired with a positive control on the same fixture, and a refusal is shown to change nothing on disk.
 */
class RegistrationImpactTest {

    static final String BASE_ID = PackRegistrationServiceTest.ulid('E');
    static final String DERIVED_ID = PackRegistrationServiceTest.ulid('F');
    private static final String DEPENDS_ON_BASE = "depends_on:\n  - pack_id: " + BASE_ID + "\n    range: '>=1.0'\n";
    private static final String POLICY = "schema_version: 1\nartifacts:\n  directive:LOCKED_ONE: locked\n";

    /** The pack the others build on: it locks one of its directives. */
    static Packs base(String version) {
        return Packs.of(BASE_ID, "base", version, "org", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", POLICY)
                .directive("LOCKED_ONE", "Locked")
                .directive("OPEN_ONE", "Open");
    }

    static Packs derived(String version) {
        return Packs.of(DERIVED_ID, "derived", version, "project", DEPENDS_ON_BASE)
                .directive("DERIVED_ONE", "Own");
    }

    // ---- q4b: a twin-declared id with one unreadable copy (WP14 review cycle 4) ------------------------------------

    @Test
    void anIdDeclaredByTwoFilesOneOfWhichCannotBeReadIsNeverKeptByteIdentical(@TempDir Path host) {
        Rig rig = new Rig(host.resolve("locked"));
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        String unreadableTwin = Packs.directiveText("LOCKED_ONE", "Mine") + "references:\n  - directive:X\n";
        rig.install(
                "derived",
                derived("1.0.0")
                        .file("directives/TWIN.directive.yaml", unreadableTwin)
                        .directive("LOCKED_ONE", "Mine"));
        Map<String, String> before = rig.filesOf(rig.packs.resolve("derived"));

        assertThat(refusalOf(() -> rig.register(
                        "derived", rig.stage(derived("2.0.0").directive("LOCKED_ONE", "Mine")), Preconditions.none())))
                .as("one copy of LOCKED_ONE cannot be read, so the readable copy is no proof the id is unchanged")
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(rig.filesOf(rig.packs.resolve("derived"))).as("nothing changed").isEqualTo(before);

        Rig control = new Rig(host.resolve("open"));
        control.register("base", control.stage(base("1.0.0")), Preconditions.none());
        control.install(
                "derived",
                derived("1.0.0")
                        .file(
                                "directives/TWIN.directive.yaml",
                                Packs.directiveText("OPEN_ONE", "Mine") + "references:\n  - directive:X\n")
                        .directive("OPEN_ONE", "Mine"));
        assertThat(control.register(
                                "derived",
                                control.stage(derived("2.0.0").directive("OPEN_ONE", "Mine")),
                                Preconditions.none())
                        .outcome())
                .as("the control: the same twins over an open id")
                .isEqualTo(Outcome.REPLACED);
    }
}
