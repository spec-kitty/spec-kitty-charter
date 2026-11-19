package ai.speckitty.charter.packs.domain.rules;

import static ai.speckitty.charter.packs.fixtures.FixtureUlids.ordered;
import static ai.speckitty.charter.packs.fixtures.FixtureUlids.registeredIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.rules.CompositionRules.CompositionFault;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code CLOSURE} (#554, FR-003, T046): one failing and one passing set per code, and sets with several faults, which
 * report every fault, the first being the refusal {@link PackCompositionResolver#resolve} throws.
 */
@DisplayName("CLOSURE reports every composition fault, each with its code and the packs it names")
class CompositionRulesTest {

    private static final String DIRECTIVE = "directive:DIRECTIVE_009";

    private static final ReasonCode CYCLE = code(RefusalCode.PACK_DEPENDENCY_CYCLE);
    private static final ReasonCode UNKNOWN = code(RefusalCode.PACK_DEPENDENCY_UNKNOWN);
    private static final ReasonCode OUT_OF_RANGE = code(RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE);
    private static final ReasonCode PARENT_UNRESOLVED = code(RefusalCode.PACK_PARENT_UNRESOLVED);
    private static final ReasonCode LEVEL = code(RefusalCode.PACK_LEVEL_VIOLATION);
    private static final ReasonCode NAME = code(RefusalCode.PACK_NAME_CONFLICT);
    private static final ReasonCode DUPLICATE = code(RefusalCode.PACK_DUPLICATE_ID);
    private static final ReasonCode SIBLING = code(RefusalCode.PACK_SIBLING_CONFLICT);

    // ---- the ruleset and its findings --------------------------------------------------------------------------

    @Nested
    @DisplayName("the ruleset")
    class Ruleset {

        @Test
        @DisplayName("a set that resolves passes, with no finding")
        void aSetThatResolvesPasses() {
            PackUnderResolution base = pack(ordered("BASE"), "base", "1.0.0", PackLevel.BUILT_IN);
            PackUnderResolution dependent =
                    pack(ordered("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), ">=1.0"));

            Rationale rationale = CompositionRules.closure().check(List.of(dependent, base));

            assertThat(rationale.isPassing()).isTrue();
            assertThat(rationale.details()).isEmpty();
        }

        @Test
        @DisplayName("each fault is a failing, unplaced violation that carries its CLOSURE code and the fault's detail")
        void eachFaultIsAFailingViolation() {
            PackUnderResolution dependent =
                    pack(ordered("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(ordered("GONE"), ">=1.0"));

            List<Reason> reasons = CompositionRules.closure().apply(List.of(dependent));

            assertThat(reasons).singleElement().satisfies(reason -> {
                assertThat(reason.isFailing(Severity.VIOLATION)).isTrue();
                assertThat(reason.rule()).isEqualTo(CompositionRules.RULE);
                assertThat(reason.code()).isEqualTo(UNKNOWN).isIn(PackCodes.CLOSURE);
                assertThat(reason.location()).isEqualTo(Location.NOWHERE);
                assertThat(reason.detail())
                        .isEqualTo(
                                "pack `dependent` (01DEP000000000000000000000) depends on pack `01GNZNE0000000000000000000`, which no configured pack"
                                        + " declares");
            });
            assertThat(CompositionRules.closure().check(List.of(dependent)).isFailing())
                    .isTrue();
        }

        @Test
        @DisplayName("the core exposes CLOSURE beside content(): PackRulesets.closure() is this ruleset")
        void thePackRulesetsExposeClosure() {
            PackUnderResolution dependent =
                    pack(ordered("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(ordered("GONE"), ">=1.0"));
            PackUnderResolution base = pack(ordered("BASE"), "base", "1.0.0", PackLevel.BUILT_IN);

            assertThat(PackRulesets.closure().apply(List.of(dependent)))
                    .isEqualTo(CompositionRules.closure().apply(List.of(dependent)))
                    .isNotEmpty();
            assertThat(PackRulesets.closure().apply(List.of(base))).isEmpty();
        }

        @Test
        @DisplayName("an unresolved reference is no CLOSURE fault: the reference checks are the closure walk's (#556)")
        void anUnresolvedReferenceIsNoClosureFault() {
            // The only fault of this set is a reference the reader writes to an artifact the writing pack does not
            // hold.
            PackUnderResolution writing = pack(ordered("WRI"), "writing", "1.0.0", PackLevel.BUILT_IN);
            PackUnderResolution reader =
                    pack(ordered("REA"), "reader", "1.0.0", PackLevel.PROJECT, depends(ordered("WRI"), ">=1.0"));

            assertThat(CompositionRules.closure().apply(List.of(writing, reader)))
                    .isEmpty();
        }

        @Test
        @DisplayName("the preset phase reports through its own rule, over the packs in the order given")
        void thePresetPhaseReportsThroughItsOwnRule() {
            PackUnderResolution left = pack(ordered("LEFT"), "left", "1.0.0", PackLevel.ORG);
            PackUnderResolution right = pack(ordered("RIGHT"), "right", "1.0.0", PackLevel.ORG);
            Map<String, List<String>> presets =
                    Map.of(ordered("LEFT"), List.of("team"), ordered("RIGHT"), List.of("team"));

            List<Reason> reasons = CompositionRules.closure()
                    .must(CompositionRules.presets(holding(presets)))
                    .apply(List.of(left, right));

            assertThat(reasons).singleElement().satisfies(reason -> {
                assertThat(reason.rule()).isEqualTo(CompositionRules.PRESETS_RULE);
                assertThat(reason.code()).isEqualTo(SIBLING);
                assertThat(reason.detail())
                        .isEqualTo(
                                "packs `left` (01KZEFT0000000000000000000) and `right` (01RHZGHT000000000000000000) at org both hold the preset `team`,"
                                        + " and neither extends the other by parent_pack");
            });
            assertThat(CompositionRules.presets(holding(Map.of())).apply(List.of(left, right)))
                    .isEmpty();
        }

        @ParameterizedTest
        @EnumSource(Failure.class)
        @DisplayName("each closure failure answers its PackCodes constant; a reference failure is none of CLOSURE's")
        void eachClosureFailureAnswersItsCode(Failure failure) {
            Set<Failure> references = EnumSet.of(
                    Failure.REFERENCE_UNQUALIFIED, Failure.REFERENCE_PACK_MISSING, Failure.REFERENCE_ARTIFACT_MISSING);
            if (references.contains(failure)) {
                assertThatThrownBy(() -> CompositionRules.codeOf(failure))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(failure.name());
                return;
            }
            ReasonCode code = CompositionRules.codeOf(failure);
            assertThat(code.value()).isEqualTo(failure.code().token());
            assertThat(code.severity()).isEqualTo(Severity.VIOLATION);
            assertThat(PackCodes.CLOSURE).contains(code);
        }

        @Test
        @DisplayName("every CLOSURE code is the code of one closure failure")
        void everyClosureCodeIsTheCodeOfOneFailure() {
            List<ReasonCode> answered = Arrays.stream(Failure.values())
                    .filter(failure -> !failure.name().startsWith("REFERENCE_"))
                    .map(CompositionRules::codeOf)
                    .toList();

            assertThat(answered).containsExactlyInAnyOrderElementsOf(PackCodes.CLOSURE);
        }

        @Test
        @DisplayName("a fault keeps its code, packs and detail; its pack list is copied")
        void aFaultKeepsItsFields() {
            List<RefusalPack> packs = new ArrayList<>(
                    List.of(new RefusalPack(new PackUlid(ordered("A")), registeredIdOf(ordered("A")), "a")));
            CompositionFault fault = new CompositionFault(CYCLE, packs, "why");
            packs.clear();

            assertThat(fault.packs())
                    .containsExactly(new RefusalPack(new PackUlid(ordered("A")), registeredIdOf(ordered("A")), "a"));
            assertThatThrownBy(() -> fault.packs()
                            .add(new RefusalPack(new PackUlid(ordered("B")), registeredIdOf(ordered("B")), "b")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> new CompositionFault(null, List.of(), "d"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new CompositionFault(CYCLE, null, "d")).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new CompositionFault(CYCLE, List.of(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- pack_dependency_cycle ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("pack_dependency_cycle")
    class Cycles {

        @Test
        @DisplayName("a pack that depends on itself is a cycle; a dependency on another pack is not")
        void aPackThatDependsOnItself() {
            PackUnderResolution self =
                    pack(ordered("SELF"), "self", "1.0.0", PackLevel.ORG, depends(ordered("SELF"), ">=1.0"));

            assertThat(CompositionRules.faults(List.of(self))).singleElement().satisfies(fault -> {
                assertThat(fault.code()).isEqualTo(CYCLE);
                assertThat(fault.packs()).containsExactly(refusalPack(ordered("SELF"), "self"));
                assertThat(fault.detail()).isEqualTo("pack `self` (01SEKZF0000000000000000000) depends on itself");
            });
            PackUnderResolution base = pack(ordered("BASE"), "base", "1.0.0", PackLevel.ORG);
            PackUnderResolution dependent =
                    pack(ordered("DEP"), "dep", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), ">=1.0"));
            assertThat(CompositionRules.faults(List.of(dependent, base))).isEmpty();
        }

        @Test
        @DisplayName("a pack that names itself as parent is a cycle")
        void aPackThatNamesItselfAsParent() {
            PackUnderResolution loop = extending(ordered("LOOP"), "loop", PackLevel.ORG, ordered("LOOP"));

            assertThat(CompositionRules.faults(List.of(loop))).singleElement().satisfies(fault -> {
                assertThat(fault.code()).isEqualTo(CYCLE);
                assertThat(fault.detail())
                        .isEqualTo("pack `loop` (01KZNZNZP00000000000000000) names itself as its parent_pack");
            });
        }

        @Test
        @DisplayName("two packs that name each other as parent, or a parent cycle through a dependency, are a cycle")
        void parentCycles() {
            PackUnderResolution first = extending(ordered("FIRST"), "first", PackLevel.ORG, ordered("SECOND"));
            PackUnderResolution second = extending(ordered("SECOND"), "second", PackLevel.ORG, ordered("FIRST"));
            PackUnderResolution parent =
                    pack(ordered("PARENT"), "parent", "1.0.0", PackLevel.ORG, depends(ordered("CHILD"), ">=1.0"));
            PackUnderResolution child = extending(ordered("CHILD"), "child", PackLevel.ORG, ordered("PARENT"));

            assertThat(CompositionRules.faults(List.of(first, second)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(CYCLE);
                        assertThat(fault.packs())
                                .containsExactly(
                                        refusalPack(ordered("FIRST"), "first"),
                                        refusalPack(ordered("SECOND"), "second"));
                        assertThat(fault.detail())
                                .isEqualTo(
                                        "pack `first` (01FHZRST000000000000000000) is on a dependency cycle: `first` -> `second` ->"
                                                + " `first`");
                    });
            assertThat(CompositionRules.faults(List.of(parent, child)))
                    .extracting(CompositionFault::code)
                    .containsExactly(CYCLE);
        }

        @Test
        @DisplayName("a cycle names a pack on it, not one only downstream of it")
        void aCycleNamesAPackOnIt() {
            PackUnderResolution downstream =
                    pack(ordered("AAA"), "a", "1.0.0", PackLevel.ORG, depends(ordered("BBB"), ">=1.0"));
            PackUnderResolution b = pack(ordered("BBB"), "b", "1.0.0", PackLevel.ORG, depends(ordered("CCC"), ">=1.0"));
            PackUnderResolution c = pack(ordered("CCC"), "c", "1.0.0", PackLevel.ORG, depends(ordered("BBB"), ">=1.0"));

            assertThat(CompositionRules.faults(List.of(downstream, b, c)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(CYCLE);
                        assertThat(fault.packs())
                                .containsExactly(refusalPack(ordered("BBB"), "b"), refusalPack(ordered("CCC"), "c"));
                        assertThat(fault.detail())
                                .isEqualTo(
                                        "pack `b` (01BBB000000000000000000000) is on a dependency cycle: `b` -> `c` -> `b`");
                    });
        }

        @Test
        @DisplayName("a pack on a cycle the first walk passed by is named too, and the first cycle is unchanged")
        void overlappingCyclesNameEveryPackOnThem() {
            // A depends on B and C; B and C each depend on A. The walk from A follows B only, so C is named by the
            // shortest cycle through it; D only depends on A, lies on no cycle and is named by none.
            PackUnderResolution a = new PackUnderResolution(
                    configured(ordered("AAA"), PackLevel.ORG),
                    descriptor(
                            ordered("AAA"),
                            "a",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(
                                    new PackDependency(ordered("BBB"), ">=1.0"),
                                    new PackDependency(ordered("CCC"), ">=1.0"))));
            PackUnderResolution b = pack(ordered("BBB"), "b", "1.0.0", PackLevel.ORG, depends(ordered("AAA"), ">=1.0"));
            PackUnderResolution c = pack(ordered("CCC"), "c", "1.0.0", PackLevel.ORG, depends(ordered("AAA"), ">=1.0"));
            PackUnderResolution d = pack(ordered("DDD"), "d", "1.0.0", PackLevel.ORG, depends(ordered("AAA"), ">=1.0"));

            List<CompositionFault> faults = CompositionRules.faults(List.of(d, c, b, a));

            assertThat(faults).allSatisfy(fault -> assertThat(fault.code()).isEqualTo(CYCLE));
            assertThat(faults)
                    .extracting(CompositionFault::detail)
                    .containsExactly(
                            "pack `a` (01AAA000000000000000000000) is on a dependency cycle: `a` -> `b` -> `a`",
                            "pack `c` (01CCC000000000000000000000) is on a dependency cycle: `c` -> `a` -> `c`");
            assertThat(faults.get(1).packs())
                    .containsExactly(refusalPack(ordered("CCC"), "c"), refusalPack(ordered("AAA"), "a"));
        }

        @Test
        @DisplayName("the shortest cycle through a passed-by pack is named, over its longer ones")
        void theShortestCycleThroughAPassedByPackIsNamed() {
            // The walk from A goes A -> B -> C -> A. E is on A -> E -> A and on E -> B -> C -> A -> E: the first is
            // named.
            PackUnderResolution a = new PackUnderResolution(
                    configured(ordered("AAA"), PackLevel.ORG),
                    descriptor(
                            ordered("AAA"),
                            "a",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(
                                    new PackDependency(ordered("BBB"), ">=1.0"),
                                    new PackDependency(ordered("EEE"), ">=1.0"))));
            PackUnderResolution b = pack(ordered("BBB"), "b", "1.0.0", PackLevel.ORG, depends(ordered("CCC"), ">=1.0"));
            PackUnderResolution c = pack(ordered("CCC"), "c", "1.0.0", PackLevel.ORG, depends(ordered("AAA"), ">=1.0"));
            PackUnderResolution e = new PackUnderResolution(
                    configured(ordered("EEE"), PackLevel.ORG),
                    descriptor(
                            ordered("EEE"),
                            "e",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(
                                    new PackDependency(ordered("BBB"), ">=1.0"),
                                    new PackDependency(ordered("AAA"), ">=1.0"))));

            assertThat(CompositionRules.faults(List.of(a, b, c, e)))
                    .extracting(CompositionFault::detail)
                    .containsExactly(
                            "pack `a` (01AAA000000000000000000000) is on a dependency cycle: `a` -> `b` -> `c` -> `a`",
                            "pack `e` (01EEE000000000000000000000) is on a dependency cycle: `e` -> `a` -> `e`");
        }

        @Test
        @DisplayName("a cycle is found past a dependency the order already placed")
        void aCycleIsFoundPastAPlacedDependency() {
            PackUnderResolution base = pack(ordered("BASE"), "base", "1.0.0", PackLevel.ORG);
            PackUnderResolution a = new PackUnderResolution(
                    configured(ordered("AAA"), PackLevel.ORG),
                    descriptor(
                            ordered("AAA"),
                            "a",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(
                                    new PackDependency(ordered("BASE"), ">=1.0"),
                                    new PackDependency(ordered("BBB"), ">=1.0"))));
            PackUnderResolution b = pack(ordered("BBB"), "b", "1.0.0", PackLevel.ORG, depends(ordered("AAA"), ">=1.0"));

            assertThat(CompositionRules.faults(List.of(a, b, base)))
                    .extracting(CompositionFault::detail)
                    .containsExactly(
                            "pack `a` (01AAA000000000000000000000) is on a dependency cycle: `a` -> `b` -> `a`");
        }

        @Test
        @DisplayName("a repeated pack_id is placed once, so a cycle behind it is still found")
        void aRepeatedPackIdIsPlacedOnce() {
            // 01YYY000000000000000000000 depends on the repeated 01XXX000000000000000000000 and on
            // 01ZZZ000000000000000000000, which depends on 01YYY000000000000000000000: the twin must not count as a
            // second placement of 01XXX000000000000000000000, or 01YYY000000000000000000000 would be placed and the
            // cycle missed.
            PackUnderResolution x = pack(ordered("XXX"), "x", "1.0.0", PackLevel.ORG);
            PackUnderResolution twin = configuredAs("twin", ordered("XXX"), "x-twin");
            PackUnderResolution y = new PackUnderResolution(
                    configured(ordered("YYY"), PackLevel.ORG),
                    descriptor(
                            ordered("YYY"),
                            "y",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(
                                    new PackDependency(ordered("XXX"), ">=1.0"),
                                    new PackDependency(ordered("_Z"), ">=1.0"))));
            PackUnderResolution z = pack(ordered("_Z"), "z", "1.0.0", PackLevel.ORG, depends(ordered("YYY"), ">=1.0"));

            assertThat(CompositionRules.faults(List.of(x, twin, y, z)))
                    .extracting(CompositionFault::code)
                    .containsExactly(DUPLICATE, CYCLE);
        }

        @Test
        @DisplayName("two disjoint cycles are two faults, in the order of their first packs")
        void twoDisjointCyclesAreTwoFaults() {
            PackUnderResolution a = pack(ordered("AAA"), "a", "1.0.0", PackLevel.ORG, depends(ordered("BBB"), ">=1.0"));
            PackUnderResolution b = pack(ordered("BBB"), "b", "1.0.0", PackLevel.ORG, depends(ordered("AAA"), ">=1.0"));
            PackUnderResolution x = pack(ordered("XXX"), "x", "1.0.0", PackLevel.ORG, depends(ordered("YYY"), ">=1.0"));
            PackUnderResolution y = pack(ordered("YYY"), "y", "1.0.0", PackLevel.ORG, depends(ordered("XXX"), ">=1.0"));

            assertThat(CompositionRules.faults(List.of(y, x, b, a)))
                    .extracting(CompositionFault::detail)
                    .containsExactly(
                            "pack `a` (01AAA000000000000000000000) is on a dependency cycle: `a` -> `b` -> `a`",
                            "pack `x` (01XXX000000000000000000000) is on a dependency cycle: `x` -> `y` -> `x`");
        }

        @Test
        @DisplayName("a pack downstream of two cycles adds no third, and a cycle is reported once")
        void aCycleIsReportedOnce() {
            PackUnderResolution a = pack(ordered("AAA"), "a", "1.0.0", PackLevel.ORG, depends(ordered("BBB"), ">=1.0"));
            PackUnderResolution b = pack(ordered("BBB"), "b", "1.0.0", PackLevel.ORG, depends(ordered("AAA"), ">=1.0"));
            PackUnderResolution downstream = new PackUnderResolution(
                    configured(ordered("DDD"), PackLevel.ORG),
                    descriptor(
                            ordered("DDD"),
                            "d",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(
                                    new PackDependency(ordered("AAA"), ">=1.0"),
                                    new PackDependency(ordered("XXX"), ">=1.0"))));
            PackUnderResolution x = pack(ordered("XXX"), "x", "1.0.0", PackLevel.ORG, depends(ordered("YYY"), ">=1.0"));
            PackUnderResolution y = pack(ordered("YYY"), "y", "1.0.0", PackLevel.ORG, depends(ordered("XXX"), ">=1.0"));

            assertThat(CompositionRules.faults(List.of(downstream, a, b, x, y)))
                    .extracting(
                            CompositionFault::code,
                            fault -> fault.packs().getFirst().descriptorPackId().value())
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(CYCLE, ordered("AAA")),
                            org.assertj.core.groups.Tuple.tuple(CYCLE, ordered("XXX")));
        }
    }

    // ---- pack_dependency_unknown and pack_parent_unresolved ----------------------------------------------------

    @Nested
    @DisplayName("pack_dependency_unknown and pack_parent_unresolved")
    class UnknownTargets {

        @Test
        @DisplayName("a dependency no configured pack declares is unknown; a declared one is not")
        void anUnknownDependency() {
            PackUnderResolution dependent =
                    pack(ordered("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(ordered("GONE"), ">=1.0"));
            PackUnderResolution gone = pack(ordered("GONE"), "gone", "1.0.0", PackLevel.ORG);

            assertThat(CompositionRules.faults(List.of(dependent)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(UNKNOWN);
                        assertThat(fault.packs()).containsExactly(refusalPack(ordered("DEP"), "dependent"));
                    });
            assertThat(CompositionRules.faults(List.of(dependent, gone))).isEmpty();
        }

        @Test
        @DisplayName("a parent_pack no configured pack declares is unresolved; a declared one is not")
        void anUnresolvedParent() {
            PackUnderResolution orphan = extending(ordered("ORPH"), "orphan", PackLevel.ORG, ordered("MISSING"));
            PackUnderResolution missing = pack(ordered("MISSING"), "missing", "1.0.0", PackLevel.ORG);

            assertThat(CompositionRules.faults(List.of(orphan))).singleElement().satisfies(fault -> {
                assertThat(fault.code()).isEqualTo(PARENT_UNRESOLVED);
                assertThat(fault.detail())
                        .isEqualTo(
                                "pack `orphan` (01NZRPH0000000000000000000) names parent_pack `01MHZSSHZNG000000000000000`, which no configured"
                                        + " pack declares");
            });
            assertThat(CompositionRules.faults(List.of(orphan, missing))).isEmpty();
        }

        @Test
        @DisplayName(
                "every unknown dependency of a pack is reported, and a parent also listed as a dependency is judged")
        void everyUnknownDependencyIsReported() {
            PackUnderResolution base = pack(ordered("BASE"), "base", "1.0.0", PackLevel.ORG);
            PackUnderResolution dependent = new PackUnderResolution(
                    configured(ordered("DEP"), PackLevel.ORG),
                    new PackDescriptor(
                            ordered("DEP"),
                            "1.0.0",
                            Optional.of(ordered("BASE")),
                            "dependent",
                            Optional.of(PackLevel.ORG),
                            Optional.empty(),
                            PackPaths.none(),
                            List.of(
                                    new PackDependency(ordered("ONE"), ">=1.0"),
                                    new PackDependency(ordered("BASE"), ">=1.0"),
                                    new PackDependency(ordered("TWO"), ">=1.0"))));

            assertThat(CompositionRules.faults(List.of(dependent, base)))
                    .extracting(CompositionFault::detail)
                    .containsExactly(
                            "pack `dependent` (01DEP000000000000000000000) depends on pack `01NZNE00000000000000000000`, which no configured pack declares",
                            "pack `dependent` (01DEP000000000000000000000) depends on pack `01TWNZ00000000000000000000`, which no configured pack declares");
        }
    }

    // ---- pack_dependency_out_of_range --------------------------------------------------------------------------

    @Nested
    @DisplayName("pack_dependency_out_of_range")
    class OutOfRange {

        @Test
        @DisplayName("a held version outside the declared range is out of range; one inside it is not")
        void aHeldVersionOutsideTheRange() {
            PackUnderResolution base = pack(ordered("BASE"), "base", "1.0.0", PackLevel.BUILT_IN);
            PackUnderResolution dependent =
                    pack(ordered("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), ">=2.0"));
            PackUnderResolution admitted =
                    pack(ordered("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), ">=1.0"));

            assertThat(CompositionRules.faults(List.of(dependent, base)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(OUT_OF_RANGE);
                        assertThat(fault.packs())
                                .containsExactly(
                                        refusalPack(ordered("DEP"), "dependent"), refusalPack(ordered("BASE"), "base"));
                        assertThat(fault.detail())
                                .isEqualTo(
                                        "pack `dependent` (01DEP000000000000000000000) requires pack `base` (01BASE00000000000000000000) in range `>=2.0`, but"
                                                + " `base` is at version 1.0.0");
                    });
            assertThat(CompositionRules.faults(List.of(admitted, base))).isEmpty();
        }

        /**
         * Two packs ask one pack for ranges; the held version satisfies the first in every row. Which ranges share a
         * version is {@code Pep440RangeTest}'s truth table, so two rows show the wiring.
         */
        @ParameterizedTest
        @CsvSource(
                delimiter = '|',
                value = {">=1.0,<2|>=2.0|1.5.0|false", ">=1.0,<3|>=2.0|2.5.0|true"})
        @DisplayName("two ranges no version satisfies together are refused naming both packs, before the held version")
        void rangesNoVersionSatisfiesTogether(String firstRange, String secondRange, String held, boolean resolves) {
            PackUnderResolution base = pack(ordered("BASE"), "base", held, PackLevel.BUILT_IN);
            PackUnderResolution first =
                    pack(ordered("ONE"), "one", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), firstRange));
            PackUnderResolution second =
                    pack(ordered("TWO"), "two", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), secondRange));

            List<CompositionFault> faults = CompositionRules.faults(List.of(second, first, base));

            if (resolves) {
                assertThat(faults).isEmpty();
                return;
            }
            assertThat(faults.getFirst().code()).isEqualTo(OUT_OF_RANGE);
            assertThat(faults.getFirst().packs())
                    .containsExactly(refusalPack(ordered("TWO"), "two"), refusalPack(ordered("ONE"), "one"));
            assertThat(faults.getFirst().detail())
                    .isEqualTo(
                            "packs `one` (01NZNE00000000000000000000) and `two` (01TWNZ00000000000000000000) require pack `base` (01BASE00000000000000000000) in ranges `"
                                    + firstRange + "` and `" + secondRange + "`, which no version satisfies together");
            assertThat(faults)
                    .as("the held version is then checked against each range: the second's is out of range too")
                    .extracting(CompositionFault::code)
                    .containsExactly(OUT_OF_RANGE, OUT_OF_RANGE);
        }

        @Test
        @DisplayName("every disjoint pair is reported, in the order the packs ask")
        void everyDisjointPairIsReported() {
            PackUnderResolution base = pack(ordered("BASE"), "base", "5.0.0", PackLevel.BUILT_IN);
            PackUnderResolution one =
                    pack(ordered("ONE"), "one", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), "<2"));
            PackUnderResolution two =
                    pack(ordered("TWO"), "two", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), ">=2,<3"));
            PackUnderResolution three =
                    pack(ordered("TRE"), "three", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), ">=3"));

            List<CompositionFault> ranges = CompositionRules.faults(List.of(three, two, one, base)).stream()
                    .filter(fault -> fault.detail().contains("no version satisfies together"))
                    .toList();

            assertThat(ranges)
                    .extracting(
                            fault -> fault.packs().getFirst().descriptorPackId().value() + "/"
                                    + fault.packs().get(1).descriptorPackId().value())
                    .containsExactly(
                            "01TRE000000000000000000000/01NZNE00000000000000000000",
                            "01TWNZ00000000000000000000/01NZNE00000000000000000000",
                            "01TWNZ00000000000000000000/01TRE000000000000000000000");
        }
    }

    // ---- pack_level_violation ----------------------------------------------------------------------------------

    private static final List<PackLevel> FROM_BUILT_IN = List.of(PackLevel.BUILT_IN, PackLevel.ORG, PackLevel.PROJECT);

    static Stream<Arguments> levelMatrix() {
        List<Arguments> rows = new ArrayList<>();
        for (boolean parent : new boolean[] {false, true}) {
            for (PackLevel own : PackLevel.values()) {
                for (PackLevel target : PackLevel.values()) {
                    rows.add(Arguments.of(
                            parent, own, target, FROM_BUILT_IN.indexOf(target) <= FROM_BUILT_IN.indexOf(own)));
                }
            }
        }
        return rows.stream();
    }

    /** Every pair of levels, for each edge: a pack reaches its own level or one nearer built-in, and no further. */
    @ParameterizedTest(name = "{0}: {1} -> {2} allowed={3}")
    @MethodSource("levelMatrix")
    @DisplayName("pack_level_violation: a dependency or parent further from built-in than the pack's own level")
    void aPackTakesADependencyOrParentOnlyAtItsOwnLevelOrOneNearerBuiltIn(
            boolean parentEdge, PackLevel own, PackLevel target, boolean allowed) {
        PackUnderResolution targetPack = pack(ordered("TRGT"), "target", "1.0.0", target);
        PackUnderResolution ownPack = parentEdge
                ? extending(ordered("OWN0"), "own", own, ordered("TRGT"))
                : pack(ordered("OWN0"), "own", "1.0.0", own, depends(ordered("TRGT"), ">=1.0"));

        List<CompositionFault> faults = CompositionRules.faults(List.of(ownPack, targetPack));

        if (allowed) {
            assertThat(faults).isEmpty();
            return;
        }
        assertThat(faults).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(LEVEL);
            assertThat(fault.packs())
                    .containsExactly(refusalPack(ordered("OWN0"), "own"), refusalPack(ordered("TRGT"), "target"));
            assertThat(fault.detail())
                    .isEqualTo(
                            "pack `own` (01NZWN01000000000000000000) at " + own.token()
                                    + (parentEdge
                                            ? " names pack `target` (01TRGT00000000000000000000) at " + target.token()
                                                    + " as its parent_pack"
                                            : " depends on pack `target` (01TRGT00000000000000000000) at "
                                                    + target.token())
                                    + "; a pack may take a dependency or a parent only at its own level or one nearer built-in");
        });
    }

    // ---- pack_name_conflict and pack_duplicate_id --------------------------------------------------------------

    @Nested
    @DisplayName("pack_name_conflict and pack_duplicate_id")
    class Identity {

        @Test
        @DisplayName("two packs with one name conflict, naming the later first; two names do not")
        void twoPacksWithOneName() {
            PackUnderResolution first = pack(ordered("AAA"), "shared-name", "1.0.0", PackLevel.ORG);
            PackUnderResolution second = pack(ordered("BBB"), "shared-name", "1.0.0", PackLevel.ORG);
            PackUnderResolution other = pack(ordered("BBB"), "other-name", "1.0.0", PackLevel.ORG);

            assertThat(CompositionRules.faults(List.of(second, first)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(NAME);
                        assertThat(fault.packs())
                                .containsExactly(
                                        refusalPack(ordered("BBB"), "shared-name"),
                                        refusalPack(ordered("AAA"), "shared-name"));
                        assertThat(fault.detail())
                                .isEqualTo(
                                        "packs `shared-name` (01AAA000000000000000000000) and `shared-name` (01BBB000000000000000000000) both declare the name"
                                                + " `shared-name`");
                    });
            assertThat(CompositionRules.faults(List.of(first, other))).isEmpty();
        }

        @Test
        @DisplayName("two packs declaring one pack_id are a duplicate; every further holder is one more")
        void twoPacksDeclaringOnePackId() {
            PackUnderResolution gamma = pack(ordered("GAMMA"), "gamma", "1.0.0", PackLevel.ORG);
            PackUnderResolution twin = configuredAs("twin", ordered("GAMMA"), "gamma-twin");
            PackUnderResolution triplet = configuredAs("zz-triplet", ordered("GAMMA"), "gamma-triplet");

            assertThat(CompositionRules.faults(List.of(triplet, twin, gamma)))
                    .allSatisfy(fault -> assertThat(fault.code()).isEqualTo(DUPLICATE))
                    .extracting(CompositionFault::packs)
                    .containsExactly(
                            List.of(
                                    new RefusalPack(
                                            new PackUlid(ordered("GAMMA")), new PackIdentifier("twin"), "gamma-twin"),
                                    refusalPack(ordered("GAMMA"), "gamma")),
                            List.of(
                                    new RefusalPack(
                                            new PackUlid(ordered("GAMMA")),
                                            new PackIdentifier("zz-triplet"),
                                            "gamma-triplet"),
                                    refusalPack(ordered("GAMMA"), "gamma")));
            assertThat(CompositionRules.faults(List.of(gamma))).isEmpty();
        }

        @Test
        @DisplayName("a twin that contests what its first holder defines adds no sibling contest")
        void aTwinAddsNoSiblingContest() {
            PackUnderResolution gamma = defining(ordered("GAMMA"), "gamma", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution twin = new PackUnderResolution(
                    configured("twin", PackLevel.ORG),
                    descriptor(ordered("GAMMA"), "gamma-twin", "1.0.0", PackLevel.ORG, List.of()),
                    List.of(),
                    List.of(DIRECTIVE));

            assertThat(CompositionRules.faults(List.of(twin, gamma)))
                    .extracting(CompositionFault::code)
                    .containsExactly(DUPLICATE);
        }

        @Test
        @DisplayName("a dependency on a duplicated pack_id is judged against its first holder")
        void aDependencyOnADuplicatedIdIsJudgedAgainstItsFirstHolder() {
            PackUnderResolution base = pack(ordered("BASE"), "base", "1.0.0", PackLevel.BUILT_IN);
            PackUnderResolution twin = new PackUnderResolution(
                    configured("base-twin", PackLevel.BUILT_IN),
                    descriptor(ordered("BASE"), "base-twin", "3.0.0", PackLevel.BUILT_IN, List.of()));
            PackUnderResolution dependent =
                    pack(ordered("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), ">=2.0"));

            assertThat(CompositionRules.faults(List.of(base, twin, dependent)))
                    .extracting(CompositionFault::code)
                    .containsExactly(DUPLICATE, OUT_OF_RANGE);
        }
    }

    // ---- pack_sibling_conflict ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("pack_sibling_conflict")
    class SiblingContests {

        @Test
        @DisplayName("two siblings changing one artifact conflict, naming the second first; at two levels they merge")
        void twoSiblingsChangingOneArtifact() {
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution project = refining(ordered("PROJ"), "project", PackLevel.PROJECT, DIRECTIVE);

            assertThat(CompositionRules.faults(List.of(right, left)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.packs())
                                .containsExactly(
                                        refusalPack(ordered("RIGHT"), "right"), refusalPack(ordered("LEFT"), "left"));
                        assertThat(fault.detail())
                                .isEqualTo(
                                        "packs `left` (01KZEFT0000000000000000000) and `right` (01RHZGHT000000000000000000) at org both change the artifact"
                                                + " `directive:DIRECTIVE_009`, and no pack of the compose declares an overrides"
                                                + " edge settling it");
                    });
            assertThat(CompositionRules.faults(List.of(left, project))).isEmpty();
        }

        @Test
        @DisplayName("two siblings defining one artifact conflict; a mission type's step adds no contest of its own")
        void twoSiblingsDefiningOneArtifact() {
            PackUnderResolution left =
                    defining(ordered("LEFT"), "left", PackLevel.ORG, "mission_type:m", "action:m/implement");
            PackUnderResolution right =
                    defining(ordered("RIGHT"), "right", PackLevel.ORG, "mission_type:m", "action:m/implement");
            PackUnderResolution stepOnlyLeft = defining(ordered("LEFT"), "left", PackLevel.ORG, "action:m/implement");
            PackUnderResolution stepOnlyRight =
                    defining(ordered("RIGHT"), "right", PackLevel.ORG, "action:m/implement");

            assertThat(CompositionRules.faults(List.of(right, left)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.detail())
                                .contains("both define the artifact `mission_type:m`")
                                .doesNotContain("action:");
                    });
            assertThat(CompositionRules.faults(List.of(stepOnlyRight, stepOnlyLeft)))
                    .isEmpty();
        }

        @Test
        @DisplayName("a qualifier does not make two writings of one artifact two contests")
        void aQualifierDoesNotMakeTwoContests() {
            PackUnderResolution bare = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution qualified = refining(ordered("RIGHT"), "right", PackLevel.ORG, "base::" + DIRECTIVE);

            assertThat(CompositionRules.faults(List.of(bare, qualified)))
                    .extracting(CompositionFault::code)
                    .containsExactly(SIBLING);
        }

        @ParameterizedTest
        @ValueSource(strings = {"01RHZGHT000000000000000000", "01KZEFT0000000000000000000"})
        @DisplayName("#358: two distribution packs settling one artifact conflict, whether or not they name one winner")
        void twoRootsSettlingOneArtifact(String secondWinner) {
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution first =
                    settling(ordered("DONE"), "done", DIRECTIVE, ordered("LEFT"), ordered("LEFT"), ordered("RIGHT"));
            PackUnderResolution second = settling(
                    ordered("DTWO"), "dtwo", "org::" + DIRECTIVE, secondWinner, ordered("LEFT"), ordered("RIGHT"));

            assertThat(CompositionRules.faults(List.of(second, first, right, left)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.packs())
                                .containsExactly(
                                        refusalPack(ordered("DONE"), "done"),
                                        refusalPack(ordered("DTWO"), "dtwo"),
                                        refusalPack(ordered("RIGHT"), "right"),
                                        refusalPack(ordered("LEFT"), "left"));
                        assertThat(fault.detail())
                                .endsWith(
                                        "and overrides edges settling it are declared by packs `done` (01DNZNE0000000000000000000) and"
                                                + " `dtwo` (01DTWNZ0000000000000000000), more than one distribution pack, and only one may settle"
                                                + " a contest");
                    });
            assertThat(CompositionRules.faults(List.of(first, right, left)))
                    .as("the control: the one distribution pack settles it")
                    .isEmpty();
        }

        // A14 (FR-035, DM-01M6RA92BS6GGCYTRCFQDYYY5S, rule C15): a settlement names exactly one winner. One descriptor
        // cannot name two (it is refused as it is read), and the overrides edges of the compose naming two contestants
        // answer the conflict; exactly one winner settles the contest.
        @Test
        @DisplayName("A14: a settlement naming two winners conflicts, and exactly one winner settles the contest")
        void aSettlementNamingTwoWinners() {
            assertThatThrownBy(() -> new PackDescriptor(
                            ordered("DSTR"),
                            "1.0.0",
                            Optional.empty(),
                            "distribution",
                            Optional.of(PackLevel.ORG),
                            Optional.empty(),
                            PackPaths.none(),
                            List.of(),
                            List.of(
                                    new PackOverride(DIRECTIVE, ordered("LEFT")),
                                    new PackOverride(DIRECTIVE, ordered("RIGHT")))))
                    .as("one descriptor naming two winners of one artifact")
                    .isInstanceOf(IllegalArgumentException.class);
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution namingLeft = settling(
                    ordered("DSTR"), "distribution", DIRECTIVE, ordered("LEFT"), ordered("LEFT"), ordered("RIGHT"));
            PackUnderResolution namingRight =
                    settling(ordered("DTWO"), "dtwo", DIRECTIVE, ordered("RIGHT"), ordered("LEFT"), ordered("RIGHT"));

            assertThat(CompositionRules.faults(List.of(namingLeft, namingRight, left, right)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.detail())
                                .contains("more than one distribution pack", ordered("DSTR"), ordered("DTWO"));
                    });
            assertThat(CompositionRules.faults(List.of(namingLeft, left, right)))
                    .isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"01GHNZST000000000000000000", "01DSTR00000000000000000000"})
        @DisplayName("an overrides edge naming a member that is not a contestant settles nothing")
        void anEdgeNamingANonContestantSettlesNothing(String member) {
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution distribution = settling(ordered("DSTR"), "distribution", DIRECTIVE, member);

            assertThat(CompositionRules.faults(List.of(distribution, left, right)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.detail())
                                .endsWith("and an overrides edge settling it names " + member
                                        + ", which is not a contestant");
                    });
        }

        // #358 (operator ruling (a), 2026-11-17, refined on Q4 2026-11-18): the distribution pack is derived, the pack
        // that depends on every contestant and that no pack of the contest's level depends on (ADR-0048 d8, d9, d12).
        // Only its overrides edge settles a contest.
        @Test
        @DisplayName("#358: the distribution pack, which depends on both contestants, settles the contest")
        void theDistributionPackSettles() {
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution distribution = settling(
                    ordered("DSTR"), "distribution", DIRECTIVE, ordered("LEFT"), ordered("LEFT"), ordered("RIGHT"));

            assertThat(CompositionRules.faults(List.of(distribution, right, left)))
                    .isEmpty();
            assertThat(CompositionRules.closure()
                            .check(List.of(distribution, right, left))
                            .isFailing())
                    .isFalse();
        }

        @Test
        @DisplayName("#358: a member another pack depends on does not settle the contest, and the refusal names it")
        void aMemberDoesNotSettle() {
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution member = settling(ordered("MEMB"), "member", DIRECTIVE, ordered("LEFT"));
            PackUnderResolution distribution = pack(
                    ordered("DSTR"),
                    "distribution",
                    "1.0.0",
                    PackLevel.ORG,
                    List.of(
                            new PackDependency(ordered("LEFT"), ">=1.0"),
                            new PackDependency(ordered("RIGHT"), ">=1.0"),
                            new PackDependency(ordered("MEMB"), ">=1.0")));

            assertThat(CompositionRules.faults(List.of(distribution, member, right, left)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.packs())
                                .as("the declaring pack broke the rule, so it is named first")
                                .containsExactly(
                                        refusalPack(ordered("MEMB"), "member"),
                                        refusalPack(ordered("RIGHT"), "right"),
                                        refusalPack(ordered("LEFT"), "left"));
                        assertThat(fault.detail())
                                .isEqualTo(
                                        "packs `left` (01KZEFT0000000000000000000) and `right` (01RHZGHT000000000000000000) at org both change the artifact"
                                                + " `directive:DIRECTIVE_009`, and an overrides edge settling it is declared by"
                                                + " pack `member` (01MEMB00000000000000000000), which is not the distribution pack, the pack that"
                                                + " depends on every contestant and that no pack of the contest's level depends"
                                                + " on");
                    });
            assertThat(CompositionRules.closure()
                            .check(List.of(distribution, member, right, left))
                            .isFailing())
                    .as("the ruleset, as every surface runs it")
                    .isTrue();
        }

        @Test
        @DisplayName("#358: a contestant does not settle its own contest, even when nothing depends on it")
        void aContestantDoesNotSettleItsOwnContest() {
            PackUnderResolution left =
                    refiningAndSettling(ordered("LEFT"), "left", DIRECTIVE, ordered("LEFT"), ordered("RIGHT"));
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution flatLeft = refiningAndSettling(ordered("LEFT"), "left", DIRECTIVE, ordered("LEFT"));

            for (List<PackUnderResolution> compose : List.of(List.of(left, right), List.of(flatLeft, right))) {
                assertThat(CompositionRules.faults(compose)).singleElement().satisfies(fault -> {
                    assertThat(fault.code()).isEqualTo(SIBLING);
                    assertThat(fault.packs())
                            .containsExactly(
                                    refusalPack(ordered("LEFT"), "left"), refusalPack(ordered("RIGHT"), "right"));
                    assertThat(fault.detail())
                            .endsWith(
                                    "an overrides edge settling it is declared by pack `left` (01KZEFT0000000000000000000),"
                                            + " which is not the distribution pack"
                                            + ", the pack that depends on every contestant and that no pack of the"
                                            + " contest's level depends on");
                });
            }
        }

        @Test
        @DisplayName("#358: a member's edge is refused even beside the distribution pack's edge")
        void aMembersEdgeBesideTheDistributionPacks() {
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution member = settling(ordered("MEMB"), "member", DIRECTIVE, ordered("RIGHT"));
            PackUnderResolution distribution = settling(
                    ordered("DSTR"),
                    "distribution",
                    DIRECTIVE,
                    ordered("LEFT"),
                    ordered("LEFT"),
                    ordered("RIGHT"),
                    ordered("MEMB"));

            assertThat(CompositionRules.faults(List.of(distribution, member, right, left)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.packs()).first().isEqualTo(refusalPack(ordered("MEMB"), "member"));
                        assertThat(fault.detail()).contains("declared by pack `member` (01MEMB00000000000000000000)");
                    });
        }

        @Test
        @DisplayName("#358: a pack that does not depend on the contestants does not settle their contest")
        void anUnrelatedPackDoesNotSettle() {
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution unrelated = settling(ordered("UNRL"), "unrelated", DIRECTIVE, ordered("LEFT"));
            PackUnderResolution halfway =
                    settling(ordered("HALF"), "halfway", DIRECTIVE, ordered("LEFT"), ordered("LEFT"));

            for (PackUnderResolution settler : List.of(unrelated, halfway)) {
                assertThat(CompositionRules.faults(List.of(settler, right, left)))
                        .singleElement()
                        .satisfies(fault -> {
                            assertThat(fault.code()).isEqualTo(SIBLING);
                            assertThat(fault.packs().getFirst().packId().value())
                                    .isEqualTo(settler.configured().id().value());
                            assertThat(fault.detail()).contains("which is not the distribution pack");
                        });
            }
        }

        @Test
        @DisplayName("#358: a dependent at another level leaves the distribution pack of its own level")
        void aDependentAtAnotherLevelLeavesTheDistribution() {
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution distribution = settling(
                    ordered("DSTR"), "distribution", DIRECTIVE, ordered("LEFT"), ordered("LEFT"), ordered("RIGHT"));
            PackUnderResolution project =
                    pack(ordered("PROJ"), "project", "1.0.0", PackLevel.PROJECT, depends(ordered("DSTR"), ">=1.0"));
            PackUnderResolution orgDependent =
                    pack(ordered("ORGD"), "org-dependent", "1.0.0", PackLevel.ORG, depends(ordered("DSTR"), ">=1.0"));

            assertThat(CompositionRules.faults(List.of(project, distribution, right, left)))
                    .isEmpty();
            assertThat(CompositionRules.faults(List.of(orgDependent, distribution, right, left)))
                    .as("the control: a dependent at the contest's level makes the settler a member")
                    .singleElement()
                    .satisfies(
                            fault -> assertThat(fault.detail())
                                    .contains(
                                            "declared by pack `distribution` (01DSTR00000000000000000000), which is not the distribution pack"));
        }

        @Test
        @DisplayName("#358 Q4: a pack of another level that reaches every contestant is their distribution pack")
        void aPackOfAnotherLevelReachingTheContestantsSettles() {
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution project = new PackUnderResolution(
                    configured(ordered("PROJ"), PackLevel.PROJECT),
                    new PackDescriptor(
                            ordered("PROJ"),
                            "1.0.0",
                            Optional.empty(),
                            "project",
                            Optional.of(PackLevel.PROJECT),
                            Optional.empty(),
                            PackPaths.none(),
                            List.of(
                                    new PackDependency(ordered("LEFT"), ">=1.0"),
                                    new PackDependency(ordered("RIGHT"), ">=1.0")),
                            List.of(new PackOverride(DIRECTIVE, ordered("LEFT")))));
            PackUnderResolution orgDistribution = settling(
                    ordered("DSTR"), "distribution", DIRECTIVE, ordered("RIGHT"), ordered("LEFT"), ordered("RIGHT"));

            assertThat(CompositionRules.faults(List.of(project, right, left))).isEmpty();
            assertThat(CompositionRules.faults(List.of(project, orgDistribution, right, left)))
                    .as("with an org distribution settling too, there are two distribution packs")
                    .singleElement()
                    .satisfies(fault -> assertThat(fault.detail()).contains("more than one distribution pack"));
        }

        @Test
        @DisplayName("#358: parent_pack counts as depends_on, for the reach and for who depends on the distribution")
        void parentPackCountsAsADependency() {
            PackUnderResolution left = refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution extendingLeft = new PackUnderResolution(
                    configured(ordered("DSTR"), PackLevel.ORG),
                    new PackDescriptor(
                            ordered("DSTR"),
                            "1.0.0",
                            Optional.of(ordered("LEFT")),
                            "distribution",
                            Optional.of(PackLevel.ORG),
                            Optional.empty(),
                            PackPaths.none(),
                            depends(ordered("RIGHT"), ">=1.0"),
                            List.of(new PackOverride(DIRECTIVE, ordered("RIGHT")))));
            PackUnderResolution distribution = settling(
                    ordered("DSTR"), "distribution", DIRECTIVE, ordered("LEFT"), ordered("LEFT"), ordered("RIGHT"));
            PackUnderResolution child = extending(ordered("CHLD"), "child", PackLevel.ORG, ordered("DSTR"));

            assertThat(CompositionRules.faults(List.of(extendingLeft, right, left)))
                    .as("the distribution reaches left through its parent_pack")
                    .isEmpty();
            assertThat(CompositionRules.faults(List.of(child, distribution, right, left)))
                    .as("a child of the distribution at its level makes it a member, so its edge is refused")
                    .singleElement()
                    .satisfies(fault -> assertThat(fault.packs().getFirst())
                            .isEqualTo(refusalPack(ordered("DSTR"), "distribution")));
        }

        @Test
        @DisplayName("every contest is reported, the changed artifacts before the defined ones")
        void everyContestIsReported() {
            PackUnderResolution left = new PackUnderResolution(
                    configured(ordered("LEFT"), PackLevel.ORG),
                    descriptor(ordered("LEFT"), "left", "1.0.0", PackLevel.ORG, List.of()),
                    List.of(DIRECTIVE, "tactic:t"),
                    List.of("styleguide:s"));
            PackUnderResolution right = new PackUnderResolution(
                    configured(ordered("RIGHT"), PackLevel.ORG),
                    descriptor(ordered("RIGHT"), "right", "1.0.0", PackLevel.ORG, List.of()),
                    List.of(DIRECTIVE, "tactic:t"),
                    List.of("styleguide:s"));

            assertThat(CompositionRules.faults(List.of(right, left)))
                    .extracting(CompositionFault::detail)
                    .extracting(detail -> detail.substring(detail.indexOf("both"), detail.indexOf(", and")))
                    .containsExactly(
                            "both change the artifact `directive:DIRECTIVE_009`",
                            "both change the artifact `tactic:t`",
                            "both define the artifact `styleguide:s`");
        }

        @Nested
        @DisplayName("of presets")
        class Presets {

            private final Map<String, List<String>> presetIds = new LinkedHashMap<>();

            private PackUnderResolution holdingPresets(PackUnderResolution pack, String... presets) {
                presetIds.put(pack.descriptor().packId(), List.of(presets));
                return pack;
            }

            private List<CompositionFault> check(PackUnderResolution... packs) {
                return CompositionRules.presetFaults(List.of(packs), holding(presetIds));
            }

            @Test
            @DisplayName("two unrelated packs of one level holding one preset id conflict; different ids do not")
            void twoUnrelatedPacksOfOneLevel() {
                PackUnderResolution left =
                        holdingPresets(pack(ordered("LEFT"), "left", "1.0.0", PackLevel.ORG), "team");
                PackUnderResolution right =
                        holdingPresets(pack(ordered("RIGHT"), "right", "1.0.0", PackLevel.ORG), "team");
                PackUnderResolution crew =
                        holdingPresets(pack(ordered("CREW"), "crew", "1.0.0", PackLevel.ORG), "crew");

                assertThat(check(left, right)).singleElement().satisfies(fault -> {
                    assertThat(fault.code()).isEqualTo(SIBLING);
                    assertThat(fault.packs())
                            .containsExactly(
                                    refusalPack(ordered("RIGHT"), "right"), refusalPack(ordered("LEFT"), "left"));
                });
                assertThat(check(left, crew)).isEmpty();
            }

            @Test
            @DisplayName("a child and its parent or grandparent are related; two children of one parent are siblings")
            void relatedAndSiblingPacks() {
                PackUnderResolution top = holdingPresets(pack(ordered("TOP"), "top", "1.0.0", PackLevel.ORG), "team");
                PackUnderResolution middle = extending(ordered("MID"), "middle", PackLevel.ORG, ordered("TOP"));
                PackUnderResolution leaf =
                        holdingPresets(extending(ordered("LEAF"), "leaf", PackLevel.ORG, ordered("MID")), "team");
                PackUnderResolution other =
                        holdingPresets(extending(ordered("OTHER"), "other", PackLevel.ORG, ordered("TOP")), "crew");
                PackUnderResolution one =
                        holdingPresets(extending(ordered("ONE"), "one", PackLevel.ORG, ordered("MID")), "crew");

                assertThat(check(top, middle, leaf)).isEmpty();
                assertThat(check(top, other, middle, one))
                        .extracting(CompositionFault::code)
                        .containsExactly(SIBLING);
            }

            @Test
            @DisplayName("a child and its parent may hold one preset id: the child's copy takes the parent's place")
            void aChildAndItsParent() {
                PackUnderResolution parent =
                        holdingPresets(pack(ordered("PARENT"), "parent", "1.0.0", PackLevel.ORG), "team");
                PackUnderResolution child =
                        holdingPresets(extending(ordered("CHILD"), "child", PackLevel.ORG, ordered("PARENT")), "team");

                assertThat(check(child, parent)).isEmpty();
                assertThat(check(parent, child)).isEmpty();
            }

            @Test
            @DisplayName("the packs are judged in merge order, whatever order they are given in")
            void thePacksAreJudgedInMergeOrder() {
                // 01AAA000000000000000000000 depends on 01ZZZ000000000000000000000, so 01ZZZ000000000000000000000
                // merges first although it sorts last.
                PackUnderResolution first = holdingPresets(
                        pack(ordered("AAA"), "a", "1.0.0", PackLevel.ORG, depends(ordered("_Z"), ">=1.0")), "team");
                PackUnderResolution last = holdingPresets(pack(ordered("_Z"), "z", "1.0.0", PackLevel.ORG), "team");

                List<CompositionFault> given = check(first, last);

                assertThat(given).singleElement().satisfies(fault -> {
                    assertThat(fault.packs())
                            .containsExactly(refusalPack(ordered("AAA"), "a"), refusalPack(ordered("_Z"), "z"));
                    assertThat(fault.detail())
                            .startsWith(
                                    "packs `z` (01ZZZ000000000000000000000) and `a` (01AAA000000000000000000000) at org");
                });
                assertThat(check(last, first)).isEqualTo(given);
                assertThat(CompositionRules.presets(holding(presetIds)).apply(List.of(first, last)))
                        .extracting(Reason::detail)
                        .containsExactly(given.getFirst().detail());
            }

            @Test
            @DisplayName("packs of different levels may hold one preset id: the layer merge applies")
            void packsOfDifferentLevels() {
                PackUnderResolution org = holdingPresets(pack(ordered("ORG"), "org", "1.0.0", PackLevel.ORG), "team");
                PackUnderResolution project =
                        holdingPresets(pack(ordered("PROJ"), "project", "1.0.0", PackLevel.PROJECT), "team");

                assertThat(check(org, project)).isEmpty();
            }

            @Test
            @DisplayName("every contesting pair is reported, and a parent cycle ends the walk")
            void everyContestingPairIsReported() {
                PackUnderResolution a =
                        holdingPresets(extending(ordered("AAA"), "a", PackLevel.ORG, ordered("BBB")), "team");
                PackUnderResolution b =
                        holdingPresets(extending(ordered("BBB"), "b", PackLevel.ORG, ordered("AAA")), "team");
                PackUnderResolution c =
                        holdingPresets(pack(ordered("CCC"), "c", "1.0.0", PackLevel.ORG), "team", "team");

                // Merge order: c, placed; then a and b, on a parent cycle, in the stable order.
                assertThat(check(a, b, c))
                        .extracting(fault -> fault.packs()
                                        .getFirst()
                                        .descriptorPackId()
                                        .value() + "/"
                                + fault.packs().get(1).descriptorPackId().value())
                        .containsExactly(
                                "01AAA000000000000000000000/01CCC000000000000000000000",
                                "01BBB000000000000000000000/01CCC000000000000000000000");
            }
        }
    }

    // ---- several faults ------------------------------------------------------------------------------------------

    static Stream<Arguments> multiFaultSets() {
        return Stream.of(
                Arguments.of(
                        "a cycle and an unknown dependency",
                        List.of(
                                pack(ordered("AAA"), "a", "1.0.0", PackLevel.ORG, depends(ordered("BBB"), ">=1.0")),
                                pack(ordered("BBB"), "b", "1.0.0", PackLevel.ORG, depends(ordered("AAA"), ">=1.0")),
                                pack(ordered("CCC"), "c", "1.0.0", PackLevel.ORG, depends(ordered("GONE"), ">=1.0"))),
                        List.of(UNKNOWN, CYCLE)),
                Arguments.of(
                        "a name conflict and a sibling contest",
                        List.of(
                                defining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                                defining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE),
                                pack(ordered("AAA"), "shared-name", "1.0.0", PackLevel.ORG),
                                pack(ordered("BBB"), "shared-name", "1.0.0", PackLevel.ORG)),
                        List.of(NAME, SIBLING)),
                Arguments.of(
                        "a duplicate id and an out-of-range dependency",
                        List.of(
                                pack(ordered("BASE"), "base", "1.0.0", PackLevel.BUILT_IN),
                                new PackUnderResolution(
                                        configured("base-twin", PackLevel.BUILT_IN),
                                        descriptor(
                                                ordered("BASE"), "base-twin", "1.0.0", PackLevel.BUILT_IN, List.of())),
                                pack(
                                        ordered("DEP"),
                                        "dependent",
                                        "1.0.0",
                                        PackLevel.ORG,
                                        depends(ordered("BASE"), ">=2.0"))),
                        List.of(DUPLICATE, OUT_OF_RANGE)),
                Arguments.of(
                        "a level violation and an unresolved parent",
                        List.of(
                                pack(
                                        ordered("OWN0"),
                                        "own",
                                        "1.0.0",
                                        PackLevel.BUILT_IN,
                                        depends(ordered("TRGT"), ">=1.0")),
                                pack(ordered("TRGT"), "target", "1.0.0", PackLevel.PROJECT),
                                extending(ordered("ORPH"), "orphan", PackLevel.ORG, ordered("MISSING"))),
                        List.of(LEVEL, PARENT_UNRESOLVED)),
                everyKindAtOnce());
    }

    /** One fault of every kind in one set: the faults come in the resolver's order. */
    private static Arguments everyKindAtOnce() {
        return Arguments.of(
                "every kind at once, in the resolver's order",
                List.of(
                        pack(ordered("SELF"), "self", "1.0.0", PackLevel.ORG, depends(ordered("SELF"), ">=1.0")),
                        pack(ordered("AAA"), "a", "1.0.0", PackLevel.ORG, depends(ordered("BBB"), ">=1.0")),
                        pack(ordered("BBB"), "b", "1.0.0", PackLevel.ORG, depends(ordered("AAA"), ">=1.0")),
                        pack(ordered("BASE"), "base", "1.0.0", PackLevel.BUILT_IN),
                        new PackUnderResolution(
                                configured("base-twin", PackLevel.BUILT_IN),
                                descriptor(ordered("BASE"), "base-twin", "1.0.0", PackLevel.BUILT_IN, List.of())),
                        pack(ordered("NAME"), "a", "1.0.0", PackLevel.ORG),
                        pack(ordered("ONE"), "one", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), "<1")),
                        pack(ordered("TWO"), "two", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), ">=1")),
                        pack(ordered("UNK"), "unknown", "1.0.0", PackLevel.ORG, depends(ordered("GONE"), ">=1.0")),
                        pack(ordered("PRJ"), "project", "1.0.0", PackLevel.PROJECT),
                        pack(ordered("LVL"), "level", "1.0.0", PackLevel.ORG, depends(ordered("PRJ"), ">=1.0")),
                        extending(ordered("ORPH"), "orphan", PackLevel.ORG, ordered("MISSING")),
                        refining(ordered("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(ordered("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)),
                List.of(
                        DUPLICATE,
                        NAME,
                        OUT_OF_RANGE,
                        LEVEL,
                        OUT_OF_RANGE,
                        PARENT_UNRESOLVED,
                        CYCLE,
                        UNKNOWN,
                        SIBLING,
                        CYCLE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiFaultSets")
    @DisplayName("a set with several faults reports every one, and the first is the refusal resolve throws")
    void aSetWithSeveralFaultsReportsEveryOne(String name, List<PackUnderResolution> packs, List<ReasonCode> codes) {
        List<CompositionFault> faults = CompositionRules.faults(packs);

        assertThat(faults).extracting(CompositionFault::code).containsExactlyElementsOf(codes);
        assertThat(CompositionRules.closure().apply(packs))
                .extracting(Reason::code, Reason::detail)
                .containsExactlyElementsOf(faults.stream()
                        .map(fault -> org.assertj.core.groups.Tuple.tuple(fault.code(), fault.detail()))
                        .toList());
        assertThatThrownBy(() -> PackCompositionResolver.resolve(packs))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(CompositionRules.codeOf(refusal.failure()))
                            .isEqualTo(faults.getFirst().code());
                    assertThat(refusal.packs()).isEqualTo(faults.getFirst().packs());
                    assertThat(refusal.detail()).isEqualTo(faults.getFirst().detail());
                });
    }

    @Test
    @DisplayName("a descriptor that is not well formed is a precondition failure of CLOSURE, as of the resolver")
    void aDescriptorThatIsNotWellFormedIsAPreconditionFailure() {
        PackUnderResolution base = pack(ordered("BASE"), "base", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution dependent =
                pack(ordered("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(ordered("BASE"), "banana"));

        assertThatThrownBy(() -> CompositionRules.closure().apply(List.of(dependent, base)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("well-formed");
    }

    // ---- fixtures ------------------------------------------------------------------------------------------------

    private static ReasonCode code(RefusalCode refusal) {
        return new ReasonCode(refusal.token(), Severity.VIOLATION);
    }

    private static Function<PackUnderResolution, Collection<String>> holding(Map<String, List<String>> presets) {
        return pack -> presets.getOrDefault(pack.descriptor().packId(), List.of());
    }

    private static RefusalPack refusalPack(String id, String name) {
        return new RefusalPack(new PackUlid(id), registeredIdOf(id), name);
    }

    private static List<PackDependency> depends(String packId, String range) {
        return List.of(new PackDependency(packId, range));
    }

    private static ConfiguredPack configured(String id, PackLevel level) {
        return new ConfiguredPack(registeredIdOf(id), level);
    }

    private static PackDescriptor descriptor(
            String id, String name, String version, PackLevel level, List<PackDependency> dependencies) {
        return new PackDescriptor(
                id,
                version,
                Optional.empty(),
                name,
                Optional.of(level),
                Optional.empty(),
                PackPaths.none(),
                dependencies);
    }

    private static PackUnderResolution pack(String id, String name, String version, PackLevel level) {
        return pack(id, name, version, level, List.of());
    }

    private static PackUnderResolution pack(
            String id, String name, String version, PackLevel level, List<PackDependency> dependencies) {
        return new PackUnderResolution(configured(id, level), descriptor(id, name, version, level, dependencies));
    }

    private static PackUnderResolution extending(String id, String name, PackLevel level, String parent) {
        return new PackUnderResolution(
                configured(id, level),
                new PackDescriptor(id, "1.0.0", Optional.of(parent), name, Optional.of(level), Optional.empty()));
    }

    private static PackUnderResolution refining(String id, String name, PackLevel level, String artifact) {
        return new PackUnderResolution(
                configured(id, level), descriptor(id, name, "1.0.0", level, List.of()), List.of(artifact));
    }

    private static PackUnderResolution defining(String id, String name, PackLevel level, String... artifacts) {
        return new PackUnderResolution(
                configured(id, level), descriptor(id, name, "1.0.0", level, List.of()), List.of(), List.of(artifacts));
    }

    private static PackUnderResolution settling(
            String id, String name, String artifact, String member, String... members) {
        return new PackUnderResolution(
                configured(id, PackLevel.ORG),
                new PackDescriptor(
                        id,
                        "1.0.0",
                        Optional.empty(),
                        name,
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        Arrays.stream(members)
                                .map(dependency -> new PackDependency(dependency, ">=1.0"))
                                .toList(),
                        List.of(new PackOverride(artifact, member))));
    }

    /** A sibling that changes {@code artifact} and settles a contest over it, depending on {@code members}. */
    private static PackUnderResolution refiningAndSettling(
            String id, String name, String artifact, String member, String... members) {
        PackUnderResolution settler = settling(id, name, artifact, member, members);
        return new PackUnderResolution(settler.configured(), settler.descriptor(), List.of(artifact));
    }

    /** A pack configured under one id whose descriptor declares another {@code pack_id}. */
    private static PackUnderResolution configuredAs(String configuredId, String packId, String name) {
        return new PackUnderResolution(
                configured(configuredId, PackLevel.ORG),
                new PackDescriptor(
                        packId, "1.0.0", Optional.empty(), name, Optional.of(PackLevel.ORG), Optional.empty()));
    }
}
