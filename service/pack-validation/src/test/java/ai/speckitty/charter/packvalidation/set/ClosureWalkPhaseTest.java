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
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
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
 * {@code REFERENCES}, the domain phase of {@code pack-doctor validate --full} (#632 item 3, ruling R-s, FR-006): per
 * pack, over its own closure, the delegation, reference and enhancement target checks, in registration's phase order,
 * each the domain's one body, every fault of the first check that finds any.
 */
@DisplayName("ClosureWalkPhase: delegations, references, enhancement targets per own closure (#632 item 3, R-s)")
class ClosureWalkPhaseTest {

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
        return ClosureWalkPhase.faults(List.of(set), this::factsOf);
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
            "each pack is judged in its own closure: a dependent's artifact is no holder of its base's bare reference")
    void eachPackIsJudgedInItsOwnClosure() {
        PackUnderResolution writing = writing();
        PackUnderResolution reader = reader("directive:ONLY_IN_READER");
        writes(writing, List.of("directive:ONLY_IN_READER"), List.of(), List.of());
        List<PackUnderResolution> set = List.of(writing, reader);

        assertThat(PackCompositionResolver.referenceFaultsOf(set, writing, factsOf(writing)))
                .as("judged in the whole set, the dependent would hold it and the bare reference be unqualified")
                .extracting(ClosureFault::code)
                .containsExactly(RefusalCode.PACK_REFERENCE_UNQUALIFIED);
        assertThat(faults(writing, reader))
                .as("writing's own closure is writing alone, as its registration composes it")
                .isEmpty();
    }

    @Test
    @DisplayName("the packs' faults follow the set's order, each pack judged whatever another found")
    void thePacksFaultsFollowTheSetsOrder() {
        PackUnderResolution writing = writing("directive:SHARE");
        PackUnderResolution reader = reader();
        writes(reader, List.of("writing::directive:NOPE"), List.of(), List.of());
        writes(writing, List.of("writing::directive:GONE"), List.of(), List.of());

        assertThat(faults(reader, writing))
                .extracting(fault -> fault.packs().getFirst().name())
                .containsExactly("reader", "writing");
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
