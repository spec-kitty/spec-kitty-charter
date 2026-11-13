package ai.speckitty.charter.packvalidation.badfixture;

import ai.speckitty.charter.packs.application.RegistryWriteLock;
import java.nio.channels.FileLock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/** A pack-validation class that takes a lock, its own, a file's or the registry's: a check holds no lock. */
public final class ValidationTakesALock {

    private ValidationTakesALock() {}

    /** Builds the lock the rule must refuse. */
    public static ReentrantLock lock() {
        return new ReentrantLock();
    }

    /** Names the registry's write lock, which the rule must refuse too. */
    public static @Nullable RegistryWriteLock registryLock() {
        return null;
    }

    /** Names a file lock, which the rule must refuse too. */
    public static @Nullable FileLock fileLock() {
        return null;
    }
}
