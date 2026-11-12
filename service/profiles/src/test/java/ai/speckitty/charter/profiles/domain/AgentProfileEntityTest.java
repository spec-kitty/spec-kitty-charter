package ai.speckitty.charter.profiles.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AgentProfile entity")
class AgentProfileEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("built-in");

    private static AgentProfile.Builder valid(String id) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(PACK)
                .name("Name of " + id)
                .role("implementer")
                .purpose("Purpose of " + id)
                .specialization(
                        new Specialization("Focus of " + id, Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private static final Specialization JENNY_SPECIALIZATION = new Specialization(
            "Java implementation, JUnit 5 testing, AssertJ assertions, Maven lifecycle",
            Optional.of("Spring framework conventions, Hibernate/JPA, ArchUnit architecture rules"),
            Optional.of("Architectural decisions, deployment strategies, infrastructure-as-code"),
            Optional.of("Well-tested, idiomatic Java code that passes all Maven quality gates"));

    private static final Collaboration JENNY_COLLABORATION = new Collaboration(
            List.of("reviewer"),
            List.of("architect", "planner"),
            List.of("reviewer", "curator", "implementer"),
            List.of("source-code", "unit-tests", "pull-request"),
            List.of("test-first-bug-fixing"),
            List.of("implement", "fix", "refactor", "test", "debug"));

    private static final SpecializationContext JENNY_CONTEXT = new SpecializationContext(
            List.of("java"),
            List.of("junit5", "assertj", "maven"),
            List.of("src/main/java/**/*.java", "src/test/java/**/*.java"),
            List.of("java", "junit", "try-with-resources"),
            List.of("pragmatic", "type-safe"),
            List.of("low", "medium", "high"));

    /**
     * The built-in {@code java-jenny} profile as the pack authors it (abridged texts), every list with more than one
     * entry so order counts. The fields she does not declare (avatar, model, effort, sentinel, toolguide and
     * styleguide references, excluding) carry plausible values so that every field of the schema is set.
     */
    private static AgentProfile.Builder javaJenny() {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier("java-jenny"))
                .packId(PACK)
                .schemaVersion(Optional.of("1.0"))
                .name("Java Jenny")
                .description(Optional.of("Java-specialist implementer applying ATDD/TDD discipline"))
                .role("implementer")
                .altRole("reviewer")
                .avatarImage(Optional.of("avatars/java-jenny.png"))
                .capability("java-implementation")
                .capability("junit-testing")
                .capability("maven-build")
                .routingPriority(Optional.of(80))
                .maxConcurrentTasks(Optional.of(5))
                .model(Optional.of("a-model"))
                .effort(Optional.of("high"))
                .purpose("Deliver idiomatic, well-tested Java code that faithfully implements specifications.")
                .specialization(JENNY_SPECIALIZATION)
                .collaboration(JENNY_COLLABORATION)
                .modeDefault(new ModeDefault(
                        "implementation",
                        "ATDD/TDD-driven feature development in Java",
                        "Building new features, writing production code with acceptance and unit tests first"))
                .modeDefault(new ModeDefault(
                        "debugging",
                        "Investigating and fixing defects with reproduction tests",
                        "Resolving bug reports with a failing test first"))
                .initializationDeclaration(Optional.of("I am Java Jenny. I write idiomatic, well-tested Java code."))
                .sentinel(Optional.of(true))
                .specializationContext(JENNY_CONTEXT)
                .appliesToLanguage("java")
                .appliesToLanguage("kotlin")
                .directiveReference(new DirectiveReference(
                        "010", "Specification Fidelity Requirement", "Implementations reflect the specification"))
                .directiveReference(new DirectiveReference(
                        "034", "Test-First Development", "Tests define the contract before production code"))
                .tacticReference(new ArtifactReference("acceptance-test-first", "ATDD outside-in"))
                .tacticReference(new ArtifactReference("tdd-red-green-refactor", "Unit-level red-green-refactor"))
                .toolguideReference(new ArtifactReference("java-supply-chain", "Exact commands for dependency checks"))
                .toolguideReference(new ArtifactReference("gradle-build", "The build runs through the wrapper"))
                .styleguideReference(new ArtifactReference("java-style", "Naming and null-safety conventions"))
                .styleguideReference(new ArtifactReference("test-style", "Given-when-then test names"))
                .selfReviewStep(new SelfReviewStep("compile", Optional.of("mvn compile"), "zero compilation errors"))
                .selfReviewStep(new SelfReviewStep("run-tests", Optional.of("mvn test"), "all tests pass"))
                .selfReviewStep(new SelfReviewStep(
                        "acceptance-review",
                        Optional.empty(),
                        "each acceptance criterion from the specification has a corresponding passing test"))
                .excluding(Optional.of(new Exclusion.WholeFields(List.of("capabilities", "mode-defaults"))));
    }

    private static void hasJennysTexts(AgentProfile p) {
        assertThat(p.name()).isEqualTo("Java Jenny");
        assertThat(p.label()).isEqualTo("Java Jenny");
        assertThat(p.description()).contains("Java-specialist implementer applying ATDD/TDD discipline");
        assertThat(p.purpose())
                .isEqualTo("Deliver idiomatic, well-tested Java code that faithfully implements" + " specifications.");
        assertThat(p.initializationDeclaration())
                .contains("I am Java Jenny. I write idiomatic, well-tested Java code.");
        assertThat(p.avatarImage()).contains("avatars/java-jenny.png");
    }

    private static void hasJennysRouting(AgentProfile p) {
        assertThat(p.role()).isEqualTo("implementer");
        assertThat(p.altRoles()).containsExactly("reviewer");
        assertThat(p.capabilities()).containsExactly("java-implementation", "junit-testing", "maven-build");
        assertThat(p.routingPriority()).contains(80);
        assertThat(p.maxConcurrentTasks()).contains(5);
        assertThat(p.model()).contains("a-model");
        assertThat(p.effort()).contains("high");
        assertThat(p.sentinel()).contains(true);
        assertThat(p.appliesToLanguages()).containsExactly("java", "kotlin");
    }

    private static void hasJennysReferences(AgentProfile p) {
        assertThat(p.directiveReferences())
                .extracting(DirectiveReference::code, DirectiveReference::name)
                .containsExactly(
                        tuple("010", "Specification Fidelity Requirement"), tuple("034", "Test-First Development"));
        assertThat(p.directiveReferences().get(1).rationale())
                .isEqualTo("Tests define the contract before production code");
        assertThat(p.tacticReferences())
                .containsExactly(
                        new ArtifactReference("acceptance-test-first", "ATDD outside-in"),
                        new ArtifactReference("tdd-red-green-refactor", "Unit-level red-green-refactor"));
        assertThat(p.toolguideReferences())
                .containsExactly(
                        new ArtifactReference("java-supply-chain", "Exact commands for dependency checks"),
                        new ArtifactReference("gradle-build", "The build runs through the wrapper"));
        assertThat(p.styleguideReferences())
                .containsExactly(
                        new ArtifactReference("java-style", "Naming and null-safety conventions"),
                        new ArtifactReference("test-style", "Given-when-then test names"));
    }

    @Test
    @DisplayName("a profile built with every field set reads every field back, in authored order")
    void everyFieldRoundTrips() {
        AgentProfile profile = javaJenny().build();

        assertThat(profile)
                .as("identity and pack")
                .satisfies(p -> {
                    assertThat(p.id()).isEqualTo(new AgentProfileIdentifier("java-jenny"));
                    assertThat(p.packId()).isEqualTo(PACK);
                    assertThat(p.schemaVersion()).contains("1.0");
                })
                .as("descriptive texts")
                .satisfies(AgentProfileEntityTest::hasJennysTexts)
                .as("routing and execution")
                .satisfies(AgentProfileEntityTest::hasJennysRouting)
                .as("specialization, its context and collaboration")
                .satisfies(p -> {
                    assertThat(p.specialization()).isEqualTo(JENNY_SPECIALIZATION);
                    assertThat(p.specializationContext()).isEqualTo(JENNY_CONTEXT);
                    assertThat(p.collaboration()).isEqualTo(JENNY_COLLABORATION);
                })
                .as("mode defaults with their record fields")
                .satisfies(p -> assertThat(p.modeDefaults())
                        .extracting(ModeDefault::mode, ModeDefault::description, ModeDefault::useCase)
                        .containsExactly(
                                tuple(
                                        "implementation",
                                        "ATDD/TDD-driven feature development in Java",
                                        "Building new features, writing production code with acceptance and unit"
                                                + " tests first"),
                                tuple(
                                        "debugging",
                                        "Investigating and fixing defects with reproduction tests",
                                        "Resolving bug reports with a failing test first")))
                .as("governance references, each list on its own")
                .satisfies(AgentProfileEntityTest::hasJennysReferences)
                .as("self-review steps with their record fields, a step without a command included")
                .satisfies(p -> assertThat(p.selfReviewSteps())
                        .extracting(SelfReviewStep::name, SelfReviewStep::command, SelfReviewStep::gate)
                        .containsExactly(
                                tuple("compile", Optional.of("mvn compile"), "zero compilation errors"),
                                tuple("run-tests", Optional.of("mvn test"), "all tests pass"),
                                tuple(
                                        "acceptance-review",
                                        Optional.empty(),
                                        "each acceptance criterion from the specification has a corresponding"
                                                + " passing test")))
                .as("excluding, as authored")
                .satisfies(p -> assertThat(p.excluding())
                        .contains(new Exclusion.WholeFields(List.of("capabilities", "mode-defaults"))));
    }

    @Test
    @DisplayName("a built profile does not follow later changes to its builder")
    void builtProfileIsDetachedFromItsBuilder() {
        AgentProfile.Builder builder = javaJenny();
        AgentProfile profile = builder.build();

        builder.role("architect")
                .capability("deployment")
                .selfReviewStep(new SelfReviewStep("lint", Optional.empty(), "no warnings"))
                .modeDefault(new ModeDefault("planning", "Plans", "Before a mission"))
                .appliesToLanguage("scala")
                .directiveReference(new DirectiveReference("999", "Later directive", "why"))
                .tacticReference(new ArtifactReference("later-tactic", "why"))
                .toolguideReference(new ArtifactReference("later-toolguide", "why"))
                .styleguideReference(new ArtifactReference("later-styleguide", "why"));

        assertThat(profile.appliesToLanguages()).containsExactly("java", "kotlin");
        assertThat(profile.directiveReferences())
                .hasSize(2)
                .extracting(DirectiveReference::code)
                .doesNotContain("999");
        assertThat(profile.tacticReferences())
                .extracting(ArtifactReference::id)
                .containsExactly("acceptance-test-first", "tdd-red-green-refactor");
        assertThat(profile.toolguideReferences())
                .extracting(ArtifactReference::id)
                .containsExactly("java-supply-chain", "gradle-build");
        assertThat(profile.styleguideReferences())
                .extracting(ArtifactReference::id)
                .containsExactly("java-style", "test-style");
        assertThat(profile.role()).isEqualTo("implementer");
        assertThat(profile.altRoles()).containsExactly("reviewer");
        assertThat(profile.capabilities()).containsExactly("java-implementation", "junit-testing", "maven-build");
        assertThat(profile.selfReviewSteps())
                .extracting(SelfReviewStep::name)
                .containsExactly("compile", "run-tests", "acceptance-review");
        assertThat(profile.modeDefaults()).extracting(ModeDefault::mode).containsExactly("implementation", "debugging");
    }

    @Test
    @DisplayName("a self-review step needs a name and a gate but no command; a mode default needs all three fields")
    void stepAndModeRefuseAMissingField() {
        assertThat(new SelfReviewStep("locality-review", Optional.empty(), "only related files change").command())
                .isEmpty();

        assertThatNullPointerException()
                .isThrownBy(() -> new SelfReviewStep("compile", Optional.of("mvn compile"), null))
                .withMessage("gate");
        assertThatNullPointerException()
                .isThrownBy(() -> new SelfReviewStep(null, Optional.empty(), "all tests pass"))
                .withMessage("name");
        assertThatNullPointerException()
                .isThrownBy(() -> new ModeDefault("debugging", "Investigating defects", null))
                .withMessage("useCase");
        assertThatNullPointerException()
                .isThrownBy(() -> new ModeDefault(null, "Investigating defects", "Bug reports"))
                .withMessage("mode");
    }

    @Test
    @DisplayName("it is a CharterArtifact of kind agent_profile, named agent_profile:<profile-id>")
    void identityAndKind() {
        CharterArtifact profile = valid("architect-alphonso").build();

        assertThat(profile.kind()).isEqualTo(ArtifactKind.AGENT_PROFILE);
        assertThat(profile.localId()).isEqualTo("architect-alphonso");
        assertThat(profile.urn()).isEqualTo(new Urn(NodeKind.of(ArtifactKind.AGENT_PROFILE), "architect-alphonso"));
        assertThat(profile.label()).isEqualTo("Name of architect-alphonso");
        assertThat(profile.packId()).isEqualTo(PACK);
    }

    @Test
    @DisplayName("an undeclared defaulted field stays undeclared: no default is invented")
    void defaultsAreNotInvented() {
        AgentProfile profile = valid("p").build();

        assertThat(profile.schemaVersion()).isEmpty();
        assertThat(profile.description()).isEmpty();
        assertThat(profile.routingPriority()).isEmpty();
        assertThat(profile.maxConcurrentTasks()).isEmpty();
        assertThat(profile.sentinel()).isEmpty();
        assertThat(profile.initializationDeclaration()).isEmpty();
        assertThat(profile.collaboration()).isEqualTo(Collaboration.NONE);
        assertThat(profile.specializationContext()).isEqualTo(SpecializationContext.NONE);
        assertThat(profile.excluding()).isEmpty();
    }

    @Test
    @DisplayName("declared values are kept as declared, lists in authored order with duplicates")
    void declaredValuesAreKept() {
        AgentProfile profile = valid("p")
                .schemaVersion(Optional.of("1.0"))
                .routingPriority(Optional.of(0))
                .maxConcurrentTasks(Optional.of(1))
                .sentinel(Optional.of(false))
                .role("reviewer")
                .altRole("implementer")
                .directiveReference(new DirectiveReference("003", "Decision documentation", "why"))
                .directiveReference(new DirectiveReference("003", "Decision documentation", "why"))
                .build();

        assertThat(profile.schemaVersion()).contains("1.0");
        assertThat(profile.routingPriority()).contains(0);
        assertThat(profile.maxConcurrentTasks()).contains(1);
        assertThat(profile.sentinel()).contains(false);
        assertThat(profile.role()).isEqualTo("reviewer");
        assertThat(profile.altRoles()).containsExactly("implementer");
        assertThat(profile.directiveReferences()).hasSize(2);
    }

    @Test
    @DisplayName("lineage is not minted from the profile: it has no relationships of its own")
    void mintsNoRelationships() {
        AgentProfile profile = valid("java-jenny")
                .directiveReference(new DirectiveReference("003", "Decision documentation", "why"))
                .tacticReference(new ArtifactReference("tdd-red-green", "why"))
                .build();

        assertThat(profile.derivedRelationships()).isEmpty();
    }

    @Test
    @DisplayName("a role outside the kebab grammar is rejected while a kebab role builds (ADR-0069)")
    void rolesFollowTheSchema() {
        assertThat(valid("p").role("qa-lead-2").build().role()).isEqualTo("qa-lead-2");

        assertThatThrownBy(() -> valid("p").role("Implementer").build())
                .isInstanceOfSatisfying(
                        ConstraintBreachException.class,
                        breach -> assertThat(breach.constraintDescription())
                                .isEqualTo("An agent profile's role and alt-roles must be lowercase letters, digits and"
                                        + " hyphens, starting with a letter, at most 64 characters."));
    }

    @Test
    @DisplayName("routing priority 0..100, concurrency above 0 and schema version 1.0 or 2 hold at their bounds")
    void boundsFollowTheSchema() {
        assertThat(valid("p").routingPriority(Optional.of(100)).build().routingPriority())
                .contains(100);
        assertThat(valid("p").schemaVersion(Optional.of("2")).build().schemaVersion())
                .contains("2");

        assertThatThrownBy(() -> valid("p").routingPriority(Optional.of(101)).build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> valid("p").routingPriority(Optional.of(-1)).build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> valid("p").maxConcurrentTasks(Optional.of(0)).build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> valid("p").schemaVersion(Optional.of("1.1")).build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a blank profile id is rejected")
    void blankIdRejected() {
        assertThatThrownBy(() -> new AgentProfileIdentifier(" ")).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("the map form of excluding keeps key order and an entry with no values")
    void exclusionKeepsItsData() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("capabilities", List.of("deploy"));
        values.put("alt-roles", List.of());

        Exclusion.FieldValues exclusion = new Exclusion.FieldValues(values);

        assertThat(exclusion.values())
                .containsExactly(Map.entry("capabilities", List.of("deploy")), Map.entry("alt-roles", List.of()));
        assertThat(valid("p")
                        .excluding(Optional.of(new Exclusion.WholeFields(List.of("capabilities"))))
                        .build()
                        .excluding())
                .contains(new Exclusion.WholeFields(List.of("capabilities")));
    }

    @Test
    @DisplayName("equality and hash are by identity, not by other fields")
    void identityEquality() {
        AgentProfile jenny = javaJenny().build();
        AgentProfile renamed = javaJenny().name("Jenny, renamed").build();

        assertThat(jenny).isEqualTo(renamed).hasSameHashCodeAs(renamed);
        assertThat(jenny)
                .isNotEqualTo(javaJenny()
                        .id(new AgentProfileIdentifier("java-jenny-2"))
                        .build());
    }
}
