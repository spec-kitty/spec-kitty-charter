package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RoleBearer;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Specialization;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fallback-profile rule of ADR-0068 at registration: the profile a pack names in {@code fallback_profile} is held in
 * the pack's closure, locked along it, and declares the reserved role {@code generic} and no alt-roles. Every refusal
 * is paired with a passing control on the same fixture, and a refusal stores nothing.
 */
class FallbackProfileImpactTest {

    private static final String ORG_ID = PackRegistrationServiceTest.ulid('R');
    private static final String PROJECT_ID = PackRegistrationServiceTest.ulid('S');
    private static final String LOCK_GENERIC = "schema_version: 1\nartifacts:\n  agent_profile:generic-agent: locked\n";
    private static final String DECLARES = "mutability_policy: policy.yaml\nfallback_profile: generic-agent\n";

    private static String profile(String id, String roleLines) {
        return "profile-id: " + id + "\nname: " + id + "\n" + roleLines
                + "purpose: Serve when no profile fits.\nspecialization:\n  primary-focus: Anything\n";
    }

    /** An org pack that declares a locked, role-neutral fallback: the passing shape. */
    private static Packs org(String fallbackRole, String policy) {
        return Packs.of(ORG_ID, "org-base", "1.0.0", "org", DECLARES)
                .file("policy.yaml", policy)
                .file("agent_profiles/generic-agent.agent.yaml", profile("generic-agent", fallbackRole));
    }

    @Test
    void aDerivedPackMayNameAFallbackItsClosureHoldsButNotOneOutsideIt(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("org-base", rig.stage(org("role: generic\n", LOCK_GENERIC)), Preconditions.none());
        String dependsOnOrg = "depends_on:\n  - pack_id: " + ORG_ID + "\n    range: '>=1.0'\n";
        Packs derived =
                Packs.of(PROJECT_ID, "project", "1.0.0", "project", dependsOnOrg + "fallback_profile: generic-agent\n");
        Packs standalone = Packs.of(PROJECT_ID, "project", "1.0.0", "project", "fallback_profile: generic-agent\n");

        assertThat(refusal(() -> rig.register("project", rig.stage(standalone), Preconditions.none()))
                        .code())
                .as("without the dependency the closure holds no generic-agent")
                .isEqualTo(RefusalCode.FALLBACK_PROFILE_UNKNOWN);
        assertThat(rig.register("project", rig.stage(derived), Preconditions.none())
                        .outcome())
                .as("the org's locked profile, named through the closure (ADR-0068 decision 2)")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aSingleArtifactWriteOfTheFallbackProfileIsJudgedAsWrittenNotAsStored(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("org-base", rig.stage(org("role: generic\n", LOCK_GENERIC)), Preconditions.none());
        PackIdentifier orgBase = new PackIdentifier("org-base");
        Pack stored = rig.repository.load(orgBase);

        AgentProfile implementer = agentProfile("implementer");
        CharterRefusal refused = refusal(() -> rig.writeStore.commit(
                orgBase, null, PackWriteStore.Change.artifact(stored.with(implementer), implementer)));

        assertThat(refused.code()).isEqualTo(RefusalCode.FALLBACK_PROFILE_NOT_ROLE_NEUTRAL);
        assertThat(rig.repository.load(orgBase).artifacts(ArtifactKind.AGENT_PROFILE))
                .as("nothing was stored")
                .singleElement()
                .isInstanceOfSatisfying(
                        RoleBearer.class, held -> assertThat(held.role()).isEqualTo("generic"));
        AgentProfile generic = agentProfile("generic");
        rig.writeStore.commit(orgBase, null, PackWriteStore.Change.artifact(stored.with(generic), generic));
    }

    @Test
    void aWriteToThePackHoldingTheFallbackIsJudgedForThePackThatDeclaresIt(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("org-base", rig.stage(org("role: generic\n", LOCK_GENERIC)), Preconditions.none());
        String dependsOnOrg = "depends_on:\n  - pack_id: " + ORG_ID + "\n    range: '>=1.0'\n";
        rig.register(
                "project",
                rig.stage(Packs.of(
                        PROJECT_ID, "project", "1.0.0", "project", dependsOnOrg + "fallback_profile: generic-agent\n")),
                Preconditions.none());
        String extendable = "schema_version: 1\nartifacts:\n  agent_profile:generic-agent: extendable\n";
        Packs unlocked = Packs.of(ORG_ID, "org-base", "1.1.0", "org", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", extendable)
                .file("agent_profiles/generic-agent.agent.yaml", profile("generic-agent", "role: generic\n"));

        CharterRefusal refused = refusal(() -> rig.register("org-base", rig.stage(unlocked), Preconditions.none()));

        assertThat(refused.code())
                .as("the org pack declares no fallback; the project that builds on it does")
                .isEqualTo(RefusalCode.FALLBACK_PROFILE_NOT_LOCKED);
        assertThat(refused.getMessage()).contains("closure rooted at project");
        Packs stillLocked = Packs.of(ORG_ID, "org-base", "1.1.0", "org", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", LOCK_GENERIC)
                .file("agent_profiles/generic-agent.agent.yaml", profile("generic-agent", "role: generic\n"));
        assertThat(rig.register("org-base", rig.stage(stillLocked), Preconditions.none())
                        .outcome())
                .as("the control: the same write keeping the lock")
                .isEqualTo(Outcome.REPLACED);
    }

    private static AgentProfile agentProfile(String role) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier("generic-agent"))
                .packId(new PackIdentifier("org-base"))
                .name("generic-agent")
                .role(role)
                .purpose("Serve when no profile fits.")
                .specialization(new Specialization("Anything", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
    }
}
