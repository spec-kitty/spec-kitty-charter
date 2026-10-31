package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The offering over in-memory ports: the merge order (configured level, then id in code-point order), the configured
 * level over the declared one, the descriptor's name and version, and the refusals of one verified read.
 */
class OfferingReadServiceTest {

    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);

    private final Map<String, Optional<PackDescriptor>> descriptors = new HashMap<>();
    private final Map<String, CharterRefusal> unreadable = new HashMap<>();

    private ConfiguredPack configured(String id, PackLevel level) {
        descriptors.putIfAbsent(id, Optional.of(descriptor(id, Optional.empty())));
        return new ConfiguredPack(new PackIdentifier(id), level);
    }

    private static PackDescriptor descriptor(String id, Optional<PackLevel> declared) {
        return new PackDescriptor(id, "1.0." + id.length(), Optional.empty(), id + " pack", declared, Optional.empty());
    }

    private OfferingReadService service(List<ConfiguredPack> packs, ConsistentRead read) {
        return new OfferingReadService(() -> packs, new InMemoryDescriptors(), read);
    }

    private OfferingReadService service(List<ConfiguredPack> packs) {
        return service(packs, new ConsistentRead(() -> D1));
    }

    @Test
    void theOrderIsByConfiguredLevelThenByIdInCodePointOrder() {
        // Configured out of order: project first, and the lower-case id before the upper-case one at every level.
        List<ConfiguredPack> packs = List.of(
                configured("charlie", PackLevel.PROJECT),
                configured("Kilo", PackLevel.PROJECT),
                configured("bravo", PackLevel.ORG),
                configured("Omega", PackLevel.ORG),
                configured("alpha", PackLevel.BUILT_IN),
                configured("Zeta", PackLevel.BUILT_IN));

        assertThat(service(packs).offering().value())
                .extracting(entry -> entry.id().value() + "@" + entry.level().token())
                .containsExactly(
                        "Zeta@built-in", "alpha@built-in", "Omega@org", "bravo@org", "Kilo@project", "charlie@project");
    }

    @Test
    void theIdOrderComparesCodePointsNotUtf16Units() {
        // U+1F600 is a surrogate pair, which String.compareTo puts before U+FB01; code-point order puts it after.
        List<ConfiguredPack> packs = List.of(configured("😀", PackLevel.ORG), configured("ﬁ", PackLevel.ORG));

        assertThat(service(packs).offering().value())
                .extracting(entry -> entry.id().value())
                .containsExactly("ﬁ", "😀");
    }

    @Test
    void anEntryCarriesTheConfiguredLevelAndTheDescriptorsNameAndVersion() {
        descriptors.put("local", Optional.of(descriptor("local", Optional.of(PackLevel.ORG))));

        OfferingReadService.OfferingEntry entry = service(List.of(configured("local", PackLevel.PROJECT)))
                .offering()
                .value()
                .getFirst();

        assertThat(entry.id()).isEqualTo(new PackIdentifier("local"));
        assertThat(entry.level())
                .as("the configured level, not the declared one")
                .isEqualTo(PackLevel.PROJECT);
        assertThat(entry.name()).isEqualTo("local pack");
        assertThat(entry.version()).isEqualTo("1.0.5");
    }

    @Test
    void aPackWithoutADescriptorRefusesTheWholeOfferingAsUnreadable() {
        // Operator ruling of 2026-10-31: a configured pack without pack.yaml is no readable canonical pack.
        descriptors.put("bare", Optional.empty());
        List<ConfiguredPack> packs = List.of(configured("qa", PackLevel.ORG), configured("bare", PackLevel.ORG));

        assertThatThrownBy(() -> service(packs).offering()).isInstanceOfSatisfying(CharterRefusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refused.getMessage()).isEqualTo("pack bare has no pack.yaml");
        });
    }

    @Test
    void anUnreadableDescriptorRefusesTheWholeOffering() {
        unreadable.put("broken", new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of broken cannot be read"));
        List<ConfiguredPack> packs = List.of(configured("qa", PackLevel.ORG), configured("broken", PackLevel.ORG));

        assertThatThrownBy(() -> service(packs).offering())
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void theAnswerCarriesTheFingerprintItVerified() {
        AtomicInteger digests = new AtomicInteger();
        ConsistentRead read = new ConsistentRead(() -> {
            digests.incrementAndGet();
            return D1;
        });

        ConsistentRead.Verified<List<OfferingReadService.OfferingEntry>> verified =
                service(List.of(configured("qa", PackLevel.ORG)), read).offering();

        assertThat(verified.fingerprint()).isEqualTo(D1);
        assertThat(digests.get()).as("one verified read: two digests").isEqualTo(2);
    }

    @Test
    void anEmptyCatalogIsAnEmptyOffering() {
        assertThat(service(List.of()).offering().value()).isEmpty();
    }

    @Test
    void inputsThatChangeWhileTheOfferingIsReadAreStale() {
        AtomicInteger count = new AtomicInteger();
        ConsistentRead drifting = new ConsistentRead(() -> count.incrementAndGet() == 1 ? D1 : D2);

        assertThatThrownBy(() -> service(List.of(configured("qa", PackLevel.ORG)), drifting)
                        .offering())
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.STALE_INPUT);
                    assertThat(refused.getMessage())
                            .contains("The inputs changed while the offering was read; read it again.");
                });
    }

    /** The descriptors by pack id; an id in {@link #unreadable} refuses, as storage refuses an invalid pack.yaml. */
    private final class InMemoryDescriptors implements PackDescriptorRepository {

        @Override
        public PackDescriptor get(PackIdentifier pack) {
            return find(pack).orElseThrow(() -> new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack.yaml"));
        }

        @Override
        public Optional<PackDescriptor> find(PackIdentifier pack) {
            CharterRefusal refusal = unreadable.get(pack.value());
            if (refusal != null) {
                throw refusal;
            }
            return descriptors.getOrDefault(pack.value(), Optional.empty());
        }

        @Override
        public void save(PackIdentifier pack, PackDescriptor descriptor) {
            throw new UnsupportedOperationException("the offering never writes");
        }
    }
}
