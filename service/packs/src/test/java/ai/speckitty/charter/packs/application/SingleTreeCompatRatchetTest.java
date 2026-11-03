package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The compatibility ratchet (FR-018, NFR-004, SC-003): a compose built only from today's packs — the single
 * {@code built-in} tree and the dependency-free shapes of the existing offering fixtures — answers exactly the golden
 * values written as literals below, today's pre-mission answers, never read back from the service. The only tolerated
 * difference is the additive dependency fact WP03 exposed, which for a dependency-free fixture is an empty list; the
 * ratchet is shrink-only, so any other difference is a finding, not an expectation update.
 */
class SingleTreeCompatRatchetTest {

    private static final String DIGEST = "sha256:" + "4".repeat(64);

    /** Today's actual shape: one pack, the single {@code built-in} tree, declaring no {@code depends_on}. */
    private static final String TREE_ID = "01BUILTIN0000000000000000";

    private static final int READS = 100;

    @Test
    void theSingleBuiltinTreeAnswersTheGoldenValuesAcrossOneHundredReads() {
        OfferingReadService service = offering(
                List.of(configured(TREE_ID, PackLevel.BUILT_IN)),
                new PackDescriptor(
                        TREE_ID,
                        "1.0.0",
                        Optional.empty(),
                        "built-in",
                        Optional.of(PackLevel.BUILT_IN),
                        Optional.empty()));
        List<OfferingReadService.OfferingEntry> golden =
                List.of(entry(TREE_ID, PackLevel.BUILT_IN, "built-in", "1.0.0"));

        for (int read = 0; read < READS; read++) {
            ConsistentRead.Verified<List<OfferingReadService.OfferingEntry>> verified = service.offering();
            assertThat(verified.value())
                    .as("the single built-in tree, read %d", read)
                    .containsExactlyElementsOf(golden);
            assertThat(verified.fingerprint())
                    .as("read %d carries the digest it verified", read)
                    .isEqualTo(DIGEST);
        }
    }

    @Test
    void theComposeReadAnswersTodaysSingleTreeOnceWithNoDependencyFacts() {
        OfferingReadService service = offering(
                List.of(configured(TREE_ID, PackLevel.BUILT_IN)),
                new PackDescriptor(
                        TREE_ID,
                        "1.0.0",
                        Optional.empty(),
                        "built-in",
                        Optional.of(PackLevel.BUILT_IN),
                        Optional.empty()));

        for (int read = 0; read < READS; read++) {
            List<PackCompositionResolver.PackUnderResolution> closure =
                    service.compose().value();
            assertThat(closure)
                    .as("the closure holds the tree once, read %d", read)
                    .hasSize(1);
            PackCompositionResolver.PackUnderResolution pack = closure.getFirst();
            assertThat(pack.configured().id()).isEqualTo(new PackIdentifier(TREE_ID));
            assertThat(pack.configured().level()).isEqualTo(PackLevel.BUILT_IN);
            assertThat(pack.descriptor().name()).isEqualTo("built-in");
            assertThat(pack.descriptor().packVersion()).isEqualTo("1.0.0");
            assertThat(pack.descriptor().dependencies())
                    .as("WP03's additive fact, empty for a pack that declares no depends_on")
                    .isEmpty();
            assertThat(pack.descriptor().overrides()).isEmpty();
        }
    }

    @Test
    void theDependencyFreeFixtureSetAnswersTheGoldenValuesAcrossOneHundredReads() {
        // The existing offering fixtures' shapes: configured out of order across all three levels, ids whose UTF-16
        // order differs from their code-point order, and no pack declaring a dependency.
        List<ConfiguredPack> scrambled = List.of(
                configured("charlie", PackLevel.PROJECT),
                configured("Kilo", PackLevel.PROJECT),
                configured("bravo", PackLevel.ORG),
                configured("Omega", PackLevel.ORG),
                configured("alpha", PackLevel.BUILT_IN),
                configured("Zeta", PackLevel.BUILT_IN));
        List<ConfiguredPack> surrogatePair =
                List.of(configured("😀", PackLevel.BUILT_IN), configured("ﬁ", PackLevel.BUILT_IN));

        OfferingReadService levels = offering(
                scrambled,
                descriptor("charlie", "charlie pack", "1.0.7"),
                descriptor("Kilo", "Kilo pack", "1.0.4"),
                descriptor("bravo", "bravo pack", "1.0.5"),
                descriptor("Omega", "Omega pack", "1.0.5"),
                descriptor("alpha", "alpha pack", "1.0.5"),
                descriptor("Zeta", "Zeta pack", "1.0.4"));
        OfferingReadService codePoints = offering(
                surrogatePair, descriptor("😀", "grinning pack", "1.0.2"), descriptor("ﬁ", "ligature pack", "1.0.1"));

        List<OfferingReadService.OfferingEntry> goldenLevels = List.of(
                entry("Zeta", PackLevel.BUILT_IN, "Zeta pack", "1.0.4"),
                entry("alpha", PackLevel.BUILT_IN, "alpha pack", "1.0.5"),
                entry("Omega", PackLevel.ORG, "Omega pack", "1.0.5"),
                entry("bravo", PackLevel.ORG, "bravo pack", "1.0.5"),
                entry("Kilo", PackLevel.PROJECT, "Kilo pack", "1.0.4"),
                entry("charlie", PackLevel.PROJECT, "charlie pack", "1.0.7"));
        List<OfferingReadService.OfferingEntry> goldenCodePoints = List.of(
                entry("ﬁ", PackLevel.BUILT_IN, "ligature pack", "1.0.1"),
                entry("😀", PackLevel.BUILT_IN, "grinning pack", "1.0.2"));

        for (int read = 0; read < READS; read++) {
            assertThat(levels.offering().value())
                    .as("the out-of-order fixture, read %d", read)
                    .containsExactlyElementsOf(goldenLevels);
            assertThat(codePoints.offering().value())
                    .as("the code-point fixture, read %d: U+FB01 before the surrogate pair U+1F600", read)
                    .containsExactlyElementsOf(goldenCodePoints);
        }
    }

    /** A golden entry: today's four answer fields plus the additive dependency fact, empty for a dependency-free pack. */
    private static OfferingReadService.OfferingEntry entry(String id, PackLevel level, String name, String version) {
        return new OfferingReadService.OfferingEntry(new PackIdentifier(id), id, level, name, version, List.of());
    }

    private static ConfiguredPack configured(String id, PackLevel level) {
        return new ConfiguredPack(new PackIdentifier(id), level);
    }

    private static PackDescriptor descriptor(String id, String name, String version) {
        return new PackDescriptor(id, version, Optional.empty(), name, Optional.empty(), Optional.empty());
    }

    /** The offering read over in-memory ports, exactly as the existing offering fixtures build it. */
    private static OfferingReadService offering(List<ConfiguredPack> configured, PackDescriptor... descriptors) {
        Map<String, PackDescriptor> byId = new HashMap<>();
        for (PackDescriptor descriptor : descriptors) {
            byId.put(descriptor.packId(), descriptor);
        }
        PackDescriptorRepository repository = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return find(pack).orElseThrow();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.ofNullable(byId.get(pack.value()));
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException("the ratchet never writes");
            }
        };
        return new OfferingReadService(
                () -> configured, repository, pack -> List.of(), new ConsistentRead(() -> DIGEST));
    }
}
