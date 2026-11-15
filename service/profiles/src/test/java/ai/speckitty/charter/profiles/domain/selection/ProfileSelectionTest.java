package ai.speckitty.charter.profiles.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Performer;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.profiles.domain.SpecializationContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** ADR-0069 decision 4 as ADR-0078 rules it (D2 to D4): the selection order, one test per step. */
@DisplayName("ProfileSelection (ADR-0069 d4, ADR-0078)")
class ProfileSelectionTest {

    private static final String BUILT_IN = "built-in";
    private static final String ORG_BASE = "org-base";
    private static final String PROJECT = "project";

    // The example packs' profiles, as plain builders: built-in at closure position 0, org-base 1, project 2.
    private static final SelectionCandidate IVAN = candidate(
            profile("implementer-ivan", "implementer")
                    .routingPriority(Optional.of(50))
                    .specializationContext(context(
                            List.of("python", "typescript", "javascript", "java", "go", "rust"),
                            List.of("pytest", "junit"))),
            BUILT_IN,
            0,
            0);
    private static final SelectionCandidate JENNY = candidate(
            profile("java-jenny", "implementer")
                    .routingPriority(Optional.of(80))
                    .specializationContext(context(List.of("java"), List.of("junit5", "assertj", "spring"))),
            BUILT_IN,
            0,
            1);
    private static final SelectionCandidate PEDRO = candidate(
            profile("python-pedro", "implementer")
                    .routingPriority(Optional.of(80))
                    .specializationContext(context(List.of("python"), List.of("pytest", "pydantic"))),
            BUILT_IN,
            0,
            1);
    private static final SelectionCandidate RENATA = candidate(
            profile("reviewer-renata", "reviewer")
                    .routingPriority(Optional.of(50))
                    .specializationContext(context(List.of("java", "python"), List.of())),
            BUILT_IN,
            0,
            0);
    private static final SelectionCandidate RITA = candidate(profile("reviewer-rita", "reviewer"), ORG_BASE, 1, 0);
    private static final SelectionCandidate HUMAN_OPERATOR = candidate(
            profile("human-operator", AgentProfile.OPERATOR_ROLE).performer(Optional.of(Performer.HUMAN)),
            ORG_BASE,
            1,
            0);
    private static final SelectionCandidate HUMAN_REVIEWER =
            candidate(profile("human-reviewer", "reviewer").performer(Optional.of(Performer.HUMAN)), ORG_BASE, 1, 0);
    private static final SelectionCandidate PROJECT_REVIEWER =
            candidate(profile("project-reviewer", "reviewer").altRole("implementer"), PROJECT, 2, 0);
    private static final SelectionCandidate GENERIC = candidate(
            profile("generic-agent", AgentProfile.GENERIC_ROLE).routingPriority(Optional.of(10)), BUILT_IN, 0, 0);

    private static final List<SelectionCandidate> EXAMPLE_PACKS =
            List.of(IVAN, JENNY, PEDRO, RENATA, HUMAN_OPERATOR, PROJECT_REVIEWER);

    private static AgentProfile.Builder profile(String id, String role) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(new PackIdentifier(BUILT_IN))
                .name("Name of " + id)
                .purpose("Purpose of " + id)
                .role(role)
                .specialization(new Specialization("Focus", Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private static SpecializationContext context(List<String> languages, List<String> frameworks) {
        return new SpecializationContext(languages, frameworks, List.of(), List.of(), List.of(), List.of());
    }

    private static SelectionCandidate candidate(AgentProfile.Builder profile, String pack, int position, int depth) {
        return new SelectionCandidate(profile.build(), pack, position, depth);
    }

    private static SelectionContext language(String language) {
        return new SelectionContext(Optional.of(language), Optional.empty());
    }

    private static SelectionContext framework(String framework) {
        return new SelectionContext(Optional.empty(), Optional.of(framework));
    }

    private static Candidates unpinned(List<SelectionCandidate> candidates) {
        return Candidates.unpinned(candidates, Optional.of(GENERIC));
    }

    private static Candidates pinned(List<SelectionCandidate> candidates, String pin, Set<String> specializations) {
        return new Candidates(candidates, Optional.of(pin), specializations, Optional.of(GENERIC));
    }

    private static SelectionAnswer.Matched single(String id, String pack, Fit fit) {
        return new SelectionAnswer.Matched(List.of(new Pick(id, pack, fit)), false);
    }

    private static final SelectionAnswer.Matched FALLBACK = single("generic-agent", BUILT_IN, Fit.FALLBACK);

    @Nested
    @DisplayName("step 1, reserved roles")
    class Reserved {

        @Test
        @DisplayName("generic is refused: it is served only as a fallback")
        void genericRefused() {
            assertThatThrownBy(() -> ProfileSelection.select(
                            AgentProfile.GENERIC_ROLE, SelectionContext.NONE, unpinned(EXAMPLE_PACKS)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("generic");
        }

        @Test
        @DisplayName("operator answers the human operator")
        void operatorAnswersHuman() {
            assertThat(ProfileSelection.select(
                            AgentProfile.OPERATOR_ROLE, SelectionContext.NONE, unpinned(EXAMPLE_PACKS)))
                    .isEqualTo(single("human-operator", ORG_BASE, Fit.PRIMARY));
        }

        @Test
        @DisplayName(
                "operator keeps human candidates: an unpinned human alt-operator is not dropped by the performer step")
        void operatorKeepsHumansOnly() {
            SelectionCandidate humanAlt = candidate(
                    profile("lead", "reviewer")
                            .altRole(AgentProfile.OPERATOR_ROLE)
                            .performer(Optional.of(Performer.HUMAN)),
                    ORG_BASE,
                    1,
                    0);

            assertThat(ProfileSelection.select(
                            AgentProfile.OPERATOR_ROLE, SelectionContext.NONE, unpinned(List.of(IVAN, humanAlt))))
                    .isEqualTo(single("lead", ORG_BASE, Fit.ALT));
        }

        @Test
        @DisplayName("operator with no human operator is no match, never the fallback")
        void operatorNeverFallsBack() {
            assertThat(ProfileSelection.select(
                            AgentProfile.OPERATOR_ROLE, SelectionContext.NONE, unpinned(List.of(IVAN, RENATA))))
                    .isEqualTo(new SelectionAnswer.NoMatch());
        }
    }

    @Nested
    @DisplayName("step 2, pin")
    class Pin {

        @Test
        @DisplayName("a pin to renata excludes project-reviewer")
        void pinExcludesOthers() {
            assertThat(ProfileSelection.select(
                            "reviewer",
                            SelectionContext.NONE,
                            pinned(List.of(RENATA, RITA, PROJECT_REVIEWER), "reviewer-renata", Set.of())))
                    .isEqualTo(single("reviewer-renata", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("a pin to a profile with a deeper specialization that fits: the specialization wins")
        void pinNarrowsToDeeperSpecialization() {
            assertThat(ProfileSelection.select(
                            "implementer",
                            language("java"),
                            pinned(EXAMPLE_PACKS, "implementer-ivan", Set.of("java-jenny", "python-pedro"))))
                    .isEqualTo(single("java-jenny", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("a pinned specialization fits any context, though it declares languages")
        void pinnedSpecializationFitsAnyContext() {
            assertThat(ProfileSelection.select(
                            "implementer", language("kotlin"), pinned(EXAMPLE_PACKS, "java-jenny", Set.of())))
                    .isEqualTo(single("java-jenny", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("the pinned profile is never among the tied: a same-tier specialization is deeper (D2)")
        void pinNeverTies() {
            SelectionCandidate kotlinKim = candidate(
                    profile("kotlin-kim", "implementer").specializationContext(context(List.of("kotlin"), List.of())),
                    PROJECT,
                    2,
                    1);

            SelectionAnswer answer = ProfileSelection.select(
                    "implementer",
                    language("kotlin"),
                    pinned(List.of(IVAN, kotlinKim), "implementer-ivan", Set.of("kotlin-kim")));

            assertThat(answer).isEqualTo(single("kotlin-kim", PROJECT, Fit.PRIMARY));
        }

        @Test
        @DisplayName("specializations without a pin are refused")
        void specializationsNeedPin() {
            assertThatThrownBy(
                            () -> new Candidates(List.of(), Optional.empty(), Set.of("java-jenny"), Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("step 3, performer")
    class PerformerStep {

        @Test
        @DisplayName("a human reviewer without a pin is excluded")
        void humanUnpinnedExcluded() {
            assertThat(ProfileSelection.select(
                            "reviewer", SelectionContext.NONE, unpinned(List.of(HUMAN_REVIEWER, RENATA))))
                    .isEqualTo(single("reviewer-renata", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("a human reviewer pinned is answered")
        void humanPinnedAnswered() {
            assertThat(ProfileSelection.select(
                            "reviewer",
                            SelectionContext.NONE,
                            pinned(List.of(HUMAN_REVIEWER, RENATA), "human-reviewer", Set.of())))
                    .isEqualTo(single("human-reviewer", ORG_BASE, Fit.PRIMARY));
        }

        @Test
        @DisplayName("an agent declared as such is kept like an absent performer")
        void declaredAgentKept() {
            SelectionCandidate agent = candidate(
                    profile("agent-reviewer", "reviewer").performer(Optional.of(Performer.AGENT)), PROJECT, 2, 0);

            assertThat(ProfileSelection.select("reviewer", SelectionContext.NONE, unpinned(List.of(agent))))
                    .isEqualTo(single("agent-reviewer", PROJECT, Fit.PRIMARY));
        }
    }

    @Nested
    @DisplayName("step 4, role fit")
    class RoleFitStep {

        @Test
        @DisplayName("implementer answers ivan: the reviewers and the operator do not take it as primary")
        void implementerAnswersIvan() {
            assertThat(ProfileSelection.select("implementer", SelectionContext.NONE, unpinned(EXAMPLE_PACKS)))
                    .isEqualTo(single("implementer-ivan", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("facilitator, which no candidate takes, answers the fallback")
        void facilitatorFallsBack() {
            assertThat(ProfileSelection.select("facilitator", SelectionContext.NONE, unpinned(EXAMPLE_PACKS)))
                    .isEqualTo(FALLBACK);
        }
    }

    @Nested
    @DisplayName("step 5, context")
    class ContextStep {

        @Test
        @DisplayName("implementer with language java answers jenny")
        void javaAnswersJenny() {
            assertThat(ProfileSelection.select("implementer", language("java"), unpinned(EXAMPLE_PACKS)))
                    .isEqualTo(single("java-jenny", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("implementer with language kotlin answers ivan: a root fits any context")
        void kotlinAnswersIvan() {
            assertThat(ProfileSelection.select("implementer", language("kotlin"), unpinned(EXAMPLE_PACKS)))
                    .isEqualTo(single("implementer-ivan", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("alt-only: every primary is filtered out by context, so the alt candidate is answered")
        void altOnly() {
            assertThat(ProfileSelection.select(
                            "implementer", language("kotlin"), unpinned(List.of(JENNY, PEDRO, PROJECT_REVIEWER))))
                    .isEqualTo(single("project-reviewer", PROJECT, Fit.ALT));
        }

        @Test
        @DisplayName("a declared framework fits when it holds the asked framework")
        void frameworkMatches() {
            assertThat(ProfileSelection.select("implementer", framework("pydantic"), unpinned(EXAMPLE_PACKS)))
                    .isEqualTo(single("python-pedro", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("every asked dimension it declares must match: java with pytest fits neither specialist")
        void everyDeclaredDimensionMustMatch() {
            SelectionContext javaPytest = new SelectionContext(Optional.of("java"), Optional.of("pytest"));

            assertThat(ProfileSelection.select("implementer", javaPytest, unpinned(EXAMPLE_PACKS)))
                    .isEqualTo(single("implementer-ivan", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("both dimensions matching fit")
        void bothDimensionsMatch() {
            SelectionContext javaSpring = new SelectionContext(Optional.of("java"), Optional.of("spring"));

            assertThat(ProfileSelection.select("implementer", javaSpring, unpinned(EXAMPLE_PACKS)))
                    .isEqualTo(single("java-jenny", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("an undeclared asked dimension is not judged; a declared one still decides")
        void undeclaredDimensionNotJudged() {
            SelectionCandidate springSam = candidate(
                    profile("spring-sam", "implementer").specializationContext(context(List.of(), List.of("spring"))),
                    PROJECT,
                    2,
                    1);
            SelectionContext javaSpring = new SelectionContext(Optional.of("java"), Optional.of("spring"));

            assertThat(ProfileSelection.select("implementer", javaSpring, unpinned(List.of(IVAN, springSam))))
                    .isEqualTo(single("spring-sam", PROJECT, Fit.PRIMARY));
            assertThat(ProfileSelection.select("implementer", language("java"), unpinned(List.of(IVAN, springSam))))
                    .isEqualTo(single("implementer-ivan", BUILT_IN, Fit.PRIMARY));
        }

        @Test
        @DisplayName("no asked context: a specialist that declares one is filtered out")
        void noContextFiltersSpecialists() {
            assertThat(ProfileSelection.select("implementer", SelectionContext.NONE, unpinned(List.of(JENNY))))
                    .isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("a descriptor-free specialization fits any context")
        void descriptorFreeFitsAnyContext() {
            SelectionCandidate refined = candidate(profile("ivan-refined", "implementer"), PROJECT, 2, 1);

            assertThat(ProfileSelection.select("implementer", language("kotlin"), unpinned(List.of(IVAN, refined))))
                    .isEqualTo(single("ivan-refined", PROJECT, Fit.PRIMARY));
        }
    }

    @Nested
    @DisplayName("step 6, tier")
    class Tier {

        @Test
        @DisplayName("a primary fit outranks an alt fit, whatever its depth and priority")
        void primaryBeforeAlt() {
            SelectionCandidate deepAlt = candidate(
                    profile("deep-alt", "reviewer").altRole("implementer").routingPriority(Optional.of(100)),
                    PROJECT,
                    2,
                    3);

            assertThat(ProfileSelection.select("implementer", SelectionContext.NONE, unpinned(List.of(deepAlt, IVAN))))
                    .isEqualTo(single("implementer-ivan", BUILT_IN, Fit.PRIMARY));
        }
    }

    @Nested
    @DisplayName("step 7, depth")
    class Depth {

        @Test
        @DisplayName("the longest in-force chain wins over a higher priority")
        void deepestWins() {
            SelectionCandidate shallow =
                    candidate(profile("shallow", "implementer").routingPriority(Optional.of(100)), PROJECT, 2, 1);
            SelectionCandidate deep = candidate(profile("deep", "implementer"), PROJECT, 2, 2);

            assertThat(ProfileSelection.select("implementer", SelectionContext.NONE, unpinned(List.of(shallow, deep))))
                    .isEqualTo(single("deep", PROJECT, Fit.PRIMARY));
        }
    }

    @Nested
    @DisplayName("step 8, priority")
    class Priority {

        @Test
        @DisplayName("the highest routing priority wins")
        void highestWins() {
            SelectionCandidate low =
                    candidate(profile("low", "reviewer").routingPriority(Optional.of(49)), PROJECT, 2, 0);

            assertThat(ProfileSelection.select("reviewer", SelectionContext.NONE, unpinned(List.of(low, RITA))))
                    .isEqualTo(single("reviewer-rita", ORG_BASE, Fit.PRIMARY));
        }

        @Test
        @DisplayName("an absent routing priority counts as 50")
        void absentCountsAsFifty() {
            SelectionCandidate high =
                    candidate(profile("high", "reviewer").routingPriority(Optional.of(51)), PROJECT, 2, 0);

            assertThat(ProfileSelection.select("reviewer", SelectionContext.NONE, unpinned(List.of(RITA, high))))
                    .isEqualTo(single("high", PROJECT, Fit.PRIMARY));
            assertThat(ProfileSelection.select("reviewer", SelectionContext.NONE, unpinned(List.of(RITA, RENATA))))
                    .isInstanceOfSatisfying(
                            SelectionAnswer.Matched.class,
                            answer -> assertThat(answer.tie()).isTrue());
            assertThat(ProfileSelection.DEFAULT_ROUTING_PRIORITY).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("step 9, tie")
    class Tie {

        @Test
        @DisplayName("renata and rita tie, answered in closure order whatever the input order")
        void renataAndRitaTie() {
            SelectionAnswer.Matched expected = new SelectionAnswer.Matched(
                    List.of(
                            new Pick("reviewer-renata", BUILT_IN, Fit.PRIMARY),
                            new Pick("reviewer-rita", ORG_BASE, Fit.PRIMARY)),
                    true);

            assertThat(ProfileSelection.select("reviewer", SelectionContext.NONE, unpinned(List.of(RITA, RENATA))))
                    .isEqualTo(expected);
            assertThat(ProfileSelection.select("reviewer", SelectionContext.NONE, unpinned(List.of(RENATA, RITA))))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("a tie in one pack is sorted by id")
        void sameClosurePositionSortedById() {
            SelectionCandidate zed = candidate(profile("zed", "planner"), PROJECT, 2, 0);
            SelectionCandidate abe = candidate(profile("abe", "planner"), PROJECT, 2, 0);

            assertThat(ProfileSelection.select("planner", SelectionContext.NONE, unpinned(List.of(zed, abe))))
                    .isEqualTo(new SelectionAnswer.Matched(
                            List.of(new Pick("abe", PROJECT, Fit.PRIMARY), new Pick("zed", PROJECT, Fit.PRIMARY)),
                            true));
        }

        @Test
        @DisplayName("closure position orders a tie before id")
        void closurePositionBeforeId() {
            SelectionCandidate abe = candidate(profile("abe", "planner"), PROJECT, 2, 0);
            SelectionCandidate zed = candidate(profile("zed", "planner"), BUILT_IN, 0, 0);

            assertThat(ProfileSelection.select("planner", SelectionContext.NONE, unpinned(List.of(abe, zed))))
                    .isEqualTo(new SelectionAnswer.Matched(
                            List.of(new Pick("zed", BUILT_IN, Fit.PRIMARY), new Pick("abe", PROJECT, Fit.PRIMARY)),
                            true));
        }

        @Test
        @DisplayName("a match holds one pick, or a tie of more than one")
        void matchedShape() {
            Pick pick = new Pick("a", PROJECT, Fit.PRIMARY);

            assertThatThrownBy(() -> new SelectionAnswer.Matched(List.of(), false))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SelectionAnswer.Matched(List.of(pick), true))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SelectionAnswer.Matched(List.of(pick, pick), false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("step 10, none")
    class None {

        @Test
        @DisplayName("no candidate fits: the fallback, marked fallback")
        void fallback() {
            assertThat(ProfileSelection.select("facilitator", SelectionContext.NONE, unpinned(List.of())))
                    .isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("no candidate fits and no fallback: no match")
        void noFallbackNoMatch() {
            assertThat(ProfileSelection.select(
                            "facilitator", SelectionContext.NONE, Candidates.unpinned(EXAMPLE_PACKS, Optional.empty())))
                    .isEqualTo(new SelectionAnswer.NoMatch());
        }
    }

    @Nested
    @DisplayName("value types")
    class Values {

        @Test
        @DisplayName("a candidate's closure position and depth must not be negative")
        void candidateBounds() {
            AgentProfile ivan = IVAN.profile();

            assertThatThrownBy(() -> new SelectionCandidate(ivan, BUILT_IN, -1, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SelectionCandidate(ivan, BUILT_IN, 0, -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new SelectionCandidate(ivan, BUILT_IN, 0, 0).profileId()).isEqualTo("implementer-ivan");
        }

        @Test
        @DisplayName("a pick and a context need every part")
        void partsRequired() {
            assertThatThrownBy(() -> new Pick("a", PROJECT, null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new SelectionContext(Optional.empty(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
