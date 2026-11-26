package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.fixtures.CompositionFacts;
import ai.speckitty.charter.packs.fixtures.FixtureUlids;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The relation rules collect every fault (#556, FR-003, squad R1): each rule finds every fault in edge order, the rules
 * run in their fixed order, and a fault an earlier one implies is not found again.
 */
@DisplayName("ImpactRelations: every relation fault of a closure, none implied twice (#556)")
class ImpactRelationsTest {

    private static final List<String> HELD = List.of(
            "directive:A", "directive:B", "directive:C", "directive:D", "directive:E", "directive:F", "directive:G");

    private static Urn u(String urn) {
        return Urn.parse(urn).orElseThrow();
    }

    private static Relationship edge(String from, RelationType type, String to) {
        return Relationship.of(
                u("directive:" + from),
                Optional.empty(),
                u("directive:" + to),
                Optional.empty(),
                type,
                Optional.empty(),
                Optional.empty());
    }

    private static Relationship enhances(String from, String to) {
        return edge(from, RelationType.ENHANCES, to);
    }

    private static Relationship overrides(String from, String to) {
        return edge(from, RelationType.OVERRIDES, to);
    }

    /**
     * An {@code overrides} edge whose target resolves to its own source: written with the source qualified by acme's own
     * name, as no relationship may be a bare self-loop.
     */
    private static Relationship selfOverride(String id) {
        return Relationship.of(
                u("directive:" + id),
                Optional.of("acme"),
                u("directive:" + id),
                Optional.empty(),
                RelationType.OVERRIDES,
                Optional.empty(),
                Optional.empty());
    }

    /** One org pack, acme, holding directives A to G and writing the edges. */
    private static List<ClosureFault> judged(List<String> held, Relationship... edges) {
        PackDescriptor descriptor = new PackDescriptor(
                FixtureUlids.of("acme"),
                "1.0.0",
                Optional.empty(),
                "acme",
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                List.of());
        List<PackUnderResolution> packs = List.of(new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier("acme"), PackLevel.ORG), descriptor, List.of(), held));
        Map<String, PackCompositionFacts> facts = new LinkedHashMap<>();
        facts.put(
                "acme",
                CompositionFacts.of(
                        List.of(),
                        held,
                        List.of(),
                        List.of(),
                        MutabilityPolicySource.empty(),
                        List.of(),
                        List.of(edges)));
        return ImpactRelations.check(new OfferingReadService.ComposedClosure(packs, facts));
    }

    private static List<ClosureFault> judged(Relationship... edges) {
        return judged(HELD, edges);
    }

    @Test
    @DisplayName("US1.1: a self-override and an enhances cycle are two faults, in rule order")
    void aSelfOverrideAndAnEnhancesCycleAreTwoFaults() {
        List<ClosureFault> faults = judged(selfOverride("A"), enhances("B", "C"), enhances("C", "B"));

        assertThat(faults)
                .extracting(ClosureFault::code)
                .containsExactly(RefusalCode.SELF_OVERRIDE, RefusalCode.ENHANCES_CYCLE);
        assertThat(faults.get(1).detail()).contains("directive:B -> directive:C -> directive:B");
    }

    @Test
    @DisplayName("US1.2: one fault is one fault, with the detail it always had")
    void oneFaultIsOneFault() {
        assertThat(judged(selfOverride("A"))).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.SELF_OVERRIDE);
            assertThat(fault.detail())
                    .isEqualTo("the overrides edge of directive:A in pack acme resolves to its own source");
            assertThat(fault.packs()).extracting(pack -> pack.packId().value()).containsExactly("acme");
        });
    }

    @Test
    @DisplayName("a self-override is not an override cycle too")
    void aSelfOverrideIsNoOverrideCycle() {
        assertThat(judged(selfOverride("A"))).extracting(ClosureFault::code).doesNotContain(RefusalCode.OVERRIDE_CYCLE);
    }

    @Test
    @DisplayName("a two-node enhances cycle is one fault: its places are not also judged ungrounded")
    void aTwoNodeCycleIsOneFault() {
        assertThat(judged(enhances("A", "B"), enhances("B", "A")))
                .singleElement()
                .satisfies(fault -> assertThat(fault.code()).isEqualTo(RefusalCode.ENHANCES_CYCLE));
    }

    @Test
    @DisplayName("an enhancer that leads into a reported cycle is that cycle's fault, not an ungrounded one")
    void anEnhancerIntoACycleIsNotUngroundedToo() {
        assertThat(judged(enhances("A", "B"), enhances("B", "A"), enhances("C", "A")))
                .extracting(ClosureFault::code)
                .containsExactly(RefusalCode.ENHANCES_CYCLE);
    }

    @Test
    @DisplayName("two separate cycles are two faults, each once, and an ungrounded enhancer elsewhere is a third")
    void twoCyclesAndAnUngroundedEnhancerAreThreeFaults() {
        List<ClosureFault> faults = judged(
                List.of("directive:A", "directive:B", "directive:C", "directive:D", "directive:E"),
                enhances("A", "B"),
                enhances("B", "A"),
                enhances("C", "D"),
                enhances("D", "C"),
                enhances("E", "Z"));

        assertThat(faults)
                .extracting(ClosureFault::code)
                .containsExactly(RefusalCode.ENHANCES_CYCLE, RefusalCode.ENHANCES_CYCLE, RefusalCode.ENHANCES_CYCLE);
        assertThat(faults.get(0).detail()).contains("cycle: directive:A -> directive:B -> directive:A");
        assertThat(faults.get(1).detail()).contains("cycle: directive:C -> directive:D -> directive:C");
        assertThat(faults.get(2).detail()).contains("directive:E").contains("no chain of enhances edges");
    }

    @Test
    @DisplayName("every override cycle is found, each once")
    void everyOverrideCycleIsFoundOnce() {
        assertThat(judged(overrides("A", "B"), overrides("B", "A"), overrides("C", "D"), overrides("D", "C")))
                .extracting(ClosureFault::code)
                .containsExactly(RefusalCode.OVERRIDE_CYCLE, RefusalCode.OVERRIDE_CYCLE);
    }

    @Test
    @DisplayName("every self-override is found, in edge order")
    void everySelfOverrideIsFound() {
        assertThat(judged(selfOverride("B"), selfOverride("A")))
                .extracting(ClosureFault::detail)
                .satisfiesExactly(
                        first -> assertThat(first).contains("directive:B"),
                        second -> assertThat(second).contains("directive:A"));
    }

    @Test
    @DisplayName("cyclesIn: two cycles that share a node are reported once, through the first")
    void cyclesSharingANodeAreReportedOnce() {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        graph.put("a", List.of("b"));
        graph.put("b", List.of("a", "c"));
        graph.put("c", List.of("b"));

        assertThat(ImpactRelations.cyclesIn(graph)).containsExactly(List.of("a", "b"));
    }

    @Test
    @DisplayName("cyclesIn: one strongly connected group is one cycle, whatever the order of its edges (review 1)")
    void oneGroupIsOneCycleInEveryEdgeOrder() {
        Map<String, List<String>> first = new LinkedHashMap<>();
        first.put("v", List.of("x", "u"));
        first.put("x", List.of("y"));
        first.put("y", List.of("x", "u"));
        first.put("u", List.of("v"));
        Map<String, List<String>> second = new LinkedHashMap<>(first);
        second.put("v", List.of("u", "x"));

        assertThat(ImpactRelations.cyclesIn(first)).hasSize(1);
        assertThat(ImpactRelations.cyclesIn(second)).hasSize(1);
    }

    @Test
    @DisplayName("cyclesIn: two separate groups are two cycles in every edge and node order")
    void twoGroupsAreTwoCyclesInEveryOrder() {
        Map<String, List<String>> first = new LinkedHashMap<>();
        first.put("a", List.of("c", "b"));
        first.put("b", List.of("a"));
        first.put("c", List.of("d"));
        first.put("d", List.of("c"));
        Map<String, List<String>> second = new LinkedHashMap<>();
        second.put("d", List.of("c"));
        second.put("c", List.of("d"));
        second.put("b", List.of("a"));
        second.put("a", List.of("b", "c"));

        assertThat(ImpactRelations.cyclesIn(first)).containsExactly(List.of("c", "d"), List.of("a", "b"));
        assertThat(ImpactRelations.cyclesIn(second)).containsExactly(List.of("d", "c"), List.of("b", "a"));
    }

    @Test
    @DisplayName("cyclesIn: an acyclic graph has no cycle, and a self-loop is a cycle of one")
    void anAcyclicGraphHasNoCycleAndASelfLoopIsOne() {
        Map<String, List<String>> acyclic = new LinkedHashMap<>();
        acyclic.put("a", List.of("b", "c"));
        acyclic.put("b", List.of("c"));
        Map<String, List<String>> loop = new LinkedHashMap<>();
        loop.put("a", List.of("a"));

        assertThat(ImpactRelations.cyclesIn(acyclic)).isEmpty();
        assertThat(ImpactRelations.cyclesIn(loop)).containsExactly(List.of("a"));
    }

    @Test
    @DisplayName("cyclesIn: a long chain ending in a cycle does not exhaust the stack")
    void aLongChainDoesNotExhaustTheStack() {
        int n = 200_000;
        Map<Integer, List<Integer>> graph = new LinkedHashMap<>();
        IntStream.range(0, n).forEach(i -> graph.put(i, new ArrayList<>(List.of(i + 1))));
        graph.put(n, List.of(n - 1));

        assertThat(ImpactRelations.cyclesIn(graph)).containsExactly(List.of(n - 1, n));
    }
}
