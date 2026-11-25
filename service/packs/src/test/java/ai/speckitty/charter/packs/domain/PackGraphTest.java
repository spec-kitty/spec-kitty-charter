package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The one owner of the walks over the pack graph (ADR-0082 decisions 1 and 2, #637): the edges a pack builds on, the
 * descents along them and back up them, the cycles and strongly connected groups, and the grounding of
 * {@code enhances} chains. Every answer is in a stable order, whatever the hash order of its nodes.
 */
@DisplayName("PackGraph: every cycle, group and descending walk over the pack graph (#637)")
class PackGraphTest {

    private static PackDescriptor descriptor(String id, Optional<String> parent, String... dependsOn) {
        return new PackDescriptor(
                id,
                "1.0.0",
                parent,
                "pack-" + id,
                Optional.of(PackLevel.ORG),
                Optional.empty(),
                PackPaths.none(),
                List.of(dependsOn).stream()
                        .map(target -> new PackDependency(target, ">=1.0"))
                        .toList());
    }

    private static Map<String, List<String>> graph(String... edges) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (String edge : edges) {
            String[] ends = edge.split(">", -1);
            graph.computeIfAbsent(ends[0], ignored -> new ArrayList<>()).add(ends[1]);
        }
        return graph;
    }

    @Nested
    @DisplayName("the edges a pack builds on")
    class BuildsOn {

        @Test
        @DisplayName("its depends_on targets in declared order, then its parent_pack")
        void dependsOnThenParent() {
            assertThat(PackGraph.buildsOn(descriptor("p", Optional.of("parent"), "b", "a")))
                    .containsExactly("b", "a", "parent");
        }

        @Test
        @DisplayName("none for a pack that declares neither")
        void none() {
            assertThat(PackGraph.buildsOn(descriptor("p", Optional.empty()))).isEmpty();
        }
    }

    @Nested
    @DisplayName("ancestorsOf: one breadth-first descent over what each pack builds on")
    class Reachable {

        @Test
        @DisplayName("the starts first, then each node in the order it is first met, each once")
        void firstSeenOrder() {
            Map<String, List<String>> graph = graph("a>c", "a>b", "b>d", "c>d");

            assertThat(PackGraph.ancestorsOf(List.of("a"), node -> graph.getOrDefault(node, List.of())))
                    .containsExactly("a", "c", "b", "d");
        }

        @Test
        @DisplayName("a cycle terminates, and a node with no successors ends its branch")
        void aCycleTerminates() {
            Map<String, List<String>> graph = graph("a>b", "b>a", "b>x");

            assertThat(PackGraph.ancestorsOf(List.of("b"), node -> graph.getOrDefault(node, List.of())))
                    .containsExactly("b", "a", "x");
        }

        @Test
        @DisplayName("several starts keep their order, and a start another reaches is met once")
        void severalStarts() {
            Map<String, List<String>> graph = graph("z>a");

            assertThat(PackGraph.ancestorsOf(List.of("z", "a", "q"), node -> graph.getOrDefault(node, List.of())))
                    .containsExactly("z", "a", "q");
        }

        @Test
        @DisplayName("a self-loop is met once, and a start with no successors ends at itself")
        void aSelfLoopAndALoneStart() {
            Map<String, List<String>> graph = graph("a>a");

            assertThat(PackGraph.ancestorsOf(List.of("a", "lone"), node -> graph.getOrDefault(node, List.of())))
                    .containsExactly("a", "lone");
        }

        @Test
        @DisplayName("a long chain does not exhaust the stack")
        void aLongChain() {
            int n = 200_000;

            assertThat(PackGraph.ancestorsOf(List.of(0), node -> node < n ? List.of(node + 1) : List.of()))
                    .hasSize(n + 1);
        }
    }

    @Nested
    @DisplayName("the descents over depends_on and parent_pack")
    class Descents {

        @Test
        @DisplayName("the composition closure records a target nothing declares and does not descend from it")
        void compositionClosure() {
            PackDescriptor alpha = descriptor("alpha", Optional.empty(), "beta");
            PackDescriptor beta = descriptor("beta", Optional.of("missing"));
            Map<String, PackDescriptor> byPackId = Map.of("alpha", alpha, "beta", beta);

            assertThat(PackGraph.compositionClosure(List.of("alpha"), id -> Optional.ofNullable(byPackId.get(id))))
                    .containsExactly("alpha", "beta", "missing");
        }

        @Test
        @DisplayName("the composition closure terminates on a cycle, each pack once")
        void compositionClosureOverACycle() {
            Map<String, PackDescriptor> byPackId =
                    Map.of("a", descriptor("a", Optional.empty(), "b"), "b", descriptor("b", Optional.of("a")));

            assertThat(PackGraph.compositionClosure(List.of("b"), id -> Optional.ofNullable(byPackId.get(id))))
                    .containsExactly("b", "a");
        }

        @Test
        @DisplayName("dependentsClosure follows the order of the descriptors given, not the dependency order")
        void dependentsClosureFollowsInputOrder() {
            List<PackDescriptor> descriptors = List.of(
                    descriptor("second", Optional.empty(), "base"),
                    descriptor("first", Optional.of("base")),
                    descriptor("base", Optional.empty()));

            assertThat(PackGraph.dependentsClosure("base", descriptors)).containsExactly("base", "second", "first");
        }

        @Test
        @DisplayName("directDependents lists a pack that names the target twice in depends_on once")
        void aTwiceNamedDependencyIsOneDependent() {
            PackGraph.DirectDependents<PackDescriptor> found = PackGraph.directDependents(
                    "target", List.of(descriptor("twice", Optional.empty(), "target", "target")), p -> p);

            assertThat(found.dependsOn()).extracting(PackDescriptor::packId).containsExactly("twice");
            assertThat(found.parentPack()).isEmpty();
        }

        @Test
        @DisplayName("dependentsClosure: the pack and every pack that reaches it, over depends_on or parent_pack")
        void dependentsClosure() {
            List<PackDescriptor> descriptors = List.of(
                    descriptor("base", Optional.empty()),
                    descriptor("middle", Optional.empty(), "base"),
                    descriptor("child", Optional.of("middle")),
                    descriptor("other", Optional.empty()),
                    descriptor("loop", Optional.empty(), "child", "loop"));

            assertThat(PackGraph.dependentsClosure("base", descriptors))
                    .containsExactly("base", "middle", "child", "loop");
            assertThat(PackGraph.dependentsClosure("other", descriptors)).containsExactly("other");
        }

        @Test
        @DisplayName("directDependents: the packs whose depends_on, then whose parent_pack, names the pack, in order")
        void directDependents() {
            List<PackDescriptor> packs = List.of(
                    descriptor("c", Optional.of("target")),
                    descriptor("b", Optional.empty(), "target"),
                    descriptor("a", Optional.of("target"), "target"),
                    descriptor("d", Optional.empty(), "elsewhere"));

            PackGraph.DirectDependents<PackDescriptor> found = PackGraph.directDependents("target", packs, p -> p);

            assertThat(found.dependsOn()).extracting(PackDescriptor::packId).containsExactly("b", "a");
            assertThat(found.parentPack()).extracting(PackDescriptor::packId).containsExactly("c", "a");
        }
    }

    @Nested
    @DisplayName("cyclesIn: one cycle per strongly connected group")
    class CyclesIn {

        @Test
        @DisplayName("two cycles that share a node are reported once, through the first")
        void cyclesSharingANodeAreReportedOnce() {
            assertThat(PackGraph.cyclesIn(graph("a>b", "b>a", "b>c", "c>b"))).containsExactly(List.of("a", "b"));
        }

        @Test
        @DisplayName("one strongly connected group is one cycle, whatever the order of its edges (review 1)")
        void oneGroupIsOneCycleInEveryEdgeOrder() {
            Map<String, List<String>> first = graph("v>x", "v>u", "x>y", "y>x", "y>u", "u>v");
            Map<String, List<String>> second = new LinkedHashMap<>(first);
            second.put("v", List.of("u", "x"));

            assertThat(PackGraph.cyclesIn(first)).hasSize(1);
            assertThat(PackGraph.cyclesIn(second)).hasSize(1);
        }

        @Test
        @DisplayName("two separate groups are two cycles in every edge and node order")
        void twoGroupsAreTwoCyclesInEveryOrder() {
            Map<String, List<String>> first = graph("a>c", "a>b", "b>a", "c>d", "d>c");
            Map<String, List<String>> second = graph("d>c", "c>d", "b>a", "a>b", "a>c");

            assertThat(PackGraph.cyclesIn(first)).containsExactly(List.of("c", "d"), List.of("a", "b"));
            assertThat(PackGraph.cyclesIn(second)).containsExactly(List.of("d", "c"), List.of("b", "a"));
        }

        @Test
        @DisplayName("an acyclic graph has no cycle, and a self-loop is a cycle of one")
        void anAcyclicGraphHasNoCycleAndASelfLoopIsOne() {
            assertThat(PackGraph.cyclesIn(graph("a>b", "a>c", "b>c"))).isEmpty();
            assertThat(PackGraph.cyclesIn(graph("a>a"))).containsExactly(List.of("a"));
        }

        @Test
        @DisplayName("a successor with no entry of its own is a dead end, never part of a cycle")
        void aSuccessorWithNoEntryIsADeadEnd() {
            assertThat(PackGraph.cyclesIn(graph("a>outside", "a>b", "b>a"))).containsExactly(List.of("a", "b"));
            assertThat(PackGraph.cyclesIn(graph("a>outside"))).isEmpty();
        }

        @Test
        @DisplayName("a long chain ending in a cycle does not exhaust the stack")
        void aLongChainDoesNotExhaustTheStack() {
            int n = 200_000;
            Map<Integer, List<Integer>> graph = new LinkedHashMap<>();
            IntStream.range(0, n).forEach(i -> graph.put(i, new ArrayList<>(List.of(i + 1))));
            graph.put(n, List.of(n - 1));

            assertThat(PackGraph.cyclesIn(graph)).containsExactly(List.of(n - 1, n));
        }
    }

    @Nested
    @DisplayName("every walk refuses a missing argument")
    class Arguments {

        @Test
        @DisplayName("null inputs are refused with the parameter's name")
        void nullsAreRefused() {
            assertThatThrownBy(() -> PackGraph.buildsOn(null)).hasMessage("descriptor");
            assertThatThrownBy(() -> PackGraph.compositionClosure(List.of(), null))
                    .hasMessage("descriptorOf");
            assertThatThrownBy(() -> PackGraph.dependentsClosure(null, List.of()))
                    .hasMessage("packId");
            assertThatThrownBy(() -> PackGraph.directDependents("p", null, p -> null))
                    .hasMessage("packs");
            assertThatThrownBy(() -> PackGraph.cyclesIn(null)).hasMessage("graph");
            assertThatThrownBy(() -> PackGraph.ungrounded(List.of(), Map.of(), null))
                    .hasMessage("excused");
        }
    }

    @Nested
    @DisplayName("ungrounded: enhancers no chain grounds")
    class Ungrounded {

        @Test
        @DisplayName("an enhancer that is grounded but also reaches an excused cycle is excused, not found")
        void groundedAndExcusedIsNotFound() {
            Map<String, List<String>> enhances = graph("both>held", "both>x", "x>y", "y>x");

            assertThat(PackGraph.ungrounded(List.of("both", "x", "y"), enhances, List.of("x", "y")))
                    .isEmpty();
        }

        @Test
        @DisplayName("a chain that reaches a non-enhancer grounds every enhancer along it")
        void aGroundedChain() {
            assertThat(PackGraph.ungrounded(List.of("a", "b"), graph("a>b", "b>held"), List.of()))
                    .isEmpty();
        }

        @Test
        @DisplayName("an enhancer whose targets nothing holds is ungrounded, in the enhancers' order")
        void anUnheldTarget() {
            assertThat(PackGraph.ungrounded(List.of("b", "a", "c"), graph("c>held"), List.of()))
                    .containsExactly("b", "a");
        }

        @Test
        @DisplayName("an enhancer on, or leading into, an excused cycle is not found again")
        void anExcusedCycle() {
            Map<String, List<String>> enhances = graph("x>y", "y>x", "lead>x", "alone>lead");

            assertThat(PackGraph.ungrounded(List.of("x", "y", "lead", "alone", "stray"), enhances, List.of("x", "y")))
                    .containsExactly("stray");
        }
    }
}
