package ai.speckitty.charter.profiles.domain.resolution;

import static ai.speckitty.charter.profiles.domain.resolution.Profiles.BUILT_IN_D001;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.BUILT_IN_D030;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.D001;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.D030;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.D038;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.REVIEW;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.REVIEW_COLLABORATION;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.content;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.minimal;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.nonIdentity;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.qualified;
import static ai.speckitty.charter.profiles.domain.resolution.Profiles.reviewer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.ArtifactReference;
import ai.speckitty.charter.profiles.domain.Collaboration;
import ai.speckitty.charter.profiles.domain.DirectiveReference;
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
                        .map(profile -> new Enhancer(profile, profile.packId()))
                        .toList());
    }

    private static void assertRefused(AgentProfile enhancing, String field) {
        assertThatThrownBy(() -> merge(enhancing))
                .isInstanceOfSatisfying(ProfileEnhancementDropsField.class, refused -> {
                    assertThat(refused.pack()).isEqualTo(new PackIdentifier("org-base"));
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
                .directiveReference(BUILT_IN_D030)
                .directiveReference(BUILT_IN_D001)
                .directiveReference(D038)
                .capability("security-audit")
                .capability("code-review")
                .capability("threat-modelling")
                .build();

        AgentProfile merged = merge(enhancing);

        assertThat(merged.directiveReferences()).containsExactly(D030, D001, qualified("org-base", D038));
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
                        .directiveReference(BUILT_IN_D001)
                        .directiveReference(new ai.speckitty.charter.profiles.domain.DirectiveReference(
                                "built-in::DIRECTIVE_030", "Test and Typecheck Quality Gate", "Another rationale"))
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
                .directiveReference(BUILT_IN_D001)
                .directiveReference(BUILT_IN_D030)
                .directiveReference(D038)
                .build();
        AgentProfile project = minimal("reviewer-renata-project", "project", REVIEW)
                .directiveReference(BUILT_IN_D001)
                .directiveReference(BUILT_IN_D030)
                .build();

        assertThatThrownBy(() -> merge(org, project))
                .isInstanceOfSatisfying(ProfileEnhancementDropsField.class, refused -> {
                    assertThat(refused.pack()).isEqualTo(new PackIdentifier("project"));
                    assertThat(refused.field()).isEqualTo("directive-references");
                });
        assertThat(merge(project, org).directiveReferences()).containsExactly(D001, D030, qualified("org-base", D038));
    }

    /**
     * A reference is kept by what it names, not by how it is written (#673): an enhancer of another pack that restates
     * the base's bare reference bare names an artifact of its own pack, and so drops the base's; written qualified, or
     * with the kind written out, it keeps it. A later enhancer of a third pack keeps an earlier one's own reference only
     * by qualifying it with that pack.
     */
    @Test
    void aReferenceIsKeptByWhatItNamesNotByHowItIsWritten() {
        assertRefused(
                enhancer().directiveReference(D001).directiveReference(D030).build(), "directive-references");
        AgentProfile spelledOut = enhancer()
                .directiveReference(qualified(
                        "built-in", new DirectiveReference("directive:DIRECTIVE_001", D001.name(), D001.rationale())))
                .directiveReference(BUILT_IN_D030)
                .build();
        assertThat(merge(spelledOut).directiveReferences())
                .as("answered as built-in's, a reference qualified with built-in is bare")
                .containsExactly(
                        new DirectiveReference("directive:DIRECTIVE_001", D001.name(), D001.rationale()), D030);

        AgentProfile org = enhancer()
                .directiveReference(BUILT_IN_D001)
                .directiveReference(BUILT_IN_D030)
                .directiveReference(D038)
                .build();
        AgentProfile projectBare = minimal("reviewer-renata-project", "project", REVIEW)
                .directiveReference(BUILT_IN_D001)
                .directiveReference(BUILT_IN_D030)
                .directiveReference(D038)
                .build();
        assertThatThrownBy(() -> merge(org, projectBare))
                .isInstanceOfSatisfying(ProfileEnhancementDropsField.class, refused -> {
                    assertThat(refused.pack()).isEqualTo(new PackIdentifier("project"));
                    assertThat(refused.field()).isEqualTo("directive-references");
                });
        AgentProfile projectQualified = minimal("reviewer-renata-project", "project", REVIEW)
                .directiveReference(BUILT_IN_D001)
                .directiveReference(BUILT_IN_D030)
                .directiveReference(qualified("org-base", D038))
                .build();
        assertThat(merge(org, projectQualified).directiveReferences())
                .containsExactly(D001, D030, qualified("org-base", D038));
    }

    /**
     * Every reference list an enhancer of another pack declares is written as the base's pack reads it (#673): its bare
     * entries qualified with the enhancer's pack, those qualified with the base's pack bare, any other kept.
     */
    @Test
    void everyReferenceListOfAnEnhancerOfAnotherPackIsWrittenForTheBasesPack() {
        AgentProfile enhancing = enhancer()
                .tacticReference(new ArtifactReference("built-in::code-review-incremental", "Read intent first"))
                .tacticReference(new ArtifactReference("org-tactic", "why"))
                .tacticReference(new ArtifactReference("built-in::tactic:base-tactic", "why"))
                .toolguideReference(new ArtifactReference("org-guide", "why"))
                .styleguideReference(new ArtifactReference("other::style", "why"))
                .collaboration(new Collaboration(
                        REVIEW_COLLABORATION.handoffTo(),
                        REVIEW_COLLABORATION.handoffFrom(),
                        REVIEW_COLLABORATION.worksWith(),
                        REVIEW_COLLABORATION.outputArtifacts(),
                        List.of("org-procedure", "built-in::review"),
                        REVIEW_COLLABORATION.canonicalVerbs()))
                .build();

        AgentProfile merged = merge(enhancing);

        assertThat(merged.tacticReferences())
                .extracting(ArtifactReference::id)
                .containsExactly("code-review-incremental", "org-base::org-tactic", "tactic:base-tactic");
        assertThat(merged.toolguideReferences())
                .extracting(ArtifactReference::id)
                .containsExactly("org-base::org-guide");
        assertThat(merged.styleguideReferences())
                .extracting(ArtifactReference::id)
                .containsExactly("other::style");
        assertThat(merged.collaboration().operatingProcedures()).containsExactly("org-base::org-procedure", "review");
    }

    @Test
    void theCollaborationTheBaseDeclaresIsKeptWhenTheEnhancerLeavesItAbsent() {
        assertThat(merge(enhancer().build()).collaboration()).isEqualTo(REVIEW_COLLABORATION);
    }
}
