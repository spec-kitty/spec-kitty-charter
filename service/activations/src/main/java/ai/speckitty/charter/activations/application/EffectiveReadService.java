package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSet.Summary;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The reads of what an activation puts in force: {@code getActivation} with its deny notice, the effective artifact
 * list and the skipped report (FR-013, FR-017, FR-020, FR-021, FR-027).
 */
@Service
public class EffectiveReadService {

    /**
     * The reads over the register and the packs.
     *
     * @param repository the activation register
     * @param packs the packs, read once per request
     */
    @Autowired
    public EffectiveReadService(ActivationRepository repository, AvailablePacks packs) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(packs, "packs");
    }

    /**
     * Where a page continues.
     *
     * @param position the last artifact of the page before
     * @param setTag the tag of the set that page belonged to
     * @param cursor the cursor as the request carried it
     */
    public record After(ArtifactRef position, String setTag, String cursor) {}

    /**
     * What a list request asks.
     *
     * @param kind the kind filter
     * @param state the state filter
     * @param pageSize the page size
     * @param after where the page continues
     */
    public record PageQuery(Optional<ArtifactKind> kind, Optional<State> state, int pageSize, Optional<After> after) {}

    /**
     * An activation as {@code getActivation} answers it.
     *
     * @param stored the stored activation
     * @param denyNotApplied the deny entries the floor or a lock overrides
     */
    public record Viewed(StoredActivation stored, List<ArtifactRef> denyNotApplied) {}

    /**
     * A page of the effective artifacts.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read
     * @param governanceFingerprint the digest of the closure's own packs
     * @param summary the counts of the whole set
     * @param items the page
     * @param next where the next page continues; empty on the last
     * @param query what was asked
     */
    public record Page(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            Summary summary,
            List<EffectiveArtifact> items,
            Optional<After> next,
            PageQuery query) {}

    /**
     * The skipped report of an activation.
     *
     * @param stored the stored activation
     * @param sourceFingerprint the source fingerprint of the read
     * @param governanceFingerprint the digest of the closure's own packs
     * @param skipped the report
     */
    public record Report(
            StoredActivation stored,
            String sourceFingerprint,
            String governanceFingerprint,
            EffectiveSkippedReport skipped) {}

    /** A page cursor of another set, which a client holds after the document or a pack of the closure changed. */
    public static final class StaleCursorException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** A cursor that belongs to another set. */
        public StaleCursorException() {
            super("The page cursor belongs to another set of effective artifacts; read the first page again.");
        }
    }

    /**
     * {@code getActivation}.
     *
     * @param id the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim
     * @return the read
     */
    public ActivationService.Read<Viewed> activation(ActivationId id, @Nullable String ifNoneMatch) {
        return new ActivationService.Read.NotModified<>("");
    }

    /**
     * {@code listEffectiveArtifacts}.
     *
     * @param id the activation's id
     * @param query what is asked
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim
     * @return the read
     */
    public ActivationService.Read<Page> list(ActivationId id, PageQuery query, @Nullable String ifNoneMatch) {
        return new ActivationService.Read.NotModified<>("");
    }

    /**
     * {@code getActivationSkippedReport}.
     *
     * @param id the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim
     * @return the read
     */
    public ActivationService.Read<Report> skipped(ActivationId id, @Nullable String ifNoneMatch) {
        return new ActivationService.Read.NotModified<>("");
    }
}
