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
 * The role-pin rule of ruling D3 at registration: each pin of a pack's {@code policy.yaml} names a profile its closure
 * holds and that fits the pinned role, a human-performed one included, and no two packs of a closure pin one role to
 * different profiles. Each refusal is 422 with a detail naming the closure, the pack, the role and the profile, and
 * stores nothing; each is paired with a passing control.
 */
class RolePinImpactTest {

    private static final String ORG_ID = PackRegistrationServiceTest.ulid('P');
    private static final String PROJECT_ID = PackRegistrationServiceTest.ulid('Q');
    private static final String NAMES_POLICY = "mutability_policy: policy.yaml\n";
    private static final String DEPENDS_ON_ORG = "depends_on:\n  - pack_id: " + ORG_ID + "\n    range: '>=1.0'\n";

    private static String profile(String id, String roleLines) {
        return "profile-id: " + id + "\nname: " + id + "\n" + roleLines
                + "purpose: Do the work of the role.\nspecialization:\n  primary-focus: The role\n";
    }

    private static String pins(String... rolesThenProfiles) {
        StringBuilder text = new StringBuilder("schema_version: 1\nrole_pins:\n");
        for (int i = 0; i < rolesThenProfiles.length; i += 2) {
            text.append("  ")
                    .append(rolesThenProfiles[i])
                    .append(": ")
                    .append(rolesThenProfiles[i + 1])
                    .append('\n');
        }
        return text.toString();
    }

    /** An org pack with two reviewers, one that can also implement, an implementer and a human operator. */
    private static Packs org(String version, String descriptor) {
        return Packs.of(ORG_ID, "org-base", version, "org", descriptor)
                .file(
                        "agent_profiles/reviewer-renata.agent.yaml",
                        profile("reviewer-renata", "role: reviewer\nalt-roles: [implementer]\n"))
                .file("agent_profiles/reviewer-rhea.agent.yaml", profile("reviewer-rhea", "role: reviewer\n"))
                .file("agent_profiles/implementer-ivan.agent.yaml", profile("implementer-ivan", "role: implementer\n"))
                .file(
                        "agent_profiles/human-operator.agent.yaml",
                        profile("human-operator", "role: operator\nperformer: human\n"));
    }

    private static Packs project(String policy) {
        return Packs.of(PROJECT_ID, "project", "1.0.0", "project", DEPENDS_ON_ORG + NAMES_POLICY)
                .file("policy.yaml", policy);
    }

    private static Rig withOrg(Path host) {
        Rig rig = new Rig(host);
        rig.register("org-base", rig.stage(org("1.0.0", "")), Preconditions.none());
        return rig;
    }

    @Test
    void aPinToAProfileTheClosureHoldsThatFitsTheRoleIsStored(@TempDir Path host) {
        Rig rig = withOrg(host);

        assertThat(rig.register(
                                "project",
                                rig.stage(
                                        project(pins("reviewer", "reviewer-renata", "implementer", "reviewer-renata"))),
                                Preconditions.none())
                        .outcome())
                .as("reviewer is renata's role and implementer one of her alt-roles")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aPinToAProfileTheClosureDoesNotHoldIsRolePinUnknown(@TempDir Path host) {
        Rig rig = withOrg(host);

        CharterRefusal refused = refusal(() ->
                rig.register("project", rig.stage(project(pins("reviewer", "reviewer-rex"))), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.ROLE_PIN_UNKNOWN);
        assertThat(refused.getMessage())
                .contains("closure rooted at project")
                .contains("role pin reviewer: reviewer-rex of pack project");
        assertThat(rig.packs.resolve("project")).as("nothing was stored").doesNotExist();
    }

    @Test
    void aPinToAProfileThatDoesNotFitTheRoleIsRefusedAHumanOneIncluded(@TempDir Path host) {
        Rig rig = withOrg(host);

        CharterRefusal agent = refusal(() -> rig.register(
                "project", rig.stage(project(pins("reviewer", "implementer-ivan"))), Preconditions.none()));
        CharterRefusal human = refusal(() ->
                rig.register("project", rig.stage(project(pins("reviewer", "human-operator"))), Preconditions.none()));

        assertThat(agent.code()).isEqualTo(RefusalCode.ROLE_PIN_NOT_FITTING);
        assertThat(agent.getMessage())
                .contains("closure rooted at project")
                .contains("role pin reviewer: implementer-ivan of pack project")
                .contains("role implementer");
        assertThat(human.code())
                .as("a human-performed profile is judged by its role like any other (no human exemption)")
                .isEqualTo(RefusalCode.ROLE_PIN_NOT_FITTING);
        assertThat(human.getMessage()).contains("human-operator").contains("role operator");
        assertThat(rig.packs.resolve("project")).as("nothing was stored").doesNotExist();
    }

    @Test
    void aDifferingPinAnywhereInTheClosureIsRolePinConflict(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register(
                "org-base",
                rig.stage(org("1.0.0", NAMES_POLICY).file("policy.yaml", pins("implementer", "implementer-ivan"))),
                Preconditions.none());

        CharterRefusal refused = refusal(() -> rig.register(
                "project", rig.stage(project(pins("implementer", "reviewer-renata"))), Preconditions.none()));

        assertThat(refused.code())
                .as("both pins are valid alone; pins are tighten-only, so a derived pack cannot re-pin (ruling D3)")
                .isEqualTo(RefusalCode.ROLE_PIN_CONFLICT);
        assertThat(refused.getMessage())
                .contains("closure rooted at project")
                .contains("the role implementer")
                .contains("pack org-base pins it to implementer-ivan")
                .contains("pack project pins it to reviewer-renata");
        assertThat(refused.packs()).extracting(RefusalPack::descriptorPackId).containsExactly(ORG_ID, PROJECT_ID);
        assertThat(rig.packs.resolve("project")).as("nothing was stored").doesNotExist();
    }

    @Test
    void aWriteToADependencyThatAddsADifferingPinIsRefusedForTheClosureOfThePackThatBuildsOnIt(@TempDir Path host) {
        Rig rig = withOrg(host);
        rig.register("project", rig.stage(project(pins("reviewer", "reviewer-renata"))), Preconditions.none());

        CharterRefusal refused = refusal(() -> rig.register(
                "org-base",
                rig.stage(org("1.1.0", NAMES_POLICY).file("policy.yaml", pins("reviewer", "reviewer-rhea"))),
                Preconditions.none()));

        assertThat(refused.code())
                .as("org-base's pin is valid alone; the project that builds on it pins reviewer to renata")
                .isEqualTo(RefusalCode.ROLE_PIN_CONFLICT);
        assertThat(refused.getMessage()).contains("closure rooted at project");
        assertThat(rig.register(
                                "org-base",
                                rig.stage(org("1.1.0", NAMES_POLICY)
                                        .file("policy.yaml", pins("reviewer", "reviewer-renata"))),
                                Preconditions.none())
                        .outcome())
                .as("the control: the dependency pins the role as the project does")
                .isEqualTo(Outcome.REPLACED);
    }
}
