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
import ai.speckitty.charter.kernel.validation.Origin;
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
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The case-only policy lock (ruling R-y, FR-016, plan D15): a lock of a pack's mutability policy file that names an
 * artifact of the pack's own closure ({@code depends_on} and {@code parent_pack}, never a dependent) only by letter case
 * gets registration's phase 6 answer, {@code pack_reference_artifact_missing}, naming the declaring pack then the
 * holder, with the resolver's case-only detail and no violation of its own. A lock held exactly, or held by no pack of
 * the own closure in any case, is no fault.
 */
@DisplayName("PolicyLockRules: a policy lock that names an own-closure artifact only by case is refused (R-y)")
class PolicyLockRulesTest {

    private static final String BASE_ID = "01BASE00000000000000000000";
    private static final String ORG_ID = "01ZRG000000000000000000000";
    private static final String DEPENDENT_ID = "01DEPENDENT000000000000000";

    /** The base, standing alone. */
    private static final PackUnderResolution BASE = pack(BASE_ID, "base", List.of());

    /** An org pack that depends on the base. */
    private static final PackUnderResolution ORG = pack(ORG_ID, "org", List.of(BASE_ID));

    private final Map<String, PackCompositionFacts> facts = new HashMap<>();

    private Function<PackUnderResolution, PackCompositionFacts> factsOf() {
        return pack -> facts.getOrDefault(pack.configured().id().value(), PackCompositionFacts.NONE);
    }

    /** As the readout builds a pack under resolution: what a reference can name is the facts' referable list. */
    private List<PackUnderResolution> read(PackUnderResolution... packs) {
        Function<PackUnderResolution, PackCompositionFacts> factsOf = factsOf();
        return Arrays.stream(packs)
                .map(pack -> new PackUnderResolution(
                        pack.configured(),
                        pack.descriptor(),
                        List.of(),
                        factsOf.apply(pack).referable(),
                        Set.of()))
                .toList();
    }

    private List<ClosureFault> judge(PackUnderResolution... packs) {
        return PolicyLockRules.phase(factsOf()).apply(read(packs));
    }

    @Test
    @DisplayName("a lock that names the pack's own artifact only by case is pack_reference_artifact_missing")
    void aCaseOnlyLockOnItsOwnArtifactIsRefused() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), locking("directive:directive_001")));

        assertThat(judge(BASE)).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
            assertThat(fault.reason().rule()).isEqualTo(PolicyLockRules.RULE).isEqualTo("closure.policy-locks");
            assertThat(fault.origin()).isEqualTo(new Origin("base::directive:directive_001"));
            assertThat(fault.packs()).extracting(RefusalPack::name).containsExactly("base", "base");
            assertThat(fault.detail())
                    .isEqualTo("pack `base` (" + BASE_ID + ") names `directive:directive_001`, but pack `base` ("
                            + BASE_ID + ") holds `directive:DIRECTIVE_001`: ids differ only by case and are matched"
                            + " exactly");
            // Registration's phase 6 answer carries no violation of its own.
            assertThat(fault.violations()).isEmpty();
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
            assertThat(fault.reason().rule()).isEqualTo(PolicyLockRules.RULE);
            assertThat(fault.origin()).isEqualTo(new Origin("org::directive:directive_001"));
            assertThat(fault.packs()).extracting(RefusalPack::name).containsExactly("org", "base");
            assertThat(fault.detail())
                    .isEqualTo("pack `org` (" + ORG_ID + ") names `directive:directive_001`, but pack `base` ("
                            + BASE_ID + ") holds `directive:DIRECTIVE_001`: ids differ only by case and are matched"
                            + " exactly");
            assertThat(fault.violations()).isEmpty();
        });
    }

    @Test
    @DisplayName("a lock held exactly is no fault")
    void aLockHeldExactlyIsAccepted() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), MutabilityPolicySource.empty()));
        facts.put("org", facts(List.of(), List.of(), locking("directive:DIRECTIVE_001")));

        assertThat(judge(BASE, ORG)).isEmpty();
    }

    @Test
    @DisplayName("a lock no pack of the own closure holds, in any case, is no fault: it changes no write")
    void aLockNobodyHoldsIsAccepted() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), MutabilityPolicySource.empty()));
        facts.put("org", facts(List.of(), List.of(), locking("directive:directive_404")));

        assertThat(judge(BASE, ORG)).isEmpty();
    }

    @Test
    @DisplayName("a case match held only by a dependent, outside the own closure, is no fault")
    void aCaseMatchHeldOnlyByADependentIsAccepted() {
        PackUnderResolution dependent = pack(DEPENDENT_ID, "dependent", List.of(BASE_ID));
        facts.put("base", facts(List.of(), List.of(), locking("directive:directive_dep")));
        facts.put("dependent", facts(List.of("directive:DIRECTIVE_DEP"), List.of(), MutabilityPolicySource.empty()));

        assertThat(judge(BASE, dependent)).isEmpty();
    }

    @Test
    @DisplayName("a pack with no policy is no fault")
    void aPackWithNoPolicyIsAccepted() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), MutabilityPolicySource.empty()));
        facts.put("org", facts(List.of(), List.of(), MutabilityPolicySource.empty()));

        assertThat(judge(BASE, ORG)).isEmpty();
    }

    @Test
    @DisplayName("through closureFaults, a pack's case-only preset item comes first, then its case-only lock (D15)")
    void thePresetItemComesBeforeTheLock() {
        facts.put("base", facts(List.of("directive:DIRECTIVE_001"), List.of(), MutabilityPolicySource.empty()));
        facts.put(
                "org",
                facts(
                        List.of(),
                        List.of(new Preset(
                                "team",
                                "The team preset.",
                                Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.of("Directive_001"))))),
                        locking("directive:directive_001")));

        assertThat(PackRulesets.closureFaults(read(BASE, ORG), factsOf()))
                .extracting(
                        ClosureFault::code,
                        fault -> fault.reason().rule(),
                        ClosureFault::origin,
                        fault -> fault.violations().size())
                .containsExactly(
                        tuple(
                                RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING,
                                PresetItemRules.RULE,
                                new Origin("org::directive:Directive_001"),
                                0),
                        tuple(
                                RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING,
                                PolicyLockRules.RULE,
                                new Origin("org::directive:directive_001"),
                                0));
    }

    private static MutabilityPolicySource locking(String urn) {
        return MutabilityPolicySource.builder()
                .entry(Urn.parse(urn).orElseThrow(), MutabilityLevel.LOCKED)
                .build();
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
