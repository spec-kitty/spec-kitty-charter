package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.application.ActivationWrites;
import ai.speckitty.charter.activations.domain.ActivationChange;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.SkippedReport;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.SharedSkip;
import ai.speckitty.charter.api.model.SkippedDeactivated;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The wire form of a per-artifact write's answer: the stored revision, the references in the contract's shape, the four
 * skipped lists sorted, and an {@code up} link to the activation, no {@code self} (the write's path has no GET).
 */
class ActivationChangeMapperTest {

    private static final ArtifactRef D = new ArtifactRef(ArtifactKind.DIRECTIVE, "D");
    private static final ArtifactRef T = new ArtifactRef(ArtifactKind.TACTIC, "t");
    private static final ArtifactRef X = new ArtifactRef(ArtifactKind.TACTIC, "x");
    private static final ArtifactRef P = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "p");
    private static final ArtifactRef RESEARCH = new ArtifactRef(ArtifactKind.MISSION_TYPE, "research");

    private final ActivationChangeMapper mapper = new ActivationChangeMapper(new ActivationMapper("/api/v1"));

    private static ArtifactReference reference(ArtifactReference.KindEnum kind, String id) {
        return new ArtifactReference(kind, id);
    }

    @Test
    void everyMemberIsMappedAndTheListsAreSorted() {
        ActivationDocument after = ActivationDocument.builder()
                .selected(ArtifactKind.DIRECTIVE, List.of("D"))
                .build()
                .document();
        StoredActivation stored = StoredActivation.of(new ActivationId("a-1"), after);
        SkippedReport skipped = new SkippedReport(
                List.of(X, T),
                List.of(new SkippedReport.SkippedDeactivated(X, true, List.of(D))),
                List.of(P),
                List.of(new SkippedReport.SharedSkip(T, D)));
        ActivationChange change = new ActivationChange(after, List.of(RESEARCH, T, D), List.of(X), skipped);

        ai.speckitty.charter.api.model.ActivationChange answer =
                mapper.toChange(new ActivationWrites.Outcome(stored, change));

        assertThat(answer.getRevision()).isEqualTo(stored.revision().token());
        assertThat(answer.getApplied())
                .containsExactly(
                        reference(ArtifactReference.KindEnum.DIRECTIVE, "D"),
                        reference(ArtifactReference.KindEnum.MISSION_TYPE, "research"),
                        reference(ArtifactReference.KindEnum.TACTIC, "t"));
        assertThat(answer.getCleared()).containsExactly(reference(ArtifactReference.KindEnum.TACTIC, "x"));
        assertThat(answer.getSkipped().getSkippedByScope())
                .containsExactly(
                        reference(ArtifactReference.KindEnum.TACTIC, "t"),
                        reference(ArtifactReference.KindEnum.TACTIC, "x"));
        assertThat(answer.getSkipped().getSkippedDeactivated())
                .containsExactly(new SkippedDeactivated(
                        SkippedDeactivated.KindEnum.TACTIC,
                        "x",
                        true,
                        List.of(reference(ArtifactReference.KindEnum.DIRECTIVE, "D"))));
        assertThat(answer.getSkipped().getUnreachedBehindDeactivated())
                .containsExactly(reference(ArtifactReference.KindEnum.AGENT_PROFILE, "p"));
        assertThat(answer.getSkipped().getSkippedShared())
                .containsExactly(new SharedSkip(
                        SharedSkip.KindEnum.TACTIC, "t", reference(ArtifactReference.KindEnum.DIRECTIVE, "D")));
        assertThat(answer.getLinks()).containsOnlyKeys("up");
        assertThat(answer.getLinks().get("up").getHref()).isEqualTo("/api/v1/activations/a-1");
    }

    @Test
    void droppedIsMappedWhenTheWriteDroppedAStaleIdAndLeftOutOtherwise() {
        ActivationDocument after = ActivationDocument.builder().build().document();
        StoredActivation stored = StoredActivation.of(new ActivationId("a-1"), after);
        ActivationChange change = new ActivationChange(after, List.of(), List.of(), SkippedReport.empty());

        ai.speckitty.charter.api.model.ActivationChange none =
                mapper.toChange(new ActivationWrites.Outcome(stored, change));
        ai.speckitty.charter.api.model.ActivationChange some =
                mapper.toChange(new ActivationWrites.Outcome(stored, change.withDropped(List.of(T, D))));

        assertThat(none.getDropped()).isNull();
        assertThat(some.getDropped())
                .containsExactly(
                        reference(ArtifactReference.KindEnum.DIRECTIVE, "D"),
                        reference(ArtifactReference.KindEnum.TACTIC, "t"));
    }

    @Test
    void aNoOpAnswersTheStoredRevisionWithEmptyLists() {
        ActivationDocument document = ActivationDocument.builder().build().document();
        StoredActivation stored = StoredActivation.of(new ActivationId("a-1"), document);

        ai.speckitty.charter.api.model.ActivationChange answer = mapper.toChange(
                new ActivationWrites.Outcome(stored, ActivationChange.of(document, document, SkippedReport.empty())));

        assertThat(answer.getRevision()).isEqualTo(stored.revision().token());
        assertThat(answer.getApplied()).isEmpty();
        assertThat(answer.getCleared()).isEmpty();
        assertThat(answer.getSkipped().getSkippedShared()).isEmpty();
    }
}
