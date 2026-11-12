package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackDocumentRepository;
import ai.speckitty.charter.packs.domain.PackDocumentText;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The document read of a stored pack (#451): the pack is resolved as the summary resolves it, the document and the
 * revision are taken in one verified read, and what cannot be answered is a refusal with its own code.
 */
class PackDocumentQueryTest {

    private static final PackIdentifier ORG = new PackIdentifier("org-pack");
    private static final PackIdentifier BARE = new PackIdentifier("bare-pack");
    private static final PackIdentifier BUILT_IN = new PackIdentifier("built-in-pack");
    private static final String FINGERPRINT = "sha256:" + "0".repeat(64);
    private static final PackRevision REVISION = PackRevision.compute(List.of("a", "b"));
    private static final PackDocumentText DOCUMENT =
            new PackDocumentText("# Org\n\nWords.\n", Optional.of("Org"), "Words.\n");

    private final PackDocumentRepository documents = mock(PackDocumentRepository.class);
    private final PackRevisions revisions = mock(PackRevisions.class);
    private final PackDescriptorRepository descriptors = mock(PackDescriptorRepository.class);
    private final PackSummaryQuery summaries = new PackSummaryQuery(
            new ConsistentRead(() -> FINGERPRINT),
            () -> List.of(
                    new ConfiguredPack(ORG, PackLevel.ORG),
                    new ConfiguredPack(BARE, PackLevel.PROJECT),
                    new ConfiguredPack(BUILT_IN, PackLevel.BUILT_IN)),
            descriptors,
            revisions,
            documents);
    private final PackDocumentQuery query =
            new PackDocumentQuery(new ConsistentRead(() -> FINGERPRINT), summaries, documents, revisions);

    PackDocumentQueryTest() {
        when(descriptors.get(ORG)).thenReturn(mock(PackDescriptor.class));
        when(descriptors.get(BARE)).thenReturn(mock(PackDescriptor.class));
        when(revisions.currentRevision(ORG)).thenReturn(REVISION);
        when(revisions.currentRevision(BARE)).thenReturn(REVISION);
        when(documents.document(ORG)).thenReturn(Optional.of(DOCUMENT));
        when(documents.document(BARE)).thenReturn(Optional.empty());
    }

    @Test
    void aPackWithADocumentAnswersItWithTheRevisionAndTheFingerprintItWasReadUnder() {
        var verified = query.document("org-pack");

        assertThat(verified.value().document()).isEqualTo(DOCUMENT);
        assertThat(verified.value().pack()).isEqualTo(ORG);
        assertThat(verified.value().revision()).isEqualTo(REVISION);
        assertThat(verified.fingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void aPackWithoutADocumentIsRefusedWithItsOwnCode() {
        assertThatThrownBy(() -> query.document("bare-pack")).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_DOCUMENT_NOT_FOUND);
            assertThat(refusal.getMessage()).contains("bare-pack");
        });
    }

    @Test
    void anUnknownPackIsNotFoundAndItsDocumentIsNeverAsked() {
        assertThatThrownBy(() -> query.document("nobody"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
        verifyNoInteractions(documents);
    }

    @Test
    void aBuiltInPackIsNotFoundBecauseItIsReadOnlyAndHasNoRegistrationSummary() {
        assertThatThrownBy(() -> query.document("built-in-pack"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
                    assertThat(refusal.getMessage()).contains("built-in");
                });
        verifyNoInteractions(documents);
    }

    @Test
    void aPackTheStorageCannotResolveIsNotFoundAndKeepsTheUnreadableRefusalAsItsCause() {
        CharterRefusal unreadable = new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml is invalid");
        when(revisions.currentRevision(ORG)).thenThrow(unreadable);

        assertThatThrownBy(() -> query.document("org-pack")).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
            assertThat(refusal.getCause()).isSameAs(unreadable);
        });
    }

    @Test
    void aPackWhoseDescriptorCannotBeReadIsRefusedAsTheSummaryRefusesIt() {
        // The doc read judges the pack as getPack does (#451): a pack.yaml that cannot be read (a stored
        // active_charter,
        // for one) is the summary's 500 pack_unreadable, never a 200 of the README beside it.
        CharterRefusal unreadable = new CharterRefusal(
                RefusalCode.PACK_UNREADABLE, "pack.yaml of org-pack cannot be read: undeclared key 'active_charter'");
        when(descriptors.get(ORG)).thenThrow(unreadable);

        assertThatThrownBy(() -> summaries.summary("org-pack")).isSameAs(unreadable);
        assertThatThrownBy(() -> query.document("org-pack")).isSameAs(unreadable);
        verifyNoInteractions(documents);
    }

    @Test
    void aDocumentTheStoreRefusesStaysUnreadableNotNotFound() {
        when(documents.document(ORG))
                .thenThrow(new CharterRefusal(RefusalCode.PACK_UNREADABLE, "org-pack/README.md: carriage return"));

        assertThatThrownBy(() -> query.document("org-pack"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void theSummaryReportsWhetherThePackHoldsADocument() {
        when(documents.holdsDocument(ORG)).thenReturn(true);
        when(documents.holdsDocument(BARE)).thenReturn(false);
        PackDescriptorRepository descriptors = mock(PackDescriptorRepository.class);
        when(descriptors.get(ORG)).thenReturn(mock(PackDescriptor.class));
        when(descriptors.get(BARE)).thenReturn(mock(PackDescriptor.class));
        PackSummaryQuery withDescriptors = new PackSummaryQuery(
                new ConsistentRead(() -> FINGERPRINT),
                () -> List.of(new ConfiguredPack(ORG, PackLevel.ORG), new ConfiguredPack(BARE, PackLevel.PROJECT)),
                descriptors,
                revisions,
                documents);

        assertThat(withDescriptors.summary("org-pack").value().hasDocument()).isTrue();
        assertThat(withDescriptors.summary("bare-pack").value().hasDocument()).isFalse();
    }
}
