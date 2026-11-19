package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.domain.TensionFinding;
import ai.speckitty.charter.activations.domain.TensionFinding.Resolution;
import ai.speckitty.charter.activations.domain.TensionSnapshot;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code getActivationTensions} (ADR-0045 decisions 11 and 12, ruling G3): every tension in force, one finding per
 * unreconciled one, and a side offered for exclusion exactly when the exclusion write accepts it on the same document.
 */
class TensionReadServiceTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final NodeRef D = new NodeRef(NodeKind.DIRECTIVE, "D");
    private static final NodeRef D2 = new NodeRef(NodeKind.DIRECTIVE, "D2");
    private static final NodeRef LK = new NodeRef(NodeKind.DIRECTIVE, "LK");
    private static final NodeRef RQ = new NodeRef(NodeKind.DIRECTIVE, "RQ");
    private static final NodeRef T = new NodeRef(NodeKind.TACTIC, "t");

    private final CountingRepository repository = new CountingRepository();
    private FakeAvailablePacks packs;
    private ClosureSnapshot snapshot;
    private TensionReadService service;
    private ActivationExclusionService exclusions;

    @BeforeEach
    void setUp() {
        snapshot = ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "D", "D2", "LK", "RQ")
                .held(ArtifactKind.TACTIC, "t")
                .lockedBy("directive:LK", "main")
                .requiredBy("directive:RQ", "main")
                .build();
        packs = new FakeAvailablePacks(snapshot);
        service = new TensionReadService(new EffectiveReadService(repository, packs));
        exclusions = new ActivationExclusionService(new ActivationWrites(repository, packs));
        packs.tensions(query -> List.of(tension(D, D2, T), tension(D, LK), tension(LK, RQ)));
    }

    private static TensionSnapshot tension(NodeRef source, NodeRef target, NodeRef... reconcilers) {
        return new TensionSnapshot(source, target, Optional.empty(), Optional.empty(), List.of(reconcilers));
    }

    private StoredActivation stored() {
        return repository
                .create(
                        ID,
                        ActivationDocument.builder()
                                .packs(List.of(PackRef.of("main")))
                                .selected(ArtifactKind.DIRECTIVE, List.of("D", "D2", "LK", "RQ"))
                                .build()
                                .document())
                .orElseThrow();
    }

    private TensionReadService.Tensions found(Read<TensionReadService.Tensions> read) {
        assertThat(read).isInstanceOf(Read.Found.class);
        return ((Read.Found<TensionReadService.Tensions>) read).value();
    }

    @Test
    void everyTensionInForceIsAnsweredAndOnlyTheUnreconciledOnesHaveAFinding() {
        stored();

        TensionReadService.Tensions read = found(service.tensions(ID, null));

        assertThat(read.tensions())
                .extracting(TensionSnapshot::source, TensionSnapshot::target)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(D, D2),
                        org.assertj.core.groups.Tuple.tuple(D, LK),
                        org.assertj.core.groups.Tuple.tuple(LK, RQ));
        assertThat(read.findings())
                .as("D and D2 are reconciled by t, so they have no finding")
                .containsExactly(new TensionFinding(D, LK, true, false), new TensionFinding(LK, RQ, false, false));
        assertThat(read.findings().get(0).resolutions())
                .containsExactly(Resolution.RECONCILE, Resolution.DEACTIVATE_ONE_SIDE);
        assertThat(read.findings().get(1).resolutions())
                .as("a locked and a required side: nothing to exclude")
                .containsExactly(Resolution.RECONCILE);
    }

    @Test
    void anOfferedSideIsAcceptedByTheExclusionWriteAndAnUnofferedOneIsRefused() {
        StoredActivation stored = stored();
        String ifMatch = "\"" + stored.revision().token() + "\"";
        TensionReadService.Tensions read = found(service.tensions(ID, null));
        assertThat(read.findings().getFirst().sourceExcludable()).isTrue();

        assertThatThrownBy(() -> exclusions.deactivate(ID, "directive", "LK", Optional.empty(), List.of(), ifMatch))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ARTIFACT_LOCKED));
        assertThatThrownBy(() -> exclusions.deactivate(ID, "directive", "RQ", Optional.empty(), List.of(), ifMatch))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThat(exclusions
                        .deactivate(ID, "directive", "D", Optional.empty(), List.of(), ifMatch)
                        .change()
                        .applied())
                .as("the side the read offered: the exclusion write accepts it with the read's revision")
                .isNotEmpty();
    }

    @Test
    void aMatchingIfNoneMatchIsNotModifiedAndTheTagIsNotTheSkippedReports() {
        StoredActivation stored = stored();
        Read<TensionReadService.Tensions> first = service.tensions(ID, null);
        String etag = ((Read.Found<TensionReadService.Tensions>) first).etag();

        Read<TensionReadService.Tensions> again = service.tensions(ID, "\"" + etag + "\"");

        assertThat(again).isEqualTo(new Read.NotModified<TensionReadService.Tensions>(etag));
        assertThat(etag)
                .isEqualTo(EffectiveTag.tensions(stored.revision(), snapshot).token())
                .isNotEqualTo(EffectiveTag.skipped(stored.revision(), snapshot).token());
        assertThat(packs.tensionReads()).as("a 304 asks for no tension").isEqualTo(1);
    }

    @Test
    void anUnknownActivationIsActivationNotFound() {
        assertThatThrownBy(() -> service.tensions(new ActivationId("missing"), null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
    }
}
