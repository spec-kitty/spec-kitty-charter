package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.Descent;
import ai.speckitty.charter.kernel.domain.GraphWalker;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.graph.ArtifactNode;
import ai.speckitty.charter.packs.domain.graph.EdgeKind;
import ai.speckitty.charter.packs.domain.graph.EdgeNode;
import ai.speckitty.charter.packs.domain.graph.PackGraphNode;
import ai.speckitty.charter.packs.domain.graph.PackNode;
import ai.speckitty.charter.packs.domain.graph.Scope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one driver of a walk over the pack graph (ADR-0082 decision 2, #638): it visits the composition closure of a
 * scope's roots once each, in the graph's order, hands the visitor only the elements the scope selects, emits the edges
 * the walk mode follows in declared order, obeys {@link Descent}, and reads a pack's facts only when the mode needs
 * them.
 */
@DisplayName("PackGraph.walk: one driver, a scope that only selects, the domain's edges (#638)")
class PackGraphWalkTest {

    private static PackNode pack(String id, Optional<String> parent, String... dependsOn) {
        return new PackNode(
                new ConfiguredPack(new PackIdentifier(id), PackLevel.ORG),
                new PackDescriptor(
                        id,
                        "1.0.0",
                        parent,
                        "pack-" + id,
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of(dependsOn).stream()
                                .map(target -> new PackDependency(target, ">=1.0"))
                                .toList()));
    }

    private static PackCompositionFacts facts(List<String> held, List<String> declared, List<Relationship> edges) {
        return new PackCompositionFacts(
                List.of(),
                held,
                declared,
                List.of(),
                MutabilityPolicySource.empty(),
                List.of(),
                edges,
                Map.of(),
                PromptFacts.NONE,
                List.of(),
                List.of());
    }

    private static Urn u(String urn) {
        return Urn.parse(urn).orElseThrow();
    }

    private static PackIdentifier id(String id) {
        return new PackIdentifier(id);
    }

    /** Records what it is handed; answers a planted descent for a named element. */
    private static final class Recorder implements GraphWalker<PackGraphNode, EdgeNode> {
        private final List<String> seen = new ArrayList<>();
        private final Map<String, Descent> answers = new LinkedHashMap<>();

        Recorder answer(String element, Descent descent) {
            answers.put(element, descent);
            return this;
        }

        @Override
        public Descent onNode(PackGraphNode node) {
            String name = switch (node) {
                case PackNode p -> "pack:" + p.pack().value();
                case ArtifactNode a -> "artifact:" + a.urn() + (a.held() ? ":held" : "");
                case EdgeNode e -> "edge-as-node:" + e.kind();
            };
            seen.add(name);
            return answers.getOrDefault(name, Descent.CONTINUE);
        }

        @Override
        public Descent onRelationship(EdgeNode edge) {
            String name = "edge:" + edge.kind() + ":" + edge.pack().value();
            seen.add(name);
            return answers.getOrDefault(name, Descent.CONTINUE);
        }
    }

    private static PackGraph graph(List<PackNode> packs, Function<PackIdentifier, PackCompositionFacts> factsOf) {
        return PackGraph.of(packs, factsOf);
    }

    private static PackCompositionFacts noFacts(PackIdentifier pack) {
        return PackCompositionFacts.NONE;
    }

    @Test
    @DisplayName("the roots' composition closure, once each, in the graph's order; a pack outside it is not visited")
    void theClosureInGraphOrder() {
        Recorder walker = new Recorder();
        graph(
                        List.of(
                                pack("base", Optional.empty()),
                                pack("other", Optional.empty()),
                                pack("mid", Optional.empty(), "base"),
                                pack("top", Optional.of("mid"))),
                        PackGraphWalkTest::noFacts)
                .walk(Scope.of(List.of(id("top"))), PackGraph.Walk.PACKS, walker);

        assertThat(walker.seen).containsExactly("pack:base", "pack:mid", "pack:top");
    }

    @Test
    @DisplayName("a dependency cycle ends: each pack is visited once")
    void aCycleTerminates() {
        Recorder walker = new Recorder();
        graph(List.of(pack("a", Optional.empty(), "b"), pack("b", Optional.empty(), "a")), PackGraphWalkTest::noFacts)
                .walk(Scope.of(List.of(id("a"))), PackGraph.Walk.PACKS, walker);

        assertThat(walker.seen).containsExactly("pack:a", "pack:b");
    }

    @Test
    @DisplayName("an element the scope does not select is not handed over, and what it builds on is still visited")
    void theSelectionFiltersAndNeverPrunes() {
        Recorder walker = new Recorder();
        graph(
                        List.of(
                                pack("base", Optional.empty()),
                                pack("mid", Optional.empty(), "base"),
                                pack("top", Optional.empty(), "mid")),
                        PackGraphWalkTest::noFacts)
                .walk(
                        new Scope(
                                List.of(id("top")), node -> !node.pack().value().equals("mid")),
                        PackGraph.Walk.PACKS,
                        walker);

        assertThat(walker.seen).containsExactly("pack:base", "pack:top");
    }

    @Test
    @DisplayName("RELATIONS: a pack's overrides, enhances, specializes and own step-link edges, in declared order")
    void relationsEmitTheirKindsInDeclaredOrder() {
        Relationship enhances = Relationship.of(u("directive:A"), u("directive:B"), RelationType.ENHANCES);
        Relationship requiresOther = Relationship.of(u("mission_type:m"), u("action:other/x"), RelationType.REQUIRES);
        Relationship overrides = Relationship.of(u("directive:C"), u("directive:D"), RelationType.OVERRIDES);
        Relationship specializes =
                Relationship.of(u("agent_profile:p"), u("agent_profile:q"), RelationType.SPECIALIZES_FROM);
        Relationship stepLink = Relationship.of(u("mission_type:m"), u("action:m/plan"), RelationType.REQUIRES);
        Recorder walker = new Recorder();

        graph(
                        List.of(pack("p", Optional.empty())),
                        pack -> facts(
                                List.of(),
                                List.of(),
                                List.of(enhances, requiresOther, overrides, specializes, stepLink)))
                .walk(Scope.of(List.of(id("p"))), PackGraph.Walk.RELATIONS, walker);

        assertThat(walker.seen)
                .containsExactly(
                        "pack:p", "edge:ENHANCES:p", "edge:OVERRIDES:p", "edge:SPECIALIZES:p", "edge:STEP_LINK:p");
    }

    private static final Relationship OVERRIDES =
            Relationship.of(u("directive:C"), u("directive:D"), RelationType.OVERRIDES);
    private static final Relationship ENHANCES =
            Relationship.of(u("directive:A"), u("directive:B"), RelationType.ENHANCES);

    /** Two packs, b building on a, each writing an overrides edge, then an enhances edge. */
    private static PackGraph twoPacksWithTwoEdges() {
        return graph(
                List.of(pack("a", Optional.empty()), pack("b", Optional.empty(), "a")),
                pack -> facts(List.of(), List.of(), List.of(OVERRIDES, ENHANCES)));
    }

    @Test
    @DisplayName("RELATIONS: an edge the scope does not select is not handed over; the next one is")
    void theSelectionFiltersEdges() {
        Recorder walker = new Recorder();

        twoPacksWithTwoEdges()
                .walk(
                        new Scope(
                                List.of(id("a")),
                                node -> !(node instanceof EdgeNode edge && edge.kind() == EdgeKind.OVERRIDES)),
                        PackGraph.Walk.RELATIONS,
                        walker);

        assertThat(walker.seen).containsExactly("pack:a", "edge:ENHANCES:a");
    }

    @Test
    @DisplayName("RELATIONS: a pack the scope does not select still has its selected edges handed over")
    void anUnselectedPacksEdgesAreHandedOver() {
        Recorder walker = new Recorder();

        twoPacksWithTwoEdges()
                .walk(new Scope(List.of(id("b")), node -> node instanceof EdgeNode), PackGraph.Walk.RELATIONS, walker);

        assertThat(walker.seen)
                .containsExactly("edge:OVERRIDES:a", "edge:ENHANCES:a", "edge:OVERRIDES:b", "edge:ENHANCES:b");
    }

    @Test
    @DisplayName("STOP on an edge ends the walk at once: no later edge and no later pack")
    void stopOnAnEdgeEndsTheWalk() {
        Recorder walker = new Recorder().answer("edge:OVERRIDES:a", Descent.STOP);

        twoPacksWithTwoEdges().walk(Scope.of(List.of(id("b"))), PackGraph.Walk.RELATIONS, walker);

        assertThat(walker.seen).containsExactly("pack:a", "edge:OVERRIDES:a");
    }

    @Test
    @DisplayName("SKIP on an edge is CONTINUE: nothing is walked beneath an edge")
    void skipOnAnEdgeContinues() {
        Recorder walker = new Recorder().answer("edge:OVERRIDES:a", Descent.SKIP);

        twoPacksWithTwoEdges().walk(Scope.of(List.of(id("b"))), PackGraph.Walk.RELATIONS, walker);

        assertThat(walker.seen)
                .containsExactly(
                        "pack:a",
                        "edge:OVERRIDES:a",
                        "edge:ENHANCES:a",
                        "pack:b",
                        "edge:OVERRIDES:b",
                        "edge:ENHANCES:b");
    }

    @Test
    @DisplayName("two packs under one pack_id are both visited, and the closure follows what each builds on")
    void everyHolderOfAPackIdIsWalked() {
        PackNode first = new PackNode(
                new ConfiguredPack(id("dup-1"), PackLevel.ORG),
                pack("dup", Optional.empty()).descriptor());
        PackNode second = new PackNode(
                new ConfiguredPack(id("dup-2"), PackLevel.ORG),
                pack("dup", Optional.empty(), "base").descriptor());
        Recorder walker = new Recorder();

        graph(List.of(pack("base", Optional.empty()), first, second), PackGraphWalkTest::noFacts)
                .walk(Scope.of(List.of(id("dup-2"))), PackGraph.Walk.PACKS, walker);

        assertThat(walker.seen).containsExactly("pack:base", "pack:dup-1", "pack:dup-2");
    }

    @Test
    @DisplayName("SKIP on a pack skips its edges; the walk goes on with the next pack")
    void skipSkipsAPacksEdges() {
        Relationship overrides = Relationship.of(u("directive:C"), u("directive:D"), RelationType.OVERRIDES);
        Recorder walker = new Recorder().answer("pack:a", Descent.SKIP);

        graph(
                        List.of(pack("a", Optional.empty()), pack("b", Optional.empty(), "a")),
                        pack -> facts(List.of(), List.of(), List.of(overrides)))
                .walk(Scope.of(List.of(id("b"))), PackGraph.Walk.RELATIONS, walker);

        assertThat(walker.seen).containsExactly("pack:a", "pack:b", "edge:OVERRIDES:b");
    }

    @Test
    @DisplayName("STOP ends the walk at once")
    void stopEndsTheWalk() {
        Recorder walker = new Recorder().answer("pack:a", Descent.STOP);

        graph(List.of(pack("a", Optional.empty()), pack("b", Optional.empty(), "a")), PackGraphWalkTest::noFacts)
                .walk(Scope.of(List.of(id("b"))), PackGraph.Walk.PACKS, walker);

        assertThat(walker.seen).containsExactly("pack:a");
    }

    @Test
    @DisplayName("PACKS never reads a pack's facts, and RELATIONS reads each pack's once, however often it walks")
    void factsAreReadOnlyWhenNeededAndOnce() {
        List<String> reads = new ArrayList<>();
        PackGraph graph = graph(List.of(pack("a", Optional.empty()), pack("b", Optional.empty(), "a")), pack -> {
            reads.add(pack.value());
            return PackCompositionFacts.NONE;
        });

        graph.walk(Scope.of(List.of(id("b"))), PackGraph.Walk.PACKS, new Recorder());
        assertThat(reads).isEmpty();

        graph.walk(Scope.of(List.of(id("b"))), PackGraph.Walk.RELATIONS, new Recorder());
        graph.walk(Scope.of(List.of(id("b"))), PackGraph.Walk.RELATIONS, new Recorder());
        assertThat(reads).containsExactly("a", "b");
    }

    @Test
    @DisplayName("a chain of 200,000 packs does not exhaust the stack")
    void aLongChain() {
        int n = 200_000;
        List<PackNode> packs = IntStream.rangeClosed(0, n)
                .mapToObj(i -> i == 0 ? pack("p0", Optional.empty()) : pack("p" + i, Optional.empty(), "p" + (i - 1)))
                .toList();
        Recorder walker = new Recorder();

        graph(packs, PackGraphWalkTest::noFacts).walk(Scope.of(List.of(id("p" + n))), PackGraph.Walk.PACKS, walker);

        assertThat(walker.seen).hasSize(n + 1);
    }

    @Test
    @DisplayName("a root no pack is configured as is an argument error")
    void anUnknownRoot() {
        assertThatThrownBy(() -> graph(List.of(pack("a", Optional.empty())), PackGraphWalkTest::noFacts)
                        .walk(Scope.of(List.of(id("nowhere"))), PackGraph.Walk.PACKS, new Recorder()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nowhere");
    }
}
