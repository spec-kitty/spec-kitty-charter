package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import org.junit.jupiter.api.Test;

class InMemoryActivationRepositoryTest extends ActivationRepositoryContract {

    @Override
    ActivationRepository repository() {
        return new InMemoryActivationRepository();
    }

    @Test
    void clearForgetsEveryActivation() {
        InMemoryActivationRepository memory = new InMemoryActivationRepository();
        ActivationId id = new ActivationId("cleared");
        memory.create(id, document(1));

        memory.clear();

        assertThat(memory.find(id)).isEmpty();
        assertThat(memory.create(id, document(2))).isPresent();
    }
}
