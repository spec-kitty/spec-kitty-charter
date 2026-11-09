package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackSources;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The freshness rule of every built artifact read (ADR-0018 D11): digest the inputs, load, digest again, and answer
 * only when both digests agree. The answer carries the digest it verified, so a caller never reports a fingerprint it
 * took separately.
 *
 * <p>This class has no stereotype and is never component-scanned: the deployment holds more than one {@link
 * PackSources}, so the port is not injected by type. A configuration creates it with a {@code @Bean} method and use
 * cases receive the {@code ConsistentRead}, never the port.
 *
 * <p>Only a {@link RuntimeException} from the load is compared against the second digest. An {@link Error}, or any
 * other throwable that is not a {@code RuntimeException}, propagates at once, without the stale comparison. When the
 * load failed and the second digest then breaks with something that is not a {@link CharterRefusal}, that break
 * propagates with the load failure added as suppressed.
 */
public final class ConsistentRead {

    private final PackSources sources;

    /**
     * A verifier over the inputs {@code sources} digests.
     *
     * @param sources the pack files port; digested afresh before and after every load
     */
    public ConsistentRead(PackSources sources) {
        this.sources = Objects.requireNonNull(sources, "sources");
    }

    /**
     * A load and the digest of the inputs it read.
     *
     * @param value what the load answered
     * @param fingerprint the digest taken before the load and confirmed after it
     * @param <T> the type of the loaded value
     */
    public record Verified<T>(T value, String fingerprint) {}

    /**
     * Runs {@code load} between two digests of the inputs.
     *
     * @param staleDetail the whole detail sentence of the {@code STALE_INPUT} refusal
     * @param load the read; it may refuse, and its refusal is the answer when the inputs did not change
     * @param <T> the type of the loaded value
     * @return the loaded value and the digest both reads agree on
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the first digest cannot be taken (the load is not run),
     *     or with {@code STALE_INPUT} when the second digest differs or cannot be taken, even when the load itself
     *     refused (the load's refusal is then the cause); otherwise the load's own refusal
     */
    public <T> Verified<T> read(String staleDetail, Supplier<T> load) {
        Objects.requireNonNull(staleDetail, "staleDetail");
        Objects.requireNonNull(load, "load");
        String first = sources.fingerprint();
        T value;
        try {
            value = load.get();
        } catch (RuntimeException failed) {
            if (!first.equals(digestAfterFailure(failed))) {
                throw new CharterRefusal(RefusalCode.STALE_INPUT, staleDetail, failed);
            }
            throw failed;
        }
        if (!first.equals(digestOrNull())) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, staleDetail);
        }
        return new Verified<>(value, first);
    }

    /** The second digest after a failed load; if it breaks in a way that is no refusal, that break keeps the load failure. */
    private @Nullable String digestAfterFailure(RuntimeException loadFailure) {
        try {
            return digestOrNull();
        } catch (RuntimeException broken) {
            broken.addSuppressed(loadFailure);
            throw broken;
        }
    }

    /** The digest now, or {@code null} when an input readable a moment ago no longer is: it changed. */
    private @Nullable String digestOrNull() {
        try {
            return sources.fingerprint();
        } catch (CharterRefusal unreadable) {
            return null;
        }
    }
}
