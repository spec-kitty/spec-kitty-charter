package ai.speckitty.charter.packvalidation.set;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PromptFacts;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code RELATIONS}, the second domain phase of {@code pack-doctor validate --full} (#632 item 3, FR-012, SC-004): per
 * root whose {@code REFERENCES} passed, over the closure the root composes, the factory's relation rules
 * ({@code PackRulesets.relationFaults}), the {@code specializes_from} cycle among them, with registration's codes; a
 * fault several roots find is reported once.
 */
@DisplayName("RelationsPhase: the relation rules and the specialization cycle per root's composed closure (FR-012)")
class RelationsPhaseTest {

    private static final String WRITING = "01M7WR0000000000000000WRTG";
    private static final String READER = "01M7RD0000000000000000RDER";

    private final Map<String, PackCompositionFacts> facts = new HashMap<>();

    private static PackUnderResolution pack(String id, String name, List<String> dependsOn, String... held) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(name), PackLevel.ORG),
                new PackDescriptor(
                        id,
                        "1.0.0",
                        Optional.empty(),
                        name,
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        dependsOn.stream()
                                .map(other -> new PackDependency(other, ">=1.0"))
                                .toList()),
                List.of(),
                Arrays.asList(held));
    }

    private void writes(PackUnderResolution pack, Relationship... edges) {
        facts.put(
                pack.configured().id().value(),
                new PackCompositionFacts(
                        List.of(),
                        pack.referableArtifacts(),
                        List.of(),
                        List.of(),
                        MutabilityPolicySource.empty(),
                        List.of(),
                        List.of(edges),
                        Map.of(),
                        PromptFacts.NONE,
                        List.of(),
                        List.of()));
    }

    private PackCompositionFacts factsOf(PackUnderResolution pack) {
        return facts.getOrDefault(pack.configured().id().value(), PackCompositionFacts.NONE);
    }

    /** The phase as {@code PackSetCheck} runs it: over the rooted closures whose {@code REFERENCES} passed. */
    private List<ClosureFault> faults(PackUnderResolution... set) {
        return RelationsPhase.faults(
                ReferencesPhase.judged(List.of(set), this::factsOf).passed(), this::factsOf);
    }

    private static Relationship edge(String from, String to, RelationType type) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return Relationship.of(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                type,
                Optional.empty(),
                Optional.empty());
    }

    private static Relationship specializes(String from, String to) {
        return edge("agent_profile:" + from, "agent_profile:" + to, RelationType.SPECIALIZES_FROM);
    }

    private static List<String> codes(List<ClosureFault> faults) {
        return faults.stream().map(fault -> fault.code().token()).toList();
    }

    private static List<String> names(ClosureFault fault) {
        return fault.packs().stream().map(RefusalPack::name).toList();
    }

    @Test
    @DisplayName("a root whose REFERENCES found a fault is not judged, as registration stops before its relations;"
            + " the other roots are")
    void aRootWithAReferenceFaultIsNotJudged() {
        PackUnderResolution writing = pack(
                WRITING,
                "writing",
                List.of(),
                "mission_type:M",
                "action:M/s",
                "agent_profile:ivan",
                "agent_profile:jenny");
        PackUnderResolution reader = pack(READER, "reader", List.of(WRITING), "step_enhancement:x");
        writes(writing, specializes("jenny", "ivan"), specializes("ivan", "jenny"));
        writes(reader, edge("step_enhancement:x", "gamma::action:M/s", RelationType.ENHANCES));

        assertThat(codes(ReferencesPhase.faults(List.of(writing, reader), this::factsOf)))
                .as("reader's root: the enhancement's target pack is missing")
                .containsExactly(RefusalCode.PACK_REFERENCE_PACK_MISSING.token());
        assertThat(codes(faults(writing, reader)))
                .as("reader's root is not judged, so its unplaced enhancer is no second problem for that cause;"
                        + " writing's root finds writing's cycle")
                .containsExactly(RefusalCode.SPECIALIZATION_CYCLE.token());
    }

    @Test
    @DisplayName("a specializes_from cycle is specialization_cycle, naming its profiles in order and its writer")
    void aSpecializationCycleIsFound() {
        PackUnderResolution writing = pack(WRITING, "writing", List.of(), "agent_profile:ivan", "agent_profile:jenny");
        writes(writing, specializes("jenny", "ivan"), specializes("ivan", "jenny"));

        assertThat(faults(writing)).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.SPECIALIZATION_CYCLE);
            assertThat(fault.detail()).contains("jenny -> ivan -> jenny");
            assertThat(fault.violations()).isEmpty();
            assertThat(names(fault)).containsExactly("writing");
        });
    }

    @Test
    @DisplayName("a cycle a dependent closes across its dependency: found from the dependent's root, naming both")
    void aCycleOnlyADependentsClosureHoldsIsFoundFromIt() {
        PackUnderResolution writing = pack(WRITING, "writing", List.of(), "agent_profile:ivan", "agent_profile:jenny");
        PackUnderResolution reader = pack(READER, "reader", List.of(WRITING));
        writes(writing, specializes("jenny", "ivan"));
        writes(
                reader,
                edge("writing::agent_profile:ivan", "writing::agent_profile:jenny", RelationType.SPECIALIZES_FROM));

        assertThat(faults(writing, reader))
                .as("writing's lineage ends alone; reader's closure holds both edges, as registering reader"
                        + " composes it")
                .singleElement()
                .satisfies(fault -> {
                    assertThat(fault.code()).isEqualTo(RefusalCode.SPECIALIZATION_CYCLE);
                    assertThat(fault.detail()).contains("jenny -> ivan -> jenny");
                    assertThat(names(fault)).containsExactly("writing", "reader");
                });
    }

    @Test
    @DisplayName("a fault two roots find is reported once")
    void aFaultTwoRootsFindIsReportedOnce() {
        PackUnderResolution writing = pack(WRITING, "writing", List.of(), "agent_profile:ivan", "agent_profile:jenny");
        PackUnderResolution reader = pack(READER, "reader", List.of(WRITING));
        writes(writing, specializes("jenny", "ivan"), specializes("ivan", "jenny"));

        assertThat(codes(faults(writing, reader)))
                .as("found from writing's root and from reader's, which holds writing")
                .containsExactly(RefusalCode.SPECIALIZATION_CYCLE.token());
    }

    @Test
    @DisplayName(
            "the relation rules in the factory's order within a root: a self-override, then a conflict, then a cycle")
    void theRulesRunInTheFactorysOrder() {
        PackUnderResolution writing =
                pack(WRITING, "writing", List.of(), "directive:A", "directive:B", "directive:C", "directive:D");
        PackUnderResolution reader = pack(READER, "reader", List.of(WRITING));
        writes(
                writing,
                edge("directive:C", "directive:D", RelationType.OVERRIDES),
                edge("directive:D", "directive:C", RelationType.OVERRIDES),
                edge("directive:A", "directive:B", RelationType.ENHANCES),
                edge("directive:A", "directive:B", RelationType.OVERRIDES));
        writes(reader, edge("writing::directive:A", "writing::directive:A", RelationType.OVERRIDES));

        assertThat(codes(faults(writing, reader)))
                .as("root reader (first by configured id) finds all three; root writing finds the last two again")
                .containsExactly(
                        RefusalCode.SELF_OVERRIDE.token(),
                        RefusalCode.CONFLICTING_RELATIONS.token(),
                        RefusalCode.OVERRIDE_CYCLE.token());
    }

    @Test
    @DisplayName("the faults follow the roots in configured-id order, not the order given nor the pack_id")
    void theFaultsFollowTheRootsInConfiguredIdOrder() {
        // The configured ids sort opposite to the pack_id ULIDs: "alpha" carries the later ULID, "zulu" the earlier.
        PackUnderResolution zulu =
                pack("01M7A00000000000000000AAAA", "zulu", List.of(), "agent_profile:ivan", "agent_profile:jenny");
        PackUnderResolution alpha =
                pack("01M7Z00000000000000000ZZZZ", "alpha", List.of(), "agent_profile:ivan", "agent_profile:jenny");
        writes(zulu, specializes("jenny", "ivan"), specializes("ivan", "jenny"));
        writes(alpha, specializes("ivan", "jenny"), specializes("jenny", "ivan"));

        assertThat(faults(zulu, alpha))
                .extracting(fault -> names(fault).getFirst())
                .containsExactly("alpha", "zulu");
        assertThat(faults(alpha, zulu)).isEqualTo(faults(zulu, alpha));
    }

    @Test
    @DisplayName("a set whose edges break no relation rule has no fault")
    void aSoundSetHasNoFault() {
        PackUnderResolution writing = pack(
                WRITING,
                "writing",
                List.of(),
                "directive:A",
                "directive:B",
                "agent_profile:ivan",
                "agent_profile:jenny");
        PackUnderResolution reader = pack(READER, "reader", List.of(WRITING), "directive:R");
        writes(writing, specializes("jenny", "ivan"), edge("directive:A", "directive:B", RelationType.ENHANCES));
        writes(reader, edge("directive:R", "writing::directive:A", RelationType.OVERRIDES));

        assertThat(faults(writing, reader)).isEmpty();
    }
}
