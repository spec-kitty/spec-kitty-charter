package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.application.WriteCoordination;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The activations slice's {@link WriteCoordination} over the pack domain's registry write lock, the one every pack write
 * takes (R-lock). It names only {@code packs.application}, as the rest of this adapter package does.
 */
@Component
public class RegistryWriteCoordination implements WriteCoordination {

    private final RegistryWriteLock lock;

    /** Coordination over the lock the pack writes share. */
    @Autowired
    public RegistryWriteCoordination() {
        this(RegistryWriteLock.shared());
    }

    /**
     * Coordination over an explicit lock; a test injects a short timeout.
     *
     * @param lock the registry write lock
     */
    public RegistryWriteCoordination(RegistryWriteLock lock) {
        this.lock = Objects.requireNonNull(lock, "lock");
    }

    @Override
    public <T> T exclusively(Supplier<T> work) {
        return lock.exclusively(work);
    }
}
