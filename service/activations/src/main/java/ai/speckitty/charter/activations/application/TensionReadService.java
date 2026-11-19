package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.domain.TensionFinding;
import ai.speckitty.charter.activations.domain.TensionSnapshot;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.transport.EntityTags;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * {@code getActivationTensions} (ADR-0045 decisions 11 and 12, ruling G3): every tension the activation puts in force,
 * and an {@code unreconciled_tension} finding for each that no artifact in force reconciles, with the resolutions the
 * activation offers. A finding never refuses: the read answers {@code 200} whatever it finds.
 *
 * <p>A side is offered for exclusion exactly when the exclusion write, with an empty body, would accept it on the same
 * stored document and closure ({@link ActivationExclusionService#excludable}): the read never advertises a write that
 * would refuse.
 */
@Service
public class TensionReadService {

    private final EffectiveReadService effective;

    /**
     * A read over the effective reads' repository and packs.
     *
     * @param effective the effective reads, which find the activation and open its closure
     */
    @Autowired
    public TensionReadService(EffectiveReadService effective) {
        this.effective = Objects.requireNonNull(effective, "effective");
    }

    /**
     * What the tensions read answers.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read
     * @param governanceFingerprint the digest of the closure's own packs
     * @param tensions the tensions in force, in (source, target) order
     * @param findings one finding per tension nothing in force reconciles, in the same order
     */
    public record Tensions(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            List<TensionSnapshot> tensions,
            List<TensionFinding> findings) {

        /**
         * Checks and copies the fields.
         *
         * @param stored the activation
         * @param sourceFingerprint the source fingerprint
         * @param governanceFingerprint the closure digest
         * @param tensions the tensions
         * @param findings the findings
         */
        public Tensions {
            Objects.requireNonNull(stored, "stored");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
            Objects.requireNonNull(governanceFingerprint, "governanceFingerprint");
            tensions = List.copyOf(Objects.requireNonNull(tensions, "tensions"));
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        }
    }

    /**
     * {@code getActivationTensions}.
     *
     * @param id the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the tensions and the findings, or {@code NotModified} when the header matches the read's tag
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code activation_not_found}, or as the effective
     *     reads refuse a closure that cannot compose or merge
     */
    public Read<Tensions> tensions(ActivationId id, @Nullable String ifNoneMatch) {
        StoredActivation stored = effective.found(id);
        PacksSession session = effective.open(stored);
        ClosureSnapshot closure = session.snapshot();
        String etag = EffectiveTag.tensions(stored.revision(), closure).token();
        if (EntityTags.noneMatch(ifNoneMatch, etag)) {
            return new Read.NotModified<>(etag);
        }
        List<TensionSnapshot> tensions = session.tensions(EffectiveSet.queryOf(stored.document()));
        ActivationDocument judged = ActivationWrites.current(stored, closure);
        List<TensionFinding> findings = tensions.stream()
                .filter(tension -> tension.reconciledBy().isEmpty())
                .map(tension -> TensionFinding.of(
                        tension,
                        ActivationExclusionService.excludable(judged, refOf(tension.source()), session),
                        ActivationExclusionService.excludable(judged, refOf(tension.target()), session)))
                .toList();
        return new Read.Found<>(
                new Tensions(stored, closure.fingerprint(), closure.closureFingerprint(), tensions, findings), etag);
    }

    /** The side as an artifact: a tension joins two guidance artifacts, which are activatable. */
    private static ArtifactRef refOf(NodeRef side) {
        return Urn.parse(side.urn())
                .flatMap(ArtifactRef::of)
                .orElseThrow(() -> new IllegalStateException("a side of a tension is an artifact: " + side.urn()));
    }
}
