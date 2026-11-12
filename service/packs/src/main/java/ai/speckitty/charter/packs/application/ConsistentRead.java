package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackSources;
import java.time.Duration;
import java.util.Collection;
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
 *
 * <p>A read that starts while a pack swap runs (the {@link PackWriteGeneration} is odd) is answered when it ends in
 * that same odd generation and its two digests agree (FR-023, #490, the operator's call on squad finding D3); before,
 * any read that started in an odd generation refused {@code STALE_INPUT}, so every verified read answered 500 in the
 * swap window. It is sound because pack writes are serialized by {@link RegistryWriteLock} and each brackets one
 * directory swap with the generation, so one odd generation moves the readable state from the old pack to the new
 * once, and never back: digests taken at its start and at its end that agree prove nothing moved between them, so the
 * load in between saw that one state. A swap whose second rename fails moves the aside copy back, so the state goes
 * from the old pack to the old pack; a lock-free read of the pack that ran between its renames is read again over the
 * restored directory ({@link PackWriteGeneration#movesOf}, squad D-1), so no load or digest answers that window. The generation is therefore checked again <em>after</em> the second digest, so
 * the second digest was taken inside that same swap. A load or first digest that fails inside a swap stays
 * {@code STALE_INPUT}, as before: a failure there may be the swap's own. A read that started in an even generation is
 * judged as before.
 */
public final class ConsistentRead {

    private final PackSources sources;
    private final PackWriteGeneration generation;

    /**
     * A verifier over the inputs {@code sources} digests.
     *
     * @param sources the pack files port; digested afresh before and after every load
     */
    public ConsistentRead(PackSources sources) {
        this(sources, PackWriteGeneration.shared());
    }

    /**
     * A verifier that also watches a write generation.
     *
     * @param sources the pack files port
     * @param generation the write generation the pack writes bracket their swap with
     */
    public ConsistentRead(PackSources sources, PackWriteGeneration generation) {
        this.sources = Objects.requireNonNull(sources, "sources");
        this.generation = Objects.requireNonNull(generation, "generation");
    }

    /**
     * The digest of the named packs' files alone, for a caller that scopes a fingerprint to a closure. Call it inside
     * the {@code load} of {@link #read}: the whole-source digests taken before and after the load then guard it, so it
     * names the files the load read.
     *
     * @param packNames the configured names of the packs
     * @return {@code sha256:} and 64 lower-case hex digits
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when a named pack cannot be read
     */
    public String fingerprintOf(Collection<String> packNames) {
        return sources.fingerprintOf(Objects.requireNonNull(packNames, "packNames"));
    }

    /**
     * Waits until no pack swap runs, so that a retried read starts on a quiet registry.
     *
     * @param max the longest wait
     */
    public void awaitWrites(Duration max) {
        generation.awaitIdle(max);
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
     *     refused (the load's refusal is then the cause), when the write generation moved during the read, or when
     *     the read started inside a swap and its first digest or its load failed (the failure is then the cause);
     *     otherwise the load's own refusal
     */
    public <T> Verified<T> read(String staleDetail, Supplier<T> load) {
        Objects.requireNonNull(staleDetail, "staleDetail");
        Objects.requireNonNull(load, "load");
        long written = generation.registry();
        boolean insideSwap = PackWriteGeneration.isSwapping(written);
        String first = firstDigest(written, insideSwap, staleDetail);
        T value = loaded(first, written, insideSwap, staleDetail, load);
        if (moved(written)) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, staleDetail); // settled: no second digest is needed
        }
        String second = digestOrNull();
        // Inside a swap the generation is checked again after the second digest, so both digests fall in that one swap.
        if (!first.equals(second) || (insideSwap && moved(written))) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, staleDetail);
        }
        return new Verified<>(value, first);
    }

    /** Whether a swap started or finished since {@code written} was read. */
    private boolean moved(long written) {
        return generation.registry() != written;
    }

    /**
     * The first digest; a refusal inside a swap or while the generation moves (a directory renamed mid-listing) is
     * stale, not unreadable.
     */
    private String firstDigest(long written, boolean insideSwap, String staleDetail) {
        try {
            return sources.fingerprint();
        } catch (CharterRefusal unreadable) {
            if (insideSwap || moved(written)) {
                throw new CharterRefusal(RefusalCode.STALE_INPUT, staleDetail, unreadable);
            }
            throw unreadable;
        }
    }

    /**
     * The load's value; its failure is the answer only when the read started outside a swap and neither the digest nor
     * the generation moved.
     */
    private <T> T loaded(String first, long written, boolean insideSwap, String staleDetail, Supplier<T> load) {
        try {
            return load.get();
        } catch (RuntimeException failed) {
            if (insideSwap || moved(written) || !first.equals(digestAfterFailure(failed))) {
                throw new CharterRefusal(RefusalCode.STALE_INPUT, staleDetail, failed);
            }
            throw failed;
        }
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
