package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Referrer;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The references of an artifact under an activation (#532; FR-012, research R1 and R10): the references the version in
 * force writes, which are the pack read of the references ({@code listArtifactReferences}) of the pack that holds the
 * winner, and nothing this edge computes. Two reads compose it: the activation names the winner, what the closure holds
 * and the source fingerprint; the winner's pack is read for its references against that fingerprint. Each reference
 * whose target the closure holds, of a kind the pack-scoped reads list, is marked for its read under the activation. The
 * artifact is answered whatever its state.
 *
 * <p>Refusals come in order: the activations port judges the activation ({@code activation_not_found}), the kind
 * ({@code kind_not_found}) and whether a pack of the closure holds the artifact ({@code artifact_not_found}). A second
 * read at another fingerprint, or of a winner its pack no longer holds, is {@code stale_input}.
 */
@Service
public final class EffectiveReferenceReads {

    private final ActivationArtifacts activations;
    private final ReferenceReads packs;

    /**
     * The reads over the activations port and the packs port.
     *
     * @param activations the port that names the winner under an activation
     * @param packs the port that reads the references the winner's pack holds
     */
    @Autowired
    public EffectiveReferenceReads(ActivationArtifacts activations, ReferenceReads packs) {
        this.activations = Objects.requireNonNull(activations, "activations");
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * The references the winner of one artifact writes, under an activation.
     *
     * @param activationId the activation's id
     * @param kindToken the artifact's kind token, as the request names it
     * @param artifactId the artifact's id
     * @return the references of the winner, each marked when the closure holds its target
     * @throws CharterRefusal as the activations port refuses, or as the pack read refuses, with {@code stale_input}
     */
    public Read references(String activationId, String kindToken, String artifactId) {
        Referrer referrer = activations.referrer(activationId, kindToken, artifactId);
        Held winner = referrer.winner();
        List<ReferenceReads.Reference> references = WinnerReads.of(() -> packs.references(
                winner.packId(), referrer.kind(), winner.id(), referrer.scope().sourceFingerprint()));
        List<Item> items = references.stream()
                .map(reference -> new Item(
                        reference,
                        reference.listed().isPresent() && referrer.held().contains(reference.urn())))
                .toList();
        return new Read(referrer.scope(), referrer.kind(), winner, items);
    }

    /**
     * One reference of the winner.
     *
     * @param reference the reference, as the pack read answers it
     * @param held whether the activation's closure holds its target, of a kind the pack-scoped reads list
     */
    public record Item(ReferenceReads.Reference reference, boolean held) {

        /**
         * Checks the fields.
         *
         * @param reference the reference
         * @param held whether the closure holds it
         */
        public Item {
            Objects.requireNonNull(reference, "reference");
        }
    }

    /**
     * What the read answers.
     *
     * @param scope the activation and the read
     * @param kind the referrer's kind
     * @param winner the referrer's winner
     * @param items the references, in the order the pack read gives them
     */
    public record Read(Scope scope, ArtifactKind kind, Held winner, List<Item> items) {

        /**
         * Checks and copies the fields.
         *
         * @param scope the scope
         * @param kind the kind
         * @param winner the winner
         * @param items the items
         */
        public Read {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(winner, "winner");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }
}
