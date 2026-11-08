package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What an activation write's {@code If-Match} takes (operator decision {@code 01M6Y3GZFRC5WEQQ7G6GT0YMW3}): the quoted
 * revision, as before, or the current {@code getActivation} tag, which covers the revision and the closure's own digest
 * ({@link EffectiveTag#activation}). A closure tag that no longer names the closure is {@code 412}. The check is in one
 * place, so the register's writes ({@link ActivationService#put}, {@link ActivationService#delete}) and the
 * sub-resource writes ({@link ActivationSelectionService}, {@link ActivationExclusionService},
 * {@link ActivationPresetService}) agree. A revision costs no read of the packs; a closure tag costs one more verified
 * read, of the closure of the document as stored, which computes the tag.
 */
class ActivationIfMatchTagTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final String FIRST = "sha256:closure-first";
    private static final String SECOND = "sha256:closure-second";

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private ActivationService service;
    private ActivationSelectionService selections;
    private ActivationExclusionService exclusions;
    private StoredActivation stored;

    @BeforeEach
    void setUp() {
        packs = new FakeAvailablePacks(closure(FIRST));
        ActivationWrites writes = new ActivationWrites(repository, packs);
        service = new ActivationService(repository, packs, new ActivationIdMint());
        selections = new ActivationSelectionService(writes);
        exclusions = new ActivationExclusionService(writes);
        stored = repository
                .create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(PackRef.of("alpha")))
                                .selected(ArtifactKind.DIRECTIVE, List.of("D1"))
                                .build()
                                .document())
                .orElseThrow();
    }

    private static ai.speckitty.charter.activations.domain.ClosureSnapshot closure(String closureFingerprint) {
        return ClosureSnapshots.snapshot()
                .pack("alpha", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D1", "D2")
                .fingerprint("sha256:source")
                .closureFingerprint(closureFingerprint)
                .build();
    }

    private String closureTag(String closureFingerprint) {
        return EntityTags.quote(EffectiveTag.activation(stored.revision(), closure(closureFingerprint))
                .token());
    }

    /** The write's answer, asserted not to be a refusal, so a refused write fails the test on an assertion. */
    private static <T> T accepted(Supplier<T> write) {
        AtomicReference<T> answer = new AtomicReference<>();
        assertThatCode(() -> answer.set(write.get())).as("the write is accepted").doesNotThrowAnyException();
        return answer.get();
    }

    private static String quoted(StoredActivation activation) {
        return EntityTags.quote(activation.revision().token());
    }

    @Test
    void aPutTakesTheCurrentClosureTagAsIfMatchAndReadsTheClosureTwice() {
        ActivationService.Written written = accepted(() -> service.put(
                ID,
                ActivationDocument.builder()
                        .packs(List.of(PackRef.of("alpha")))
                        .selected(ArtifactKind.DIRECTIVE, List.of("D1", "D2"))
                        .build(),
                closureTag(FIRST),
                null));

        assertThat(written.created()).isFalse();
        assertThat(written.stored().document().selected(ArtifactKind.DIRECTIVE).orElseThrow())
                .containsExactly("D1", "D2");
        assertThat(packs.calls())
                .as("one verified read for the tag of the stored document's closure, one for the write")
                .isEqualTo(2);
    }

    @Test
    void aPutTakesTheRevisionAsIfMatchWithoutReadingThePacksForTheTag() {
        service.put(ID, ActivationDocument.builder().packs(List.of(PackRef.of("alpha"))).build(), quoted(stored), null);

        assertThat(packs.calls()).as("the write's own snapshot only").isEqualTo(1);
    }

    @Test
    void aPutRefusesAClosureTagOfAnotherClosureWith412AndStoresNothing() {
        String stale = closureTag(FIRST);
        packs.answer(closure(SECOND));

        assertThatThrownBy(() -> service.put(
                        ID, ActivationDocument.builder().packs(List.of(PackRef.of("alpha"))).build(), stale, null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        refused -> assertThat(refused.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(repository.find(ID)).contains(stored);
    }

    @Test
    void aClosureTagOfAnotherRevisionIsRefusedEvenOverTheCurrentClosure() {
        String anotherDocument = EntityTags.quote(EffectiveTag.activation(
                                ActivationDocument.builder().build().document().revision(), closure(FIRST))
                        .token());

        assertThatThrownBy(() -> service.put(
                        ID, ActivationDocument.builder().packs(List.of(PackRef.of("alpha"))).build(), anotherDocument, null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        refused -> assertThat(refused.kind()).isEqualTo(PreconditionException.Kind.STALE));
    }

    @Test
    void aClosureTagTheClosureCannotBeReadForIsRefusedWith412() {
        String tag = closureTag(FIRST);
        packs.refuse(new ai.speckitty.charter.kernel.domain.CharterRefusal(
                ai.speckitty.charter.kernel.domain.RefusalCode.PACK_UNREADABLE, "a pack cannot be read"));

        assertThatThrownBy(() -> service.delete(ID, tag))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        refused -> assertThat(refused.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(repository.find(ID)).contains(stored);
    }

    @Test
    void aDeleteTakesTheCurrentClosureTag() {
        assertThatCode(() -> service.delete(ID, closureTag(FIRST))).doesNotThrowAnyException();

        assertThat(repository.find(ID)).isEmpty();
    }

    @Test
    void aSubResourceWriteTakesTheCurrentClosureTagAndRefusesAStaleOne() {
        String tag = closureTag(FIRST);

        ActivationWrites.Outcome outcome = accepted(
                () -> selections.select(ID, "directive", "D2", Optional.empty(), false, List.of(), tag));

        assertThat(outcome.change().applied()).hasSize(1);
        packs.answer(closure(SECOND));
        assertThatThrownBy(() -> selections.unselect(ID, "directive", "D2", tag))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        refused -> assertThat(refused.kind()).isEqualTo(PreconditionException.Kind.STALE));
    }

    @Test
    void aReactivateAndADeactivateTakeTheClosureTagToo() {
        String tag = closureTag(FIRST);

        ActivationWrites.Outcome denied = accepted(() -> exclusions.deactivate(
                ID, "directive", "D2", Optional.empty(), java.util.OptionalInt.empty(), List.of(), tag));

        assertThat(denied.change().applied()).hasSize(1);
        StoredActivation after = denied.stored();
        String next = EntityTags.quote(EffectiveTag.activation(after.revision(), closure(FIRST))
                .token());
        assertThat(accepted(() -> exclusions.reactivate(ID, "directive", "D2", next))
                        .change()
                        .cleared())
                .hasSize(1);
    }

    @Test
    void aWriteWithoutIfMatchOnASubResourceIsStillMissing() {
        assertThatThrownBy(() -> selections.unselect(ID, "directive", "D1", null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        refused -> assertThat(refused.kind()).isEqualTo(PreconditionException.Kind.MISSING));
    }
}
