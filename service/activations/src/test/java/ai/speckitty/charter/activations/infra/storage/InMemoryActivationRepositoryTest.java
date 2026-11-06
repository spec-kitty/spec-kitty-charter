package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InMemoryActivationRepositoryTest extends ActivationRepositoryContract {

    @Override
    ActivationRepository repository() {
        return new InMemoryActivationRepository();
    }

    @Override
    void storeBytes(ActivationRepository repository, ActivationId id, byte[] bytes) {
        ((InMemoryActivationRepository) repository).putStoredBytes(id, bytes);
    }

    @Override
    byte[] storedBytes(ActivationRepository repository, ActivationId id) {
        return ((InMemoryActivationRepository) repository).storedBytes(id).orElseThrow();
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

    @Test
    void theStoredBytesAreCopiesSoNoCallerChangesTheStore() {
        InMemoryActivationRepository memory = new InMemoryActivationRepository();
        ActivationId id = new ActivationId("copied");
        byte[] seeded = "{\"schemaVersion\":2}".getBytes(StandardCharsets.UTF_8);
        memory.putStoredBytes(id, seeded);

        seeded[0] = ' ';
        memory.storedBytes(id).orElseThrow()[0] = ' ';

        assertThat(memory.storedBytes(id)).hasValueSatisfying(
                bytes -> assertThat(bytes).isEqualTo("{\"schemaVersion\":2}".getBytes(StandardCharsets.UTF_8)));
        assertThat(memory.storedBytes(new ActivationId("absent"))).isEmpty();
    }
}
