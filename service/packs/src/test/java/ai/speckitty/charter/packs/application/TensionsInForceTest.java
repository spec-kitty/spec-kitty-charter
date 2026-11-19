package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.EdgeIndexTest.edge;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.urn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndexTest.Closure;
import ai.speckitty.charter.packs.application.TensionsInForce.TensionInForce;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The tensions in force (ADR-0045 decisions 8 to 10, rulings G1 and G2): resolved symmetrically from the authored edge
 * through the layer merge, kept when both sides are in force, answered from the smaller URN, collapsed on the pair with
 * the first writer's words, each with the artifacts in force that reconcile both sides.
 */
class TensionsInForceTest {

    private static final List<String> PAIR = List.of("directive:A", "directive:B");

    private static Predicate<Urn> inForce(String... urns) {
        Set<Urn> set = Set.copyOf(Stream.of(urns).map(EdgeIndexTest::urn).toList());
        return set::contains;
    }

    private static boolean all(Urn urn) {
        return true;
    }

    /** A tension as written, with words. */
    private static Relationship tension(String from, String to, String when, String reason) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return Relationship.of(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                RelationType.TENSION,
                Optional.ofNullable(when),
                Optional.ofNullable(reason));
    }

    private static Relationship tension(String from, String to) {
        return edge(from, RelationType.TENSION, to);
    }

    private static TensionInForce expected(String source, String target, String... reconcilers) {
        return new TensionInForce(
                urn(source),
                urn(target),
                Optional.empty(),
                Optional.empty(),
                new TreeSet<>(Stream.of(reconcilers).map(EdgeIndexTest::urn).toList()));
    }

    @Test
    void aTensionBetweenTwoArtifactsInForceIsInForceFromTheSmallerUrnWithItsWords() {
        Closure closure = new Closure()
                .pack("alpha", PAIR, tension("directive:A", "directive:B", "when shipping", "speed against care"));

        TensionsInForce tensions = closure.tensions(TensionsInForceTest::all);

        assertThat(tensions.all())
                .containsExactly(new TensionInForce(
                        urn("directive:A"),
                        urn("directive:B"),
                        Optional.of("when shipping"),
                        Optional.of("speed against care"),
                        new TreeSet<>()));
        assertThat(tensions.unreconciled()).isEqualTo(tensions.all());
    }

    @Test
    void aTensionWithOnlyOneSideInForceIsAbsentAndWithBothItIsPresent() {
        Closure closure = new Closure().pack("alpha", PAIR, tension("directive:A", "directive:B"));

        assertThat(closure.tensions(inForce("directive:A")).all()).isEmpty();
        assertThat(closure.tensions(inForce("directive:B")).all()).isEmpty();
        assertThat(closure.tensions(inForce("directive:A", "directive:B")).all())
                .containsExactly(expected("directive:A", "directive:B"));
    }

    /**
     * G2 symmetry: a later level's version of either side, whichever is the smaller URN, makes the authoring pack's
     * tension absent; the version it holds no longer stands for the pair it wrote.
     */
    @ParameterizedTest(name = "the project overrides {0}")
    @ValueSource(strings = {"directive:A", "directive:B"})
    void anOverrideOfEitherSideOfTheAuthoringPacksOwnVersionMakesTheTensionAbsent(String overridden) {
        Closure overriddenClosure = new Closure()
                .pack("org", PackLevel.ORG, PAIR, tension("directive:A", "directive:B"))
                .pack("project", PackLevel.PROJECT, List.of(overridden));
        Closure control = new Closure()
                .pack("org", PackLevel.ORG, PAIR, tension("directive:A", "directive:B"))
                .pack("project", PackLevel.PROJECT, List.of("directive:C"));

        assertThat(overriddenClosure.tensions(TensionsInForceTest::all).all()).isEmpty();
        assertThat(control.tensions(TensionsInForceTest::all).all())
                .containsExactly(expected("directive:A", "directive:B"));
    }

    @Test
    void aTensionOnTwoSidesTheAuthoringPackDoesNotHoldIsRepointedToTheVersionsInForce() {
        Closure closure = new Closure()
                .pack("core", PackLevel.BUILT_IN, PAIR)
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("core"),
                        List.of("directive:C"),
                        tension("core::directive:A", "core::directive:B"))
                .pack("project", PackLevel.PROJECT, List.of("directive:B"));

        assertThat(closure.tensions(TensionsInForceTest::all).all())
                .containsExactly(expected("directive:A", "directive:B"));
    }

    @Test
    void aTensionOfAnOwnSideWithAForeignSideIsAbsentOnlyWhenTheOwnSideLoses() {
        Closure foreignSideOverridden = new Closure()
                .pack("core", PackLevel.BUILT_IN, List.of("directive:B"))
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("core"),
                        List.of("directive:A"),
                        tension("directive:A", "core::directive:B"))
                .pack("project", PackLevel.PROJECT, List.of("directive:B"));
        Closure ownSideOverridden = new Closure()
                .pack("core", PackLevel.BUILT_IN, List.of("directive:B"))
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("core"),
                        List.of("directive:A"),
                        tension("directive:A", "core::directive:B"))
                .pack("project", PackLevel.PROJECT, List.of("directive:A"));

        assertThat(foreignSideOverridden.tensions(TensionsInForceTest::all).all())
                .containsExactly(expected("directive:A", "directive:B"));
        assertThat(ownSideOverridden.tensions(TensionsInForceTest::all).all()).isEmpty();
    }

    @Test
    void twoPacksWritingOnePairCollapseToOneTensionWithTheFirstWritersWords() {
        Closure closure = new Closure()
                .pack("core", PackLevel.BUILT_IN, PAIR, tension("directive:A", "directive:B", "first", "first reason"))
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("core"),
                        List.of(),
                        tension("core::directive:A", "core::directive:B", "second", "second reason"));

        assertThat(closure.tensions(TensionsInForceTest::all).all())
                .containsExactly(new TensionInForce(
                        urn("directive:A"),
                        urn("directive:B"),
                        Optional.of("first"),
                        Optional.of("first reason"),
                        new TreeSet<>()));
    }

    @Test
    void aTensionWrittenEitherWayRoundIsOneTensionFromTheSmallerUrnAndNeverASelfPair() {
        Closure closure = new Closure()
                .pack("core", PackLevel.BUILT_IN, List.of("directive:A"))
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("core"),
                        List.of("directive:B"),
                        tension("directive:B", "core::directive:A"));
        Closure control = new Closure()
                .pack("core", PackLevel.BUILT_IN, List.of("directive:A"))
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("core"),
                        List.of("directive:B"),
                        tension("core::directive:A", "directive:B"));

        // A self-pair cannot be written (the kernel refuses it, tension_self), and a tension in force refuses one.
        assertThatThrownBy(() -> new TensionInForce(
                        urn("directive:A"), urn("directive:A"), Optional.empty(), Optional.empty(), new TreeSet<>()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(closure.tensions(TensionsInForceTest::all).all())
                .containsExactly(expected("directive:A", "directive:B"));
        assertThat(control.tensions(TensionsInForceTest::all)).isEqualTo(closure.tensions(TensionsInForceTest::all));
    }

    @Test
    void aReconcilerInForceWithLinksToBothSidesIsListedAndOneWithASingleLinkOrNotInForceIsNot() {
        Closure closure = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:A", "directive:B", "tactic:R1", "tactic:R2", "tactic:HALF", "tactic:OUT"),
                        tension("directive:A", "directive:B"),
                        edge("tactic:R2", RelationType.RECONCILES_TENSION, "directive:A"),
                        edge("tactic:R2", RelationType.RECONCILES_TENSION, "directive:B"),
                        edge("tactic:R1", RelationType.RECONCILES_TENSION, "directive:B"),
                        edge("tactic:R1", RelationType.RECONCILES_TENSION, "directive:A"),
                        edge("tactic:HALF", RelationType.RECONCILES_TENSION, "directive:A"),
                        edge("tactic:OUT", RelationType.RECONCILES_TENSION, "directive:A"),
                        edge("tactic:OUT", RelationType.RECONCILES_TENSION, "directive:B"));

        TensionsInForce tensions =
                closure.tensions(inForce("directive:A", "directive:B", "tactic:R1", "tactic:R2", "tactic:HALF"));

        assertThat(tensions.all()).containsExactly(expected("directive:A", "directive:B", "tactic:R1", "tactic:R2"));
        assertThat(tensions.all().getFirst().reconciledBy()).containsExactly(urn("tactic:R1"), urn("tactic:R2"));
        assertThat(tensions.unreconciled()).isEmpty();
    }

    @Test
    void aTensionAndARequiresOnOnePairAreSeparateAndOnlyTheTensionIsInForceHere() {
        Closure closure = new Closure()
                .pack(
                        "alpha",
                        PAIR,
                        tension("directive:A", "directive:B"),
                        edge("directive:B", RelationType.REQUIRES, "directive:A"));

        assertThat(closure.tensions(TensionsInForceTest::all).all())
                .containsExactly(expected("directive:A", "directive:B"));
    }

    @Test
    void deliveredToKeepsTheTensionsWhoseTwoSidesAreListed() {
        Closure closure = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:A", "directive:B", "directive:C"),
                        tension("directive:A", "directive:B"),
                        tension("directive:A", "directive:C"));
        TensionsInForce tensions = closure.tensions(TensionsInForceTest::all);

        assertThat(tensions.deliveredTo(Set.of(urn("directive:A"), urn("directive:C"))))
                .containsExactly(expected("directive:A", "directive:C"));
        assertThat(tensions.deliveredTo(Set.of(urn("directive:A")))).isEmpty();
        assertThat(tensions.all()).hasSize(2);
    }

    /** NFR-001: the answer does not depend on the order the edges or the held artifacts were written in. */
    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 7, 42, 1_000, 65_537})
    void theAnswerIsTheSameWhateverTheOrderTheEdgesAndArtifactsWereWrittenIn(long seed) {
        List<Relationship> written = List.of(
                tension("directive:A", "directive:B", "w", "r"),
                tension("directive:C", "directive:D"),
                tension("directive:A", "directive:D"),
                edge("tactic:R", RelationType.RECONCILES_TENSION, "directive:A"),
                edge("tactic:R", RelationType.RECONCILES_TENSION, "directive:D"),
                edge("tactic:S", RelationType.RECONCILES_TENSION, "directive:C"),
                edge("tactic:S", RelationType.RECONCILES_TENSION, "directive:D"));
        List<String> held = List.of("directive:A", "directive:B", "directive:C", "directive:D", "tactic:R", "tactic:S");
        TensionsInForce reference = new Closure()
                .pack("alpha", held, written.toArray(Relationship[]::new))
                .tensions(TensionsInForceTest::all);

        Random random = new Random(seed);
        List<Relationship> shuffledEdges = new ArrayList<>(written);
        List<String> shuffledHeld = new ArrayList<>(held);
        Collections.shuffle(shuffledEdges, random);
        Collections.shuffle(shuffledHeld, random);
        TensionsInForce shuffled = new Closure()
                .pack("alpha", shuffledHeld, shuffledEdges.toArray(Relationship[]::new))
                .tensions(TensionsInForceTest::all);

        assertThat(shuffled).isEqualTo(reference);
        assertThat(reference.all())
                .extracting(TensionInForce::source, TensionInForce::target)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(urn("directive:A"), urn("directive:B")),
                        org.assertj.core.groups.Tuple.tuple(urn("directive:A"), urn("directive:D")),
                        org.assertj.core.groups.Tuple.tuple(urn("directive:C"), urn("directive:D")));
        assertThat(reference.all().get(1).reconciledBy()).containsExactly(urn("tactic:R"));
        assertThat(reference.all().get(2).reconciledBy()).containsExactly(urn("tactic:S"));
        assertThat(reference.unreconciled()).extracting(TensionInForce::source).containsExactly(urn("directive:A"));
    }
}
