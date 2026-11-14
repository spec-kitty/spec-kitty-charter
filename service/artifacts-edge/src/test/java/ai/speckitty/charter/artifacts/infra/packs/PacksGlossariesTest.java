package ai.speckitty.charter.artifacts.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.artifacts.application.GlossaryReads;
import ai.speckitty.charter.artifacts.application.GlossaryView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.TermHolder;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.domain.AppliesTo;
import ai.speckitty.charter.packs.domain.GlossaryContext;
import ai.speckitty.charter.packs.domain.GlossaryContextIdentifier;
import ai.speckitty.charter.packs.domain.GlossaryTerm;
import ai.speckitty.charter.packs.domain.TermIdentifier;
import ai.speckitty.charter.packs.domain.TermStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The packs adapter maps the packs slice's glossary context into the edge's view, every field and every term with its
 * URN, passes the revision and the verified fingerprint through, lists in id order, and judges a term after the pack
 * and the context the packs slice judged.
 */
class PacksGlossariesTest {

    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final PackRevision REVISION = PackRevision.compute(List.of("qa"));

    private static final GlossaryTerm MISSION = new GlossaryTerm(
            new TermIdentifier("mission"),
            "mission",
            "A unit of planned work.",
            List.of("The mission ships it."),
            List.of("feature"),
            List.of("epic"),
            TermStatus.ACTIVE,
            List.of(
                    PackQualifiedReference.parse("term:core/lane"),
                    PackQualifiedReference.parse("writing::term:style/voice")));
    private static final GlossaryTerm LANE = new GlossaryTerm(
            new TermIdentifier("lane"),
            "lane",
            "A worktree.",
            List.of(),
            List.of(),
            List.of(),
            TermStatus.DEPRECATED,
            List.of());

    private static GlossaryContext core() {
        return GlossaryContext.builder()
                .id(new GlossaryContextIdentifier("core"))
                .packId(QA)
                .name("Core")
                .description("The core vocabulary.")
                .tags(List.of("spec-kitty"))
                .appliesTo(new AppliesTo(List.of("kitty-specs/**"), List.of("markdown"), List.of("spec-kitty")))
                .term(MISSION)
                .term(LANE)
                .build();
    }

    private static GlossaryContext bare(String id) {
        return GlossaryContext.builder()
                .id(new GlossaryContextIdentifier(id))
                .packId(QA)
                .name("Name of " + id)
                .description("Where " + id + " applies.")
                .term(LANE)
                .build();
    }

    private final ForeignArtifactRead packs = mock(ForeignArtifactRead.class);

    private void holds(GlossaryContext context) {
        when(packs.read(QA, ArtifactKind.GLOSSARY, context.localId(), 0))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Foreign(context, REVISION, Optional.empty(), List.of()), FINGERPRINT));
    }

    @Test
    void theListIsInIdOrderWithTheRevisionAndTheFingerprint() {
        when(packs.held(QA, ArtifactKind.GLOSSARY))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Held(List.of(bare("zeta"), core(), bare("alpha")), REVISION),
                        FINGERPRINT));

        GlossaryReads.Listed listed = new PacksGlossaries(packs).list(QA);

        assertThat(listed.contexts()).extracting(GlossaryView::id).containsExactly("alpha", "core", "zeta");
        assertThat(listed.contexts())
                .extracting(GlossaryView::name)
                .containsExactly("Name of alpha", "Core", "Name of zeta");
        assertThat(listed.revision()).isEqualTo(REVISION.token());
        assertThat(listed.sourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void theContextMapsEveryFieldAndEveryTermInAuthoredOrder() {
        holds(core());

        GlossaryReads.Read read = new PacksGlossaries(packs).context(QA, "core");

        assertThat(read.context())
                .isEqualTo(new GlossaryView(
                        "core",
                        QA,
                        "1.0",
                        "Core",
                        "The core vocabulary.",
                        List.of("spec-kitty"),
                        List.of("kitty-specs/**"),
                        List.of("markdown"),
                        List.of("spec-kitty"),
                        List.of(
                                new GlossaryView.Term(
                                        "mission",
                                        "mission",
                                        "A unit of planned work.",
                                        "active",
                                        List.of("The mission ships it."),
                                        List.of("feature"),
                                        List.of("epic"),
                                        List.of("term:core/lane", "writing::term:style/voice"),
                                        "term:core/mission"),
                                new GlossaryView.Term(
                                        "lane",
                                        "lane",
                                        "A worktree.",
                                        "deprecated",
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        "term:core/lane"))));
        assertThat(read.revision()).isEqualTo(REVISION.token());
        assertThat(read.sourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void aContextThatStatesNoTagsAndNoAppliesToHasEmptyLists() {
        holds(bare("qa"));

        GlossaryView context = new PacksGlossaries(packs).context(QA, "qa").context();

        assertThat(context.tags()).isEmpty();
        assertThat(context.paths()).isEmpty();
        assertThat(context.languages()).isEmpty();
        assertThat(context.stacks()).isEmpty();
    }

    @Test
    void aTermIsTheContextsTermWithItsUrnItsContextItsPackAndTheContextsRead() {
        holds(core());

        GlossaryReads.TermRead read = new PacksGlossaries(packs).term(QA, "core", "mission");

        assertThat(read.term().id()).isEqualTo("mission");
        assertThat(read.term().name()).isEqualTo("mission");
        assertThat(read.term().urn()).isEqualTo("term:core/mission");
        assertThat(read.term().seeAlso()).containsExactly("term:core/lane", "writing::term:style/voice");
        assertThat(read.contextId()).isEqualTo("core");
        assertThat(read.packId()).isEqualTo(QA);
        assertThat(read.revision()).isEqualTo(REVISION.token());
        assertThat(read.sourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nothing", "Mission", "1mission", ""})
    void aTermTheContextDoesNotHoldIsArtifactNotFoundNamingTheTermAndTheContext(String termId) {
        holds(core());
        PacksGlossaries adapter = new PacksGlossaries(packs);

        assertThatThrownBy(() -> adapter.term(QA, "core", termId))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                    assertThat(refusal.getMessage())
                            .isEqualTo("term `" + termId + "` is not held in glossary context `core`");
                });
    }

    @Test
    void theContextIsJudgedBeforeTheTermByThePacksSlice() {
        CharterRefusal context =
                new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, "glossary context `nothing` is not held");
        when(packs.read(QA, ArtifactKind.GLOSSARY, "nothing", 0)).thenThrow(context);
        PacksGlossaries adapter = new PacksGlossaries(packs);

        assertThatThrownBy(() -> adapter.term(QA, "nothing", "mission")).isSameAs(context);
        assertThatThrownBy(() -> adapter.context(QA, "nothing")).isSameAs(context);
    }

    @Test
    void theTwinMapsTheContextTheActivationReadAndRefusesAnotherKindAsAFault() {
        PacksGlossaries adapter = new PacksGlossaries(packs);

        assertThat(adapter.view(core(), "the read")).isEqualTo(PacksGlossaries.viewOf(core()));
        assertThatThrownBy(() -> adapter.view(
                        ai.speckitty.charter.packs.domain.Asset.builder()
                                .id(new ai.speckitty.charter.packs.domain.AssetIdentifier("logo"))
                                .packId(QA)
                                .mime("image/png")
                                .path("logo.png")
                                .build(),
                        "The read of the glossary context logo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The read of the glossary context logo holds asset logo, which is no glossary context of"
                        + " the packs slice.");
        verifyNoMoreInteractions(packs);
    }

    @Test
    void theTwinsListReadAtAnotherFingerprintIsStaleInput() {
        when(packs.held(QA, ArtifactKind.GLOSSARY))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Held(List.of(core()), REVISION), FINGERPRINT));
        PacksGlossaries adapter = new PacksGlossaries(packs);

        assertThat(adapter.list(QA, FINGERPRINT).contexts())
                .extracting(GlossaryView::id)
                .containsExactly("core");
        assertThatThrownBy(() -> adapter.list(QA, "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void theTermDetailNamesTheTermThenTheContext() {
        assertThat(TermHolder.termNotHeld("core", "mission"))
                .isEqualTo("term `mission` is not held in glossary context `core`");
        verifyNoMoreInteractions(packs);
    }
}
