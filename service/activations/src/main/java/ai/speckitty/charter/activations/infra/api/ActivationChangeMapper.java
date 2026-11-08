package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.ActivationWrites;
import ai.speckitty.charter.activations.domain.ActivationChange;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.SkippedReport;
import ai.speckitty.charter.api.model.ActivationWarning;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.PackPin;
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
 * lists, each sorted, {@code skippedByDepth} only when the cascade ran under a {@code depth} (IC-21), {@code dropped} only when the write dropped a stale id (ADR-0059 amendment A5),
 * {@code droppedPins} always (B1: the pins it dropped, each as stored, {@code []} when none; {@link #pins}), the
 * {@code warnings} of the write (FR-032: always present, {@code []} when none, sorted by artifact), and the links. The
 * ten read buckets of the skipped report answer {@code []}: a write reports what its cascade left out, and what a read
 * leaves out is the effective reads' to report. The write's path has no {@code GET}, so the answer has no {@code self}; its {@code up} is the activation the
 * write applied to.
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
                pins(change.droppedPins()),
                warnings(change.warnings()),
                Map.of("up", new Link(activations.self(outcome.stored().id().value()))));
        if (!change.dropped().isEmpty()) {
            answer.setDropped(references(change.dropped()));
        }
        return answer;
    }

    private static ai.speckitty.charter.api.model.SkippedReport skipped(SkippedReport report) {
        ai.speckitty.charter.api.model.SkippedReport skipped = new ai.speckitty.charter.api.model.SkippedReport(
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
                        .toList(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        report.skippedByDepth().map(ActivationChangeMapper::references).ifPresent(skipped::setSkippedByDepth);
        return skipped;
    }

    private static List<ActivationWarning> warnings(List<ActivationChange.Warning> warnings) {
        return warnings.stream()
                .map(warning -> new ActivationWarning(
                        ActivationWarning.CodeEnum.fromValue(warning.code().token()), reference(warning.artifact())))
                .toList();
    }

    /**
     * Pins in the contract's {@code PackPin} shape, in the order given: the pack and the version as it was pinned.
     *
     * @param pins the pins
     * @return their wire form
     */
    static List<PackPin> pins(List<PackRef> pins) {
        return pins.stream()
                .map(ref -> {
                    PackPin pin = new PackPin(ref.packId());
                    ref.version().ifPresent(pin::setVersion);
                    return pin;
                })
                .toList();
    }

    /**
     * Ids in the contract's {@code ArtifactReference} shape, in the order given.
     *
     * @param refs the ids
     * @return their wire form
     */
    static List<ArtifactReference> references(List<ArtifactRef> refs) {
        return refs.stream().map(ActivationChangeMapper::reference).toList();
    }

    private static ArtifactReference reference(ArtifactRef ref) {
        return new ArtifactReference(
                ArtifactReference.KindEnum.fromValue(ref.kind().token()), ref.id());
    }
}
