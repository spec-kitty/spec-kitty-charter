package ai.speckitty.charter.packs.infra.fetch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When the resolver guard last refused each allowlisted host. {@code HttpClient} turns a failed connect-time lookup
 * into a bare {@code ConnectException} and drops the guard's {@link NonGlobalAddressException}, and the JDK answers a
 * refused {@code localhost} lookup with the loopback address anyway; this record is how the fetcher still types a
 * rebinding caught at connect as {@code NON_GLOBAL_ADDRESS}, and how a test observes the guard the JDK installed.
 * Only allowlisted hosts are ever recorded, so it is bounded by the allowlist.
 */
final class GuardRefusals {

    private final Map<String, Long> lastRefusal = new ConcurrentHashMap<>();

    void record(String host) {
        lastRefusal.put(GuideAllowlist.normalise(host), System.nanoTime());
    }

    /**
     * Whether the guard refused the host at or after the given instant.
     *
     * @param host the host
     * @param sinceNanos a {@link System#nanoTime()} reading
     */
    boolean refusedSince(String host, long sinceNanos) {
        Long at = lastRefusal.get(GuideAllowlist.normalise(host));
        return at != null && at - sinceNanos >= 0;
    }
}
