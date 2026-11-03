package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * One compose is one closure rooted at one level (FR-011), with dependencies before dependents (FR-004) and a shared
 * dependency loaded once (FR-005) — SC-001's five-pack diamond. The level rule and the layer merge across levels are
 * pinned in {@code PackCompositionResolverTest} and the composition scenarios.
 */
class ComposeLevelSeparationTest {

    @Test
    void theFivePackDiamondResolvesWithEveryDependencyBeforeItsDependentsAndTheSharedPackOnce() {
        PackUnderResolution alpha = pack("01AAA", "alpha", PackLevel.ORG);
        PackUnderResolution bravo = pack("01BBB", "bravo", PackLevel.ORG, new PackDependency("01AAA", ">=1.0"));
        PackUnderResolution charlie = pack("01CCC", "charlie", PackLevel.ORG, new PackDependency("01AAA", "<2.0"));
        PackUnderResolution delta = pack(
                "01DDD",
                "delta",
                PackLevel.ORG,
                new PackDependency("01BBB", ">=1.0"),
                new PackDependency("01CCC", ">=1.0"));
        PackUnderResolution echo = pack("01EEE", "echo", PackLevel.ORG, new PackDependency("01AAA", ">=1.0"));

        List<PackUnderResolution> resolved =
                PackCompositionResolver.resolve(List.of(echo, delta, charlie, bravo, alpha));
        assertThat(idsOf(resolved))
                .as("SC-001: alpha before bravo and charlie, both before delta, alpha once; delta and echo are ready"
                        + " together once bravo and charlie are placed, and the stable order puts delta first")
                .containsExactly("01AAA", "01BBB", "01CCC", "01DDD", "01EEE");
    }

    @Test
    void aDependencyChainResolvesInDependencyOrderWhateverItsIdsSortAs() {
        // One level, ids sorting the opposite way to the dependencies: only the dependency edges can order this.
        PackUnderResolution base = pack("01ZBASE", "base", PackLevel.ORG);
        PackUnderResolution middle = pack("01MID0", "middle", PackLevel.ORG, new PackDependency("01ZBASE", ">=1.0"));
        PackUnderResolution top = pack("01AATOP", "top", PackLevel.ORG, new PackDependency("01MID0", ">=1.0"));

        assertThat(idsOf(PackCompositionResolver.resolve(List.of(top, middle, base))))
                .containsExactly("01ZBASE", "01MID0", "01AATOP");
    }

    @Test
    void aSameLevelDependencyIsAllowedAndResolvesInDependencyOrder() {
        PackUnderResolution dependency = pack("01ZDEP", "dependency", PackLevel.ORG);
        PackUnderResolution dependent =
                pack("01ADEP", "dependent", PackLevel.ORG, new PackDependency("01ZDEP", ">=1.0"));

        assertThat(idsOf(PackCompositionResolver.resolve(List.of(dependent, dependency))))
                .as("two org packs may depend on each other's artifacts; the dependency comes first whatever its id")
                .containsExactly("01ZDEP", "01ADEP");
    }

    private static List<String> idsOf(List<PackUnderResolution> resolved) {
        return resolved.stream().map(entry -> entry.descriptor().packId()).toList();
    }

    private static PackUnderResolution pack(String id, String name, PackLevel level, PackDependency... dependencies) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(
                        id,
                        "1.0.0",
                        Optional.empty(),
                        name,
                        Optional.of(level),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of(dependencies)));
    }
}
