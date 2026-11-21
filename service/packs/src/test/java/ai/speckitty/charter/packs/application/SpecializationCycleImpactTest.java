package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The specialization rule of ruling D2 at registration: the {@code specializes_from} edges among a closure's agent
 * profiles form no cycle. The refusal is 422, names the cycle's profile ids in order and the pack that writes it, and
 * stores nothing; an acyclic lineage of the same profiles is the control.
 */
class SpecializationCycleImpactTest {

    private static final String ORG_ID = PackRegistrationServiceTest.ulid('T');

    private static String profile(String id) {
        return "profile-id: " + id + "\nname: " + id + "\nrole: implementer\n"
                + "purpose: Implement.\nspecialization:\n  primary-focus: Code\n";
    }

    private static String lineage(String... childThenParent) {
        StringBuilder text = new StringBuilder("schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n");
        for (int i = 0; i < childThenParent.length; i += 2) {
            text.append("- {source: 'agent_profile:")
                    .append(childThenParent[i])
                    .append("', target: 'agent_profile:")
                    .append(childThenParent[i + 1])
                    .append("', relation: specializes_from}\n");
        }
        return text.toString();
    }

    private static Packs org(String version, String graph) {
        return Packs.of(ORG_ID, "org-base", version, "org", "")
                .file("agent_profiles/implementer-ivan.agent.yaml", profile("implementer-ivan"))
                .file("agent_profiles/java-jenny.agent.yaml", profile("java-jenny"))
                .file("agent_profiles/spring-sam.agent.yaml", profile("spring-sam"))
                .file("lineage.graph.yaml", graph);
    }

    @Test
    void aCycleOfSpecializesFromEdgesIsRefusedNamingItsProfilesInOrder(@TempDir Path host) {
        Rig rig = new Rig(host);

        CharterRefusal refused = refusal(() -> rig.register(
                "org-base",
                rig.stage(org(
                        "1.0.0",
                        lineage(
                                "spring-sam", "java-jenny",
                                "java-jenny", "implementer-ivan",
                                "implementer-ivan", "spring-sam"))),
                Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.SPECIALIZATION_CYCLE);
        assertThat(refused.getMessage())
                .contains("closure rooted at org-base")
                .contains("spring-sam -> java-jenny -> implementer-ivan -> spring-sam");
        assertThat(refused.packs())
                .extracting(pack -> pack.descriptorPackId().value())
                .containsExactly(ORG_ID);
        assertThat(rig.packs.resolve("org-base")).as("nothing was stored").doesNotExist();
    }

    @Test
    void twoSeparateCyclesAreTwoFaultsOfTheOneRefusal(@TempDir Path host) {
        Rig rig = new Rig(host);

        CharterRefusal refused = refusal(() -> rig.register(
                "org-base",
                rig.stage(org(
                                "1.0.0",
                                lineage(
                                        "spring-sam", "java-jenny",
                                        "java-jenny", "spring-sam",
                                        "implementer-ivan", "python-pedro",
                                        "python-pedro", "implementer-ivan"))
                        .file("agent_profiles/python-pedro.agent.yaml", profile("python-pedro"))),
                Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.SPECIALIZATION_CYCLE);
        assertThat(refused.getMessage()).endsWith("(2 fault(s); every one is in violations)");
        assertThat(refused.violations())
                .extracting(violation -> violation.code())
                .containsExactly("specialization_cycle", "specialization_cycle");
        assertThat(refused.violations().get(0).detail()).contains("spring-sam -> java-jenny -> spring-sam");
        assertThat(refused.violations().get(1).detail())
                .contains("implementer-ivan -> python-pedro -> implementer-ivan");
    }

    @Test
    void anAcyclicLineageOfTheSameProfilesIsStored(@TempDir Path host) {
        Rig rig = new Rig(host);

        assertThat(rig.register(
                                "org-base",
                                rig.stage(org(
                                        "1.0.0",
                                        lineage("spring-sam", "java-jenny", "java-jenny", "implementer-ivan"))),
                                Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
    }
}
