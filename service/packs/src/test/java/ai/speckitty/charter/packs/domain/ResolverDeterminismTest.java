package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Resolution is deterministic (NFR-001): the same configured packs and descriptor contents produce the same resolved
 * sequence across one hundred repeated runs, whatever order the catalog lists them in. The tie-break is the order the
 * resolver documents — configured level rank, then pack id by code point — and the assertion is on the sequence, not
 * its multiset.
 */
class ResolverDeterminismTest {

    private static final int RUNS = 100;

    @Test
    void theResolvedSequenceIsIdenticalAcrossOneHundredShuffledInputs() {
        // Eight packs over three levels with a diamond (BBB and CCC share AAA; DDD depends on both) and a sibling
        // pair sharing AAA, configured in an order nothing like the resolved one.
        List<PackUnderResolution> fixture = List.of(
                pack("01AAA", "aaa", PackLevel.BUILT_IN),
                pack("01FFF", "fff", PackLevel.BUILT_IN),
                pack("01BBB", "bbb", PackLevel.ORG, new PackDependency("01AAA", ">=1.0")),
                pack("01CCC", "ccc", PackLevel.ORG, new PackDependency("01AAA", "<2.0")),
                pack(
                        "01DDD",
                        "ddd",
                        PackLevel.ORG,
                        new PackDependency("01BBB", ">=1.0"),
                        new PackDependency("01CCC", ">=1.0")),
                pack("01EEE", "eee", PackLevel.ORG, new PackDependency("01AAA", ">=1.0")),
                pack("01GGG", "ggg", PackLevel.PROJECT, new PackDependency("01DDD", ">=1.0")),
                pack("01HHH", "hhh", PackLevel.PROJECT, new PackDependency("01EEE", ">=1.0")));

        for (int run = 0; run < RUNS; run++) {
            List<PackUnderResolution> shuffled = new ArrayList<>(fixture);
            Collections.shuffle(shuffled, new Random(0x5EED_0000L + run));
            assertThat(idsOf(PackCompositionResolver.resolve(shuffled)))
                    .as("run %d", run)
                    .containsExactly("01AAA", "01FFF", "01BBB", "01CCC", "01DDD", "01EEE", "01GGG", "01HHH");
        }
    }

    @Test
    void packsNoDependencyRelatesStayInLevelThenIdCodePointOrder() {
        // U+1F600 is a surrogate pair, which String.compareTo puts before U+FB01; code-point order puts it after.
        List<PackUnderResolution> fixture = List.of(
                pack("😀", "grinning", PackLevel.BUILT_IN),
                pack("omega", "omega", PackLevel.ORG),
                pack("Kilo", "kilo", PackLevel.PROJECT),
                pack("ﬁ", "ligature", PackLevel.BUILT_IN),
                pack("Omega", "omega-upper", PackLevel.ORG),
                pack("kilo", "kilo-lower", PackLevel.PROJECT));

        for (int run = 0; run < RUNS; run++) {
            List<PackUnderResolution> shuffled = new ArrayList<>(fixture);
            Collections.shuffle(shuffled, new Random(0xD00D_0000L + run));
            assertThat(idsOf(PackCompositionResolver.resolve(shuffled)))
                    .as("run %d: level rank, then pack id by code point", run)
                    .containsExactly("ﬁ", "😀", "Omega", "omega", "Kilo", "kilo");
        }
    }

    @Test
    void aLevelStaysContiguousWhenItsDependencyIsMetLate() {
        // B2 (built-in) needs B1 (built-in); O1 (org) needs nothing. FIFO would answer B1, O1, B2.
        List<PackUnderResolution> fixture = List.of(
                pack("01O1", "o1", PackLevel.ORG),
                pack("01B2", "b2", PackLevel.BUILT_IN, new PackDependency("01B1", ">=1.0")),
                pack("01B1", "b1", PackLevel.BUILT_IN));

        assertThat(idsOf(PackCompositionResolver.resolve(fixture))).containsExactly("01B1", "01B2", "01O1");
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
