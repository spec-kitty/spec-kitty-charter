package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.packs.domain.PackSources;
import org.jspecify.annotations.Nullable;

/**
 * A test seam: the real pack sources, plus one change a scenario arms to happen on disk right after the next digest
 * and before the load that follows it. It lives in test sources only. The scenario configuration wraps it in the one
 * {@code @Primary ConsistentRead}, so every reader of the seam sees an armed change: {@code ServiceInfoQuery} and
 * {@code TacticReadService}, which are the classes under test. The change fires on the next digest of any read,
 * including the harness's own {@code /info} probe after a 200.
 *
 * <p>It tests detection, not comparison: nothing here reports a different digest. The armed change rewrites a real
 * pack file, so the load reads the new bytes and the second digest, computed by the real adapter, differs from the
 * first only because the file did. Armed with nothing, it changes no file: the negative control, which must answer 200
 * with the digest that was verified.
 */
final class DriftingSources implements PackSources {

    private final PackSources real;
    private @Nullable Runnable change;

    /**
     * A seam over the real sources.
     *
     * @param real the sources the service digests its inputs with
     */
    DriftingSources(PackSources real) {
        this.real = real;
    }

    /**
     * Arms {@code onDisk} to run once, right after the next digest returns and before anything else reads the files.
     *
     * @param onDisk the change to the real files; a no-op arms the negative control
     */
    void arm(Runnable onDisk) {
        change = onDisk;
    }

    /** Forgets an armed change that never ran, so the next scenario starts clean. */
    void disarm() {
        change = null;
    }

    @Override
    public String fingerprint() {
        String digest = real.fingerprint();
        Runnable armed = change;
        change = null;
        if (armed != null) {
            armed.run();
        }
        return digest;
    }
}
