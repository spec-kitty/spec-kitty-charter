package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The resolution cost is bounded (NFR-003): a compose of fifty packs with two hundred dependency edges — levels mixed,
 * every range satisfied, a few diamonds — resolves within 250 ms at p95. This is a bounded smoke, not a benchmark
 * harness: a warm-up keeps JIT noise out of the number, and if the case ever flakes it is quarantined and recorded,
 * never deleted or weakened. Tagged {@code timing}: it runs under {@code :packs:timingBudgets}, alone, and not in the
 * unit tier.
 */
@Tag("timing")
class ResolverCostBoundTest {

    private static final int PACKS = 50;

    private static final int EDGES = 200;

    private static final int WARM_UP = 10;

    private static final int MEASURED = 100;

    /** The NFR's contract: p95 below 250 ms on the reference build. */
    private static final double P95_LIMIT_MILLIS = 250.0;

    @Test
    void aFiftyPackTwoHundredEdgeComposeResolvesWithinTheP95Bound() {
        List<PackUnderResolution> fixture = fixture();
        assertThat(edgesOf(fixture))
                .as("the fixture holds exactly two hundred edges")
                .isEqualTo(EDGES);
        assertThat(PackCompositionResolver.resolve(fixture))
                .as("the fixture resolves: acyclic, in range, own level or lower")
                .hasSize(PACKS);

        for (int warm = 0; warm < WARM_UP; warm++) {
            PackCompositionResolver.resolve(fixture);
        }
        long[] nanos = new long[MEASURED];
        for (int measured = 0; measured < MEASURED; measured++) {
            long start = System.nanoTime();
            PackCompositionResolver.resolve(fixture);
            nanos[measured] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        // Nearest-rank p95: the 95th smallest of the measured resolutions.
        double p95Millis = nanos[(int) Math.ceil(0.95 * MEASURED) - 1] / 1_000_000.0;
        assertThat(p95Millis)
                .as("p95 over %d resolutions of %d packs and %d edges", MEASURED, PACKS, EDGES)
                .isLessThan(P95_LIMIT_MILLIS);
    }

    /**
     * The fixture: pack {@code i} sits at built-in for {@code i < 20}, org below 40 and project above, and every edge
     * points from pack {@code i} to a pack with a lower index, so the graph is acyclic by construction and every edge
     * follows the own-level-or-lower rule, because the level ranks are non-decreasing in the index. The diamonds at
     * 6, 18, 30 and 42 give two siblings a shared dependency and a pack that depends on both.
     *
     * @return the fifty packs, pure resolver inputs, no filesystem
     */
    private static List<PackUnderResolution> fixture() {
        List<Set<Integer>> targetsByIndex = new ArrayList<>(PACKS);
        for (int index = 0; index < PACKS; index++) {
            targetsByIndex.add(new LinkedHashSet<>());
        }
        int edges = 0;
        for (int index = 1; index < PACKS; index++) {
            edges += add(targetsByIndex, index, index - 1);
        }
        for (int diamond : new int[] {6, 18, 30, 42}) {
            edges += add(targetsByIndex, diamond, diamond - 2);
            edges += add(targetsByIndex, diamond + 1, diamond - 2);
            edges += add(targetsByIndex, diamond + 2, diamond);
        }
        fill:
        for (int gap = 2; gap < PACKS; gap++) {
            for (int index = gap; index < PACKS; index++) {
                if (edges >= EDGES) {
                    break fill;
                }
                edges += add(targetsByIndex, index, index - gap);
            }
        }
        List<PackUnderResolution> packs = new ArrayList<>(PACKS);
        for (int index = 0; index < PACKS; index++) {
            PackLevel level = index < 20 ? PackLevel.BUILT_IN : index < 40 ? PackLevel.ORG : PackLevel.PROJECT;
            List<PackDependency> dependencies = new ArrayList<>();
            for (int target : targetsByIndex.get(index)) {
                dependencies.add(new PackDependency(id(target), ">=1.0"));
            }
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(id(index)), level),
                    new PackDescriptor(
                            id(index),
                            "1.0.0",
                            Optional.empty(),
                            "pack-" + index,
                            Optional.of(level),
                            Optional.empty(),
                            PackPaths.none(),
                            dependencies)));
        }
        return packs;
    }

    private static int add(List<Set<Integer>> targetsByIndex, int from, int to) {
        return targetsByIndex.get(from).add(to) ? 1 : 0;
    }

    private static int edgesOf(List<PackUnderResolution> packs) {
        return packs.stream()
                .mapToInt(pack -> pack.descriptor().dependencies().size())
                .sum();
    }

    private static String id(int index) {
        // Twenty-six characters, zero-padded, so the code-point order matches the index order.
        return String.format("01COST%020d", index);
    }
}
