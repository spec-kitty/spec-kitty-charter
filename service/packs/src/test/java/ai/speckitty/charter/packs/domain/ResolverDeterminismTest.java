package ai.speckitty.charter.packs.domain;

import static ai.speckitty.charter.packs.fixtures.FixtureUlids.registeredIdOf;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.fixtures.FixtureUlids;
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
                pack("01AAA000000000000000000000", "aaa", PackLevel.BUILT_IN),
                pack("01FFF000000000000000000000", "fff", PackLevel.BUILT_IN),
                pack(
                        "01BBB000000000000000000000",
                        "bbb",
                        PackLevel.ORG,
                        new PackDependency("01AAA000000000000000000000", ">=1.0")),
                pack(
                        "01CCC000000000000000000000",
                        "ccc",
                        PackLevel.ORG,
                        new PackDependency("01AAA000000000000000000000", "<2.0")),
                pack(
                        "01DDD000000000000000000000",
                        "ddd",
                        PackLevel.ORG,
                        new PackDependency("01BBB000000000000000000000", ">=1.0"),
                        new PackDependency("01CCC000000000000000000000", ">=1.0")),
                pack(
                        "01EEE000000000000000000000",
                        "eee",
                        PackLevel.ORG,
                        new PackDependency("01AAA000000000000000000000", ">=1.0")),
                pack(
                        "01GGG000000000000000000000",
                        "ggg",
                        PackLevel.PROJECT,
                        new PackDependency("01DDD000000000000000000000", ">=1.0")),
                pack(
                        "01HHH000000000000000000000",
                        "hhh",
                        PackLevel.PROJECT,
                        new PackDependency("01EEE000000000000000000000", ">=1.0")));

        for (int run = 0; run < RUNS; run++) {
            List<PackUnderResolution> shuffled = new ArrayList<>(fixture);
            Collections.shuffle(shuffled, new Random(0x5EED_0000L + run));
            assertThat(idsOf(PackCompositionResolver.resolve(shuffled)))
                    .as("run %d", run)
                    .containsExactly(
                            "01AAA000000000000000000000",
                            "01FFF000000000000000000000",
                            "01BBB000000000000000000000",
                            "01CCC000000000000000000000",
                            "01DDD000000000000000000000",
                            "01EEE000000000000000000000",
                            "01GGG000000000000000000000",
                            "01HHH000000000000000000000");
        }
    }

    @Test
    void packsNoDependencyRelatesStayInLevelThenIdCodePointOrder() {
        // U+002D sorts before U+0030, and U+0030 before U+0061: an order that ignored the hyphen would differ.
        List<PackUnderResolution> fixture = List.of(
                registered("ab", PackLevel.BUILT_IN),
                registered("omega", PackLevel.ORG),
                registered("kilo-1", PackLevel.PROJECT),
                registered("a-b", PackLevel.BUILT_IN),
                registered("omega0", PackLevel.ORG),
                registered("kilo", PackLevel.PROJECT));

        for (int run = 0; run < RUNS; run++) {
            List<PackUnderResolution> shuffled = new ArrayList<>(fixture);
            Collections.shuffle(shuffled, new Random(0xD00D_0000L + run));
            assertThat(PackCompositionResolver.resolve(shuffled))
                    .extracting(entry -> entry.configured().id().value())
                    .as("run %d: level rank, then pack id by code point", run)
                    .containsExactly("a-b", "ab", "omega", "omega0", "kilo", "kilo-1");
        }
    }

    @Test
    void aLevelStaysContiguousWhenItsDependencyIsMetLate() {
        // B2 (built-in) needs B1 (built-in); O1 (org) needs nothing. FIFO would answer B1, O1, B2.
        List<PackUnderResolution> fixture = List.of(
                pack("01NZ1000000000000000000000", "o1", PackLevel.ORG),
                pack(
                        "01B20000000000000000000000",
                        "b2",
                        PackLevel.BUILT_IN,
                        new PackDependency("01B10000000000000000000000", ">=1.0")),
                pack("01B10000000000000000000000", "b1", PackLevel.BUILT_IN));

        assertThat(idsOf(PackCompositionResolver.resolve(fixture)))
                .containsExactly(
                        "01B10000000000000000000000", "01B20000000000000000000000", "01NZ1000000000000000000000");
    }

    private static List<String> idsOf(List<PackUnderResolution> resolved) {
        return resolved.stream().map(entry -> entry.descriptor().packId()).toList();
    }

    /** A pack registered under a pack id, whose descriptor declares a ULID of its own. */
    private static PackUnderResolution registered(String id, PackLevel level) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(
                        FixtureUlids.of(id), "1.0.0", Optional.empty(), id, Optional.of(level), Optional.empty()));
    }

    private static PackUnderResolution pack(String id, String name, PackLevel level, PackDependency... dependencies) {
        return new PackUnderResolution(
                new ConfiguredPack(registeredIdOf(id), level),
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
