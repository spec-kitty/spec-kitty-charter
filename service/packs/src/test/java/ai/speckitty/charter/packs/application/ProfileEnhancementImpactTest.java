package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile enhancement rule of rulings D5 and D7 at registration (#380): an agent profile that enhances another may
 * add to it, never drop part of a field it declares. Each refusal is 422 {@code profile_enhancement_drops_field}, names
 * the enhancing pack first and the field, and stores nothing; an enhancer that only adds is the control.
 */
class ProfileEnhancementImpactTest {

    private static final String ORG_ID = PackRegistrationServiceTest.ulid('E');
    private static final String PROJECT_ID = PackRegistrationServiceTest.ulid('F');
    private static final String DEPENDS_ON_ORG = "depends_on:\n  - pack_id: " + ORG_ID + "\n    range: '>=1.0'\n";

    private static final String BASE = """
            profile-id: reviewer-renata
            name: Reviewer Renata
            description: Quality assurance
            role: reviewer
            capabilities: [code-review, security-audit]
            purpose: Review the work.
            specialization:
              primary-focus: Code quality
              secondary-awareness: Architectural constraints
            """;

    private static String enhancer(String id, String body) {
        return "profile-id: " + id + "\nname: Reviewer Renata of the org\nrole: reviewer\n"
                + "purpose: Review the work as the org does.\n" + body;
    }

    private static final String SAME_SPECIALIZATION =
            "specialization:\n  primary-focus: Code quality\n  secondary-awareness: Architectural constraints\n";

    private static String enhances(String source, String target) {
        return "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n- {source: 'agent_profile:" + source
                + "', target: '" + target + "', relation: enhances}\n";
    }

    private static Packs org(String enhancerBody) {
        return Packs.of(ORG_ID, "org-base", "1.0.0", "org", "")
                .file("agent_profiles/reviewer-renata.agent.yaml", BASE)
                .file("agent_profiles/reviewer-renata-org.agent.yaml", enhancer("reviewer-renata-org", enhancerBody))
                .file("enhances.graph.yaml", enhances("reviewer-renata-org", "agent_profile:reviewer-renata"));
    }

    private static CharterRefusal refusedOrg(Rig rig, String enhancerBody) {
        CharterRefusal refused =
                refusal(() -> rig.register("org-base", rig.stage(org(enhancerBody)), Preconditions.none()));
        assertThat(rig.packs.resolve("org-base")).as("nothing was stored").doesNotExist();
        return refused;
    }

    private static void assertDrops(CharterRefusal refused, String field) {
        assertThat(refused.code()).isEqualTo(RefusalCode.PROFILE_ENHANCEMENT_DROPS_FIELD);
        assertThat(refused.getMessage())
                .contains("closure rooted at org-base")
                .contains("reviewer-renata-org of pack org-base enhances reviewer-renata")
                .contains(field);
        assertThat(refused.packs())
                .extracting(RefusalPack::descriptorPackId)
                .first()
                .isEqualTo(ORG_ID);
    }

    @Test
    void aSpecializationThatLeavesOutTheBasesIsRefusedNamingThePackAndTheField(@TempDir Path host) {
        Rig rig = new Rig(host);

        assertDrops(refusedOrg(rig, "specialization:\n  primary-focus: Code quality\n"), "specialization");
    }

    @Test
    void aListItemDroppedIsRefused(@TempDir Path host) {
        Rig rig = new Rig(host);

        assertDrops(refusedOrg(rig, SAME_SPECIALIZATION + "capabilities: [code-review]\n"), "capabilities");
    }

    @Test
    void aScalarChangedIsRefused(@TempDir Path host) {
        Rig rig = new Rig(host);

        assertDrops(refusedOrg(rig, SAME_SPECIALIZATION + "description: Another text\n"), "description");
    }

    @Test
    void anEnhancerThatDeclaresExcludingIsRefused(@TempDir Path host) {
        Rig rig = new Rig(host);

        CharterRefusal refused = refusedOrg(rig, SAME_SPECIALIZATION + "excluding:\n  - capabilities\n");

        assertDrops(refused, "excluding");
        assertThat(refused.getMessage()).contains("declares excluding");
    }

    @Test
    void anEnhancerThatOnlyAddsIsStored(@TempDir Path host) {
        Rig rig = new Rig(host);

        assertThat(rig.register(
                                "org-base",
                                rig.stage(org(SAME_SPECIALIZATION
                                        + "description: Quality assurance\n"
                                        + "capabilities: [security-audit, code-review, threat-modelling]\n"
                                        + "model: a-model\n")),
                                Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void anEnhancerOfADependencysProfileIsJudgedInTheDerivedPackAndNamesItFirst(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register(
                "org-base",
                rig.stage(Packs.of(ORG_ID, "org-base", "1.0.0", "org", "")
                        .file("agent_profiles/reviewer-renata.agent.yaml", BASE)),
                Preconditions.none());
        Packs project = Packs.of(PROJECT_ID, "project", "1.0.0", "project", DEPENDS_ON_ORG)
                .file(
                        "agent_profiles/reviewer-renata-project.agent.yaml",
                        enhancer("reviewer-renata-project", SAME_SPECIALIZATION + "capabilities: [code-review]\n"))
                .file(
                        "enhances.graph.yaml",
                        enhances("reviewer-renata-project", "org-base::agent_profile:reviewer-renata"));

        CharterRefusal refused = refusal(() -> rig.register("project", rig.stage(project), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.PROFILE_ENHANCEMENT_DROPS_FIELD);
        assertThat(refused.getMessage())
                .contains("closure rooted at project")
                .contains("of pack project enhances reviewer-renata")
                .contains("capabilities");
        assertThat(refused.packs())
                .extracting(RefusalPack::descriptorPackId)
                .first()
                .isEqualTo(PROJECT_ID);
        assertThat(rig.packs.resolve("project")).as("nothing was stored").doesNotExist();
    }
}
