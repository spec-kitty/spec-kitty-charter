package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.ActivationWrites;
import ai.speckitty.charter.activations.domain.ActivationChange;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.SkippedReport;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.SharedSkip;
import ai.speckitty.charter.api.model.SkippedDeactivated;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The wire form of a per-artifact write's answer (the contract's {@code ActivationChange}, ADR-0020 D6): the revision
 * as stored after the write, {@code applied} and {@code cleared} as {@code ArtifactReference} items, the four skipped
 * lists, each sorted, {@code dropped} only when the write dropped a stale id (ADR-0059 amendment R5), and the links. The write's path has no {@code GET}, so the answer has no {@code self}; its
 * {@code up} is the activation the write applied to.
 */
@Component
public class ActivationChangeMapper {

    private final ActivationMapper activations;

    /**
     * A mapper that names the activation through the document mapper's paths.
     *
     * @param activations the document mapper, whose {@code self} path is the activation's
     */
    @Autowired
    public ActivationChangeMapper(ActivationMapper activations) {
        this.activations = Objects.requireNonNull(activations, "activations");
    }

    /**
     * The answer of a write.
     *
     * @param outcome the write's outcome
     * @return the wire form
     */
    public ai.speckitty.charter.api.model.ActivationChange toChange(ActivationWrites.Outcome outcome) {
        ActivationChange change = outcome.change();
        ai.speckitty.charter.api.model.ActivationChange answer = new ai.speckitty.charter.api.model.ActivationChange(
                outcome.stored().revision().token(),
                references(change.applied()),
                references(change.cleared()),
                skipped(change.skipped()),
                Map.of("up", new Link(activations.self(outcome.stored().id().value()))));
        if (!change.dropped().isEmpty()) {
            answer.setDropped(references(change.dropped()));
        }
        return answer;
    }

    private static ai.speckitty.charter.api.model.SkippedReport skipped(SkippedReport report) {
        return new ai.speckitty.charter.api.model.SkippedReport(
                references(report.skippedByScope()),
                report.skippedDeactivated().stream()
                        .map(skip -> new SkippedDeactivated(
                                SkippedDeactivated.KindEnum.fromValue(
                                        skip.ref().kind().token()),
                                skip.ref().id(),
                                skip.viaRequires(),
                                references(skip.requiredBy())))
                        .toList(),
                references(report.unreachedBehindDeactivated()),
                report.skippedShared().stream()
                        .map(skip -> new SharedSkip(
                                SharedSkip.KindEnum.fromValue(skip.ref().kind().token()),
                                skip.ref().id(),
                                reference(skip.referencedBy())))
                        .toList());
    }

    private static List<ArtifactReference> references(List<ArtifactRef> refs) {
        return refs.stream().map(ActivationChangeMapper::reference).toList();
    }

    private static ArtifactReference reference(ArtifactRef ref) {
        return new ArtifactReference(
                ArtifactReference.KindEnum.fromValue(ref.kind().token()), ref.id());
    }
}
