package ai.speckitty.charter.packs.domain.rules;

import static ai.speckitty.charter.packs.domain.rules.PlantedFixtures.facts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.MissionStepLinkRule;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.MissionTypeFacts.Delegation;
import ai.speckitty.charter.packs.domain.MissionTypeFacts.SubstepFacts;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Expected;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Planted;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("MissionTypeRules (#554, T033)")
class MissionTypeRulesTest {

    private static final String FILE = "missions/rx/rx.mission-type.yaml";

    private static final String SHARD = "mission_type.graph.yaml";

    private static final Set<String> CODES = Set.of(
            PackCodes.MISSION_STEP_UNLINKED.value(),
            PackCodes.SUBSTEP_DELEGATION_UNRESOLVED.value(),
            PackCodes.SUBSTEP_DELEGATION_UNSCOPED.value());

    private static RawEdge edge(String source, String target, String relation) {
        return new RawEdge(source, target, relation, Optional.empty(), Optional.empty());
    }

    private static final RawEdge LINKS_A = edge("mission_type:rx", "action:rx/a", "requires");

    private static MissionTypeFacts steps(String... ids) {
        return new MissionTypeFacts(FILE, "rx", List.of(ids), List.of());
    }

    private static MissionTypeFacts delegating(String... candidates) {
        return new MissionTypeFacts(
                FILE,
                "rx",
                List.of("a"),
                List.of(new SubstepFacts(
                        "steps", 0, "a", 0, "run", Optional.of(new Delegation("tactic", List.of(candidates))))));
    }

    private static FileRead<RawGraph> shard(RawEdge... edges) {
        return new FileRead.Read<>(SHARD, new RawGraph(SHARD, RawGraph.Shape.SHARD, List.of(), List.of(edges)));
    }

    /** Facts of a pack whose configured name and descriptor name may differ, with one shard. */
    private static PackContents named(
            String configured, FileRead<PackDescriptor> descriptor, MissionTypeFacts type, RawEdge edge) {
        return new PackContents(
                new PackRoot(configured, Optional.of(PackLevel.ORG), "loc"),
                descriptor,
                List.of(shard(edge)),
                new FileRead.Absent<>("drg/fragment.yaml"),
                List.of(),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(),
                List.of(type));
    }

    private static FileRead<PackDescriptor> declaring(String name) {
        return new FileRead.Read<>(
                "pack.yaml",
                new PackDescriptor(
                        "01ARWG13C00000000000001ABC",
                        "1.0.0",
                        Optional.empty(),
                        name,
                        Optional.of(PackLevel.ORG),
                        Optional.empty()));
    }

    // ---- STEP_UNLINKED ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a step with no requires edge is one violation at the type's file, in the link rule's words")
    void anUnlinkedStepIsOneFindingAtTheTypesFile() {
        PackContents contents = facts(List.of(), List.of(), List.of(steps("a", "b")), List.of(LINKS_A));

        assertThat(MissionTypeRules.STEP_UNLINKED.apply(contents))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackCodes.MISSION_STEP_UNLINKED);
                    assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
                    assertThat(finding.isFailing()).isTrue();
                    assertThat(finding.rule()).isEqualTo("mission_step_unlinked");
                    assertThat(finding.location()).isEqualTo(Location.in(FILE));
                    assertThat(finding.detail())
                            .isEqualTo(FILE + ": "
                                    + MissionStepLinkRule.judge(
                                                    "qa",
                                                    List.of(steps("a", "b")),
                                                    List.of(new MissionStepLinkRule.Edge(
                                                            "drg/fragment.yaml",
                                                            "mission_type:rx",
                                                            "action:rx/a",
                                                            "requires")))
                                            .getFirst()
                                            .reason());
                });
    }

    @Test
    @DisplayName("every step linked is no fault, the links read from a shard as well as from the fragment")
    void linkedStepsAreNoFault() {
        PackContents fromFragment = facts(List.of(), List.of(), List.of(steps("a")), List.of(LINKS_A));
        PackContents fromShard = named("qa", declaring("qa"), steps("a"), LINKS_A);

        assertThat(MissionTypeRules.STEP_UNLINKED.apply(fromFragment)).isEmpty();
        assertThat(MissionTypeRules.STEP_UNLINKED.apply(fromShard)).isEmpty();
    }

    @Test
    @DisplayName("a stray step edge is a violation at the graph document that holds it")
    void aStrayEdgeIsAFindingAtItsDocument() {
        PackContents contents =
                named("qa", declaring("qa"), steps("a"), edge("mission_type:rx", "action:rx/zz", "requires"));

        assertThat(MissionTypeRules.STEP_UNLINKED.apply(contents))
                .extracting(reason -> reason.location().file())
                .containsExactly(FILE, SHARD);
    }

    @Test
    @DisplayName("the pack's own name is the one its descriptor declares, else its configured name")
    void theOwnNameIsTheDescriptorsElseTheConfiguredOne() {
        RawEdge qualified = edge("declared::mission_type:rx", "action:rx/a", "requires");

        assertThat(MissionTypeRules.STEP_UNLINKED.apply(
                        named("configured", declaring("declared"), steps("a"), qualified)))
                .isEmpty();
        assertThat(MissionTypeRules.STEP_UNLINKED.apply(
                        named("declared", new FileRead.Absent<>("pack.yaml"), steps("a"), qualified)))
                .isEmpty();
        assertThat(MissionTypeRules.STEP_UNLINKED.apply(named("configured", declaring("other"), steps("a"), qualified)))
                .extracting(Reason::code)
                .containsExactly(PackCodes.MISSION_STEP_UNLINKED);
    }

    @Test
    @DisplayName("a type that repeats a substep id is not held, so its steps are not judged")
    void aTypeThatRepeatsASubstepIdIsNotJudged() {
        MissionTypeFacts repeats = new MissionTypeFacts(
                FILE,
                "rx",
                List.of("a", "b"),
                List.of(
                        new SubstepFacts("steps", 0, "a", 0, "r", Optional.empty()),
                        new SubstepFacts("steps", 0, "a", 1, "r", Optional.empty())));

        assertThat(MissionTypeRules.STEP_UNLINKED.apply(facts(List.of(), List.of(), List.of(repeats), List.of())))
                .isEmpty();
    }

    @Test
    @DisplayName("neither rule is judged when a graph document could not be read")
    void neitherRuleIsJudgedWhenAGraphCouldNotBeRead() {
        FileRead<RawGraph> unreadable =
                new FileRead.Unreadable<>("drg/fragment.yaml", new ParseFailure("drg/fragment.yaml", "bad"), List.of());
        PackContents contents =
                facts(List.of(), List.of(), List.of(steps("a", "b"), delegating("missing")), unreadable);

        assertThat(MissionTypeRules.STEP_UNLINKED.apply(contents)).isEmpty();
        assertThat(MissionTypeRules.SUBSTEP_DELEGATION.apply(contents)).isEmpty();
        assertThat(MissionTypeRules.RULES.apply(contents)).isEmpty();
    }

    // ---- SUBSTEP_DELEGATION -------------------------------------------------------------------------------------

    @Test
    @DisplayName("a candidate the pack does not hold is unresolved, one held but not scoped is unscoped, at its place")
    void unresolvedAndUnscopedCandidates() {
        PackContents contents = facts(
                List.of(
                        new ArtifactFacts("tactics/held.tactic.yaml", "tactic", "held"),
                        new ArtifactFacts("tactics/scoped.tactic.yaml", "tactic", "scoped")),
                List.of(),
                List.of(delegating("missing", "held", "scoped")),
                List.of(LINKS_A, edge("action:rx/a", "tactic:scoped", "scope")));

        List<Reason> findings = MissionTypeRules.SUBSTEP_DELEGATION.apply(contents);

        assertThat(findings)
                .extracting(Reason::code, Reason::location)
                .containsExactly(
                        tuple(
                                PackCodes.SUBSTEP_DELEGATION_UNRESOLVED,
                                Location.in(FILE).at("/steps/0/substeps/0/delegates_to/candidates/0")),
                        tuple(
                                PackCodes.SUBSTEP_DELEGATION_UNSCOPED,
                                Location.in(FILE).at("/steps/0/substeps/0/delegates_to/candidates/1")));
        assertThat(findings).allSatisfy(finding -> {
            assertThat(finding.isFailing()).isTrue();
            assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
            assertThat(finding.rule()).isEqualTo(finding.code().value());
            assertThat(finding.detail()).startsWith(FILE + ": ");
        });
    }

    @Test
    @DisplayName("a candidate held and scoped is no fault")
    void aHeldScopedCandidateIsNoFault() {
        PackContents contents = facts(
                List.of(new ArtifactFacts("tactics/tdd.tactic.yaml", "tactic", "tdd")),
                List.of(),
                List.of(delegating("tdd")),
                List.of(LINKS_A, edge("action:rx/a", "tactic:tdd", "scope")));

        assertThat(MissionTypeRules.SUBSTEP_DELEGATION.apply(contents)).isEmpty();
        assertThat(MissionTypeRules.RULES.apply(contents)).isEmpty();
    }

    @Test
    @DisplayName("the holder's ruleset is the link rule, then the delegation rule")
    void theRulesetIsItsRules() {
        MissionTypeFacts type = new MissionTypeFacts(
                FILE, "rx", List.of("a", "b"), delegating("x").substeps());
        PackContents contents = facts(List.of(), List.of(), List.of(type), List.of(LINKS_A));

        assertThat(MissionTypeRules.RULES.apply(contents))
                .extracting(Reason::code)
                .containsExactly(PackCodes.MISSION_STEP_UNLINKED, PackCodes.SUBSTEP_DELEGATION_UNRESOLVED);
    }

    // ---- the planted packs --------------------------------------------------------------------------------------

    static List<Planted> planted() {
        return PlantedFixtures.all();
    }

    static List<Planted> expecting() {
        return PlantedFixtures.expecting(CODES);
    }

    @ParameterizedTest
    @MethodSource("expecting")
    @DisplayName("a plant's expected.json is the oracle of the rules' findings")
    void thePlantsExpectedFindingsAreTheOracle(Planted planted) {
        assertThat(MissionTypeRules.RULES.apply(planted.contents()))
                .extracting(Expected::of)
                .containsExactlyElementsOf(planted.expected().orElseThrow().stream()
                        .filter(expected -> CODES.contains(expected.code()))
                        .toList());
    }
}
