package ai.speckitty.charter.profiles.domain.resolution;

import static ai.speckitty.charter.profiles.domain.resolution.Profiles.D001;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.D030;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.D038;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.REVIEW;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.REVIEW_COLLABORATION;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.content;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.minimal;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.nonIdentity;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.reviewer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.Collaboration;
import ai.speckitty.charter.profiles.domain.Exclusion;
import ai.speckitty.charter.profiles.domain.Performer;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.profiles.domain.resolution.ProfileEnhancementMerge.Enhancer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileEnhancementMerge (ruling D5, D7)")
class ProfileEnhancementMergeTest {

    private static final AgentProfile BASE =
            reviewer("reviewer-renata", "built-in").build();

    /** An enhancer that restates the base's specialization, so only the fields a case adds are judged. */
    private static AgentProfile.Builder enhancer() {
        return minimal("reviewer-renata-org", "org-base", REVIEW);
    }

    private static AgentProfile merge(AgentProfile... enhancers) {
        return ProfileEnhancementMerge.merge(
                BASE,
                java.util.Arrays.stream(enhancers)
                        .map(profile -> new Enhancer(profile, profile.packId().value()))
                        .toList());
    }

    private static void assertRefused(AgentProfile enhancing, String field) {
        assertThatThrownBy(() -> merge(enhancing))
                .isInstanceOfSatisfying(ProfileEnhancementDropsField.class, refused -> {
                    assertThat(refused.pack()).isEqualTo("org-base");
                    assertThat(refused.field()).isEqualTo(field);
                })
                .hasMessageContaining("org-base")
                .hasMessageContaining(field);
    }

    @Test
    void withoutEnhancersTheBaseIsAnsweredAsItIs() {
        AgentProfile merged = ProfileEnhancementMerge.merge(BASE, List.of());

        assertThat(merged).isEqualTo(BASE);
        assertThat(merged.packId()).isEqualTo(BASE.packId());
        assertThat(content(merged)).isEqualTo(content(BASE));
    }

    @Test
    void anEnhancerThatRestatesAndAddsReplacesTheFieldsItDeclaresAndLeavesTheOthers() {
        AgentProfile enhancing = enhancer()
                .directiveReference(D030)
                .directiveReference(D001)
                .directiveReference(D038)
                .capability("security-audit")
                .capability("code-review")
                .capability("threat-modelling")
                .build();

        AgentProfile merged = merge(enhancing);

        assertThat(merged.directiveReferences()).containsExactly(D030, D001, D038);
        assertThat(merged.capabilities()).containsExactly("security-audit", "code-review", "threat-modelling");
        Map<String, Object> expected = nonIdentity(BASE);
        expected.put("directive-references", ProfileField.DIRECTIVE_REFERENCES.shapeOf(enhancing));
        expected.put("capabilities", ProfileField.CAPABILITIES.shapeOf(enhancing));
        assertThat(nonIdentity(merged)).isEqualTo(expected);
    }

    @Test
    void theIdentityFieldsOfAnEnhancerAreItsOwnAndNeverMerged() {
        AgentProfile enhancing = enhancer()
                .role("implementer")
                .altRole("architect")
                .routingPriority(Optional.of(90))
                .performer(Optional.of(Performer.AGENT))
                .build();

        AgentProfile merged = merge(enhancing);

        assertThat(merged.id()).isEqualTo(BASE.id());
        assertThat(merged.name()).isEqualTo(BASE.name());
        assertThat(merged.purpose()).isEqualTo(BASE.purpose());
        assertThat(merged.role()).isEqualTo("reviewer");
        assertThat(merged.altRoles()).isEmpty();
        assertThat(merged.routingPriority()).contains(50);
        assertThat(merged.performer()).isEmpty();
        assertThat(content(merged)).isEqualTo(content(BASE));
    }

    // ---- objects

    @Test
    void aSpecializationThatLeavesOutTheBasesIsRefusedNamingThePackAndTheField() {
        assertRefused(
                minimal(
                                "reviewer-renata-org",
                                "org-base",
                                new Specialization(
                                        "Code quality", Optional.empty(), Optional.empty(), Optional.empty()))
                        .build(),
                "specialization");
    }

    @Test
    void aSubFieldChangedIsRefused() {
        assertRefused(
                minimal(
                                "reviewer-renata-org",
                                "org-base",
                                new Specialization(
                                        "Security",
                                        REVIEW.secondaryAwareness(),
                                        REVIEW.avoidanceBoundary(),
                                        Optional.empty()))
                        .build(),
                "specialization");
    }

    @Test
    void aSubFieldAddedToAnObjectIsAccepted() {
        Specialization wider = new Specialization(
                REVIEW.primaryFocus(),
                REVIEW.secondaryAwareness(),
                REVIEW.avoidanceBoundary(),
                Optional.of("Every critical issue is found"));

        assertThat(merge(minimal("reviewer-renata-org", "org-base", wider).build())
                        .specialization())
                .isEqualTo(wider);
    }

    @Test
    void aListInsideAnObjectLosingAnItemIsRefusedAndOneThatGainsIsAccepted() {
        Collaboration fewer = new Collaboration(
                List.of("implementer"),
                List.of("implementer"),
                List.of(),
                List.of("review-report"),
                List.of(),
                List.of("review", "approve"));
        assertRefused(enhancer().collaboration(fewer).build(), "collaboration");

        Collaboration more = new Collaboration(
                List.of("planner", "implementer"),
                List.of("implementer"),
                List.of("architect"),
                List.of("review-report"),
                List.of(),
                List.of("approve", "review"));
        assertThat(merge(enhancer().collaboration(more).build()).collaboration())
                .isEqualTo(more);
    }

    // ---- lists

    @Test
    void aListItemDroppedIsRefused() {
        assertRefused(
                enhancer().capability("code-review").capability("design-review").build(), "capabilities");
    }

    @Test
    void aListOfObjectsWithAnItemChangedIsRefused() {
        assertRefused(
                enhancer()
                        .directiveReference(D001)
                        .directiveReference(new ai.speckitty.charter.profiles.domain.DirectiveReference(
                                "030", "Test and Typecheck Quality Gate", "Another rationale"))
                        .build(),
                "directive-references");
    }

    // ---- scalars

    @Test
    void aScalarChangedIsRefusedAndTheSameScalarIsAccepted() {
        assertRefused(enhancer().maxConcurrentTasks(Optional.of(3)).build(), "max-concurrent-tasks");
        assertRefused(enhancer().description(Optional.of("Another text")).build(), "description");

        assertThat(merge(enhancer().maxConcurrentTasks(Optional.of(8)).build()).maxConcurrentTasks())
                .contains(8);
    }

    @Test
    void aScalarTheBaseLeavesAbsentIsAdded() {
        assertThat(merge(enhancer().model(Optional.of("a-model")).build()).model())
                .contains("a-model");
    }

    // ---- excluding (ruling D7)

    @Test
    void anEnhancerThatDeclaresExcludingIsRefusedNamingExcluding() {
        assertRefused(
                enhancer()
                        .excluding(Optional.of(new Exclusion.WholeFields(List.of("capabilities"))))
                        .build(),
                "excluding");
        assertRefused(
                enhancer()
                        .excluding(
                                Optional.of(new Exclusion.FieldValues(Map.of("capabilities", List.of("code-review")))))
                        .build(),
                "excluding");
    }

    @Test
    void anEmptyExcludingDeclaresNothingAndIsAccepted() {
        assertThat(merge(enhancer()
                                .excluding(Optional.of(new Exclusion.WholeFields(List.of())))
                                .build())
                        .excluding())
                .isEmpty();
    }

    // ---- several enhancers

    @Test
    void enhancersMergeInClosureOrderAndALaterOneMustKeepWhatAnEarlierOneAdded() {
        AgentProfile org = enhancer()
                .directiveReference(D001)
                .directiveReference(D030)
                .directiveReference(D038)
                .build();
        AgentProfile project = minimal("reviewer-renata-project", "project", REVIEW)
                .directiveReference(D001)
                .directiveReference(D030)
                .build();

        assertThatThrownBy(() -> merge(org, project))
                .isInstanceOfSatisfying(ProfileEnhancementDropsField.class, refused -> {
                    assertThat(refused.pack()).isEqualTo("project");
                    assertThat(refused.field()).isEqualTo("directive-references");
                });
        assertThat(merge(project, org).directiveReferences()).containsExactly(D001, D030, D038);
    }

    @Test
    void theCollaborationTheBaseDeclaresIsKeptWhenTheEnhancerLeavesItAbsent() {
        assertThat(merge(enhancer().build()).collaboration()).isEqualTo(REVIEW_COLLABORATION);
    }
}
