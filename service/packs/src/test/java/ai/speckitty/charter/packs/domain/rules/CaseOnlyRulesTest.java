package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The case-only answer of pack-doctor's {@code CLOSURE} (rulings R-y, R-z; FR-016): a preset item or a policy lock that
 * names an artifact only by letter case is refused as registration's composing judge refuses it in its phase 6, per
 * root over the composed closure, every member's mentions against every member's holdings. pack-doctor roots a
 * closure at every pack of the set; the same fault found from two roots is reported once. Each fault is
 * {@code pack_reference_artifact_missing}, naming the mentioning pack then the holder (the first holder in merge order),
 * with the resolver's case-only detail and no violation of its own.
 */
@DisplayName("CaseOnlyRules: a mention that names an artifact of a composed closure only by case is refused (R-z)")
class CaseOnlyRulesTest {

    private static final String BASE_ID = "01BASE00000000000000000000";
    private static final String ORG_ID = "01ZRG000000000000000000000";
    private static final String DEPENDENT_ID = "01DEPENDENT000000000000000";
    private static final String MISSING_ID = "01MSSNG0000000000000000000";

    /** The base, standing alone. */
    private static final PackUnderResolution BASE = pack(BASE_ID, "base", List.of());

    /** An org pack that depends on the base. */
    private static final PackUnderResolution ORG = pack(ORG_ID, "org", List.of(BASE_ID));

    private final Map<String, PackCompositionFacts> facts = new HashMap<>();

    private PackCompositionFacts factsOf(PackUnderResolution pack) {
        return facts.getOrDefault(pack.configured().id().value(), PackCompositionFacts.NONE);
    }

    /** As the readout builds a pack under resolution: what a reference can name is the facts' referable list. */
    private List<PackUnderResolution> read(PackUnderResolution... packs) {
        return Arrays.stream(packs)
                .map(pack -> new PackUnderResolution(
                        pack.configured(),
                        pack.descriptor(),
                        List.of(),
                        factsOf(pack).referable(),
                        Set.of()))
                .toList();
    }

    /** {@code CLOSURE} as pack-doctor runs it. */
    private List<ClosureFault> judge(PackUnderResolution... packs) {
        return PackRulesets.closureFaults(read(packs), this::factsOf);
    }

    private static String detail(
            String mentioning, String mentioningId, String mention, String holder, String holderId, String held) {
        return "pack `" + mentioning + "` (" + mentioningId + ") names `" + mention + "`, but pack `" + holder + "` ("
                + holderId + ") holds `" + held + "`: ids differ only by case and are matched exactly";
    }

    @Test
    @DisplayName("a lock that names the pack's own artifact only by case is pack_reference_artifact_missing")
    void aCaseOnlyLockOnItsOwnArtifactIsRefused() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), locking("directive:directive_001")));

        assertThat(judge(BASE)).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
            assertThat(fault.packs()).extracting(RefusalPack::name).containsExactly("base", "base");
            assertThat(fault.detail())
                    .isEqualTo(detail(
                            "base", BASE_ID, "directive:directive_001", "base", BASE_ID, "directive:DIRECTIVE_001"));
            // Registration's phase 6 answer carries no violation of its own.
            assertThat(fault.violations()).isEmpty();
            assertThat(fault.reason().rule()).isEqualTo(CaseOnlyRules.RULE);
        });
    }

    @Test
    @DisplayName(
            "a lock that names a depends_on pack's artifact only by case names the declaring pack, then the holder")
    void aCaseOnlyLockOnADependencyIsRefused() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), MutabilityPolicySource.empty()));
        facts.put("org", facts(List.of(), List.of(), locking("directive:directive_001")));

        assertThat(judge(BASE, ORG)).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
            assertThat(fault.packs()).extracting(RefusalPack::name).containsExactly("org", "base");
            assertThat(fault.detail())
                    .isEqualTo(detail(
                            "org", ORG_ID, "directive:directive_001", "base", BASE_ID, "directive:DIRECTIVE_001"));
            assertThat(fault.violations()).isEmpty();
        });
    }

    @Test
    @DisplayName("R-z: a dependency's lock that its dependent holds only by case is refused, from the dependent's root")
    void aDependencysLockHeldByItsDependentOnlyByCaseIsRefused() {
        PackUnderResolution dependent = pack(DEPENDENT_ID, "dependent", List.of(BASE_ID));
        facts.put("base", facts(List.of(), List.of(), locking("directive:directive_dep")));
        facts.put("dependent", facts(List.of("directive:DIRECTIVE_DEP"), List.of(), MutabilityPolicySource.empty()));

        assertThat(judge(BASE, dependent)).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
            assertThat(fault.packs()).extracting(RefusalPack::name).containsExactly("base", "dependent");
            assertThat(fault.detail())
                    .isEqualTo(detail(
                            "base",
                            BASE_ID,
                            "directive:directive_dep",
                            "dependent",
                            DEPENDENT_ID,
                            "directive:DIRECTIVE_DEP"));
            assertThat(fault.violations()).isEmpty();
        });
    }

    @Test
    @DisplayName("R-z: a dependency's preset item its dependent holds only by case keeps its own-closure fault (R-k),"
            + " and the case-only answer follows it")
    void aDependencysPresetItemHeldByItsDependentOnlyByCase() {
        PackUnderResolution dependent = pack(DEPENDENT_ID, "dependent", List.of(BASE_ID));
        facts.put("base", facts(List.of(), List.of(team("directive_dep")), MutabilityPolicySource.empty()));
        facts.put("dependent", facts(List.of("directive:DIRECTIVE_DEP"), List.of(), MutabilityPolicySource.empty()));

        assertThat(judge(BASE, dependent))
                .extracting(
                        ClosureFault::code,
                        fault -> fault.packs().getFirst().name(),
                        fault -> fault.violations().size())
                .containsExactly(
                        tuple(RefusalCode.PRESET_ITEM_UNDECLARED, "base", 1),
                        tuple(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING, "base", 0));
    }

    @Test
    @DisplayName("the holder is the first in registration's merge order, dependencies first (A4)")
    void theHolderIsTheFirstInMergeOrder() {
        PackUnderResolution dependent = pack(DEPENDENT_ID, "dependent", List.of(BASE_ID));
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), MutabilityPolicySource.empty()));
        facts.put(
                "dependent", facts(List.of("directive:Directive_001"), List.of(), locking("directive:directive_001")));

        assertThat(judge(BASE, dependent))
                .singleElement()
                .satisfies(fault ->
                        assertThat(fault.packs()).extracting(RefusalPack::name).containsExactly("dependent", "base"));
    }

    @Test
    @DisplayName("a fault two roots' closures find is reported once")
    void aFaultTwoRootsFindIsReportedOnce() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), locking("directive:directive_001")));

        assertThat(judge(BASE, ORG)).hasSize(1);
    }

    @Test
    @DisplayName("several locks of one pack: one fault each, in URN order (A6)")
    void severalLocksComeInUrnOrder() {
        facts.put(
                "base", facts(List.of("directive:A", "directive:B"), List.of(), locking("directive:b", "directive:a")));

        assertThat(judge(BASE))
                .extracting(ClosureFault::detail)
                .containsExactly(
                        detail("base", BASE_ID, "directive:a", "base", BASE_ID, "directive:A"),
                        detail("base", BASE_ID, "directive:b", "base", BASE_ID, "directive:B"));
    }

    @Test
    @DisplayName("a lock held exactly is no fault")
    void aLockHeldExactlyIsAccepted() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), MutabilityPolicySource.empty()));
        facts.put("org", facts(List.of(), List.of(), locking("directive:DIRECTIVE_001")));

        assertThat(judge(BASE, ORG)).isEmpty();
    }

    @Test
    @DisplayName("a lock no pack of any closure holds, in any case or kind, is no fault: it changes no write")
    void aLockNobodyHoldsIsAccepted() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), MutabilityPolicySource.empty()));
        facts.put("org", facts(List.of(), List.of(), locking("directive:directive_404", "tactic:directive_001")));

        assertThat(judge(BASE, ORG)).isEmpty();
    }

    @Test
    @DisplayName("a pack with no policy is no fault")
    void aPackWithNoPolicyIsAccepted() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), MutabilityPolicySource.empty()));
        facts.put("org", facts(List.of(), List.of(), MutabilityPolicySource.empty()));

        assertThat(judge(BASE, ORG)).isEmpty();
    }

    @Test
    @DisplayName("a closure that reaches a pack outside the set is not judged: CLOSURE's composition fault answers")
    void aClosureReachingOutsideTheSetIsNotJudged() {
        PackUnderResolution orphan = pack(DEPENDENT_ID, "orphan", List.of(MISSING_ID));
        facts.put("orphan", facts(List.of("directive:X"), List.of(), locking("directive:x")));

        assertThat(judge(orphan)).extracting(ClosureFault::code).containsExactly(RefusalCode.PACK_DEPENDENCY_UNKNOWN);
    }

    @Test
    @DisplayName("a pack's case-only preset item comes first, then its case-only lock (D15), after every preset-item"
            + " fault")
    void thePresetItemComesBeforeTheLock() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), MutabilityPolicySource.empty()));
        facts.put("org", facts(List.of(), List.of(team("Directive_001")), locking("directive:directive_001")));

        assertThat(judge(BASE, ORG))
                .extracting(
                        ClosureFault::code,
                        ClosureFault::detail,
                        fault -> fault.violations().size())
                .containsExactly(
                        tuple(
                                RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING,
                                detail(
                                        "org",
                                        ORG_ID,
                                        "directive:Directive_001",
                                        "base",
                                        BASE_ID,
                                        "directive:DIRECTIVE_001"),
                                0),
                        tuple(
                                RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING,
                                detail(
                                        "org",
                                        ORG_ID,
                                        "directive:directive_001",
                                        "base",
                                        BASE_ID,
                                        "directive:DIRECTIVE_001"),
                                0));
    }

    private static Preset team(String directive) {
        return new Preset(
                "team", "The team preset.", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.of(directive))));
    }

    private static MutabilityPolicySource locking(String... urns) {
        MutabilityPolicySource.Builder policy = MutabilityPolicySource.builder();
        for (String urn : urns) {
            policy.entry(Urn.parse(urn).orElseThrow(), MutabilityLevel.LOCKED);
        }
        return policy.build();
    }

    private static PackCompositionFacts facts(List<String> held, List<Preset> presets, MutabilityPolicySource policy) {
        return new PackCompositionFacts(List.of(), held, List.of(), List.of(), policy, presets);
    }

    /** A pack configured under its name, whose descriptor declares the ULID {@code id}. */
    private static PackUnderResolution pack(String id, String name, List<String> dependsOn) {
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
                                .map(dep -> new PackDependency(dep, ">=1.0,<2"))
                                .toList(),
                        List.of()),
                List.of(),
                List.of());
    }
}
