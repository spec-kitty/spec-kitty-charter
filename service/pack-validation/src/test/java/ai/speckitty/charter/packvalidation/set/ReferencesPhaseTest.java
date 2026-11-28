package ai.speckitty.charter.packvalidation.set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PromptFacts;
import ai.speckitty.charter.packs.domain.SubstepDelegationRule;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code REFERENCES}, the domain phase of {@code pack-doctor validate --full} (#632 item 3, rulings R-s and R-ae,
 * FR-006): per root, over the closure the root composes, every pack in it, the delegation, reference and enhancement
 * target checks, in registration's phase order, each the domain's one body, every fault of the first check that finds
 * any; a fault several roots find is reported once.
 */
@DisplayName("ReferencesPhase: delegations, references, enhancement targets per root's composed closure (R-s, R-ae)")
class ReferencesPhaseTest {

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

    private static PackUnderResolution writing(String... held) {
        return pack(WRITING, "writing", List.of(), held);
    }

    private static PackUnderResolution reader(String... held) {
        return pack(READER, "reader", List.of(WRITING), held);
    }

    private void writes(
            PackUnderResolution pack,
            List<String> references,
            List<Relationship> edges,
            List<SubstepDelegationRule.QualifiedCandidate> delegations) {
        facts.put(
                pack.configured().id().value(),
                new PackCompositionFacts(
                        List.of(),
                        List.of(),
                        List.of(),
                        references,
                        MutabilityPolicySource.empty(),
                        List.of(),
                        edges,
                        Map.of(),
                        PromptFacts.NONE,
                        List.of(),
                        delegations));
    }

    private PackCompositionFacts factsOf(PackUnderResolution pack) {
        return facts.getOrDefault(pack.configured().id().value(), PackCompositionFacts.NONE);
    }

    private List<ClosureFault> faults(PackUnderResolution... set) {
        return ReferencesPhase.judged(List.of(set), this::factsOf).faults();
    }

    private static Relationship enhances(String from, String to) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return Relationship.of(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                RelationType.ENHANCES,
                Optional.empty(),
                Optional.empty());
    }

    private static SubstepDelegationRule.QualifiedCandidate delegating(String reference) {
        return new SubstepDelegationRule.QualifiedCandidate(
                "missions/rx/rx.mission-type.yaml", "rx", "a", "run", PackQualifiedReference.parse(reference));
    }

    private static List<String> codes(List<ClosureFault> faults) {
        return faults.stream().map(fault -> fault.code().token()).toList();
    }

    @Test
    @DisplayName("a rooted closure with a composition fault is no input: CLOSURE passing rules it out, so it throws")
    void anOwnClosureWithACompositionFaultThrows() {
        PackUnderResolution reader = reader();

        assertThatThrownBy(() -> faults(reader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reader");
    }

    @Test
    @DisplayName("every reference fault of a pack, in the references' order, with registration's codes")
    void everyReferenceFaultOfAPack() {
        PackUnderResolution writing = writing("directive:SHARE");
        PackUnderResolution reader = reader();
        writes(reader, List.of("writing::directive:NOPE", "gamma::directive:SHARE"), List.of(), List.of());

        assertThat(codes(faults(writing, reader)))
                .containsExactly(
                        RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING.token(),
                        RefusalCode.PACK_REFERENCE_PACK_MISSING.token());
    }

    @Test
    @DisplayName(
            "a delegation fault is answered as registration answers it, and its scope edge's reference is not judged")
    void aDelegationFaultGatesTheReferences() {
        PackUnderResolution writing = writing("tactic:other");
        PackUnderResolution reader = reader();
        writes(reader, List.of("writing::tactic:tdd"), List.of(), List.of(delegating("writing::tactic:tdd")));

        assertThat(faults(writing, reader)).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
            assertThat(fault.violations())
                    .extracting(RefusalViolation::pointer, RefusalViolation::code)
                    .containsExactly(org.assertj.core.api.Assertions.tuple(
                            "missions/rx/rx.mission-type.yaml", SubstepDelegationRule.UNRESOLVED));
            assertThat(fault.packs()).extracting(RefusalPack::name).containsExactly("reader", "writing");
        });
    }

    @Test
    @DisplayName("a reference fault gates the enhancement targets, as registration's phase 3 gates its phase 4")
    void aReferenceFaultGatesTheTargets() {
        PackUnderResolution writing = writing("mission_type:M", "action:M/s");
        PackUnderResolution reader = reader("step_enhancement:x");
        writes(
                reader,
                List.of("writing::directive:NOPE"),
                List.of(enhances("step_enhancement:x", "writing::action:M/nosuch")),
                List.of());

        assertThat(codes(faults(writing, reader))).containsExactly(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING.token());
    }

    @Test
    @DisplayName("every enhancement target fault of a pack, when its delegations and references pass")
    void everyTargetFault() {
        PackUnderResolution writing = writing("mission_type:M", "action:M/s");
        PackUnderResolution reader = reader("step_enhancement:x", "step_enhancement:y");
        writes(
                reader,
                List.of(),
                List.of(
                        enhances("step_enhancement:x", "writing::action:M/nosuch"),
                        enhances("step_enhancement:y", "gamma::action:M/s")),
                List.of());

        List<ClosureFault> faults = faults(writing, reader);

        assertThat(codes(faults))
                .containsExactly(
                        RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING.token(),
                        RefusalCode.PACK_REFERENCE_PACK_MISSING.token());
        assertThat(faults.getFirst().detail()).contains("action:M/nosuch");
    }

    @Test
    @DisplayName(
            "a dependency's bare reference that only a dependent holds is unqualified, judged from the dependent's root"
                    + " (R-ae)")
    void aDependencysBareReferenceOnlyADependentHoldsIsUnqualified() {
        PackUnderResolution writing = writing();
        PackUnderResolution reader = reader("directive:ONLY_IN_READER");
        writes(writing, List.of("directive:ONLY_IN_READER"), List.of(), List.of());

        assertThat(faults(writing, reader))
                .as("reader's root composes [writing, reader] and judges every member, as registering reader does")
                .singleElement()
                .satisfies(fault -> {
                    assertThat(fault.code()).isEqualTo(RefusalCode.PACK_REFERENCE_UNQUALIFIED);
                    assertThat(fault.packs().getFirst().name()).isEqualTo("writing");
                });
    }

    @Test
    @DisplayName("the faults follow the roots in configured-id order, and each root's closure in merge order")
    void theFaultsFollowTheRootsInConfiguredIdOrderAndMergeOrder() {
        PackUnderResolution writing = writing("directive:SHARE");
        PackUnderResolution reader = reader();
        writes(reader, List.of("writing::directive:NOPE"), List.of(), List.of());
        writes(writing, List.of("writing::directive:GONE"), List.of(), List.of());

        assertThat(faults(writing, reader))
                .as("root reader (first by configured id) judges writing, then reader, in merge order")
                .extracting(fault -> fault.packs().getFirst().name())
                .containsExactly("writing", "reader");
        assertThat(faults(reader, writing)).isEqualTo(faults(writing, reader));
    }

    @Test
    @DisplayName("the roots sort by their configured id, not by the descriptor's pack_id (Tier R final confirm F5)")
    void theRootsSortByTheirConfiguredIdNotByThePackId() {
        // The configured ids sort opposite to the pack_id ULIDs: "alpha" carries the later ULID, "zulu" the earlier.
        PackUnderResolution zulu = pack("01M7A00000000000000000AAAA", "zulu", List.of(), "directive:Z");
        PackUnderResolution alpha = pack("01M7Z00000000000000000ZZZZ", "alpha", List.of(), "directive:A");
        writes(zulu, List.of("zulu::directive:GONE"), List.of(), List.of());
        writes(alpha, List.of("alpha::directive:GONE"), List.of(), List.of());

        assertThat(faults(zulu, alpha))
                .extracting(fault -> fault.packs().getFirst().name())
                .containsExactly("alpha", "zulu");
    }

    @Test
    @DisplayName("a fault two roots find is reported once")
    void aFaultTwoRootsFindIsReportedOnce() {
        PackUnderResolution writing = writing();
        PackUnderResolution reader = reader();
        writes(writing, List.of("writing::directive:GONE"), List.of(), List.of());

        assertThat(faults(writing, reader))
                .as("found from writing's root and from reader's, which holds writing")
                .singleElement()
                .satisfies(fault -> assertThat(fault.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING));
    }

    @Test
    @DisplayName("the stop rule spans the closure: a dependency's delegation fault hides a dependent's reference fault")
    void theStopRuleSpansTheClosure() {
        PackUnderResolution writing = writing("tactic:other");
        PackUnderResolution reader = reader();
        writes(writing, List.of(), List.of(), List.of(delegating("writing::tactic:tdd")));
        writes(reader, List.of("writing::directive:NOPE"), List.of(), List.of());

        assertThat(codes(faults(writing, reader)))
                .as("registering reader answers writing's delegation fault and stops, as --full now does")
                .containsExactly(RefusalCode.VALIDATION_FAILED.token());
    }

    private static final String SIBLING_A = "01M7SA0000000000000000SAAA";
    private static final String SIBLING_B = "01M7SB0000000000000000SBBB";
    private static final String ROOT = "01M7RT0000000000000000RTTT";

    @Test
    @DisplayName("siblings under a shared root: a bare reference only the sibling holds is unqualified from the root")
    void aBareReferenceOnlyASiblingHoldsIsUnqualifiedFromTheSharedRoot() {
        PackUnderResolution a = pack(SIBLING_A, "sib-a", List.of());
        PackUnderResolution b = pack(SIBLING_B, "sib-b", List.of(), "directive:IN_B");
        PackUnderResolution root = pack(ROOT, "root", List.of(SIBLING_A, SIBLING_B));
        writes(a, List.of("directive:IN_B"), List.of(), List.of());

        assertThat(faults(a, b, root))
                .as("sib-a and sib-b pass alone; root's closure holds both, as registering root composes it")
                .singleElement()
                .satisfies(fault -> {
                    assertThat(fault.code()).isEqualTo(RefusalCode.PACK_REFERENCE_UNQUALIFIED);
                    assertThat(fault.packs().getFirst().name()).isEqualTo("sib-a");
                });
    }

    @Test
    @DisplayName("a qualified reference to a pack outside depends_on is one fault per root, each in that root's words")
    void aQualifiedReferenceOutsideDependsOnIsOneFaultPerRoot() {
        PackUnderResolution a = pack(SIBLING_A, "sib-a", List.of());
        PackUnderResolution b = pack(SIBLING_B, "sib-b", List.of(), "directive:IN_B");
        PackUnderResolution root = pack(ROOT, "root", List.of(SIBLING_A, SIBLING_B));
        writes(a, List.of("sib-b::directive:IN_B"), List.of(), List.of());

        List<ClosureFault> faults = faults(a, b, root);

        assertThat(faults)
                .as("root's closure holds sib-b outside sib-a's depends_on; sib-a's own closure holds no sib-b")
                .hasSize(2)
                .allSatisfy(fault -> assertThat(fault.packs().getFirst().name()).isEqualTo("sib-a"));
        assertThat(faults.get(0).detail()).isNotEqualTo(faults.get(1).detail());
    }

    @Test
    @DisplayName("a set whose packs write nothing amiss has no fault")
    void aSoundSetHasNoFault() {
        PackUnderResolution writing = writing("directive:SHARE", "mission_type:M", "action:M/s");
        PackUnderResolution reader = reader("step_enhancement:x");
        writes(
                reader,
                List.of("writing::directive:SHARE"),
                List.of(enhances("step_enhancement:x", "writing::action:M/s")),
                List.of(delegating("writing::directive:SHARE")));

        assertThat(faults(writing, reader)).isEmpty();
    }
}
