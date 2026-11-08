package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.packs.application.ActivationImpact;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The activations slice, as the registration impact check of {@code packs} asks it: the one implementation of
 * {@link ActivationImpact}. It names only {@code packs.application}, as the rest of this adapter package does
 * ({@code HexagonalArchitectureTest} pins it).
 *
 * <p>{@link #activationRoots()} is the {@code packs} list of every stored activation that reads, as the pack ids it names
 * in the order written, or empty for an activation that lists none, which roots at every configured pack. The
 * affected-activations notice is built in PR B (FR-027) and is added to this adapter then; until then the port's notice
 * hook does nothing.
 */
@Component
public class ActivationImpactAdapter implements ActivationImpact {

    private static final Logger LOG = LoggerFactory.getLogger(ActivationImpactAdapter.class);

    private final ActivationRepository repository;

    /**
     * The adapter over the activation store.
     *
     * @param repository the store of the activation documents
     */
    @Autowired
    public ActivationImpactAdapter(ActivationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public List<Optional<List<String>>> activationRoots() {
        List<StoredActivation> stored;
        try {
            stored = repository.list();
        } catch (RuntimeException unlistable) {
            // No refusal code names a fault of the activation store, so the write or the registration job ends as
            // internal_error, as a document the store cannot read back does (ADR-0065, DIV-614); this is its log line.
            LOG.error(
                    "The registration impact check cannot list the stored activations; the write is answered"
                            + " internal_error and nothing is written.",
                    unlistable);
            throw unlistable;
        }
        return stored.stream()
                .map(activation -> activation
                        .document()
                        .packs()
                        .map(packs -> packs.stream().map(PackRef::packId).toList()))
                .toList();
    }
}
