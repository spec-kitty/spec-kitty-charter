package ai.speckitty.charter.profiles.domain.resolution;

import static ai.speckitty.charter.profiles.domain.resolution.Profiles.content;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.ArtifactReference;
import ai.speckitty.charter.profiles.domain.Collaboration;
import ai.speckitty.charter.profiles.domain.DirectiveReference;
import ai.speckitty.charter.profiles.domain.Exclusion;
import ai.speckitty.charter.profiles.domain.ModeDefault;
import ai.speckitty.charter.profiles.domain.Performer;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.profiles.domain.SpecializationContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileInheritance (ruling D6)")
class ProfileInheritanceTest {

    private static final PackIdentifier BUILT_IN = new PackIdentifier("built-in");

    private static final SpecializationContext IVAN_CONTEXT = new SpecializationContext(
            List.of("python", "typescript", "javascript"),
            List.of("django", "fastapi"),
            List.of("src/**/*.py"),
            List.of("implementation"),
            List.of("pragmatic"),
            List.of("medium"));

    private static final Collaboration IVAN_COLLABORATION = new Collaboration(
            List.of("reviewer"),
            List.of("planner"),
            List.of(),
            List.of("source-code"),
            List.of(),
            List.of("implement"));

    /** The built-in implementer-ivan, abridged: a field of each kind. */
    private static AgentProfile ivan() {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier("implementer-ivan"))
                .packId(BUILT_IN)
                .schemaVersion(Optional.of("1.0"))
                .name("Implementer Ivan")
                .description(Optional.of("General-purpose software implementation specialist"))
                .role("implementer")
                .capability("code-implementation")
                .routingPriority(Optional.of(50))
                .maxConcurrentTasks(Optional.of(5))
                .purpose("Implement the work.")
                .specialization(new Specialization(
                        "Writing production code", Optional.of("Testing"), Optional.empty(), Optional.empty()))
                .collaboration(IVAN_COLLABORATION)
                .modeDefault(new ModeDefault("implement", "Write the code", "A work package"))
                .initializationDeclaration(Optional.of("I am Implementer Ivan."))
                .specializationContext(IVAN_CONTEXT)
                .tacticReference(new ArtifactReference("tdd-red-green-refactor", "Test first"))
                .build();
    }

    /**
     * The built-in java-jenny, abridged: she declares her own languages and specialization, and leaves the
     * collaboration, the mode defaults, the initialization declaration and the description absent, so they come from
     * ivan.
     */
    private static AgentProfile jenny() {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier("java-jenny"))
                .packId(BUILT_IN)
                .name("Java Jenny")
                .role("implementer")
                .appliesToLanguage("java")
                .capability("java-implementation")
                .capability("junit-testing")
                .routingPriority(Optional.of(80))
                .purpose("Implement the work in Java.")
                .specialization(
                        new Specialization("Java implementation", Optional.empty(), Optional.empty(), Optional.empty()))
                .specializationContext(new SpecializationContext(
                        List.of("java"), List.of("junit5"), List.of(), List.of(), List.of(), List.of()))
                .performer(Optional.of(Performer.AGENT))
                .build();
    }

    @Test
    void jennyKeepsTheFieldsSheDeclaresAndInheritsTheOnesSheLeavesAbsentFromIvan() {
        AgentProfile ivan = ivan();
        AgentProfile jenny = jenny();

        AgentProfile effective = ProfileInheritance.fold(List.of(ivan, jenny));

        assertThat(effective.specializationContext().languages())
                .as("a declared object replaces the ancestor's whole: jenny's languages stay [java]")
                .containsExactly("java");
        assertThat(effective.specializationContext().frameworks()).containsExactly("junit5");
        assertThat(effective.capabilities())
                .as("a declared list replaces the ancestor's")
                .containsExactly("java-implementation", "junit-testing");
        assertThat(effective.specialization().primaryFocus())
                .as("the required specialization is always the leaf's own")
                .isEqualTo("Java implementation");
        assertThat(effective.specialization().secondaryAwareness()).isEmpty();
        assertThat(effective.collaboration())
                .as("an absent object is inherited")
                .isEqualTo(IVAN_COLLABORATION);
        assertThat(effective.modeDefaults()).as("an absent list is inherited").isEqualTo(ivan.modeDefaults());
        assertThat(effective.tacticReferences()).isEqualTo(ivan.tacticReferences());
        assertThat(effective.description()).as("an absent scalar is inherited").isEqualTo(ivan.description());
        assertThat(effective.maxConcurrentTasks()).contains(5);
        assertThat(effective.initializationDeclaration()).contains("I am Implementer Ivan.");
        assertThat(effective.appliesToLanguages()).containsExactly("java");
    }

    @Test
    void theIdentityFieldsAreTheLeafsAndAreNeverInherited() {
        AgentProfile ivan = AgentProfile.builder()
                .id(new AgentProfileIdentifier("implementer-ivan"))
                .packId(BUILT_IN)
                .name("Implementer Ivan")
                .role("implementer")
                .altRole("reviewer")
                .routingPriority(Optional.of(50))
                .performer(Optional.of(Performer.AGENT))
                .purpose("Implement the work.")
                .specialization(new Specialization("Code", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
        AgentProfile leaf = AgentProfile.builder()
                .id(new AgentProfileIdentifier("kotlin-kai"))
                .packId(new PackIdentifier("project"))
                .name("Kotlin Kai")
                .role("implementer")
                .purpose("Implement the work in Kotlin.")
                .specialization(new Specialization("Kotlin", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();

        AgentProfile effective = ProfileInheritance.fold(List.of(ivan, leaf));

        assertThat(effective).isEqualTo(leaf);
        assertThat(effective.packId()).isEqualTo(leaf.packId());
        assertThat(effective.name()).isEqualTo("Kotlin Kai");
        assertThat(effective.purpose()).isEqualTo("Implement the work in Kotlin.");
        assertThat(effective.altRoles()).as("alt-roles are identity").isEmpty();
        assertThat(effective.routingPriority())
                .as("routing-priority is identity")
                .isEmpty();
        assertThat(effective.performer()).as("performer is identity").isEmpty();
    }

    /**
     * A field inherited from an ancestor of another pack names what it named there (#673): each bare reference of each
     * list, and of the exclusion's values, is qualified with the ancestor's pack; one qualified with the leaf's pack is
     * bare; one qualified with a third pack is kept.
     */
    @Test
    void aFieldInheritedFromAnotherPackNamesWhatItNamedThere() {
        AgentProfile root = Profiles.minimal("root", "built-in", Profiles.REVIEW)
                .directiveReference(new DirectiveReference("DIRECTIVE_001", "One", "why"))
                .directiveReference(new DirectiveReference("org-base::DIRECTIVE_038", "Org", "why"))
                .tacticReference(new ArtifactReference("tactic:t", "why"))
                .toolguideReference(new ArtifactReference("other::g", "why"))
                .styleguideReference(new ArtifactReference("s", "why"))
                .collaboration(
                        new Collaboration(List.of(), List.of(), List.of(), List.of(), List.of("review"), List.of()))
                .excluding(Optional.of(new Exclusion.FieldValues(
                        Map.of("directive-references", List.of("DIRECTIVE_003"), "capabilities", List.of("x")))))
                .build();
        AgentProfile leaf =
                Profiles.minimal("leaf", "org-base", Profiles.REVIEW).build();

        AgentProfile effective = ProfileInheritance.fold(List.of(root, leaf));

        assertThat(effective.packId()).isEqualTo(new PackIdentifier("org-base"));
        assertThat(effective.directiveReferences())
                .extracting(DirectiveReference::code)
                .containsExactly("built-in::DIRECTIVE_001", "DIRECTIVE_038");
        assertThat(effective.tacticReferences())
                .extracting(ArtifactReference::id)
                .containsExactly("built-in::tactic:t");
        assertThat(effective.toolguideReferences())
                .extracting(ArtifactReference::id)
                .containsExactly("other::g");
        assertThat(effective.styleguideReferences())
                .extracting(ArtifactReference::id)
                .containsExactly("built-in::s");
        assertThat(effective.collaboration().operatingProcedures()).containsExactly("built-in::review");
        assertThat(effective.excluding())
                .contains(new Exclusion.FieldValues(Map.of(
                        "directive-references", List.of("built-in::DIRECTIVE_003"), "capabilities", List.of("x"))));
        assertThat(effective.writtenReferences())
                .extracting(Object::toString)
                .contains("built-in::directive:DIRECTIVE_001", "directive:DIRECTIVE_038", "built-in::procedure:review");
    }

    @Test
    void aChainOfThreeTakesEachFieldFromTheNearestProfileThatDeclaresItAndSeveralParentsFoldInTheirOrder() {
        AgentProfile root = Profiles.minimal("root", "built-in", Profiles.REVIEW)
                .model(Optional.of("root-model"))
                .effort(Optional.of("low"))
                .capability("root-capability")
                .build();
        AgentProfile otherParent = Profiles.minimal("other", "built-in", Profiles.REVIEW)
                .effort(Optional.of("medium"))
                .build();
        AgentProfile leaf = Profiles.minimal("leaf", "org-base", Profiles.REVIEW)
                .capability("leaf-capability")
                .build();

        AgentProfile effective = ProfileInheritance.fold(List.of(root, otherParent, leaf));

        assertThat(effective.model()).contains("root-model");
        assertThat(effective.effort())
                .as("the later parent in closure order wins")
                .contains("medium");
        assertThat(effective.capabilities()).containsExactly("leaf-capability");
        assertThat(ProfileInheritance.fold(List.of(otherParent, root, leaf)).effort())
                .contains("low");
    }

    @Test
    void aChainOfTheProfileAloneIsTheProfile() {
        AgentProfile jenny = jenny();

        assertThat(content(ProfileInheritance.fold(List.of(jenny)))).isEqualTo(content(jenny));
    }

    @Test
    void anEmptyChainIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> ProfileInheritance.fold(List.of()));
    }
}
