package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackSources;
import ai.speckitty.charter.packs.domain.Procedure;
import ai.speckitty.charter.packs.domain.ProcedureIdentifier;
import ai.speckitty.charter.packs.domain.ProcedureRepository;
import ai.speckitty.charter.packs.domain.ProcedureStep;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The procedure read use case over in-memory ports (#484, #489): the procedure and the revision it was read at, which
 * refusal an absence becomes, and the fingerprint the verified read answers.
 */
class ProcedureReadServiceTest {

    private static final PackIdentifier PACK_A = new PackIdentifier("pack-a");
    private static final PackIdentifier PACK_B = new PackIdentifier("pack-b");
    private static final PackIdentifier ABSENT = new PackIdentifier("absent");
    // Configured, but its files cannot be read: the storage refuses it PACK_UNREADABLE.
    private static final PackIdentifier UNREADABLE = new PackIdentifier("unreadable");
    private static final ProcedureIdentifier LANDING = new ProcedureIdentifier("landing");
    private static final ProcedureIdentifier REVIEW = new ProcedureIdentifier("review");

    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);

    private final Procedure landing = procedure(PACK_A, "landing");
    private final Procedure review = procedure(PACK_B, "review");
    private final Map<PackIdentifier, List<Procedure>> stored =
            Map.of(PACK_A, List.of(landing), PACK_B, List.of(review));
    private final Map<PackIdentifier, PackRevision> revisions = Map.of(
            PACK_A, PackRevision.compute(List.of("pack-a")),
            PACK_B, PackRevision.compute(List.of("pack-b")));

    private final AtomicInteger digests = new AtomicInteger();
    private final ProcedureReadService service = serviceDigesting(() -> {
        digests.incrementAndGet();
        return D1;
    });

    private ProcedureReadService serviceDigesting(PackSources sources) {
        return new ProcedureReadService(new InMemoryProcedures(), new ConsistentRead(sources), this::revisionOf);
    }

    private ProcedureReadService serviceWhoseInputsChange() {
        AtomicInteger count = new AtomicInteger();
        return serviceDigesting(() -> count.incrementAndGet() == 1 ? D1 : D2);
    }

    @Test
    void readsOneProcedureOfTheAskedPackWithThatPacksRevision() {
        Revised<Procedure> read = service.procedureWithRevision(PACK_A, LANDING).value();

        assertThat(read.value()).isEqualTo(landing);
        assertThat(read.revision())
                .as("the asked pack's revision, not another pack's")
                .isEqualTo(revisions.get(PACK_A))
                .isNotEqualTo(revisions.get(PACK_B));
        assertThat(service.procedureWithRevision(PACK_B, REVIEW).value().revision())
                .as("the control: the other pack answers its own")
                .isEqualTo(revisions.get(PACK_B));
    }

    @Test
    void anAbsentProcedureIsArtifactNotFoundAndDoesNotFallBackToAnotherPack() {
        // Control: the present pack holds landing, so the refusal is the procedure's absence, not a broken fixture.
        assertThat(service.procedureWithRevision(PACK_A, LANDING).value().value())
                .isEqualTo(landing);

        assertThatThrownBy(() -> service.procedureWithRevision(PACK_A, REVIEW))
                .as("pack-b holds review; pack-a does not")
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                    assertThat(r.getMessage()).isEqualTo("Pack pack-a has no procedure review.");
                });
    }

    @Test
    void anAbsentOrUnreadablePackIsPackNotFoundNotAnInternalError() {
        // The storage refuses an unknown pack PACK_NOT_FOUND itself; it passes through, with nothing chained.
        assertThatThrownBy(() -> service.procedureWithRevision(ABSENT, LANDING))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
                    assertThat(r.getCause()).isNull();
                });
        // A configured pack that cannot be read is not found too, and keeps the storage refusal as its cause.
        assertThatThrownBy(() -> service.procedureWithRevision(UNREADABLE, LANDING))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
                    assertThat(r.getCause())
                            .isInstanceOfSatisfying(
                                    CharterRefusal.class,
                                    c -> assertThat(c.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
                });
    }

    @Test
    void aRevisionThatCannotBeReadIsPackNotFoundToo() {
        ProcedureReadService unreadableRevision =
                new ProcedureReadService(new InMemoryProcedures(), new ConsistentRead(() -> D1), pack -> {
                    throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack-a cannot be listed");
                });

        assertThatThrownBy(() -> unreadableRevision.procedureWithRevision(PACK_A, LANDING))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
                    assertThat(r.getCause())
                            .isInstanceOfSatisfying(
                                    CharterRefusal.class,
                                    c -> assertThat(c.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
                });
    }

    @Test
    void theReadDigestsTwiceAndAnswersTheFingerprintItVerified() {
        assertThat(service.procedureWithRevision(PACK_A, LANDING).fingerprint()).isEqualTo(D1);
        assertThat(digests).hasValue(2);
    }

    @Test
    void changedInputsBeatAnAbsentProcedureAndRefuseASuccessfulRead() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().procedureWithRevision(PACK_A, REVIEW))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage("The inputs changed while the procedure was read; read it again.");
        assertThatThrownBy(() -> serviceWhoseInputsChange().procedureWithRevision(PACK_A, LANDING))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    private static Procedure procedure(PackIdentifier pack, String id) {
        return Procedure.builder()
                .id(new ProcedureIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .description("What " + id + " is.")
                .entryCondition("ready")
                .exitCondition("done")
                .step(new ProcedureStep(
                        "Do it", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
    }

    /** The stored procedures of a pack, refused as the storage refuses an unknown or an unreadable pack. */
    private List<Procedure> storedIn(PackIdentifier pack) {
        if (pack.equals(UNREADABLE)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack unreadable has no pack.yaml");
        }
        List<Procedure> found = stored.get(pack);
        if (found == null) {
            throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
        }
        return found;
    }

    private PackRevision revisionOf(PackIdentifier pack) {
        storedIn(pack);
        return revisions.get(pack);
    }

    private final class InMemoryProcedures implements ProcedureRepository {
        @Override
        public Optional<Procedure> load(PackIdentifier pack, ProcedureIdentifier procedure) {
            return storedIn(pack).stream().filter(p -> p.id().equals(procedure)).findFirst();
        }
    }
}
