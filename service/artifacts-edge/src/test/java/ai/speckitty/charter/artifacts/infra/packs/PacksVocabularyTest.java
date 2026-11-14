package ai.speckitty.charter.artifacts.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.artifacts.application.ContextsInForce;
import ai.speckitty.charter.artifacts.application.GlossaryView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.domain.GlossaryContext;
import ai.speckitty.charter.packs.domain.GlossaryContextIdentifier;
import ai.speckitty.charter.packs.domain.GlossaryTerm;
import ai.speckitty.charter.packs.domain.TermIdentifier;
import ai.speckitty.charter.packs.domain.TermStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The winning copies of the contexts in force are read from the packs slice's production read, each winning pack once
 * however many of its contexts are in force (NFR-005), against the activation's fingerprint; of each pack only the
 * copies it wins are kept.
 *
 * <p>The packs: core (built-in) holds spec-kitty-core, testing and qa; proj (project) holds its own copy of
 * spec-kitty-core.
 */
class PacksVocabularyTest {

    private static final PackIdentifier CORE = new PackIdentifier("core");
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final Scope SCOPE = new Scope("a-1", "sha256:rev", "sha256:closure", FINGERPRINT);

    private static final GlossaryContext CORE_KITTY = context(CORE, "spec-kitty-core", "lane");
    private static final GlossaryContext TESTING = context(CORE, "testing", "flake");
    private static final GlossaryContext QA = context(CORE, "qa", "check");
    private static final GlossaryContext PROJ_KITTY = context(PROJ, "spec-kitty-core", "mission");

    /** The packs slice's read, counted: the production read path of the adapter. */
    private final ForeignArtifactRead packs = mock(ForeignArtifactRead.class);

    private static GlossaryContext context(PackIdentifier pack, String id, String termId) {
        return GlossaryContext.builder()
                .id(new GlossaryContextIdentifier(id))
                .packId(pack)
                .name("Name of " + id)
                .description("Where " + id + " applies.")
                .term(new GlossaryTerm(
                        new TermIdentifier(termId),
                        termId,
                        "The definition of " + termId + ".",
                        List.of(),
                        List.of(),
                        List.of(),
                        TermStatus.ACTIVE,
                        List.of()))
                .build();
    }

    private void holds(PackIdentifier pack, String fingerprint, CharterArtifact... contexts) {
        when(packs.held(pack, ArtifactKind.GLOSSARY))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Held(List.of(contexts), PackRevision.compute(List.of(pack.value()))),
                        fingerprint));
    }

    private static ContextsInForce inForce(Held... contexts) {
        return new ContextsInForce(SCOPE, List.of(contexts));
    }

    private static Held won(String id, PackIdentifier pack, PackLevel level) {
        return new Held(id, pack, level, "open");
    }

    @Test
    void twoContextsInForceOfOnePackAreOneReadOfThatPack() {
        holds(CORE, FINGERPRINT, CORE_KITTY, TESTING, QA);

        Map<String, GlossaryView> copies = new PacksVocabulary(packs)
                .read(inForce(
                        won("spec-kitty-core", CORE, PackLevel.BUILT_IN), won("testing", CORE, PackLevel.BUILT_IN)));

        assertThat(copies)
                .containsOnlyKeys("spec-kitty-core", "testing")
                .containsEntry("spec-kitty-core", PacksGlossaries.viewOf(CORE_KITTY))
                .containsEntry("testing", PacksGlossaries.viewOf(TESTING));
        verify(packs, times(1)).held(CORE, ArtifactKind.GLOSSARY);
        verifyNoMoreInteractions(packs);
    }

    @Test
    void contextsInForceOfTwoPacksAreOneReadOfEachAndOnlyTheCopyEachPackWinsIsKept() {
        holds(CORE, FINGERPRINT, CORE_KITTY, TESTING, QA);
        holds(PROJ, FINGERPRINT, PROJ_KITTY);

        Map<String, GlossaryView> copies = new PacksVocabulary(packs)
                .read(inForce(
                        won("spec-kitty-core", PROJ, PackLevel.PROJECT), won("testing", CORE, PackLevel.BUILT_IN)));

        assertThat(copies)
                .containsOnlyKeys("spec-kitty-core", "testing")
                .containsEntry("spec-kitty-core", PacksGlossaries.viewOf(PROJ_KITTY))
                .containsEntry("testing", PacksGlossaries.viewOf(TESTING));
        verify(packs, times(1)).held(CORE, ArtifactKind.GLOSSARY);
        verify(packs, times(1)).held(PROJ, ArtifactKind.GLOSSARY);
        verifyNoMoreInteractions(packs);
    }

    @Test
    void aWinnerItsPackNoLongerHoldsIsAbsent() {
        holds(CORE, FINGERPRINT, TESTING);

        assertThat(new PacksVocabulary(packs)
                        .read(inForce(
                                won("spec-kitty-core", CORE, PackLevel.BUILT_IN),
                                won("testing", CORE, PackLevel.BUILT_IN))))
                .containsOnlyKeys("testing");
    }

    @Test
    void noContextInForceReadsNoPack() {
        assertThat(new PacksVocabulary(packs).read(inForce())).isEmpty();
        verifyNoInteractions(packs);
    }

    @Test
    void aPackReadAtAnotherFingerprintIsStaleInput() {
        holds(CORE, "sha256:" + "0".repeat(64), TESTING);

        assertThatThrownBy(() -> new PacksVocabulary(packs).read(inForce(won("testing", CORE, PackLevel.BUILT_IN))))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }
}
