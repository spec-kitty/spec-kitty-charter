package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.packs.application.ActivationImpact;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        return repository.list().stream()
                .map(stored -> stored.document()
                        .packs()
                        .map(packs -> packs.stream().map(PackRef::packId).toList()))
                .toList();
    }
}
