package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The pack-scoped reads of a pack's presets (#98): the list in id order, one preset with its must-have marks, the pack
 * judged first, and the revision and the fingerprint taken in one verified read. The packs are in memory.
 */
class PresetReadServiceTest {

    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final PackIdentifier OTHER = new PackIdentifier("other");
    private static final String FINGERPRINT = "sha256:" + "1".repeat(64);

    private final Map<String, Optional<PackDescriptor>> descriptors = new HashMap<>();
    private final Map<String, Pack> stored = new HashMap<>();
    private final AtomicInteger digests = new AtomicInteger();

    private static PackDescriptor descriptor(String name) {
        return new PackDescriptor(
                "id-" + name,
                "1.0.0",
                Optional.empty(),
                name,
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                List.of());
    }

    private static Preset team() {
        return new Preset(
                "team",
                "What a review team puts in force.",
                Map.of(
                        ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("D")),
                        ArtifactKind.TACTIC, List.of(PresetItem.of("t"))));
    }

    private static Preset minimal() {
        return new Preset(
                "minimal", "The least a review needs.", Map.of(ArtifactKind.TACTIC, List.of(PresetItem.of("t"))));
    }

    /** A pack with a pack.yaml and the given presets, added in the order given. */
    private void pack(PackIdentifier id, Preset... presets) {
        descriptors.put(id.value(), Optional.of(descriptor(id.value())));
        Pack.Builder pack = Pack.builder().id(id).descriptor(descriptor(id.value()));
        for (Preset preset : presets) {
            pack.preset(preset);
        }
        stored.put(id.value(), pack.build());
    }

    private PresetReadService service() {
        PackDescriptorRepository repository = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return find(pack).orElseThrow();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                Optional<PackDescriptor> found = descriptors.get(pack.value());
                if (found == null) {
                    throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
                }
                return found;
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException("a read never writes");
            }
        };
        PackRepository packs = pack -> stored.get(pack.value());
        PackRevisions revisions = pack -> PackRevision.compute(List.of(pack.value()));
        ConsistentRead read = new ConsistentRead(() -> {
            digests.incrementAndGet();
            return FINGERPRINT;
        });
        return new PresetReadService(packs, repository, read, revisions);
    }

    @Test
    void theListAnswersEveryPresetInIdOrderWithTheRevisionAndTheFingerprintOfOneVerifiedRead() {
        pack(QA, team(), minimal());

        ConsistentRead.Verified<PresetReadService.Listed> verified = service().list(QA);

        assertThat(verified.value().presets()).extracting(Preset::id).containsExactly("minimal", "team");
        assertThat(verified.value().revision()).isEqualTo(PackRevision.compute(List.of("qa")));
        assertThat(verified.fingerprint()).isEqualTo(FINGERPRINT);
        assertThat(digests).as("one verified read: two whole-source digests").hasValue(2);
    }

    @Test
    void aPackWithNoPresetAnswersAnEmptyList() {
        pack(QA);

        assertThat(service().list(QA).value().presets()).isEmpty();
    }

    @Test
    void onePresetAnswersItsDescriptionAndItsItemsWithTheirMustHaveMarks() {
        pack(QA, team(), minimal());

        ConsistentRead.Verified<PresetReadService.Held> verified = service().read(QA, "team");

        Preset preset = verified.value().preset();
        assertThat(preset.description()).isEqualTo("What a review team puts in force.");
        assertThat(preset.artifacts().get(ArtifactKind.DIRECTIVE)).containsExactly(new PresetItem("D", true));
        assertThat(preset.artifacts().get(ArtifactKind.TACTIC)).containsExactly(new PresetItem("t", false));
        assertThat(verified.value().revision()).isEqualTo(PackRevision.compute(List.of("qa")));
        assertThat(verified.fingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void aPresetThePackDoesNotHoldIsPresetNotFoundEvenWhenAnotherPackHoldsIt() {
        pack(QA, minimal());
        pack(OTHER, team());
        PresetReadService service = service();

        assertThatThrownBy(() -> service.read(QA, "team")).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PRESET_NOT_FOUND);
            assertThat(refusal.getMessage()).isEqualTo("Pack qa holds no preset team.");
        });
    }

    @Test
    void anUnknownPackIsPackNotFoundOnTheListAndOnTheItemWhateverPresetItNames() {
        PresetReadService service = service();

        assertThatThrownBy(() -> service.list(new PackIdentifier("nope")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
        assertThatThrownBy(() -> service.read(new PackIdentifier("nope"), "ghost"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void aConfiguredPackWithoutAPackYamlIsPackUnreadable() {
        pack(QA, team());
        descriptors.put(QA.value(), Optional.empty());
        PresetReadService service = service();

        assertThatThrownBy(() -> service.read(QA, "team"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }
}
