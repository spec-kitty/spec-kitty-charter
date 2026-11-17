package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackPaths;
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
            PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
            PackUnderResolution dependent =
                    pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, depends("01BASE", ">=1.0"));

            Rationale rationale = CompositionRules.closure().check(List.of(dependent, base));

            assertThat(rationale.isPassing()).isTrue();
            assertThat(rationale.details()).isEmpty();
        }

        @Test
        @DisplayName("each fault is a failing, unplaced violation that carries its CLOSURE code and the fault's detail")
        void eachFaultIsAFailingViolation() {
            PackUnderResolution dependent =
                    pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, depends("01GONE", ">=1.0"));

            List<Reason> reasons = CompositionRules.closure().apply(List.of(dependent));

            assertThat(reasons).singleElement().satisfies(reason -> {
                assertThat(reason.isFailing(Severity.VIOLATION)).isTrue();
                assertThat(reason.rule()).isEqualTo(CompositionRules.RULE);
                assertThat(reason.code()).isEqualTo(UNKNOWN).isIn(PackCodes.CLOSURE);
                assertThat(reason.location()).isEqualTo(Location.NOWHERE);
                assertThat(reason.detail())
                        .isEqualTo("pack `dependent` (01DEP) depends on pack `01GONE`, which no configured pack"
                                + " declares");
            });
            assertThat(CompositionRules.closure().check(List.of(dependent)).isFailing())
                    .isTrue();
        }

        @Test
        @DisplayName("an unresolved reference is no CLOSURE fault: the reference checks are the closure walk's (#556)")
        void anUnresolvedReferenceIsNoClosureFault() {
            // The only fault of this set is a reference the reader writes to an artifact the writing pack does not
            // hold.
            PackUnderResolution writing = pack("01WRI", "writing", "1.0.0", PackLevel.BUILT_IN);
            PackUnderResolution reader = pack("01REA", "reader", "1.0.0", PackLevel.PROJECT, depends("01WRI", ">=1.0"));

            assertThat(CompositionRules.closure().apply(List.of(writing, reader)))
                    .isEmpty();
        }

        @Test
        @DisplayName("the preset phase reports through its own rule, over the packs in the order given")
        void thePresetPhaseReportsThroughItsOwnRule() {
            PackUnderResolution left = pack("01LEFT", "left", "1.0.0", PackLevel.ORG);
            PackUnderResolution right = pack("01RIGHT", "right", "1.0.0", PackLevel.ORG);
            Map<String, List<String>> presets = Map.of("01LEFT", List.of("team"), "01RIGHT", List.of("team"));

            List<Reason> reasons = CompositionRules.closure()
                    .must(CompositionRules.presets(holding(presets)))
                    .apply(List.of(left, right));

            assertThat(reasons).singleElement().satisfies(reason -> {
                assertThat(reason.rule()).isEqualTo(CompositionRules.PRESETS_RULE);
                assertThat(reason.code()).isEqualTo(SIBLING);
                assertThat(reason.detail())
                        .isEqualTo("packs `left` (01LEFT) and `right` (01RIGHT) at org both hold the preset `team`,"
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
            List<RefusalPack> packs = new ArrayList<>(List.of(new RefusalPack("01A", "01A", "a")));
            CompositionFault fault = new CompositionFault(CYCLE, packs, "why");
            packs.clear();

            assertThat(fault.packs()).containsExactly(new RefusalPack("01A", "01A", "a"));
            assertThatThrownBy(() -> fault.packs().add(new RefusalPack("01B", "01B", "b")))
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
            PackUnderResolution self = pack("01SELF", "self", "1.0.0", PackLevel.ORG, depends("01SELF", ">=1.0"));

            assertThat(CompositionRules.faults(List.of(self))).singleElement().satisfies(fault -> {
                assertThat(fault.code()).isEqualTo(CYCLE);
                assertThat(fault.packs()).containsExactly(refusalPack("01SELF", "self"));
                assertThat(fault.detail()).isEqualTo("pack `self` (01SELF) depends on itself");
            });
            PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.ORG);
            PackUnderResolution dependent = pack("01DEP", "dep", "1.0.0", PackLevel.ORG, depends("01BASE", ">=1.0"));
            assertThat(CompositionRules.faults(List.of(dependent, base))).isEmpty();
        }

        @Test
        @DisplayName("a pack that names itself as parent is a cycle")
        void aPackThatNamesItselfAsParent() {
            PackUnderResolution loop = extending("01LOOP", "loop", PackLevel.ORG, "01LOOP");

            assertThat(CompositionRules.faults(List.of(loop))).singleElement().satisfies(fault -> {
                assertThat(fault.code()).isEqualTo(CYCLE);
                assertThat(fault.detail()).isEqualTo("pack `loop` (01LOOP) names itself as its parent_pack");
            });
        }

        @Test
        @DisplayName("two packs that name each other as parent, or a parent cycle through a dependency, are a cycle")
        void parentCycles() {
            PackUnderResolution first = extending("01FIRST", "first", PackLevel.ORG, "01SECOND");
            PackUnderResolution second = extending("01SECOND", "second", PackLevel.ORG, "01FIRST");
            PackUnderResolution parent =
                    pack("01PARENT", "parent", "1.0.0", PackLevel.ORG, depends("01CHILD", ">=1.0"));
            PackUnderResolution child = extending("01CHILD", "child", PackLevel.ORG, "01PARENT");

            assertThat(CompositionRules.faults(List.of(first, second)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(CYCLE);
                        assertThat(fault.packs())
                                .containsExactly(refusalPack("01FIRST", "first"), refusalPack("01SECOND", "second"));
                        assertThat(fault.detail())
                                .isEqualTo("pack `first` (01FIRST) is on a dependency cycle: `first` -> `second` ->"
                                        + " `first`");
                    });
            assertThat(CompositionRules.faults(List.of(parent, child)))
                    .extracting(CompositionFault::code)
                    .containsExactly(CYCLE);
        }

        @Test
        @DisplayName("a cycle names a pack on it, not one only downstream of it")
        void aCycleNamesAPackOnIt() {
            PackUnderResolution downstream = pack("01AAA", "a", "1.0.0", PackLevel.ORG, depends("01BBB", ">=1.0"));
            PackUnderResolution b = pack("01BBB", "b", "1.0.0", PackLevel.ORG, depends("01CCC", ">=1.0"));
            PackUnderResolution c = pack("01CCC", "c", "1.0.0", PackLevel.ORG, depends("01BBB", ">=1.0"));

            assertThat(CompositionRules.faults(List.of(downstream, b, c)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(CYCLE);
                        assertThat(fault.packs()).containsExactly(refusalPack("01BBB", "b"), refusalPack("01CCC", "c"));
                        assertThat(fault.detail())
                                .isEqualTo("pack `b` (01BBB) is on a dependency cycle: `b` -> `c` -> `b`");
                    });
        }

        @Test
        @DisplayName("a pack on a cycle the first walk passed by is named too, and the first cycle is unchanged")
        void overlappingCyclesNameEveryPackOnThem() {
            // A depends on B and C; B and C each depend on A. The walk from A follows B only, so C is named by the
            // shortest cycle through it; D only depends on A, lies on no cycle and is named by none.
            PackUnderResolution a = new PackUnderResolution(
                    configured("01AAA", PackLevel.ORG),
                    descriptor(
                            "01AAA",
                            "a",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(new PackDependency("01BBB", ">=1.0"), new PackDependency("01CCC", ">=1.0"))));
            PackUnderResolution b = pack("01BBB", "b", "1.0.0", PackLevel.ORG, depends("01AAA", ">=1.0"));
            PackUnderResolution c = pack("01CCC", "c", "1.0.0", PackLevel.ORG, depends("01AAA", ">=1.0"));
            PackUnderResolution d = pack("01DDD", "d", "1.0.0", PackLevel.ORG, depends("01AAA", ">=1.0"));

            List<CompositionFault> faults = CompositionRules.faults(List.of(d, c, b, a));

            assertThat(faults).allSatisfy(fault -> assertThat(fault.code()).isEqualTo(CYCLE));
            assertThat(faults)
                    .extracting(CompositionFault::detail)
                    .containsExactly(
                            "pack `a` (01AAA) is on a dependency cycle: `a` -> `b` -> `a`",
                            "pack `c` (01CCC) is on a dependency cycle: `c` -> `a` -> `c`");
            assertThat(faults.get(1).packs()).containsExactly(refusalPack("01CCC", "c"), refusalPack("01AAA", "a"));
        }

        @Test
        @DisplayName("the shortest cycle through a passed-by pack is named, over its longer ones")
        void theShortestCycleThroughAPassedByPackIsNamed() {
            // The walk from A goes A -> B -> C -> A. E is on A -> E -> A and on E -> B -> C -> A -> E: the first is
            // named.
            PackUnderResolution a = new PackUnderResolution(
                    configured("01AAA", PackLevel.ORG),
                    descriptor(
                            "01AAA",
                            "a",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(new PackDependency("01BBB", ">=1.0"), new PackDependency("01EEE", ">=1.0"))));
            PackUnderResolution b = pack("01BBB", "b", "1.0.0", PackLevel.ORG, depends("01CCC", ">=1.0"));
            PackUnderResolution c = pack("01CCC", "c", "1.0.0", PackLevel.ORG, depends("01AAA", ">=1.0"));
            PackUnderResolution e = new PackUnderResolution(
                    configured("01EEE", PackLevel.ORG),
                    descriptor(
                            "01EEE",
                            "e",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(new PackDependency("01BBB", ">=1.0"), new PackDependency("01AAA", ">=1.0"))));

            assertThat(CompositionRules.faults(List.of(a, b, c, e)))
                    .extracting(CompositionFault::detail)
                    .containsExactly(
                            "pack `a` (01AAA) is on a dependency cycle: `a` -> `b` -> `c` -> `a`",
                            "pack `e` (01EEE) is on a dependency cycle: `e` -> `a` -> `e`");
        }

        @Test
        @DisplayName("a cycle is found past a dependency the order already placed")
        void aCycleIsFoundPastAPlacedDependency() {
            PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.ORG);
            PackUnderResolution a = new PackUnderResolution(
                    configured("01AAA", PackLevel.ORG),
                    descriptor(
                            "01AAA",
                            "a",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(new PackDependency("01BASE", ">=1.0"), new PackDependency("01BBB", ">=1.0"))));
            PackUnderResolution b = pack("01BBB", "b", "1.0.0", PackLevel.ORG, depends("01AAA", ">=1.0"));

            assertThat(CompositionRules.faults(List.of(a, b, base)))
                    .extracting(CompositionFault::detail)
                    .containsExactly("pack `a` (01AAA) is on a dependency cycle: `a` -> `b` -> `a`");
        }

        @Test
        @DisplayName("a repeated pack_id is placed once, so a cycle behind it is still found")
        void aRepeatedPackIdIsPlacedOnce() {
            // 01YYY depends on the repeated 01XXX and on 01ZZZ, which depends on 01YYY: the twin must not count as a
            // second placement of 01XXX, or 01YYY would be placed and the cycle missed.
            PackUnderResolution x = pack("01XXX", "x", "1.0.0", PackLevel.ORG);
            PackUnderResolution twin = configuredAs("twin", "01XXX", "x-twin");
            PackUnderResolution y = new PackUnderResolution(
                    configured("01YYY", PackLevel.ORG),
                    descriptor(
                            "01YYY",
                            "y",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(new PackDependency("01XXX", ">=1.0"), new PackDependency("01ZZZ", ">=1.0"))));
            PackUnderResolution z = pack("01ZZZ", "z", "1.0.0", PackLevel.ORG, depends("01YYY", ">=1.0"));

            assertThat(CompositionRules.faults(List.of(x, twin, y, z)))
                    .extracting(CompositionFault::code)
                    .containsExactly(DUPLICATE, CYCLE);
        }

        @Test
        @DisplayName("two disjoint cycles are two faults, in the order of their first packs")
        void twoDisjointCyclesAreTwoFaults() {
            PackUnderResolution a = pack("01AAA", "a", "1.0.0", PackLevel.ORG, depends("01BBB", ">=1.0"));
            PackUnderResolution b = pack("01BBB", "b", "1.0.0", PackLevel.ORG, depends("01AAA", ">=1.0"));
            PackUnderResolution x = pack("01XXX", "x", "1.0.0", PackLevel.ORG, depends("01YYY", ">=1.0"));
            PackUnderResolution y = pack("01YYY", "y", "1.0.0", PackLevel.ORG, depends("01XXX", ">=1.0"));

            assertThat(CompositionRules.faults(List.of(y, x, b, a)))
                    .extracting(CompositionFault::detail)
                    .containsExactly(
                            "pack `a` (01AAA) is on a dependency cycle: `a` -> `b` -> `a`",
                            "pack `x` (01XXX) is on a dependency cycle: `x` -> `y` -> `x`");
        }

        @Test
        @DisplayName("a pack downstream of two cycles adds no third, and a cycle is reported once")
        void aCycleIsReportedOnce() {
            PackUnderResolution a = pack("01AAA", "a", "1.0.0", PackLevel.ORG, depends("01BBB", ">=1.0"));
            PackUnderResolution b = pack("01BBB", "b", "1.0.0", PackLevel.ORG, depends("01AAA", ">=1.0"));
            PackUnderResolution downstream = new PackUnderResolution(
                    configured("01DDD", PackLevel.ORG),
                    descriptor(
                            "01DDD",
                            "d",
                            "1.0.0",
                            PackLevel.ORG,
                            List.of(new PackDependency("01AAA", ">=1.0"), new PackDependency("01XXX", ">=1.0"))));
            PackUnderResolution x = pack("01XXX", "x", "1.0.0", PackLevel.ORG, depends("01YYY", ">=1.0"));
            PackUnderResolution y = pack("01YYY", "y", "1.0.0", PackLevel.ORG, depends("01XXX", ">=1.0"));

            assertThat(CompositionRules.faults(List.of(downstream, a, b, x, y)))
                    .extracting(
                            CompositionFault::code,
                            fault -> fault.packs().getFirst().packId())
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(CYCLE, "01AAA"),
                            org.assertj.core.groups.Tuple.tuple(CYCLE, "01XXX"));
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
                    pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, depends("01GONE", ">=1.0"));
            PackUnderResolution gone = pack("01GONE", "gone", "1.0.0", PackLevel.ORG);

            assertThat(CompositionRules.faults(List.of(dependent)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(UNKNOWN);
                        assertThat(fault.packs()).containsExactly(refusalPack("01DEP", "dependent"));
                    });
            assertThat(CompositionRules.faults(List.of(dependent, gone))).isEmpty();
        }

        @Test
        @DisplayName("a parent_pack no configured pack declares is unresolved; a declared one is not")
        void anUnresolvedParent() {
            PackUnderResolution orphan = extending("01ORPH", "orphan", PackLevel.ORG, "01MISSING");
            PackUnderResolution missing = pack("01MISSING", "missing", "1.0.0", PackLevel.ORG);

            assertThat(CompositionRules.faults(List.of(orphan))).singleElement().satisfies(fault -> {
                assertThat(fault.code()).isEqualTo(PARENT_UNRESOLVED);
                assertThat(fault.detail())
                        .isEqualTo("pack `orphan` (01ORPH) names parent_pack `01MISSING`, which no configured"
                                + " pack declares");
            });
            assertThat(CompositionRules.faults(List.of(orphan, missing))).isEmpty();
        }

        @Test
        @DisplayName(
                "every unknown dependency of a pack is reported, and a parent also listed as a dependency is judged")
        void everyUnknownDependencyIsReported() {
            PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.ORG);
            PackUnderResolution dependent = new PackUnderResolution(
                    configured("01DEP", PackLevel.ORG),
                    new PackDescriptor(
                            "01DEP",
                            "1.0.0",
                            Optional.of("01BASE"),
                            "dependent",
                            Optional.of(PackLevel.ORG),
                            Optional.empty(),
                            PackPaths.none(),
                            List.of(
                                    new PackDependency("01ONE", ">=1.0"),
                                    new PackDependency("01BASE", ">=1.0"),
                                    new PackDependency("01TWO", ">=1.0"))));

            assertThat(CompositionRules.faults(List.of(dependent, base)))
                    .extracting(CompositionFault::detail)
                    .containsExactly(
                            "pack `dependent` (01DEP) depends on pack `01ONE`, which no configured pack declares",
                            "pack `dependent` (01DEP) depends on pack `01TWO`, which no configured pack declares");
        }
    }

    // ---- pack_dependency_out_of_range --------------------------------------------------------------------------

    @Nested
    @DisplayName("pack_dependency_out_of_range")
    class OutOfRange {

        @Test
        @DisplayName("a held version outside the declared range is out of range; one inside it is not")
        void aHeldVersionOutsideTheRange() {
            PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
            PackUnderResolution dependent =
                    pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, depends("01BASE", ">=2.0"));
            PackUnderResolution admitted =
                    pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, depends("01BASE", ">=1.0"));

            assertThat(CompositionRules.faults(List.of(dependent, base)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(OUT_OF_RANGE);
                        assertThat(fault.packs())
                                .containsExactly(refusalPack("01DEP", "dependent"), refusalPack("01BASE", "base"));
                        assertThat(fault.detail())
                                .isEqualTo(
                                        "pack `dependent` (01DEP) requires pack `base` (01BASE) in range `>=2.0`, but"
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
            PackUnderResolution base = pack("01BASE", "base", held, PackLevel.BUILT_IN);
            PackUnderResolution first = pack("01ONE", "one", "1.0.0", PackLevel.ORG, depends("01BASE", firstRange));
            PackUnderResolution second = pack("01TWO", "two", "1.0.0", PackLevel.ORG, depends("01BASE", secondRange));

            List<CompositionFault> faults = CompositionRules.faults(List.of(second, first, base));

            if (resolves) {
                assertThat(faults).isEmpty();
                return;
            }
            assertThat(faults.getFirst().code()).isEqualTo(OUT_OF_RANGE);
            assertThat(faults.getFirst().packs())
                    .containsExactly(refusalPack("01TWO", "two"), refusalPack("01ONE", "one"));
            assertThat(faults.getFirst().detail())
                    .isEqualTo("packs `one` (01ONE) and `two` (01TWO) require pack `base` (01BASE) in ranges `"
                            + firstRange + "` and `" + secondRange + "`, which no version satisfies together");
            assertThat(faults)
                    .as("the held version is then checked against each range: the second's is out of range too")
                    .extracting(CompositionFault::code)
                    .containsExactly(OUT_OF_RANGE, OUT_OF_RANGE);
        }

        @Test
        @DisplayName("every disjoint pair is reported, in the order the packs ask")
        void everyDisjointPairIsReported() {
            PackUnderResolution base = pack("01BASE", "base", "5.0.0", PackLevel.BUILT_IN);
            PackUnderResolution one = pack("01ONE", "one", "1.0.0", PackLevel.ORG, depends("01BASE", "<2"));
            PackUnderResolution two = pack("01TWO", "two", "1.0.0", PackLevel.ORG, depends("01BASE", ">=2,<3"));
            PackUnderResolution three = pack("01TRE", "three", "1.0.0", PackLevel.ORG, depends("01BASE", ">=3"));

            List<CompositionFault> ranges = CompositionRules.faults(List.of(three, two, one, base)).stream()
                    .filter(fault -> fault.detail().contains("no version satisfies together"))
                    .toList();

            assertThat(ranges)
                    .extracting(fault -> fault.packs().getFirst().packId() + "/"
                            + fault.packs().get(1).packId())
                    .containsExactly("01TRE/01ONE", "01TWO/01ONE", "01TWO/01TRE");
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
        PackUnderResolution targetPack = pack("01TRGT", "target", "1.0.0", target);
        PackUnderResolution ownPack = parentEdge
                ? extending("01OWN0", "own", own, "01TRGT")
                : pack("01OWN0", "own", "1.0.0", own, depends("01TRGT", ">=1.0"));

        List<CompositionFault> faults = CompositionRules.faults(List.of(ownPack, targetPack));

        if (allowed) {
            assertThat(faults).isEmpty();
            return;
        }
        assertThat(faults).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(LEVEL);
            assertThat(fault.packs()).containsExactly(refusalPack("01OWN0", "own"), refusalPack("01TRGT", "target"));
            assertThat(fault.detail())
                    .isEqualTo(
                            "pack `own` (01OWN0) at " + own.token()
                                    + (parentEdge
                                            ? " names pack `target` (01TRGT) at " + target.token()
                                                    + " as its parent_pack"
                                            : " depends on pack `target` (01TRGT) at " + target.token())
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
            PackUnderResolution first = pack("01AAA", "shared-name", "1.0.0", PackLevel.ORG);
            PackUnderResolution second = pack("01BBB", "shared-name", "1.0.0", PackLevel.ORG);
            PackUnderResolution other = pack("01BBB", "other-name", "1.0.0", PackLevel.ORG);

            assertThat(CompositionRules.faults(List.of(second, first)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(NAME);
                        assertThat(fault.packs())
                                .containsExactly(
                                        refusalPack("01BBB", "shared-name"), refusalPack("01AAA", "shared-name"));
                        assertThat(fault.detail())
                                .isEqualTo("packs `shared-name` (01AAA) and `shared-name` (01BBB) both declare the name"
                                        + " `shared-name`");
                    });
            assertThat(CompositionRules.faults(List.of(first, other))).isEmpty();
        }

        @Test
        @DisplayName("two packs declaring one pack_id are a duplicate; every further holder is one more")
        void twoPacksDeclaringOnePackId() {
            PackUnderResolution gamma = pack("01GAMMA", "gamma", "1.0.0", PackLevel.ORG);
            PackUnderResolution twin = configuredAs("twin", "01GAMMA", "gamma-twin");
            PackUnderResolution triplet = configuredAs("zz-triplet", "01GAMMA", "gamma-triplet");

            assertThat(CompositionRules.faults(List.of(triplet, twin, gamma)))
                    .allSatisfy(fault -> assertThat(fault.code()).isEqualTo(DUPLICATE))
                    .extracting(CompositionFault::packs)
                    .containsExactly(
                            List.of(new RefusalPack("01GAMMA", "twin", "gamma-twin"), refusalPack("01GAMMA", "gamma")),
                            List.of(
                                    new RefusalPack("01GAMMA", "zz-triplet", "gamma-triplet"),
                                    refusalPack("01GAMMA", "gamma")));
            assertThat(CompositionRules.faults(List.of(gamma))).isEmpty();
        }

        @Test
        @DisplayName("a twin that contests what its first holder defines adds no sibling contest")
        void aTwinAddsNoSiblingContest() {
            PackUnderResolution gamma = defining("01GAMMA", "gamma", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution twin = new PackUnderResolution(
                    configured("twin", PackLevel.ORG),
                    descriptor("01GAMMA", "gamma-twin", "1.0.0", PackLevel.ORG, List.of()),
                    List.of(),
                    List.of(DIRECTIVE));

            assertThat(CompositionRules.faults(List.of(twin, gamma)))
                    .extracting(CompositionFault::code)
                    .containsExactly(DUPLICATE);
        }

        @Test
        @DisplayName("a dependency on a duplicated pack_id is judged against its first holder")
        void aDependencyOnADuplicatedIdIsJudgedAgainstItsFirstHolder() {
            PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
            PackUnderResolution twin = new PackUnderResolution(
                    configured("base-twin", PackLevel.BUILT_IN),
                    descriptor("01BASE", "base-twin", "3.0.0", PackLevel.BUILT_IN, List.of()));
            PackUnderResolution dependent =
                    pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, depends("01BASE", ">=2.0"));

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
            PackUnderResolution left = refining("01LEFT", "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining("01RIGHT", "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution project = refining("01PROJ", "project", PackLevel.PROJECT, DIRECTIVE);

            assertThat(CompositionRules.faults(List.of(right, left)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.packs())
                                .containsExactly(refusalPack("01RIGHT", "right"), refusalPack("01LEFT", "left"));
                        assertThat(fault.detail())
                                .isEqualTo("packs `left` (01LEFT) and `right` (01RIGHT) at org both change the artifact"
                                        + " `directive:DIRECTIVE_009`, and no pack of the compose declares an overrides"
                                        + " edge settling it");
                    });
            assertThat(CompositionRules.faults(List.of(left, project))).isEmpty();
        }

        @Test
        @DisplayName("two siblings defining one artifact conflict; a mission type's step adds no contest of its own")
        void twoSiblingsDefiningOneArtifact() {
            PackUnderResolution left =
                    defining("01LEFT", "left", PackLevel.ORG, "mission_type:m", "action:m/implement");
            PackUnderResolution right =
                    defining("01RIGHT", "right", PackLevel.ORG, "mission_type:m", "action:m/implement");
            PackUnderResolution stepOnlyLeft = defining("01LEFT", "left", PackLevel.ORG, "action:m/implement");
            PackUnderResolution stepOnlyRight = defining("01RIGHT", "right", PackLevel.ORG, "action:m/implement");

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
            PackUnderResolution bare = refining("01LEFT", "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution qualified = refining("01RIGHT", "right", PackLevel.ORG, "base::" + DIRECTIVE);

            assertThat(CompositionRules.faults(List.of(bare, qualified)))
                    .extracting(CompositionFault::code)
                    .containsExactly(SIBLING);
        }

        @Test
        @DisplayName("edges naming different winners leave the contest unsettled; agreeing edges settle it")
        void edgesNamingDifferentWinners() {
            PackUnderResolution left = refining("01LEFT", "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining("01RIGHT", "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution first = settling("01DONE", "done", DIRECTIVE, "01LEFT");
            PackUnderResolution second = settling("01DTWO", "dtwo", "org::" + DIRECTIVE, "01RIGHT");
            PackUnderResolution agreeing = settling("01DTWO", "dtwo", "org::" + DIRECTIVE, "01LEFT");

            assertThat(CompositionRules.faults(List.of(second, first, right, left)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.detail())
                                .endsWith("and overrides edges settling it name different winners, 01LEFT, 01RIGHT");
                    });
            assertThat(CompositionRules.faults(List.of(agreeing, first, right, left)))
                    .isEmpty();
        }

        // A14 (FR-035, DM-01M6RA92BS6GGCYTRCFQDYYY5S, rule C15): a settlement names exactly one winner. One descriptor
        // cannot name two (it is refused as it is read), and the overrides edges of the compose naming two contestants
        // answer the conflict; exactly one winner settles the contest.
        @Test
        @DisplayName("A14: a settlement naming two winners conflicts, and exactly one winner settles the contest")
        void aSettlementNamingTwoWinners() {
            assertThatThrownBy(() -> new PackDescriptor(
                            "01DSTR",
                            "1.0.0",
                            Optional.empty(),
                            "distribution",
                            Optional.of(PackLevel.ORG),
                            Optional.empty(),
                            PackPaths.none(),
                            List.of(),
                            List.of(new PackOverride(DIRECTIVE, "01LEFT"), new PackOverride(DIRECTIVE, "01RIGHT"))))
                    .as("one descriptor naming two winners of one artifact")
                    .isInstanceOf(IllegalArgumentException.class);
            PackUnderResolution left = refining("01LEFT", "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining("01RIGHT", "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution namingLeft = settling("01DSTR", "distribution", DIRECTIVE, "01LEFT");
            PackUnderResolution namingRight = settling("01DTWO", "dtwo", DIRECTIVE, "01RIGHT");

            assertThat(CompositionRules.faults(List.of(namingLeft, namingRight, left, right)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.detail()).contains("different winners", "01LEFT", "01RIGHT");
                    });
            assertThat(CompositionRules.faults(List.of(namingLeft, left, right)))
                    .isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"01GHOST", "01DSTR"})
        @DisplayName("an overrides edge naming a member that is not a contestant settles nothing")
        void anEdgeNamingANonContestantSettlesNothing(String member) {
            PackUnderResolution left = refining("01LEFT", "left", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution right = refining("01RIGHT", "right", PackLevel.ORG, DIRECTIVE);
            PackUnderResolution distribution = settling("01DSTR", "distribution", DIRECTIVE, member);

            assertThat(CompositionRules.faults(List.of(distribution, left, right)))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(SIBLING);
                        assertThat(fault.detail())
                                .endsWith("and an overrides edge settling it names " + member
                                        + ", which is not a contestant");
                    });
        }

        @Test
        @DisplayName("every contest is reported, the changed artifacts before the defined ones")
        void everyContestIsReported() {
            PackUnderResolution left = new PackUnderResolution(
                    configured("01LEFT", PackLevel.ORG),
                    descriptor("01LEFT", "left", "1.0.0", PackLevel.ORG, List.of()),
                    List.of(DIRECTIVE, "tactic:t"),
                    List.of("styleguide:s"));
            PackUnderResolution right = new PackUnderResolution(
                    configured("01RIGHT", PackLevel.ORG),
                    descriptor("01RIGHT", "right", "1.0.0", PackLevel.ORG, List.of()),
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
                PackUnderResolution left = holdingPresets(pack("01LEFT", "left", "1.0.0", PackLevel.ORG), "team");
                PackUnderResolution right = holdingPresets(pack("01RIGHT", "right", "1.0.0", PackLevel.ORG), "team");
                PackUnderResolution crew = holdingPresets(pack("01CREW", "crew", "1.0.0", PackLevel.ORG), "crew");

                assertThat(check(left, right)).singleElement().satisfies(fault -> {
                    assertThat(fault.code()).isEqualTo(SIBLING);
                    assertThat(fault.packs())
                            .containsExactly(refusalPack("01RIGHT", "right"), refusalPack("01LEFT", "left"));
                });
                assertThat(check(left, crew)).isEmpty();
            }

            @Test
            @DisplayName("a child and its parent or grandparent are related; two children of one parent are siblings")
            void relatedAndSiblingPacks() {
                PackUnderResolution top = holdingPresets(pack("01TOP", "top", "1.0.0", PackLevel.ORG), "team");
                PackUnderResolution middle = extending("01MID", "middle", PackLevel.ORG, "01TOP");
                PackUnderResolution leaf = holdingPresets(extending("01LEAF", "leaf", PackLevel.ORG, "01MID"), "team");
                PackUnderResolution other =
                        holdingPresets(extending("01OTHER", "other", PackLevel.ORG, "01TOP"), "crew");
                PackUnderResolution one = holdingPresets(extending("01ONE", "one", PackLevel.ORG, "01MID"), "crew");

                assertThat(check(top, middle, leaf)).isEmpty();
                assertThat(check(top, other, middle, one))
                        .extracting(CompositionFault::code)
                        .containsExactly(SIBLING);
            }

            @Test
            @DisplayName("a child and its parent may hold one preset id: the child's copy takes the parent's place")
            void aChildAndItsParent() {
                PackUnderResolution parent = holdingPresets(pack("01PARENT", "parent", "1.0.0", PackLevel.ORG), "team");
                PackUnderResolution child =
                        holdingPresets(extending("01CHILD", "child", PackLevel.ORG, "01PARENT"), "team");

                assertThat(check(child, parent)).isEmpty();
                assertThat(check(parent, child)).isEmpty();
            }

            @Test
            @DisplayName("the packs are judged in merge order, whatever order they are given in")
            void thePacksAreJudgedInMergeOrder() {
                // 01AAA depends on 01ZZZ, so 01ZZZ merges first although it sorts last.
                PackUnderResolution first =
                        holdingPresets(pack("01AAA", "a", "1.0.0", PackLevel.ORG, depends("01ZZZ", ">=1.0")), "team");
                PackUnderResolution last = holdingPresets(pack("01ZZZ", "z", "1.0.0", PackLevel.ORG), "team");

                List<CompositionFault> given = check(first, last);

                assertThat(given).singleElement().satisfies(fault -> {
                    assertThat(fault.packs()).containsExactly(refusalPack("01AAA", "a"), refusalPack("01ZZZ", "z"));
                    assertThat(fault.detail()).startsWith("packs `z` (01ZZZ) and `a` (01AAA) at org");
                });
                assertThat(check(last, first)).isEqualTo(given);
                assertThat(CompositionRules.presets(holding(presetIds)).apply(List.of(first, last)))
                        .extracting(Reason::detail)
                        .containsExactly(given.getFirst().detail());
            }

            @Test
            @DisplayName("packs of different levels may hold one preset id: the layer merge applies")
            void packsOfDifferentLevels() {
                PackUnderResolution org = holdingPresets(pack("01ORG", "org", "1.0.0", PackLevel.ORG), "team");
                PackUnderResolution project =
                        holdingPresets(pack("01PROJ", "project", "1.0.0", PackLevel.PROJECT), "team");

                assertThat(check(org, project)).isEmpty();
            }

            @Test
            @DisplayName("every contesting pair is reported, and a parent cycle ends the walk")
            void everyContestingPairIsReported() {
                PackUnderResolution a = holdingPresets(extending("01AAA", "a", PackLevel.ORG, "01BBB"), "team");
                PackUnderResolution b = holdingPresets(extending("01BBB", "b", PackLevel.ORG, "01AAA"), "team");
                PackUnderResolution c = holdingPresets(pack("01CCC", "c", "1.0.0", PackLevel.ORG), "team", "team");

                // Merge order: c, placed; then a and b, on a parent cycle, in the stable order.
                assertThat(check(a, b, c))
                        .extracting(fault -> fault.packs().getFirst().packId() + "/"
                                + fault.packs().get(1).packId())
                        .containsExactly("01AAA/01CCC", "01BBB/01CCC");
            }
        }
    }

    // ---- several faults ------------------------------------------------------------------------------------------

    static Stream<Arguments> multiFaultSets() {
        return Stream.of(
                Arguments.of(
                        "a cycle and an unknown dependency",
                        List.of(
                                pack("01AAA", "a", "1.0.0", PackLevel.ORG, depends("01BBB", ">=1.0")),
                                pack("01BBB", "b", "1.0.0", PackLevel.ORG, depends("01AAA", ">=1.0")),
                                pack("01CCC", "c", "1.0.0", PackLevel.ORG, depends("01GONE", ">=1.0"))),
                        List.of(UNKNOWN, CYCLE)),
                Arguments.of(
                        "a name conflict and a sibling contest",
                        List.of(
                                defining("01LEFT", "left", PackLevel.ORG, DIRECTIVE),
                                defining("01RIGHT", "right", PackLevel.ORG, DIRECTIVE),
                                pack("01AAA", "shared-name", "1.0.0", PackLevel.ORG),
                                pack("01BBB", "shared-name", "1.0.0", PackLevel.ORG)),
                        List.of(NAME, SIBLING)),
                Arguments.of(
                        "a duplicate id and an out-of-range dependency",
                        List.of(
                                pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN),
                                new PackUnderResolution(
                                        configured("base-twin", PackLevel.BUILT_IN),
                                        descriptor("01BASE", "base-twin", "1.0.0", PackLevel.BUILT_IN, List.of())),
                                pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, depends("01BASE", ">=2.0"))),
                        List.of(DUPLICATE, OUT_OF_RANGE)),
                Arguments.of(
                        "a level violation and an unresolved parent",
                        List.of(
                                pack("01OWN0", "own", "1.0.0", PackLevel.BUILT_IN, depends("01TRGT", ">=1.0")),
                                pack("01TRGT", "target", "1.0.0", PackLevel.PROJECT),
                                extending("01ORPH", "orphan", PackLevel.ORG, "01MISSING")),
                        List.of(LEVEL, PARENT_UNRESOLVED)),
                everyKindAtOnce());
    }

    /** One fault of every kind in one set: the faults come in the resolver's order. */
    private static Arguments everyKindAtOnce() {
        return Arguments.of(
                "every kind at once, in the resolver's order",
                List.of(
                        pack("01SELF", "self", "1.0.0", PackLevel.ORG, depends("01SELF", ">=1.0")),
                        pack("01AAA", "a", "1.0.0", PackLevel.ORG, depends("01BBB", ">=1.0")),
                        pack("01BBB", "b", "1.0.0", PackLevel.ORG, depends("01AAA", ">=1.0")),
                        pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN),
                        new PackUnderResolution(
                                configured("base-twin", PackLevel.BUILT_IN),
                                descriptor("01BASE", "base-twin", "1.0.0", PackLevel.BUILT_IN, List.of())),
                        pack("01NAME", "a", "1.0.0", PackLevel.ORG),
                        pack("01ONE", "one", "1.0.0", PackLevel.ORG, depends("01BASE", "<1")),
                        pack("01TWO", "two", "1.0.0", PackLevel.ORG, depends("01BASE", ">=1")),
                        pack("01UNK", "unknown", "1.0.0", PackLevel.ORG, depends("01GONE", ">=1.0")),
                        pack("01PRJ", "project", "1.0.0", PackLevel.PROJECT),
                        pack("01LVL", "level", "1.0.0", PackLevel.ORG, depends("01PRJ", ">=1.0")),
                        extending("01ORPH", "orphan", PackLevel.ORG, "01MISSING"),
                        refining("01LEFT", "left", PackLevel.ORG, DIRECTIVE),
                        refining("01RIGHT", "right", PackLevel.ORG, DIRECTIVE)),
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
        PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution dependent = pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, depends("01BASE", "banana"));

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
        return new RefusalPack(id, id, name);
    }

    private static List<PackDependency> depends(String packId, String range) {
        return List.of(new PackDependency(packId, range));
    }

    private static ConfiguredPack configured(String id, PackLevel level) {
        return new ConfiguredPack(new PackIdentifier(id), level);
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

    private static PackUnderResolution settling(String id, String name, String artifact, String member) {
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
                        List.of(),
                        List.of(new PackOverride(artifact, member))));
    }

    /** A pack configured under one id whose descriptor declares another {@code pack_id}. */
    private static PackUnderResolution configuredAs(String configuredId, String packId, String name) {
        return new PackUnderResolution(
                configured(configuredId, PackLevel.ORG),
                new PackDescriptor(
                        packId, "1.0.0", Optional.empty(), name, Optional.of(PackLevel.ORG), Optional.empty()));
    }
}
