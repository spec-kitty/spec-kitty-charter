package ai.speckitty.charter.missions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The substeps of a mission step (FR-004, operator ruling DM-01M7B1YH1GF15RD0G4ZPSA8KW9): each invariant with an accepted
 * neighbour and a refused twin.
 */
@DisplayName("Substeps on the mission step (FR-004)")
class SubstepTest {

    private static Substep substep(String id) {
        return new Substep(id, "Do it", Optional.empty(), List.of(), Optional.empty(), Optional.empty());
    }

    private static MissionStep step(List<Substep> substeps) {
        return new MissionStep(
                "implement",
                "Implement",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                substeps);
    }

    @ParameterizedTest(name = "the substep id ''{0}'' is accepted")
    @ValueSource(strings = {"bootstrap", "commit-plan", "supply-chain-security-check", "a1"})
    void kebabIdsAreAccepted(String id) {
        assertThatCode(() -> substep(id)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "the substep id ''{0}'' is refused")
    @ValueSource(strings = {"commit_plan", "Commit", "1st", "-a", "a-", "", "a b"})
    void otherIdsAreRefused(String id) {
        assertThatThrownBy(() -> substep(id)).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a blank description, command or guidance is refused")
    void blankTextsAreRefused() {
        assertThatThrownBy(() -> new Substep("a", " ", Optional.empty(), List.of(), Optional.empty(), Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new Substep("a", "d", Optional.of(""), List.of(), Optional.empty(), Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new Substep("a", "d", Optional.empty(), List.of(), Optional.empty(), Optional.of(" ")))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("an input needs a flag and a source")
    void anInputNeedsAFlagAndASource() {
        assertThat(new SubstepInput("--mission", "mission_slug", true).optional())
                .isTrue();
        assertThatThrownBy(() -> new SubstepInput("", "action", false)).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new SubstepInput("--action", " ", false))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @ParameterizedTest(name = "a substep delegates to a {0}")
    @EnumSource(
            value = ArtifactKind.class,
            names = {"DIRECTIVE", "TACTIC", "STYLEGUIDE", "TOOLGUIDE", "PARADIGM", "PROCEDURE"})
    void theGuidanceKindsAreDelegable(ArtifactKind kind) {
        assertThat(SubstepDelegation.delegable(kind)).isTrue();
        assertThatCode(() -> new SubstepDelegation(kind, List.of(SubstepCandidate.parse("x"))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "a substep does not delegate to a {0}")
    @EnumSource(
            value = ArtifactKind.class,
            names = {"DIRECTIVE", "TACTIC", "STYLEGUIDE", "TOOLGUIDE", "PARADIGM", "PROCEDURE"},
            mode = EnumSource.Mode.EXCLUDE)
    void noOtherKindIsDelegable(ArtifactKind kind) {
        assertThat(SubstepDelegation.delegable(kind)).isFalse();
        assertThatThrownBy(() -> new SubstepDelegation(kind, List.of(SubstepCandidate.parse("x"))))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a substep never delegates to an agent profile, though a scope edge from an action may reach one")
    void anAgentProfileIsNoDelegationKind() {
        // ADR-0032 decision 1, operator ruling DM-01M7DTN8JWR4G1P35NXGCMS65M: a mission type names roles, never
        // profiles.
        assertThat(RelationType.SCOPE.allows(NodeKind.ACTION, NodeKind.AGENT_PROFILE))
                .isTrue();
        assertThatThrownBy(
                        () -> new SubstepDelegation(ArtifactKind.AGENT_PROFILE, List.of(SubstepCandidate.parse("x"))))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("never to an agent_profile");
    }

    @Test
    @DisplayName("a delegation names at least one candidate")
    void aDelegationNamesACandidate() {
        assertThatThrownBy(() -> new SubstepDelegation(ArtifactKind.TACTIC, List.of()))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a delegation names each candidate once; the same id of another pack is another candidate")
    void aDelegationNamesEachCandidateOnce() {
        assertThatThrownBy(() -> new SubstepDelegation(
                        ArtifactKind.TACTIC, List.of(SubstepCandidate.parse("tdd"), SubstepCandidate.parse("tdd"))))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A substep delegation names each candidate once.");
        assertThatCode(() -> new SubstepDelegation(
                        ArtifactKind.TACTIC,
                        List.of(SubstepCandidate.parse("tdd"), SubstepCandidate.parse("base::tdd"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a candidate is written [<pack>::]<id>, and written back as it was read")
    void aCandidateIsBareOrQualified() {
        SubstepCandidate bare = SubstepCandidate.parse("DIRECTIVE_010");
        SubstepCandidate qualified = SubstepCandidate.parse("writing::tdd");

        assertThat(bare.qualified()).isFalse();
        assertThat(bare.id()).isEqualTo("DIRECTIVE_010");
        assertThat(bare).hasToString("DIRECTIVE_010");
        assertThat(qualified.qualified()).isTrue();
        assertThat(qualified.packName()).contains("writing");
        assertThat(qualified.id()).isEqualTo("tdd");
        assertThat(qualified).hasToString("writing::tdd");
    }

    @ParameterizedTest(name = "the candidate ''{0}'' is refused")
    @ValueSource(strings = {"", "::tdd", "a::b::c", "Writing::tdd", "tdd!", "-tdd", "writing::"})
    void malformedCandidatesAreRefused(String written) {
        assertThatThrownBy(() -> SubstepCandidate.parse(written)).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a candidate qualified by a pack name that is no pack id is refused, and a very long one cannot recurse")
    void aQualifierThatIsNoPackIdIsRefusedWithoutRecursion() {
        assertThat(SubstepCandidate.parse("a-b::tdd").packName()).contains("a-b");
        assertThatThrownBy(() -> SubstepCandidate.parse("a_b::tdd")).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> SubstepCandidate.parse("p".repeat(64) + "::tdd"))
                .isInstanceOf(ConstraintBreachException.class);
        // #440: the qualifier is matched with the kernel's possessive slug, so 20,000 runs do not overflow the stack.
        assertThatThrownBy(() -> SubstepCandidate.parse("a-".repeat(20_000) + "a::tdd"))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("the candidate grammar is the one the mission-type schema states")
    void theGrammarIsTheSchemas() throws IOException {
        String schema = Files.readString(
                Path.of("../../contracts/packs/schemas/mission-type.schema.json"), StandardCharsets.UTF_8);
        Matcher candidates = Pattern.compile("\"candidates\": \\{\\s*\"items\": \\{[^}]*\"pattern\": \"([^\"]*)\"")
                .matcher(schema);

        assertThat(candidates.find())
                .as("the schema states a candidates pattern")
                .isTrue();
        assertThat(candidates.group(1)).isEqualTo(SubstepCandidate.GRAMMAR);
    }

    @Test
    @DisplayName("a step keeps its substeps in authored order, and a substep id stands once in it")
    void aStepKeepsItsSubstepsInOrderAndOnce() {
        assertThat(step(List.of(substep("zeta"), substep("alpha"))).substeps())
                .extracting(Substep::id)
                .containsExactly("zeta", "alpha");
        assertThat(step(List.of()).substeps()).isEmpty();
        assertThatThrownBy(() -> step(List.of(substep("a"), substep("b"), substep("a"))))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A substep id stands once within its step.");
    }
}
