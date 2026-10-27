package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.PackSources;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.domain.TacticStep;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The use-case rules over in-memory ports: what is returned, and which refusal an absence becomes. */
class TacticReadServiceTest {

    private static final PackIdentifier PACK_A = new PackIdentifier("pack-a");
    private static final PackIdentifier PACK_B = new PackIdentifier("pack-b");
    private static final PackIdentifier ABSENT = new PackIdentifier("absent");
    private static final TacticIdentifier ALPHA = new TacticIdentifier("alpha");

    private final Tactic alpha = tactic(PACK_A, "alpha");
    private final Tactic beta = tactic(PACK_A, "beta");
    private final Tactic other = tactic(PACK_B, "other");
    private final Map<PackIdentifier, List<Tactic>> stored =
            Map.of(PACK_A, List.of(alpha, beta), PACK_B, List.of(other));

    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);

    private final AtomicInteger digests = new AtomicInteger();
    private final TacticReadService service = serviceDigesting(() -> {
        digests.incrementAndGet();
        return D1;
    });

    private TacticReadService serviceDigesting(PackSources sources) {
        return new TacticReadService(new InMemoryPacks(), new InMemoryTactics(), new ConsistentRead(sources));
    }

    /** Answers D1, then D2 for every later digest: the inputs changed while the read was answered. */
    private TacticReadService serviceWhoseInputsChange() {
        AtomicInteger count = new AtomicInteger();
        return serviceDigesting(() -> count.incrementAndGet() == 1 ? D1 : D2);
    }

    @Test
    void listsOnlyTheAskedPacksTactics() {
        assertThat(service.tacticsOf(PACK_A).value()).containsExactly(alpha, beta);
        assertThat(service.tacticsOf(PACK_B).value()).containsExactly(other).doesNotContain(alpha, beta);
    }

    @Test
    void readsOneTacticOfTheAskedPack() {
        assertThat(service.tactic(PACK_A, ALPHA).value()).isEqualTo(alpha);
    }

    @Test
    void anAbsentPackIsAClientSideNotFoundNotAnInternalError() {
        // Control: the same service answers the present pack, so the refusal is the absence's, not a broken setup.
        assertThat(service.tacticsOf(PACK_A).value()).isNotEmpty();

        assertThatThrownBy(() -> service.tacticsOf(ABSENT)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
            // The original unreadable-pack refusal stays chained, so the diagnostic trail is not lost.
            assertThat(r.getCause())
                    .isInstanceOfSatisfying(
                            CharterRefusal.class, c -> assertThat(c.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        });
        assertThatThrownBy(() -> service.tactic(ABSENT, ALPHA))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void anAbsentTacticIsNotFoundAndDoesNotFallBackToAnotherPack() {
        // "other" exists, but in pack-b: asking pack-a for it must refuse, not cross packs.
        assertThatThrownBy(() -> service.tactic(PACK_A, new TacticIdentifier("other")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.TACTIC_NOT_FOUND));
    }

    @Test
    void everyReadDigestsTwiceAndAnswersTheDigestItVerified() {
        assertThat(service.tacticsOf(PACK_A).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(2);

        assertThat(service.tactic(PACK_A, ALPHA).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(4);

        assertThatThrownBy(() -> service.tactic(PACK_A, new TacticIdentifier("missing")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.TACTIC_NOT_FOUND));
        assertThat(digests).hasValue(6);
    }

    @Test
    void changedInputsBeatTheListOfAnAbsentPack() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().tacticsOf(ABSENT))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage("The inputs changed while the tactics were read; read them again.");
    }

    @Test
    void changedInputsBeatAnAbsentPackAndAnAbsentTactic() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().tactic(ABSENT, ALPHA))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage("The inputs changed while the tactic was read; read it again.");
        assertThatThrownBy(() -> serviceWhoseInputsChange().tactic(PACK_A, new TacticIdentifier("missing")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void changedInputsRefuseASuccessfulReadToo() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().tacticsOf(PACK_A))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    private static Tactic tactic(PackIdentifier pack, String id) {
        return Tactic.builder()
                .id(new TacticIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .name(id)
                .step(new TacticStep(id + " step", Optional.empty(), List.of(), List.of()))
                .build();
    }

    /** Point read only: assembling the aggregate fails the test, so the service cannot regress to it. */
    private final class InMemoryPacks implements PackRepository {
        @Override
        public Pack load(PackIdentifier pack) {
            throw new AssertionError("the read must not assemble the whole aggregate");
        }

        @Override
        public List<Tactic> tacticsOf(PackIdentifier pack) {
            List<Tactic> found = stored.get(pack);
            if (found == null) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + pack.value());
            }
            return found;
        }
    }

    private final class InMemoryTactics implements TacticRepository {
        @Override
        public void save(Tactic tactic) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Tactic> load(PackIdentifier pack, TacticIdentifier tactic) {
            List<Tactic> found = stored.get(pack);
            if (found == null) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + pack.value());
            }
            return found.stream().filter(t -> t.id().equals(tactic)).findFirst();
        }
    }
}
